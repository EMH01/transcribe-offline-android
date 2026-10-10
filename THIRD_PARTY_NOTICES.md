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

## Gemma 3 1B IT QAT Q4_0

The alpha2 writing assistant uses `ggml-org/gemma-3-1b-it-qat-GGUF` from Hugging Face.

Model file: `gemma-3-1b-it-qat-Q4_0.gguf`

SHA-256:

`ef60e4e91a738c99ae9976b050657dfe68a4007a0ccca121b55ec0c413dccd58`

Terms: Gemma Terms of Use.
