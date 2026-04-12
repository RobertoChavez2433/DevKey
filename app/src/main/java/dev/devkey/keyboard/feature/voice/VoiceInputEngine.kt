package dev.devkey.keyboard.feature.voice

import android.content.Context
import android.util.Log
import dev.devkey.keyboard.debug.DevKeyLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Voice input engine using TF Lite Whisper for on-device speech-to-text.
 *
 * Manages the full voice input lifecycle: audio recording, silence detection,
 * speech recognition via Whisper, and state management.
 *
 * Audio capture is delegated to [AudioCaptureManager].
 * Falls back gracefully when model files are not available.
 *
 * @param context Android context for accessing assets and permissions.
 */
class VoiceInputEngine(private val context: Context) {

    companion object {
        private const val TAG = "DevKey/VoiceInputEngine"
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

    /** Silence detector for auto-stop. */
    private val silenceDetector = SilenceDetector()

    /** Audio preprocessor. */
    private val processor = WhisperProcessor(context)

    /** Observable voice state. */
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    /** Audio capture manager — owns AudioRecord lifecycle and amplitude tracking. */
    private val captureManager = AudioCaptureManager(
        context = context,
        silenceDetector = silenceDetector,
        onSilenceDetected = {
            Log.i(TAG, "Silence detected — auto-stopping")
            _state.value = VoiceState.PROCESSING
            DevKeyLogger.voice("state_transition", mapOf("state" to "PROCESSING", "source" to "recordingLoop", "trigger" to "silence_detected"))
        }
    )

    /** Observable audio amplitude for waveform visualization (0.0 to 1.0). */
    val amplitude: StateFlow<Float> = captureManager.amplitude

    /** Whether the model was loaded successfully. */
    private var modelLoaded = false

    /** Load the Whisper TF Lite interpreter lazily on first [startListening] call. */
    private fun initialize() {
        if (interpreter != null) return
        try {
            val bytes = context.assets.open("whisper-tiny.en.tflite").use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes).rewind()
            interpreter = Interpreter(buf)
            modelLoaded = true
            processor.loadResources()
            Log.i(TAG, "Whisper model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Whisper model not available — voice input will be limited", e)
            DevKeyLogger.voice("error", mapOf("kind" to "model_missing", "source" to "initialize"))
            modelLoaded = false
            // Don't set ERROR state — allow recording to proceed, skip inference
        }
    }

    /**
     * Start recording audio for voice input.
     *
     * Initializes the model (if needed), starts [AudioCaptureManager], and
     * suspends until the capture loop ends (silence detected or cancelled).
     *
     * @throws SecurityException if RECORD_AUDIO permission is not granted.
     */
    suspend fun startListening() {
        if (_state.value == VoiceState.LISTENING) return

        if (!captureManager.hasPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            _state.value = VoiceState.ERROR
            DevKeyLogger.voice("state_transition", mapOf("state" to "ERROR", "source" to "startListening", "reason" to "permission_denied"))
            return
        }

        if (interpreter == null) initialize()

        _state.value = VoiceState.LISTENING
        DevKeyLogger.voice("state_transition", mapOf("state" to "LISTENING", "source" to "startListening"))

        val started = captureManager.startCapture()
        if (!started) {
            _state.value = VoiceState.ERROR
            DevKeyLogger.voice("state_transition", mapOf("state" to "ERROR", "source" to "startListening", "reason" to "capture_init_failed"))
        }
    }

    /**
     * Stop recording and process the audio through Whisper.
     *
     * @return The transcribed text, or an error/status message.
     */
    suspend fun stopListening(): String {
        _state.value = VoiceState.PROCESSING
        DevKeyLogger.voice("state_transition", mapOf("state" to "PROCESSING", "source" to "stopListening"))

        val audioData = captureManager.stopCapture()
        if (audioData.isEmpty()) {
            _state.value = VoiceState.IDLE
            DevKeyLogger.voice("state_transition", mapOf("state" to "IDLE", "source" to "stopListening", "reason" to "empty_audio"))
            return ""
        }

        return runInference(audioData)
    }

