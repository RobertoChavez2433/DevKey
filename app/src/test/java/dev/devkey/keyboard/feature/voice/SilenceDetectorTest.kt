package dev.devkey.keyboard.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SilenceDetector.
 *
 * Tests silent buffer, loud buffer, empty buffer, sustained silence,
 * reset, and custom threshold.
 */
class SilenceDetectorTest {

    private lateinit var detector: SilenceDetector

    @Before
    fun setUp() {
        detector = SilenceDetector()
    }

    // --- Default constants ---

    @Test
    fun `default threshold is 500_0`() {
        assertEquals(500.0, SilenceDetector.DEFAULT_THRESHOLD, 0.001)
    }

    @Test
    fun `default timeout is 2000ms`() {
        assertEquals(2000L, SilenceDetector.DEFAULT_TIMEOUT_MS)
    }

    // --- Empty buffer ---

    @Test
    fun `empty buffer (size 0) returns false`() {
        val buffer = ShortArray(10)
        assertFalse(detector.isSilent(buffer, size = 0))
    }

    // --- Loud buffer ---

    @Test
    fun `loud buffer above threshold returns false`() {
        // Samples well above DEFAULT_THRESHOLD (500.0 RMS)
        val buffer = ShortArray(10) { 10000.toShort() }
        assertFalse(detector.isSilent(buffer, size = buffer.size))
    }

    // --- Silent buffer (below threshold but timeout not elapsed) ---

    @Test
    fun `silent buffer below threshold returns false before timeout elapses`() {
        // Samples near zero — below threshold, but timeout (2000ms) has not passed
        val buffer = ShortArray(10) { 0 }
        val result = detector.isSilent(buffer, size = buffer.size)
        // On first detection, silence timer starts but 2000ms has not yet elapsed
        assertFalse(result)
    }

    // --- Sustained silence ---

    @Test
    fun `sustained silence returns true after timeout with zero threshold`() {
        // Set timeout to 0 so silence is detected immediately
        detector.timeoutMs = 0L
        val buffer = ShortArray(10) { 0 } // all zeros — RMS = 0 < 500
        val result = detector.isSilent(buffer, size = buffer.size)
        assertTrue(result)
    }

    // --- Loud buffer resets silence timer ---

    @Test
    fun `loud buffer after silent resets and returns false`() {
        detector.timeoutMs = 0L
        val silentBuffer = ShortArray(10) { 0 }
        detector.isSilent(silentBuffer, size = silentBuffer.size) // start timer

        val loudBuffer = ShortArray(10) { 10000.toShort() }
        val result = detector.isSilent(loudBuffer, size = loudBuffer.size)
        assertFalse(result)
    }

    // --- reset ---

    @Test
    fun `reset clears silence timer so detection starts fresh`() {
        detector.timeoutMs = 50L
        val buffer = ShortArray(10) { 0 }
        // First call starts the silence timer
        detector.isSilent(buffer, size = buffer.size)
        // Wait for timeout to elapse
        Thread.sleep(60)
        // Confirm silence is now detected
        assertTrue(detector.isSilent(buffer, size = buffer.size))
        // Reset clears the timer
        detector.reset()
        // Immediately after reset, timeout has not elapsed again
        val result = detector.isSilent(buffer, size = buffer.size)
        assertFalse(result)
    }

    // --- Custom threshold ---

    @Test
    fun `custom threshold changes what counts as silent`() {
        // Set threshold very high so moderate sounds are "silent"
        detector.threshold = 50000.0
        detector.timeoutMs = 0L
        // Buffer with moderate amplitude (1000) — below new threshold of 50000
        val buffer = ShortArray(10) { 1000.toShort() }
        val result = detector.isSilent(buffer, size = buffer.size)
        assertTrue(result)
    }

    // --- Negative size ---

    @Test
    fun `negative size returns false`() {
        val buffer = ShortArray(10) { 0 }
        assertFalse(detector.isSilent(buffer, size = -1))
    }

    // --- RMS exactly at threshold ---

    @Test
    fun `RMS exactly at threshold is not silent`() {
        // RMS = sqrt(sum(x^2)/N). For all samples = 500, RMS = 500.0 exactly.
        // Condition is rms < threshold, so equal should NOT be silent.
        detector.threshold = 500.0
        detector.timeoutMs = 0L
        val buffer = ShortArray(10) { 500.toShort() }
        val result = detector.isSilent(buffer, size = buffer.size)
        assertFalse(result)
    }

    // --- Partial buffer ---

    @Test
    fun `partial buffer reads only first N samples`() {
        detector.timeoutMs = 0L
        // First 10 samples are loud (10000), rest are silent (0)
        val buffer = ShortArray(100) { if (it < 10) 10000.toShort() else 0 }
        // Read only first 10 samples (loud) — should not be silent
        assertFalse(detector.isSilent(buffer, size = 10))
    }

    // --- Silence timer persistence ---

    @Test
    fun `silence timer persists across multiple calls`() {
        detector.timeoutMs = 50L
        val buffer = ShortArray(10) { 0 }
        // First call starts the timer
        assertFalse(detector.isSilent(buffer, size = buffer.size))
        // Wait for timeout to pass
        Thread.sleep(60)
        // Second call should detect silence because the timer was started by the first call
        assertTrue(detector.isSilent(buffer, size = buffer.size))
    }

    // --- Reset idempotency ---

    @Test
    fun `reset when already reset is idempotent`() {
        detector.timeoutMs = 0L
        detector.reset()
        detector.reset()
        val buffer = ShortArray(10) { 0 }
        // With timeoutMs=0, silent buffer should still be detected
        assertTrue(detector.isSilent(buffer, size = buffer.size))
    }
}
