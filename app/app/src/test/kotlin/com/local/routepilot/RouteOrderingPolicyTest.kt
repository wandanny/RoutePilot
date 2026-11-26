package com.local.routepilot

import com.local.routepilot.domain.RouteOrderingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteOrderingPolicyTest {
  // Square road-duration matrix in seconds: row/column 0 is the depot, 1..3 are stops.
  private val durations = listOf(
    listOf(0.0, 10.0, 30.0, 20.0),
    listOf(10.0, 0.0, 15.0, 25.0),
    listOf(30.0, 15.0, 0.0, 5.0),
    listOf(20.0, 25.0, 5.0, 0.0),
  )

  @Test
  fun lockedRouteReturnsNoReordering() {
    assertNull(RouteOrderingPolicy.reorderedIndices(durations, 3, true))
  }

  @Test
  fun unlockedRouteReturnsEachStopOnce() {
    val indices = RouteOrderingPolicy.reorderedIndices(durations, 3, false)

    assertEquals(3, indices?.size)
    assertEquals((1..3).toSet(), indices?.toSet())
  }

  @Test
  fun unlockedRouteStaysAPermutationWithAsymmetricDurations() {
    val asymmetric = listOf(
      listOf(0.0, 4.0, 9.0, 2.0),
      listOf(7.0, 0.0, 3.0, 11.0),
      listOf(1.0, 8.0, 0.0, 6.0),
      listOf(5.0, 2.0, 12.0, 0.0),
    )

    val indices = RouteOrderingPolicy.reorderedIndices(asymmetric, 3, false)

    assertEquals(3, indices?.size)
    assertEquals((1..3).toSet(), indices?.toSet())
  }
}
