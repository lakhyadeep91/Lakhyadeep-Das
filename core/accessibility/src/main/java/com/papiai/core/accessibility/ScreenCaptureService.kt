package com.papiai.core.accessibility

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private val handler = Handler(Looper.getMainLooper())

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null || intent.action == ACTION_STOP) {
      cleanupAndStop()
      return START_NOT_STICKY
    }

    ensureChannel()
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
    @Suppress("DEPRECATION")
    val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

    if (resultCode != Activity.RESULT_OK || resultData == null) {
      cleanupAndStop()
      return START_NOT_STICKY
    }

    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val projection = mgr.getMediaProjection(resultCode, resultData)
    if (projection == null) {
      cleanupAndStop()
      return START_NOT_STICKY
    }

    mediaProjection = projection
    projection.registerCallback(
      object : MediaProjection.Callback() {
        override fun onStop() {
          cleanupAndStop()
        }
      },
      handler,
    )

    captureSingleFrame(projection)
    return START_NOT_STICKY
  }

  private fun captureSingleFrame(projection: MediaProjection) {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val (width, height, density) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = wm.maximumWindowMetrics.bounds
      Triple(bounds.width(), bounds.height(), resources.displayMetrics.densityDpi)
    } else {
      val dm = resources.displayMetrics
      Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
    }

    val safeW = width.coerceAtLeast(1)
    val safeH = height.coerceAtLeast(1)
    val reader = ImageReader.newInstance(safeW, safeH, PixelFormat.RGBA_8888, 2)
    imageReader = reader

    var captured = false
    reader.setOnImageAvailableListener({ ir ->
      if (captured) return@setOnImageAvailableListener
      val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
      captured = true
      try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * safeW
        val rawBitmap = Bitmap.createBitmap(
          safeW + rowPadding / pixelStride,
          safeH,
          Bitmap.Config.ARGB_8888,
        )
        rawBitmap.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, safeW, safeH)
        if (cropped !== rawBitmap) rawBitmap.recycle()
        AccessibilityBridge.publishCapturedBitmap(cropped)
      } finally {
        image.close()
        cleanupAndStop()
      }
    }, handler)

    virtualDisplay = projection.createVirtualDisplay(
      "PapiScreenOcrCapture",
      safeW,
      safeH,
      density,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      reader.surface,
      null,
      handler,
    )

    // Safety timeout so projection never lingers if no frame arrives
    handler.postDelayed({
      if (!captured) cleanupAndStop()
    }, 3500L)
  }

  private fun cleanupAndStop() {
    runCatching { virtualDisplay?.release() }
    runCatching { imageReader?.close() }
    runCatching { mediaProjection?.stop() }
    virtualDisplay = null
    imageReader = null
    mediaProjection = null
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (nm.getNotificationChannel(CHANNEL_ID) == null) {
        nm.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Screen Region Capture",
            NotificationManager.IMPORTANCE_LOW,
          )
        )
      }
    }
  }

  private fun buildNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_camera)
      .setContentTitle("PAPI AI Screen OCR")
      .setContentText("Capturing single authorized frame for local OCR…")
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .build()
  }

  override fun onDestroy() {
    cleanupAndStop()
    super.onDestroy()
  }

  companion object {
    const val CHANNEL_ID = "papi_screen_capture"
    const val NOTIFICATION_ID = 4101
    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_RESULT_DATA = "result_data"
    const val ACTION_STOP = "com.papiai.action.STOP_CAPTURE"

    fun startSingleCapture(context: Context, resultCode: Int, data: Intent) {
      val intent = Intent(context, ScreenCaptureService::class.java).apply {
        putExtra(EXTRA_RESULT_CODE, resultCode)
        putExtra(EXTRA_RESULT_DATA, data)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }
  }
}
