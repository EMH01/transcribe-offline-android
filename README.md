# Transcribe Offline Android

**Transcribe Offline Android** is a lightweight, privacy-first speech-to-text application designed to run **entirely on-device**.

It is intended for people and environments where reliable access to cloud AI cannot be assumed: intermittent or expensive connectivity, slow networks, older hardware, limited infrastructure, or regions and networks where modern online services are unavailable or restricted.

The app records speech locally and transcribes it with **Whisper Tiny multilingual Q5_1** through `whisper.cpp`. Once installed, it requires **no account, API key, server, cloud service, or Internet connection** to perform transcription.

## Why this project exists

Speech-to-text has become a common capability in modern AI products, but many implementations assume fast, continuous and unrestricted Internet access. That assumption excludes users for whom connectivity is unreliable, costly, slow, or simply unavailable.

This project explores a different baseline:

> useful voice transcription should remain available even when the network is not.

The goal is not to reproduce a large cloud AI stack on a low-end phone. It is to provide a focused, understandable and practical tool that works locally, respects device constraints, and remains usable in offline-first scenarios.

## Design principles

- **Offline by default** — runtime transcription never leaves the device.
- **Low dependency footprint** — no login, backend, analytics service or runtime API.
- **Lightweight UX** — one primary flow: **record → stop → read/edit → copy/share**.
- **Older-device aware** — ARM64 and ARMv7 builds, conservative UI, small quantized model.
- **Transparent performance** — the app shows audio duration, processing time and real-time factor (RTF).
- **Modular architecture** — the speech stack can later be reused by a fully local LLM assistant.
- **Reproducible builds** — the model and `whisper.cpp` revision are pinned and verified.

## Stable baseline: 0.2.1

The application has a working end-to-end offline pipeline that has been validated on real Android hardware:

```text
Microphone
    ↓
16 kHz mono PCM
    ↓
lightweight voice/silence trimming
    ↓
Whisper Tiny Q5_1
    ↓
editable transcription
    ↓
copy / share
```

Version **0.2.1** adds the first performance and reliability pass over the working baseline:

- lightweight energy-based voice activity trimming before inference;
- leading and trailing silence removal to avoid wasting CPU time;
- deterministic Whisper decoding (`temperature = 0`);
- blank and non-speech token suppression;
- stricter no-speech handling to reduce hallucinations on silence;
- visible audio duration, inference time and RTF;
- custom adaptive launcher icon;
- release-signing support for stable Android app identity.

These changes improve both **latency** and **transcription stability** on constrained phones. Version 0.2.1 has been validated as a functional release baseline on real hardware; inference speed still depends heavily on the device CPU, so the app exposes its own performance metrics instead of relying on desktop benchmarks.

## Architecture

```text
:app
  ├── Jetpack Compose / Material 3 UI
  ├── TranscribeViewModel
  └── packaged Whisper model
       │
       ├── :core-audio
       │     ├── AudioRecord
       │     ├── PCM 16 kHz mono
       │     └── lightweight voice activity trimming
       │
       ├── :core-speech
       │     └── SpeechToTextEngine contract
       │
       └── :core-whisper
             ├── WhisperTinyEngine
             ├── JNI bridge
             └── whisper.cpp
```

The UI does not depend directly on Whisper. It talks to the `SpeechToTextEngine` abstraction, which keeps the application replaceable and reusable.

That separation is intentional: a future local assistant can reuse `core-audio`, `core-speech` and `core-whisper`, then add a `core-llm` module without rewriting the speech pipeline.

## Offline behavior

The Android manifest intentionally contains **no `INTERNET` permission**.

At build time, the development machine downloads two pinned dependencies:

1. the selected `whisper.cpp` source revision;
2. `ggml-tiny-q5_1.bin`, whose SHA-256 is verified before packaging.

The final APK contains the model and native inference code. On the phone, transcription is therefore local and remains available in airplane mode.

## Model

- **Model:** `ggml-tiny-q5_1.bin`
- **Family:** Whisper Tiny multilingual
- **Quantization:** Q5_1
- **Approximate model size:** 32 MB
- **Runtime language:** Spanish (`es`)
- **SHA-256:** `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7`

The model binary is not committed to Git. Gradle downloads and verifies it before packaging it under `assets/models/`.

## Performance measurement

On-device inference speed varies substantially across CPUs. Instead of hiding that, the app exposes:

- recorded audio duration;
- voice duration after silence trimming;
- inference time;
- **RTF (real-time factor)**.

```text
RTF = inference time / processed audio duration
```

Examples:

- `RTF < 1.0` → faster than real time;
- `RTF = 1.0` → one second of compute per second of audio;
- `RTF > 1.0` → slower than real time.

This makes performance work measurable rather than subjective.

## Android compatibility

- **Minimum Android:** 8.0 / API 26
- **Native ABIs:** `arm64-v8a`, `armeabi-v7a`
- **Audio input:** 16-bit PCM, mono, 16 kHz
- Whisper inference runs on a dedicated background thread so the Compose UI remains responsive.

## User experience

The interface is intentionally small and direct:

- large primary recording action;
- clear recording state and timer;
- lightweight audio-level visualization;
- explicit processing state;
- editable transcription result;
- copy and share actions;
- light and dark Material 3 themes;
- adaptive Android launcher icon.

The goal is that a first-time user should be able to operate the app without instructions.

## Build

Requirements:

- Android Studio / JDK 17+
- Android SDK 35
- Android NDK
- CMake 3.22.1
- Git available during the first native build

Debug build:

```bash
./gradlew :app:assembleDebug
```

Release build:

```bash
./gradlew :app:assembleRelease
```

For real-device distribution, use a **stable signed release APK**, not the debug build. Release signing is configured locally so the private signing key never needs to be committed to the public repository.

The stable application identity uses a long-lived RSA signing certificate. Its SHA-256 certificate fingerprint is:

```text
F8:45:DB:90:80:06:0A:59:9D:80:00:99:34:52:93:D7:4C:7E:C4:24:51:49:E5:18:C8:43:0E:0D:AC:B4:B6:E0
```

Future stable APKs must be signed with the same private key so Android can install them as updates over the existing app.

See [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

## Project background

This project is also an experiment in **AI-assisted engineering across domain boundaries**.

Its author works primarily in **Python and Data Science rather than native Android development**. The project deliberately uses modern AI-assisted development to extend into an unfamiliar stack—Kotlin, Jetpack Compose, JNI, NDK and C++—while keeping the engineering process grounded in reproducible builds, device testing, explicit benchmarks and inspectable architecture.

The objective is not to present AI-generated code as expertise by itself. It is to explore how strong problem decomposition, validation and existing software/data skills can make new technical domains practically accessible.

## Roadmap

Near-term work:

- expand benchmarks across older Android hardware;
- tune thread count per CPU class;
- refine silence detection thresholds from real recordings;
- improve long-recording behavior;
- add local transcription history.

Future direction:

```text
Microphone
    ↓
Whisper
    ↓
local text
    ↓
small local LLM
    ↓
assistant response
    ↓
optional local TTS
```

The long-term goal is to reuse the offline speech stack as the input layer for a small, fully local AI assistant.

## Third-party software

`whisper.cpp` is developed by Georgi Gerganov and contributors and is used under its MIT license.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.
