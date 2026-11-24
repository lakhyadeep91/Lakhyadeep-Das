package com.papiai.feature.translator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papiai.core.accessibility.AccessibilityBridge
import com.papiai.core.accessibility.TextInsertionOutcome
import com.papiai.core.speech.PapiTtsEngine
import com.papiai.core.translation.IndicTrans2OnnxEngine
import com.papiai.core.translation.OptionalLibreTranslateClient
import com.papiai.data.local.HistoryRepository
import com.papiai.data.models.ModelDownloadRepository
import com.papiai.data.settings.SettingsRepository
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.HistoryEntry
import com.papiai.domain.model.HistoryType
import com.papiai.domain.model.ModelInstallStatus
import com.papiai.domain.model.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranslatorUiState(
  val sourceText: String = "",
  val translatedText: String = "",
  val sourceLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
  val targetLanguage: SupportedLanguage = SupportedLanguage.ODIA,
  val isTranslating: Boolean = false,
  val engineName: String = "",
  val statusNote: String? = null,
  val errorMessage: String? = null,
  val isEnToIndicInstalled: Boolean = false,
  val isIndicToEnInstalled: Boolean = false,
  val insertionFeedback: String? = null,
)

class TranslatorViewModel(
  private val context: Context,
  private val settingsRepository: SettingsRepository,
  private val historyRepository: HistoryRepository,
  private val modelRepository: ModelDownloadRepository,
  private val indicTransEngine: IndicTrans2OnnxEngine,
  private val libreTranslateClient: OptionalLibreTranslateClient,
  private val ttsEngine: PapiTtsEngine,
) : ViewModel() {

  private val _state = MutableStateFlow(TranslatorUiState())
  val state: StateFlow<TranslatorUiState> = _state.asStateFlow()

  val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    AppSettings(),
  )

  init {
    viewModelScope.launch {
      modelRepository.modelStates.collect { models ->
        val enIndic = models.firstOrNull { it.artifact.id == "indictrans2-en-indic" }?.status == ModelInstallStatus.INSTALLED
        val indicEn = models.firstOrNull { it.artifact.id == "indictrans2-indic-en" }?.status == ModelInstallStatus.INSTALLED
        _state.update {
          it.copy(
            isEnToIndicInstalled = enIndic,
            isIndicToEnInstalled = indicEn,
          )
        }
      }
    }
  }

  fun preloadText(text: String, source: SupportedLanguage) {
    val target = if (source == SupportedLanguage.ODIA) SupportedLanguage.ENGLISH else SupportedLanguage.ODIA
    _state.update {
      it.copy(
        sourceText = text,
        sourceLanguage = source,
        targetLanguage = target,
        errorMessage = null,
      )
    }
  }

  fun updateSourceText(text: String) {
    _state.update { it.copy(sourceText = text, errorMessage = null) }
  }

  fun setSourceLanguage(lang: SupportedLanguage) {
    _state.update { cur ->
      val newTarget = if (cur.targetLanguage == lang) {
        if (lang == SupportedLanguage.ENGLISH) SupportedLanguage.ODIA else SupportedLanguage.ENGLISH
      } else {
        cur.targetLanguage
      }
      cur.copy(sourceLanguage = lang, targetLanguage = newTarget, errorMessage = null)
    }
  }

  fun setTargetLanguage(lang: SupportedLanguage) {
    _state.update { cur ->
      val newSource = if (cur.sourceLanguage == lang) {
        if (lang == SupportedLanguage.ENGLISH) SupportedLanguage.HINDI else SupportedLanguage.ENGLISH
      } else {
        cur.sourceLanguage
      }
      cur.copy(sourceLanguage = newSource, targetLanguage = lang, errorMessage = null)
    }
  }

  fun swapLanguages() {
    _state.update { cur ->
      cur.copy(
        sourceLanguage = cur.targetLanguage,
        targetLanguage = cur.sourceLanguage,
        sourceText = cur.translatedText.ifBlank { cur.sourceText },
        translatedText = if (cur.translatedText.isNotBlank()) cur.sourceText else "",
        errorMessage = null,
      )
    }
  }

  fun translate() {
    val cur = _state.value
    if (cur.sourceText.isBlank()) {
      _state.update { it.copy(errorMessage = "Enter or paste text to translate.") }
      return
    }

    viewModelScope.launch {
      _state.update { it.copy(isTranslating = true, errorMessage = null, statusNote = null) }
      val enIndicDir = modelRepository.getInstalledModelDir("indictrans2-en-indic")
      val indicEnDir = modelRepository.getInstalledModelDir("indictrans2-indic-en")

      val offlineResult = indicTransEngine.translate(
        text = cur.sourceText,
        source = cur.sourceLanguage,
        target = cur.targetLanguage,
        enToIndicDir = enIndicDir,
        indicToEnDir = indicEnDir,
      )

      val cfg = settings.value
      val finalResult = if (offlineResult.isFailure &&
        cfg.enableOptionalOnlineTranslation &&
        cfg.libreTranslateUrl.isNotBlank()
      ) {
        libreTranslateClient.translate(
          baseUrl = cfg.libreTranslateUrl,
          text = cur.sourceText,
          source = cur.sourceLanguage,
          target = cur.targetLanguage,
        )
      } else {
        offlineResult
      }

      finalResult.fold(
        onSuccess = { res ->
          _state.update {
            it.copy(
              isTranslating = false,
              translatedText = res.translatedText,
              engineName = res.engineName,
              statusNote = res.statusNote ?: "Completed in ${res.durationMs} ms",
              errorMessage = null,
            )
          }
          if (cfg.keepLocalHistory) {
            historyRepository.save(
              HistoryEntry(
                type = HistoryType.TRANSLATION,
                title = "${res.sourceLanguage.englishName} -> ${res.targetLanguage.englishName}",
                content = res.translatedText.take(1500),
                secondaryContent = cur.sourceText.take(500),
                languageCode = res.targetLanguage.code,
              )
            )
          }
        },
        onFailure = { err ->
          _state.update {
            it.copy(
              isTranslating = false,
              errorMessage = err.message ?: "Translation failed",
            )
          }
        },
      )
    }
  }

  fun speakTarget() {
    val cur = _state.value
    if (cur.translatedText.isBlank()) return
    val cfg = settings.value
    ttsEngine.configure(cfg.ttsSpeed, cfg.ttsPitch, cfg.ttsVolume, cfg.preferredTtsVoice)
    ttsEngine.speakText(cur.translatedText, cur.targetLanguage)
  }

  fun isTargetTtsVerified(): Boolean =
    ttsEngine.isLanguageVerified(_state.value.targetLanguage)

  fun insertOrCopyTranslation() {
    val text = _state.value.translatedText
    if (text.isBlank()) return
    val outcome = AccessibilityBridge.insertTextOrCopyFallback(context, text)
    val msg = when (outcome) {
      is TextInsertionOutcome.InsertedViaAccessibility -> "Inserted into ${outcome.targetPackage}"
      is TextInsertionOutcome.CopiedToClipboardFallback -> "Copied to clipboard (no active non-password input field)"
      is TextInsertionOutcome.BlockedSecureField -> outcome.reason
    }
    _state.update { it.copy(insertionFeedback = msg) }
  }
}
