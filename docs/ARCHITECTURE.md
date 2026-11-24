# PAPI AI Modular Architecture

## Module Graph
- `:domain`: Pure Kotlin domain models, `SupportedLanguage`, `ModelCatalogRegistry`, and `PapiStrings` (EN/HI/OR).
- `:data`: Room local database (`PapiDatabase`), DataStore Preferences (`SettingsRepository`), and `ModelDownloadRepository` (OkHttp Range pause/resume + SHA-256 verification + safe ZIP extraction).
- `:core:ai`: `DeviceResourceMonitor` and `AiModelLifecycleManager` (single-slot heavy model enforcement and low-memory trimming for Galaxy A52).
- `:core:ocr`: `TesseractOcrEngine` wrapping Tesseract4Android 4.9.0 with region cropping and background IO execution.
- `:core:translation`: `IndicTrans2OnnxEngine` wrapping ONNX Runtime Android 1.22.0 + `OptionalLibreTranslateClient` (off by default).
- `:core:speech`: `VoskVoiceRecognizer` (Vosk Android 0.3.75) + `PapiTtsEngine` (Android TTS with engine provenance classification & Odia verification).
- `:core:accessibility`: `PapiAccessibilityService`, `ScreenCaptureService` (`mediaProjection` FGS), and `VoiceForegroundService` (`microphone` FGS).
- `:feature:reader`, `:feature:translator`, `:feature:voice`, `:feature:models`, `:feature:settings`: Jetpack Compose Material 3 screens & ViewModels.
- `:app`: Adaptive Navigation shell (`NavigationBar` / `NavigationRail`), `PapiAppContainer`, and `MainActivity`.
