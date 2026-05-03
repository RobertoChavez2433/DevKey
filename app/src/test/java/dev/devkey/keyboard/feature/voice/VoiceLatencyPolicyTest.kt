package dev.devkey.keyboard.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLatencyPolicyTest {

    @Test
    fun `stop to committed target is explicit`() {
        assertEquals(2_500L, VoiceLatencyPolicy.RELEASE_STOP_TO_COMMITTED_TARGET_MS)
    }

    @Test
    fun `release quality requires target latency`() {
        assertTrue(VoiceLatencyPolicy.meetsReleaseTarget(2_500L))
        assertFalse(VoiceLatencyPolicy.meetsReleaseTarget(2_501L))
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
