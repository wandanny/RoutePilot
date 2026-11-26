package com.local.routepilot.domain

object RouteOrderingPolicy {
  fun reorderedIndices(matrix: List<List<Double>>, stopCount: Int, locked: Boolean): List<Int>? {
    if (locked || stopCount <= 0) return null

    fun cost(from: Int, to: Int): Double {
      if (from !in matrix.indices || to !in matrix[from].indices) return Double.MAX_VALUE / 4
      return matrix[from][to]
    }

    var best: List<Int> = emptyList()
    var bestCost = Double.MAX_VALUE
    for (last in 1..stopCount) {
      val remaining = (1..stopCount).filter { it != last }.toMutableList()
      val order = mutableListOf<Int>()
      var current = 0
      while (remaining.isNotEmpty()) {
        val next = remaining.minByOrNull { cost(current, it) } ?: break
        order.add(next)
        remaining.remove(next)
        current = next
      }
      order.add(last)

      var total = 0.0
      var at = 0
      for (next in order) {
        total += cost(at, next)
        at = next
      }
      if (total < bestCost) {
        bestCost = total
        best = order
      }
    }
    return best
  }
}
