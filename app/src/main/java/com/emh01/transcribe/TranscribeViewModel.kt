package com.emh01.transcribe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emh01.transcribe.audio.PcmAudioRecorder
import com.emh01.transcribe.audio.VoiceActivityTrimmer
import com.emh01.transcribe.llm.LocalLlmTextEngine
import com.emh01.transcribe.parakeet.ParakeetEngine
import com.emh01.transcribe.speech.LocalVocabularyCorrector
import com.emh01.transcribe.text.TextChatMessage
import com.emh01.transcribe.text.TextChatRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranscribeViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = PcmAudioRecorder()
    private val speechEngine = ParakeetEngine(application)
    private val textEngineLazy = lazy { LocalLlmTextEngine(application) }
    private val textEngine by textEngineLazy
    private val llmAvailable = LocalLlmTextEngine.isSupportedDevice()

    private val preferences = application.getSharedPreferences(
        "transcribe_preferences",
        Application.MODE_PRIVATE,
    )

    private var glossary: String = if (preferences.contains(KEY_GLOSSARY)) {
        preferences.getString(KEY_GLOSSARY, "").orEmpty()
    } else {
        DEFAULT_GLOSSARY
    }

    private val _uiState = MutableStateFlow(
        TranscribeUiState(
            glossary = glossary,
            llmAvailable = llmAvailable,
        ),
    )
    val uiState: StateFlow<TranscribeUiState> = _uiState.asStateFlow()

    fun switchMode(mode: WorkspaceMode) {
        val current = _uiState.value
        if (current.mode == mode ||
            current.stage == TranscribeStage.Recording ||
            current.stage == TranscribeStage.Processing ||
            current.chatVoiceState != ChatVoiceState.Idle
        ) return

        _uiState.update {
            it.copy(
                mode = mode,
                stage = TranscribeStage.Idle,
                errorMessage = null,
            )
        }
    }

    fun startRecording() {
        val current = _uiState.value
        if (current.mode != WorkspaceMode.Transcribe ||
            current.stage == TranscribeStage.Recording ||
            current.stage == TranscribeStage.Processing ||
            current.chatVoiceState != ChatVoiceState.Idle
        ) return

        _uiState.update {
            it.copy(
                stage = TranscribeStage.Recording,
                amplitude = 0f,
                errorMessage = null,
            )
        }

        recorder.start(
            onAmplitude = { amplitude ->
                _uiState.update { state ->
                    if (state.stage == TranscribeStage.Recording) {
                        state.copy(amplitude = amplitude)
                    } else {
                        state
                    }
                }
            },
            onError = { error ->
                _uiState.update {
                    it.copy(
                        stage = TranscribeStage.Error,
                        amplitude = 0f,
                        errorMessage = error.message ?: "No se pudo usar el micrófono.",
                    )
                }
            },
        )
    }

    fun stopAndTranscribe() {
        val current = _uiState.value
        if (current.mode != WorkspaceMode.Transcribe ||
            current.stage != TranscribeStage.Recording
        ) return

        _uiState.update {
            it.copy(
                stage = TranscribeStage.Processing,
                amplitude = 0f,
            )
        }

        viewModelScope.launch {
            runCatching {
                transcribeRecorderAudio()
            }.onSuccess { payload ->
                _uiState.update {
                    it.copy(
                        stage = TranscribeStage.Result,
                        text = payload.text,
                        processingMs = payload.processingMs,
                        originalAudioMs = payload.originalAudioMs,
                        processedAudioMs = payload.processedAudioMs,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        stage = TranscribeStage.Error,
                        errorMessage = error.message ?: "No se pudo transcribir el audio.",
                    )
                }
            }
        }
    }

    fun updateText(text: String) {
        _uiState.update {
            it.copy(
                text = text,
                improvementError = null,
            )
        }
    }

    fun improveWriting() {
        val current = _uiState.value
        if (current.mode != WorkspaceMode.Transcribe ||
            current.stage != TranscribeStage.Result ||
            current.text.isBlank() ||
            current.isImprovingText
        ) return

        if (!llmAvailable) {
            _uiState.update {
                it.copy(
                    improvementError =
                        "La mejora de redacción local necesita un dispositivo Android de 64 bits.",
                )
            }
            return
        }

        val textToImprove = current.text
        _uiState.update {
            it.copy(
                isImprovingText = true,
                improvementError = null,
                originalText = it.originalText ?: textToImprove,
            )
        }

        viewModelScope.launch {
            runCatching {
                textEngine.improve(
                    text = textToImprove,
                    glossary = glossary,
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        text = result.text.ifBlank { textToImprove },
                        isImprovingText = false,
                        improvementMs = result.elapsedMs,
                        improvementError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isImprovingText = false,
                        improvementError =
                            error.message ?: "No se pudo mejorar la redacción localmente.",
                    )
                }
            }
        }
    }

    fun restoreOriginalText() {
        _uiState.update { current ->
            val original = current.originalText ?: return@update current
            current.copy(
                text = original,
                originalText = null,
                improvementMs = 0L,
                improvementError = null,
            )
        }
    }

    fun updateChatInput(value: String) {
        _uiState.update {
            it.copy(
                chatInput = value.take(MAX_CHAT_INPUT_CHARS),
                chatError = null,
            )
        }
    }

    fun startChatRecording() {
        val current = _uiState.value
        if (current.mode != WorkspaceMode.Assistant ||
            current.chatVoiceState != ChatVoiceState.Idle ||
            current.stage == TranscribeStage.Recording ||
            current.stage == TranscribeStage.Processing
        ) return

        _uiState.update {
            it.copy(
                chatVoiceState = ChatVoiceState.Recording,
                chatVoiceAmplitude = 0f,
                chatError = null,
            )
        }

        recorder.start(
            onAmplitude = { amplitude ->
                _uiState.update { state ->
                    if (state.chatVoiceState == ChatVoiceState.Recording) {
                        state.copy(chatVoiceAmplitude = amplitude)
                    } else {
                        state
                    }
                }
            },
            onError = { error ->
                _uiState.update {
                    it.copy(
                        chatVoiceState = ChatVoiceState.Idle,
                        chatVoiceAmplitude = 0f,
                        chatError = error.message ?: "No se pudo usar el micrófono.",
                    )
                }
            },
        )
    }

    fun stopChatRecording() {
        val current = _uiState.value
        if (current.mode != WorkspaceMode.Assistant ||
            current.chatVoiceState != ChatVoiceState.Recording
        ) return

        _uiState.update {
            it.copy(
                chatVoiceState = ChatVoiceState.Processing,
                chatVoiceAmplitude = 0f,
                chatError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                transcribeRecorderAudio()
            }.onSuccess { payload ->
                _uiState.update { state ->
                    val combined = listOf(state.chatInput.trim(), payload.text.trim())
                        .filter(String::isNotEmpty)
                        .joinToString(" ")
                        .take(MAX_CHAT_INPUT_CHARS)

                    state.copy(
                        chatInput = combined,
                        chatVoiceState = ChatVoiceState.Idle,
                        chatVoiceProcessingMs = payload.processingMs,
                        chatVoiceAmplitude = 0f,
                        chatError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        chatVoiceState = ChatVoiceState.Idle,
                        chatVoiceAmplitude = 0f,
                        chatError =
                            error.message ?: "No se pudo convertir la voz en texto.",
                    )
                }
            }
        }
    }

    fun sendChatMessage() {
        val current = _uiState.value
        val input = current.chatInput.trim()

        if (current.mode != WorkspaceMode.Assistant ||
            input.isEmpty() ||
            current.isGeneratingChat ||
            current.chatVoiceState != ChatVoiceState.Idle
        ) return

        if (!llmAvailable) {
            _uiState.update {
                it.copy(
                    chatError =
                        "El asistente local necesita un dispositivo Android de 64 bits.",
                )
            }
            return
        }

        val userMessage = ChatMessage(
            role = ChatRole.User,
            text = input,
        )
        val conversation = current.chatMessages + userMessage

        _uiState.update {
            it.copy(
                chatInput = "",
                chatMessages = conversation,
                isGeneratingChat = true,
                chatError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                textEngine.chat(
                    messages = conversation
                        .takeLast(MAX_CONTEXT_MESSAGES)
                        .map { message ->
                            TextChatMessage(
                                role = if (message.role == ChatRole.User) {
                                    TextChatRole.User
                                } else {
                                    TextChatRole.Assistant
                                },
                                text = message.text,
                            )
                        },
                    glossary = glossary,
                )
            }.onSuccess { result ->
                val answer = result.text.trim()
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + ChatMessage(
                            role = ChatRole.Assistant,
                            text = answer.ifBlank {
                                "No pude generar una respuesta útil. Inténtalo de nuevo."
                            },
                            elapsedMs = result.elapsedMs,
                        ),
                        isGeneratingChat = false,
                        chatError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGeneratingChat = false,
                        chatError =
                            error.message ?: "No se pudo generar la respuesta local.",
                    )
                }
            }
        }
    }

    fun clearChat() {
        val current = _uiState.value
        if (current.isGeneratingChat ||
            current.chatVoiceState != ChatVoiceState.Idle
        ) return

        _uiState.update {
            it.copy(
                chatInput = "",
                chatMessages = emptyList(),
                chatError = null,
                chatVoiceProcessingMs = 0L,
            )
        }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                stage = TranscribeStage.Idle,
                text = "",
                amplitude = 0f,
                processingMs = 0L,
                originalAudioMs = 0L,
                processedAudioMs = 0L,
                errorMessage = null,
                isImprovingText = false,
                originalText = null,
                improvementMs = 0L,
                improvementError = null,
            )
        }
    }

    fun saveGlossary(value: String) {
        glossary = value.trim().take(MAX_GLOSSARY_CHARS)
        preferences.edit().putString(KEY_GLOSSARY, glossary).apply()
        _uiState.update { it.copy(glossary = glossary) }
    }

    fun showMicrophonePermissionError() {
        _uiState.update {
            it.copy(
                stage = TranscribeStage.Error,
                errorMessage = "La app necesita permiso de micrófono para grabar.",
            )
        }
    }

    fun showChatMicrophonePermissionError() {
        _uiState.update {
            it.copy(
                chatError = "La app necesita permiso de micrófono para dictar mensajes.",
            )
        }
    }

    private suspend fun transcribeRecorderAudio(): SpeechPayload {
        val samples = recorder.stop()
        require(samples.isNotEmpty()) { "No se detectó audio." }

        val prepared = VoiceActivityTrimmer.prepare(samples)
        require(prepared.samples.isNotEmpty()) {
            "No se detectó voz suficiente. Acércate un poco al micrófono y vuelve a intentarlo."
        }

        val result = speechEngine.transcribe(
            samples = prepared.samples,
            language = "es",
            initialPrompt = glossary.takeIf { it.isNotBlank() },
        )

        return SpeechPayload(
            text = LocalVocabularyCorrector.correct(result.text, glossary),
            processingMs = result.elapsedMs,
            originalAudioMs = prepared.originalDurationMs,
            processedAudioMs = prepared.processedDurationMs,
        )
    }

    override fun onCleared() {
        recorder.close()
        speechEngine.close()
        if (textEngineLazy.isInitialized()) {
            textEngine.close()
        }
        super.onCleared()
    }
}

