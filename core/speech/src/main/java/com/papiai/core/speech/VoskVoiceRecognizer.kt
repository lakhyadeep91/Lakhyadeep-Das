package com.papiai.core.speech

import com.papiai.core.ai.AiModelLifecycleManager
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.VoiceSessionState
import com.papiai.domain.model.VoiceStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

class VoskVoiceRecognizer(
  private val lifecycleManager: AiModelLifecycleManager,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var model: Model? = null
  private var loadedLanguage: SupportedLanguage? = null
  private var speechService: SpeechService? = null

  private val _state = MutableStateFlow(VoiceSessionState())
  val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

  private val recognitionListener = object : RecognitionListener {
    override fun onPartialResult(hypothesis: String?) {
      val partial = extractField(hypothesis, "partial")
      if (partial.isNotBlank()) {
        _state.update { it.copy(partialText = partial) }
      }
    }

    override fun onResult(hypothesis: String?) {
      val text = extractField(hypothesis, "text")
      if (text.isNotBlank()) {
        _state.update { cur ->
          val joined = if (cur.transcriptText.isBlank()) text else "${cur.transcriptText} $text"
          cur.copy(partialText = "", transcriptText = joined)
        }
      }
    }

    override fun onFinalResult(hypothesis: String?) {
      val text = extractField(hypothesis, "text")
      _state.update { cur ->
        val joined = when {
          text.isBlank() -> cur.transcriptText
          cur.transcriptText.isBlank() -> text
          else -> "${cur.transcriptText} $text"
        }
        cur.copy(
          status = VoiceStatus.IDLE,
          partialText = "",
          transcriptText = joined,
          isForegroundMicActive = false,
        )
      }
    }

    override fun onError(exception: Exception?) {
      _state.update {
        it.copy(
          status = VoiceStatus.ERROR,
          isForegroundMicActive = false,
          errorMessage = exception?.message ?: "Microphone recognition error",
        )
      }
    }

    override fun onTimeout() {
      stopListening()
    }
  }

  fun startListening(
    language: SupportedLanguage,
    modelDir: File?,
  ) {
    if (language == SupportedLanguage.ODIA) {
      _state.update {
        it.copy(
          status = VoiceStatus.ERROR,
          activeLanguage = language,
          isForegroundMicActive = false,
          errorMessage = "Odia offline speech recognition has no verified Vosk small model. Use English or Hindi offline voice typing, or type/OCR Odia directly.",
        )
      }
      return
    }

    if (modelDir == null || !modelDir.exists()) {
      _state.update {
        it.copy(
          status = VoiceStatus.ERROR,
          activeLanguage = language,
          isForegroundMicActive = false,
          errorMessage = "Offline ${language.englishName} Vosk ASR model is not installed. Download it in Model Manager (~40 MB).",
        )
      }
      return
    }

    scope.launch {
      val modelId = "vosk-${language.code}"
      val acquired = lifecycleManager.acquireModelSlot(modelId, minRequiredRamMb = 350) {
        releaseInternal()
      }
      if (!acquired) {
        _state.update {
          it.copy(
            status = VoiceStatus.ERROR,
            isForegroundMicActive = false,
            errorMessage = "Insufficient free RAM (need >= 350 MB) to start Vosk ASR.",
          )
        }
        return@launch
      }

      try {
        _state.update {
          it.copy(
            status = VoiceStatus.PROCESSING,
            activeLanguage = language,
            errorMessage = null,
          )
        }

        if (model == null || loadedLanguage != language) {
          releaseInternal()
          model = Model(modelDir.absolutePath)
          loadedLanguage = language
        }

        val recognizer = Recognizer(model, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f).also {
          it.startListening(recognitionListener)
        }

        _state.update {
          it.copy(
            status = VoiceStatus.LISTENING,
            activeLanguage = language,
            isForegroundMicActive = true,
            engineLabel = "Vosk Offline ASR (${language.englishName})",
            errorMessage = null,
          )
        }
      } catch (t: Throwable) {
        releaseInternal()
        _state.update {
          it.copy(
            status = VoiceStatus.ERROR,
            isForegroundMicActive = false,
            errorMessage = t.message ?: "Failed to start microphone recognition",
          )
        }
      }
    }
  }

  fun stopListening() {
    runCatching { speechService?.stop() }
    runCatching { speechService?.shutdown() }
    speechService = null
    _state.update {
      it.copy(
        status = VoiceStatus.IDLE,
        isForegroundMicActive = false,
      )
    }
  }

  fun updateTranscript(newText: String) {
    _state.update { it.copy(transcriptText = newText) }
  }

  fun clearTranscript() {
    _state.update { it.copy(partialText = "", transcriptText = "", errorMessage = null) }
  }

  fun releaseInternal() {
    stopListening()
    runCatching { model?.close() }
    model = null
    loadedLanguage = null
  }

  private fun extractField(json: String?, key: String): String {
    if (json.isNullOrBlank()) return ""
    return runCatching { JSONObject(json).optString(key, "").trim() }.getOrDefault("")
  }
}
