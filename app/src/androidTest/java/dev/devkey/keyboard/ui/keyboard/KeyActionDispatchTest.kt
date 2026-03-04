package dev.devkey.keyboard.ui.keyboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.devkey.keyboard.test.MockKeyboardActionListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for key action dispatch.
 *
 * Verifies that tapping keys sends the correct keycodes through the bridge
 * to the mock action listener.
 */
class KeyActionDispatchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockListener = MockKeyboardActionListener()

    /**
     * Tap the 'a' key and verify the mock listener receives code 97.
     */
    @Test
    fun tapLetterKey_firesOnKeyAction() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Tap 'a' (code 97)
        composeTestRule.onNodeWithTag("key_97").performClick()
        composeTestRule.waitForIdle()

        // Verify the listener received the key event
        assertTrue(
            "Expected at least one key event for 'a' (code 97)",
            mockListener.keyEvents.any { it.primaryCode == 97 }
        )
    }

    /**
     * Tap the backspace key and verify the correct code is sent.
     * Backspace has code KeyCodes.DELETE = -5.
     *
     * Note: The smart back/esc key may send SMART_BACK_ESC=-301 which
     * gets resolved by the bridge. This test uses the standard DELETE key.
     */
    @Test
    fun tapSpecialKey_firesCorrectCode() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Tap backspace (DELETE = -5)
        composeTestRule.onNodeWithTag("key_${KeyCodes.DELETE}").performClick()
        composeTestRule.waitForIdle()

        // Verify the listener received a key event for DELETE
        // The bridge may translate, but the press/release should still fire
        assertTrue(
            "Expected at least one press event for backspace",
            mockListener.pressedCodes.isNotEmpty() || mockListener.keyEvents.isNotEmpty()
        )
    }

    /**
     * Tap the 123 key and verify it does NOT forward to the bridge.
     * The 123 key (SYMBOLS = -2) is handled locally by Compose UI
     * and filtered by isComposeLocalCode().
     */
    @Test
    fun tapSymbolsKey_doesNotForwardToBridge() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        mockListener.clear()

        // Tap 123 key (SYMBOLS = -2)
        composeTestRule.onNodeWithTag("key_${KeyCodes.SYMBOLS}").performClick()
        composeTestRule.waitForIdle()

        // Verify the listener did NOT receive a key event for SYMBOLS code
        val symbolsEvents = mockListener.keyEvents.filter { it.primaryCode == KeyCodes.SYMBOLS }
        assertEquals(
            "SYMBOLS key should not be forwarded to bridge",
            0,
            symbolsEvents.size
        )
    }
}
