package dev.devkey.keyboard.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLatencyPolicyTest {

    @Test
    fun `stop to committed target is explicit`() {
        assertEquals(1_000L, VoiceLatencyPolicy.RELEASE_STOP_TO_COMMITTED_TARGET_MS)
    }

    @Test
    fun `streaming runtime and full window runtime are named explicitly`() {
        assertEquals(
            "android_on_device_speech_recognizer",
            VoiceLatencyPolicy.RUNTIME_ANDROID_ON_DEVICE,
        )
        assertEquals(
            "tflite_whisper_tiny_full_window",
            VoiceLatencyPolicy.RUNTIME_TFLITE_WHISPER,
        )
    }

    @Test
    fun `release quality requires target latency`() {
        assertTrue(VoiceLatencyPolicy.meetsReleaseTarget(1_000L))
        assertFalse(VoiceLatencyPolicy.meetsReleaseTarget(1_001L))
    }

    @Test
    fun `missed target is labeled offline delayed with runtime next step`() {
        val data = VoiceLatencyPolicy.stopToCommittedLogData(9_000L)

        assertEquals(false, data["release_quality"])
        assertEquals(VoiceLatencyPolicy.POSTURE_OFFLINE_DELAYED, data["release_posture"])
        assertEquals(
            VoiceLatencyPolicy.RUNTIME_EVALUATION_NEXT_STEP,
            data["runtime_next_step"],
        )
    }
}
