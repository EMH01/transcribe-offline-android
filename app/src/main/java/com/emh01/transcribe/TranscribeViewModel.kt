package com.emh01.transcribe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emh01.transcribe.audio.PcmAudioRecorder
import com.emh01.transcribe.whisper.WhisperTinyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranscribeViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = PcmAudioRecorder()
    private val speechEngine = WhisperTinyEngine(application)

    private val _uiState = MutableStateFlow(TranscribeUiState())
    val uiState: StateFlow<TranscribeUiState> = _uiState.asStateFlow()

    fun startRecording() {
        if (_uiState.value.stage == TranscribeStage.Recording ||
            _uiState.value.stage == TranscribeStage.Processing
        ) return

        _uiState.value = TranscribeUiState(stage = TranscribeStage.Recording)
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
                speechEngine.transcribe(samples = samples, language = "es")
            }.onSuccess { result ->
                _uiState.value = TranscribeUiState(
                    stage = TranscribeStage.Result,
                    text = result.text,
                    processingMs = result.elapsedMs,
                )
            }.onFailure { error ->
                _uiState.value = TranscribeUiState(
                    stage = TranscribeStage.Error,
                    errorMessage = error.message ?: "No se pudo transcribir el audio.",
                )
            }
        }
    }

    fun updateText(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun reset() {
        _uiState.value = TranscribeUiState()
    }

    fun showMicrophonePermissionError() {
        _uiState.value = TranscribeUiState(
            stage = TranscribeStage.Error,
            errorMessage = "La app necesita permiso de micrófono para grabar.",
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
    val errorMessage: String? = null,
)
