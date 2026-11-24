package com.papiai.feature.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.PapiStrings
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.VoiceSessionState
import com.papiai.domain.model.VoiceStatus

@Composable
fun VoiceTypingScreen(
  settings: AppSettings,
  state: VoiceSessionState,
  selectedLanguage: SupportedLanguage,
  isSelectedModelInstalled: Boolean,
  insertionFeedback: String?,
  onSelectLanguage: (SupportedLanguage) -> Unit,
  onStartListening: () -> Unit,
  onStopListening: () -> Unit,
  onToggleListening: () -> Unit,
  onTranscriptEdited: (String) -> Unit,
  onClearTranscript: () -> Unit,
  onInsertOrCopy: () -> Unit,
  onSendToTranslator: (String, SupportedLanguage) -> Unit,
  onOpenModels: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val labels = PapiStrings.forLanguage(settings.uiLanguage)
  var showMicRationale by remember { mutableStateOf(false) }

  val permissionsToRequest = remember {
    buildList {
      add(Manifest.permission.RECORD_AUDIO)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }.toTypedArray()
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    if (grants[Manifest.permission.RECORD_AUDIO] == true) {
      onToggleListening()
    }
  }

  fun hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  if (showMicRationale) {
    AlertDialog(
      onDismissRequest = { showMicRationale = false },
      title = { Text("Microphone Permission Explanation") },
      text = {
        Text(
          "PAPI AI requests microphone access only when you tap Push-to-Talk or Start Listening. " +
            "Audio is processed 100% locally by the offline Vosk engine, never uploaded, and a persistent notification is always displayed while the microphone is active."
        )
      },
      confirmButton = {
        Button(onClick = {
          showMicRationale = false
          permissionLauncher.launch(permissionsToRequest)
        }) {
          Text("Grant Microphone")
        }
      },
      dismissButton = {
        TextButton(onClick = { showMicRationale = false }) {
          Text("Cancel")
        }
      },
    )
  }

  val isListening = state.status == VoiceStatus.LISTENING
  val pulseTransition = rememberInfiniteTransition(label = "micPulse")
  val pulseScale by pulseTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = if (isListening) 1.18f else 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(700),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "micPulseScale",
  )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (isListening || state.isForegroundMicActive) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(Icons.Default.Mic, contentDescription = null)
              Text(
                text = "Microphone Active (${state.engineLabel}) — Persistent notification shown. Audio stays offline.",
                style = MaterialTheme.typography.labelLarge,
              )
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
            Text("Voice Recognition Language (Vosk Offline)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SupportedLanguage.entries.forEach { lang ->
                FilterChip(
                  selected = selectedLanguage == lang,
                  onClick = { onSelectLanguage(lang) },
                  label = { Text("${lang.englishName} (${lang.nativeName})") },
                  modifier = Modifier.testTag("voice_lang_${lang.code}"),
                )
              }
            }

            if (selectedLanguage == SupportedLanguage.ODIA) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Default.Warning, contentDescription = null)
                  Text(
                    text = "Odia offline ASR is not available in Vosk small models. PAPI AI never fabricates unsupported language recognition. Select English or Hindi for offline voice typing.",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            } else if (!isSelectedModelInstalled) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "${selectedLanguage.englishName} Vosk model (~40 MB) is not downloaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                  )
                  Spacer(Modifier.width(8.dp))
                  FilledTonalButton(
                    onClick = onOpenModels,
                    modifier = Modifier.testTag("voice_open_models_button"),
                  ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Get Model")
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
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = if (isListening) {
                MaterialTheme.colorScheme.secondary
              } else {
                MaterialTheme.colorScheme.primary
              },
              modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
                .pointerInput(selectedLanguage, isSelectedModelInstalled) {
                  detectTapGestures(
                    onPress = {
                      if (!hasAudioPermission()) {
                        showMicRationale = true
                        return@detectTapGestures
                      }
                      if (selectedLanguage != SupportedLanguage.ODIA && isSelectedModelInstalled) {
                        onStartListening()
                        tryAwaitRelease()
                        onStopListening()
                      }
                    },
                  )
                }
                .testTag("voice_push_to_talk_pad"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                  contentDescription = if (isListening) labels.stopListening else labels.startListening,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(40.dp),
                )
              }
            }

            Text(
              text = if (isListening) {
                "Listening… Release Push-to-Talk or tap Stop"
              } else {
                "Hold circle for Push-to-Talk, or use Start/Stop button below"
              },
              style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = {
                  if (!hasAudioPermission()) {
                    showMicRationale = true
                  } else {
                    onToggleListening()
                  }
                },
                enabled = selectedLanguage != SupportedLanguage.ODIA && isSelectedModelInstalled,
                colors = if (isListening) {
                  ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                  ButtonDefaults.buttonColors()
                },
                modifier = Modifier.testTag("voice_toggle_button"),
              ) {
                Icon(if (isListening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isListening) labels.stopListening else "Start Continuous Mic")
              }
            }

            if (state.partialText.isNotBlank()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "Live: ${state.partialText}",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(12.dp),
                )
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
              text = state.errorMessage!!,
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
              Text("Review & Edit Dictated Text", style = MaterialTheme.typography.titleMedium)
              FilledTonalButton(
                onClick = onClearTranscript,
                enabled = state.transcriptText.isNotBlank(),
                modifier = Modifier.testTag("voice_clear_button"),
              ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Clear")
              }
            }

            OutlinedTextField(
              value = state.transcriptText,
              onValueChange = onTranscriptEdited,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_transcript_input"),
              minLines = 4,
              placeholder = { Text("Recognized speech appears here for editing before insertion…") },
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = onInsertOrCopy,
                enabled = state.transcriptText.isNotBlank(),
                modifier = Modifier.testTag("voice_insert_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.insertAtCursor)
              }

              OutlinedButton(
                onClick = {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cb.setPrimaryClip(ClipData.newPlainText("PAPI AI Voice", state.transcriptText))
                },
                enabled = state.transcriptText.isNotBlank(),
                modifier = Modifier.testTag("voice_copy_button"),
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.copyText)
              }
            }

            OutlinedButton(
              onClick = { onSendToTranslator(state.transcriptText, selectedLanguage) },
              enabled = state.transcriptText.isNotBlank(),
              modifier = Modifier.fillMaxWidth().testTag("voice_translate_button"),
            ) {
              Icon(Icons.Default.Translate, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("Send to AI Translator")
            }

            insertionFeedback?.let { msg ->
              Text(
                text = msg,
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
