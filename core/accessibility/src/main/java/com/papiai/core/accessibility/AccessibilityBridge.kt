package com.papiai.core.accessibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import com.papiai.domain.model.ScreenCaptureSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TextInsertionOutcome {
  data class InsertedViaAccessibility(val targetPackage: String) : TextInsertionOutcome
  data object CopiedToClipboardFallback : TextInsertionOutcome
  data class BlockedSecureField(val reason: String) : TextInsertionOutcome
}

object AccessibilityBridge {
  private val _isServiceConnected = MutableStateFlow(false)
  val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

  private val _latestSnapshot = MutableStateFlow<ScreenCaptureSnapshot?>(null)
  val latestSnapshot: StateFlow<ScreenCaptureSnapshot?> = _latestSnapshot.asStateFlow()

  private val _capturedScreenBitmap = MutableStateFlow<Bitmap?>(null)
  val capturedScreenBitmap: StateFlow<Bitmap?> = _capturedScreenBitmap.asStateFlow()

  private val _refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
  val refreshRequests: SharedFlow<Unit> = _refreshRequests.asSharedFlow()

  private val _stopVoiceRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
  val stopVoiceRequests: SharedFlow<Unit> = _stopVoiceRequests.asSharedFlow()

  internal var activeServiceRef: PapiAccessibilityService? = null

  fun onServiceStateChanged(connected: Boolean, service: PapiAccessibilityService?) {
    _isServiceConnected.value = connected
    activeServiceRef = if (connected) service else null
  }

  fun publishSnapshot(snapshot: ScreenCaptureSnapshot) {
    _latestSnapshot.value = snapshot
  }

  fun publishCapturedBitmap(bitmap: Bitmap?) {
    val previous = _capturedScreenBitmap.value
    _capturedScreenBitmap.value = bitmap
    if (previous != null && previous !== bitmap && !previous.isRecycled) {
      previous.recycle()
    }
  }

  fun requestScreenRefresh(): Boolean {
    val svc = activeServiceRef
    return if (svc != null) {
      svc.extractVisibleScreenNow()
      true
    } else {
      _refreshRequests.tryEmit(Unit)
      false
    }
  }

  fun requestStopVoiceFromNotification() {
    _stopVoiceRequests.tryEmit(Unit)
  }

  fun insertTextOrCopyFallback(context: Context, text: String): TextInsertionOutcome {
    if (text.isBlank()) {
      return TextInsertionOutcome.CopiedToClipboardFallback
    }
    val svc = activeServiceRef
    if (svc != null) {
      val result = svc.insertTextIntoFocusedEditable(text)
      if (result !is TextInsertionOutcome.CopiedToClipboardFallback) {
        return result
      }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PAPI AI", text))
    return TextInsertionOutcome.CopiedToClipboardFallback
  }

  fun isAccessibilityEnabledInSettings(context: Context): Boolean {
    val expected = ComponentName(context, PapiAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
  }
}
