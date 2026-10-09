package com.emh01.transcribe.llm

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.emh01.transcribe.text.TextImprovementEngine
import com.emh01.transcribe.text.TextImprovementResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class LocalQwenTextEngine(
    context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
) : TextImprovementEngine {
    private val appContext = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qwen-text-improvement").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }.asCoroutineDispatcher()

    private var modelPtr: Long = 0L
    private var closed = false

    override suspend fun improve(
        text: String,
        glossary: String,
    ): TextImprovementResult = withContext(dispatcher) {
        check(!closed) { "El motor de redacción está cerrado." }
        check(isSupportedDevice()) {
            "La mejora de redacción local necesita un dispositivo Android de 64 bits."
        }

        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "No hay texto para mejorar." }

        val startedAt = SystemClock.elapsedRealtime()
        val ptr = ensureModel()
        val chunks = splitIntoChunks(cleanText)

        val improved = chunks.joinToString(separator = "\n\n") { chunk ->
            val prompt = buildPrompt(chunk, glossary)
            val raw = LlamaNative.generate(
                modelPtr = ptr,
                prompt = prompt,
                maxTokens = MAX_OUTPUT_TOKENS,
                threadCount = preferredThreadCount(),
            )
            cleanModelOutput(raw).ifBlank { chunk }
        }

        TextImprovementResult(
            text = improved.trim(),
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private fun ensureModel(): Long {
        if (modelPtr == 0L) {
            val modelFile = ensureModelFile()
            modelPtr = LlamaNative.loadModel(modelFile.absolutePath)
            check(modelPtr != 0L) { "No se pudo cargar el modelo local de redacción." }
        }
        return modelPtr
    }

    private fun ensureModelFile(): File {
        val modelDir = File(appContext.filesDir, "local-llm").apply { mkdirs() }
        val target = File(modelDir, MODEL_FILE_NAME)
        if (target.exists() && target.length() > MIN_EXPECTED_MODEL_BYTES) {
            return target
        }

        val temporary = File(modelDir, "$MODEL_FILE_NAME.tmp")
        if (temporary.exists()) temporary.delete()

        appContext.assets.open(modelAssetPath).buffered(1024 * 1024).use { input ->
            temporary.outputStream().buffered(1024 * 1024).use { output ->
                input.copyTo(output, 1024 * 1024)
            }
        }

        check(temporary.length() > MIN_EXPECTED_MODEL_BYTES) {
            "La copia local del modelo de redacción está incompleta."
        }

        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) {
            "No se pudo preparar el modelo local de redacción."
        }
        return target
    }

    private fun buildPrompt(text: String, glossary: String): String {
        val vocabulary = glossary
            .split(',', ';', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(", ")
            .ifBlank { "(sin vocabulario adicional)" }

        val system = """
            Eres un editor de dictado en español.
            Devuelve únicamente el texto final mejorado, sin explicaciones, títulos ni comentarios.
            Corrige ortografía, puntuación y redacción ligera.
            Mantén el significado, los hechos, números y detalles del texto original.
            No inventes información ni elimines contenido relevante.
            Conserva exactamente los nombres propios y términos del vocabulario local cuando correspondan.
            Vocabulario local: $vocabulary
        """.trimIndent()

        return buildString {
            append("<|im_start|>system\n")
            append(system)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append("Mejora este dictado:\n")
            append(text)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    private fun cleanModelOutput(raw: String): String {
        return raw
            .substringBefore("<|im_end|>")
            .substringBefore("<|endoftext|>")
            .trim()
            .removePrefix("Texto mejorado:")
            .removePrefix("Texto corregido:")
            .trim()
    }

    private fun splitIntoChunks(text: String): List<String> {
        if (text.length <= MAX_CHUNK_CHARS) return listOf(text)

        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val result = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val value = current.toString().trim()
            if (value.isNotEmpty()) result += value
            current.clear()
        }

        for (sentence in sentences) {
            if (sentence.length > MAX_CHUNK_CHARS) {
                flush()
                sentence.chunked(MAX_CHUNK_CHARS).forEach { part ->
                    if (part.isNotBlank()) result += part.trim()
                }
                continue
            }

            if (current.isNotEmpty() && current.length + 1 + sentence.length > MAX_CHUNK_CHARS) {
                flush()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        flush()

        return result.ifEmpty { listOf(text) }
    }

    private fun preferredThreadCount(): Int {
        val available = Runtime.getRuntime().availableProcessors()
        return min(4, max(1, available - 1))
    }

    override fun close() {
        if (closed) return
        runBlocking {
            withContext(dispatcher) {
                if (modelPtr != 0L) {
                    LlamaNative.freeModel(modelPtr)
                    modelPtr = 0L
                }
                closed = true
            }
        }
        dispatcher.close()
    }

    companion object {
        const val DEFAULT_MODEL_ASSET =
            "models/qwen2.5-0.5b-instruct-q4_k_m.gguf"

        private const val MODEL_FILE_NAME =
            "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        private const val MIN_EXPECTED_MODEL_BYTES = 450_000_000L
        private const val MAX_CHUNK_CHARS = 2_200
        private const val MAX_OUTPUT_TOKENS = 512

        fun isSupportedDevice(): Boolean =
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }
}
