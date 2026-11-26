package com.local.routepilot.ui.main

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.local.routepilot.data.AiSettings
import com.local.routepilot.data.LocalAddressIndex
import com.local.routepilot.data.TomTomClient
import com.local.routepilot.domain.FailureReason
import com.local.routepilot.domain.RouteInfo
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import com.local.routepilot.ui.map.NavigationScreen
import com.local.routepilot.ui.scan.LabelScannerScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private enum class StopFilter { ALL, PENDING, DONE, FAILED }

@Composable
fun MainScreen(viewModel: MainScreenViewModel = koinViewModel()) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsState(RoutePilotUiState())
  val batchIndex by viewModel.batchIndex.collectAsState(0)
  val sourceLabel by viewModel.sourceLabel.collectAsState("")
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val reduceMotion = rememberReducedMotion()
  var tab by remember { mutableStateOf(0) }
  var filter by remember { mutableStateOf(StopFilter.ALL) }
  var showAdd by remember { mutableStateOf(false) }
  var failTarget by remember { mutableStateOf<Stop?>(null) }
  var detailStop by remember { mutableStateOf<Stop?>(null) }
  var showScanner by remember { mutableStateOf(false) }
  var mapFullscreen by remember { mutableStateOf(false) }
  var mapFocusStopId by remember { mutableStateOf<Long?>(null) }
  var showResetConfirm by remember { mutableStateOf(false) }
  var showOptions by remember { mutableStateOf(false) }
  var showUnlockConfirm by remember { mutableStateOf(false) }
  var showSummary by remember { mutableStateOf(false) }
  var showAiSettings by remember { mutableStateOf(false) }
  var showBatch by remember { mutableStateOf(false) }

  LaunchedEffect(context) { viewModel.attachContext(context) }

  val readText = { uri: Uri ->
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
    if (uri != null) scope.launch {
      val routeId = state.activeRoute?.id ?: return@launch
      val text = viewModel.routeCsv(routeId)
      context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
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

  val isLocked = state.activeRoute?.locked == true
  val pendingCount = state.stops.count { it.status == StopStatus.PENDING }
  val doneCount = state.stops.count { it.status == StopStatus.DONE }
  val failedCount = state.stops.count { it.status == StopStatus.FAILED }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    bottomBar = {
      NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf("Route" to "R", "Scan" to "S", "Map" to "M", "Archive" to "A")
        items.forEachIndexed { index, item ->
          NavigationBarItem(
            selected = tab == index,
            onClick = { tab = index },
            icon = { Text(item.second, style = MaterialTheme.typography.labelMedium) },
            label = { Text(item.first, style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.semantics { contentDescription = "${item.first} tab" },
          )
        }
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      when (tab) {
        0 -> RouteTab(
          state = state,
          filter = filter,
          reduceMotion = reduceMotion,
          onFilter = { filter = it },
          onOpenOptions = { showOptions = true },
          onScan = { showScanner = true },
          onNavigateAll = {
            if (!isLocked) scope.launch { viewModel.prepareNavigation() }
            mapFocusStopId = null
            tab = 2
            mapFullscreen = true
          },
          onImport = { importCsv.launch(arrayOf("text/*", "application/*")) },
          onAdd = { showAdd = true },
          onOptimize = {
            if (isLocked) showUnlockConfirm = true else viewModel.optimizeNow()
          },
          onMaps = { showBatch = true },
          onUndo = { viewModel.undo() },
          onComplete = { viewModel.complete(it.id) },
          onFail = { failTarget = it },
          onDetail = { detailStop = it },
        )
        1 -> ScanTab(
          sourceLabel = sourceLabel,
          onOpen = { showScanner = true },
        )
        2 -> NavigationScreen(
          fullscreen = false,
          onToggleFullscreen = { mapFullscreen = true },
          focusStopId = mapFocusStopId,
          onComplete = { viewModel.complete(it.id) },
          onFail = { failTarget = it },
          onNavigate = { viewModel.navigate(context, it) },
        )
        3 -> ArchiveList(state.routes)
      }
    }
  }

  if (showUnlockConfirm) {
    AlertDialog(
      onDismissRequest = { showUnlockConfirm = false },
      title = { Text("Route order is locked") },
      text = { Text("Unlocking lets RoutePilot re-order the remaining stops.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.unlockAndOptimize()
            showUnlockConfirm = false
          },
        ) { Text("Unlock and re-plan") }
      },
      dismissButton = { TextButton(onClick = { showUnlockConfirm = false }) { Text("Cancel") } },
    )
  }

  if (showOptions) {
    OptionsDialog(
      context = context,
      pendingCount = pendingCount,
      doneCount = doneCount,
      failedCount = failedCount,
      isLocked = isLocked,
      onDismiss = { showOptions = false },
      onSummary = { showOptions = false; showSummary = true },
      onBackup = { showOptions = false; backup.launch("routepilot-backup.json") },
      onRestore = { showOptions = false; restore.launch(arrayOf("application/json")) },
      onExportCsv = { showOptions = false; csvExport.launch("route.csv") },
      onAiSettings = { showOptions = false; showAiSettings = true },
      onMaps = { showOptions = false; showBatch = true },
      onReset = { showOptions = false; showResetConfirm = true },
      onToggleLock = { viewModel.setRouteLocked(it) },
      onClearGeocodeCache = { viewModel.clearGeocodeCache() },
    )
  }

  if (showSummary) {
    Dialog(onDismissRequest = { showSummary = false }) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.background,
      ) {
        SummaryScreen(stops = state.stops, onClose = { showSummary = false })
      }
    }
  }

  if (showAiSettings) {
    AiSettingsDialog(context = context, onDismiss = { showAiSettings = false })
  }

  if (showBatch) {
    BatchSheet(
      batches = viewModel.mapBatches,
      batchIndex = batchIndex,
      isLocked = isLocked,
      onOpen = { viewModel.openBatch(it) },
      onNext = { viewModel.nextBatch() },
      onClose = { showBatch = false },
    )
  }

  if (showResetConfirm) {
    AlertDialog(
      onDismissRequest = { showResetConfirm = false },
      title = { Text("Reset route?") },
      text = {
        Text(
          "Pending: $pendingCount · Done: $doneCount · Failed: $failedCount. " +
            "The current route will be archived and a new empty route started. " +
            "The order lock is released.",
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

  detailStop?.let { stop ->
    StopDetailDialog(
      stop = stop,
      isLocked = isLocked,
      onDismiss = { detailStop = null },
      onReorder = { delta ->
        viewModel.move(stop.id, delta)
        detailStop = null
      },
      onPin = {
        viewModel.pin(stop.id)
        detailStop = null
      },
      onSaveWindow = { start, end ->
        viewModel.saveWindow(stop.id, start, end)
        detailStop = null
      },
      onDone = {
        viewModel.complete(stop.id)
        detailStop = null
      },
      onFail = {
        failTarget = stop
        detailStop = null
      },
      onNavigate = {
        detailStop = null
        mapFocusStopId = stop.id
        tab = 2
        mapFullscreen = true
      },
    )
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
private fun rememberReducedMotion(): Boolean {
  val context = LocalContext.current
  return remember(context) {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
  }
}

@Composable
private fun RouteTab(
  state: RoutePilotUiState,
  filter: StopFilter,
  reduceMotion: Boolean,
  onFilter: (StopFilter) -> Unit,
  onOpenOptions: () -> Unit,
  onScan: () -> Unit,
  onNavigateAll: () -> Unit,
  onImport: () -> Unit,
  onAdd: () -> Unit,
  onOptimize: () -> Unit,
  onMaps: () -> Unit,
  onUndo: () -> Unit,
  onComplete: (Stop) -> Unit,
  onFail: (Stop) -> Unit,
  onDetail: (Stop) -> Unit,
) {
  val isLocked = state.activeRoute?.locked == true
  val pending = state.stops.count { it.status == StopStatus.PENDING }
  val done = state.stops.count { it.status == StopStatus.DONE }
  val total = state.stops.size
  val next = state.stops.firstOrNull { it.status == StopStatus.PENDING }
  LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    item {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("RoutePilot", style = MaterialTheme.typography.displayLarge)
          if (isLocked) {
            Surface(
              shape = RoundedCornerShape(50),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 8.dp),
            ) {
              Text(
                "Locked",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }
        }
        TextButton(
          onClick = onOpenOptions,
          modifier = Modifier.semantics { contentDescription = "Route options" },
        ) { Text("Options") }
      }
    }
    item {
      Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(Modifier.padding(20.dp)) {
          Text("Today's progress", style = MaterialTheme.typography.titleMedium)
          Text("$done of $total stops", style = MaterialTheme.typography.displayLarge)
          if (state.plan.busy) {
            if (reduceMotion) {
              Text("Optimising route…", style = MaterialTheme.typography.labelMedium)
            } else {
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("  Optimising route…", style = MaterialTheme.typography.labelMedium)
              }
            }
          } else if (state.plan.roadOptimized) {
            Text(
              "%.1f km · %.0f min · road optimised".format(state.plan.distanceKm, state.plan.durationMin),
              style = MaterialTheme.typography.labelMedium,
            )
          }
          if (next != null) {
            Text(
              "Next: ${next.label.ifBlank { next.address }}",
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(top = 12.dp),
            )
            Text(next.address, style = MaterialTheme.typography.bodyMedium)
          } else {
            Text("No pending stops", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
          }
        }
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = onScan,
          modifier = Modifier.weight(1f).semantics { contentDescription = "Scan label" },
        ) { Text("Scan label") }
        Button(
          onClick = onNavigateAll,
          modifier = Modifier.weight(1f).semantics { contentDescription = "Navigate all stops" },
        ) { Text("Navigate all") }
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import CSV") }
        OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Add stop") }
        OutlinedButton(onClick = onOptimize, modifier = Modifier.weight(1f)) { Text("Optimize now") }
      }
    }
    item {
      Button(
        onClick = onMaps,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open in Google Maps" },
      ) { Text("Open in Google Maps") }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        TextButton(onClick = onUndo, modifier = Modifier.semantics { contentDescription = "Undo last completion" }) {
          Text("Undo")
        }
        Text("$pending pending · $done done", style = MaterialTheme.typography.labelMedium)
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = filter == StopFilter.ALL,
          onClick = { onFilter(StopFilter.ALL) },
          label = { Text("All") },
          modifier = Modifier.semantics { contentDescription = "Filter all stops" },
        )
        FilterChip(
          selected = filter == StopFilter.PENDING,
          onClick = { onFilter(StopFilter.PENDING) },
          label = { Text("Pending") },
          modifier = Modifier.semantics { contentDescription = "Filter pending stops" },
        )
        FilterChip(
          selected = filter == StopFilter.DONE,
          onClick = { onFilter(StopFilter.DONE) },
          label = { Text("Done") },
          modifier = Modifier.semantics { contentDescription = "Filter done stops" },
        )
        FilterChip(
          selected = filter == StopFilter.FAILED,
          onClick = { onFilter(StopFilter.FAILED) },
          label = { Text("Failed") },
          leadingIcon = { Text("!") },
          modifier = Modifier.semantics { contentDescription = "Filter failed stops" },
        )
      }
    }
    val visible = state.stops.filter { stop ->
      when (filter) {
        StopFilter.ALL -> true
        StopFilter.PENDING -> stop.status == StopStatus.PENDING
        StopFilter.DONE -> stop.status == StopStatus.DONE
        StopFilter.FAILED -> stop.status == StopStatus.FAILED
      }
    }
    if (visible.isEmpty()) {
      item { EmptyState() }
    } else {
      items(visible, key = { it.id }) { stop ->
        StopCard(
          stop = stop,
          sequence = stop.orderIndex + 1,
          onComplete = { onComplete(stop) },
          onFail = { onFail(stop) },
          onDetail = { onDetail(stop) },
        )
      }
    }
  }
}

