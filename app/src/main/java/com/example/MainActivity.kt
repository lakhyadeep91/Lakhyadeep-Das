package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.PapiTheme
import com.papiai.domain.model.PapiStrings
import com.papiai.domain.model.SupportedLanguage
import com.papiai.feature.models.ModelManagerScreen
import com.papiai.feature.models.ModelManagerViewModel
import com.papiai.feature.reader.HomeDashboardScreen
import com.papiai.feature.reader.OcrReaderScreen
import com.papiai.feature.reader.ReaderViewModel
import com.papiai.feature.reader.ScreenReaderScreen
import com.papiai.feature.settings.PrivacyPermissionsScreen
import com.papiai.feature.settings.SettingsScreen
import com.papiai.feature.settings.SettingsViewModel
import com.papiai.feature.translator.TranslatorScreen
import com.papiai.feature.translator.TranslatorViewModel
import com.papiai.feature.voice.VoiceTypingScreen
import com.papiai.feature.voice.VoiceViewModel

object PapiRoutes {
  const val HOME = "home"
  const val SCREEN_READER = "screen_reader"
  const val OCR_READER = "ocr_reader"
  const val TRANSLATOR = "translator"
  const val VOICE = "voice"
  const val MODELS = "models"
  const val SETTINGS = "settings"
  const val PRIVACY = "privacy"
}

