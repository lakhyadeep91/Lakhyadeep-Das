package com.papiai.domain.model

enum class ModelCategory {
  OCR,
  TRANSLATION,
  SPEECH_RECOGNITION,
}

enum class ModelInstallStatus {
  NOT_INSTALLED,
  DOWNLOADING,
  PAUSED,
  VERIFYING,
  INSTALLED,
  ERROR,
}

data class ModelFileSpec(
  val relativePath: String,
  val downloadUrl: String,
  val expectedBytes: Long,
  val expectedSha256: String? = null,
)

data class ModelArtifact(
  val id: String,
  val name: String,
  val category: ModelCategory,
  val languages: List<SupportedLanguage>,
  val version: String,
  val totalBytes: Long,
  val minFreeRamMb: Int,
  val minAndroidSdk: Int,
  val licenseName: String,
  val licenseUrl: String,
  val sourceName: String,
  val sourceUrl: String,
  val files: List<ModelFileSpec>,
  val isZipArchive: Boolean = false,
  val extractDirName: String? = null,
  val compatibilityNote: String,
)

data class ModelRuntimeState(
  val artifact: ModelArtifact,
  val status: ModelInstallStatus,
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = artifact.totalBytes,
  val verifiedSha256: String? = null,
  val errorMessage: String? = null,
  val localDirPath: String? = null,
  val hasEnoughRam: Boolean = true,
  val hasEnoughStorage: Boolean = true,
) {
  val progressFraction: Float
    get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
