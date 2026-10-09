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

## Qwen2.5 0.5B Instruct Q4_K_M

The experimental writing assistant uses `Qwen/Qwen2.5-0.5B-Instruct-GGUF` from Hugging Face.

Model file: `qwen2.5-0.5b-instruct-q4_k_m.gguf`

SHA-256:

`74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`

License: Apache 2.0.
