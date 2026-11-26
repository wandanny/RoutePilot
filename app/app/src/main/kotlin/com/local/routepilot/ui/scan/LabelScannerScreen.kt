package com.local.routepilot.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.androidx.compose.koinViewModel

@Composable
fun LabelScannerScreen(onClose: () -> Unit) {
  val context = LocalContext.current
  val viewModel: ScanViewModel = koinViewModel()
  val state by viewModel.uiState.collectAsState(ScanUiState())
  val sourceLabel by viewModel.sourceLabel.collectAsState("")
  var cameraGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
    )
  }
  var showCameraRationale by remember { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    cameraGranted = it
    if (!it) showCameraRationale = true
  }
  val libraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let { viewModel.processLibraryImage(context, it) }
  }
  val reduceMotion = remember {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
  }

  LaunchedEffect(Unit) {
    if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
  }
  LaunchedEffect(state.message) {
    state.message?.let {
      Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
      viewModel.consumeMessage()
    }
  }

  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Scan label", style = MaterialTheme.typography.headlineSmall)
      TextButton(
        onClick = { viewModel.releasePhoto(); onClose() },
        modifier = Modifier.semantics { contentDescription = "Close scanner" },
      ) { Text("Close") }
    }
    if (sourceLabel.isNotBlank()) {
      AssistChip(
        onClick = {},
        label = { Text(sourceLabel) },
        modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = "Label read by $sourceLabel" },
      )
    }

    when (state.phase) {
      ScanPhase.CAMERA -> {
        if (cameraGranted) {
          CameraView(
            sessionCount = state.sessionCount,
            onCapture = { viewModel.process(it) },
            onPickFromLibrary = { libraryLauncher.launch("image/*") },
          )
        } else {
          Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Camera permission is needed to read parcel labels.")
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier.semantics { contentDescription = "Grant camera permission" },
            ) { Text("Grant camera") }
          }
        }
      }

      ScanPhase.CONFIRM -> ConfirmScreen(state, viewModel)
      ScanPhase.DUPLICATE -> DuplicateScreen(state, viewModel)
      ScanPhase.ERROR -> ErrorScreen(state, viewModel)
    }

    if (state.busy || state.cloudBusy) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (reduceMotion) {
          Text("Reading label…", modifier = Modifier.semantics { contentDescription = "Reading label" })
        } else {
          CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Reading label" })
        }
      }
    }
  }

  if (state.showConsent) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissConsent() },
      title = { Text("Send label to AI?") },
      text = {
        Text(
          "AI scanning is now the default. The label image goes to lucif.ai; addresses may be sent to " +
            "Nominatim for geocoding and OSRM for route optimisation. Your on-device result stays available " +
            "if the call fails.",
        )
      },
      confirmButton = { TextButton(onClick = { viewModel.confirmConsent() }) { Text("Send label") } },
      dismissButton = { TextButton(onClick = { viewModel.dismissConsent() }) { Text("Cancel") } },
    )
  }

  if (state.openSettings) {
    AiSettingsDialog(
      configured = state.keyConfigured,
      onDismiss = { viewModel.closeSettings() },
      onSave = { viewModel.saveApiKey(it) },
    )
  }

  if (showCameraRationale) {
    AlertDialog(
      onDismissRequest = { showCameraRationale = false },
      title = { Text("Camera access") },
      text = { Text("RoutePilot uses the camera to photograph parcel labels. You can also pick a label photo from your gallery.") },
      confirmButton = {
        TextButton(onClick = {
          showCameraRationale = false
          permissionLauncher.launch(Manifest.permission.CAMERA)
        }) { Text("Grant camera") }
      },
      dismissButton = { TextButton(onClick = { showCameraRationale = false }) { Text("Not now") } },
    )
  }
}

