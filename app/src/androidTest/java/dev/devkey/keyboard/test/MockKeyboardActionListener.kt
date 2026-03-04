package dev.devkey.keyboard.test

import dev.devkey.keyboard.core.KeyboardActionListener

/**
 * Mock implementation of [KeyboardActionListener] for testing.
 *
 * Captures all method calls to lists so tests can assert what was sent
 * to the IME bridge.
 */
class MockKeyboardActionListener : KeyboardActionListener {

    data class KeyEvent(
        val primaryCode: Int,
        val keyCodes: IntArray?,
        val x: Int,
        val y: Int
    )

    val pressedCodes = mutableListOf<Int>()
    val releasedCodes = mutableListOf<Int>()
    val keyEvents = mutableListOf<KeyEvent>()
    val textEvents = mutableListOf<CharSequence>()

    override fun onPress(primaryCode: Int) {
        pressedCodes.add(primaryCode)
    }

    override fun onRelease(primaryCode: Int) {
        releasedCodes.add(primaryCode)
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) {
        keyEvents.add(KeyEvent(primaryCode, keyCodes, x, y))
    }

    override fun onText(text: CharSequence) {
        textEvents.add(text)
    }

    fun clear() {
        pressedCodes.clear()
        releasedCodes.clear()
        keyEvents.clear()
        textEvents.clear()
    }
}