@Composable
private fun EmptyState() {
  Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(Modifier.padding(20.dp)) {
      Text("No stops yet", style = MaterialTheme.typography.titleLarge)
      Text(
        "Import a CSV or scan a label to get started. Stops are saved on this device.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun StopCard(
  stop: Stop,
  sequence: Int,
  onComplete: () -> Unit,
  onFail: () -> Unit,
  onDetail: () -> Unit,
) {
  val title = stop.label.ifBlank { stop.address }
  Card(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onDetail)
      .semantics(mergeDescendants = true) {
        contentDescription = "Stop $sequence, $title, ${stop.status.name}"
      },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(sequence.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
          Text(title, style = MaterialTheme.typography.titleMedium)
          Text(stop.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      MiniMap(hasLocation = stop.latitude != null && stop.longitude != null)
      if (stop.latitude == null || stop.longitude == null) {
        Text(
          "Address not found — tap to edit.",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
        )
      } else if (stop.estimatedCoordinates) {
        Text(
          "Coordinates estimated by AI — check the address",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StatusPill(stop.status)
        if (stop.status == StopStatus.PENDING) {
          TextButton(
            onClick = onComplete,
            modifier = Modifier.semantics { contentDescription = "Mark stop $sequence done" },
          ) { Text("Done") }
        }
      }
    }
  }
}

@Composable
private fun MiniMap(hasLocation: Boolean) {
  val shape = RoundedCornerShape(12.dp)
  if (!hasLocation) {
    Box(
      Modifier
        .fillMaxWidth()
        .height(88.dp)
        .padding(top = 12.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        "Location not found",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
  Canvas(
    Modifier
      .fillMaxWidth()
      .height(88.dp)
      .padding(top = 12.dp)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .semantics { contentDescription = "Map preview with one pin" },
  ) {
    val gridStep = 22.dp.toPx()
    var x = gridStep
    while (x < size.width) {
      drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
      x += gridStep
    }
    var y = gridStep
    while (y < size.height) {
      drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
      y += gridStep
    }
    val road = Color(0xFFD6C5B8)
    drawLine(road, Offset(0f, size.height * 0.68f), Offset(size.width, size.height * 0.44f), strokeWidth = 10.dp.toPx())
    drawLine(road, Offset(size.width * 0.28f, 0f), Offset(size.width * 0.46f, size.height), strokeWidth = 8.dp.toPx())
    val pin = Offset(size.width * 0.62f, size.height * 0.4f)
    drawCircle(Color(0xFFC2410C), 12.dp.toPx(), pin)
    drawCircle(Color.White, 4.dp.toPx(), pin)
  }
}

@Composable
private fun StatusPill(status: StopStatus) {
  val label = when (status) {
    StopStatus.PENDING -> "○ Pending"
    StopStatus.DONE -> "✓ Done"
    StopStatus.FAILED -> "× Failed"
  }
  val color = when (status) {
    StopStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    StopStatus.DONE -> Color(0xFF2E7D32)
    StopStatus.FAILED -> MaterialTheme.colorScheme.error
  }
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.semantics { contentDescription = label },
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
  }
}

@Composable
private fun ScanTab(sourceLabel: String, onOpen: () -> Unit) {
  Column(
    Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("Scan the next label", style = MaterialTheme.typography.titleLarge)
    Text("AI reads the address; you confirm it before saving.", style = MaterialTheme.typography.bodyMedium)
    if (sourceLabel.isNotBlank()) {
      Text("Last read: $sourceLabel", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    }
    Button(
      onClick = onOpen,
      modifier = Modifier.padding(top = 16.dp).semantics { contentDescription = "Open scanner" },
    ) { Text("Open scanner") }
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
private fun OptionsDialog(
  context: Context,
  pendingCount: Int,
  doneCount: Int,
  failedCount: Int,
  isLocked: Boolean,
  onDismiss: () -> Unit,
  onSummary: () -> Unit,
  onBackup: () -> Unit,
  onRestore: () -> Unit,
  onExportCsv: () -> Unit,
  onAiSettings: () -> Unit,
  onMaps: () -> Unit,
  onReset: () -> Unit,
  onToggleLock: (Boolean) -> Unit,
  onClearGeocodeCache: () -> Unit,
) {
  var mapsSection by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (mapsSection) "Maps settings" else "Route options") },
    text = {
      if (mapsSection) {
        MapsSettingsPanel(context = context, onClearGeocodeCache = onClearGeocodeCache)
      } else {
        Column {
          Text(
            "Pending $pendingCount · Done $doneCount · Failed $failedCount",
            style = MaterialTheme.typography.labelMedium,
          )
          OptionsRow("Summary", onSummary)
          OptionsRow("Open in Google Maps", onMaps)
          OptionsRow("Backup", onBackup)
          OptionsRow("Restore", onRestore)
          OptionsRow("Export CSV", onExportCsv)
          OptionsRow("AI settings", onAiSettings)
          OptionsRow("Maps settings") { mapsSection = true }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Lock route order", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
              checked = isLocked,
              onCheckedChange = onToggleLock,
              modifier = Modifier.semantics { contentDescription = "Lock route order" },
            )
          }
          if (isLocked) {
            Text(
              "Order locked. Optimize now will ask before changing it.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          OptionsRow("Reset route", onReset)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { if (mapsSection) mapsSection = false else onDismiss() },
      ) { Text(if (mapsSection) "Back" else "Close") }
    },
  )
}

@Composable
private fun OptionsRow(title: String, onClick: () -> Unit) {
  TextButton(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
  ) { Text(title) }
}

@Composable
private fun MapsSettingsPanel(context: Context, onClearGeocodeCache: () -> Unit) {
  val settings = remember(context) { AiSettings(context) }
  val index = remember(context) { LocalAddressIndex(context) }
  var key by remember { mutableStateOf(settings.tomtomApiKey()) }
  var restrict by remember { mutableStateOf(settings.restrictArea()) }
  var status by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val hasKey = remember(key) { TomTomClient(settings).hasKey() }
  Column {
    OutlinedTextField(
      value = key,
      onValueChange = { key = it },
      label = { Text("TomTom API key") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      "Free key from developer.tomtom.com. Stored on this device and used only when the local index has no match. " +
        "Leave blank to stay offline-first.",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      if (hasKey) "TomTom key active" else "No TomTom key — local index and Nominatim only.",
      style = MaterialTheme.typography.labelSmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = {
          settings.saveTomtomApiKey(key)
          status = if (key.isBlank()) "TomTom key cleared" else "TomTom key saved"
        },
        modifier = Modifier.semantics { contentDescription = "Save TomTom API key" },
      ) { Text("Save key") }
      TextButton(onClick = { key = ""; settings.saveTomtomApiKey(""); status = "TomTom key cleared" }) {
        Text("Clear key")
      }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Restrict stops to current area",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
      Switch(
        checked = restrict,
        onCheckedChange = { restrict = it; settings.saveRestrictArea(it) },
        modifier = Modifier.semantics { contentDescription = "Restrict stops to current area" },
      )
    }
    Text(
      "Rejects locations more than 50 km from your current position. Leave off for test labels from other cities.",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      "${index.count} local addresses for Taiping, Kamunting and Simpang.",
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 8.dp),
    )
    Button(
      onClick = {
        scope.launch {
          index.refreshIfNeeded(true)
          status = "Address index checked."
        }
      },
      modifier = Modifier.semantics { contentDescription = "Check for address index updates" },
    ) { Text("Check for updates") }
    TextButton(
      onClick = { onClearGeocodeCache(); status = "Geocode cache cleared." },
      modifier = Modifier.semantics { contentDescription = "Clear geocode cache" },
    ) { Text("Clear geocode cache") }
    if (status.isNotBlank()) {
      Text(status, style = MaterialTheme.typography.labelSmall)
    }
    Text(
      "Map data © OpenStreetMap contributors (ODbL); postcodes © MCMC / GeoNames (CC BY 4.0); " +
        "POIs © Overture (CDLA Permissive 2.0)",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

@Composable
private fun AiSettingsDialog(context: Context, onDismiss: () -> Unit) {
  val settings = remember(context) { AiSettings(context) }
  var key by remember { mutableStateOf(settings.apiKey()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("AI settings") },
    text = {
      Column {
        OutlinedTextField(
          value = key,
          onValueChange = { key = it },
          label = { Text("lucif.ai API key") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "Stored encrypted on this device. Leave blank to use on-device reading only.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { settings.saveApiKey(key); onDismiss() }) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun StopDetailDialog(
  stop: Stop,
  isLocked: Boolean,
  onDismiss: () -> Unit,
  onReorder: (Int) -> Unit,
  onPin: () -> Unit,
  onSaveWindow: (String, String) -> Unit,
  onDone: () -> Unit,
  onFail: () -> Unit,
  onNavigate: () -> Unit,
) {
  var start by remember { mutableStateOf(stop.windowStart) }
  var end by remember { mutableStateOf(stop.windowEnd) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stop.label.ifBlank { stop.address }) },
    text = {
      Column {
        Text(stop.address, style = MaterialTheme.typography.bodyMedium)
        StatusPill(stop.status)
        OutlinedTextField(
          value = start,
          onValueChange = { start = it },
          label = { Text("Window start (HH:mm)") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = end,
          onValueChange = { end = it },
          label = { Text("Window end (HH:mm)") },
          modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onSaveWindow(start, end) }, modifier = Modifier.semantics { contentDescription = "Save window changes" }) {
          Text("Save changes")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = onNavigate, modifier = Modifier.semantics { contentDescription = "Navigate to stop" }) { Text("Navigate") }
          TextButton(onClick = onDone, modifier = Modifier.semantics { contentDescription = "Mark stop done" }) { Text("Done") }
          TextButton(onClick = onFail, modifier = Modifier.semantics { contentDescription = "Mark stop failed" }) { Text("Failed") }
        }
        if (isLocked) {
          Text(
            "Unlock route order to pin or reorder this stop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          TextButton(onClick = onPin, modifier = Modifier.semantics { contentDescription = if (stop.pinned) "Unpin stop" else "Pin stop" }) {
            Text(if (stop.pinned) "Unpin stop" else "Pin stop")
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onReorder(-1) }, modifier = Modifier.semantics { contentDescription = "Move stop up" }) { Text("↑ Move up") }
            TextButton(onClick = { onReorder(1) }, modifier = Modifier.semantics { contentDescription = "Move stop down" }) { Text("↓ Move down") }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
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
