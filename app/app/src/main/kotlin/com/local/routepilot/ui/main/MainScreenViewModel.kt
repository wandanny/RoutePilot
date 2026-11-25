package com.local.routepilot.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.routepilot.data.DeviceLocationProvider
import com.local.routepilot.data.PlanResult
import com.local.routepilot.data.RoutePlanner
import com.local.routepilot.data.RouteRepository
import com.local.routepilot.domain.FailureReason
import com.local.routepilot.domain.RouteEngine
import com.local.routepilot.domain.RouteInfo
import com.local.routepilot.domain.RouteLinks
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class PlanState(
  val busy: Boolean = false,
  val distanceKm: Double = 0.0,
  val durationMin: Double = 0.0,
  val unresolved: Int = 0,
  val roadOptimized: Boolean = false,
  val chunks: List<List<Stop>> = emptyList(),
  val chunkIndex: Int = 0,
  val showConfirm: Boolean = false,
)

data class RoutePilotUiState(
  val activeRoute: RouteInfo? = null,
  val stops: List<Stop> = emptyList(),
  val routes: List<RouteInfo> = emptyList(),
  val failures: List<Stop> = emptyList(),
  val message: String? = null,
  val origin: Pair<Double, Double>? = null,
  val plan: PlanState = PlanState(),
)

