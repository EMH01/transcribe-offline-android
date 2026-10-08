# Third-party notices

## whisper.cpp

This project builds against `ggml-org/whisper.cpp`, pinned to commit:

`d1be6fde11ac6e0407606b4e42fe72d34add8037`

Project: https://github.com/ggml-org/whisper.cpp

License: MIT.

## Whisper Tiny Q5_1 model

The build downloads the `ggml-tiny-q5_1.bin` model distributed for `whisper.cpp` from the `ggerganov/whisper.cpp` Hugging Face repository and verifies its SHA-256 before packaging it into the APK.
