package com.papiai.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun PrivacyPermissionsScreen(
  isAccessibilityConnected: Boolean,
  statusMessage: String?,
  onOpenAccessibilitySettings: () -> Unit,
  onOpenNotificationSettings: () -> Unit,
  onClearHistory: () -> Unit,
  onDeleteAllModels: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val hasMicPermission = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.RECORD_AUDIO,
  ) == PackageManager.PERMISSION_GRANTED

  val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  } else {
    true
  }

  var confirmDeleteModels by remember { mutableStateOf(false) }

  if (confirmDeleteModels) {
    AlertDialog(
      onDismissRequest = { confirmDeleteModels = false },
      title = { Text("Delete All Downloaded Models?") },
      text = { Text("This deletes all Tesseract, Vosk, and IndicTrans2 models stored locally in app files.") },
      confirmButton = {
        Button(
          onClick = {
            confirmDeleteModels = false
            onDeleteAllModels()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete All")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteModels = false }) { Text("Cancel") }
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
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Security, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Privacy & Zero-Tracking Commitments", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text = "• All OCR, Voice Typing, Screen Reading, and IndicTrans2 translation execute locally on your device.\n" +
                "• No analytics, no ads, no Firebase, and no mandatory cloud servers.\n" +
                "• Password fields, biometric prompts, and secure windows are always skipped.\n" +
                "• MediaProjection captures only a single frame on explicit consent.",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }

      item {
        PermissionCard(
          icon = Icons.Default.AccessibilityNew,
          title = "Accessibility Service",
          status = if (isAccessibilityConnected) "Granted & Active" else "Not Enabled",
          explanation = "Allows reading visible non-password text from apps on your explicit command and inserting voice-typed text into focused fields.",
          actionLabel = "Open Accessibility Settings",
          onAction = onOpenAccessibilitySettings,
          testTag = "privacy_accessibility_button",
        )
      }

      item {
        PermissionCard(
          icon = Icons.Default.Mic,
          title = "Microphone (RECORD_AUDIO)",
          status = if (hasMicPermission) "Granted" else "Requested on demand in Voice Typing",
          explanation = "Used strictly during push-to-talk or active voice typing with a persistent notification. Never records silently.",
          actionLabel = null,
          onAction = {},
          testTag = "privacy_mic_card",
        )
      }

      item {
        PermissionCard(
          icon = Icons.Default.Screenshot,
          title = "Screen Region Capture (MediaProjection)",
          status = "Per-session system prompt",
          explanation = "Android prompts for consent before every screen-region OCR capture, captures one frame, and immediately stops projection.",
          actionLabel = null,
          onAction = {},
          testTag = "privacy_projection_card",
        )
      }

      item {
        PermissionCard(
          icon = Icons.Default.Notifications,
          title = "Foreground Notifications",
          status = if (hasNotificationPermission) "Granted" else "Recommended on Android 13+",
          explanation = "Displays transparent ongoing notifications whenever the microphone or screen capture foreground service is active.",
          actionLabel = "Notification Settings",
          onAction = onOpenNotificationSettings,
          testTag = "privacy_notifications_button",
        )
      }

      item {
        Card {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("Local Data Controls", style = MaterialTheme.typography.titleMedium)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = onClearHistory,
                modifier = Modifier
                  .weight(1f)
                  .testTag("privacy_clear_history_button"),
              ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Clear History")
              }

              FilledTonalButton(
                onClick = { confirmDeleteModels = true },
                modifier = Modifier
                  .weight(1f)
                  .testTag("privacy_delete_models_button"),
              ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Delete Models")
              }
            }

            statusMessage?.let { msg ->
              Text(
                text = msg,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionCard(
  icon: ImageVector,
  title: String,
  status: String,
  explanation: String,
  actionLabel: String?,
  onAction: () -> Unit,
  testTag: String,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
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
          Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
      }
      Text(explanation, style = MaterialTheme.typography.bodyMedium)
      if (actionLabel != null) {
        FilledTonalButton(
          onClick = onAction,
          modifier = Modifier.testTag(testTag),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}
