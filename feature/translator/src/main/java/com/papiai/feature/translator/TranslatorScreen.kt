package com.papiai.feature.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.PapiStrings
import com.papiai.domain.model.SupportedLanguage

@Composable
fun TranslatorScreen(
  settings: AppSettings,
  state: TranslatorUiState,
  isTargetTtsVerified: Boolean,
  onSourceTextChanged: (String) -> Unit,
  onSelectSourceLang: (SupportedLanguage) -> Unit,
  onSelectTargetLang: (SupportedLanguage) -> Unit,
  onSwapLanguages: () -> Unit,
  onTranslate: () -> Unit,
  onSpeakTarget: () -> Unit,
  onInsertOrCopy: () -> Unit,
  onOpenModels: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val labels = PapiStrings.forLanguage(settings.uiLanguage)

  val requiredModelPresent = when {
    state.sourceLanguage == state.targetLanguage -> true
    state.sourceLanguage == SupportedLanguage.ENGLISH -> state.isEnToIndicInstalled
    state.targetLanguage == SupportedLanguage.ENGLISH -> state.isIndicToEnInstalled
    else -> state.isEnToIndicInstalled && state.isIndicToEnInstalled
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("AI Translator (IndicTrans2 ONNX)", style = MaterialTheme.typography.titleMedium)
              IconButton(
                onClick = onSwapLanguages,
                modifier = Modifier.testTag("translator_swap_button"),
              ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = labels.swapLanguages)
              }
            }

            Text("From", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SupportedLanguage.entries.forEach { lang ->
                FilterChip(
                  selected = state.sourceLanguage == lang,
                  onClick = { onSelectSourceLang(lang) },
                  label = { Text("${lang.englishName} (${lang.nativeName})") },
                  modifier = Modifier.testTag("trans_src_${lang.code}"),
                )
              }
            }

            Text("To", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SupportedLanguage.entries.forEach { lang ->
                FilterChip(
                  selected = state.targetLanguage == lang,
                  onClick = { onSelectTargetLang(lang) },
                  label = { Text("${lang.englishName} (${lang.nativeName})") },
                  modifier = Modifier.testTag("trans_tgt_${lang.code}"),
                )
              }
            }

            if (!requiredModelPresent) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = if (settings.enableOptionalOnlineTranslation && settings.libreTranslateUrl.isNotBlank()) {
                      "Offline IndicTrans2 ONNX model not installed. Will use your configured LibreTranslate server."
                    } else {
                      "Offline IndicTrans2 200M ONNX model (~282 MB, MIT License) is required for offline translation."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  FilledTonalButton(
                    onClick = onOpenModels,
                    modifier = Modifier.testTag("translator_open_models_button"),
                  ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Download in Model Manager")
                  }
                }
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
            OutlinedTextField(
              value = state.sourceText,
              onValueChange = onSourceTextChanged,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("translator_source_input"),
              minLines = 4,
              label = { Text("Source (${state.sourceLanguage.englishName})") },
              placeholder = { Text("Type or paste paragraphs to translate…") },
            )

            Button(
              onClick = onTranslate,
              enabled = !state.isTranslating && state.sourceText.isNotBlank(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("translator_run_button"),
            ) {
              if (state.isTranslating) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  modifier = Modifier.height(18.dp).width(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Running IndicTrans2 ONNX…")
              } else {
                Icon(Icons.Default.Translate, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(labels.translate)
              }
            }
          }
        }
      }

      if (state.errorMessage != null) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          ) {
            Text(
              text = state.errorMessage,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      }

      item {
        Card {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Translation (${state.targetLanguage.englishName})", style = MaterialTheme.typography.titleMedium)
              if (state.engineName.isNotBlank()) {
                Text(state.engineName, style = MaterialTheme.typography.labelSmall)
              }
            }

            OutlinedTextField(
              value = state.translatedText,
              onValueChange = {},
              readOnly = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("translator_output_field"),
              minLines = 4,
              placeholder = { Text("Translated text preserving paragraphs will appear here…") },
            )

            if (state.statusNote != null) {
              Text(
                text = state.statusNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (state.translatedText.isNotBlank() && !isTargetTtsVerified) {
              Text(
                text = "Note: ${state.targetLanguage.englishName} TTS is not verified on your active speech engine.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = onSpeakTarget,
                enabled = state.translatedText.isNotBlank(),
                modifier = Modifier.testTag("translator_speak_button"),
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.play)
              }

              OutlinedButton(
                onClick = {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cb.setPrimaryClip(ClipData.newPlainText("PAPI AI Translation", state.translatedText))
                },
                enabled = state.translatedText.isNotBlank(),
                modifier = Modifier.testTag("translator_copy_button"),
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.copyText)
              }

              OutlinedButton(
                onClick = onInsertOrCopy,
                enabled = state.translatedText.isNotBlank(),
                modifier = Modifier.testTag("translator_insert_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Insert")
              }
            }

            state.insertionFeedback?.let { fb ->
              Text(
                text = fb,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}
