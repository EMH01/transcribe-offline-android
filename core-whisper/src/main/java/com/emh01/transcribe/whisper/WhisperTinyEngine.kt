package com.emh01.transcribe.whisper

import android.content.Context
import android.os.SystemClock
import com.emh01.transcribe.speech.SpeechToTextEngine
import com.emh01.transcribe.speech.TranscriptionResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class WhisperTinyEngine(
    context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
) : SpeechToTextEngine {
    private val appContext = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "whisper-inference").apply { priority = Thread.NORM_PRIORITY - 1 }
    }.asCoroutineDispatcher()

    private var contextPtr: Long = 0L
    private var closed = false

    override suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int,
        language: String,
    ): TranscriptionResult = withContext(dispatcher) {
        check(!closed) { "Whisper engine is closed" }
        require(sampleRate == REQUIRED_SAMPLE_RATE) {
            "Whisper expects 16 kHz PCM; received $sampleRate Hz"
        }
        require(samples.isNotEmpty()) { "No audio samples were provided" }

        val ptr = ensureContext()
        val startedAt = SystemClock.elapsedRealtime()
        val text = WhisperNative.transcribe(
            contextPtr = ptr,
            audioData = samples,
            threadCount = preferredThreadCount(),
            language = language,
        ).trim()

        TranscriptionResult(
            text = text,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private fun ensureContext(): Long {
        if (contextPtr == 0L) {
            contextPtr = WhisperNative.initContextFromAsset(
                appContext.assets,
                modelAssetPath,
            )
            check(contextPtr != 0L) { "Whisper model failed to load" }
        }
        return contextPtr
    }

    private fun preferredThreadCount(): Int {
        val available = Runtime.getRuntime().availableProcessors()
        return min(4, max(1, available - 1))
    }

    override fun close() {
        if (closed) return
        runBlocking {
            withContext(dispatcher) {
                if (contextPtr != 0L) {
                    WhisperNative.freeContext(contextPtr)
                    contextPtr = 0L
                }
                closed = true
            }
        }
        dispatcher.close()
    }

    companion object {
        const val REQUIRED_SAMPLE_RATE = 16_000
        const val DEFAULT_MODEL_ASSET = "models/ggml-tiny-q5_1.bin"
    }
}
