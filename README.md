# PAPI AI – Universal Voice & Screen Assistant

PAPI AI is a native Android application (Kotlin + Jetpack Compose + Material 3) focused on offline-first accessibility, OCR, Indic translation (Odia, Hindi, English), and local voice typing, optimized for mid-range ARM64 hardware such as the Samsung Galaxy A52 (Android 14).

## Key Features

1. **Universal Screen Reader (`:feature:reader`, `:core:accessibility`)**
   - Uses an explicit opt-in `AccessibilityService` (`PapiAccessibilityService`) to extract visible non-password text nodes in reading order.
   - Selectable text regions, play/pause/resume/stop/repeat controls, adjustable speed, and live word-range highlighting.
   - Automatically skips password fields (`isPassword` / password `InputType`) and sensitive system/credential packages.

2. **OCR Image & Screen-Region Reader (`:core:ocr`, `:feature:reader`)**
   - Uses `Tesseract4Android 4.9.0` (Apache-2.0) with on-demand `tessdata_fast` models for **English (`eng`)**, **Hindi (`hin`)**, and **Odia (`ori`)**.
   - Supports zero-permission Android Photo Picker (`PickVisualMedia`) and authorized single-frame `MediaProjection` screen capture (`foregroundServiceType="mediaProjection"`), with interactive region crop sliders and editable output.

3. **AI Translator (`:core:translation`, `:feature:translator`)**
   - Offline translation between **Odia**, **Hindi**, and **English** using **AI4Bharat IndicTrans2 Distilled 200M INT8 ONNX** models executed via **ONNX Runtime Android (`1.22.0`)**.
   - Preserves paragraph breaks and supports optional, strictly user-configured self-hosted **LibreTranslate** fallback (disabled by default).

4. **Text-to-Speech (`:core:speech`)**
   - Adjustable voice, speed, pitch, and volume with word-range progress tracking.
   - Inspects active Android TTS engine provenance to clearly label **Open-Source** engines (e.g., eSpeak-NG / RHVoice / Piper) vs. **Proprietary System Engines** (e.g., Google / Samsung TTS).
   - Dynamically verifies `or-IN` (Odia) availability and never claims Odia TTS support unless verified on the active engine.

5. **AI Voice Typing (`:core:speech`, `:feature:voice`)**
   - Uses **Vosk Android (`0.3.75`)** offline speech recognition for **English** and **Hindi** with push-to-talk and toggle listening.
   - Shows a persistent foreground notification (`foregroundServiceType="microphone"`) whenever listening is active.
   - Allows editing before insertion via `AccessibilityService` (blocked on password fields) with clipboard fallback.
   - Transparently discloses that Odia has no verified offline Vosk small model.

6. **On-Demand Model Manager (`:feature:models`, `:data`, `:core:ai`)**
   - Downloads AI models only on explicit user request, with HTTP `Range` pause/resume, delete, SHA-256 checksum computation/verification, and preflight RAM/storage checks.
   - `AiModelLifecycleManager` enforces a single-slot heavy model policy to protect mid-range devices (4–6 GB RAM) from OOM.

## Strict Free & Open-Source Policy

- Zero paid APIs, zero mandatory API keys, zero ads, zero billing, zero analytics, zero Firebase.
- See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [MODEL_LICENSES.md](MODEL_LICENSES.md), and [PRIVACY.md](PRIVACY.md).
