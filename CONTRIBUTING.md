# Contributing to PAPI AI

Thank you for contributing to PAPI AI!

## Strict FOSS & Honesty Guidelines
1. **Free & Open-Source Only**: Do not introduce proprietary SDKs, Firebase, ad networks, analytics, or dependencies requiring paid API keys.
2. **Audit Licenses**: Every new dependency must be added to `THIRD_PARTY_NOTICES.md`, and every model URL must be verified and listed in `MODEL_LICENSES.md`.
3. **No Fake Features**: Do not add placeholder buttons or claim unsupported language/model compatibility.
4. **Mid-Range Device Budget**: Test memory footprint for 4–6 GB RAM ARM64 devices (e.g., Samsung Galaxy A52) and ensure `AiModelLifecycleManager` unloads idle models before loading new ones.

## Development Workflow
1. Fork and create a feature branch.
2. Run unit and Robolectric UI tests:
   ```bash
   gradle testDebugUnitTest
   ```
3. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
4. Submit a pull request describing your changes and license audit updates.
