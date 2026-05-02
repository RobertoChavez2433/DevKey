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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    @Volatile
    private var silenceTriggered = false

    private val _amplitude = MutableStateFlow(0f)

    /** Observable audio amplitude for waveform visualization (0.0 to 1.0). */
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /**
     * Returns true if RECORD_AUDIO permission is granted.
     */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun isCapturing(): Boolean = audioRecord != null

    /**
     * Create and start an [AudioRecord] instance, then run the capture loop
     * until cancelled or silence is detected.
     *
     * @return false if permission is denied or AudioRecord fails to initialize.
     */
    fun startCapture(scope: CoroutineScope): Boolean {
        if (!hasPermission()) {
            return failStart(
                kind = "permission_denied",
                message = "RECORD_AUDIO permission not granted"
            )
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE * 2)
        val record = createAudioRecord(bufferSize) ?: return false
        audioRecord = record

        if (!startAudioRecord(record)) {
            return false
        }

        resetCaptureState()
        recordingJob = scope.launch(Dispatchers.IO) {
            val chunk = ShortArray(CHUNK_SIZE)
            var keepReading = true
            while (isActive && keepReading) {
                val record = audioRecord
                val read = try {
                    record?.read(chunk, 0, CHUNK_SIZE) ?: -1
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "AudioRecord read failed", e)
                    DevKeyLogger.voice(
                        "error",
                        mapOf("kind" to "audiorecord_read_failed", "source" to "recordingLoop")
                    )
                    keepReading = false
                    -1
                }
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

                    if (!silenceTriggered && silenceDetector.isSilent(chunk, read)) {
                        silenceTriggered = true
                        Log.i(TAG, "Silence detected — auto-stopping")
                        onSilenceDetected()
                    }
                }
                if (read < 0) keepReading = false
            }
        }

        return true
    }

    private fun createAudioRecord(bufferSize: Int): AudioRecord? =
        try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    failStart(
                        kind = "audiorecord_init_failed",
                        message = "AudioRecord failed to initialize"
                    )
                    record.release()
                    audioRecord = null
                }
            }.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: IllegalArgumentException) {
            failStart(
                kind = "audiorecord_exception",
                message = "Failed to create AudioRecord",
                error = e
            )
            null
        } catch (e: SecurityException) {
            failStart(
                kind = "audiorecord_security_exception",
                message = "AudioRecord permission check failed",
                error = e
            )
            null
        }

    private fun startAudioRecord(record: AudioRecord): Boolean =
        try {
            record.startRecording()
            true
        } catch (e: IllegalStateException) {
            releaseAfterStartFailure(
                kind = "audiorecord_start_failed",
                message = "Failed to start AudioRecord",
                error = e
            )
        } catch (e: SecurityException) {
            releaseAfterStartFailure(
                kind = "audiorecord_start_security_exception",
                message = "AudioRecord start permission check failed",
                error = e
            )
        }

    private fun releaseAfterStartFailure(
        kind: String,
        message: String,
        error: Throwable
    ): Boolean {
        failStart(kind = kind, message = message, error = error)
        audioRecord?.release()
        audioRecord = null
        return false
    }

    private fun resetCaptureState() {
        audioBuffer.clear()
        silenceDetector.reset()
        silenceTriggered = false
        _amplitude.value = 0f
    }

    private fun failStart(
        kind: String,
        message: String,
        error: Throwable? = null
    ): Boolean {
        if (error == null) {
            Log.w(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
        DevKeyLogger.voice(
            "error",
            mapOf("kind" to kind, "source" to "startCapture")
        )
        return false
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
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Error stopping AudioRecord", e)
            DevKeyLogger.voice(
                "error",
                mapOf("kind" to "audiorecord_stop_failed", "source" to "stopCapture")
            )
        }
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null
        silenceTriggered = false
        _amplitude.value = 0f

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
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Error cancelling AudioRecord", e)
        }
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null
        audioBuffer.clear()
        silenceTriggered = false
        _amplitude.value = 0f
    }

    /** Release all native resources. Equivalent to [cancel]. */
    fun release() = cancel()
}
