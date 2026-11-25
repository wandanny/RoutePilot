package com.local.routepilot.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.local.routepilot.domain.FailureReason
import com.local.routepilot.domain.RouteEngine
import com.local.routepilot.domain.RouteInfo
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import com.local.routepilot.ui.map.NavigationScreen
import com.local.routepilot.ui.scan.LabelScannerScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(viewModel: MainScreenViewModel = koinViewModel()) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsState(RoutePilotUiState())
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var tab by remember { mutableStateOf(0) }
  var showAdd by remember { mutableStateOf(false) }
  var failTarget by remember { mutableStateOf<Stop?>(null) }
  var showScanner by remember { mutableStateOf(false) }
  var mapFullscreen by remember { mutableStateOf(false) }
  var mapFocusStopId by remember { mutableStateOf<Long?>(null) }
  var showResetConfirm by remember { mutableStateOf(false) }

  val readText = { uri: android.net.Uri ->
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
  }
  val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { viewModel.importCsv(readText(it)) }
  }
  val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { viewModel.restoreBackup(readText(it)) }
  }
  val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) scope.launch {
      context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.backupJson().toByteArray()) }
    }
  }
  val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    if (uri != null && state.activeRoute != null) scope.launch {
      val text = viewModel.routeCsv(state.activeRoute!!.id)
      context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }
  }

  val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    if (it) viewModel.setOriginFromDevice(context)
  }
  val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

  LaunchedEffect(Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
    ) {
      viewModel.setOriginFromDevice(context)
    } else {
      locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(state.message) {
    state.message?.let {
      snackbar.showSnackbar(it)
      viewModel.consumeMessage()
    }
  }

  if (showScanner) {
    LabelScannerScreen(onClose = { showScanner = false })
    return
  }

  if (tab == 2 && mapFullscreen) {
    NavigationScreen(
      fullscreen = true,
      onToggleFullscreen = { mapFullscreen = false },
      focusStopId = mapFocusStopId,
      onComplete = { viewModel.complete(it.id) },
      onFail = { failTarget = it },
      onNavigate = { viewModel.navigate(context, it) },
    )
    failTarget?.let { stop ->
      FailDialog(
        stop = stop,
        onDismiss = { failTarget = null },
        onFail = { reason, note, retry ->
          viewModel.fail(stop.id, reason, note, retry)
          failTarget = null
        },
      )
    }
    return
  }

  val total = state.stops.size
  val done = state.stops.count { it.status == StopStatus.DONE }
  val next = state.stops.firstOrNull { it.status == StopStatus.PENDING }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    bottomBar = {
      NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf("Route" to "R", "Scan" to "S", "Map" to "M", "Exceptions" to "!", "Archive" to "A")
        items.forEachIndexed { index, item ->
          NavigationBarItem(
            selected = tab == index,
            onClick = { tab = index },
            icon = { Text(item.second, style = MaterialTheme.typography.labelMedium) },
            label = { Text(item.first, style = MaterialTheme.typography.labelMedium) },
          )
        }
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      if (tab != 2) {
        Text("RoutePilot", style = MaterialTheme.typography.displayLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AssistChip(onClick = {}, label = { Text(state.activeRoute?.name ?: "No route") })
          AssistChip(onClick = {}, label = { Text("$done / $total delivered") })
        }
      }

      when (tab) {
        0 -> {
          Card(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          ) {
            Column(Modifier.padding(20.dp)) {
              Text("Today's progress", style = MaterialTheme.typography.titleMedium)
              Text("$done of $total stops", style = MaterialTheme.typography.displayLarge)
              if (state.plan.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                  Text("  Optimising route…", style = MaterialTheme.typography.labelMedium)
                }
              } else if (state.plan.roadOptimized) {
                Text(
                  "%.1f km · %.0f min · road optimised".format(state.plan.distanceKm, state.plan.durationMin),
                  style = MaterialTheme.typography.labelMedium,
                )
              }
              if (next != null) {
                Text("Next: ${next.label.ifBlank { next.address }}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(next.address, style = MaterialTheme.typography.bodyMedium)
              } else {
                Text("No pending stops", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showScanner = true }) { Text("Scan label") }
            Button(onClick = {
              scope.launch {
                viewModel.prepareNavigation()
                mapFocusStopId = null
                tab = 2
                mapFullscreen = true
              }
            }) { Text("Navigate all stops") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importCsv.launch(arrayOf("text/*", "application/*")) }) { Text("Import CSV") }
            OutlinedButton(onClick = { showAdd = true }) { Text("Add stop") }
            OutlinedButton(onClick = { viewModel.optimizeNow() }) { Text("Optimize now") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { backup.launch("routepilot-backup.json") }) { Text("Backup") }
            TextButton(onClick = { restore.launch(arrayOf("application/json")) }) { Text("Restore") }
            TextButton(onClick = { csvExport.launch("route.csv") }) { Text("Export CSV") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { viewModel.undo() }) { Text("Undo") }
            TextButton(onClick = { viewModel.openRoute() }) { Text("Maps") }
            TextButton(onClick = { showResetConfirm = true }) { Text("Reset") }
          }

          if (state.stops.isEmpty()) {
            EmptyState()
          } else {
            StopList(
              state = state,
              viewModel = viewModel,
              onFail = { failTarget = it },
              onNavigate = { stop ->
                mapFocusStopId = stop.id
                tab = 2
                mapFullscreen = true
              },
            )
          }
        }

        1 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Scan the next parcel label", style = MaterialTheme.typography.titleLarge)
          Text("AI reads the address; you confirm it before saving.", style = MaterialTheme.typography.bodyMedium)
          Button(onClick = { showScanner = true }, modifier = Modifier.padding(top = 16.dp)) { Text("Open scanner") }
        }

        2 -> NavigationScreen(
          fullscreen = false,
          onToggleFullscreen = { mapFullscreen = true },
          focusStopId = mapFocusStopId,
          onComplete = { viewModel.complete(it.id) },
          onFail = { failTarget = it },
          onNavigate = { viewModel.navigate(context, it) },
        )

        3 -> ExceptionList(state.failures)
        4 -> ArchiveList(state.routes)
      }
    }
  }

  if (state.plan.showConfirm) {
    val chunks = state.plan.chunks
    AlertDialog(
      onDismissRequest = { viewModel.dismissRouteConfirm() },
      title = { Text("Optimized route ready") },
      text = {
        Column {
          Text("%.1f km · %.0f min".format(state.plan.distanceKm, state.plan.durationMin))
          Text("${chunks.size} Google Maps route(s), up to 9 stops each.")
          if (state.plan.unresolved > 0) Text("${state.plan.unresolved} address(es) without coordinates are at the end.")
        }
      },
      confirmButton = { TextButton(onClick = { viewModel.confirmRoute(context) }) { Text("Open Maps") } },
      dismissButton = { TextButton(onClick = { viewModel.dismissRouteConfirm() }) { Text("Cancel") } },
    )
  }

  if (state.plan.chunkIndex > 1 && state.plan.chunkIndex <= state.plan.chunks.size) {
    Button(onClick = { viewModel.openNextChunk(context) }, modifier = Modifier.padding(16.dp)) {
      Text("Open next route (${state.plan.chunkIndex}/${state.plan.chunks.size})")
    }
  }

  if (showResetConfirm) {
    AlertDialog(
      onDismissRequest = { showResetConfirm = false },
      title = { Text("Reset route?") },
      text = {
        Text(
          "Pending: ${state.stops.count { it.status == StopStatus.PENDING }} · " +
            "Done: ${state.stops.count { it.status == StopStatus.DONE }} · " +
            "Failed: ${state.stops.count { it.status == StopStatus.FAILED }}. " +
            "The current route will be archived and a new empty route started.",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          viewModel.resetRoute()
          showResetConfirm = false
          tab = 0
          mapFullscreen = false
        }) { Text("Reset route") }
      },
      dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
    )
  }

  if (showAdd) {
    AddStopDialog(onDismiss = { showAdd = false }, onAdd = { fields ->
      viewModel.addStop(
        address = fields[0], label = fields[1], recipient = fields[2], phone = fields[3],
        notes = fields[4], priority = fields[5].toIntOrNull() ?: 0,
        windowStart = fields[6], windowEnd = fields[7],
        latitude = fields[8].toDoubleOrNull(), longitude = fields[9].toDoubleOrNull(),
      )
      showAdd = false
    })
  }
  failTarget?.let { stop ->
    FailDialog(
      stop = stop,
      onDismiss = { failTarget = null },
      onFail = { reason, note, retry ->
        viewModel.fail(stop.id, reason, note, retry)
        failTarget = null
      },
    )
  }
}

