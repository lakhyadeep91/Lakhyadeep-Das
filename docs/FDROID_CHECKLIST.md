# F-Droid Preparation Checklist — PAPI AI

- [x] **100% Free & Open-Source Dependencies**: All libraries (`tesseract4android`, `onnxruntime-android`, `vosk-android`, AndroidX, OkHttp, Coil) are Apache-2.0 or MIT.
- [x] **No Proprietary SDKs**: Firebase, Google Play Services, AdMob, and proprietary analytics are completely excluded from `app/build.gradle.kts` and all module build files.
- [x] **No Prebuilt Binary Blobs in Repo**: No opaque `.aar` or `.jar` binaries committed in `libs/`; dependencies resolve from Maven Central, Google Maven, and JitPack.
- [x] **No Non-Free Model Bundling**: No large weights bundled in APK; models are downloaded on explicit user demand with license & source displayed before download.
- [x] **Reproducible Metadata**: `dependenciesInfo { includeInApk = false }` is configured in `app/build.gradle.kts` so Google signing block dependency info is omitted from APKs.
- [ ] **Reproducible Build & Signing**: Configure F-Droid metadata (`metadata/com.aistudio.papiai.vktrmz.yml`) pointing to tagged releases and verify `fdroid build` in an F-Droid buildserver container.
- [ ] **Fastlane Metadata**: Add `fastlane/metadata/android/en-US/` screenshots, icon, and description before submitting merge request to `fdroiddata`.
