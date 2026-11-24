package com.papiai.core.speech

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.papiai.domain.model.SpeechPlaybackState
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.TtsEngineInfo
import com.papiai.domain.model.TtsPlaybackProgress
import com.papiai.domain.model.TtsVoiceOption
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpeechSegment(
  val id: String,
  val text: String,
)

class PapiTtsEngine(private val context: Context) {

  private var tts: TextToSpeech? = null
  private var isReady = false

  private var speed = 1.0f
  private var pitch = 1.0f
  private var volume = 1.0f
  private var activeEnginePackage: String? = null
  private var preferredVoiceName: String? = null

  private var queue: List<SpeechSegment> = emptyList()
  private var currentIndex: Int = 0
  private var currentLanguage: SupportedLanguage = SupportedLanguage.ENGLISH

  private val _progress = MutableStateFlow(TtsPlaybackProgress())
  val progress: StateFlow<TtsPlaybackProgress> = _progress.asStateFlow()

  private val _engineInfo = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
  val engineInfo: StateFlow<List<TtsEngineInfo>> = _engineInfo.asStateFlow()

  private val _activeEngine = MutableStateFlow<TtsEngineInfo?>(null)
  val activeEngine: StateFlow<TtsEngineInfo?> = _activeEngine.asStateFlow()

  init {
    initializeEngine(null)
  }

  fun initializeEngine(enginePackage: String?) {
    isReady = false
    runCatching { tts?.shutdown() }
    activeEnginePackage = enginePackage
    tts = if (enginePackage != null) {
      TextToSpeech(context.applicationContext, { status ->
        onInitComplete(status)
      }, enginePackage)
    } else {
      TextToSpeech(context.applicationContext) { status ->
        onInitComplete(status)
      }
    }
  }

  private fun onInitComplete(status: Int) {
    if (status != TextToSpeech.SUCCESS) {
      isReady = false
      return
    }
    isReady = true
    val instance = tts ?: return
    instance.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
          val seg = queue.getOrNull(currentIndex)
          _progress.update {
            it.copy(
              state = SpeechPlaybackState.PLAYING,
              currentRegionId = seg?.id,
              spokenText = seg?.text.orEmpty(),
            )
          }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
          _progress.update {
            it.copy(activeWordStart = start, activeWordEnd = end)
          }
        }

