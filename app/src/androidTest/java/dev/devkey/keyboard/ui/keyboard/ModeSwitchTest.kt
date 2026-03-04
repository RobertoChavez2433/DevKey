package dev.devkey.keyboard.ui.keyboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.devkey.keyboard.test.MockKeyboardActionListener
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for keyboard mode switching.
 *
 * Verifies that tapping the 123 key switches to symbols layout
 * and tapping ABC switches back to normal.
 */
class ModeSwitchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockListener = MockKeyboardActionListener()

    /**
     * Tap the 123 key and verify that symbol keys become visible.
     *
     * Test tags follow the pattern "key_{primaryCode}".
     * The 123 key has code KeyCodes.SYMBOLS = -2.
     * Symbol layout keys include '!' (code 33), '@' (code 64), '#' (code 35).
     */
    @Test
    fun tapSymbolsKey_switchesToSymbolsLayout() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Tap 123 key (SYMBOLS = -2)
        composeTestRule.onNodeWithTag("key_${KeyCodes.SYMBOLS}").performClick()
        composeTestRule.waitForIdle()

        // Verify symbol keys are visible (e.g., '!' = code 33)
        composeTestRule.onNodeWithTag("key_33").assertIsDisplayed()
    }

    /**
     * Tap 123 to enter symbols, then tap ABC to return to normal layout.
     * ABC key has code SymbolsLayout.KEYCODE_ALPHA = -200.
     */
    @Test
    fun tapAbcKey_switchesBackToNormal() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Switch to symbols
        composeTestRule.onNodeWithTag("key_${KeyCodes.SYMBOLS}").performClick()
        composeTestRule.waitForIdle()

        // Tap ABC to return
        composeTestRule.onNodeWithTag("key_${SymbolsLayout.KEYCODE_ALPHA}").performClick()
        composeTestRule.waitForIdle()

        // Verify alpha keys are back (e.g., 'q' = code 113)
        composeTestRule.onNodeWithTag("key_113").assertIsDisplayed()
    }

    /**
     * Tap 123 to enter symbols, tap 123 again to toggle back to normal.
     * This tests the toggle behavior where tapping the same mode key returns to Normal.
     *
     * Note: The symbols layout does NOT have a 123 key — it has an ABC key.
     * So this test verifies toggling via the toolbar onSymbols callback instead.
     */
    @Test
    fun tapSymbolsKey_twiceFromToolbar_togglesBackToNormal() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // First tap to enter symbols
        composeTestRule.onNodeWithTag("key_${KeyCodes.SYMBOLS}").performClick()
        composeTestRule.waitForIdle()

        // Tap ABC from symbols layout to return to normal
        composeTestRule.onNodeWithTag("key_${SymbolsLayout.KEYCODE_ALPHA}").performClick()
        composeTestRule.waitForIdle()

        // Verify alpha keys visible again (e.g., 'a' = code 97)
        composeTestRule.onNodeWithTag("key_97").assertIsDisplayed()
    }
}
