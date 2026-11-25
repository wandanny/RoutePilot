package com.local.routepilot.domain

import java.net.URLEncoder

object RouteLinks {
  const val CHUNK_SIZE = 9

  fun chunks(stops: List<Stop>): List<List<Stop>> = stops.chunked(CHUNK_SIZE)

  fun build(origin: String, stops: List<Stop>): String {
    require(stops.isNotEmpty()) { "At least one stop is required" }
    val encoded = { value: String -> URLEncoder.encode(value, "UTF-8") }
    val place = { stop: Stop ->
      if (stop.latitude != null && stop.longitude != null) {
        "${stop.latitude},${stop.longitude}"
      } else {
        stop.address
      }
    }
    val waypoints = stops.dropLast(1).joinToString("%7C") { encoded(place(it)) }
    return buildString {
      append("https://www.google.com/maps/dir/?api=1")
      append("&origin=").append(encoded(origin))
      append("&destination=").append(encoded(place(stops.last())))
      if (waypoints.isNotBlank()) append("&waypoints=").append(waypoints)
      append("&travelmode=driving")
      append("&dir_action=navigate")
      append("&utm_source=routepilot&utm_campaign=optimized_route")
    }
  }
}
