# AI Model Licenses & Compatibility Audit

No large AI models are bundled inside the PAPI AI APK. All models below are downloaded strictly on user request via the in-app **Model Manager**, with computed SHA-256 digests recorded locally and verified whenever upstream checksums are available.

| Model ID | Model Name | Languages | Approx. Size | Min Free RAM | License | Verified Source |
|---|---|---|---|---|---|---|
| `tess-eng` | Tesseract OCR `tessdata_fast` English | English (`eng`) | ~4.1 MB | 128 MB | Apache-2.0 | https://github.com/tesseract-ocr/tessdata_fast |
| `tess-hin` | Tesseract OCR `tessdata_fast` Hindi | Hindi (`hin`) | ~1.1 MB | 128 MB | Apache-2.0 | https://github.com/tesseract-ocr/tessdata_fast |
| `tess-ori` | Tesseract OCR `tessdata_fast` Odia | Odia (`ori`) | ~1.48 MB | 128 MB | Apache-2.0 | https://github.com/tesseract-ocr/tessdata_fast |
| `vosk-en` | `vosk-model-small-en-us-0.15` | English (`en-US`) | ~40 MB | 350 MB | Apache-2.0 | https://alphacephei.com/vosk/models |
| `vosk-hi` | `vosk-model-small-hi-0.22` | Hindi (`hi-IN`) | ~42 MB | 350 MB | Apache-2.0 | https://alphacephei.com/vosk/models |
| `indictrans2-en-indic` | IndicTrans2 Distilled 200M ONNX INT8 (En -> Indic) | English -> Hindi, Odia | ~282 MB | 900 MB | MIT | https://huggingface.co/Aji230923/indictrans2-en-indic-dist-200M-onnx (derived from `ai4bharat/indictrans2-en-indic-dist-200M`) |
| `indictrans2-indic-en` | IndicTrans2 Distilled 200M ONNX INT8 (Indic -> En) | Hindi, Odia -> English | ~280 MB | 900 MB | MIT | https://huggingface.co/ngiftsun/indictrans2-indic-en-int8-mobile (derived from `ai4bharat/indictrans2-indic-en-dist-200M`) |

## Honest Unsupported Language Disclosures

1. **Odia Offline Voice Typing (ASR)**: Alpha Cephei does not publish a verified `vosk-model-small-or` model. PAPI AI explicitly marks Odia offline ASR as unavailable rather than inventing or mislabeling a model.
2. **Odia Text-to-Speech (TTS)**: Stock Android TTS engines rarely include `or-IN` voices. PAPI AI runs `TextToSpeech.isLanguageAvailable(Locale("or", "IN"))` at runtime and warns the user whenever Odia TTS is not verified on the selected engine.