@Composable
private fun AiSettingsDialog(configured: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var key by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("AI settings") },
    text = {
      Column {
        Text("Paste your lucif.ai API key. It is stored encrypted on this device and never shipped in the app.")
        if (configured) {
          Text("A key is already saved — leaving this blank keeps it.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
          key,
          { key = it },
          label = { Text("API key") },
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun CameraView(sessionCount: Int, onCapture: (Bitmap) -> Unit, onPickFromLibrary: () -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val controller = remember {
    LifecycleCameraController(context.applicationContext).apply {
      setEnabledUseCases(CameraController.IMAGE_CAPTURE)
    }
  }
  var torchOn by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxSize()) {
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).apply {
          this.controller = controller
          controller.bindToLifecycle(lifecycleOwner)
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      Modifier
        .matchParentSize()
        .padding(horizontal = 32.dp, vertical = 160.dp)
        .border(2.dp, Color.White, RoundedCornerShape(20.dp))
        .semantics { contentDescription = "Label guide frame" },
    )

    Column(
      Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Added this session: $sessionCount", color = Color.White)
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
          onClick = {
            torchOn = !torchOn
            runCatching { controller.enableTorch(torchOn) }
          },
          modifier = Modifier.semantics { contentDescription = "Toggle torch" },
        ) { Text(if (torchOn) "Torch off" else "Torch on", color = Color.White) }
        Button(
          onClick = {
            controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
              override fun onCaptureSuccess(image: ImageProxy) {
                try {
                  val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                  val bitmap = Bitmap.createBitmap(image.toBitmap(), 0, 0, image.width, image.height, matrix, true)
                  onCapture(bitmap)
                } finally {
                  image.close()
                }
              }

              override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
              }
            })
          },
          modifier = Modifier.semantics { contentDescription = "Capture label photo" },
        ) { Text("Capture") }
        OutlinedButton(
          onClick = onPickFromLibrary,
          modifier = Modifier.semantics { contentDescription = "Choose label photo from gallery" },
        ) { Text("From gallery", color = Color.White) }
      }
    }
  }
}

@Composable
private fun ConfirmScreen(state: ScanUiState, viewModel: ScanViewModel) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Text("Confirm delivery", style = MaterialTheme.typography.headlineSmall)
    if (state.warnings.isNotEmpty()) {
      Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
          "Check these fields: ${state.warnings.joinToString("; ")}",
          modifier = Modifier.padding(12.dp),
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
    OutlinedTextField(state.name, { viewModel.update("name", it) }, label = { Text("Recipient") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.address, { viewModel.update("address", it) }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.postcode, { viewModel.update("postcode", it) }, label = { Text("Postcode") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.phone, { viewModel.update("phone", it) }, label = { Text("Phone (if available)") }, modifier = Modifier.fillMaxWidth())

    if (state.warnings.isNotEmpty()) {
      Text("Recognised text", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
      Card(Modifier.fillMaxWidth()) {
        Text(state.rawText, modifier = Modifier.padding(12.dp))
      }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { viewModel.save() }, modifier = Modifier.weight(1f)) { Text("Save stop") }
      OutlinedButton(onClick = { viewModel.backToCamera() }, modifier = Modifier.weight(1f)) { Text("Retake") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = { viewModel.improveWithAi() },
        enabled = !state.cloudBusy,
        modifier = Modifier.weight(1f),
      ) { Text(if (state.keyConfigured) "Improve with AI" else "Set AI key") }
      OutlinedButton(onClick = { viewModel.openSettings() }, modifier = Modifier.weight(1f)) { Text("AI settings") }
    }
    Text(
      "AI improvements this session: ${state.cloudCount}",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

@Composable
private fun DuplicateScreen(state: ScanUiState, viewModel: ScanViewModel) {
  AlertDialog(
    onDismissRequest = { viewModel.backToCamera() },
    title = { Text("Duplicate tracking ID") },
    text = {
      Text("This parcel was already added${state.trackingId.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}: " +
        "${state.duplicate?.label ?: state.duplicate?.address ?: "existing stop"}")
    },
    confirmButton = { TextButton(onClick = { viewModel.save() }) { Text("Add anyway") } },
    dismissButton = { TextButton(onClick = { viewModel.backToCamera() }) { Text("Skip") } },
  )
}

@Composable
private fun ErrorScreen(state: ScanUiState, viewModel: ScanViewModel) {
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
    Text("Could not read the label: ${state.message}", color = MaterialTheme.colorScheme.error)
    Button(onClick = { viewModel.backToCamera() }) { Text("Try again") }
  }
}
