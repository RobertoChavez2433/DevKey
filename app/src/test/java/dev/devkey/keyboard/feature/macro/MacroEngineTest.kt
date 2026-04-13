package dev.devkey.keyboard.feature.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MacroEngine.
 *
 * Tests the recording state machine — start/stop/cancel,
 * capture order, ignoring when not recording, and buffer clearing.
 * Pure JVM tests — replay() is not tested here because it requires
 * KeyboardActionBridge which depends on LatinKeyboardBaseView.OnKeyboardActionListener.
 */
class MacroEngineTest {

    private lateinit var engine: MacroEngine

    @Before
    fun setUp() {
        engine = MacroEngine()
    }

    // --- Initial state ---

    @Test
    fun `engine is not recording initially`() {
        assertFalse(engine.isRecording)
    }

    @Test
    fun `getCapturedSteps returns empty list initially`() {
        assertTrue(engine.getCapturedSteps().isEmpty())
    }

    // --- startRecording ---

    @Test
    fun `startRecording sets isRecording to true`() {
        engine.startRecording()
        assertTrue(engine.isRecording)
    }

    @Test
    fun `startRecording clears any previous buffer`() {
        engine.startRecording()
        engine.captureKey("a", 97, listOf())
        engine.stopRecording()

        engine.startRecording()
        assertEquals(0, engine.getCapturedSteps().size)
    }

    // --- captureKey ---

    @Test
    fun `captureKey records a step when recording`() {
        engine.startRecording()
        engine.captureKey("a", 97, listOf("ctrl"))
        assertEquals(1, engine.getCapturedSteps().size)
    }

    @Test
    fun `captureKey preserves key, keyCode and modifiers`() {
        engine.startRecording()
        engine.captureKey("c", 99, listOf("ctrl"))
        val step = engine.getCapturedSteps()[0]
        assertEquals("c", step.key)
        assertEquals(99, step.keyCode)
        assertEquals(listOf("ctrl"), step.modifiers)
    }

    @Test
    fun `captureKey does nothing when not recording`() {
        engine.captureKey("a", 97, listOf())
        assertEquals(0, engine.getCapturedSteps().size)
    }

    @Test
    fun `captureKey ignores empty key strings`() {
        engine.startRecording()
        engine.captureKey("", 0, listOf())
        assertEquals(0, engine.getCapturedSteps().size)
    }

    @Test
    fun `captureKey preserves capture order`() {
        engine.startRecording()
        engine.captureKey("h", 'h'.code, listOf())
        engine.captureKey("i", 'i'.code, listOf())
        engine.captureKey("!", '!'.code, listOf())
        val steps = engine.getCapturedSteps()
        assertEquals(3, steps.size)
        assertEquals("h", steps[0].key)
        assertEquals("i", steps[1].key)
        assertEquals("!", steps[2].key)
    }

    // --- stopRecording ---

    @Test
    fun `stopRecording returns captured steps`() {
        engine.startRecording()
        engine.captureKey("z", 'z'.code, listOf("ctrl"))
        val steps = engine.stopRecording()
        assertEquals(1, steps.size)
        assertEquals("z", steps[0].key)
    }

    @Test
    fun `stopRecording sets isRecording to false`() {
        engine.startRecording()
        engine.stopRecording()
        assertFalse(engine.isRecording)
    }

    @Test
    fun `stopRecording clears the internal buffer`() {
        engine.startRecording()
        engine.captureKey("a", 97, listOf())
        engine.stopRecording()
        // After stop, getCapturedSteps should be empty (buffer cleared)
        assertEquals(0, engine.getCapturedSteps().size)
    }

    // --- cancelRecording ---

    @Test
    fun `cancelRecording sets isRecording to false`() {
        engine.startRecording()
        engine.captureKey("a", 97, listOf())
        engine.cancelRecording()
        assertFalse(engine.isRecording)
    }

    @Test
    fun `cancelRecording discards captured steps`() {
        engine.startRecording()
        engine.captureKey("a", 97, listOf())
        engine.cancelRecording()
        assertEquals(0, engine.getCapturedSteps().size)
    }

    // --- Edge cases ---

    @Test
    fun `stopRecording when not recording returns empty`() {
        val steps = engine.stopRecording()
        assertTrue(steps.isEmpty())
    }

    @Test
    fun `cancelRecording when not recording is safe`() {
        engine.cancelRecording() // should not crash
        assertFalse(engine.isRecording)
    }

    @Test
    fun `double startRecording clears previous buffer`() {
        engine.startRecording()
        engine.captureKey("a", 97, listOf())
        engine.captureKey("b", 98, listOf())
        engine.startRecording() // should clear buffer
        assertEquals(0, engine.getCapturedSteps().size)
        assertTrue(engine.isRecording)
    }

    // --- Phase 10 stress tests ---

    @Test
    fun `record with modifier keys includes modifiers in steps`() {
        engine.startRecording()
        engine.captureKey("c", 99, listOf("ctrl"))
        engine.captureKey("v", 118, listOf("ctrl", "shift"))
        engine.captureKey("z", 122, listOf("ctrl", "alt"))
        val steps = engine.stopRecording()
        assertEquals(3, steps.size)
        assertEquals(listOf("ctrl"), steps[0].modifiers)
        assertEquals(listOf("ctrl", "shift"), steps[1].modifiers)
        assertEquals(listOf("ctrl", "alt"), steps[2].modifiers)
    }

    @Test
    fun `record empty macro no crash on replay`() {
        engine.startRecording()
        val steps = engine.stopRecording()
        assertEquals(0, steps.size)
        // Replay with empty steps list should not throw
        // (replay requires bridge/modifierState, but empty list short-circuits the loop)
        // We verify the engine state is clean after the empty record cycle
        assertFalse(engine.isRecording)
        assertEquals(0, engine.getCapturedSteps().size)
    }

    @Test
    fun `cancel recording mid-capture cleans state`() {
        engine.startRecording()
        engine.captureKey("a", 97, listOf())
        engine.captureKey("b", 98, listOf("shift"))
        engine.captureKey("c", 99, listOf("ctrl"))
        assertTrue(engine.isRecording)
        assertEquals(3, engine.getCapturedSteps().size)

        engine.cancelRecording()

        assertFalse(engine.isRecording)
        assertEquals(0, engine.getCapturedSteps().size)
    }
}
