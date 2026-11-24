package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.papiai.core.ai.AiModelLifecycleManager
import com.papiai.core.ai.DeviceResourceMonitor
import com.papiai.core.ocr.TesseractOcrEngine
import com.papiai.core.speech.PapiTtsEngine
import com.papiai.core.speech.VoskVoiceRecognizer
import com.papiai.core.translation.IndicTrans2OnnxEngine
import com.papiai.core.translation.OptionalLibreTranslateClient
import com.papiai.data.local.HistoryRepository
import com.papiai.data.local.PapiDataProvider
import com.papiai.data.models.ModelDownloadRepository
import com.papiai.data.settings.SettingsRepository
import com.papiai.feature.models.ModelManagerViewModel
import com.papiai.feature.reader.ReaderViewModel
import com.papiai.feature.settings.SettingsViewModel
import com.papiai.feature.translator.TranslatorViewModel
import com.papiai.feature.voice.VoiceViewModel

class PapiAppContainer(context: Context) {
  private val appContext = context.applicationContext

  val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
  val historyRepository: HistoryRepository by lazy {
    PapiDataProvider.createHistoryRepository(appContext)
  }
  val modelRepository: ModelDownloadRepository by lazy {
    PapiDataProvider.createModelRepository(appContext)
  }

  val resourceMonitor: DeviceResourceMonitor by lazy { DeviceResourceMonitor(appContext) }
  val lifecycleManager: AiModelLifecycleManager by lazy {
    AiModelLifecycleManager(appContext, resourceMonitor)
  }

  val ocrEngine: TesseractOcrEngine by lazy {
    TesseractOcrEngine(modelRepository.tesseractRootDir, lifecycleManager)
  }

  val indicTransEngine: IndicTrans2OnnxEngine by lazy {
    IndicTrans2OnnxEngine(lifecycleManager)
  }

  val libreTranslateClient: OptionalLibreTranslateClient by lazy {
    OptionalLibreTranslateClient()
  }

  val ttsEngine: PapiTtsEngine by lazy { PapiTtsEngine(appContext) }

  val voskRecognizer: VoskVoiceRecognizer by lazy {
    VoskVoiceRecognizer(lifecycleManager)
  }

  val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return when {
        modelClass.isAssignableFrom(ReaderViewModel::class.java) -> ReaderViewModel(
          context = appContext,
          settingsRepository = settingsRepository,
          historyRepository = historyRepository,
          modelRepository = modelRepository,
          ocrEngine = ocrEngine,
          ttsEngine = ttsEngine,
          resourceMonitor = resourceMonitor,
          lifecycleManager = lifecycleManager,
        ) as T

        modelClass.isAssignableFrom(TranslatorViewModel::class.java) -> TranslatorViewModel(
          context = appContext,
          settingsRepository = settingsRepository,
          historyRepository = historyRepository,
          modelRepository = modelRepository,
          indicTransEngine = indicTransEngine,
          libreTranslateClient = libreTranslateClient,
          ttsEngine = ttsEngine,
        ) as T

        modelClass.isAssignableFrom(VoiceViewModel::class.java) -> VoiceViewModel(
          context = appContext,
          settingsRepository = settingsRepository,
          historyRepository = historyRepository,
          modelRepository = modelRepository,
          voskRecognizer = voskRecognizer,
        ) as T

        modelClass.isAssignableFrom(ModelManagerViewModel::class.java) -> ModelManagerViewModel(
          modelRepository = modelRepository,
          settingsRepository = settingsRepository,
          resourceMonitor = resourceMonitor,
          lifecycleManager = lifecycleManager,
        ) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
          context = appContext,
          settingsRepository = settingsRepository,
          historyRepository = historyRepository,
          modelRepository = modelRepository,
          ttsEngine = ttsEngine,
          lifecycleManager = lifecycleManager,
        ) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
      }
    }
  }
}
