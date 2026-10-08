# Transcribe Offline Android

A lightweight Android dictation app designed to work **fully offline at runtime** on older/low-end phones. The app records 16 kHz mono PCM audio and transcribes Spanish locally with **Whisper Tiny multilingual Q5_1** through `whisper.cpp`.

## Goals

- Beautiful, friendly, lightweight and intuitive UI.
- One obvious flow: **record → stop → read/edit → copy/share**.
- No account, API key, cloud service or runtime Internet access.
- Spanish transcription on-device.
- Reusable architecture for a future local LLM assistant.

## Architecture

```text
:app
  ├── Compose / Material 3 UI
  ├── TranscribeViewModel
  └── model asset (packaged in APK)
       │
       ├── :core-audio
       │     └── AudioRecord → FloatArray PCM 16 kHz mono
       │
       ├── :core-speech
       │     └── SpeechToTextEngine contract
       │
       └── :core-whisper
             ├── WhisperTinyEngine
             ├── JNI bridge
             └── whisper.cpp (pinned build dependency)
```

The UI only talks to the speech engine abstraction. A future app can reuse `core-audio`, `core-speech` and `core-whisper` and add a `core-llm` module without rewriting the transcription stack.

## Offline behavior

The Android manifest intentionally contains **no `INTERNET` permission**. Once the APK is built and installed, model loading and inference happen locally.

The development machine does need Internet once during a clean build for two build-time dependencies:

1. CMake fetches a pinned `whisper.cpp` source commit.
2. `:app:prepareWhisperModel` downloads and SHA-256 verifies `ggml-tiny-q5_1.bin` (~32 MB), then packages it under `assets/models/`.

After installation, put the phone in airplane mode: recording and transcription continue to work.

## Model

- Model: `ggml-tiny-q5_1.bin`
- Type: Whisper Tiny multilingual, Q5_1 quantized
- Approximate size: 32 MB
- SHA-256: `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7`
- Runtime language: `es`

The binary is not committed to Git. Gradle downloads and verifies it before `preBuild`, then includes it in the APK.

## Build

Requirements:

- Android Studio with JDK 17+
- Android SDK 35
- Android NDK + CMake 3.22.1
- Git available to CMake for the pinned `whisper.cpp` fetch

Open the project and build normally, or run:

```bash
./gradlew :app:assembleDebug
```

The first clean build downloads the model and `whisper.cpp`. Subsequent builds reuse local build caches and the already downloaded model.

## Device compatibility

- Minimum Android: 8.0 / API 26
- Native ABIs: `arm64-v8a`, `armeabi-v7a`
- Audio: mono, PCM 16-bit, 16 kHz
- Whisper inference is serialized onto a dedicated background thread so the Compose UI remains responsive.

## Current scope

Implemented in this version:

- microphone permission flow;
- local PCM capture;
- lightweight recording level visualization;
- timer and clear recording state;
- Whisper Tiny Q5_1 local inference;
- editable result;
- copy/share;
- light/dark Material 3 UI;
- no runtime Internet permission.

Planned later: local history, longer-recording optimizations, benchmarks on the target Xiaomi, and the separate local-LLM assistant app.

## Third-party

`whisper.cpp` is developed by Georgi Gerganov and contributors and is used under its MIT license. See `THIRD_PARTY_NOTICES.md`.