enum class WorkspaceMode {
    Transcribe,
    Assistant,
}

enum class TranscribeStage {
    Idle,
    Recording,
    Processing,
    Result,
    Error,
}

enum class ChatVoiceState {
    Idle,
    Recording,
    Processing,
}

enum class ChatRole {
    User,
    Assistant,
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val elapsedMs: Long = 0L,
)

data class TranscribeUiState(
    val mode: WorkspaceMode = WorkspaceMode.Transcribe,
    val stage: TranscribeStage = TranscribeStage.Idle,
    val text: String = "",
    val amplitude: Float = 0f,
    val processingMs: Long = 0L,
    val originalAudioMs: Long = 0L,
    val processedAudioMs: Long = 0L,
    val glossary: String = "",
    val errorMessage: String? = null,
    val llmAvailable: Boolean = false,
    val isImprovingText: Boolean = false,
    val originalText: String? = null,
    val improvementMs: Long = 0L,
    val improvementError: String? = null,
    val chatInput: String = "",
    val chatMessages: List<ChatMessage> = emptyList(),
    val isGeneratingChat: Boolean = false,
    val chatError: String? = null,
    val chatVoiceState: ChatVoiceState = ChatVoiceState.Idle,
    val chatVoiceAmplitude: Float = 0f,
    val chatVoiceProcessingMs: Long = 0L,
) {
    val realtimeFactor: Float
        get() = if (processedAudioMs > 0) {
            processingMs.toFloat() / processedAudioMs
        } else {
            0f
        }
}

private data class SpeechPayload(
    val text: String,
    val processingMs: Long,
    val originalAudioMs: Long,
    val processedAudioMs: Long,
)

private const val KEY_GLOSSARY = "local_glossary"
private const val MAX_GLOSSARY_CHARS = 500
private const val MAX_CHAT_INPUT_CHARS = 3_000
private const val MAX_CONTEXT_MESSAGES = 6
private const val DEFAULT_GLOSSARY =
    "Esther María, Amarilys, Rodovaldo, Guillermina, Alejandro, Martín, Romel, Daniel"
