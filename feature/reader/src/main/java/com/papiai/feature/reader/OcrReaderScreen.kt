package com.papiai.feature.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.papiai.core.ocr.NormalizedCropRect
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.PapiStrings
import com.papiai.domain.model.SupportedLanguage

@Composable
fun OcrReaderScreen(
  settings: AppSettings,
  state: OcrUiState,
  onSelectLanguage: (SupportedLanguage) -> Unit,
  onImagePicked: (android.net.Uri) -> Unit,
  onScreenCaptureConsentResult: (Int, android.content.Intent) -> Unit,
  onCropChanged: (NormalizedCropRect) -> Unit,
  onRunOcr: () -> Unit,
  onTextEdited: (String) -> Unit,
  onSpeakText: () -> Unit,
  onSendToTranslator: (String, SupportedLanguage) -> Unit,
  onOpenModels: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val labels = PapiStrings.forLanguage(settings.uiLanguage)

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) onImagePicked(uri)
  }

  val mediaProjectionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    val data = result.data
    if (result.resultCode == Activity.RESULT_OK && data != null) {
      onScreenCaptureConsentResult(result.resultCode, data)
    }
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
            Text("OCR Script Language (Tesseract 4)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SupportedLanguage.entries.forEach { lang ->
                FilterChip(
                  selected = state.selectedLanguage == lang,
                  onClick = { onSelectLanguage(lang) },
                  label = { Text("${lang.englishName} (${lang.nativeName})") },
                  modifier = Modifier.testTag("ocr_lang_${lang.code}"),
                )
              }
            }

            if (!state.isLanguageModelInstalled) {
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
                    text = "${state.selectedLanguage.englishName} OCR data not installed (~1–4 MB).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                  )
                  Spacer(Modifier.width(8.dp))
                  FilledTonalButton(
                    onClick = onOpenModels,
                    modifier = Modifier.testTag("ocr_open_models_button"),
                  ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Get Model")
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("ocr_pick_image_button"),
              ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(labels.pickImage)
              }

              FilledTonalButton(
                onClick = {
                  val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                  mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("ocr_capture_screen_button"),
              ) {
                Icon(Icons.Default.Screenshot, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(labels.captureScreen)
              }
            }
          }
        }
      }

      if (state.activeBitmap != null) {
        item {
          Card {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "${state.sourceLabel} • Crop Region for OCR",
                style = MaterialTheme.typography.titleMedium,
              )
              val bmp = state.activeBitmap
              val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.45f, 2.0f)

              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(ratio),
              ) {
                Image(
                  bitmap = bmp.asImageBitmap(),
                  contentDescription = "Selected OCR source image",
                  contentScale = ContentScale.FillBounds,
                  modifier = Modifier.fillMaxSize(),
                )
                val crop = state.cropRect
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val l = crop.left * size.width
                  val t = crop.top * size.height
                  val w = (crop.right - crop.left) * size.width
                  val h = (crop.bottom - crop.top) * size.height
                  drawRect(
                    color = Color(0x66000000),
                    size = size,
                  )
                  drawRect(
                    color = Color(0xFF4FD1C5),
                    topLeft = Offset(l, t),
                    size = Size(w, h),
                    style = Stroke(width = 4f),
                  )
                }
              }

              Text("Horizontal Crop", style = MaterialTheme.typography.labelSmall)
              RangeSlider(
                value = state.cropRect.left..state.cropRect.right,
                onValueChange = { range ->
                  onCropChanged(
                    state.cropRect.copy(
                      left = range.start.coerceAtMost(range.endInclusive - 0.1f),
                      right = range.endInclusive.coerceAtLeast(range.start + 0.1f),
                    )
                  )
                },
                valueRange = 0f..1f,
              )

              Text("Vertical Crop", style = MaterialTheme.typography.labelSmall)
              RangeSlider(
                value = state.cropRect.top..state.cropRect.bottom,
                onValueChange = { range ->
                  onCropChanged(
                    state.cropRect.copy(
                      top = range.start.coerceAtMost(range.endInclusive - 0.1f),
                      bottom = range.endInclusive.coerceAtLeast(range.start + 0.1f),
                    )
                  )
                },
                valueRange = 0f..1f,
              )

              Button(
                onClick = onRunOcr,
                enabled = !state.isProcessing && state.isLanguageModelInstalled,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("ocr_run_button"),
              ) {
                if (state.isProcessing) {
                  CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(18.dp).width(18.dp),
                  )
                  Spacer(Modifier.width(8.dp))
                  Text("Running Local Tesseract OCR…")
                } else {
                  Icon(Icons.Default.DocumentScanner, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text(labels.extractText)
                }
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
              Text("Editable OCR Result", style = MaterialTheme.typography.titleMedium)
              state.processingTimeMs?.let { ms ->
                Text("${ms} ms", style = MaterialTheme.typography.labelSmall)
              }
            }

            OutlinedTextField(
              value = state.extractedText,
              onValueChange = onTextEdited,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("ocr_result_input"),
              minLines = 5,
              placeholder = { Text("Extracted Odia, Hindi, or English text will appear here…") },
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = onSpeakText,
                enabled = state.extractedText.isNotBlank(),
                modifier = Modifier.testTag("ocr_speak_button"),
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.play)
              }

              OutlinedButton(
                onClick = { onSendToTranslator(state.extractedText, state.selectedLanguage) },
                enabled = state.extractedText.isNotBlank(),
                modifier = Modifier.testTag("ocr_translate_button"),
              ) {
                Icon(Icons.Default.Translate, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.translate)
              }

              OutlinedButton(
                onClick = {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cb.setPrimaryClip(ClipData.newPlainText("PAPI AI OCR", state.extractedText))
                },
                enabled = state.extractedText.isNotBlank(),
                modifier = Modifier.testTag("ocr_copy_button"),
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(labels.copyText)
              }
            }
          }
        }
      }
    }
  }
}
