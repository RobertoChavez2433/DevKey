package dev.devkey.keyboard.core

import dev.devkey.keyboard.LatinKeyboardBaseView

/**
 * Bridge between the Compose keyboard UI and the legacy [LatinKeyboardBaseView.OnKeyboardActionListener].
 *
 * Translates key actions from the Compose layer into calls to the existing IME listener,
 * handling modifier state (Shift, Ctrl, Alt) for proper key event generation.
 */
class KeyboardActionBridge(
    private val listener: LatinKeyboardBaseView.OnKeyboardActionListener
) {

    companion object {
        /** Matches LatinKeyboardBaseView's NOT_A_TOUCH_COORDINATE */
        private const val NOT_A_TOUCH_COORDINATE = -1
    }

    /**
     * Notify listener that a key was pressed (finger down).
     */
    fun onKeyPress(code: Int) {
        listener.onPress(code)
    }

    /**
     * Notify listener that a key was released (finger up).
     */
    fun onKeyRelease(code: Int) {
        listener.onRelease(code)
    }

    /**
     * Send a key event through the listener, applying modifier state.
     *
     * - If Shift active and code is a letter: sends uppercase code
     * - Otherwise: sends the code as-is through listener.onKey()
     * - After sending: consumes one-shot modifiers
     *
     * Note: Ctrl+key and Alt+key combinations are handled by LatinIME.onKey()
     * which checks its own modifier state, so we just send the code directly.
     */
    fun onKey(code: Int, modifierState: ModifierStateManager) {
        val effectiveCode = if (modifierState.isShiftActive() && isLetter(code)) {
            Character.toUpperCase(code)
        } else {
            code
        }

        listener.onKey(
            effectiveCode,
            intArrayOf(effectiveCode),
            NOT_A_TOUCH_COORDINATE,
            NOT_A_TOUCH_COORDINATE
        )

        modifierState.consumeOneShot()
    }

    /**
     * Send a text sequence through the listener.
     */
    fun onText(text: CharSequence) {
        listener.onText(text)
    }

    private fun isLetter(code: Int): Boolean {
        return code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code
    }
}
