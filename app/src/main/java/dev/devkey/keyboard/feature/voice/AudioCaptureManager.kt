package dev.devkey.keyboard.feature.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dev.devkey.keyboard.debug.DevKeyLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages AudioRecord lifecycle: setup, recording loop, buffer accumulation,
 * amplitude tracking, and teardown.
 *
 * Extracted from [VoiceInputEngine] to keep audio I/O concerns isolated.
 *
 * @param context Android context used for permission checks.
 * @param onSilenceDetected Callback invoked when [SilenceDetector] triggers auto-stop.
 */
internal class AudioCaptureManager(
    private val context: Context,
    private val silenceDetector: SilenceDetector,
    private val onSilenceDetected: () -> Unit,
) {
    companion object {
        private const val TAG = "DevKey/AudioCaptureManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** Read 100 ms of audio at a time (1 600 samples at 16 kHz). */
        const val CHUNK_SIZE = 1600
    }

    private var audioRecord: AudioRecord? = null
    private val audioBuffer = mutableListOf<Short>()
    private var recordingJob: Job? = null

    private val _amplitude = MutableStateFlow(0f)

    /** Observable audio amplitude for waveform visualization (0.0 to 1.0). */
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /**
     * Returns true if RECORD_AUDIO permission is granted.
     */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Create and start an [AudioRecord] instance, then run the capture loop
     * until cancelled or silence is detected.
     *
     * @return false if permission is denied or AudioRecord fails to initialize.
     */
    suspend fun startCapture(): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            DevKeyLogger.voice(
                "error",
                mapOf("kind" to "permission_denied", "source" to "startCapture")
            )
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                DevKeyLogger.voice(
                    "error",
                    mapOf("kind" to "audiorecord_init_failed", "source" to "startCapture")
                )
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            DevKeyLogger.voice(
                "error",
                mapOf("kind" to "audiorecord_exception", "source" to "startCapture")
            )
            return false
        }

        audioBuffer.clear()
        silenceDetector.reset()
        _amplitude.value = 0f

        audioRecord?.startRecording()

        coroutineScope {
            recordingJob = launch(Dispatchers.IO) {
                val chunk = ShortArray(CHUNK_SIZE)
                while (isActive) {
                    val read = audioRecord?.read(chunk, 0, CHUNK_SIZE) ?: -1
                    if (read > 0) {
                        synchronized(audioBuffer) {
                            for (i in 0 until read) audioBuffer.add(chunk[i])
                        }

                        var sumOfSquares = 0.0
                        for (i in 0 until read) {
                            val sample = chunk[i].toDouble()
                            sumOfSquares += sample * sample
                        }
                        val rms = sqrt(sumOfSquares / read)
                        _amplitude.value = (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)

                        if (silenceDetector.isSilent(chunk, read)) {
                            Log.i(TAG, "Silence detected — auto-stopping")
                            onSilenceDetected()
                        }
                    }
                }
            }
        }

        return true
    }

    /**
     * Stop the active [AudioRecord] and return all accumulated audio samples.
     *
     * The internal buffer is cleared after the data is extracted.
     */
    fun stopCapture(): ShortArray {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
            DevKeyLogger.voice(
                "error",
                mapOf("kind" to "audiorecord_stop_failed", "source" to "stopCapture")
            )
        }
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null

        return synchronized(audioBuffer) {
            val data = audioBuffer.toShortArray()
            audioBuffer.clear()
            data
        }
    }

    /**
     * Cancel recording and discard buffered audio without returning it.
     */
    fun cancel() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling AudioRecord", e)
        }
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null
        audioBuffer.clear()
        _amplitude.value = 0f
    }

    /** Release all native resources. Equivalent to [cancel]. */
    fun release() = cancel()
}
