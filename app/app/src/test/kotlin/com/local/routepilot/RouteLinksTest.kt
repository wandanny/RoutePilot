package com.local.routepilot

import com.local.routepilot.domain.RouteLinks
import com.local.routepilot.domain.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLinksTest {
  private fun stop(id: Long, address: String, lat: Double? = null, lon: Double? = null) = Stop(
    id = id, routeId = 1, address = address, latitude = lat, longitude = lon,
  )

  @Test
  fun buildsDrivingDirectionsUrlWithWaypoints() {
    val url = RouteLinks.build("4.8,100.7", listOf(stop(1, "A", 4.81, 100.71), stop(2, "B", 4.82, 100.72)))
    assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
    assertTrue(url.contains("origin=4.8%2C100.7"))
    assertTrue(url.contains("destination=4.82%2C100.72"))
    assertTrue(url.contains("waypoints=4.81%2C100.71"))
    assertTrue(url.contains("travelmode=driving"))
    assertTrue(url.contains("dir_action=navigate"))
  }

  @Test
  fun chunksLongRoutesIntoNineStopGroups() {
    val stops = (1..20L).map { stop(it, "Stop $it") }
    val chunks = RouteLinks.chunks(stops)
    assertEquals(3, chunks.size)
    assertEquals(9, chunks[0].size)
    assertEquals(9, chunks[1].size)
    assertEquals(2, chunks[2].size)
  }
}
