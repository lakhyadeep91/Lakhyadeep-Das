package com.papiai.feature.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papiai.core.accessibility.AccessibilityBridge
import com.papiai.core.accessibility.ScreenCaptureService
import com.papiai.core.ai.AiModelLifecycleManager
import com.papiai.core.ai.DeviceResourceMonitor
import com.papiai.core.ocr.NormalizedCropRect
import com.papiai.core.ocr.TesseractOcrEngine
import com.papiai.core.speech.PapiTtsEngine
import com.papiai.core.speech.SpeechSegment
import com.papiai.data.local.HistoryRepository
import com.papiai.data.models.ModelDownloadRepository
import com.papiai.data.settings.SettingsRepository
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.DeviceResourceSnapshot
import com.papiai.domain.model.HistoryEntry
import com.papiai.domain.model.HistoryType
import com.papiai.domain.model.ModelInstallStatus
import com.papiai.domain.model.ScreenCaptureSnapshot
import com.papiai.domain.model.ScreenRegionNode
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.TtsEngineInfo
import com.papiai.domain.model.TtsPlaybackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OcrUiState(
  val selectedLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
  val isLanguageModelInstalled: Boolean = false,
  val activeBitmap: Bitmap? = null,
  val cropRect: NormalizedCropRect = NormalizedCropRect(),
  val extractedText: String = "",
  val isProcessing: Boolean = false,
  val processingTimeMs: Long? = null,
  val sourceLabel: String = "",
  val errorMessage: String? = null,
)

data class DashboardSummaryState(
  val settings: AppSettings = AppSettings(),
  val deviceSnapshot: DeviceResourceSnapshot = DeviceResourceSnapshot(0, 0, 0, "arm64-v8a", "14", 34),
  val installedModelsCount: Int = 0,
  val totalModelsCount: Int = 0,
  val isAccessibilityActive: Boolean = false,
  val activeTtsEngine: TtsEngineInfo? = null,
  val recentHistory: List<HistoryEntry> = emptyList(),
)