@Composable
private fun EmptyState() {
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(20.dp)) {
      Text("No stops yet", style = MaterialTheme.typography.titleLarge)
      Text("Import a CSV or scan a label to get started. Stops are saved on this device.", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun StopList(
  state: RoutePilotUiState,
  viewModel: MainScreenViewModel,
  onFail: (Stop) -> Unit,
  onNavigate: (Stop) -> Unit,
) {
  val pending = state.stops.filter { it.status == StopStatus.PENDING }
  val finished = state.stops.filter { it.status != StopStatus.PENDING }
  LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(pending, key = { it.id }) { stop ->
      StopCard(
        stop = stop,
        distance = RouteEngine.distanceLabel(state.origin?.first, state.origin?.second, stop),
        onNavigate = { onNavigate(stop) },
        onComplete = { viewModel.complete(stop.id) },
        onFail = { onFail(stop) },
        onPin = { viewModel.pin(stop.id) },
        onMove = { delta -> viewModel.move(stop.id, delta) },
      )
    }
    item { Text("Completed / failed: ${finished.size}", style = MaterialTheme.typography.labelMedium) }
    items(finished, key = { it.id }) { stop ->
      Text("${stop.label.ifBlank { stop.address }} — ${stop.status.name}")
    }
  }
}

@Composable
private fun StopCard(
  stop: Stop, distance: String, onNavigate: () -> Unit, onComplete: () -> Unit,
  onFail: () -> Unit, onPin: () -> Unit, onMove: (Int) -> Unit,
) {
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(16.dp)) {
      Text(stop.label.ifBlank { stop.address }, style = MaterialTheme.typography.titleMedium)
      Text(stop.address, style = MaterialTheme.typography.bodyMedium)
      if (stop.recipient.isNotBlank()) Text("To: ${stop.recipient} ${stop.phone}")
      if (stop.windowStart.isNotBlank()) Text("Window: ${stop.windowStart}–${stop.windowEnd}")
      if (distance.isNotBlank()) Text(distance, style = MaterialTheme.typography.labelMedium)
      if (stop.estimatedCoordinates) {
        Text(
          "Coordinates estimated by AI — check the address",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onNavigate) { Text("Navigate") }
        TextButton(onClick = onComplete) { Text("Done") }
        TextButton(onClick = onFail) { Text("Failed") }
        TextButton(onClick = onPin) { Text(if (stop.pinned) "Unpin" else "Pin") }
        TextButton(onClick = { onMove(-1) }) { Text("↑") }
        TextButton(onClick = { onMove(1) }) { Text("↓") }
      }
    }
  }
}

