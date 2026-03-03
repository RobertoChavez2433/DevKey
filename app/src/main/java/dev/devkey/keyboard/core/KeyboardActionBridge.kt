package dev.devkey.keyboard.core

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.KeyPressLogger
import dev.devkey.keyboard.ui.keyboard.KeyCodes

/**
 * Bridge between the Compose keyboard UI and the IME's [KeyboardActionListener].
 *
 * This is the intentional boundary between the UI layer and LatinIME.
 * It handles:
 * - Shift-to-uppercase transformation before the key reaches the IME
 * - Smart backspace/escape resolution via InputConnection
 * - Key press logging for diagnostics
 *
 * The bridge does NOT bypass LatinIME to call [KeyEventSender] directly
 * because LatinIME must handle prediction, composing text, and InputConnection
 * lifecycle before any key event can be synthesized.
 */
class KeyboardActionBridge(
    private val listener: KeyboardActionListener
) {

    companion object {
        /** Sentinel value meaning the key event did not come from a touch */
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
            code.toChar().uppercaseChar().code
        } else {
            code
        }

        KeyPressLogger.logBridgeOnKey(code, effectiveCode, modifierState.isShiftActive(), modifierState.isCtrlActive(), modifierState.isAltActive())

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

    /**
     * Resolve smart backspace/esc: returns DELETE if cursor has text to delete
     * or a selection exists, otherwise returns ESCAPE.
     *
     * Defaults to DELETE when InputConnection is unavailable.
     */
    fun resolveSmartBackEsc(inputConnection: InputConnection?): Int {
        if (inputConnection == null) return KeyCodes.DELETE
        return try {
            val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
            val cursor = extracted?.selectionStart ?: 0
            val selLength = (extracted?.selectionEnd ?: 0) - cursor
            if (cursor > 0 || selLength > 0) KeyCodes.DELETE else KeyCodes.ESCAPE
        } catch (_: Exception) {
            KeyCodes.DELETE
        }
    }

    private fun isLetter(code: Int): Boolean {
        return Character.isLetter(code)
    }
}
