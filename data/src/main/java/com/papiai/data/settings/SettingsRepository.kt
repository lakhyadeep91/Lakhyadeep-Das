package com.papiai.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.papiSettingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "papi_settings")

class SettingsRepository(private val context: Context) {

  private object Keys {
    val UI_LANGUAGE = stringPreferencesKey("ui_language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TTS_SPEED = floatPreferencesKey("tts_speed")
    val TTS_PITCH = floatPreferencesKey("tts_pitch")
    val TTS_VOLUME = floatPreferencesKey("tts_volume")
    val PREFERRED_TTS_ENGINE = stringPreferencesKey("preferred_tts_engine")
    val PREFERRED_TTS_VOICE = stringPreferencesKey("preferred_tts_voice")
    val ENABLE_ONLINE_TRANSLATION = booleanPreferencesKey("enable_online_translation")
    val LIBRETRANSLATE_URL = stringPreferencesKey("libretranslate_url")
    val KEEP_LOCAL_HISTORY = booleanPreferencesKey("keep_local_history")
  }

  val settings: Flow<AppSettings> = context.papiSettingsDataStore.data.map { prefs ->
    AppSettings(
      uiLanguage = SupportedLanguage.fromCode(prefs[Keys.UI_LANGUAGE]),
      themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
      ttsSpeed = prefs[Keys.TTS_SPEED] ?: 1.0f,
      ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
      ttsVolume = prefs[Keys.TTS_VOLUME] ?: 1.0f,
      preferredTtsEngine = prefs[Keys.PREFERRED_TTS_ENGINE],
      preferredTtsVoice = prefs[Keys.PREFERRED_TTS_VOICE],
      enableOptionalOnlineTranslation = prefs[Keys.ENABLE_ONLINE_TRANSLATION] ?: false,
      libreTranslateUrl = prefs[Keys.LIBRETRANSLATE_URL] ?: "",
      keepLocalHistory = prefs[Keys.KEEP_LOCAL_HISTORY] ?: true,
    )
  }

  suspend fun setUiLanguage(language: SupportedLanguage) {
    context.papiSettingsDataStore.edit { it[Keys.UI_LANGUAGE] = language.code }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.papiSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setTtsParams(speed: Float, pitch: Float, volume: Float) {
    context.papiSettingsDataStore.edit {
      it[Keys.TTS_SPEED] = speed.coerceIn(0.5f, 2.0f)
      it[Keys.TTS_PITCH] = pitch.coerceIn(0.5f, 2.0f)
      it[Keys.TTS_VOLUME] = volume.coerceIn(0.1f, 1.0f)
    }
  }

  suspend fun setPreferredTts(enginePackage: String?, voiceName: String?) {
    context.papiSettingsDataStore.edit {
      if (enginePackage != null) it[Keys.PREFERRED_TTS_ENGINE] = enginePackage else it.remove(Keys.PREFERRED_TTS_ENGINE)
      if (voiceName != null) it[Keys.PREFERRED_TTS_VOICE] = voiceName else it.remove(Keys.PREFERRED_TTS_VOICE)
    }
  }

  suspend fun setOptionalOnlineTranslation(enabled: Boolean, libreTranslateUrl: String) {
    context.papiSettingsDataStore.edit {
      it[Keys.ENABLE_ONLINE_TRANSLATION] = enabled
      it[Keys.LIBRETRANSLATE_URL] = libreTranslateUrl.trim()
    }
  }

  suspend fun setKeepLocalHistory(enabled: Boolean) {
    context.papiSettingsDataStore.edit { it[Keys.KEEP_LOCAL_HISTORY] = enabled }
  }

  suspend fun resetDefaults() {
    context.papiSettingsDataStore.edit { it.clear() }
  }
}