    /** Run Whisper inference on [audioData] and return the transcription text. */
    private suspend fun runInference(audioData: ShortArray): String {
        val startMs = System.currentTimeMillis()
        return withContext(Dispatchers.Default) {
            try {
                val interp = interpreter
                if (interp == null || !modelLoaded) {
                    _state.value = VoiceState.IDLE
                    DevKeyLogger.voice("state_transition", mapOf("state" to "IDLE", "source" to "stopListening", "reason" to "model_unavailable"))
                    return@withContext "[Voice model not available]"
                }

                val flatInput = processor.processAudio(audioData)
                if (flatInput == null) {
                    _state.value = VoiceState.IDLE
                    DevKeyLogger.voice("state_transition", mapOf(
                        "state" to "IDLE", "source" to "stopListening",
                        "reason" to "audio_processing_failed"
                    ))
                    return@withContext "[Audio processing failed]"
                }

                // Log model input/output tensor shapes for diagnostics
                val inputTensor = interp.getInputTensor(0)
                val outputTensor = interp.getOutputTensor(0)
                DevKeyLogger.voice("model_shapes", mapOf(
                    "input" to inputTensor.shape().contentToString(),
                    "output" to outputTensor.shape().contentToString(),
                    "flatSize" to flatInput.size
                ))

                // Reshape flat mel array to match model input shape [1, 80, 3000]
                val inputBuf = ByteBuffer.allocateDirect(flatInput.size * 4).order(ByteOrder.nativeOrder())
                for (v in flatInput) inputBuf.putFloat(v)
                inputBuf.rewind()

                // Output shape is [1, 449] — use 2D array to match
                val outputShape = outputTensor.shape()
                val outputLen = if (outputShape.size >= 2) outputShape[1] else outputShape[0]
                val outputTokens2D = Array(1) { IntArray(outputLen) }
                val outputBuffer = hashMapOf<Int, Any>(0 to outputTokens2D)
                interp.runForMultipleInputsOutputs(arrayOf<Any>(inputBuf), outputBuffer)

                val transcription = processor.decodeTokens(outputTokens2D[0])
                _state.value = VoiceState.IDLE
                DevKeyLogger.voice("processing_complete", mapOf(
                    "result_length" to transcription.length,
                    "duration_ms" to (System.currentTimeMillis() - startMs),
                    "source" to "stopListening"
                ))
                DevKeyLogger.voice("state_transition", mapOf("state" to "IDLE", "source" to "stopListening", "reason" to "inference_complete"))
                transcription.ifEmpty { "[No speech detected]" }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper inference failed", e)
                _state.value = VoiceState.IDLE
                DevKeyLogger.voice("error", mapOf("kind" to "inference_failed", "source" to "stopListening"))
                DevKeyLogger.voice("state_transition", mapOf(
                    "state" to "IDLE", "source" to "stopListening",
                    "reason" to "inference_exception",
                    "duration_ms" to (System.currentTimeMillis() - startMs)
                ))
                "[Transcription error]"
            }
        }
    }

    /**
     * Cancel recording without processing.
     */
    fun cancelListening() {
        captureManager.cancel()
        _state.value = VoiceState.IDLE
        DevKeyLogger.voice("state_transition", mapOf("state" to "IDLE", "source" to "cancelListening"))
    }

    /**
     * Process a WAV file directly for testing — bypasses AudioRecord entirely.
     *
     * Reads a 16-bit PCM WAV file from the given path, runs it through the
     * Whisper pipeline, and returns the transcription. Debug builds only.
     *
     * @param filePath Absolute path to a 16kHz mono 16-bit PCM WAV file.
     * @return Transcribed text or an error/status message.
     */
    suspend fun processFileForTest(filePath: String): String {
        if (interpreter == null) initialize()
        _state.value = VoiceState.PROCESSING
        DevKeyLogger.voice("state_transition", mapOf("state" to "PROCESSING", "source" to "processFileForTest"))

        val audioData = readWavPcm(filePath)
        if (audioData == null || audioData.isEmpty()) {
            _state.value = VoiceState.IDLE
            DevKeyLogger.voice("state_transition", mapOf("state" to "IDLE", "source" to "processFileForTest", "reason" to "file_read_failed"))
            return "[Failed to read WAV file: $filePath]"
        }

        DevKeyLogger.voice("file_loaded", mapOf("path" to filePath, "samples" to audioData.size))
        return runInference(audioData)
    }

    /** Read PCM samples from a standard 16-bit mono WAV file. */
    private fun readWavPcm(path: String): ShortArray? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            // WAV header: first 44 bytes for standard PCM format
            if (bytes.size < 44) return null
            val dataSize = bytes.size - 44
            val samples = ShortArray(dataSize / 2)
            val buf = java.nio.ByteBuffer.wrap(bytes, 44, dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.asShortBuffer().get(samples)
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read WAV file: $path", e)
            null
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        captureManager.release()
        interpreter?.close()
        interpreter = null
    }
}
