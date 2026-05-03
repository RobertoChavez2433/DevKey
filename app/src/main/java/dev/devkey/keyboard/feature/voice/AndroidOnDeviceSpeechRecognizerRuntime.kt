package dev.devkey.keyboard.feature.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dev.devkey.keyboard.debug.DevKeyLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

internal class AndroidOnDeviceSpeechRecognizerRuntime(
    private val context: Context,
    private val setState: (VoiceInputEngine.VoiceState) -> Unit,
    private val shouldCommit: (String) -> Boolean,
    private val onAutoTranscription: (String) -> Unit,
    private val onComplete: () -> Unit,
    private val logLatency: (String, Long, Map<String, Any?>) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var recognitionResult: CompletableDeferred<String>? = null
    private var manualStopPending = false
    private var recognitionStartMs = 0L
    private var stopStartMs = 0L

    val active: Boolean
        get() = recognizer != null && recognitionResult != null

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(startMs: Long): Boolean {
        if (!isAvailable()) {
            DevKeyLogger.voice(
                "runtime_unavailable",
                mapOf(
                    "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                    "source" to "startListening"
                )
            )
            return false
        }

        return try {
            destroy()
            val result = CompletableDeferred<String>()
            val created = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognitionResult = result
            manualStopPending = false
            recognitionStartMs = SystemClock.elapsedRealtime()
            stopStartMs = recognitionStartMs
            created.setRecognitionListener(createListener(result))
            recognizer = created

            setState(VoiceInputEngine.VoiceState.LISTENING)
            DevKeyLogger.voice(
                "runtime_selected",
                mapOf(
                    "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                    "source" to "startListening",
                    "api_level" to Build.VERSION.SDK_INT,
                )
            )
            DevKeyLogger.voice(
                "state_transition",
                mapOf(
                    "state" to "LISTENING",
                    "source" to "startListening",
                    "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE
                )
            )
            created.startListening(createIntent())
            logLatency(
                "recording_start",
                startMs,
                mapOf("runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE)
            )
            true
        } catch (e: IllegalArgumentException) {
            handleStartFailure(e)
        } catch (e: IllegalStateException) {
            handleStartFailure(e)
        } catch (e: SecurityException) {
            handleStartFailure(e)
        }
    }

    suspend fun stop(source: String, requestedAtMs: Long): String {
        val result = recognitionResult ?: return ""
        manualStopPending = true
        stopStartMs = requestedAtMs
        setState(VoiceInputEngine.VoiceState.PROCESSING)
        DevKeyLogger.voice(
            "state_transition",
            mapOf(
                "state" to "PROCESSING",
                "source" to source,
                "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE
            )
        )

        try {
            recognizer?.stopListening()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "On-device recognizer stop failed", e)
            DevKeyLogger.voice(
                "recognizer_stop_failed",
                mapOf(
                    "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                    "kind" to e.javaClass.simpleName
                )
            )
        }
        logLatency(
            "recording_stop",
            requestedAtMs,
            mapOf("runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE, "source" to source)
        )

        val recognized = withTimeoutOrNull(VoiceLatencyPolicy.ON_DEVICE_RECOGNIZER_RESULT_TIMEOUT_MS) {
            result.await()
        }
        if (recognized != null) return recognized

        DevKeyLogger.voice(
            "recognizer_timeout",
            mapOf(
                "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                "timeout_ms" to VoiceLatencyPolicy.ON_DEVICE_RECOGNIZER_RESULT_TIMEOUT_MS
            )
        )
        cancel("timeout")
        return "[No speech detected]"
    }

    fun cancel(source: String) {
        try {
            recognizer?.cancel()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "On-device recognizer cancel failed", e)
        }
        recognitionResult?.takeIf { !it.isCompleted }?.complete("[No speech detected]")
        reset(source)
    }

    fun release() {
        destroy()
        recognitionResult = null
        manualStopPending = false
    }

    private fun createIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }

    private fun createListener(result: CompletableDeferred<String>): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                DevKeyLogger.voice(
                    "recognizer_ready",
                    mapOf("runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE)
                )
            }

            override fun onBeginningOfSpeech() {
                DevKeyLogger.voice(
                    "speech_started",
                    mapOf("runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE)
                )
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) {
                DevKeyLogger.voice(
                    "audio_buffer_received",
                    mapOf(
                        "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                        "bytes" to (buffer?.size ?: 0)
                    )
                )
            }

            override fun onEndOfSpeech() {
                stopStartMs = SystemClock.elapsedRealtime()
                setState(VoiceInputEngine.VoiceState.PROCESSING)
                DevKeyLogger.voice(
                    "state_transition",
                    mapOf(
                        "state" to "PROCESSING",
                        "source" to "system_endpointer",
                        "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE
                    )
                )
            }

            override fun onError(error: Int) {
                val statusText = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "[No speech detected]"
                    else -> "[Transcription error]"
                }
                DevKeyLogger.voice(
                    "recognizer_error",
                    mapOf(
                        "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                        "code" to error,
                        "name" to recognitionErrorName(error)
                    )
                )
                complete(result, statusText, "recognizer_error")
            }

            override fun onResults(results: Bundle?) {
                complete(
                    result = result,
                    text = bestResult(results),
                    callbackSource = if (manualStopPending) "manual_stop" else "system_endpointer"
                )
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = bestResult(partialResults)
                DevKeyLogger.voice(
                    "partial_result",
                    mapOf(
                        "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                        "result_length" to partial.length
                    )
                )
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                DevKeyLogger.voice(
                    "recognizer_event",
                    mapOf(
                        "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                        "type" to eventType
                    )
                )
            }
        }

    private fun complete(
        result: CompletableDeferred<String>,
        text: String,
        callbackSource: String
    ) {
        if (!result.isCompleted) {
            result.complete(text.ifEmpty { "[No speech detected]" })
        }
        val durationMs = SystemClock.elapsedRealtime() - recognitionStartMs
        logLatency(
            "stop_to_result",
            stopStartMs,
            mapOf(
                "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                "source" to callbackSource
            )
        )
        DevKeyLogger.voice(
            "processing_complete",
            mapOf(
                "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                "result_length" to text.length,
                "duration_ms" to durationMs,
                "source" to callbackSource
            )
        )
        val autoCommit = !manualStopPending && shouldCommit(text)
        reset(callbackSource, "recognition_complete")
        if (autoCommit) onAutoTranscription(text)
    }

    private fun reset(source: String, reason: String? = null) {
        destroy()
        recognitionResult = null
        manualStopPending = false
        setState(VoiceInputEngine.VoiceState.IDLE)
        DevKeyLogger.voice(
            "state_transition",
            mapOf(
                "state" to "IDLE",
                "source" to source,
                "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE
            ) + mapOf("reason" to reason).filterValues { it != null }
        )
        onComplete()
    }

    private fun destroy() {
        try {
            recognizer?.destroy()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "On-device recognizer destroy failed", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "On-device recognizer destroy failed", e)
        }
        recognizer = null
    }

    private fun handleStartFailure(error: Throwable): Boolean {
        Log.w(TAG, "On-device speech recognizer failed to start", error)
        DevKeyLogger.voice(
            "runtime_start_failed",
            mapOf(
                "runtime" to VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
                "kind" to error.javaClass.simpleName
            )
        )
        destroy()
        return false
    }

    private fun bestResult(results: Bundle?): String {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return matches?.firstOrNull().orEmpty().trim()
    }

    private fun recognitionErrorName(error: Int): String =
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            else -> "ERROR_$error"
        }

    private companion object {
        const val TAG = "DevKey/OnDeviceSpeech"
    }
}
