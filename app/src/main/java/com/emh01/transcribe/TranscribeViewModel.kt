package com.emh01.transcribe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emh01.transcribe.audio.PcmAudioRecorder
import com.emh01.transcribe.audio.VoiceActivityTrimmer
import com.emh01.transcribe.whisper.WhisperBaseEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranscribeViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = PcmAudioRecorder()
    private val speechEngine = WhisperBaseEngine(application)
    private val preferences = application.getSharedPreferences(
        "transcribe_preferences",
        Application.MODE_PRIVATE,
    )
    private var glossary: String = preferences.getString(KEY_GLOSSARY, "").orEmpty()

    private val _uiState = MutableStateFlow(TranscribeUiState(glossary = glossary))
    val uiState: StateFlow<TranscribeUiState> = _uiState.asStateFlow()

    fun startRecording() {
        if (_uiState.value.stage == TranscribeStage.Recording ||
            _uiState.value.stage == TranscribeStage.Processing
        ) return

        _uiState.value = TranscribeUiState(
            stage = TranscribeStage.Recording,
            glossary = glossary,
        )
        recorder.start(
            onAmplitude = { amplitude ->
                _uiState.update { current ->
                    if (current.stage == TranscribeStage.Recording) {
                        current.copy(amplitude = amplitude)
                    } else {
                        current
                    }
                }
            },
            onError = { error ->
                _uiState.value = TranscribeUiState(
                    stage = TranscribeStage.Error,
                    errorMessage = error.message ?: "No se pudo usar el micrófono.",
                )
            },
        )
    }

    fun stopAndTranscribe() {
        if (_uiState.value.stage != TranscribeStage.Recording) return

        _uiState.update { it.copy(stage = TranscribeStage.Processing, amplitude = 0f) }
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
                result to prepared
            }.onSuccess { (result, prepared) ->
                _uiState.value = TranscribeUiState(
                    stage = TranscribeStage.Result,
                    text = result.text,
                    processingMs = result.elapsedMs,
                    originalAudioMs = prepared.originalDurationMs,
                    processedAudioMs = prepared.processedDurationMs,
                    glossary = glossary,
                )
            }.onFailure { error ->
                _uiState.value = TranscribeUiState(
                    stage = TranscribeStage.Error,
                    errorMessage = error.message ?: "No se pudo transcribir el audio.",
                    glossary = glossary,
                )
            }
        }
    }

    fun updateText(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun reset() {
        _uiState.value = TranscribeUiState(glossary = glossary)
    }

    fun saveGlossary(value: String) {
        glossary = value.trim().take(MAX_GLOSSARY_CHARS)
        preferences.edit().putString(KEY_GLOSSARY, glossary).apply()
        _uiState.update { it.copy(glossary = glossary) }
    }

    fun showMicrophonePermissionError() {
        _uiState.value = TranscribeUiState(
            stage = TranscribeStage.Error,
            errorMessage = "La app necesita permiso de micrófono para grabar.",
            glossary = glossary,
        )
    }

    override fun onCleared() {
        recorder.close()
        speechEngine.close()
        super.onCleared()
    }
}

enum class TranscribeStage {
    Idle,
    Recording,
    Processing,
    Result,
    Error,
}

data class TranscribeUiState(
    val stage: TranscribeStage = TranscribeStage.Idle,
    val text: String = "",
    val amplitude: Float = 0f,
    val processingMs: Long = 0L,
    val originalAudioMs: Long = 0L,
    val processedAudioMs: Long = 0L,
    val glossary: String = "",
    val errorMessage: String? = null,
) {
    val realtimeFactor: Float
        get() = if (processedAudioMs > 0) processingMs.toFloat() / processedAudioMs else 0f
}

private const val KEY_GLOSSARY = "local_glossary"
private const val MAX_GLOSSARY_CHARS = 500
