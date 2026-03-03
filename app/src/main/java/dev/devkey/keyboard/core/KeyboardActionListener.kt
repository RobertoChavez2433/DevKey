package dev.devkey.keyboard.core

/**
 * Contract between [KeyboardActionBridge] (Compose UI layer) and the IME service.
 *
 * Only the four methods that cross the bridge boundary are included:
 * press/release for haptic feedback, onKey for character input, and onText
 * for multi-character sequences. Swipe gestures and cancel events are handled
 * internally by LatinIME and do not flow through the bridge.
 */
interface KeyboardActionListener {

    /**
     * Called when the user presses a key (finger down).
     * For keys that repeat, this is only called once.
     *
     * @param primaryCode The unicode/keycode of the key being pressed.
     *                    Zero if the touch is not on a valid key.
     */
    fun onPress(primaryCode: Int) {}

    /**
     * Called when the user releases a key (finger up).
     * For keys that repeat, this is only called once.
     *
     * @param primaryCode The code of the key that was released.
     */
    fun onRelease(primaryCode: Int) {}

    /**
     * Send a key press to the listener.
     *
     * This is the core contract of the interface — implementors MUST override this method.
     *
     * @param primaryCode The key that was pressed.
     * @param keyCodes Alternative key codes (primary first), used for proximity correction.
     *                 May be null if no alternatives are available.
     * @param x X-coordinate pixel of touch event, or -1 if not from a touch.
     * @param y Y-coordinate pixel of touch event, or -1 if not from a touch.
     */
    fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int)

    /**
     * Sends a sequence of characters to the listener.
     *
     * @param text The sequence of characters to be displayed.
     */
    fun onText(text: CharSequence) {}
}
