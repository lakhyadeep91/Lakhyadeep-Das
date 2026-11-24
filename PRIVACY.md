# Privacy Policy & Permissions Architecture

PAPI AI is designed to protect user privacy by keeping screen contents, OCR images, voice audio, and translations local on the device.

## Data Collection & Network Policy
- **Zero Telemetry & Zero Ads**: PAPI AI contains no analytics SDKs, crash reporters, ad networks, or Firebase libraries.
- **No Mandatory Cloud Services**: Core Screen Reader, OCR, Voice Typing, and IndicTrans2 ONNX translation work offline once models are downloaded.
- **On-Demand Model Downloads Only**: Network requests occur only when you press **Download** in Model Manager or if you explicitly enable and configure your own self-hosted **LibreTranslate** server in Settings.

## Permissions & Safeguards
1. **AccessibilityService (`BIND_ACCESSIBILITY_SERVICE`)**:
   - Enabled explicitly by the user in Android System Settings.
   - Extracts visible non-password text only. Password fields (`isPassword` / password `InputType`) and credential/lockscreen/password-manager windows are skipped.
2. **Microphone (`RECORD_AUDIO`) + Foreground Service (`FOREGROUND_SERVICE_MICROPHONE`)**:
   - Requested only when tapping Push-to-Talk or Continuous Mic in Voice Typing.
   - Displays a persistent Android notification whenever the microphone is active.
3. **Screen Capture (`MediaProjection` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`)**:
   - Prompts for Android system consent per capture session.
   - Captures a single bitmap frame for region OCR and immediately terminates the `VirtualDisplay` and `MediaProjection`.
4. **Image Selection**:
   - Uses the zero-permission Android Photo Picker (`PickVisualMedia`); broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`) are never requested.

## User Data Controls
- Open **Privacy & Permissions** inside the app at any time to **Clear Local History** (Room database) or **Delete All Downloaded Models**.
