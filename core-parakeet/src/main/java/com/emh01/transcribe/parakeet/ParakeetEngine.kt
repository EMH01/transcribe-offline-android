package com.emh01.transcribe.parakeet

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

class ParakeetEngine(
    context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
) : SpeechToTextEngine {
    private val appContext = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "parakeet-inference").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }.asCoroutineDispatcher()

    private var contextPtr: Long = 0L
    private var closed = false

    override suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int,
        language: String,
        initialPrompt: String?,
    ): TranscriptionResult = withContext(dispatcher) {
        check(!closed) { "Parakeet engine is closed" }
        require(sampleRate == REQUIRED_SAMPLE_RATE) {
            "Parakeet expects 16 kHz PCM; received $sampleRate Hz"
        }
        require(samples.isNotEmpty()) { "No audio samples were provided" }

        // Parakeet TDT v3 detects the spoken language itself and does not expose
        // Whisper-style initial-prompt biasing. The app's existing local
        // vocabulary corrector still runs after transcription.
        @Suppress("UNUSED_VARIABLE")
        val requestedLanguage = language
        @Suppress("UNUSED_VARIABLE")
        val vocabularyHint = initialPrompt

        val ptr = ensureContext()
        val startedAt = SystemClock.elapsedRealtime()
        val text = ParakeetNative.transcribe(
            contextPtr = ptr,
            audioData = samples,
            threadCount = preferredThreadCount(),
        ).trim()

        TranscriptionResult(
            text = text,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private fun ensureContext(): Long {
        if (contextPtr == 0L) {
            contextPtr = ParakeetNative.initContextFromAsset(
                appContext.assets,
                modelAssetPath,
            )
            check(contextPtr != 0L) { "Parakeet model failed to load" }
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
                    ParakeetNative.freeContext(contextPtr)
                    contextPtr = 0L
                }
                closed = true
            }
        }
        dispatcher.close()
    }

    companion object {
        const val REQUIRED_SAMPLE_RATE = 16_000
        const val DEFAULT_MODEL_ASSET =
            "models/ggml-parakeet-tdt-0.6b-v3-q4_0.bin"
    }
}
