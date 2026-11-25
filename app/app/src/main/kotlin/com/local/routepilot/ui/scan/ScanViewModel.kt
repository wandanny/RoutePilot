package com.local.routepilot.ui.scan

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.local.routepilot.data.AiSettings
import com.local.routepilot.data.ImagePreparer
import com.local.routepilot.data.LabelParser
import com.local.routepilot.data.LucifClient
import com.local.routepilot.data.RouteRepository
import com.local.routepilot.domain.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanPhase { CAMERA, CONFIRM, DUPLICATE, ERROR }

data class ScanUiState(
  val phase: ScanPhase = ScanPhase.CAMERA,
  val name: String = "",
  val address: String = "",
  val postcode: String = "",
  val phone: String = "",
  val trackingId: String = "",
  val rawText: String = "",
  val warnings: List<String> = emptyList(),
  val duplicate: Stop? = null,
  val sessionCount: Int = 0,
  val message: String? = null,
  val busy: Boolean = false,
  val cloudBusy: Boolean = false,
  val cloudCount: Int = 0,
  val showConsent: Boolean = false,
  val openSettings: Boolean = false,
  val keyConfigured: Boolean = false,
)

private data class OcrOutcome(
  val parsed: LabelParser.Result,
  val trackingId: String,
  val duplicate: Stop?,
  val bounds: Rect?,
)