class ReaderViewModel(
  private val context: Context,
  private val settingsRepository: SettingsRepository,
  private val historyRepository: HistoryRepository,
  private val modelRepository: ModelDownloadRepository,
  private val ocrEngine: TesseractOcrEngine,
  private val ttsEngine: PapiTtsEngine,
  private val resourceMonitor: DeviceResourceMonitor,
  private val lifecycleManager: AiModelLifecycleManager,
) : ViewModel() {

  private val _selectedRegionIds = MutableStateFlow<Set<String>>(emptySet())
  private val _readerLanguage = MutableStateFlow(SupportedLanguage.ENGLISH)
  val readerLanguage: StateFlow<SupportedLanguage> = _readerLanguage.asStateFlow()

  private val _ocrState = MutableStateFlow(OcrUiState())
  val ocrState: StateFlow<OcrUiState> = _ocrState.asStateFlow()

  val ttsProgress: StateFlow<TtsPlaybackProgress> = ttsEngine.progress
  val screenSnapshot: StateFlow<ScreenCaptureSnapshot?> = AccessibilityBridge.latestSnapshot
  val isAccessibilityConnected: StateFlow<Boolean> = AccessibilityBridge.isServiceConnected
  val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    AppSettings(),
  )

  val dashboardState: StateFlow<DashboardSummaryState> = combine(
    settings,
    modelRepository.modelStates,
    AccessibilityBridge.isServiceConnected,
    ttsEngine.activeEngine,
    historyRepository.entries,
  ) { appSettings, models, a11yConnected, ttsInfo, history ->
    val activeSlot = lifecycleManager.activeModelFlow.value
    DashboardSummaryState(
      settings = appSettings,
      deviceSnapshot = resourceMonitor.snapshot(activeSlot),
      installedModelsCount = models.count { it.status == ModelInstallStatus.INSTALLED },
      totalModelsCount = models.size,
      isAccessibilityActive = a11yConnected || AccessibilityBridge.isAccessibilityEnabledInSettings(context),
      activeTtsEngine = ttsInfo,
      recentHistory = history.take(6),
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummaryState())

  private val _regionSelectionOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
  val regionSelectionOverrides: StateFlow<Map<String, Boolean>> = _regionSelectionOverrides.asStateFlow()

  init {
    viewModelScope.launch {
      AccessibilityBridge.capturedScreenBitmap.collect { bmp ->
        if (bmp != null) {
          _ocrState.update {
            it.copy(
              activeBitmap = bmp,
              cropRect = NormalizedCropRect(),
              sourceLabel = "Screen Capture",
              errorMessage = null,
            )
          }
          refreshOcrModelPresence()
        }
      }
    }
    viewModelScope.launch {
      modelRepository.modelStates.collect {
        refreshOcrModelPresence()
      }
    }
    refreshOcrModelPresence()
  }

  fun refreshScreenText() {
    AccessibilityBridge.requestScreenRefresh()
  }

  fun openAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun toggleRegionSelection(regionId: String, currentSelected: Boolean) {
    _regionSelectionOverrides.update { it + (regionId to !currentSelected) }
  }

  fun selectAllRegions(regions: List<ScreenRegionNode>, selected: Boolean) {
    _regionSelectionOverrides.value = regions.associate { it.id to selected }
  }

  fun setReaderLanguage(language: SupportedLanguage) {
    _readerLanguage.value = language
  }

  fun isTtsLanguageVerified(language: SupportedLanguage): Boolean =
    ttsEngine.isLanguageVerified(language)

  fun playScreenRegions(regions: List<ScreenRegionNode>) {
    val overrides = _regionSelectionOverrides.value
    val selected = regions.filter { overrides[it.id] ?: it.isSelected }
    if (selected.isEmpty()) return

    val cfg = settings.value
    ttsEngine.configure(cfg.ttsSpeed, cfg.ttsPitch, cfg.ttsVolume, cfg.preferredTtsVoice)
    val segments = selected.map { SpeechSegment(it.id, it.text) }
    ttsEngine.speakSegments(segments, _readerLanguage.value)

    if (cfg.keepLocalHistory) {
      viewModelScope.launch {
        historyRepository.save(
          HistoryEntry(
            type = HistoryType.SCREEN_READ,
            title = screenSnapshot.value?.windowTitle ?: "Screen Reader",
            content = selected.joinToString("\n") { it.text }.take(1500),
            languageCode = _readerLanguage.value.code,
          )
        )
      }
    }
  }

  fun updatePlaybackSpeed(speed: Float) {
    val cfg = settings.value
    viewModelScope.launch {
      settingsRepository.setTtsParams(speed, cfg.ttsPitch, cfg.ttsVolume)
      ttsEngine.configure(speed, cfg.ttsPitch, cfg.ttsVolume, cfg.preferredTtsVoice)
    }
  }

  fun pauseSpeech() = ttsEngine.pause()
  fun resumeSpeech() = ttsEngine.resume()
  fun stopSpeech() = ttsEngine.stop()
  fun repeatSpeech() = ttsEngine.repeat()

  fun setOcrLanguage(language: SupportedLanguage) {
    _ocrState.update { it.copy(selectedLanguage = language, errorMessage = null) }
    refreshOcrModelPresence()
  }

  fun refreshOcrModelPresence() {
    val lang = _ocrState.value.selectedLanguage
    val installed = ocrEngine.isLanguageDataPresent(lang)
    _ocrState.update { it.copy(isLanguageModelInstalled = installed) }
  }

  fun loadPickedImage(uri: Uri) {
    viewModelScope.launch {
      _ocrState.update { it.copy(isProcessing = true, errorMessage = null) }
      val bmp = withContext(Dispatchers.IO) {
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
          }
        }.getOrNull()
      }
      if (bmp == null) {
        _ocrState.update {
          it.copy(isProcessing = false, errorMessage = "Unable to decode selected image.")
        }
      } else {
        _ocrState.update {
          it.copy(
            isProcessing = false,
            activeBitmap = bmp,
            cropRect = NormalizedCropRect(),
            sourceLabel = "Selected Image",
            errorMessage = null,
          )
        }
      }
    }
  }

  fun startScreenCapture(resultCode: Int, data: Intent) {
    ScreenCaptureService.startSingleCapture(context, resultCode, data)
  }

  fun updateCropRect(crop: NormalizedCropRect) {
    _ocrState.update { it.copy(cropRect = crop) }
  }

  fun updateExtractedText(newText: String) {
    _ocrState.update { it.copy(extractedText = newText) }
  }

  fun runOcr() {
    val cur = _ocrState.value
    val bitmap = cur.activeBitmap
    if (bitmap == null) {
      _ocrState.update { it.copy(errorMessage = "Select an image or capture the screen first.") }
      return
    }
    viewModelScope.launch {
      _ocrState.update { it.copy(isProcessing = true, errorMessage = null) }
      val res = ocrEngine.recognizeBitmap(
        bitmap = bitmap,
        language = cur.selectedLanguage,
        cropRect = cur.cropRect,
        sourceLabel = cur.sourceLabel.ifBlank { "Image" },
      )
      res.fold(
        onSuccess = { ocr ->
          _ocrState.update {
            it.copy(
              isProcessing = false,
              extractedText = ocr.text.ifBlank { "(No text detected in selected region)" },
              processingTimeMs = ocr.processingTimeMs,
              errorMessage = null,
            )
          }
          if (ocr.text.isNotBlank() && settings.value.keepLocalHistory) {
            historyRepository.save(
              HistoryEntry(
                type = HistoryType.OCR,
                title = "OCR (${ocr.language.englishName})",
                content = ocr.text.take(1500),
                languageCode = ocr.language.code,
              )
            )
          }
        },
        onFailure = { err ->
          _ocrState.update {
            it.copy(
              isProcessing = false,
              errorMessage = err.message ?: "OCR failed",
            )
          }
        },
      )
    }
  }

  fun speakOcrText() {
    val text = _ocrState.value.extractedText
    if (text.isBlank()) return
    val cfg = settings.value
    ttsEngine.configure(cfg.ttsSpeed, cfg.ttsPitch, cfg.ttsVolume, cfg.preferredTtsVoice)
    ttsEngine.speakText(text, _ocrState.value.selectedLanguage)
  }
}
