package com.emh01.transcribe.whisper

import android.content.res.AssetManager

internal object WhisperNative {
    init {
        System.loadLibrary("whisper_jni")
    }

    external fun initContextFromAsset(
        assetManager: AssetManager,
        assetPath: String,
    ): Long

    external fun transcribe(
        contextPtr: Long,
        audioData: FloatArray,
        threadCount: Int,
        language: String,
    ): String

    external fun freeContext(contextPtr: Long)
}
