package com.local.routepilot.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.local.routepilot.domain.Stop
import org.koin.androidx.compose.koinViewModel

@Composable
fun NavigationScreen(
  fullscreen: Boolean = false,
  onToggleFullscreen: () -> Unit = {},
  focusStopId: Long? = null,
  onComplete: (Stop) -> Unit = {},
  onFail: (Stop) -> Unit = {},
  onNavigate: (Stop) -> Unit = {},
) {
  val viewModel: NavigationViewModel = koinViewModel()
  val state by viewModel.uiState.collectAsState(NavigationUiState())

  LaunchedEffect(state.message) {
    state.message?.let { viewModel.consumeMessage() }
  }

  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier.fillMaxWidth().padding(12.dp).zIndex(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Route map", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
      OutlinedButton(onClick = { viewModel.recalculate() }) { Text("Recalculate") }
      TextButton(onClick = onToggleFullscreen) {
        Text(if (fullscreen) "Minimize" else "Maximize")
      }
    }

    Box(
      Modifier
        .fillMaxWidth()
        .zIndex(-1f)
        .then(if (fullscreen) Modifier.weight(1f) else Modifier.height(300.dp)),
    ) {
      MapCanvas(
        modifier = Modifier.fillMaxSize(),
        focusStopId = focusStopId,
        showRecenter = true,
      )
    }

    Card(Modifier.fillMaxWidth().padding(8.dp).zIndex(1f)) {
      Column(Modifier.padding(12.dp)) {
        Text(
          "%.1f km · %.0f min · ${state.stops.size} stops".format(
            state.path?.distanceKm ?: 0.0,
            state.path?.durationMin ?: 0.0,
          ),
          style = MaterialTheme.typography.labelMedium,
        )
        if (state.path?.steps?.isNotEmpty() == true) {
          val step = state.path!!.steps[state.stepIndex.coerceAtMost(state.path!!.steps.lastIndex)]
          Text(step.instruction, style = MaterialTheme.typography.titleMedium)
          Text("${step.distanceM.toInt()} m", style = MaterialTheme.typography.bodyMedium)
        } else {
          Text("Road steps unavailable — showing the direct path.", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }

  state.selectedStop?.let { stop ->
    AlertDialog(
      onDismissRequest = { viewModel.clearSelection() },
      title = { Text(stop.label.ifBlank { stop.address }) },
      text = { Text(stop.address) },
      confirmButton = { TextButton(onClick = { onComplete(stop); viewModel.clearSelection() }) { Text("Done") } },
      dismissButton = {
        Row {
          TextButton(onClick = { onNavigate(stop); viewModel.clearSelection() }) { Text("Navigate") }
          TextButton(onClick = { onFail(stop); viewModel.clearSelection() }) { Text("Failed") }
        }
      },
    )
  }
}
