# Build Instructions — PAPI AI

## Prerequisites
- **JDK**: OpenJDK 21 (or 17+)
- **Android SDK**: `compileSdk 36`, `targetSdk 36`, `minSdk 24`
- **Build Tools**: `36.0.0`
- **Gradle**: `9.3.1` (use `gradle` on PATH or Android Studio Gradle sync)

## Build Debug APK
```bash
gradle :app:assembleDebug
```
Output APK path:
`app/build/outputs/apk/debug/app-debug.apk`

## Run Unit & Robolectric Tests
```bash
gradle testDebugUnitTest
```

## Target Device Notes (Samsung Galaxy A52 / Android 14 / ARM64)
- Native libraries (`libtesseract.so`, `libleptonica.so`, `libonnxruntime.so`, `libvosk.so`, `libjnidispatch.so`) are packaged for `arm64-v8a` (as well as `armeabi-v7a`, `x86`, and `x86_64` for emulator testing).
- On-device models are **not** bundled in the APK to keep the installer lean; download only the languages you need in **Model Manager**.
