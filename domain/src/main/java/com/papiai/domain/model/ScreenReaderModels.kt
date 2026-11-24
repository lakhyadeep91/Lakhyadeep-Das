package com.papiai.domain.model

data class ScreenRegionNode(
  val id: String,
  val text: String,
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
  val packageName: String,
  val className: String,
  val isEditable: Boolean = false,
  val isSelected: Boolean = true,
)

data class ScreenCaptureSnapshot(
  val timestampMs: Long,
  val packageName: String,
  val windowTitle: String,
  val regions: List<ScreenRegionNode>,
  val skippedSecureNodes: Int = 0,
  val isSecureWindowBlocked: Boolean = false,
)

enum class SpeechPlaybackState {
  IDLE,
  PLAYING,
  PAUSED,
}

data class TtsVoiceOption(
  val name: String,
  val localeTag: String,
  val isNetworkRequired: Boolean,
)

data class TtsEngineInfo(
  val packageName: String,
  val label: String,
  val isOpenSource: Boolean,
  val provenanceNote: String,
  val isEnglishSupported: Boolean,
  val isHindiSupported: Boolean,
  val isOdiaSupported: Boolean,
  val voices: List<TtsVoiceOption> = emptyList(),
)

data class TtsPlaybackProgress(
  val state: SpeechPlaybackState = SpeechPlaybackState.IDLE,
  val currentRegionId: String? = null,
  val activeWordStart: Int = -1,
  val activeWordEnd: Int = -1,
  val spokenText: String = "",
)
