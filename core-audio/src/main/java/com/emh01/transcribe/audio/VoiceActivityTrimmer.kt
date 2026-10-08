package com.emh01.transcribe.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PreparedAudio(
    val samples: FloatArray,
    val originalDurationMs: Long,
    val processedDurationMs: Long,
    val trimmedStartMs: Long,
    val trimmedEndMs: Long,
)

/**
 * Lightweight energy-based voice activity trimming.
 *
 * It intentionally does not use another neural model: this keeps the APK small
 * and avoids adding CPU/RAM pressure on older phones. The goal is to remove the
 * silence before the first utterance and after the last utterance, which both
 * wastes Whisper compute and can encourage hallucinations on tiny models.
 */
object VoiceActivityTrimmer {
    private const val FRAME_MS = 30
    private const val PADDING_MS = 240
    private const val MIN_PEAK_RMS = 0.010f
    private const val MIN_ABSOLUTE_THRESHOLD = 0.006f
    private const val NOISE_MULTIPLIER = 2.4f
    private const val MAX_THRESHOLD_FRACTION_OF_PEAK = 0.55f
    private const val MIN_ACTIVE_FRAMES = 2

    fun prepare(
        samples: FloatArray,
        sampleRate: Int = PcmAudioRecorder.SAMPLE_RATE,
    ): PreparedAudio {
        require(sampleRate > 0)
        if (samples.isEmpty()) {
            return PreparedAudio(
                samples = samples,
                originalDurationMs = 0,
                processedDurationMs = 0,
                trimmedStartMs = 0,
                trimmedEndMs = 0,
            )
        }

        val frameSize = max(1, sampleRate * FRAME_MS / 1000)
        val frameCount = (samples.size + frameSize - 1) / frameSize
        val rms = FloatArray(frameCount)

        for (frame in 0 until frameCount) {
            val start = frame * frameSize
            val end = min(samples.size, start + frameSize)
            var energy = 0.0
            for (i in start until end) {
                val value = samples[i].toDouble()
                energy += value * value
            }
            rms[frame] = sqrt(energy / max(1, end - start)).toFloat()
        }

        val sorted = rms.sorted()
        val lowQuarterCount = max(1, sorted.size / 4)
        val noiseFloor = sorted.take(lowQuarterCount).average().toFloat()
        val peak = rms.maxOrNull() ?: 0f

        val originalDurationMs = samples.size * 1000L / sampleRate
        if (peak < MIN_PEAK_RMS) {
            return PreparedAudio(
                samples = FloatArray(0),
                originalDurationMs = originalDurationMs,
                processedDurationMs = 0,
                trimmedStartMs = originalDurationMs,
                trimmedEndMs = 0,
            )
        }

        val threshold = max(
            MIN_ABSOLUTE_THRESHOLD,
            min(
                noiseFloor * NOISE_MULTIPLIER,
                peak * MAX_THRESHOLD_FRACTION_OF_PEAK,
            ),
        )

        val activeFrames = rms.indices.filter { rms[it] >= threshold }
        if (activeFrames.size < MIN_ACTIVE_FRAMES) {
            return PreparedAudio(
                samples = FloatArray(0),
                originalDurationMs = originalDurationMs,
                processedDurationMs = 0,
                trimmedStartMs = originalDurationMs,
                trimmedEndMs = 0,
            )
        }

        val paddingFrames = max(1, PADDING_MS / FRAME_MS)
        val firstFrame = max(0, activeFrames.first() - paddingFrames)
        val lastFrame = min(frameCount - 1, activeFrames.last() + paddingFrames)

        val startSample = firstFrame * frameSize
        val endSampleExclusive = min(samples.size, (lastFrame + 1) * frameSize)
        val prepared = samples.copyOfRange(startSample, endSampleExclusive)

        val processedDurationMs = prepared.size * 1000L / sampleRate
        val trimmedStartMs = startSample * 1000L / sampleRate
        val trimmedEndMs = max(0L, originalDurationMs - processedDurationMs - trimmedStartMs)

        return PreparedAudio(
            samples = prepared,
            originalDurationMs = originalDurationMs,
            processedDurationMs = processedDurationMs,
            trimmedStartMs = trimmedStartMs,
            trimmedEndMs = trimmedEndMs,
        )
    }
}
