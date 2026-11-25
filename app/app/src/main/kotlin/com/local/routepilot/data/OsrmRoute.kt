package com.local.routepilot.data

import com.local.routepilot.domain.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class RouteStep(
  val instruction: String,
  val distanceM: Double,
  val latitude: Double,
  val longitude: Double,
)

data class RoutePath(
  val points: List<Pair<Double, Double>> = emptyList(),
  val steps: List<RouteStep> = emptyList(),
  val distanceKm: Double = 0.0,
  val durationMin: Double = 0.0,
)

class OsrmRoute {
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  suspend fun route(origin: Pair<Double, Double>, stops: List<Stop>): RoutePath? = withContext(Dispatchers.IO) {
    runCatching {
      val coords = listOf(origin) + stops.map { it.latitude!! to it.longitude!! }
      val path = coords.joinToString(";") { "${it.second},${it.first}" }
      val request = Request.Builder()
        .url("https://router.project-osrm.org/route/v1/driving/$path?overview=full&geometries=geojson&steps=true")
        .build()
      val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
      val root = JSON.parseToJsonElement(body).jsonObject
      val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
      val coordinates = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray.orEmpty()
      val points = coordinates.mapNotNull { point ->
        val pair = point.jsonArray
        val lon = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull
        val lat = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull
        if (lat != null && lon != null) lat to lon else null
      }
      val steps = route["legs"]?.jsonArray?.flatMap { leg ->
        leg.jsonObject["steps"]?.jsonArray.orEmpty().mapNotNull { step ->
          val obj = step.jsonObject
          val maneuver = obj["maneuver"]?.jsonObject ?: return@mapNotNull null
          val location = maneuver["location"]?.jsonArray ?: return@mapNotNull null
          val lon = location.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
          val lat = location.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
          val type = maneuver["type"]?.jsonPrimitive?.content.orEmpty()
          val modifier = maneuver["modifier"]?.jsonPrimitive?.content.orEmpty()
          val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
          RouteStep(
            instruction = instruction(type, modifier, name),
            distanceM = obj["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            latitude = lat,
            longitude = lon,
          )
        }
      }.orEmpty()

      RoutePath(
        points = points,
        steps = steps,
        distanceKm = (route["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 1000.0,
        durationMin = (route["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 60.0,
      )
    }.getOrNull()
  }

  private fun instruction(type: String, modifier: String, name: String): String {
    val action = when (type) {
      "depart" -> "Head out"
      "arrive" -> "Arrive at destination"
      "turn" -> when (modifier) {
        "left" -> "Turn left"
        "right" -> "Turn right"
        "slight left" -> "Bear left"
        "slight right" -> "Bear right"
        "sharp left" -> "Sharp left"
        "sharp right" -> "Sharp right"
        "uturn" -> "Make a U-turn"
        else -> "Continue"
      }
      "merge" -> "Merge"
      "on ramp" -> "Take the ramp"
      "off ramp" -> "Leave the ramp"
      "roundabout", "rotary" -> "Take the roundabout"
      "fork" -> "Take the fork"
      "end of road" -> "At the end of the road, turn $modifier"
      else -> "Continue"
    }
    return if (name.isBlank()) action else "$action onto $name"
  }

  companion object { private val JSON = Json { ignoreUnknownKeys = true } }
}
