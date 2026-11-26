package com.local.routepilot.data

import com.local.routepilot.domain.RouteEngine
import com.local.routepilot.domain.RouteOrderingPolicy
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class PlanResult(
  val distanceKm: Double = 0.0,
  val durationMin: Double = 0.0,
  val unresolved: Int = 0,
  val roadOptimized: Boolean = false,
  val ordered: List<Stop> = emptyList(),
  val locked: Boolean = false,
)

class RoutePlanner(
  private val repository: RouteRepository,
  private val geocoder: GeocodingService,
) {
  private val optimizer = OsrmMatrix()

  suspend fun prepare(origin: Pair<Double, Double>?): PlanResult = withContext(Dispatchers.IO) {
    val route = repository.activeRoute.first() ?: return@withContext PlanResult()
    val locked = route.locked
    val restrict = geocoder.restrictArea()
    val pending = repository.stopsNow(route.id).filter { it.status == StopStatus.PENDING }

    var unresolved = 0
    pending.forEach { stop ->
      if (stop.latitude == null || stop.longitude == null) {
        val point = geocoder.resolve(stop.address, origin, restrict)
        if (point != null) {
          repository.updateCoordinates(stop.id, point.first, point.second)
        } else {
          unresolved++
        }
      }
    }

    val geocoded = repository.stopsNow(route.id)
      .filter { it.status == StopStatus.PENDING && it.latitude != null && it.longitude != null }
    if (locked) {
      return@withContext PlanResult(unresolved = unresolved, ordered = geocoded, locked = true)
    }

    val points = geocoded.map { it.latitude!! to it.longitude!! }
    var durations: List<List<Double>>? = null
    var distances: List<List<Double>>? = null
    if (origin != null && points.size >= 2) {
      if (geocoder.tomtom.hasKey()) {
        durations = geocoder.tomtom.matrix(origin, points)
      }
      if (durations == null) {
        val table = optimizer.table(origin, points)
        durations = table?.first
        distances = table?.second
      }
    }

    val indices = durations?.let { RouteOrderingPolicy.reorderedIndices(it, points.size, locked = false) }
    val orderedStops = indices?.mapNotNull { index -> geocoded.getOrNull(index - 1) } ?: geocoded
    val unresolvedStops = repository.stopsNow(route.id)
      .filter { it.status == StopStatus.PENDING && (it.latitude == null || it.longitude == null) }
    val finalOrder = orderedStops + unresolvedStops
    repository.updateOrder(finalOrder.mapIndexed { index, stop -> stop.copy(orderIndex = index) })

    var distanceM = 0.0
    var durationS = 0.0
    if (indices != null && durations != null) {
      var at = 0
      for (index in indices) {
        durationS += durations.getOrNull(at)?.getOrNull(index) ?: 0.0
        distanceM += distances?.getOrNull(at)?.getOrNull(index)
          ?: straightMeters(at, index, origin, points)
        at = index
      }
    }

    PlanResult(
      distanceKm = distanceM / 1000.0,
      durationMin = durationS / 60.0,
      unresolved = unresolved,
      roadOptimized = indices != null,
      ordered = finalOrder,
    )
  }

  private fun straightMeters(
    from: Int,
    to: Int,
    origin: Pair<Double, Double>?,
    points: List<Pair<Double, Double>>,
  ): Double {
    val a = if (from == 0) origin else points.getOrNull(from - 1)
    val b = points.getOrNull(to - 1)
    if (a == null || b == null) return 0.0
    return RouteEngine.haversine(a.first, a.second, b.first, b.second) * 1000.0
  }
}

private class OsrmMatrix {
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  suspend fun table(
    origin: Pair<Double, Double>,
    stops: List<Pair<Double, Double>>,
  ): Pair<List<List<Double>>, List<List<Double>>>? = withContext(Dispatchers.IO) {
    runCatching {
      val coords = listOf(origin) + stops
      val path = coords.joinToString(";") { "${it.second},${it.first}" }
      val request = Request.Builder()
        .url("https://router.project-osrm.org/table/v1/driving/$path?annotations=duration,distance")
        .build()
      val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
      val root = JSON.parseToJsonElement(body).jsonObject
      if (root["code"]?.toString()?.trim('"') != "Ok") return@runCatching null
      val durations = root["durations"]?.jsonArray ?: return@runCatching null
      val distances = root["distances"]?.jsonArray ?: return@runCatching null
      table(durations) to table(distances)
    }.getOrNull()
  }

  private fun table(array: JsonArray): List<List<Double>> =
    array.map { row -> row.jsonArray.map { cell -> cell.jsonPrimitive.doubleOrNull ?: 0.0 } }

  companion object { private val JSON = Json { ignoreUnknownKeys = true } }
}
