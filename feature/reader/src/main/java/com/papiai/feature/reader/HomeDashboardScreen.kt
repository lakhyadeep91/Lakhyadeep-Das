package com.papiai.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.papiai.domain.model.HistoryEntry
import com.papiai.domain.model.PapiStrings
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeDashboardScreen(
  state: DashboardSummaryState,
  heroArtResId: Int,
  onNavigateScreenReader: () -> Unit,
  onNavigateOcr: () -> Unit,
  onNavigateTranslator: () -> Unit,
  onNavigateVoice: () -> Unit,
  onNavigateModels: () -> Unit,
  onNavigatePrivacy: () -> Unit,
  onOpenHistoryInTranslator: (HistoryEntry) -> Unit,
  modifier: Modifier = Modifier,
) {
  val labels = PapiStrings.forLanguage(state.settings.uiLanguage)

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_hero_card"),
          shape = MaterialTheme.shapes.extraLarge,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
          Box(modifier = Modifier.fillMaxWidth().height(206.dp)) {
            Image(
              painter = painterResource(id = heroArtResId),
              contentDescription = "PAPI AI acoustic and prism banner",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color(0x66062328),
                      Color(0xE0062328),
                    )
                  )
                )
            )
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  color = Color(0x33FFFFFF),
                  shape = RoundedCornerShape(50),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.VerifiedUser,
                      contentDescription = null,
                      tint = Color(0xFFBDF4EE),
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                      text = labels.offlineReady,
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                    )
                  }
                }
                Text(
                  text = "${state.deviceSnapshot.architecture} • ${state.deviceSnapshot.availableRamMb} MB free",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFD9F3F5),
                )
              }

              Column {
                Text(
                  text = "PAPI AI",
                  style = MaterialTheme.typography.displaySmall,
                  color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  text = labels.heroTagline,
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFFE3F2F4),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          QuickActionTile(
            title = labels.quickScreenRead,
            subtitle = "Accessibility",
            icon = Icons.Default.RecordVoiceOver,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tag = "quick_screen_reader_tile",
            onClick = onNavigateScreenReader,
            modifier = Modifier.weight(1f),
          )
          QuickActionTile(
            title = labels.quickOcr,
            subtitle = "Tesseract OCR",
            icon = Icons.Default.DocumentScanner,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tag = "quick_ocr_tile",
            onClick = onNavigateOcr,
            modifier = Modifier.weight(1f),
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          QuickActionTile(
            title = labels.quickTranslate,
            subtitle = "IndicTrans2",
            icon = Icons.Default.Translate,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            tag = "quick_translator_tile",
            onClick = onNavigateTranslator,
            modifier = Modifier.weight(1f),
          )
          QuickActionTile(
            title = labels.quickVoiceType,
            subtitle = "Vosk Offline",
            icon = Icons.Default.Mic,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tag = "quick_voice_tile",
            onClick = onNavigateVoice,
            modifier = Modifier.weight(1f),
          )
        }
      }

      item {
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "System & Engine Readiness",
              style = MaterialTheme.typography.titleMedium,
            )

            StatusRow(
              icon = Icons.Default.AccessibilityNew,
              title = if (state.isAccessibilityActive) labels.accessibilityEnabled else labels.accessibilityDisabled,
              detail = if (state.isAccessibilityActive) "Ready to read screens & insert text safely" else "Tap Privacy & Permissions to enable",
              onClick = onNavigatePrivacy,
              testTag = "status_accessibility_row",
            )

            StatusRow(
              icon = Icons.Default.Memory,
              title = "Offline AI Models (${state.installedModelsCount}/${state.totalModelsCount} installed)",
              detail = "Free storage: ${state.deviceSnapshot.freeStorageMb} MB • Active slot: ${state.deviceSnapshot.activeLoadedModelId ?: "Idle"}",
              onClick = onNavigateModels,
              testTag = "status_models_row",
            )

            val tts = state.activeTtsEngine
            StatusRow(
              icon = Icons.Default.GraphicEq,
              title = tts?.label ?: "Checking Android TTS Engine…",
              detail = buildString {
                append(tts?.provenanceNote ?: "No active TTS engine detected")
                if (tts != null) {
                  append(if (tts.isOdiaSupported) " • Odia TTS verified" else " • Odia TTS unavailable")
                }
              },
              onClick = onNavigateScreenReader,
              testTag = "status_tts_row",
            )

            StatusRow(
              icon = Icons.Default.Security,
              title = "Local-First Privacy Guard",
              detail = "Zero ads, zero trackers, password fields blocked",
              onClick = onNavigatePrivacy,
              testTag = "status_privacy_row",
            )
          }
        }
      }

      if (state.recentHistory.isNotEmpty()) {
        item {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Outlined.History, contentDescription = null)
            Text(
              text = "Recent Local Activity",
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }

        items(state.recentHistory, key = { it.id }) { entry ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenHistoryInTranslator(entry) }
              .testTag("history_item_${entry.id}"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = entry.title,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                )
                AssistChip(
                  onClick = { onOpenHistoryInTranslator(entry) },
                  label = { Text(entry.languageCode.uppercase(), style = MaterialTheme.typography.labelSmall) },
                )
              }
              Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                  .format(Date(entry.timestampMs)),
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

@Composable
private fun QuickActionTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  containerColor: Color,
  contentColor: Color,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .clip(MaterialTheme.shapes.large)
      .clickable(onClick = onClick)
      .testTag(tag),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = containerColor),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = contentColor,
        modifier = Modifier.size(28.dp),
      )
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = contentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = contentColor.copy(alpha = 0.8f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun StatusRow(
  icon: ImageVector,
  title: String,
  detail: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onClick)
      .padding(8.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.size(40.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.labelLarge)
      Text(
        text = detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = "Open",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
  }
}
