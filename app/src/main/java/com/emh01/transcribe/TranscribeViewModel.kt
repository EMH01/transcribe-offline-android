package com.emh01.transcribe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emh01.transcribe.audio.PcmAudioRecorder
import com.emh01.transcribe.audio.VoiceActivityTrimmer
import com.emh01.transcribe.llm.LocalQwenTextEngine
import com.emh01.transcribe.speech.LocalVocabularyCorrector
import com.emh01.transcribe.whisper.WhisperBaseEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranscribeViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = PcmAudioRecorder()
    private val speechEngine = WhisperBaseEngine(application)
    private val textEngineLazy = lazy { LocalQwenTextEngine(application) }
    private val textEngine by textEngineLazy
    private val llmAvailable = LocalQwenTextEngine.isSupportedDevice()

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
            current.stage == TranscribeStage.Processing
        ) return

        _uiState.value = TranscribeUiState(
            mode = mode,
            glossary = glossary,
            llmAvailable = llmAvailable,
        )
    }

    fun startRecording() {
        val current = _uiState.value
        if (current.stage == TranscribeStage.Recording ||
            current.stage == TranscribeStage.Processing
        ) return

        _uiState.value = TranscribeUiState(
            mode = current.mode,
            stage = TranscribeStage.Recording,
            glossary = glossary,
            llmAvailable = llmAvailable,
        )

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
                val mode = _uiState.value.mode
                _uiState.value = TranscribeUiState(
                    mode = mode,
                    stage = TranscribeStage.Error,
                    errorMessage = error.message ?: "No se pudo usar el micrófono.",
                    glossary = glossary,
                    llmAvailable = llmAvailable,
                )
            },
        )
    }

    fun stopAndTranscribe() {
        val current = _uiState.value
        if (current.stage != TranscribeStage.Recording) return

        val mode = current.mode
        _uiState.update {
            it.copy(
                stage = TranscribeStage.Processing,
                amplitude = 0f,
            )
        }

        viewModelScope.launch {
            runCatching {
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

                Triple(
                    LocalVocabularyCorrector.correct(result.text, glossary),
                    result,
                    prepared,
                )
            }.onSuccess { (recognizedText, result, prepared) ->
                _uiState.value = if (mode == WorkspaceMode.Transcribe) {
                    TranscribeUiState(
                        mode = mode,
                        stage = TranscribeStage.Result,
                        text = recognizedText,
                        processingMs = result.elapsedMs,
                        originalAudioMs = prepared.originalDurationMs,
                        processedAudioMs = prepared.processedDurationMs,
                        glossary = glossary,
                        llmAvailable = llmAvailable,
                    )
                } else {
                    TranscribeUiState(
                        mode = mode,
                        stage = TranscribeStage.Result,
                        instruction = recognizedText,
                        processingMs = result.elapsedMs,
                        originalAudioMs = prepared.originalDurationMs,
                        processedAudioMs = prepared.processedDurationMs,
                        glossary = glossary,
                        llmAvailable = llmAvailable,
                    )
                }
            }.onFailure { error ->
                _uiState.value = TranscribeUiState(
                    mode = mode,
                    stage = TranscribeStage.Error,
                    errorMessage = error.message ?: "No se pudo transcribir el audio.",
                    glossary = glossary,
                    llmAvailable = llmAvailable,
                )
            }
        }
    }

    fun updateText(text: String) {
        _uiState.update {
            it.copy(
                text = text,
                improvementError = null,
                draftError = null,
            )
        }
    }

    fun updateInstruction(instruction: String) {
        _uiState.update {
            it.copy(
                instruction = instruction,
                draftError = null,
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

    fun generateDraft() {
        val current = _uiState.value
        if (current.mode != WorkspaceMode.Write ||
            current.stage != TranscribeStage.Result ||
            current.instruction.isBlank() ||
            current.isGeneratingDraft
        ) return

        if (!llmAvailable) {
            _uiState.update {
                it.copy(
                    draftError =
                        "La redacción con IA local necesita un dispositivo Android de 64 bits.",
                )
            }
            return
        }

        val instruction = current.instruction
        _uiState.update {
            it.copy(
                isGeneratingDraft = true,
                draftError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                textEngine.draft(
                    instruction = instruction,
                    glossary = glossary,
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        text = result.text,
                        isGeneratingDraft = false,
                        draftMs = result.elapsedMs,
                        draftError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGeneratingDraft = false,
                        draftError =
                            error.message ?: "No se pudo redactar el texto localmente.",
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

    fun reset() {
        val mode = _uiState.value.mode
        _uiState.value = TranscribeUiState(
            mode = mode,
            glossary = glossary,
            llmAvailable = llmAvailable,
        )
    }

    fun saveGlossary(value: String) {
        glossary = value.trim().take(MAX_GLOSSARY_CHARS)
        preferences.edit().putString(KEY_GLOSSARY, glossary).apply()
        _uiState.update { it.copy(glossary = glossary) }
    }

    fun showMicrophonePermissionError() {
        val mode = _uiState.value.mode
        _uiState.value = TranscribeUiState(
            mode = mode,
            stage = TranscribeStage.Error,
            errorMessage = "La app necesita permiso de micrófono para grabar.",
            glossary = glossary,
            llmAvailable = llmAvailable,
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
    Write,
}

enum class TranscribeStage {
    Idle,
    Recording,
    Processing,
    Result,
    Error,
}

data class TranscribeUiState(
    val mode: WorkspaceMode = WorkspaceMode.Transcribe,
    val stage: TranscribeStage = TranscribeStage.Idle,
    val text: String = "",
    val instruction: String = "",
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
    val isGeneratingDraft: Boolean = false,
    val draftMs: Long = 0L,
    val draftError: String? = null,
) {
    val realtimeFactor: Float
        get() = if (processedAudioMs > 0) {
            processingMs.toFloat() / processedAudioMs
        } else {
            0f
        }
}

private const val KEY_GLOSSARY = "local_glossary"
private const val MAX_GLOSSARY_CHARS = 500
private const val DEFAULT_GLOSSARY =
    "Esther María, Amarilys, Rodovaldo, Guillermina, Alejandro, Martín, Romel, Daniel"