private data class NavDestination(
  val route: String,
  val label: String,
  val icon: ImageVector,
  val testTag: String,
)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PapiRootApp()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapiRootApp() {
  val context = LocalContext.current
  val container = remember(context) { PapiAppContainer(context) }

  val readerVm: ReaderViewModel = viewModel(factory = container.viewModelFactory)
  val translatorVm: TranslatorViewModel = viewModel(factory = container.viewModelFactory)
  val voiceVm: VoiceViewModel = viewModel(factory = container.viewModelFactory)
  val modelsVm: ModelManagerViewModel = viewModel(factory = container.viewModelFactory)
  val settingsVm: SettingsViewModel = viewModel(factory = container.viewModelFactory)

  val settings by settingsVm.settings.collectAsStateWithLifecycle()
  val labels = PapiStrings.forLanguage(settings.uiLanguage)
  val navController = rememberNavController()
  val backStack by navController.currentBackStackEntryAsState()
  val currentRoute = backStack?.destination?.route ?: PapiRoutes.HOME

  val primaryDestinations = listOf(
    NavDestination(PapiRoutes.HOME, labels.navHome, Icons.Default.Home, "nav_home"),
    NavDestination(PapiRoutes.SCREEN_READER, labels.navReader, Icons.Default.RecordVoiceOver, "nav_reader"),
    NavDestination(PapiRoutes.OCR_READER, "OCR", Icons.Default.DocumentScanner, "nav_ocr"),
    NavDestination(PapiRoutes.TRANSLATOR, labels.navTranslator, Icons.Default.Translate, "nav_translator"),
    NavDestination(PapiRoutes.VOICE, labels.navVoice, Icons.Default.Mic, "nav_voice"),
  )

  fun navigateSingleTop(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  val screenTitle = when (currentRoute) {
    PapiRoutes.HOME -> labels.appTitle
    PapiRoutes.SCREEN_READER -> labels.quickScreenRead
    PapiRoutes.OCR_READER -> labels.quickOcr
    PapiRoutes.TRANSLATOR -> labels.quickTranslate
    PapiRoutes.VOICE -> labels.quickVoiceType
    PapiRoutes.MODELS -> labels.navModels
    PapiRoutes.SETTINGS -> labels.navSettings
    PapiRoutes.PRIVACY -> labels.navPrivacy
    else -> labels.appTitle
  }

  PapiTheme(themeMode = settings.themeMode) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavRail = maxWidth >= 600.dp

      Scaffold(
        topBar = {
          TopAppBar(
            title = {
              Text(
                text = screenTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            actions = {
              IconButton(
                onClick = { navigateSingleTop(PapiRoutes.MODELS) },
                modifier = Modifier.testTag("topbar_models_button"),
              ) {
                Icon(Icons.Default.Memory, contentDescription = labels.navModels)
              }
              IconButton(
                onClick = { navigateSingleTop(PapiRoutes.PRIVACY) },
                modifier = Modifier.testTag("topbar_privacy_button"),
              ) {
                Icon(Icons.Default.Security, contentDescription = labels.navPrivacy)
              }
              IconButton(
                onClick = { navigateSingleTop(PapiRoutes.SETTINGS) },
                modifier = Modifier.testTag("topbar_settings_button"),
              ) {
                Icon(Icons.Default.Settings, contentDescription = labels.navSettings)
              }
            },
          )
        },
        bottomBar = {
          if (!useNavRail) {
            NavigationBar {
              primaryDestinations.forEach { dest ->
                NavigationBarItem(
                  selected = currentRoute == dest.route,
                  onClick = { navigateSingleTop(dest.route) },
                  icon = { Icon(dest.icon, contentDescription = dest.label) },
                  label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                  modifier = Modifier.testTag(dest.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
          if (useNavRail) {
            NavigationRail {
              primaryDestinations.forEach { dest ->
                NavigationRailItem(
                  selected = currentRoute == dest.route,
                  onClick = { navigateSingleTop(dest.route) },
                  icon = { Icon(dest.icon, contentDescription = dest.label) },
                  label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                  modifier = Modifier.testTag(dest.testTag),
                )
              }
            }
          }

          NavHost(
            navController = navController,
            startDestination = PapiRoutes.HOME,
            modifier = Modifier.weight(1f),
          ) {
            composable(PapiRoutes.HOME) {
              val dashState by readerVm.dashboardState.collectAsStateWithLifecycle()
              HomeDashboardScreen(
                state = dashState,
                heroArtResId = R.drawable.papi_hero_art_1795529478025,
                onNavigateScreenReader = { navigateSingleTop(PapiRoutes.SCREEN_READER) },
                onNavigateOcr = { navigateSingleTop(PapiRoutes.OCR_READER) },
                onNavigateTranslator = { navigateSingleTop(PapiRoutes.TRANSLATOR) },
                onNavigateVoice = { navigateSingleTop(PapiRoutes.VOICE) },
                onNavigateModels = { navigateSingleTop(PapiRoutes.MODELS) },
                onNavigatePrivacy = { navigateSingleTop(PapiRoutes.PRIVACY) },
                onOpenHistoryInTranslator = { entry ->
                  translatorVm.preloadText(
                    text = entry.content,
                    source = SupportedLanguage.fromCode(entry.languageCode),
                  )
                  navigateSingleTop(PapiRoutes.TRANSLATOR)
                },
              )
            }

            composable(PapiRoutes.SCREEN_READER) {
              val snapshot by readerVm.screenSnapshot.collectAsStateWithLifecycle()
              val a11yConnected by readerVm.isAccessibilityConnected.collectAsStateWithLifecycle()
              val overrides by readerVm.regionSelectionOverrides.collectAsStateWithLifecycle()
              val readerLang by readerVm.readerLanguage.collectAsStateWithLifecycle()
              val ttsProgress by readerVm.ttsProgress.collectAsStateWithLifecycle()

              ScreenReaderScreen(
                settings = settings,
                isAccessibilityConnected = a11yConnected,
                snapshot = snapshot,
                selectionOverrides = overrides,
                readerLanguage = readerLang,
                isLanguageTtsVerified = readerVm.isTtsLanguageVerified(readerLang),
                playbackProgress = ttsProgress,
                onOpenAccessibilitySettings = readerVm::openAccessibilitySettings,
                onRefreshScreen = readerVm::refreshScreenText,
                onSelectLanguage = readerVm::setReaderLanguage,
                onToggleRegion = readerVm::toggleRegionSelection,
                onSelectAll = { selected ->
                  readerVm.selectAllRegions(snapshot?.regions.orEmpty(), selected)
                },
                onPlay = { readerVm.playScreenRegions(snapshot?.regions.orEmpty()) },
                onPause = readerVm::pauseSpeech,
                onResume = readerVm::resumeSpeech,
                onStop = readerVm::stopSpeech,
                onRepeat = readerVm::repeatSpeech,
                onSpeedChange = readerVm::updatePlaybackSpeed,
                onSendToTranslator = { text, lang ->
                  translatorVm.preloadText(text, lang)
                  navigateSingleTop(PapiRoutes.TRANSLATOR)
                },
              )
            }

            composable(PapiRoutes.OCR_READER) {
              val ocrState by readerVm.ocrState.collectAsStateWithLifecycle()
              OcrReaderScreen(
                settings = settings,
                state = ocrState,
                onSelectLanguage = readerVm::setOcrLanguage,
                onImagePicked = readerVm::loadPickedImage,
                onScreenCaptureConsentResult = readerVm::startScreenCapture,
                onCropChanged = readerVm::updateCropRect,
                onRunOcr = readerVm::runOcr,
                onTextEdited = readerVm::updateExtractedText,
                onSpeakText = readerVm::speakOcrText,
                onSendToTranslator = { text, lang ->
                  translatorVm.preloadText(text, lang)
                  navigateSingleTop(PapiRoutes.TRANSLATOR)
                },
                onOpenModels = { navigateSingleTop(PapiRoutes.MODELS) },
              )
            }

            composable(PapiRoutes.TRANSLATOR) {
              val transState by translatorVm.state.collectAsStateWithLifecycle()
              TranslatorScreen(
                settings = settings,
                state = transState,
                isTargetTtsVerified = translatorVm.isTargetTtsVerified(),
                onSourceTextChanged = translatorVm::updateSourceText,
                onSelectSourceLang = translatorVm::setSourceLanguage,
                onSelectTargetLang = translatorVm::setTargetLanguage,
                onSwapLanguages = translatorVm::swapLanguages,
                onTranslate = translatorVm::translate,
                onSpeakTarget = translatorVm::speakTarget,
                onInsertOrCopy = translatorVm::insertOrCopyTranslation,
                onOpenModels = { navigateSingleTop(PapiRoutes.MODELS) },
              )
            }

            composable(PapiRoutes.VOICE) {
              val vState by voiceVm.voiceState.collectAsStateWithLifecycle()
              val vLang by voiceVm.selectedLanguage.collectAsStateWithLifecycle()
              val vInstalled by voiceVm.isSelectedModelInstalled.collectAsStateWithLifecycle()
              val vFeedback by voiceVm.insertionFeedback.collectAsStateWithLifecycle()

              VoiceTypingScreen(
                settings = settings,
                state = vState,
                selectedLanguage = vLang,
                isSelectedModelInstalled = vInstalled,
                insertionFeedback = vFeedback,
                onSelectLanguage = voiceVm::selectLanguage,
                onStartListening = voiceVm::startListening,
                onStopListening = voiceVm::stopListening,
                onToggleListening = voiceVm::toggleListening,
                onTranscriptEdited = voiceVm::updateTranscript,
                onClearTranscript = voiceVm::clearTranscript,
                onInsertOrCopy = voiceVm::insertOrCopyTranscript,
                onSendToTranslator = { text, lang ->
                  translatorVm.preloadText(text, lang)
                  navigateSingleTop(PapiRoutes.TRANSLATOR)
                },
                onOpenModels = { navigateSingleTop(PapiRoutes.MODELS) },
              )
            }

            composable(PapiRoutes.MODELS) {
              val models by modelsVm.models.collectAsStateWithLifecycle()
              val category by modelsVm.selectedCategory.collectAsStateWithLifecycle()
              val resources by modelsVm.deviceResources.collectAsStateWithLifecycle()

              ModelManagerScreen(
                settings = settings,
                models = models,
                selectedCategory = category,
                resources = resources,
                onSelectCategory = modelsVm::setCategoryFilter,
                onDownloadOrResume = modelsVm::downloadOrResume,
                onPause = modelsVm::pause,
                onDelete = modelsVm::delete,
                onReleaseLoadedModels = modelsVm::releaseLoadedModels,
              )
            }

            composable(PapiRoutes.SETTINGS) {
              val engines by settingsVm.ttsEngines.collectAsStateWithLifecycle()
              val activeEngine by settingsVm.activeTtsEngine.collectAsStateWithLifecycle()

              SettingsScreen(
                settings = settings,
                ttsEngines = engines,
                activeTtsEngine = activeEngine,
                onSelectUiLanguage = settingsVm::setUiLanguage,
                onSelectThemeMode = settingsVm::setThemeMode,
                onTtsParamsChange = settingsVm::setTtsParams,
                onSelectTtsEngine = settingsVm::selectTtsEngine,
                onSelectTtsVoice = settingsVm::selectTtsVoice,
                onTestVoice = settingsVm::testTtsSample,
                onOnlineTranslationChanged = settingsVm::setOnlineTranslation,
                onKeepHistoryChanged = settingsVm::setKeepLocalHistory,
              )
            }

            composable(PapiRoutes.PRIVACY) {
              val a11yConnected by settingsVm.isAccessibilityConnected.collectAsStateWithLifecycle()
              val statusMsg by settingsVm.statusMessage.collectAsStateWithLifecycle()

              PrivacyPermissionsScreen(
                isAccessibilityConnected = a11yConnected,
                statusMessage = statusMsg,
                onOpenAccessibilitySettings = settingsVm::openAccessibilitySettings,
                onOpenNotificationSettings = settingsVm::openAppNotificationSettings,
                onClearHistory = settingsVm::clearAllLocalHistory,
                onDeleteAllModels = settingsVm::deleteAllDownloadedModels,
              )
            }
          }
        }
      }
    }
  }
}