class MainScreenViewModel(
  private val repository: RouteRepository,
  private val planner: RoutePlanner,
  private val locationProvider: DeviceLocationProvider,
) : ViewModel() {
  private val origin = MutableStateFlow<Pair<Double, Double>?>(null)
  private val message = MutableStateFlow<String?>(null)
  private val _plan = MutableStateFlow(PlanState())
  private var lastCompleted: Long? = null
  private var plannerJob: Job? = null

  val ready: StateFlow<Boolean>
    get() = _ready
  private val _ready = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      runCatching { repository.ensureActiveRoute() }
      _ready.value = true
    }
  }

  val uiState = combine(
    repository.activeRoute,
    repository.routes,
    repository.failures,
    origin,
    message,
  ) { route, routes, failures, origin, message ->
    RoutePilotUiState(route, emptyList(), routes, failures, message, origin)
  }.combine(_plan) { state, plan ->
    state.copy(plan = plan)
  }.combine(
    repository.activeRoute.flatMapLatest { route ->
      if (route == null) flowOf(emptyList()) else repository.stops(route.id)
    },
  ) { state, stops ->
    val ordered = if (state.plan.roadOptimized) {
      stops.sortedBy { it.orderIndex }
    } else {
      RouteEngine.order(stops, state.origin?.first, state.origin?.second)
    }
    state.copy(stops = ordered)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = RoutePilotUiState(),
  )

  fun setOrigin(latitude: Double, longitude: Double) {
    origin.value = latitude to longitude
  }

  fun consumeMessage() {
    message.value = null
  }

  suspend fun prepareNavigation() {
    locationProvider.current()?.let { origin.value = it }
    val result = runCatching { planner.prepare(origin.value) }.getOrNull() ?: PlanResult()
    _plan.value = _plan.value.copy(
      distanceKm = result.distanceKm,
      durationMin = result.durationMin,
      unresolved = result.unresolved,
      roadOptimized = result.roadOptimized,
    )
  }

  fun optimizeNow() {
    if (_plan.value.busy) return
    locationProvider.current()?.let { origin.value = it }
    plannerJob?.cancel()
    plannerJob = viewModelScope.launch {
      _plan.value = _plan.value.copy(busy = true)
      val result = runCatching { planner.prepare(origin.value) }.getOrNull() ?: PlanResult()
      _plan.value = _plan.value.copy(
        busy = false,
        distanceKm = result.distanceKm,
        durationMin = result.durationMin,
        unresolved = result.unresolved,
        roadOptimized = result.roadOptimized,
      )
      message.value = when {
        result.roadOptimized -> "Route optimized: %.1f km · %.0f min".format(result.distanceKm, result.durationMin)
        result.unresolved > 0 -> "${result.unresolved} address(es) still unresolved"
        else -> "Road routing unavailable — using straight lines"
      }
    }
  }

  fun resetRoute() {
    viewModelScope.launch {
      repository.newRoute("New route")
      _plan.value = PlanState()
      message.value = "Route archived. New route started."
    }
  }

  private fun refreshPlan() {
    plannerJob?.cancel()
    locationProvider.current()?.let { origin.value = it }
    plannerJob = viewModelScope.launch {
      _plan.value = _plan.value.copy(busy = true)
      val result = runCatching { planner.prepare(origin.value) }.getOrNull() ?: PlanResult()
      _plan.value = _plan.value.copy(
        busy = false,
        distanceKm = result.distanceKm,
        durationMin = result.durationMin,
        unresolved = result.unresolved,
        roadOptimized = result.roadOptimized,
      )
      if (result.unresolved > 0) {
        message.value = "${result.unresolved} address(es) could not be geocoded and were left at the end"
      }
    }
  }

  fun importCsv(text: String) {
    viewModelScope.launch {
      val (count, errors) = repository.importCsv(text)
      message.value = when {
        count == 0 && errors.isNotEmpty() -> errors.first()
        errors.isNotEmpty() -> "Imported $count stops. ${errors.first()}"
        else -> "Imported $count stops"
      }
      if (count > 0) refreshPlan()
    }
  }

  fun addStop(
    address: String, label: String, recipient: String, phone: String, notes: String,
    priority: Int, windowStart: String, windowEnd: String, latitude: Double?, longitude: Double?,
  ) {
    if (address.isBlank()) {
      message.value = "Address is required"
      return
    }
    viewModelScope.launch {
      val route = repository.activeRoute.first()
      if (route == null) {
        message.value = "No active route"
        return@launch
      }
      repository.addStop(
        Stop(
          routeId = route.id, address = address.trim(), label = label.trim(),
          recipient = recipient.trim(), phone = phone.trim(), notes = notes.trim(),
          priority = priority, windowStart = windowStart.trim(), windowEnd = windowEnd.trim(),
          latitude = latitude, longitude = longitude,
        ),
      )
      message.value = "Stop added"
      refreshPlan()
    }
  }

  fun complete(id: Long) {
    viewModelScope.launch {
      lastCompleted = id
      repository.setStatus(id, StopStatus.DONE)
      message.value = "Stop completed"
      refreshPlan()
    }
  }

  fun fail(id: Long, reason: FailureReason, note: String, retryAt: String) {
    viewModelScope.launch {
      repository.setStatus(id, StopStatus.FAILED, reason, note, retryAt)
      message.value = "Stop moved to Exceptions"
      refreshPlan()
    }
  }

  fun undo() {
    val id = lastCompleted ?: return
    viewModelScope.launch {
      repository.setStatus(id, StopStatus.PENDING)
      lastCompleted = null
      message.value = "Completion undone"
      refreshPlan()
    }
  }

  fun pin(id: Long) {
    viewModelScope.launch { repository.pin(id) }
  }

  fun move(id: Long, delta: Int) {
    viewModelScope.launch {
      val routeId = repository.activeRoute.first()?.id ?: return@launch
      val stops = repository.stopsNow(routeId).toMutableList()
      val index = stops.indexOfFirst { it.id == id }
      val target = index + delta
      if (index >= 0 && target in stops.indices) {
        val moved = stops.removeAt(index)
        stops.add(target, moved)
        repository.updateStops(stops.mapIndexed { i, stop -> stop.copy(orderIndex = i, pinned = false) })
      }
    }
  }

  fun navigate(context: Context, stop: Stop) {
    val uri = if (stop.latitude != null && stop.longitude != null) {
      Uri.parse("geo:${stop.latitude},${stop.longitude}?q=${Uri.encode(stop.label.ifBlank { stop.address })}")
    } else {
      Uri.parse("geo:0,0?q=${Uri.encode(stop.address)}")
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
      .onFailure { message.value = "No map app installed" }
  }

  fun openRoute() {
    val stops = uiState.value.stops.filter { it.status == StopStatus.PENDING }
    if (stops.isEmpty()) {
      message.value = "No pending stops to route"
      return
    }
    _plan.value = _plan.value.copy(chunks = RouteLinks.chunks(stops), chunkIndex = 0, showConfirm = true)
  }

  fun dismissRouteConfirm() {
    _plan.value = _plan.value.copy(showConfirm = false)
  }

  fun confirmRoute(context: Context) {
    val chunks = _plan.value.chunks
    if (chunks.isEmpty()) return
    openChunk(context, chunks, 0)
    _plan.value = _plan.value.copy(showConfirm = false, chunkIndex = 1)
  }

  fun openNextChunk(context: Context) {
    val chunks = _plan.value.chunks
    val index = _plan.value.chunkIndex
    if (index >= chunks.size) return
    openChunk(context, chunks, index)
    _plan.value = _plan.value.copy(chunkIndex = index + 1)
  }

  private fun openChunk(context: Context, chunks: List<List<Stop>>, index: Int) {
    val chunk = chunks[index]
    val originText = if (index == 0) {
      origin.value?.let { "${it.first},${it.second}" }
        ?: place(chunk.first())
    } else {
      place(chunks[index - 1].last())
    }
    val url = RouteLinks.build(originText, chunk)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
      .onFailure { message.value = "Google Maps is not installed" }
  }

  private fun place(stop: Stop): String =
    if (stop.latitude != null && stop.longitude != null) "${stop.latitude},${stop.longitude}" else stop.address

  suspend fun backupJson(): String = repository.exportBackup()
  suspend fun routeCsv(routeId: Long): String = repository.exportCsv(routeId)

  fun restoreBackup(text: String) {
    viewModelScope.launch {
      runCatching { repository.importBackup(text) }
        .onSuccess { message.value = "Backup restored"; refreshPlan() }
        .onFailure { message.value = "Restore failed: ${it.message}" }
    }
  }

  fun newRoute() {
    viewModelScope.launch {
      repository.newRoute("New route")
      message.value = "New route created"
      _plan.value = PlanState()
    }
  }
}
