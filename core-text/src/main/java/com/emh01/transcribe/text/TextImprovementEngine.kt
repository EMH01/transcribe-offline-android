package com.emh01.transcribe.text

interface TextImprovementEngine : AutoCloseable {
    suspend fun improve(
        text: String,
        glossary: String = "",
    ): TextImprovementResult

    suspend fun draft(
        instruction: String,
        glossary: String = "",
    ): TextImprovementResult
}

data class TextImprovementResult(
    val text: String,
    val elapsedMs: Long,
)
