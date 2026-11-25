package com.local.routepilot.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RouteEngine {
  private const val EARTH_RADIUS_KM = 6371.0

  fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
  }

  /**
   * Pinned stops keep their relative order at the front. The rest are selected
   * one at a time: highest priority first, then nearest to the current point,
   * then earliest time window. Address-only stops are appended at the end.
   */
  fun order(stops: List<Stop>, originLat: Double?, originLon: Double?): List<Stop> {
    val pinned = stops.filter { it.pinned && it.status == StopStatus.PENDING }
      .sortedBy { it.orderIndex }
    val candidates = stops.filter { !it.pinned && it.status == StopStatus.PENDING }.toMutableList()
    val done = stops.filter { it.status != StopStatus.PENDING }
      .sortedBy { it.orderIndex }
      .toMutableList()

    var lat = originLat
    var lon = originLon
    val result = pinned.toMutableList()

    while (candidates.isNotEmpty()) {
      val withCoords = candidates.filter { it.latitude != null && it.longitude != null }
      val currentLat = lat
      val currentLon = lon
      val next = if (withCoords.isNotEmpty() && currentLat != null && currentLon != null) {
        withCoords.minWith(
          compareByDescending<Stop> { it.priority }
            .thenBy { haversine(currentLat, currentLon, it.latitude!!, it.longitude!!) }
            .thenBy { it.windowStart }
        )
      } else {
        candidates.minWith(
          compareByDescending<Stop> { it.priority }
            .thenBy { it.windowStart }
        )
      }
      result.add(next)
      candidates.remove(next)
      if (next.latitude != null && next.longitude != null) {
        lat = next.latitude
        lon = next.longitude
      }
    }

    val finalOrder = result.toMutableList()
    finalOrder.addAll(done)
    return finalOrder.mapIndexed { index, stop -> stop.copy(orderIndex = index) }
  }

  fun distanceLabel(fromLat: Double?, fromLon: Double?, stop: Stop): String {
    if (fromLat == null || fromLon == null || stop.latitude == null || stop.longitude == null) return ""
    val km = haversine(fromLat, fromLon, stop.latitude, stop.longitude)
    return if (km < 1) "${(km * 1000).toInt()} m" else "${km.toInt()} km"
  }
}
