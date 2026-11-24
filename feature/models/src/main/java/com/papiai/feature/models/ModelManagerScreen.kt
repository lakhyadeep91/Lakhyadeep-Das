package com.papiai.feature.models

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.DeviceResourceSnapshot
import com.papiai.domain.model.ModelCategory
import com.papiai.domain.model.ModelInstallStatus
import com.papiai.domain.model.ModelRuntimeState
import com.papiai.domain.model.PapiStrings
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelManagerScreen(
  settings: AppSettings,
  models: List<ModelRuntimeState>,
  selectedCategory: ModelCategory?,
  resources: DeviceResourceSnapshot,
  onSelectCategory: (ModelCategory?) -> Unit,
  onDownloadOrResume: (String) -> Unit,
  onPause: (String) -> Unit,
  onDelete: (String) -> Unit,
  onReleaseLoadedModels: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val labels = PapiStrings.forLanguage(settings.uiLanguage)

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
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Device Memory & Storage", style = MaterialTheme.typography.titleMedium)
              }
              if (resources.activeLoadedModelId != null) {
                FilledTonalButton(
                  onClick = onReleaseLoadedModels,
                  modifier = Modifier.testTag("models_release_ram_button"),
                ) {
                  Text("Unload RAM")
                }
              }
            }
            Text(
              text = "RAM: ${resources.availableRamMb} MB free / ${resources.totalRamMb} MB total • Storage: ${resources.freeStorageMb} MB free",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = "ABI: ${resources.architecture} • Android ${resources.androidRelease} • Active slot: ${resources.activeLoadedModelId ?: "None"}",
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }

      item {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          FilterChip(
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            label = { Text("All") },
            modifier = Modifier.testTag("models_filter_all"),
          )
          FilterChip(
            selected = selectedCategory == ModelCategory.OCR,
            onClick = { onSelectCategory(ModelCategory.OCR) },
            label = { Text("OCR") },
            modifier = Modifier.testTag("models_filter_ocr"),
          )
          FilterChip(
            selected = selectedCategory == ModelCategory.TRANSLATION,
            onClick = { onSelectCategory(ModelCategory.TRANSLATION) },
            label = { Text("Translation") },
            modifier = Modifier.testTag("models_filter_translation"),
          )
          FilterChip(
            selected = selectedCategory == ModelCategory.SPEECH_RECOGNITION,
            onClick = { onSelectCategory(ModelCategory.SPEECH_RECOGNITION) },
            label = { Text("Voice ASR") },
            modifier = Modifier.testTag("models_filter_speech"),
          )
        }
      }

      items(models, key = { it.artifact.id }) { state ->
        val art = state.artifact
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("model_card_${art.id}"),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = art.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
              )
              if (state.status == ModelInstallStatus.INSTALLED) {
                Icon(
                  Icons.Default.CheckCircle,
                  contentDescription = "Installed",
                  tint = MaterialTheme.colorScheme.tertiary,
                )
              }
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              AssistChip(onClick = {}, label = { Text(formatMb(art.totalBytes), style = MaterialTheme.typography.labelSmall) })
              AssistChip(onClick = {}, label = { Text(art.licenseName, style = MaterialTheme.typography.labelSmall) })
              AssistChip(
                onClick = {},
                label = { Text(art.languages.joinToString("/") { it.englishName }, style = MaterialTheme.typography.labelSmall) },
              )
              AssistChip(
                onClick = {},
                label = { Text("Min RAM ${art.minFreeRamMb} MB", style = MaterialTheme.typography.labelSmall) },
              )
            }

            Text(
              text = "Source: ${art.sourceName} (${art.version})",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = art.compatibilityNote,
              style = MaterialTheme.typography.bodyMedium,
            )

            if (!state.hasEnoughRam || !state.hasEnoughStorage) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  Icons.Default.Warning,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text = buildString {
                    if (!state.hasEnoughRam) append("Low free RAM for inference. ")
                    if (!state.hasEnoughStorage) append("Insufficient free storage.")
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }

            if (state.status == ModelInstallStatus.DOWNLOADING ||
              state.status == ModelInstallStatus.PAUSED ||
              state.status == ModelInstallStatus.VERIFYING
            ) {
              LinearProgressIndicator(
                progress = { state.progressFraction },
                modifier = Modifier.fillMaxWidth(),
              )
              Text(
                text = "${state.status.name}: ${formatMb(state.downloadedBytes)} / ${formatMb(state.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
              )
            }

            state.verifiedSha256?.let { sha ->
              Text(
                text = "SHA-256: ${sha.take(16)}…${sha.takeLast(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }

            state.errorMessage?.let { err ->
              Text(
                text = err,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              when (state.status) {
                ModelInstallStatus.NOT_INSTALLED, ModelInstallStatus.ERROR -> {
                  Button(
                    onClick = { onDownloadOrResume(art.id) },
                    enabled = state.hasEnoughStorage,
                    modifier = Modifier.testTag("model_download_${art.id}"),
                  ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(labels.download)
                  }
                }
                ModelInstallStatus.DOWNLOADING -> {
                  FilledTonalButton(
                    onClick = { onPause(art.id) },
                    modifier = Modifier.testTag("model_pause_${art.id}"),
                  ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(labels.pauseDownload)
                  }
                }
                ModelInstallStatus.PAUSED -> {
                  Button(
                    onClick = { onDownloadOrResume(art.id) },
                    modifier = Modifier.testTag("model_resume_${art.id}"),
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(labels.resumeDownload)
                  }
                  OutlinedButton(
                    onClick = { onDelete(art.id) },
                    modifier = Modifier.testTag("model_delete_${art.id}"),
                  ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(labels.deleteModel)
                  }
                }
                ModelInstallStatus.VERIFYING -> {
                  FilledTonalButton(onClick = {}, enabled = false) {
                    Text("Verifying SHA-256…")
                  }
                }
                ModelInstallStatus.INSTALLED -> {
                  OutlinedButton(
                    onClick = { onDelete(art.id) },
                    modifier = Modifier.testTag("model_delete_${art.id}"),
                  ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(labels.deleteModel)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun formatMb(bytes: Long): String {
  val mb = bytes.toDouble() / (1024.0 * 1024.0)
  return String.format(Locale.US, "%.1f MB", mb)
}
