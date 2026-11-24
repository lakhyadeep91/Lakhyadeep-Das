package com.papiai.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.papiai.domain.model.ScreenCaptureSnapshot
import com.papiai.domain.model.ScreenRegionNode
import java.util.UUID

class PapiAccessibilityService : AccessibilityService() {

  private var lastFocusedEditablePackage: String? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    serviceInfo = serviceInfo.apply {
      eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
        AccessibilityEvent.TYPE_VIEW_FOCUSED
      feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
      flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
        AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME
      notificationTimeout = 200
    }
    AccessibilityBridge.onServiceStateChanged(true, this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    val pkg = event.packageName?.toString().orEmpty()
    if (pkg == packageName) return
    if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
      val src = event.source
      if (src != null && src.isEditable && !isSensitivePasswordNode(src)) {
        lastFocusedEditablePackage = pkg
      }
    }
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      extractVisibleScreenNow()
    }
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    AccessibilityBridge.onServiceStateChanged(false, null)
    super.onDestroy()
  }

  fun extractVisibleScreenNow() {
    val root = rootInActiveWindow ?: return
    val pkg = root.packageName?.toString().orEmpty()
    if (pkg == packageName) return

    if (isBlockedPackage(pkg)) {
      AccessibilityBridge.publishSnapshot(
        ScreenCaptureSnapshot(
          timestampMs = System.currentTimeMillis(),
          packageName = pkg,
          windowTitle = "Protected System Screen",
          regions = emptyList(),
          skippedSecureNodes = 1,
          isSecureWindowBlocked = true,
        )
      )
      return
    }

    val collected = mutableListOf<ScreenRegionNode>()
    var skippedSecure = 0

    fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
      if (node == null || depth > 40) return
      if (!node.isVisibleToUser) return

      if (isSensitivePasswordNode(node)) {
        skippedSecure++
        return
      }

      val label = listOfNotNull(
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() },
      ).firstOrNull()

      if (!label.isNullOrBlank()) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty && rect.width() > 4 && rect.height() > 4) {
          collected.add(
            ScreenRegionNode(
              id = "${rect.left}_${rect.top}_${collected.size}_${UUID.randomUUID().toString().take(4)}",
              text = label,
              left = rect.left,
              top = rect.top,
              right = rect.right,
              bottom = rect.bottom,
              packageName = node.packageName?.toString() ?: pkg,
              className = node.className?.toString().orEmpty(),
              isEditable = node.isEditable,
              isSelected = true,
            )
          )
        }
      }

      for (i in 0 until node.childCount) {
        val child = node.getChild(i)
        traverse(child, depth + 1)
      }
    }

    traverse(root, 0)

    // Reading order: top-to-bottom banded rows, then left-to-right
    val ordered = collected
      .distinctBy { "${it.text}|${it.top / 16}|${it.left / 16}" }
      .sortedWith(compareBy({ it.top / 32 }, { it.left }))

    val windowTitle = windows
      ?.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
      ?.title?.toString()
      ?: pkg.ifBlank { "Active Window" }

    AccessibilityBridge.publishSnapshot(
      ScreenCaptureSnapshot(
        timestampMs = System.currentTimeMillis(),
        packageName = pkg,
        windowTitle = windowTitle,
        regions = ordered,
        skippedSecureNodes = skippedSecure,
        isSecureWindowBlocked = false,
      )
    )
  }

  fun insertTextIntoFocusedEditable(text: String): TextInsertionOutcome {
    val root = rootInActiveWindow ?: return TextInsertionOutcome.CopiedToClipboardFallback
    val pkg = root.packageName?.toString().orEmpty()
    if (isBlockedPackage(pkg)) {
      return TextInsertionOutcome.BlockedSecureField("Blocked on protected system or credential screen.")
    }

    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    val target = if (focused != null && focused.isEditable) {
      focused
    } else {
      findFirstEditable(root, 0)
    } ?: return TextInsertionOutcome.CopiedToClipboardFallback

    if (isSensitivePasswordNode(target)) {
      return TextInsertionOutcome.BlockedSecureField("PAPI AI never writes into or reads password fields.")
    }

    val existing = target.text?.toString().orEmpty()
    val updated = if (existing.isBlank()) text else "$existing $text"
    val args = Bundle().apply {
      putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
    }
    val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    return if (ok) {
      TextInsertionOutcome.InsertedViaAccessibility(pkg.ifBlank { "Focused App" })
    } else {
      TextInsertionOutcome.CopiedToClipboardFallback
    }
  }

  private fun findFirstEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
    if (node == null || depth > 30) return null
    if (node.isEditable && node.isVisibleToUser && !isSensitivePasswordNode(node)) {
      return node
    }
    for (i in 0 until node.childCount) {
      val hit = findFirstEditable(node.getChild(i), depth + 1)
      if (hit != null) return hit
    }
    return null
  }

  private fun isSensitivePasswordNode(node: AccessibilityNodeInfo): Boolean {
    if (node.isPassword) return true
    val variation = node.inputType and InputType.TYPE_MASK_VARIATION
    val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
    if (inputClass == InputType.TYPE_CLASS_TEXT && (
        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
          variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
          variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
      )
    ) {
      return true
    }
    if (inputClass == InputType.TYPE_CLASS_NUMBER &&
      variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    ) {
      return true
    }
    return false
  }

  private fun isBlockedPackage(pkg: String): Boolean {
    val lower = pkg.lowercase()
    return lower.contains("keyguard") ||
      lower.contains("biometric") ||
      lower.contains("credentialmanager") ||
      lower.contains("keepass") ||
      lower.contains("bitwarden") ||
      lower.contains("1password")
  }
}
