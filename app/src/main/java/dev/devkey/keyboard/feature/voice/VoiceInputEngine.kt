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
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Voice input engine using Android's on-device streaming recognizer for live
 * speech and TF Lite Whisper for deterministic file-fixture validation/fallback.
 *
 * Manages the full voice input lifecycle: audio recording, silence detection,
 * speech recognition, and state management.
 *
 * Audio capture is delegated to [AudioCaptureManager].
 * Falls back gracefully when model files are not available.
 *
 * @param context Android context for accessing assets and permissions.
 */
class VoiceInputEngine(private val context: Context) {

    companion object {
        private const val TAG = "DevKey/VoiceInputEngine"
        private const val DEFAULT_OUTPUT_TOKEN_COUNT = 449
    }

    private enum class ActiveRuntime {
        NONE,
        ANDROID_ON_DEVICE,
        TFLITE_WHISPER,
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

    private val onDeviceRuntime = AndroidOnDeviceSpeechRecognizerRuntime(
        context = context,
        setState = { _state.value = it },
        shouldCommit = ::shouldCommitTranscription,
        onAutoTranscription = { transcription -> transcriptionListener?.invoke(transcription) },
        onComplete = { activeRuntime = ActiveRuntime.NONE },
        logLatency = ::logLatency,
    )

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
    @Volatile private var activeRuntime = ActiveRuntime.NONE
    private val inferenceLock = Any()
    private var cachedInputShape: IntArray = intArrayOf(1, WhisperProcessor.N_MELS, WhisperProcessor.N_FRAMES)
    private var cachedOutputLength: Int = 0
    private var reusableInputBuffer: ByteBuffer? = null
    private var reusableOutputTokens: Array<IntArray>? = null
    private var reusableOutputBuffer: HashMap<Int, Any>? = null

    fun setTranscriptionListener(listener: ((String) -> Unit)?) {
        transcriptionListener = listener
    }

    fun setAutoStopTimeoutSeconds(seconds: Int) {
        silenceDetector.timeoutMs = seconds.coerceIn(1, 10) * 1000L
    }

    fun hasPermission(): Boolean = captureManager.hasPermission()

