package com.papiai.domain.model

enum class SupportedLanguage(
  val code: String,
  val bcp47: String,
  val tesseractCode: String,
  val indicTransCode: String,
  val englishName: String,
  val nativeName: String,
) {
  ENGLISH(
    code = "en",
    bcp47 = "en-US",
    tesseractCode = "eng",
    indicTransCode = "eng_Latn",
    englishName = "English",
    nativeName = "English",
  ),
  HINDI(
    code = "hi",
    bcp47 = "hi-IN",
    tesseractCode = "hin",
    indicTransCode = "hin_Deva",
    englishName = "Hindi",
    nativeName = "हिन्दी",
  ),
  ODIA(
    code = "or",
    bcp47 = "or-IN",
    tesseractCode = "ori",
    indicTransCode = "ory_Orya",
    englishName = "Odia",
    nativeName = "ଓଡ଼ିଆ",
  );

  companion object {
    fun fromCode(code: String?): SupportedLanguage =
      entries.firstOrNull { it.code == code } ?: ENGLISH
  }
}

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK;

  companion object {
    fun fromName(name: String?): ThemeMode =
      entries.firstOrNull { it.name == name } ?: SYSTEM
  }
}
