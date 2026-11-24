package com.papiai.core.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VoiceForegroundService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP_MIC) {
      AccessibilityBridge.requestStopVoiceFromNotification()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    ensureChannel()
    val languageLabel = intent?.getStringExtra(EXTRA_LANGUAGE_LABEL) ?: "Offline ASR"
    val notification = buildNotification(languageLabel)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    return START_STICKY
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (nm.getNotificationChannel(CHANNEL_ID) == null) {
        nm.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID,
            "Active Microphone Voice Typing",
            NotificationManager.IMPORTANCE_LOW,
          )
        )
      }
    }
  }

  private fun buildNotification(languageLabel: String): Notification {
    val stopIntent = Intent(this, VoiceForegroundService::class.java).apply {
      action = ACTION_STOP_MIC
    }
    val stopPending = PendingIntent.getService(
      this,
      0,
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setContentTitle("PAPI AI Microphone Active")
      .setContentText("Listening locally ($languageLabel) — audio stays on device")
      .setOngoing(true)
      .addAction(android.R.drawable.ic_media_pause, "Stop Mic", stopPending)
      .build()
  }

  override fun onDestroy() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  companion object {
    const val CHANNEL_ID = "papi_voice_mic"
    const val NOTIFICATION_ID = 4102
    const val EXTRA_LANGUAGE_LABEL = "lang_label"
    const val ACTION_STOP_MIC = "com.papiai.action.STOP_MIC"

    fun start(context: Context, languageLabel: String) {
      val intent = Intent(context, VoiceForegroundService::class.java).apply {
        putExtra(EXTRA_LANGUAGE_LABEL, languageLabel)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, VoiceForegroundService::class.java))
    }
  }
}
