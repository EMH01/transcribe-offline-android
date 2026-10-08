package com.emh01.transcribe.speech

data class TranscriptionResult(
    val text: String,
    val elapsedMs: Long,
)

interface SpeechToTextEngine : AutoCloseable {
    suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int = 16_000,
        language: String = "es",
    ): TranscriptionResult

    override fun close()
}
