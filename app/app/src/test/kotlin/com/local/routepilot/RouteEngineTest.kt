package com.local.routepilot

import com.local.routepilot.data.CsvParser
import com.local.routepilot.domain.RouteEngine
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEngineTest {
  private fun stop(id: Long, lat: Double?, lon: Double?, priority: Int = 0, pinned: Boolean = false) = Stop(
    id = id, routeId = 1, address = "Address $id", latitude = lat, longitude = lon,
    priority = priority, pinned = pinned, status = StopStatus.PENDING,
  )

  @Test
  fun nearestStopComesFirst() {
    val ordered = RouteEngine.order(
      listOf(stop(1, 51.9, 0.1), stop(2, 51.5, 0.0)),
      51.0, 0.0,
    )
    assertEquals(2L, ordered.first().id)
  }

  @Test
  fun pinnedStopStaysFirst() {
    val ordered = RouteEngine.order(
      listOf(stop(1, 51.9, 0.1), stop(2, 51.5, 0.0, pinned = true)),
      51.0, 0.0,
    )
    assertEquals(2L, ordered.first().id)
  }

  @Test
  fun higherPriorityWinsOverDistance() {
    val ordered = RouteEngine.order(
      listOf(stop(1, 51.9, 0.1, priority = 5), stop(2, 51.5, 0.0)),
      51.0, 0.0,
    )
    assertEquals(1L, ordered.first().id)
  }

  @Test
  fun completedStopsAreMovedToTheEnd() {
    val done = stop(1, 51.5, 0.0).copy(status = StopStatus.DONE)
    val ordered = RouteEngine.order(listOf(done, stop(2, 51.9, 0.1)), 51.0, 0.0)
    assertEquals(2L, ordered.first().id)
    assertEquals(1L, ordered.last().id)
  }
}

class CsvParserTest {
  @Test
  fun detectsSemicolonAndReadsQuotedFields() {
    val result = CsvParser.parse("address;label;notes\n\"Main St; 1\";Home;\"Said, hi\"")
    assertTrue(result.errors.isEmpty())
    assertEquals("Main St; 1", result.rows.first()["address"])
    assertEquals("Said, hi", result.rows.first()["notes"])
  }

  @Test
  fun malformedRowIsReported() {
    val result = CsvParser.parse("address,label\nOnlyOneCell")
    assertEquals(1, result.errors.size)
    assertTrue(result.rows.isEmpty())
  }
}
