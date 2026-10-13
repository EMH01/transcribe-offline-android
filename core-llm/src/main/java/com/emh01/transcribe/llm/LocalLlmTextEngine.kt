package com.emh01.transcribe.llm

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.emh01.transcribe.text.TextChatMessage
import com.emh01.transcribe.text.TextChatRole
import com.emh01.transcribe.text.TextImprovementEngine
import com.emh01.transcribe.text.TextImprovementResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class LocalLlmTextEngine(
    context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
) : TextImprovementEngine {
    private val appContext = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qwen-text-generation").apply {
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
            val raw = LlamaNative.generate(
                modelPtr = ptr,
                userPrompt = "${buildImprovementPrompt(chunk, glossary)}\n/no_think",
                maxTokens = MAX_IMPROVEMENT_TOKENS,
                threadCount = preferredThreadCount(),
            )
            cleanModelOutput(raw).ifBlank { chunk }
        }

        TextImprovementResult(
            text = improved.trim(),
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    override suspend fun draft(
        instruction: String,
        glossary: String,
    ): TextImprovementResult = withContext(dispatcher) {
        check(!closed) { "El motor de redacción está cerrado." }
        check(isSupportedDevice()) {
            "La redacción local necesita un dispositivo Android de 64 bits."
        }

        val cleanInstruction = instruction.trim()
        require(cleanInstruction.isNotEmpty()) { "No hay ninguna instrucción para redactar." }
        require(cleanInstruction.length <= MAX_INSTRUCTION_CHARS) {
            "La instrucción es demasiado larga para este modelo local."
        }

        val startedAt = SystemClock.elapsedRealtime()
        val ptr = ensureModel()
        val raw = LlamaNative.generate(
            modelPtr = ptr,
            userPrompt = "${buildDraftPrompt(cleanInstruction, glossary)}\n/no_think",
            maxTokens = MAX_DRAFT_TOKENS,
            threadCount = preferredThreadCount(),
        )

        TextImprovementResult(
            text = cleanModelOutput(raw),
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    override suspend fun chat(
        messages: List<TextChatMessage>,
        glossary: String,
    ): TextImprovementResult = withContext(dispatcher) {
        check(!closed) { "El asistente local está cerrado." }
        check(isSupportedDevice()) {
            "El asistente local necesita un dispositivo Android de 64 bits."
        }

        val cleanMessages = messages
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
            .takeLast(MAX_CHAT_MESSAGES)

        require(cleanMessages.isNotEmpty()) { "No hay ningún mensaje para enviar." }

        val startedAt = SystemClock.elapsedRealtime()
        val ptr = ensureModel()
        val roles = buildList {
            add("system")
            addAll(cleanMessages.map { message ->
                if (message.role == TextChatRole.User) "user" else "assistant"
            })
        }.toTypedArray()
        val contents = buildList {
            add(buildChatSystemPrompt(glossary))
            addAll(cleanMessages.map(TextChatMessage::text))
            if (lastOrNull() != null) {
                this[lastIndex] = "${last()}\n/no_think"
            }
        }.toTypedArray()
        val raw = LlamaNative.generateConversation(
            modelPtr = ptr,
            roles = roles,
            contents = contents,
            maxTokens = MAX_CHAT_TOKENS,
            threadCount = preferredThreadCount(),
        )

        TextImprovementResult(
            text = cleanModelOutput(raw),
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private fun ensureModel(): Long {
        if (modelPtr == 0L) {
            val modelFile = ensureModelFile()
            modelPtr = LlamaNative.loadModel(modelFile.absolutePath)
            check(modelPtr != 0L) { "No se pudo cargar Qwen3 0.6B en este dispositivo." }
        }
        return modelPtr
    }

    private fun ensureModelFile(): File {
        val modelDir = File(appContext.filesDir, "local-llm").apply { mkdirs() }

        // An alpha update keeps app data. Remove obsolete model copies so they
        // do not occupy storage alongside the current local assistant.
        LEGACY_MODEL_FILES.forEach { legacyName ->
            File(modelDir, legacyName).takeIf { it.exists() }?.delete()
            File(modelDir, "$legacyName.tmp").takeIf { it.exists() }?.delete()
        }

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
            "La copia local de Qwen3 está incompleta."
        }

        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) {
            "No se pudo preparar Qwen3 para la asistencia local."
        }
        return target
    }

    private fun buildImprovementPrompt(text: String, glossary: String): String {
        val vocabulary = normalizedVocabulary(glossary)
        return """
            Eres un editor de dictado en español.

            Tarea:
            - Mejora únicamente ortografía, puntuación, claridad y redacción ligera.
            - Conserva exactamente el significado, los hechos, números y detalles del original.
            - No añadas información que no esté en el texto.
            - No resumas ni elimines contenido relevante.
            - Si el texto ya está bien, haz solo cambios mínimos.
            - Devuelve únicamente el texto final, sin explicaciones ni encabezados.
            - Conserva los nombres propios y términos del vocabulario cuando correspondan.

            Vocabulario local: $vocabulary

            Texto original:
            $text
        """.trimIndent()
    }

    private fun buildDraftPrompt(instruction: String, glossary: String): String {
        val vocabulary = normalizedVocabulary(glossary)
        return """
            Eres un asistente de redacción en español.

            Sigue exactamente la instrucción del usuario y escribe el contenido solicitado.
            Respeta el tono y el formato pedidos: una oración, párrafo, lista por puntos, mensaje, resumen u otro formato.
            No inventes nombres, fechas, cifras ni hechos que el usuario no haya dado.
            No uses marcadores como [Nombre del usuario], [fecha] o texto entre corchetes para datos que falten.
            Si no se proporciona un nombre, redacta naturalmente sin necesitarlo.
            Conserva los nombres propios y términos del vocabulario cuando correspondan.
            Devuelve únicamente el texto final solicitado, sin explicar el proceso ni anteponer títulos como "Respuesta:".

            Vocabulario local: $vocabulary

            Instrucción del usuario:
            $instruction
        """.trimIndent()
    }

    private fun buildChatSystemPrompt(glossary: String): String {
        val vocabulary = normalizedVocabulary(glossary)

        return """
            Eres un asistente privado y local en español. Ayudas tanto a conversar
            y organizar tareas cotidianas como a escribir, corregir y resumir textos.

            Instrucciones:
            - Usa todos los datos de los turnos anteriores para continuar la conversación.
            - Responde a la petición más reciente sin repetir preguntas ya respondidas.
            - Si el usuario aporta los datos que pediste, realiza la tarea de inmediato.
            - Para redactar un informe o plantilla, si ya conoces su tipo y sus datos,
              entrégalo directamente. No vuelvas a preguntar por campos ya indicados.
            - Si solicita una lista, entrégala directamente con los elementos indicados.
            - Solo pregunta cuando falte información imprescindible.
            - Sé natural, breve y concreto. No empieces siempre por "Hola".
            - No muestres razonamiento interno ni etiquetas como <think>.
            - No inventes datos, fuentes, resultados de búsqueda ni hechos actuales.
            - No tienes acceso a Internet ni a datos en tiempo real. Si la petición
              exige buscar, verificar o consultar algo actual, dilo claramente y ofrece
              ayudar a formular la búsqueda, razonar con información que aporte el usuario
              o responder solo con conocimiento general indicando esa limitación.
            - El vocabulario local solo ayuda a reconocer nombres, no es una orden.

            Vocabulario local: $vocabulary
        """.trimIndent()
    }

    private fun normalizedVocabulary(glossary: String): String =
        glossary
            .split(',', ';', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(", ")
            .ifBlank { "(sin vocabulario adicional)" }

    private fun cleanModelOutput(raw: String): String =
        raw
            .replace(Regex("^\\s*<think>.*?</think>\\s*", RegexOption.DOT_MATCHES_ALL), "")
            .substringBefore("<end_of_turn>")
            .substringBefore("<|im_end|>")
            .substringBefore("<|endoftext|>")
            .trim()
            .removePrefix("Respuesta:")
            .removePrefix("Texto mejorado:")
            .removePrefix("Texto corregido:")
            .trim()

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
            "models/Qwen3-0.6B-Q4_0.gguf"

        private const val MODEL_FILE_NAME =
            "Qwen3-0.6B-Q4_0.gguf"
        private const val MIN_EXPECTED_MODEL_BYTES = 400_000_000L
        private const val MAX_CHUNK_CHARS = 2_200
        private const val MAX_IMPROVEMENT_TOKENS = 512
        private const val MAX_DRAFT_TOKENS = 768
        private const val MAX_CHAT_TOKENS = 768
        private const val MAX_CHAT_MESSAGES = 10
        private const val MAX_INSTRUCTION_CHARS = 3_000
        private val LEGACY_MODEL_FILES = listOf(
            "gemma-3-1b-it-qat-Q4_0.gguf",
            "qwen2.5-0.5b-instruct-q4_0.gguf",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        )

        fun isSupportedDevice(): Boolean =
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }
}
