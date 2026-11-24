package com.papiai.domain.model

enum class HistoryType {
  SCREEN_READ,
  OCR,
  TRANSLATION,
  VOICE,
}

data class HistoryEntry(
  val id: Long = 0L,
  val type: HistoryType,
  val title: String,
  val content: String,
  val secondaryContent: String? = null,
  val languageCode: String,
  val timestampMs: Long = System.currentTimeMillis(),
)

data class OcrResult(
  val text: String,
  val language: SupportedLanguage,
  val processingTimeMs: Long,
  val sourceLabel: String,
)

data class TranslationResult(
  val translatedText: String,
  val sourceLanguage: SupportedLanguage,
  val targetLanguage: SupportedLanguage,
  val engineName: String,
  val isOffline: Boolean,
  val durationMs: Long,
  val statusNote: String? = null,
)

enum class VoiceStatus {
  IDLE,
  LISTENING,
  PROCESSING,
  ERROR,
}

data class VoiceSessionState(
  val status: VoiceStatus = VoiceStatus.IDLE,
  val partialText: String = "",
  val transcriptText: String = "",
  val activeLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
  val isForegroundMicActive: Boolean = false,
  val engineLabel: String = "Vosk Offline ASR",
  val errorMessage: String? = null,
)

data class AppSettings(
  val uiLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val ttsSpeed: Float = 1.0f,
  val ttsPitch: Float = 1.0f,
  val ttsVolume: Float = 1.0f,
  val preferredTtsEngine: String? = null,
  val preferredTtsVoice: String? = null,
  val enableOptionalOnlineTranslation: Boolean = false,
  val libreTranslateUrl: String = "",
  val keepLocalHistory: Boolean = true,
)

data class DeviceResourceSnapshot(
  val totalRamMb: Long,
  val availableRamMb: Long,
  val freeStorageMb: Long,
  val architecture: String,
  val androidRelease: String,
  val sdkInt: Int,
  val activeLoadedModelId: String? = null,
)
