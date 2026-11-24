package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appMetadataAndPermissionsAreCompliant() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("PAPI AI", context.getString(R.string.app_name))
    assertEquals("com.aistudio.papiai.vktrmz", context.packageName)

    val pkgInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_PERMISSIONS,
    )
    val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
    assertTrue(perms.contains(Manifest.permission.RECORD_AUDIO))
    assertTrue(perms.contains(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION))
    assertTrue(perms.contains(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE))
    assertFalse(perms.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
    assertFalse(perms.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    assertFalse(perms.contains("android.permission.READ_MEDIA_IMAGES"))
    assertFalse(perms.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
  }

  @Test
  fun navigationAcrossAllEightScreensSucceeds() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dashboard_hero_card").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_reader").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("enable_accessibility_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_ocr").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ocr_pick_image_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_translator").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("translator_swap_button").assertIsDisplayed()
    composeTestRule.onNode(hasScrollToIndexAction())
      .performScrollToNode(hasTestTag("translator_source_input"))
    composeTestRule.onNodeWithTag("translator_source_input").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_voice").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(hasScrollToIndexAction())
      .performScrollToNode(hasTestTag("voice_push_to_talk_pad"))
    composeTestRule.onNodeWithTag("voice_push_to_talk_pad").assertIsDisplayed()

    composeTestRule.onNodeWithTag("topbar_models_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("models_filter_all").assertIsDisplayed()

    composeTestRule.onNodeWithTag("topbar_settings_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_lang_or").assertIsDisplayed()

    composeTestRule.onNodeWithTag("topbar_privacy_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(hasScrollToIndexAction())
      .performScrollToNode(hasTestTag("privacy_clear_history_button"))
    composeTestRule.onNodeWithTag("privacy_clear_history_button").assertIsDisplayed()
  }
}
