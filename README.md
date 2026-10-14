# Loquia

**Private, offline voice transcription and chat for Android.**

The name **Loquia** comes from the **Latin word *loqui***, meaning **“to speak”**. It reflects the project's purpose: making spoken words useful without relying on the cloud.

Loquia transcribes speech and runs a compact conversational assistant **entirely on your device**. No account, API key, server, or internet connection is required to use it after installation. It is designed for everyday use, especially where connectivity is limited, unreliable, expensive, or unavailable.

## Features

- **Transcribe:** record speech, review and edit the text, then copy or share it.
- **Improve writing:** optionally refine punctuation and wording while preserving the intended meaning.
- **Chat:** type a message or dictate it using the microphone. Dictated text remains editable and is only sent to the local assistant when you press **Send**.
- **Local conversations:** reopen or delete chats stored on the device.
- **Custom vocabulary:** optionally add terms for transcription correction; the list starts empty and is not used as chat context.
- **Clear performance feedback:** view audio and processing times, including real-time factor (RTF).
- **Light and dark themes.**

Loquia does not request Android's `INTERNET` permission. Audio transcription, writing improvement and chat are processed locally. Chat history is stored in app preferences **without encryption**; avoid storing sensitive conversations on a shared or unsecured device.

## Download

Get the latest signed APK from [GitHub Releases](https://github.com/EMH01/transcribe-offline-android/releases). Open the most recent release and download the official APK attached to it.

**Requirements:** Android 8.0 (API 26) or later. Local chat and writing assistance require a **64-bit ARM** device; supported 32-bit ARM devices can use transcription without the local LLM. Performance depends on available memory and processor speed.

## How it works

Loquia uses two quantized models packaged into the Android build:

| Task | On-device model |
| --- | --- |
| Speech recognition | Parakeet TDT 0.6B v3 (Q4_0) |
| Chat and writing improvement | LFM2.5 1.2B Instruct (Q4_K_M) |

The application is built with **Kotlin and Jetpack Compose**, with native inference through JNI. Separate audio, speech, transcription and text-generation modules keep the implementation maintainable.

Model weights and native dependencies are fetched and verified during the build; **end users do not need to download models separately**. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for licensing and attribution.

## Build from source

You will need **JDK 17+, Android SDK 35, Android NDK, CMake 3.22.1**, and Git for the native dependencies.

```bash
./gradlew :app:assembleDebug
```

For a release build:

```bash
./gradlew :app:assembleRelease
```

Distributable updates require the project's stable signing key; do not commit signing credentials. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

## Contributing and license

Loquia is open source under the [Apache License 2.0](LICENSE). Contributions and forks are welcome; retain the required copyright, license and attribution notices. See [CONTRIBUTING.md](CONTRIBUTING.md) and [NOTICE](NOTICE).

The **Loquia name and visual identity** are not licensed for use that suggests an unofficial fork is endorsed by or affiliated with the original project. See [TRADEMARKS.md](TRADEMARKS.md).

Built by [EMH01](https://github.com/EMH01). For previous releases and technical changes, see [CHANGELOG.md](CHANGELOG.md).
