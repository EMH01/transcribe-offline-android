package com.emh01.transcribe.llm

internal object LlamaNative {
    init {
        System.loadLibrary("llm_jni")
    }

    external fun loadModel(path: String): Long

    /**
     * The native layer applies the chat template embedded in the GGUF model.
     * [userPrompt] must therefore contain plain user-visible instructions,
     * not model-specific special tokens.
     */
    external fun generate(
        modelPtr: Long,
        userPrompt: String,
        maxTokens: Int,
        threadCount: Int,
    ): String

    external fun freeModel(modelPtr: Long)
}