        override fun onDone(utteranceId: String?) {
          if (_progress.value.state == SpeechPlaybackState.PLAYING) {
            if (currentIndex + 1 < queue.size) {
              currentIndex++
              speakCurrentSegment()
            } else {
              _progress.value = TtsPlaybackProgress(state = SpeechPlaybackState.IDLE)
            }
          }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          _progress.value = TtsPlaybackProgress(state = SpeechPlaybackState.IDLE)
        }
      }
    )
    refreshEngineCatalog()
  }

  fun refreshEngineCatalog() {
    val instance = tts ?: return
    val defaultPkg = instance.defaultEngine
    val engines = instance.engines.orEmpty()
    val currentPkg = activeEnginePackage ?: defaultPkg

    val enAvailable = isLocaleSupported(instance, Locale.US)
    val hiAvailable = isLocaleSupported(instance, Locale.forLanguageTag("hi-IN"))
    val orAvailable = isLocaleSupported(instance, Locale.forLanguageTag("or-IN"))

    val voices = runCatching {
      instance.voices.orEmpty().map { v ->
        TtsVoiceOption(
          name = v.name,
          localeTag = v.locale?.toLanguageTag().orEmpty(),
          isNetworkRequired = v.isNetworkConnectionRequired,
        )
      }.sortedBy { it.localeTag }
    }.getOrDefault(emptyList())

    val catalog = engines.map { info ->
      val openSource = isOpenSourcePackage(info.name)
      val provenance = if (openSource) {
        "Verified Open-Source TTS Engine"
      } else {
        "Proprietary System TTS Fallback (${info.label})"
      }
      val isSelected = info.name == currentPkg
      TtsEngineInfo(
        packageName = info.name,
        label = info.label,
        isOpenSource = openSource,
        provenanceNote = provenance,
        isEnglishSupported = if (isSelected) enAvailable else true,
        isHindiSupported = if (isSelected) hiAvailable else false,
        isOdiaSupported = if (isSelected) orAvailable else false,
        voices = if (isSelected) voices else emptyList(),
      )
    }

    _engineInfo.value = catalog
    _activeEngine.value = catalog.firstOrNull { it.packageName == currentPkg } ?: catalog.firstOrNull()
  }

  fun configure(
    speed: Float,
    pitch: Float,
    volume: Float,
    preferredVoice: String? = preferredVoiceName,
  ) {
    this.speed = speed.coerceIn(0.5f, 2.0f)
    this.pitch = pitch.coerceIn(0.5f, 2.0f)
    this.volume = volume.coerceIn(0.1f, 1.0f)
    this.preferredVoiceName = preferredVoice
    tts?.setSpeechRate(this.speed)
    tts?.setPitch(this.pitch)
    if (preferredVoice != null) {
      val match = tts?.voices?.firstOrNull { it.name == preferredVoice }
      if (match != null) {
        tts?.voice = match
      }
    }
  }

  fun isLanguageVerified(language: SupportedLanguage): Boolean {
    val instance = tts ?: return false
    return isLocaleSupported(instance, Locale.forLanguageTag(language.bcp47))
  }

  fun speakSegments(segments: List<SpeechSegment>, language: SupportedLanguage) {
    val nonEmpty = segments.filter { it.text.isNotBlank() }
    if (nonEmpty.isEmpty() || !isReady) return
    queue = nonEmpty
    currentIndex = 0
    currentLanguage = language
    _progress.value = TtsPlaybackProgress(state = SpeechPlaybackState.PLAYING)
    speakCurrentSegment()
  }

  fun speakText(text: String, language: SupportedLanguage) {
    speakSegments(listOf(SpeechSegment("single", text)), language)
  }

  fun pause() {
    if (_progress.value.state == SpeechPlaybackState.PLAYING) {
      tts?.stop()
      val seg = queue.getOrNull(currentIndex)
      _progress.value = TtsPlaybackProgress(
        state = SpeechPlaybackState.PAUSED,
        currentRegionId = seg?.id,
        spokenText = seg?.text.orEmpty(),
      )
    }
  }

  fun resume() {
    if (_progress.value.state == SpeechPlaybackState.PAUSED && queue.isNotEmpty()) {
      _progress.update { it.copy(state = SpeechPlaybackState.PLAYING) }
      speakCurrentSegment()
    }
  }

  fun repeat() {
    if (queue.isNotEmpty()) {
      currentIndex = 0
      _progress.update { it.copy(state = SpeechPlaybackState.PLAYING) }
      speakCurrentSegment()
    }
  }

  fun stop() {
    tts?.stop()
    _progress.value = TtsPlaybackProgress(state = SpeechPlaybackState.IDLE)
  }

  fun release() {
    runCatching { tts?.stop() }
    runCatching { tts?.shutdown() }
    tts = null
    isReady = false
  }

  private fun speakCurrentSegment() {
    val instance = tts ?: return
    val seg = queue.getOrNull(currentIndex) ?: return

    val targetLocale = Locale.forLanguageTag(currentLanguage.bcp47)
    if (isLocaleSupported(instance, targetLocale)) {
      instance.language = targetLocale
    }
    instance.setSpeechRate(speed)
    instance.setPitch(pitch)

    val params = Bundle().apply {
      putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
      putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY)
    }

    instance.speak(seg.text, TextToSpeech.QUEUE_FLUSH, params, "papi_${UUID.randomUUID()}")
  }

  private fun isLocaleSupported(instance: TextToSpeech, locale: Locale): Boolean {
    val code = runCatching { instance.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
    return code >= TextToSpeech.LANG_AVAILABLE
  }

  private fun isOpenSourcePackage(pkg: String): Boolean {
    val lower = pkg.lowercase()
    return lower.contains("espeak") ||
      lower.contains("rhvoice") ||
      lower.contains("piper") ||
      lower.contains("sherpa") ||
      lower.contains("flite")
  }
}
