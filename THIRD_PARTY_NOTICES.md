# Third-party notices

## whisper.cpp

This project builds against `ggml-org/whisper.cpp`, pinned to commit:

`d1be6fde11ac6e0407606b4e42fe72d34add8037`

Project: https://github.com/ggml-org/whisper.cpp

License: MIT.

## Whisper Base Q5_1 model

The build downloads the `ggml-base-q5_1.bin` model distributed for `whisper.cpp` from the `ggerganov/whisper.cpp` Hugging Face repository and verifies its SHA-256 before packaging it into the APK.


## llama.cpp

This project builds against `ggml-org/llama.cpp` for fully local text generation.

Project: https://github.com/ggml-org/llama.cpp

License: MIT.

## Qwen2.5 0.5B Instruct Q4_0

The experimental writing assistant uses `Qwen/Qwen2.5-0.5B-Instruct-GGUF` from Hugging Face.

Model file: `qwen2.5-0.5b-instruct-q4_0.gguf`

SHA-256:

`7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed`

License: Apache 2.0.