@Composable
private fun ExceptionList(failures: List<Stop>) {
  if (failures.isEmpty()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No failed deliveries.") }
    return
  }
  LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(failures, key = { it.id }) { stop ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
          Text(stop.label.ifBlank { stop.address }, style = MaterialTheme.typography.titleMedium)
          Text("Reason: ${stop.failureReason?.label ?: "Unknown"}")
          if (stop.failureNote.isNotBlank()) Text(stop.failureNote)
          if (stop.retryAt.isNotBlank()) Text("Retry: ${stop.retryAt}")
        }
      }
    }
  }
}

@Composable
private fun ArchiveList(routes: List<RouteInfo>) {
  val archived = routes.filter { !it.active }
  if (archived.isEmpty()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No archived routes yet.") }
    return
  }
  LazyColumn {
    items(archived, key = { it.id }) { route ->
      Text("${route.name} · ${route.date}", modifier = Modifier.padding(8.dp))
    }
  }
}

@Composable
private fun AddStopDialog(onDismiss: () -> Unit, onAdd: (Array<String>) -> Unit) {
  var address by remember { mutableStateOf("") }
  var label by remember { mutableStateOf("") }
  var recipient by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var priority by remember { mutableStateOf("0") }
  var start by remember { mutableStateOf("") }
  var end by remember { mutableStateOf("") }
  var lat by remember { mutableStateOf("") }
  var lon by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add stop") },
    text = {
      Column {
        OutlinedTextField(address, { address = it }, label = { Text("Address *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(label, { label = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(recipient, { recipient = it }, label = { Text("Recipient") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(priority, { priority = it }, label = { Text("Priority") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(start, { start = it }, label = { Text("Window start") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(end, { end = it }, label = { Text("Window end") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onAdd(
          arrayOf(
            address, label, recipient, phone, notes,
            priority.toIntOrNull()?.toString() ?: "0", start, end,
            lat.toDoubleOrNull()?.toString().orEmpty(), lon.toDoubleOrNull()?.toString().orEmpty(),
          ),
        )
      }) { Text("Add") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun FailDialog(stop: Stop, onDismiss: () -> Unit, onFail: (FailureReason, String, String) -> Unit) {
  var reason by remember { mutableStateOf(FailureReason.NOT_HOME) }
  var note by remember { mutableStateOf("") }
  var retry by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delivery failed") },
    text = {
      Column {
        Text(stop.label.ifBlank { stop.address })
        FailureReason.entries.forEach { option ->
          Text(
            option.label,
            modifier = Modifier.fillMaxWidth().clickable { reason = option }.padding(8.dp),
            color = if (reason == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          )
        }
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(retry, { retry = it }, label = { Text("Retry") }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = { TextButton(onClick = { onFail(reason, note, retry) }) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun MainScreenViewModel.setOriginFromDevice(context: Context) {
  if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
    PackageManager.PERMISSION_GRANTED
  ) return
  val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
  val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
  location?.let { setOrigin(it.latitude, it.longitude) }
}
