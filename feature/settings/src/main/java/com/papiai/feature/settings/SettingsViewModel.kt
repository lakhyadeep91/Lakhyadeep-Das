package com.papiai.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papiai.core.accessibility.AccessibilityBridge
import com.papiai.core.ai.AiModelLifecycleManager
import com.papiai.core.speech.PapiTtsEngine
import com.papiai.data.local.HistoryRepository
import com.papiai.data.models.ModelDownloadRepository
import com.papiai.data.settings.SettingsRepository
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.ThemeMode
import com.papiai.domain.model.TtsEngineInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val context: Context,
  private val settingsRepository: SettingsRepository,
  private val historyRepository: HistoryRepository,
  private val modelRepository: ModelDownloadRepository,
  private val ttsEngine: PapiTtsEngine,
  private val lifecycleManager: AiModelLifecycleManager,
) : ViewModel() {

  val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    AppSettings(),
  )

  val ttsEngines: StateFlow<List<TtsEngineInfo>> = ttsEngine.engineInfo
  val activeTtsEngine: StateFlow<TtsEngineInfo?> = ttsEngine.activeEngine
  val isAccessibilityConnected: StateFlow<Boolean> = AccessibilityBridge.isServiceConnected

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  fun setUiLanguage(language: SupportedLanguage) {
    viewModelScope.launch { settingsRepository.setUiLanguage(language) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsRepository.setThemeMode(mode) }
  }

  fun setTtsParams(speed: Float, pitch: Float, volume: Float) {
    viewModelScope.launch {
      settingsRepository.setTtsParams(speed, pitch, volume)
      ttsEngine.configure(speed, pitch, volume, settings.value.preferredTtsVoice)
    }
  }

  fun selectTtsEngine(packageName: String) {
    viewModelScope.launch {
      settingsRepository.setPreferredTts(packageName, null)
      ttsEngine.initializeEngine(packageName)
    }
  }

  fun selectTtsVoice(voiceName: String) {
    viewModelScope.launch {
      val cur = settings.value
      settingsRepository.setPreferredTts(cur.preferredTtsEngine, voiceName)
      ttsEngine.configure(cur.ttsSpeed, cur.ttsPitch, cur.ttsVolume, voiceName)
    }
  }

  fun testTtsSample() {
    val cur = settings.value
    ttsEngine.configure(cur.ttsSpeed, cur.ttsPitch, cur.ttsVolume, cur.preferredTtsVoice)
    val sample = when (cur.uiLanguage) {
      SupportedLanguage.ENGLISH -> "PAPI AI local text to speech is ready."
      SupportedLanguage.HINDI -> "पापी एआई स्थानीय टेक्स्ट टू स्पीच तैयार है।"
      SupportedLanguage.ODIA -> "ପାପି ଏଆଇ ସ୍ଥାନୀୟ ଟେକ୍ସଟ୍ ଟୁ ସ୍ପିଚ୍ ପ୍ରସ୍ତୁତ।"
    }
    ttsEngine.speakText(sample, cur.uiLanguage)
  }

  fun setOnlineTranslation(enabled: Boolean, url: String) {
    viewModelScope.launch {
      settingsRepository.setOptionalOnlineTranslation(enabled, url)
    }
  }

  fun setKeepLocalHistory(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.setKeepLocalHistory(enabled)
    }
  }

  fun clearAllLocalHistory() {
    viewModelScope.launch {
      historyRepository.clearAll()
      _statusMessage.value = "All local history cleared."
    }
  }

  fun deleteAllDownloadedModels() {
    viewModelScope.launch {
      lifecycleManager.releaseAll()
      modelRepository.deleteAllModels()
      _statusMessage.value = "All downloaded models deleted from device storage."
    }
  }

  fun openAccessibilitySettings() {
    context.startActivity(
      Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }

  fun openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
      putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
