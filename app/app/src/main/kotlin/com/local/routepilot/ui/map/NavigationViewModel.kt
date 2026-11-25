package com.local.routepilot.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.routepilot.data.OsrmRoute
import com.local.routepilot.data.RoutePath
import com.local.routepilot.data.RouteRepository
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class NavigationUiState(
  val stops: List<Stop> = emptyList(),
  val location: Location? = null,
  val path: RoutePath? = null,
  val stepIndex: Int = 0,
  val busy: Boolean = false,
  val selectedStop: Stop? = null,
  val message: String? = null,
)

class NavigationViewModel(
  private val repository: RouteRepository,
  private val osrm: OsrmRoute,
) : ViewModel() {
  private val _uiState = MutableStateFlow(NavigationUiState())
  val uiState = _uiState.asStateFlow()

  private var manager: LocationManager? = null
  private var lastRecalc = 0L
  private val listener = object : LocationListener {
    override fun onLocationChanged(location: Location) {
      _uiState.value = _uiState.value.copy(location = location)
      advanceStep(location)
      checkDeviation(location)
    }

    @Deprecated("Deprecated by the API", ReplaceWith("onLocationChanged"))
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
  }

  fun start(context: Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
      PackageManager.PERMISSION_GRANTED
    ) return
    manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
      .firstNotNullOfOrNull { provider -> runCatching { manager?.getLastKnownLocation(provider) }.getOrNull() }
    if (last != null) _uiState.value = _uiState.value.copy(location = last)
    runCatching {
      manager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 10f, listener, Looper.getMainLooper())
    }
    loadStops()
  }

  fun stop() {
    manager?.removeUpdates(listener)
    manager = null
  }

  private fun loadStops() {
    viewModelScope.launch {
      val route = repository.activeRoute.first()
      val stops = if (route == null) emptyList() else repository.stopsNow(route.id)
        .filter { it.status == StopStatus.PENDING && it.latitude != null && it.longitude != null }
      _uiState.value = _uiState.value.copy(stops = stops)
      recalculate()
    }
  }

  fun recalculate() {
    viewModelScope.launch {
      val state = _uiState.value
      if (state.stops.size < 2) {
        _uiState.value = state.copy(path = straightLine(state.stops), message = "Add more stops with coordinates for road routing")
        return@launch
      }
      _uiState.value = state.copy(busy = true)
      val origin = state.location?.let { it.latitude to it.longitude }
        ?: state.stops.first().let { it.latitude!! to it.longitude!! }
      val result = osrm.route(origin, state.stops)
      lastRecalc = System.currentTimeMillis()
      _uiState.value = _uiState.value.copy(
        busy = false,
        path = result ?: straightLine(state.stops),
        stepIndex = 0,
        message = if (result == null) "Road routing unavailable — showing straight lines" else null,
      )
    }
  }

  private fun straightLine(stops: List<Stop>): RoutePath = RoutePath(
    points = stops.map { it.latitude!! to it.longitude!! },
    steps = emptyList(),
  )

  private fun advanceStep(location: Location) {
    val state = _uiState.value
    val step = state.path?.steps?.getOrNull(state.stepIndex) ?: return
    val results = FloatArray(1)
    Location.distanceBetween(location.latitude, location.longitude, step.latitude, step.longitude, results)
    if (results[0] < 30f) {
      _uiState.value = state.copy(stepIndex = state.stepIndex + 1)
    }
  }

  private fun checkDeviation(location: Location) {
    val points = _uiState.value.path?.points ?: return
    if (points.isEmpty()) return
    if (System.currentTimeMillis() - lastRecalc < 60_000) return
    val nearest = points.minOf { point ->
      val results = FloatArray(1)
      Location.distanceBetween(location.latitude, location.longitude, point.first, point.second, results)
      results[0].toDouble()
    }
    if (nearest > 80) recalculate()
  }

  fun focusStop(stopId: Long) {
    viewModelScope.launch {
      val route = repository.activeRoute.first() ?: return@launch
      val stops = repository.stopsNow(route.id).filter { it.status == StopStatus.PENDING && it.latitude != null && it.longitude != null }
      val stop = stops.firstOrNull { it.id == stopId } ?: repository.stopsNow(route.id).firstOrNull { it.id == stopId } ?: return@launch
      _uiState.value = _uiState.value.copy(stops = stops, selectedStop = stop)
      recalculate()
    }
  }

  fun selectStop(stop: Stop) {
    _uiState.value = _uiState.value.copy(selectedStop = stop)
  }

  fun clearSelection() {
    _uiState.value = _uiState.value.copy(selectedStop = null)
  }

  fun consumeMessage() {
    _uiState.value = _uiState.value.copy(message = null)
  }
}
