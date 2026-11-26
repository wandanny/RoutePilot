package com.local.routepilot.domain

import kotlinx.serialization.Serializable

@Serializable
enum class StopStatus { PENDING, DONE, FAILED }

@Serializable
enum class FailureReason(val label: String) {
  NOT_HOME("Not home"),
  REFUSED("Refused delivery"),
  CLOSED("Business closed"),
  WRONG_ADDRESS("Wrong address"),
  OTHER("Other"),
}

@Serializable
data class Stop(
  val id: Long = 0,
  val routeId: Long,
  val address: String,
  val label: String = "",
  val recipient: String = "",
  val phone: String = "",
  val notes: String = "",
  val priority: Int = 0,
  val windowStart: String = "",
  val windowEnd: String = "",
  val latitude: Double? = null,
  val longitude: Double? = null,
  val orderIndex: Int = 0,
  val pinned: Boolean = false,
  val status: StopStatus = StopStatus.PENDING,
  val failureReason: FailureReason? = null,
  val failureNote: String = "",
  val retryAt: String = "",
  val trackingId: String = "",
  val estimatedCoordinates: Boolean = false,
)

@Serializable
data class RouteInfo(
  val id: Long,
  val name: String,
  val date: String,
  val active: Boolean,
  val locked: Boolean = false,
)
