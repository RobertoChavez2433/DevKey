package dev.devkey.keyboard.feature.voice

import kotlin.math.sqrt

/**
 * Detects silence in audio buffers using RMS amplitude analysis.
 *
 * Used by [VoiceInputEngine] to automatically stop recording when
 * the user stops speaking.
 */
class SilenceDetector {

    companion object {
        /** Default RMS amplitude threshold below which audio is considered silent. */
        const val DEFAULT_THRESHOLD = 500.0

        /** Default duration of continuous silence before auto-stop (milliseconds). */
        const val DEFAULT_TIMEOUT_MS = 2000L
    }

    /** RMS amplitude threshold — audio below this level is considered silent. */
    var threshold: Double = DEFAULT_THRESHOLD

    /** Duration of continuous silence required before triggering auto-stop. */
    var timeoutMs: Long = DEFAULT_TIMEOUT_MS

    /** Timestamp when silence was first detected, 0 if not currently silent. */
    private var silenceStartTime: Long = 0L

    /**
     * Analyze an audio buffer and determine if silence has persisted long enough.
     *
     * @param audioBuffer The PCM audio samples.
     * @param size The number of valid samples in the buffer.
     * @return true if silence has persisted for at least [timeoutMs].
     */
    fun isSilent(audioBuffer: ShortArray, size: Int): Boolean {
        if (size <= 0) return false

        // Calculate RMS (Root Mean Square) amplitude
        var sumOfSquares = 0.0
        for (i in 0 until size) {
            val sample = audioBuffer[i].toDouble()
            sumOfSquares += sample * sample
        }
        val rms = sqrt(sumOfSquares / size)

        return if (rms < threshold) {
            // Audio is quiet
            if (silenceStartTime == 0L) {
                silenceStartTime = System.currentTimeMillis()
            }
            (System.currentTimeMillis() - silenceStartTime) >= timeoutMs
        } else {
            // Audio is loud — reset silence timer
            silenceStartTime = 0L
            false
        }
    }

    /**
     * Reset the silence detection state.
     */
    fun reset() {
        silenceStartTime = 0L
    }
}
