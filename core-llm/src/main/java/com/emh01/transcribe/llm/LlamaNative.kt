package com.emh01.transcribe.llm

internal object LlamaNative {
    init {
        System.loadLibrary("llm_jni")
    }

    external fun loadModel(path: String): Long

    external fun generate(
        modelPtr: Long,
        prompt: String,
        maxTokens: Int,
        threadCount: Int,
    ): String

    external fun freeModel(modelPtr: Long)
}
