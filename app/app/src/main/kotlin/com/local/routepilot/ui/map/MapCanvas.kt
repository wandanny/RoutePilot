package com.local.routepilot.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.local.routepilot.domain.Stop
import org.koin.androidx.compose.koinViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapCanvas(
  modifier: Modifier = Modifier,
  focusStopId: Long? = null,
  showRecenter: Boolean = true,
  onMarkerClick: ((Stop) -> Unit)? = null,
) {
  val context = LocalContext.current
  val viewModel: NavigationViewModel = koinViewModel()
  val state by viewModel.uiState.collectAsState(NavigationUiState())
  var mapViewRef by remember { mutableStateOf<MapView?>(null) }
  var hasCentered by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    Configuration.getInstance().userAgentValue = "RoutePilot/1.0"
    viewModel.start(context)
  }
  DisposableEffect(Unit) {
    onDispose { viewModel.stop() }
  }
  LaunchedEffect(focusStopId) {
    if (focusStopId != null) viewModel.focusStop(focusStopId)
  }
  LaunchedEffect(state.stops.firstOrNull()?.id) {
    if (!hasCentered) {
      state.stops.firstOrNull()?.let { stop ->
        if (stop.latitude != null && stop.longitude != null) {
          mapViewRef?.controller?.setCenter(GeoPoint(stop.latitude, stop.longitude))
          mapViewRef?.controller?.setZoom(13.0)
          hasCentered = true
        }
      }
    }
  }
  LaunchedEffect(state.selectedStop?.id) {
    state.selectedStop?.let { stop ->
      if (stop.latitude != null && stop.longitude != null) {
        mapViewRef?.controller?.animateTo(GeoPoint(stop.latitude, stop.longitude))
      }
    }
  }

  Box(modifier) {
    AndroidView(
      factory = { ctx: Context ->
        MapView(ctx).apply {
          mapViewRef = this
          setTileSource(TileSourceFactory.MAPNIK)
          setMultiTouchControls(true)
          controller.setZoom(13.0)
          overlays.add(MyLocationNewOverlay(this).apply {
            enableMyLocation()
            enableFollowLocation()
          })
        }
      },
      modifier = Modifier.fillMaxSize(),
      update = { mapView -> updateMap(mapView, state, viewModel, onMarkerClick) },
    )

    if (showRecenter) {
      Button(
        onClick = {
          state.location?.let { location ->
            mapViewRef?.controller?.animateTo(GeoPoint(location.latitude, location.longitude))
          }
        },
        modifier = Modifier.align(Alignment.BottomEnd),
      ) { Text("Recenter") }
    }
  }
}

private fun updateMap(
  mapView: MapView,
  state: NavigationUiState,
  viewModel: NavigationViewModel,
  onMarkerClick: ((Stop) -> Unit)?,
) {
  mapView.overlays.removeAll { it is Marker || it is Polyline }
  state.path?.points?.let { points ->
    if (points.size >= 2) {
      mapView.overlays.add(
        Polyline(mapView).apply {
          setPoints(points.map { GeoPoint(it.first, it.second) })
          color = 0xFFC2410C.toInt()
          width = 8f
        },
      )
    }
  }
  state.stops.forEachIndexed { index, stop ->
    if (stop.latitude == null || stop.longitude == null) return@forEachIndexed
    mapView.overlays.add(
      Marker(mapView).apply {
        position = GeoPoint(stop.latitude, stop.longitude)
        title = "${index + 1}. ${stop.label.ifBlank { stop.address }}"
        snippet = stop.address
        setOnMarkerClickListener { _, _ ->
          viewModel.selectStop(stop)
          onMarkerClick?.invoke(stop)
          true
        }
      },
    )
  }
  mapView.invalidate()
}
