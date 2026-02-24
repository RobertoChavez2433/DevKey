package dev.devkey.keyboard.feature.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Voice input engine using TF Lite Whisper for on-device speech-to-text.
 *
 * Manages the full voice input lifecycle: audio recording, silence detection,
 * speech recognition via Whisper, and state management.
 *
 * Falls back gracefully when model files are not available.
 *
 * @param context Android context for accessing assets and permissions.
 */
class VoiceInputEngine(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** Read 100ms of audio at a time (1600 samples at 16kHz). */
        private const val CHUNK_SIZE = 1600
    }

    /** Current voice input state. */
    enum class VoiceState {
        /** Not recording. */
        IDLE,
        /** Actively recording audio. */
        LISTENING,
        /** Processing recorded audio through Whisper. */
        PROCESSING,
        /** An error occurred. */
        ERROR
    }

    /** TF Lite interpreter for Whisper model, lazy initialized. */
    private var interpreter: Interpreter? = null

    /** Audio recorder instance. */
    private var audioRecord: AudioRecord? = null

    /** Silence detector for auto-stop. */
    private val silenceDetector = SilenceDetector()

    /** Audio preprocessor. */
    private val processor = WhisperProcessor(context)

    /** Observable voice state. */
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    /** Observable audio amplitude for waveform visualization (0.0 to 1.0). */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /** Accumulated audio samples during recording. */
    private val audioBuffer = mutableListOf<Short>()

    /** Active recording job. */
    private var recordingJob: Job? = null

    /** Whether the model was loaded successfully. */
    private var modelLoaded = false

    /**
     * Initialize the TF Lite interpreter with the Whisper model.
     *
     * Called lazily on first [startListening] call.
     */
    private fun initialize() {
        if (interpreter != null) return

        try {
            val assetManager = context.assets
            val modelFile = assetManager.open("whisper-tiny.en.tflite")
            val modelBytes = modelFile.readBytes()
            modelFile.close()

            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
            modelBuffer.put(modelBytes)
            modelBuffer.rewind()

            interpreter = Interpreter(modelBuffer)
            modelLoaded = true

            // Also load processor resources
            processor.loadResources()

            Log.i(TAG, "Whisper model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Whisper model not available — voice input will be limited", e)
            modelLoaded = false
            // Don't set ERROR state — allow recording to proceed, just skip inference
        }
    }

    /**
     * Start recording audio for voice input.
     *
     * Initializes the model (if needed), starts AudioRecord, and begins
     * a coroutine loop that reads audio chunks, updates amplitude, and
     * checks for silence.
     *
     * @throws SecurityException if RECORD_AUDIO permission is not granted.
     */
    suspend fun startListening() {
        if (_state.value == VoiceState.LISTENING) return

        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            _state.value = VoiceState.ERROR
            return
        }

        // Initialize model on first use
        if (interpreter == null) {
            initialize()
        }

        // Create AudioRecord
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
                _state.value = VoiceState.ERROR
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            _state.value = VoiceState.ERROR
            return
        }

        // Clear state
        audioBuffer.clear()
        silenceDetector.reset()
        _amplitude.value = 0f
        _state.value = VoiceState.LISTENING

        // Start recording loop
        audioRecord?.startRecording()

        coroutineScope {
            recordingJob = launch(Dispatchers.IO) {
                val chunk = ShortArray(CHUNK_SIZE)
                while (isActive && _state.value == VoiceState.LISTENING) {
                    val read = audioRecord?.read(chunk, 0, CHUNK_SIZE) ?: -1
                    if (read > 0) {
                        // Accumulate audio
                        synchronized(audioBuffer) {
                            for (i in 0 until read) {
                                audioBuffer.add(chunk[i])
                            }
                        }

                        // Update amplitude (RMS normalized to 0-1 range)
                        var sumOfSquares = 0.0
                        for (i in 0 until read) {
                            val sample = chunk[i].toDouble()
                            sumOfSquares += sample * sample
                        }
                        val rms = sqrt(sumOfSquares / read)
                        _amplitude.value = (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)

                        // Check for silence timeout
                        if (silenceDetector.isSilent(chunk, read)) {
                            Log.i(TAG, "Silence detected — auto-stopping")
                            _state.value = VoiceState.PROCESSING
                        }
                    }
                }
            }
        }
    }

    /**
     * Stop recording and process the audio through Whisper.
     *
     * @return The transcribed text, or an error/status message.
     */
    suspend fun stopListening(): String {
        _state.value = VoiceState.PROCESSING

        // Stop recording
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null

        // Get accumulated audio
        val audioData: ShortArray
        synchronized(audioBuffer) {
            audioData = audioBuffer.toShortArray()
            audioBuffer.clear()
        }

        if (audioData.isEmpty()) {
            _state.value = VoiceState.IDLE
            return ""
        }

        // Process and transcribe
        return withContext(Dispatchers.Default) {
            try {
                val interp = interpreter
                if (interp == null || !modelLoaded) {
                    _state.value = VoiceState.IDLE
                    return@withContext "[Voice model not available]"
                }

                // Preprocess audio
                val input = processor.processAudio(audioData)
                if (input == null) {
                    _state.value = VoiceState.IDLE
                    return@withContext "[Audio processing failed]"
                }

                // Run inference
                // Output shape depends on the model — typically token IDs
                val outputTokens = IntArray(256) // Max output length
                val inputBuffer = arrayOf<Any>(input)
                val outputBuffer = HashMap<Int, Any>()
                outputBuffer[0] = outputTokens

                interp.runForMultipleInputsOutputs(inputBuffer, outputBuffer)

                val transcription = processor.decodeTokens(outputTokens)

                _state.value = VoiceState.IDLE
                transcription.ifEmpty { "[No speech detected]" }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper inference failed", e)
                _state.value = VoiceState.IDLE
                "[Transcription error]"
            }
        }
    }

    /**
     * Cancel recording without processing.
     */
    fun cancelListening() {
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
        _state.value = VoiceState.IDLE
    }

    /**
     * Release all resources.
     */
    fun release() {
        cancelListening()
        interpreter?.close()
        interpreter = null
    }
}
