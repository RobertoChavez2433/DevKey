package dev.devkey.keyboard.ui.keyboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.test.MockKeyboardActionListener
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for keyboard layout rendering.
 *
 * Verifies that the full layout mode renders the expected rows and keys.
 * These tests use the default FULL layout mode.
 */
class LayoutRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockListener = MockKeyboardActionListener()

    /**
     * Verify that key nodes for all expected rows are present.
     *
     * Full mode has 6 rows:
     * - Row 0: Number row (1-0)
     * - Row 1: QWERTY row (q,w,e,r,t,y,u,i,o,p)
     * - Row 2: Home row (a,s,d,f,g,h,j,k,l)
     * - Row 3: Z row (z,x,c,v,b,n,m)
     * - Row 4: Space row (symbols, space, etc.)
     * - Row 5: Utility row (Ctrl, Alt, Tab, arrows)
     */
    @Test
    fun fullMode_rendersExpectedKeys() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Verify keys from each row exist
        // Row 0 - number row: '1' (code 49)
        composeTestRule.onNodeWithTag("key_49").assertIsDisplayed()
        // Row 1 - QWERTY: 'q' (code 113)
        composeTestRule.onNodeWithTag("key_113").assertIsDisplayed()
        // Row 2 - home: 'a' (code 97)
        composeTestRule.onNodeWithTag("key_97").assertIsDisplayed()
        // Row 3 - Z row: 'z' (code 122)
        composeTestRule.onNodeWithTag("key_122").assertIsDisplayed()
    }

    /**
     * Verify the number row keys are present (digits 1-0).
     */
    @Test
    fun fullMode_hasNumberRow() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Check digits 1 through 0 (ASCII codes 49-57 for 1-9, 48 for 0)
        for (digit in listOf(49, 50, 51, 52, 53, 54, 55, 56, 57, 48)) {
            composeTestRule.onNodeWithTag("key_$digit").assertIsDisplayed()
        }
    }

    /**
     * Verify the utility row keys are present (Ctrl, Alt, Tab, arrows).
     */
    @Test
    fun fullMode_hasUtilityRow() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // Ctrl key (code -113)
        composeTestRule.onNodeWithTag("key_${KeyCodes.CTRL_LEFT}").assertIsDisplayed()
        // Alt key (code -57)
        composeTestRule.onNodeWithTag("key_${KeyCodes.ALT_LEFT}").assertIsDisplayed()
        // Tab key (code 9)
        composeTestRule.onNodeWithTag("key_${KeyCodes.TAB}").assertIsDisplayed()
    }

    /**
     * Verify that the Shift key is present.
     */
    @Test
    fun fullMode_hasShiftKey() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        composeTestRule.onNodeWithTag("key_${Keyboard.KEYCODE_SHIFT}").assertIsDisplayed()
    }

    /**
     * Verify the QWERTY row has all 10 expected keys.
     */
    @Test
    fun fullMode_hasFullQwertyRow() {
        composeTestRule.setContent {
            DevKeyKeyboard(actionListener = mockListener)
        }

        // q=113, w=119, e=101, r=114, t=116, y=121, u=117, i=105, o=111, p=112
        for (code in listOf(113, 119, 101, 114, 116, 121, 117, 105, 111, 112)) {
            composeTestRule.onNodeWithTag("key_$code").assertIsDisplayed()
        }
    }
}
