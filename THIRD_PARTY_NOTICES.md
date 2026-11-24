# Third-Party Open-Source Notices

PAPI AI uses only verified open-source libraries. No proprietary SDKs, ad networks, analytics trackers, or closed-source billing libraries are linked into the application binary.

| Library / Component | Version | License | Source / Repository |
|---|---|---|---|
| **Tesseract4Android** (`cz.adaptech.tesseract4android:tesseract4android`) | 4.9.0 | Apache-2.0 | https://github.com/adaptech/Tesseract4Android |
| **ONNX Runtime Android** (`com.microsoft.onnxruntime:onnxruntime-android`) | 1.22.0 | MIT | https://github.com/microsoft/onnxruntime |
| **Vosk Android** (`com.alphacephei:vosk-android`) | 0.3.75 | Apache-2.0 | https://github.com/alphacep/vosk-android-demo |
| **AndroidX Core KTX / Activity / Lifecycle / Navigation / Room / DataStore** | Jetpack BOM / Stable | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| **Jetpack Compose & Material 3** | Compose BOM 2024.09.00 | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| **OkHttp** (`com.squareup.okhttp3:okhttp`) | 4.10.0 | Apache-2.0 | https://github.com/square/okhttp |
| **Coil Compose** (`io.coil-kt:coil-compose`) | 2.7.0 | Apache-2.0 | https://github.com/coil-kt/coil |
| **Kotlin Coroutines** | 1.10.2 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| **Robolectric** (test-only) | 4.16.1 | MIT | https://github.com/robolectric/robolectric |
| **JUnit 4** (test-only) | 4.13.2 | EPL-1.0 | https://github.com/junit-team/junit4 |
| **Bundled Google Fonts** (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`) | Static `.ttf` | SIL Open Font License 1.1 | https://fonts.google.com |

## System Fallback Notice

When reading aloud via Android's `TextToSpeech` API, if the user's device defaults to a proprietary pre-installed TTS engine (such as Google Speech Services or Samsung TTS) rather than an open-source engine (such as eSpeak-NG or RHVoice), PAPI AI explicitly labels the active engine as **Proprietary System TTS Fallback** in both the Home Dashboard and Settings.
