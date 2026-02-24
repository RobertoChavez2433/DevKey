package dev.devkey.keyboard.feature.macro

import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.ModifierStateManager

/**
 * Core macro engine for recording and replaying macro sequences.
 *
 * Recording captures key events into a buffer. Replay sends stored steps
 * through the keyboard action bridge.
 */
class MacroEngine {

    private var _isRecording: Boolean = false

    /** Whether the engine is currently recording key events. */
    val isRecording: Boolean
        get() = _isRecording

    private val recordingBuffer = mutableListOf<MacroStep>()

    /**
     * Starts recording. Clears any previous buffer.
     */
    fun startRecording() {
        recordingBuffer.clear()
        _isRecording = true
    }

    /**
     * Captures a key event during recording.
     * Ignores modifier-only events (empty key).
     *
     * @param key The character key pressed.
     * @param keyCode The integer keycode.
     * @param modifiers List of active modifier names.
     */
    fun captureKey(key: String, keyCode: Int, modifiers: List<String>) {
        if (!_isRecording) return
        if (key.isEmpty()) return
        recordingBuffer.add(MacroStep(key, keyCode, modifiers))
    }

    /**
     * Returns a snapshot of the currently captured steps (for live UI display).
     */
    fun getCapturedSteps(): List<MacroStep> {
        return recordingBuffer.toList()
    }

    /**
     * Stops recording and returns the captured steps.
     * Clears the buffer and resets recording state.
     */
    fun stopRecording(): List<MacroStep> {
        val steps = recordingBuffer.toList()
        recordingBuffer.clear()
        _isRecording = false
        return steps
    }

    /**
     * Cancels recording without saving.
     */
    fun cancelRecording() {
        recordingBuffer.clear()
        _isRecording = false
    }

    /**
     * Replays a macro by sending each step through the bridge.
     *
     * For each step, the key event is sent directly via the bridge's onKey method.
     * Modifier state is temporarily applied for each step.
     *
     * @param steps The macro steps to replay.
     * @param bridge The keyboard action bridge for sending key events.
     * @param modifierState The modifier state manager.
     */
    fun replay(
        steps: List<MacroStep>,
        bridge: KeyboardActionBridge,
        modifierState: ModifierStateManager
    ) {
        for (step in steps) {
            // Send the key event through the bridge
            // The bridge's onKey method handles shift transformation
            // For ctrl/alt combos, LatinIME checks modifier state directly
            bridge.onKey(step.keyCode, modifierState)
        }
    }
}
