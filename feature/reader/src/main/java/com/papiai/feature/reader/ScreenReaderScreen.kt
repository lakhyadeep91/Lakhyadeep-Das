package com.papiai.feature.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.PapiStrings
import com.papiai.domain.model.ScreenCaptureSnapshot
import com.papiai.domain.model.SpeechPlaybackState
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.TtsPlaybackProgress
import kotlin.math.roundToInt

@Composable
fun ScreenReaderScreen(
  settings: AppSettings,
  isAccessibilityConnected: Boolean,
  snapshot: ScreenCaptureSnapshot?,
  selectionOverrides: Map<String, Boolean>,
  readerLanguage: SupportedLanguage,
  isLanguageTtsVerified: Boolean,
  playbackProgress: TtsPlaybackProgress,
  onOpenAccessibilitySettings: () -> Unit,
  onRefreshScreen: () -> Unit,
  onSelectLanguage: (SupportedLanguage) -> Unit,
  onToggleRegion: (String, Boolean) -> Unit,
  onSelectAll: (Boolean) -> Unit,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onStop: () -> Unit,
  onRepeat: () -> Unit,
  onSpeedChange: (Float) -> Unit,
  onSendToTranslator: (String, SupportedLanguage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val labels = PapiStrings.forLanguage(settings.uiLanguage)
  val regions = snapshot?.regions.orEmpty()
  val selectedTextCombined = regions
    .filter { selectionOverrides[it.id] ?: it.isSelected }
    .joinToString("\n\n") { it.text }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (isAccessibilityConnected) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer
            }
          )
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(Icons.Default.AccessibilityNew, contentDescription = null)
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (isAccessibilityConnected) labels.accessibilityEnabled else labels.accessibilityDisabled,
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = if (isAccessibilityConnected) {
                    "Reads visible UI text in natural reading order. Passwords and secure screens are automatically skipped."
                  } else {
                    "Grant Accessibility permission so PAPI AI can extract visible text from other apps on your explicit request."
                  },
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              if (!isAccessibilityConnected) {
                Button(
                  onClick = onOpenAccessibilitySettings,
                  modifier = Modifier.testTag("enable_accessibility_button"),
                ) {
                  Text("Enable in Settings")
                }
              } else {
                Button(
                  onClick = onRefreshScreen,
                  modifier = Modifier.testTag("refresh_screen_text_button"),
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Extract Active Screen")
                }
              }
              if (selectedTextCombined.isNotBlank()) {
                OutlinedButton(
                  onClick = { onSendToTranslator(selectedTextCombined, readerLanguage) },
                  modifier = Modifier.testTag("reader_send_translate_button"),
                ) {
                  Icon(Icons.Default.Translate, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text(labels.translate)
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
            Text("Reading Language & Playback", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SupportedLanguage.entries.forEach { lang ->
                FilterChip(
                  selected = readerLanguage == lang,
                  onClick = { onSelectLanguage(lang) },
                  label = { Text("${lang.englishName} (${lang.nativeName})") },
                  modifier = Modifier.testTag("reader_lang_${lang.code}"),
                )
              }
            }

            if (!isLanguageTtsVerified) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Default.Warning,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text = "${readerLanguage.englishName} TTS is NOT verified on your active TTS engine. Install an open-source or system voice pack if available.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }

            Text(
              text = "Speed: ${(settings.ttsSpeed * 10).roundToInt() / 10f}x",
              style = MaterialTheme.typography.labelLarge,
            )
            Slider(
              value = settings.ttsSpeed,
              onValueChange = onSpeedChange,
              valueRange = 0.5f..2.0f,
              modifier = Modifier.testTag("reader_speed_slider"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              when (playbackProgress.state) {
                SpeechPlaybackState.IDLE -> {
                  Button(
                    onClick = onPlay,
                    enabled = selectedTextCombined.isNotBlank(),
                    modifier = Modifier.testTag("reader_play_button"),
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(labels.play)
                  }
                }
                SpeechPlaybackState.PLAYING -> {
                  Button(
                    onClick = onPause,
                    modifier = Modifier.testTag("reader_pause_button"),
                  ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(labels.pause)
                  }
                }
                SpeechPlaybackState.PAUSED -> {
                  Button(
                    onClick = onResume,
                    modifier = Modifier.testTag("reader_resume_button"),
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(labels.resume)
                  }
                }
              }

              FilledTonalButton(
                onClick = onStop,
                enabled = playbackProgress.state != SpeechPlaybackState.IDLE,
                modifier = Modifier.testTag("reader_stop_button"),
              ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.stop)
              }

              OutlinedButton(
                onClick = onRepeat,
                enabled = regions.isNotEmpty(),
                modifier = Modifier.testTag("reader_repeat_button"),
              ) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.repeat)
              }
            }
          }
        }
      }

      if (snapshot != null) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${snapshot.windowTitle} (${regions.size} regions)",
                style = MaterialTheme.typography.titleMedium,
              )
              if (snapshot.skippedSecureNodes > 0 || snapshot.isSecureWindowBlocked) {
                Text(
                  text = "Protected/password nodes skipped: ${snapshot.skippedSecureNodes}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
            if (regions.isNotEmpty()) {
              val allSelected = regions.all { selectionOverrides[it.id] ?: it.isSelected }
              IconButton(
                onClick = { onSelectAll(!allSelected) },
                modifier = Modifier.testTag("reader_select_all_button"),
              ) {
                Icon(Icons.Default.SelectAll, contentDescription = "Toggle select all regions")
              }
            }
          }
        }
      }

      if (regions.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "No external screen text captured yet",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "1. Enable PAPI AI Accessibility Service.\n2. Switch to any app you want read aloud.\n3. Return here or open the app window so PAPI AI extracts visible non-sensitive text in reading order.",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
      } else {
        itemsIndexed(regions, key = { _, item -> item.id }) { index, region ->
          val isChecked = selectionOverrides[region.id] ?: region.isSelected
          val isCurrentlySpoken = playbackProgress.currentRegionId == region.id &&
            playbackProgress.state != SpeechPlaybackState.IDLE
          val containerColor by animateColorAsState(
            targetValue = if (isCurrentlySpoken) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surface
            },
            label = "spokenRegionHighlight",
          )

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onToggleRegion(region.id, isChecked) }
              .testTag("screen_region_card_$index"),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (isCurrentlySpoken) {
              BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
              null
            },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = isChecked,
                onCheckedChange = { onToggleRegion(region.id, isChecked) },
              )
              Column(modifier = Modifier.weight(1f)) {
                val annotated = if (
                  isCurrentlySpoken &&
                  playbackProgress.activeWordStart in 0 until region.text.length &&
                  playbackProgress.activeWordEnd > playbackProgress.activeWordStart
                ) {
                  val safeEnd = playbackProgress.activeWordEnd.coerceAtMost(region.text.length)
                  buildAnnotatedString {
                    append(region.text.substring(0, playbackProgress.activeWordStart))
                    withStyle(
                      SpanStyle(
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                      )
                    ) {
                      append(region.text.substring(playbackProgress.activeWordStart, safeEnd))
                    }
                    append(region.text.substring(safeEnd))
                  }
                } else {
                  buildAnnotatedString { append(region.text) }
                }

                Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                  text = "#${index + 1} • [${region.left},${region.top}]",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}
