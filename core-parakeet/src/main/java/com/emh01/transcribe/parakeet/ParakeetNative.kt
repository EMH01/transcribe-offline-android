package com.emh01.transcribe.parakeet

import android.content.res.AssetManager

internal object ParakeetNative {
    init {
        System.loadLibrary("parakeet_jni")
    }

    external fun initContextFromAsset(
        assetManager: AssetManager,
        assetPath: String,
    ): Long

    external fun transcribe(
        contextPtr: Long,
        audioData: FloatArray,
        threadCount: Int,
    ): String

    external fun freeContext(contextPtr: Long)
}