    fun warmOfflineModelIfAllowed() {
        if (onDeviceRuntime.isAvailable()) {
            DevKeyLogger.voice(
                "model_warmup_skipped",
                mapOf(
                    "reason" to "android_on_device_runtime_primary",
                    "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                )
            )
            return
        }
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
            val options = Interpreter.Options().apply {
                setNumThreads(VoiceLatencyPolicy.TFLITE_THREAD_COUNT)
                setUseXNNPACK(true)
                setUseNNAPI(VoiceLatencyPolicy.TFLITE_USE_NNAPI)
            }
            val loadedInterpreter = Interpreter(loadModelBuffer(), options)
            interpreter = loadedInterpreter
            modelLoaded = true
            processor.loadResources()
            cacheModelTensors(loadedInterpreter)
            Log.i(TAG, "Whisper model loaded successfully")
            logLatency(
                "model_load",
                startMs,
                mapOf(
                    "threads" to VoiceLatencyPolicy.TFLITE_THREAD_COUNT,
                    "nnapi" to VoiceLatencyPolicy.TFLITE_USE_NNAPI
                )
            )
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

    private fun loadModelBuffer(): ByteBuffer {
        return try {
            context.assets.openFd("whisper-tiny.en.tflite").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength
                    )
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Whisper model memory-map unavailable; falling back to direct asset copy", e)
            val bytes = context.assets.open("whisper-tiny.en.tflite").use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
    }

    private fun cacheModelTensors(interpreter: Interpreter) {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        cachedInputShape = inputTensor.shape()
        val outputShape = outputTensor.shape()
        cachedOutputLength = if (outputShape.size >= 2) outputShape[1] else outputShape[0]
        DevKeyLogger.voice(
            "model_shapes",
            mapOf(
                "input" to cachedInputShape.contentToString(),
                "output" to outputShape.contentToString(),
                "flatSize" to (cachedInputShape.drop(1).fold(1) { acc, dim -> acc * dim })
            )
        )
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

        if (onDeviceRuntime.start(startMs)) {
            activeRuntime = ActiveRuntime.ANDROID_ON_DEVICE
            return true
        }

        if (interpreter == null) initialize()

        activeRuntime = ActiveRuntime.TFLITE_WHISPER
        _state.value = VoiceState.LISTENING
        DevKeyLogger.voice(
            "runtime_selected",
            mapOf(
                "runtime" to VoiceLatencyPolicy.RUNTIME_TFLITE_WHISPER,
                "source" to "startListening",
                "reason" to "on_device_unavailable"
            )
        )
        DevKeyLogger.voice(
            "state_transition",
            mapOf(
                "state" to "LISTENING",
                "source" to "startListening",
                "runtime" to VoiceLatencyPolicy.RUNTIME_TFLITE_WHISPER
            )
        )

        val started = captureManager.startCapture(engineScope)
        if (started) {
            logLatency(
                "recording_start",
                startMs,
                mapOf("runtime" to VoiceLatencyPolicy.RUNTIME_TFLITE_WHISPER)
            )
        }
        if (!started) {
            activeRuntime = ActiveRuntime.NONE
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
        if (activeRuntime == ActiveRuntime.ANDROID_ON_DEVICE) {
            return onDeviceRuntime.stop(source, stopStartMs).also {
                activeRuntime = ActiveRuntime.NONE
            }
        }
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
            activeRuntime = ActiveRuntime.NONE
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
                    activeRuntime = ActiveRuntime.NONE
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
                    activeRuntime = ActiveRuntime.NONE
                    _state.value = VoiceState.IDLE
                    DevKeyLogger.voice("state_transition", mapOf(
                        "state" to "IDLE", "source" to source,
                        "reason" to "audio_processing_failed"
                    ))
                    return@withContext "[Audio processing failed]"
                }

                val inferenceStartMs = SystemClock.elapsedRealtime()
                val transcription = synchronized(inferenceLock) {
                    val inputBuf = prepareInputBuffer(flatInput)
                    val outputTokens2D = prepareOutputTokens()
                    val outputBuffer = reusableOutputBuffer ?: hashMapOf<Int, Any>().also {
                        reusableOutputBuffer = it
                    }
                    outputBuffer[0] = outputTokens2D
                    interp.runForMultipleInputsOutputs(arrayOf<Any>(inputBuf), outputBuffer)
                    logLatency("inference", inferenceStartMs, mapOf("source" to source))

                    val decodeStartMs = SystemClock.elapsedRealtime()
                    processor.decodeTokens(outputTokens2D[0]).also {
                        logLatency(
                            "decode",
                            decodeStartMs,
                            mapOf("source" to source, "result_length" to it.length)
                        )
                    }
                }
                activeRuntime = ActiveRuntime.NONE
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

    private fun prepareInputBuffer(flatInput: FloatArray): ByteBuffer {
        val requiredBytes = flatInput.size * Float.SIZE_BYTES
        val buffer = reusableInputBuffer?.takeIf { it.capacity() == requiredBytes }
            ?: ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.nativeOrder()).also {
                reusableInputBuffer = it
            }
        buffer.clear()
        for (value in flatInput) buffer.putFloat(value)
        buffer.rewind()
        return buffer
    }

    private fun prepareOutputTokens(): Array<IntArray> {
        val outputLength = cachedOutputLength.takeIf { it > 0 } ?: DEFAULT_OUTPUT_TOKEN_COUNT
        val existing = reusableOutputTokens
        if (existing != null && existing[0].size == outputLength) {
            existing[0].fill(0)
            return existing
        }
        return Array(1) { IntArray(outputLength) }.also {
            reusableOutputTokens = it
        }
    }

    private fun handleInferenceFailure(source: String, startMs: Long, error: Throwable): String {
        Log.e(TAG, "Whisper inference failed", error)
        activeRuntime = ActiveRuntime.NONE
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
        if (activeRuntime == ActiveRuntime.ANDROID_ON_DEVICE) {
            onDeviceRuntime.cancel("cancelListening")
            activeRuntime = ActiveRuntime.NONE
            return
        }
        captureManager.cancel()
        activeRuntime = ActiveRuntime.NONE
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
    suspend fun processFileForTest(filePath: String, coldStart: Boolean = false): String {
        if (activeRuntime == ActiveRuntime.ANDROID_ON_DEVICE) {
            onDeviceRuntime.cancel("processFileForTest")
            activeRuntime = ActiveRuntime.NONE
            DevKeyLogger.voice(
                "streaming_runtime_released_for_file",
                mapOf("source" to "processFileForTest")
            )
        }
        if (captureManager.isCapturing()) {
            captureManager.cancel()
            DevKeyLogger.voice(
                "capture_released_for_file",
                mapOf("source" to "processFileForTest")
            )
        }
        prepareModelForFileTest(coldStart)
        activeRuntime = ActiveRuntime.TFLITE_WHISPER
        _state.value = VoiceState.PROCESSING
        DevKeyLogger.voice(
            "runtime_selected",
            mapOf(
                "runtime" to VoiceLatencyPolicy.RUNTIME_TFLITE_WHISPER,
                "source" to "processFileForTest",
                "cold_start" to coldStart
            )
        )
        DevKeyLogger.voice(
            "state_transition",
            mapOf(
                "state" to "PROCESSING",
                "source" to "processFileForTest",
                "cold_start" to coldStart,
                "runtime" to VoiceLatencyPolicy.RUNTIME_TFLITE_WHISPER
            )
        )

        val audioData = readWavPcm(filePath)
        if (audioData == null || audioData.isEmpty()) {
            activeRuntime = ActiveRuntime.NONE
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

    private fun prepareModelForFileTest(coldStart: Boolean) {
        if (coldStart) {
            interpreter?.close()
            interpreter = null
            modelLoaded = false
            reusableInputBuffer = null
            reusableOutputTokens = null
            reusableOutputBuffer = null
        }
        initialize()
        DevKeyLogger.voice(
            "model_ready_for_file",
            mapOf("loaded" to modelLoaded, "cold_start" to coldStart)
        )
    }

    /** Read PCM samples from a standard 16-bit mono WAV file. */
    private fun readWavPcm(path: String): ShortArray? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            WavPcmReader.readPcm16(bytes)
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
        onDeviceRuntime.release()
        interpreter?.close()
        interpreter = null
        transcriptionListener = null
        activeRuntime = ActiveRuntime.NONE
    }
}
