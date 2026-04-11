package dev.devkey.keyboard.core.modifier

/**
 * Simple tracker for modifier key chording detection.
 *
 * Tracks whether a modifier key was held while another key was pressed.
 * Used for Symbol and Fn keys in LatinIME that don't need the full
 * ModifierStateManager lifecycle (ONE_SHOT, LOCKED, etc.).
 *
 * The primary modifier state machine lives in [dev.devkey.keyboard.core.ModifierStateManager].
 * This class is a lightweight standalone tracker for keys that only need
 * chording detection (Symbol, Fn).
 *
 * State transitions:
 * - onPress(): -> PRESSING
 * - onOtherKeyPressed() while PRESSING: -> CHORDING
 * - onRelease(): -> RELEASING
 */
class ChordeTracker {

    private enum class State {
        RELEASING,
        PRESSING,
        CHORDING
    }

    private var state = State.RELEASING

    fun onPress() {
        state = State.PRESSING
    }

    fun onRelease() {
        state = State.RELEASING
    }

    fun onOtherKeyPressed() {
        if (state == State.PRESSING) {
            state = State.CHORDING
        }
    }

    fun isChording(): Boolean = state == State.CHORDING

    override fun toString(): String = "ChordeTracker:$state"
}
