package com.local.routepilot.data

import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PlanResult(
  val distanceKm: Double = 0.0,
  val durationMin: Double = 0.0,
  val unresolved: Int = 0,
  val roadOptimized: Boolean = false,
  val ordered: List<Stop> = emptyList(),
)

class RoutePlanner(
  private val repository: RouteRepository,
  private val lucif: LucifClient,
) {
  private val geocoder = NominatimClient()
  private val optimizer = OsrmOptimizer()

  suspend fun prepare(origin: Pair<Double, Double>?): PlanResult = withContext(Dispatchers.IO) {
    val route = repository.activeRoute.first() ?: return@withContext PlanResult()
    val all = repository.stopsNow(route.id)
    val pending = all.filter { it.status == StopStatus.PENDING }

    var unresolved = 0
    pending.forEach { stop ->
      if (stop.latitude == null || stop.longitude == null) {
        val point = geocoder.geocode(stop.address)
        if (point != null) {
          repository.updateCoordinates(stop.id, point.first, point.second)
        } else {
          val estimated = lucif.estimateCoordinates(stop.address)
          if (estimated != null) {
            repository.updateCoordinates(stop.id, estimated.first, estimated.second, estimated = true)
          } else {
            unresolved++
          }
        }
      }
    }

    val updated = repository.stopsNow(route.id)
    val withCoords = updated.filter { it.status == StopStatus.PENDING && it.latitude != null && it.longitude != null }
    val result = if (origin != null && withCoords.size >= 2) {
      optimizer.optimize(origin, withCoords)
    } else {
      null
    }

    if (result != null) {
      val byId = result.ordered.associateBy { it.id }
      val reordered = updated.map { byId[it.id] ?: it }
      repository.updateOrder(reordered)
    }

    PlanResult(
      distanceKm = result?.distanceKm ?: 0.0,
      durationMin = result?.durationMin ?: 0.0,
      unresolved = unresolved,
      roadOptimized = result != null,
    )
  }
}

private class NominatimClient {
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()
  private var lastCall = 0L

  suspend fun geocode(address: String): Pair<Double, Double>? {
    val wait = 1100 - (System.currentTimeMillis() - lastCall)
    if (wait > 0) delay(wait)
    lastCall = System.currentTimeMillis()
    val query = URLEncoder.encode(address, "UTF-8")
    val request = Request.Builder()
      .url("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=$query")
      .addHeader("User-Agent", "RoutePilot/1.0 (courier route helper)")
      .build()
    return withContext(Dispatchers.IO) {
      runCatching {
        val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
        val first = JSON.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject ?: return@runCatching null
        val lat = first["lat"]?.toString()?.toDoubleOrNull() ?: return@runCatching null
        val lon = first["lon"]?.toString()?.toDoubleOrNull() ?: return@runCatching null
        lat to lon
      }.getOrNull()
    }
  }

  companion object { private val JSON = Json { ignoreUnknownKeys = true } }
}

private class OsrmOptimizer {
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  suspend fun optimize(origin: Pair<Double, Double>, stops: List<Stop>): PlanResult? = withContext(Dispatchers.IO) {
    runCatching {
      val coords = listOf(origin) + stops.map { it.latitude!! to it.longitude!! }
      val path = coords.joinToString(";") { "${it.second},${it.first}" }
      val request = Request.Builder()
        .url("https://router.project-osrm.org/table/v1/driving/$path?annotations=duration,distance")
        .build()
      val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
      val root = JSON.parseToJsonElement(body).jsonObject
      if (root["code"]?.toString()?.trim('"') != "Ok") return@runCatching null
      val durations = root["durations"] as? JsonArray ?: return@runCatching null
      val distances = root["distances"] as? JsonArray ?: return@runCatching null

      var bestOrder: List<Int>? = null
      var bestCost = Double.MAX_VALUE
      for (lastIndex in 1..stops.size) {
        val remaining = (1..stops.size).filter { it != lastIndex }.toMutableList()
        val order = mutableListOf<Int>()
        var current = 0
        while (remaining.isNotEmpty()) {
          val next = remaining.minByOrNull { cost(durations, current, it) } ?: break
          order.add(next)
          remaining.remove(next)
          current = next
        }
        order.add(lastIndex)
        val cost = total(durations, 0, order)
        if (cost < bestCost) {
          bestCost = cost
          bestOrder = order
        }
      }
      val order = bestOrder ?: return@runCatching null
      val distance = total(distances, 0, order)
      PlanResult(
        distanceKm = distance / 1000.0,
        durationMin = bestCost / 60.0,
        unresolved = 0,
        roadOptimized = true,
      ).copy(ordered = order.map { stops[it - 1] })
    }.getOrNull()
  }

  private fun cost(matrix: JsonArray, from: Int, to: Int): Double =
    matrix.getOrNull(from)?.jsonArray?.getOrNull(to)?.toString()?.toDoubleOrNull() ?: Double.MAX_VALUE / 4

  private fun total(matrix: JsonArray, start: Int, order: List<Int>): Double {
    var current = start
    var sum = 0.0
    order.forEach { next ->
      sum += cost(matrix, current, next)
      current = next
    }
    return sum
  }

  companion object { private val JSON = Json { ignoreUnknownKeys = true } }
}
