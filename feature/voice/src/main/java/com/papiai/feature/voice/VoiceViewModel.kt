package com.papiai.feature.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papiai.core.accessibility.AccessibilityBridge
import com.papiai.core.accessibility.TextInsertionOutcome
import com.papiai.core.accessibility.VoiceForegroundService
import com.papiai.core.speech.VoskVoiceRecognizer
import com.papiai.data.local.HistoryRepository
import com.papiai.data.models.ModelDownloadRepository
import com.papiai.data.settings.SettingsRepository
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.HistoryEntry
import com.papiai.domain.model.HistoryType
import com.papiai.domain.model.ModelInstallStatus
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.VoiceSessionState
import com.papiai.domain.model.VoiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceViewModel(
  private val context: Context,
  private val settingsRepository: SettingsRepository,
  private val historyRepository: HistoryRepository,
  private val modelRepository: ModelDownloadRepository,
  private val voskRecognizer: VoskVoiceRecognizer,
) : ViewModel() {

  val voiceState: StateFlow<VoiceSessionState> = voskRecognizer.state
  val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    AppSettings(),
  )

  private val _selectedLanguage = MutableStateFlow(SupportedLanguage.ENGLISH)
  val selectedLanguage: StateFlow<SupportedLanguage> = _selectedLanguage.asStateFlow()

  private val _isSelectedModelInstalled = MutableStateFlow(false)
  val isSelectedModelInstalled: StateFlow<Boolean> = _isSelectedModelInstalled.asStateFlow()

  private val _insertionFeedback = MutableStateFlow<String?>(null)
  val insertionFeedback: StateFlow<String?> = _insertionFeedback.asStateFlow()

  init {
    viewModelScope.launch {
      modelRepository.modelStates.collect { models ->
        updateModelAvailability(_selectedLanguage.value, models.associate { it.artifact.id to it.status })
      }
    }
    viewModelScope.launch {
      AccessibilityBridge.stopVoiceRequests.collect {
        stopListening()
      }
    }
  }

  fun selectLanguage(lang: SupportedLanguage) {
    _selectedLanguage.value = lang
    val map = modelRepository.modelStates.value.associate { it.artifact.id to it.status }
    updateModelAvailability(lang, map)
  }

  private fun updateModelAvailability(lang: SupportedLanguage, statusMap: Map<String, ModelInstallStatus>) {
    val installed = when (lang) {
      SupportedLanguage.ENGLISH -> statusMap["vosk-en"] == ModelInstallStatus.INSTALLED
      SupportedLanguage.HINDI -> statusMap["vosk-hi"] == ModelInstallStatus.INSTALLED
      SupportedLanguage.ODIA -> false
    }
    _isSelectedModelInstalled.value = installed
  }

  fun startListening() {
    val lang = _selectedLanguage.value
    viewModelScope.launch {
      val dir = modelRepository.getVoskModelDir(lang)
      if (dir != null && lang != SupportedLanguage.ODIA) {
        VoiceForegroundService.start(context, lang.englishName)
      }
      voskRecognizer.startListening(lang, dir)
    }
  }

  fun stopListening() {
    voskRecognizer.stopListening()
    VoiceForegroundService.stop(context)
    val text = voiceState.value.transcriptText
    if (text.isNotBlank() && settings.value.keepLocalHistory) {
      viewModelScope.launch {
        historyRepository.save(
          HistoryEntry(
            type = HistoryType.VOICE,
            title = "Voice (${_selectedLanguage.value.englishName})",
            content = text.take(1500),
            languageCode = _selectedLanguage.value.code,
          )
        )
      }
    }
  }

  fun toggleListening() {
    if (voiceState.value.status == VoiceStatus.LISTENING) {
      stopListening()
    } else {
      startListening()
    }
  }

  fun updateTranscript(text: String) {
    voskRecognizer.updateTranscript(text)
  }

  fun clearTranscript() {
    voskRecognizer.clearTranscript()
    _insertionFeedback.value = null
  }

  fun insertOrCopyTranscript() {
    val text = voiceState.value.transcriptText
    if (text.isBlank()) return
    val outcome = AccessibilityBridge.insertTextOrCopyFallback(context, text)
    _insertionFeedback.value = when (outcome) {
      is TextInsertionOutcome.InsertedViaAccessibility -> "Inserted into ${outcome.targetPackage}"
      is TextInsertionOutcome.CopiedToClipboardFallback -> "Copied to clipboard (password fields & protected screens are never accessed)"
      is TextInsertionOutcome.BlockedSecureField -> outcome.reason
    }
  }
}
