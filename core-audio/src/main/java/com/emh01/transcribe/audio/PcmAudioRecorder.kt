package com.emh01.transcribe.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

class PcmAudioRecorder : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recording = AtomicBoolean(false)
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null
    private val chunks = mutableListOf<ShortArray>()

    val isRecording: Boolean
        get() = recording.get()

    @SuppressLint("MissingPermission")
    fun start(
        onAmplitude: (Float) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        check(!recording.get()) { "Recording is already active" }
        chunks.clear()
        recording.set(true)

        job = scope.launch {
            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                check(minBuffer > 0) { "Unsupported audio configuration: $minBuffer" }
                val bufferBytes = max(minBuffer * 2, 4096)
                val buffer = ShortArray(bufferBytes / 2)

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
                check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                    "Could not initialize the microphone"
                }
                audioRecord = recorder

                recorder.startRecording()
                while (recording.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read < 0) error("AudioRecord.read failed: $read")
                    if (read == 0) continue

                    chunks += buffer.copyOf(read)
                    var peak = 0
                    for (i in 0 until read) {
                        peak = max(peak, abs(buffer[i].toInt()))
                    }
                    onAmplitude((peak / 32768f).coerceIn(0f, 1f))
                }
            } catch (t: Throwable) {
                recording.set(false)
                onError(t)
            } finally {
                audioRecord?.let { recorder ->
                    runCatching {
                        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.stop()
                        }
                    }
                    recorder.release()
                }
                audioRecord = null
            }
        }
    }

    suspend fun stop(): FloatArray = withContext(Dispatchers.IO) {
        recording.set(false)
        job?.join()
        job = null

        val totalSamples = chunks.sumOf { it.size }
        val result = FloatArray(totalSamples)
        var offset = 0
        for (chunk in chunks) {
            for (sample in chunk) {
                result[offset++] = sample / 32768f
            }
        }
        chunks.clear()
        result
    }

    override fun close() {
        recording.set(false)
        audioRecord?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        audioRecord = null
        scope.cancel()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
