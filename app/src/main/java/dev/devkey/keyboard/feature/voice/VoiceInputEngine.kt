package dev.devkey.keyboard.feature.voice

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.voice.PermissionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.IOException
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

    /** Work owned by this engine instance and cancelled from [release]. */
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Observable voice state. */
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    /** Called when auto-stop produces a real transcription. */
    @Volatile
    private var transcriptionListener: ((String) -> Unit)? = null

    /** Audio capture manager — owns AudioRecord lifecycle and amplitude tracking. */
    private val captureManager = AudioCaptureManager(
        context = context,
        silenceDetector = silenceDetector,
        onSilenceDetected = {
            engineScope.launch {
                Log.i(TAG, "Silence detected — auto-stopping")
                val transcription = stopListening(source = "silence")
                if (shouldCommitTranscription(transcription)) {
                    transcriptionListener?.invoke(transcription)
                }
            }
        }
    )

    /** Observable audio amplitude for waveform visualization (0.0 to 1.0). */
    val amplitude: StateFlow<Float> = captureManager.amplitude

    /** Whether the model was loaded successfully. */
    private var modelLoaded = false
    @Volatile private var modelWarmupStarted = false

    fun setTranscriptionListener(listener: ((String) -> Unit)?) {
        transcriptionListener = listener
    }

    fun setAutoStopTimeoutSeconds(seconds: Int) {
        silenceDetector.timeoutMs = seconds.coerceIn(1, 10) * 1000L
    }

    fun hasPermission(): Boolean = captureManager.hasPermission()

    fun warmOfflineModelIfAllowed() {
        if (modelWarmupStarted || interpreter != null) return
        modelWarmupStarted = true
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClassMb = activityManager?.memoryClass ?: 0
        val lowRam = activityManager?.isLowRamDevice ?: false
        if (lowRam || memoryClassMb < VoiceLatencyPolicy.MIN_WARMUP_MEMORY_CLASS_MB) {
            DevKeyLogger.voice(
                "model_warmup_skipped",
                mapOf(
                    "memory_class_mb" to memoryClassMb,
                    "low_ram" to lowRam,
                )
            )
            return
        }
        engineScope.launch(Dispatchers.Default) {
            val startMs = SystemClock.elapsedRealtime()
            initialize()
            logLatency(
                "model_warmup",
                startMs,
                mapOf(
                    "loaded" to modelLoaded,
                    "memory_class_mb" to memoryClassMb,
                )
            )
        }
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) {
            onResult(true)
            return
        }
        PermissionActivity.onPermissionResult = { granted ->
            DevKeyLogger.voice(
                "permission_result",
                mapOf("granted" to granted)
            )
            onResult(granted)
        }
        try {
            val intent = Intent(context, PermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DevKeyLogger.voice("permission_requested")
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Unable to launch voice permission request", e)
            PermissionActivity.onPermissionResult = null
            DevKeyLogger.voice(
                "permission_result",
                mapOf("granted" to false, "reason" to "launch_failed")
            )
            onResult(false)
        } catch (e: SecurityException) {
            Log.w(TAG, "Voice permission request was blocked", e)
            PermissionActivity.onPermissionResult = null
            DevKeyLogger.voice(
                "permission_result",
                mapOf("granted" to false, "reason" to "launch_blocked")
            )
            onResult(false)
        }
    }

    /** Load the Whisper TF Lite interpreter lazily on first [startListening] call. */
    @Synchronized
    private fun initialize() {
        if (interpreter != null) return
        val startMs = SystemClock.elapsedRealtime()
        try {
            val bytes = context.assets.open("whisper-tiny.en.tflite").use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes).rewind()
            interpreter = Interpreter(buf)
            modelLoaded = true
            processor.loadResources()
            Log.i(TAG, "Whisper model loaded successfully")
            logLatency("model_load", startMs)
        } catch (e: IOException) {
            Log.w(TAG, "Whisper model not available — voice input will be limited", e)
            DevKeyLogger.voice("error", mapOf("kind" to "model_missing", "source" to "initialize"))
            logLatency("model_load_failed", startMs, mapOf("kind" to "model_missing"))
            modelLoaded = false
            // Don't set ERROR state — allow recording to proceed, skip inference
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Whisper model failed to initialize", e)
            DevKeyLogger.voice("error", mapOf("kind" to "model_invalid", "source" to "initialize"))
            logLatency("model_load_failed", startMs, mapOf("kind" to "model_invalid"))
            modelLoaded = false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Whisper model not available — voice input will be limited", e)
            DevKeyLogger.voice("error", mapOf("kind" to "model_missing", "source" to "initialize"))
            logLatency("model_load_failed", startMs, mapOf("kind" to "model_missing"))
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
     * @return true when capture starts, false when permission or capture setup blocks it.
     */
    suspend fun startListening(): Boolean {
        val startMs = SystemClock.elapsedRealtime()
        if (_state.value == VoiceState.LISTENING) {
            DevKeyLogger.voice(
                "state_transition",
                mapOf(
                    "state" to "LISTENING",
                    "source" to "startListening",
                    "reason" to "already_listening"
                )
            )
            return true
        }

        if (!captureManager.hasPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            _state.value = VoiceState.IDLE
            DevKeyLogger.voice(
                "permission_required",
                mapOf("source" to "startListening")
            )
            return false
        }

        if (interpreter == null) initialize()

        _state.value = VoiceState.LISTENING
        DevKeyLogger.voice("state_transition", mapOf("state" to "LISTENING", "source" to "startListening"))

        val started = captureManager.startCapture(engineScope)
        if (started) {
            logLatency("recording_start", startMs)
        }
        if (!started) {
            _state.value = VoiceState.ERROR
            DevKeyLogger.voice(
                "state_transition",
                mapOf(
                    "state" to "ERROR",
                    "source" to "startListening",
                    "reason" to "capture_init_failed"
                )
            )
        }
        return started
    }

    /**
     * Stop recording and process the audio through Whisper.
     *
     * @return The transcribed text, or an error/status message.
     */
    suspend fun stopListening(source: String = "stopListening"): String {
        val stopStartMs = SystemClock.elapsedRealtime()
        if (!captureManager.isCapturing() && _state.value == VoiceState.IDLE) {
            return ""
        }
        _state.value = VoiceState.PROCESSING
        DevKeyLogger.voice(
            "state_transition",
            mapOf("state" to "PROCESSING", "source" to source)
        )

        val audioData = captureManager.stopCapture()
        logLatency(
            if (source == "silence") "silence_stop" else "recording_stop",
            stopStartMs,
            mapOf("samples" to audioData.size, "source" to source)
        )
        if (audioData.isEmpty()) {
            _state.value = VoiceState.IDLE
            DevKeyLogger.voice(
                "state_transition",
                mapOf("state" to "IDLE", "source" to source, "reason" to "empty_audio")
            )
            return ""
        }

        return runInference(audioData, source)
    }

    /** Run Whisper inference on [audioData] and return the transcription text. */
    private suspend fun runInference(audioData: ShortArray, source: String): String {
        val startMs = System.currentTimeMillis()
        val elapsedStartMs = SystemClock.elapsedRealtime()
        return withContext(Dispatchers.Default) {
            try {
                val interp = interpreter
                if (interp == null || !modelLoaded) {
                    _state.value = VoiceState.IDLE
                    DevKeyLogger.voice(
                        "state_transition",
                        mapOf(
                            "state" to "IDLE",
                            "source" to source,
                            "reason" to "model_unavailable"
                        )
                    )
                    return@withContext "[Voice model not available]"
                }

                val preprocessingStartMs = SystemClock.elapsedRealtime()
                val flatInput = processor.processAudio(audioData)
                logLatency("preprocessing", preprocessingStartMs, mapOf("source" to source))
                if (flatInput == null) {
                    _state.value = VoiceState.IDLE
                    DevKeyLogger.voice("state_transition", mapOf(
                        "state" to "IDLE", "source" to source,
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
                val inferenceStartMs = SystemClock.elapsedRealtime()
                interp.runForMultipleInputsOutputs(arrayOf<Any>(inputBuf), outputBuffer)
                logLatency("inference", inferenceStartMs, mapOf("source" to source))

                val decodeStartMs = SystemClock.elapsedRealtime()
                val transcription = processor.decodeTokens(outputTokens2D[0])
                logLatency(
                    "decode",
                    decodeStartMs,
                    mapOf("source" to source, "result_length" to transcription.length)
                )
                _state.value = VoiceState.IDLE
                DevKeyLogger.voice("processing_complete", mapOf(
                    "result_length" to transcription.length,
                    "duration_ms" to (System.currentTimeMillis() - startMs),
                    "source" to source
                ))
                DevKeyLogger.voice(
                    "state_transition",
                    mapOf(
                        "state" to "IDLE",
                        "source" to source,
                        "reason" to "inference_complete"
                    )
                )
                logLatency("stop_to_result", elapsedStartMs, mapOf("source" to source))
                transcription.ifEmpty { "[No speech detected]" }
            } catch (e: IllegalArgumentException) {
                handleInferenceFailure(source, startMs, e)
            } catch (e: IllegalStateException) {
                handleInferenceFailure(source, startMs, e)
            }
        }
    }

    private fun handleInferenceFailure(source: String, startMs: Long, error: Throwable): String {
        Log.e(TAG, "Whisper inference failed", error)
        _state.value = VoiceState.IDLE
        DevKeyLogger.voice("error", mapOf("kind" to "inference_failed", "source" to source))
        DevKeyLogger.voice("state_transition", mapOf(
            "state" to "IDLE", "source" to source,
            "reason" to "inference_exception",
            "duration_ms" to (System.currentTimeMillis() - startMs)
        ))
        return "[Transcription error]"
    }

    private fun logLatency(
        phase: String,
        startMs: Long,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val durationMs = SystemClock.elapsedRealtime() - startMs
        val releaseGate = if (phase == "stop_to_result") {
            VoiceLatencyPolicy.stopToCommittedLogData(durationMs)
        } else {
            emptyMap()
        }
        DevKeyLogger.voice(
            "latency",
            mapOf(
                "phase" to phase,
                "duration_ms" to durationMs
            ) + releaseGate + extra
        )
    }

    /**
     * Cancel recording without processing.
     */
    fun cancelListening() {
        captureManager.cancel()
        _state.value = VoiceState.IDLE
        DevKeyLogger.voice("state_transition", mapOf("state" to "IDLE", "source" to "cancelListening"))
    }

    fun shouldCommitTranscription(text: String): Boolean =
        text.isNotBlank() && !text.startsWith("[")

    fun commitTranscriptionForTest(text: String): Boolean {
        if (!shouldCommitTranscription(text)) return false
        val listener = transcriptionListener ?: return false
        listener(text)
        return true
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
        if (captureManager.isCapturing()) {
            captureManager.cancel()
            DevKeyLogger.voice(
                "capture_released_for_file",
                mapOf("source" to "processFileForTest")
            )
        }
        reloadModelForFileTest()
        _state.value = VoiceState.PROCESSING
        DevKeyLogger.voice("state_transition", mapOf("state" to "PROCESSING", "source" to "processFileForTest"))

        val audioData = readWavPcm(filePath)
        if (audioData == null || audioData.isEmpty()) {
            _state.value = VoiceState.IDLE
            DevKeyLogger.voice(
                "state_transition",
                mapOf(
                    "state" to "IDLE",
                    "source" to "processFileForTest",
                    "reason" to "file_read_failed"
                )
            )
            return "[Failed to read WAV file]"
        }

        DevKeyLogger.voice("file_loaded", mapOf("samples" to audioData.size))
        return runInference(audioData, source = "processFileForTest")
    }

    private fun reloadModelForFileTest() {
        interpreter?.close()
        interpreter = null
        modelLoaded = false
        initialize()
        DevKeyLogger.voice("model_reloaded_for_file", mapOf("loaded" to modelLoaded))
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
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read WAV file", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Blocked from reading WAV file", e)
            null
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        engineScope.cancel()
        captureManager.release()
        interpreter?.close()
        interpreter = null
        transcriptionListener = null
    }
}
