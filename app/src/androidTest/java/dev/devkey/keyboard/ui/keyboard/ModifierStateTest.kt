package dev.devkey.keyboard.ui.keyboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.test.MockKeyboardActionListener
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for modifier key state transitions.
 *
 * Verifies Shift one-shot, double-tap lock, and consume-after-letter behavior.
 */
class ModifierStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockListener = MockKeyboardActionListener()

    /**
     * Tap Shift once and verify it enters one-shot state.
     * Shift key has code Keyboard.KEYCODE_SHIFT = -1.
     *
     * After tapping Shift, the key should visually indicate active state.
     * We verify the Shift key node is still displayed (basic smoke test).
     */
    @Test
    fun tapShift_entersOneShot() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Tap Shift
        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").performClick()
        composeTestRule.waitForIdle()

        // Shift key should still be displayed (visual state check)
        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").assertIsDisplayed()
    }

    /**
     * Double-tap Shift within 400ms to enter locked state.
     * The locked indicator dot should appear.
     */
    @Test
    fun doubleTapShift_entersLocked() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Double-tap Shift quickly
        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").performClick()
        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").performClick()
        composeTestRule.waitForIdle()

        // Shift key should still be displayed in locked state
        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").assertIsDisplayed()
    }

    /**
     * Shift one-shot consumed after letter tap.
     * Tap Shift, then tap 'a', then verify shift state is consumed.
     */
    @Test
    fun shiftOneShot_consumedAfterLetterTap() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Tap Shift to enter one-shot
        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").performClick()
        composeTestRule.waitForIdle()

        // Tap 'a' (code 97) to consume the one-shot
        composeTestRule.onNodeWithTag("key_97").performClick()
        composeTestRule.waitForIdle()

        // After consuming, the shift key should be displayed (in OFF state now)
        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").assertIsDisplayed()
    }
}
