package com.papiai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.ThemeMode
import com.papiai.domain.model.TtsEngineInfo
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: AppSettings,
  ttsEngines: List<TtsEngineInfo>,
  activeTtsEngine: TtsEngineInfo?,
  onSelectUiLanguage: (SupportedLanguage) -> Unit,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onTtsParamsChange: (Float, Float, Float) -> Unit,
  onSelectTtsEngine: (String) -> Unit,
  onSelectTtsVoice: (String) -> Unit,
  onTestVoice: () -> Unit,
  onOnlineTranslationChanged: (Boolean, String) -> Unit,
  onKeepHistoryChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showNoticesDialog by remember { mutableStateOf(false) }
  var libreUrlDraft by remember(settings.libreTranslateUrl) { mutableStateOf(settings.libreTranslateUrl) }

  if (showNoticesDialog) {
    AlertDialog(
      onDismissRequest = { showNoticesDialog = false },
      title = { Text("Open-Source Notices & Licenses") },
      text = {
        Text(
          "• PAPI AI Application Code: GNU GPL v3.0 / Apache-2.0\n" +
            "• Tesseract4Android 4.9.0 & tessdata_fast: Apache-2.0\n" +
            "• Microsoft ONNX Runtime Android 1.22.0: MIT License\n" +
            "• AI4Bharat IndicTrans2 200M: MIT License\n" +
            "• Alpha Cephei Vosk Android 0.3.75 & Small Models: Apache-2.0\n" +
            "• AndroidX Jetpack, Compose, Room, DataStore: Apache-2.0\n" +
            "• OkHttp & Coil: Apache-2.0\n\n" +
            "No proprietary analytics, ads, Firebase, or mandatory cloud APIs are included."
        )
      },
      confirmButton = {
        TextButton(onClick = { showNoticesDialog = false }) { Text("Close") }
      },
    )
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Card {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("Interface Language", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SupportedLanguage.entries.forEach { lang ->
                FilterChip(
                  selected = settings.uiLanguage == lang,
                  onClick = { onSelectUiLanguage(lang) },
                  label = { Text("${lang.nativeName} (${lang.englishName})") },
                  modifier = Modifier.testTag("settings_lang_${lang.code}"),
                )
              }
            }

            Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ThemeMode.entries.forEach { mode ->
                FilterChip(
                  selected = settings.themeMode == mode,
                  onClick = { onSelectThemeMode(mode) },
                  label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                  modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}"),
                )
              }
            }
          }
        }
      }

      item {
        Card {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Text-to-Speech & Engine Provenance", style = MaterialTheme.typography.titleMedium)
            }

            if (activeTtsEngine != null) {
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (activeTtsEngine.isOpenSource) {
                    MaterialTheme.colorScheme.tertiaryContainer
                  } else {
                    MaterialTheme.colorScheme.secondaryContainer
                  }
                )
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(activeTtsEngine.provenanceNote, style = MaterialTheme.typography.labelLarge)
                  Text(
                    text = "Verified languages — English: ${yesNo(activeTtsEngine.isEnglishSupported)}, Hindi: ${yesNo(activeTtsEngine.isHindiSupported)}, Odia: ${yesNo(activeTtsEngine.isOdiaSupported)}",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            }

            if (ttsEngines.isNotEmpty()) {
              Text("Installed Android TTS Engines", style = MaterialTheme.typography.labelLarge)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ttsEngines.forEach { engine ->
                  FilterChip(
                    selected = activeTtsEngine?.packageName == engine.packageName,
                    onClick = { onSelectTtsEngine(engine.packageName) },
                    label = {
                      Text(
                        "${engine.label} (${if (engine.isOpenSource) "FOSS" else "System/Proprietary"})"
                      )
                    },
                  )
                }
              }
            }

            val voices = activeTtsEngine?.voices.orEmpty().take(8)
            if (voices.isNotEmpty()) {
              Text("Voice Selection", style = MaterialTheme.typography.labelLarge)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                voices.forEach { v ->
                  FilterChip(
                    selected = settings.preferredTtsVoice == v.name,
                    onClick = { onSelectTtsVoice(v.name) },
                    label = { Text("${v.localeTag} • ${v.name.takeLast(12)}") },
                  )
                }
              }
            }

            Text("Speech Speed: ${(settings.ttsSpeed * 10).roundToInt() / 10f}x", style = MaterialTheme.typography.labelLarge)
            Slider(
              value = settings.ttsSpeed,
              onValueChange = { onTtsParamsChange(it, settings.ttsPitch, settings.ttsVolume) },
              valueRange = 0.5f..2.0f,
            )

            Text("Pitch: ${(settings.ttsPitch * 10).roundToInt() / 10f}x", style = MaterialTheme.typography.labelLarge)
            Slider(
              value = settings.ttsPitch,
              onValueChange = { onTtsParamsChange(settings.ttsSpeed, it, settings.ttsVolume) },
              valueRange = 0.5f..2.0f,
            )

            Text("Volume: ${(settings.ttsVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(
              value = settings.ttsVolume,
              onValueChange = { onTtsParamsChange(settings.ttsSpeed, settings.ttsPitch, it) },
              valueRange = 0.1f..1.0f,
            )

            Button(
              onClick = onTestVoice,
              modifier = Modifier.testTag("settings_test_tts_button"),
            ) {
              Icon(Icons.Default.PlayArrow, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("Test TTS Output")
            }
          }
        }
      }

      item {
        Card {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("Optional Self-Hosted Online Translation", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Disabled by default. Core features work 100% offline with downloaded IndicTrans2 models. Optionally point to your own open-source LibreTranslate instance.",
              style = MaterialTheme.typography.bodyMedium,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Enable Optional LibreTranslate Fallback", style = MaterialTheme.typography.labelLarge)
              Switch(
                checked = settings.enableOptionalOnlineTranslation,
                onCheckedChange = { onOnlineTranslationChanged(it, libreUrlDraft) },
                modifier = Modifier.testTag("settings_online_translate_switch"),
              )
            }
            if (settings.enableOptionalOnlineTranslation) {
              OutlinedTextField(
                value = libreUrlDraft,
                onValueChange = { libreUrlDraft = it },
                label = { Text("Self-Hosted LibreTranslate URL") },
                placeholder = { Text("https://libretranslate.example.org") },
                modifier = Modifier.fillMaxWidth(),
              )
              FilledTonalButton(
                onClick = { onOnlineTranslationChanged(true, libreUrlDraft) },
              ) {
                Text("Save Endpoint")
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Save Local History on Device", style = MaterialTheme.typography.labelLarge)
              Switch(
                checked = settings.keepLocalHistory,
                onCheckedChange = onKeepHistoryChanged,
                modifier = Modifier.testTag("settings_keep_history_switch"),
              )
            }
          }
        }
      }

      item {
        OutlinedButton(
          onClick = { showNoticesDialog = true },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_licenses_button"),
        ) {
          Icon(Icons.Default.Gavel, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("View Third-Party & Model Licenses")
        }
      }
    }
  }
}

private fun yesNo(flag: Boolean): String = if (flag) "Yes" else "No"
