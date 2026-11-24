package com.papiai.core.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import com.papiai.core.ai.AiModelLifecycleManager
import com.papiai.domain.model.OcrResult
import com.papiai.domain.model.SupportedLanguage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class NormalizedCropRect(
  val left: Float = 0f,
  val top: Float = 0f,
  val right: Float = 1f,
  val bottom: Float = 1f,
) {
  fun isFullImage(): Boolean =
    left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f
}

class TesseractOcrEngine(
  private val tesseractDataRoot: File,
  private val lifecycleManager: AiModelLifecycleManager,
) {
  private val engineMutex = Mutex()
  private var tessBaseApi: TessBaseAPI? = null
  private var loadedLangCode: String? = null

  fun isLanguageDataPresent(language: SupportedLanguage): Boolean {
    val file = File(tesseractDataRoot, "tessdata/${language.tesseractCode}.traineddata")
    return file.exists() && file.length() > 10_000L
  }

  suspend fun recognizeBitmap(
    bitmap: Bitmap,
    language: SupportedLanguage,
    cropRect: NormalizedCropRect = NormalizedCropRect(),
    sourceLabel: String = "Image",
  ): Result<OcrResult> = withContext(Dispatchers.IO) {
    engineMutex.withLock {
      val start = System.currentTimeMillis()
      if (!isLanguageDataPresent(language)) {
        return@withLock Result.failure(
          IllegalStateException(
            "OCR model for ${language.englishName} (${language.tesseractCode}.traineddata) is not installed. Download it in Model Manager."
          )
        )
      }

      val modelSlotId = "tess-${language.tesseractCode}"
      val acquired = lifecycleManager.acquireModelSlot(modelSlotId, minRequiredRamMb = 128) {
        releaseInternal()
      }
      if (!acquired) {
        return@withLock Result.failure(
          IllegalStateException("Insufficient free RAM to initialize Tesseract OCR.")
        )
      }

      var croppedBitmap: Bitmap? = null
      var scaledBitmap: Bitmap? = null
      try {
        if (tessBaseApi == null || loadedLangCode != language.tesseractCode) {
          releaseInternal()
          val api = TessBaseAPI()
          val ok = api.init(tesseractDataRoot.absolutePath, language.tesseractCode)
          if (!ok) {
            api.recycle()
            return@withLock Result.failure(
              IllegalStateException("Failed to initialize Tesseract for ${language.englishName}.")
            )
          }
          tessBaseApi = api
          loadedLangCode = language.tesseractCode
        }

        val working = if (!cropRect.isFullImage()) {
          val rect = Rect(
            (cropRect.left.coerceIn(0f, 0.95f) * bitmap.width).toInt(),
            (cropRect.top.coerceIn(0f, 0.95f) * bitmap.height).toInt(),
            (cropRect.right.coerceIn(0.05f, 1f) * bitmap.width).toInt(),
            (cropRect.bottom.coerceIn(0.05f, 1f) * bitmap.height).toInt(),
          )
          val w = (rect.width()).coerceAtLeast(16)
          val h = (rect.height()).coerceAtLeast(16)
          Bitmap.createBitmap(bitmap, rect.left, rect.top, w, h).also { croppedBitmap = it }
        } else {
          bitmap
        }

        val targetBitmap = downscaleIfNeeded(working, maxDim = 2200).also {
          if (it !== working) scaledBitmap = it
        }

        val api = tessBaseApi!!
        api.setImage(targetBitmap)
        val rawText = api.utF8Text.orEmpty().trim()
        api.clear()

        val elapsed = System.currentTimeMillis() - start
        Result.success(
          OcrResult(
            text = rawText,
            language = language,
            processingTimeMs = elapsed,
            sourceLabel = sourceLabel,
          )
        )
      } catch (t: Throwable) {
        Result.failure(t)
      } finally {
        scaledBitmap?.recycle()
        croppedBitmap?.recycle()
      }
    }
  }

  private fun downscaleIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
    val largest = maxOf(src.width, src.height)
    if (largest <= maxDim) return src
    val scale = maxDim.toFloat() / largest.toFloat()
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, w, h, true)
  }

  fun releaseInternal() {
    runCatching { tessBaseApi?.recycle() }
    tessBaseApi = null
    loadedLangCode = null
  }
}