class ScanViewModel(
  private val repository: RouteRepository,
  private val settings: AiSettings,
  private val cloud: LucifClient,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ScanUiState(keyConfigured = settings.apiKey().isNotBlank()))
  val uiState = _uiState.asStateFlow()

  private var capturedBitmap: Bitmap? = null
  private var textBounds: Rect? = null

  fun process(bitmap: Bitmap) {
    releasePhoto()
    capturedBitmap = bitmap
    val canUseCloud = settings.apiKey().isNotBlank()
    if (canUseCloud && !settings.hasConsent()) {
      _uiState.value = _uiState.value.copy(showConsent = true)
    }
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(busy = true)
      if (canUseCloud && settings.hasConsent()) {
        val cloudResult = withContext(Dispatchers.IO) {
          runCatching { cloud.extract(ImagePreparer.prepare(bitmap, null)) }
        }
        if (cloudResult.isSuccess) {
          val fields = cloudResult.getOrThrow()
          _uiState.value = ScanUiState(
            phase = ScanPhase.CONFIRM,
            name = fields.name.orEmpty(),
            address = fields.address.orEmpty(),
            postcode = fields.postcode.orEmpty(),
            sessionCount = _uiState.value.sessionCount,
            cloudCount = _uiState.value.cloudCount + 1,
            keyConfigured = true,
            message = "Scanned with AI",
          )
          return@launch
        }
      }
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val image = InputImage.fromBitmap(bitmap, 0)
          val textResult = com.google.android.gms.tasks.Tasks.await(
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image),
          )
          var bounds: Rect? = null
          textResult.textBlocks.forEach { block ->
            block.boundingBox?.let { rect ->
              bounds = if (bounds == null) Rect(rect) else bounds!!.apply { union(rect) }
            }
          }
          val barcodes = com.google.android.gms.tasks.Tasks.await(BarcodeScanning.getClient().process(image))
          val trackingId = barcodes.firstNotNullOfOrNull { it.rawValue ?: it.displayValue }.orEmpty()
          OcrOutcome(
            parsed = LabelParser.parse(textResult.text),
            trackingId = trackingId,
            duplicate = repository.findByTrackingId(trackingId),
            bounds = bounds,
          )
        }
      }
      textBounds = result.getOrNull()?.bounds
      result.onSuccess { outcome ->
        _uiState.value = ScanUiState(
          phase = if (outcome.duplicate != null) ScanPhase.DUPLICATE else ScanPhase.CONFIRM,
          name = outcome.parsed.name,
          address = outcome.parsed.address,
          postcode = outcome.parsed.postcode,
          phone = outcome.parsed.phone,
          trackingId = outcome.trackingId,
          rawText = outcome.parsed.rawText,
          warnings = outcome.parsed.warnings + if (canUseCloud) listOf("AI unavailable — used on-device OCR") else emptyList(),
          duplicate = outcome.duplicate,
          sessionCount = _uiState.value.sessionCount,
          cloudBusy = false,
          cloudCount = _uiState.value.cloudCount,
          keyConfigured = settings.apiKey().isNotBlank(),
        )
      }.onFailure {
        releasePhoto()
        _uiState.value = ScanUiState(
          phase = ScanPhase.ERROR,
          message = it.message ?: "Could not read the label",
          sessionCount = _uiState.value.sessionCount,
          cloudCount = _uiState.value.cloudCount,
        )
      }
    }
  }

  fun update(field: String, value: String) {
    _uiState.value = when (field) {
      "name" -> _uiState.value.copy(name = value)
      "address" -> _uiState.value.copy(address = value)
      "postcode" -> _uiState.value.copy(postcode = value)
      "phone" -> _uiState.value.copy(phone = value)
      else -> _uiState.value
    }
  }

  fun saveApiKey(value: String) {
    settings.saveApiKey(value)
    _uiState.value = _uiState.value.copy(
      keyConfigured = settings.apiKey().isNotBlank(),
      openSettings = false,
      message = if (settings.apiKey().isNotBlank()) "API key saved" else "API key cleared",
    )
  }

  fun openSettings() {
    _uiState.value = _uiState.value.copy(openSettings = true)
  }

  fun closeSettings() {
    _uiState.value = _uiState.value.copy(openSettings = false)
  }

  fun improveWithAi() {
    when {
      settings.apiKey().isBlank() -> openSettings()
      !settings.hasConsent() -> _uiState.value = _uiState.value.copy(showConsent = true)
      else -> callCloud()
    }
  }

  fun confirmConsent() {
    settings.markConsent()
    _uiState.value = _uiState.value.copy(showConsent = false)
    callCloud()
  }

  fun dismissConsent() {
    _uiState.value = _uiState.value.copy(showConsent = false)
  }

  private fun callCloud() {
    val bitmap = capturedBitmap
    if (bitmap == null || bitmap.isRecycled) {
      _uiState.value = _uiState.value.copy(message = "No photo available for cloud improvement")
      return
    }
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(cloudBusy = true)
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val jpeg = ImagePreparer.prepare(bitmap, textBounds)
          cloud.extract(jpeg)
        }
      }
      result.onSuccess { fields ->
        val current = _uiState.value
        _uiState.value = current.copy(
          name = fields.name ?: current.name,
          address = fields.address ?: current.address,
          postcode = fields.postcode ?: current.postcode,
          cloudBusy = false,
          cloudCount = current.cloudCount + 1,
          message = "Improved with AI",
        )
      }.onFailure {
        _uiState.value = _uiState.value.copy(
          cloudBusy = false,
          message = "AI unavailable: ${it.message ?: "unknown error"}",
        )
      }
    }
  }

  fun save() {
    val current = _uiState.value
    if (current.address.isBlank()) {
      _uiState.value = current.copy(message = "Address is required")
      return
    }
    viewModelScope.launch {
      val route = repository.activeRoute.first()
      if (route == null) {
        _uiState.value = _uiState.value.copy(message = "No active route")
        return@launch
      }
      val fullAddress = if (current.postcode.isNotBlank() && !current.address.contains(current.postcode)) {
        "${current.address}, ${current.postcode}"
      } else {
        current.address
      }
      repository.addStop(
        Stop(
          routeId = route.id,
          address = fullAddress,
          label = current.name,
          recipient = current.name,
          phone = current.phone,
          notes = "",
          priority = 0,
          trackingId = current.trackingId,
        ),
      )
      releasePhoto()
      _uiState.value = ScanUiState(
        phase = ScanPhase.CAMERA,
        sessionCount = current.sessionCount + 1,
        cloudCount = current.cloudCount,
        keyConfigured = settings.apiKey().isNotBlank(),
        message = "Stop added",
      )
    }
  }

  fun backToCamera() {
    releasePhoto()
    _uiState.value = ScanUiState(
      phase = ScanPhase.CAMERA,
      sessionCount = _uiState.value.sessionCount,
      cloudCount = _uiState.value.cloudCount,
      keyConfigured = settings.apiKey().isNotBlank(),
    )
  }

  fun releasePhoto() {
    capturedBitmap?.let { if (!it.isRecycled) it.recycle() }
    capturedBitmap = null
    textBounds = null
  }

  fun consumeMessage() {
    _uiState.value = _uiState.value.copy(message = null)
  }
}
