package dev.devkey.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for QwertyLayout FULL (6-row) layout.
 *
 * Verifies number row, QWERTY row, home row, Z row, space row, utility row,
 * long-press maps, key types, modifier keys, arrow keys, and repeatable keys.
 */
class QwertyLayoutFullTest {

    private val layout = QwertyLayout.getLayout(LayoutMode.FULL)

    // --- Row structure ---

    @Test
    fun `full layout has 6 rows`() {
        assertEquals(6, layout.rows.size)
    }

    // --- Number row (row 0) ---

    @Test
    fun `number row is row 0 and contains backtick key`() {
        val numberRow = layout.rows[0]
        val backtickKey = numberRow.keys.find { it.primaryLabel == "`" }
        assertNotNull(backtickKey)
        assertEquals('`'.code, backtickKey!!.primaryCode)
    }

    @Test
    fun `number row has 11 keys`() {
        assertEquals(11, layout.rows[0].keys.size)
    }

    @Test
    fun `number row backtick long-press maps to tilde`() {
        val numberRow = layout.rows[0]
        val backtickKey = numberRow.keys.find { it.primaryLabel == "`" }
        assertNotNull(backtickKey)
        assertEquals("~", backtickKey!!.longPressLabel)
        assertEquals('~'.code, backtickKey.longPressCode)
    }

    @Test
    fun `number row contains dedicated 0 key`() {
        val numberRow = layout.rows[0]
        val zeroKey = numberRow.keys.find { it.primaryLabel == "0" }
        assertNotNull("Dedicated 0 key should be in number row", zeroKey)
        assertEquals('0'.code, zeroKey!!.primaryCode)
        assertEquals(KeyType.NUMBER, zeroKey.type)
    }

    @Test
    fun `number row contains digits 1 through 9`() {
        val numberRow = layout.rows[0]
        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
        for (digit in digits) {
            val key = numberRow.keys.find { it.primaryLabel == digit }
            assertNotNull("Digit $digit should be in number row", key)
            assertEquals("Digit $digit should be NUMBER type", KeyType.NUMBER, key!!.type)
        }
    }

    @Test
    fun `number row does NOT contain Esc key`() {
        val numberRow = layout.rows[0]
        val escKey = numberRow.keys.find { it.primaryLabel == "Esc" }
        assertNull("Esc key should NOT be in number row", escKey)
    }

    @Test
    fun `number row does NOT contain Tab key`() {
        val numberRow = layout.rows[0]
        val tabKey = numberRow.keys.find { it.primaryLabel == "Tab" }
        assertNull("Tab key should NOT be in number row", tabKey)
    }

    @Test
    fun `number row digits have no long-press`() {
        val numberRow = layout.rows[0]
        for (digit in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) {
            val key = numberRow.keys.find { it.primaryLabel == digit }
            assertNotNull("Digit $digit should exist", key)
            assertNull("Digit $digit should have no long-press", key!!.longPressLabel)
        }
    }

    // --- QWERTY row (row 1) ---

    @Test
    fun `QWERTY row is row 1 and contains all 10 letter keys`() {
        val qwertyRow = layout.rows[1]
        val letters = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        for (letter in letters) {
            val key = qwertyRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Key '$letter' should be in QWERTY row", key)
        }
    }

    @Test
    fun `QWERTY row Q long-press maps to exclamation mark`() {
        val qwertyRow = layout.rows[1]
        val qKey = qwertyRow.keys.find { it.primaryLabel == "q" }
        assertNotNull(qKey)
        assertEquals("!", qKey!!.longPressLabel)
        assertEquals('!'.code, qKey.longPressCode)
    }

    @Test
    fun `QWERTY row letter keys are of type LETTER`() {
        val qwertyRow = layout.rows[1]
        assertTrue(qwertyRow.keys.all { it.type == KeyType.LETTER })
    }

    // --- Home row (row 2) ---

    @Test
    fun `home row is row 2 and contains A through L keys`() {
        val homeRow = layout.rows[2]
        val letters = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        for (letter in letters) {
            val key = homeRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Key '$letter' should be in home row", key)
        }
    }

    @Test
    fun `home row A long-press maps to tilde`() {
        val homeRow = layout.rows[2]
        val aKey = homeRow.keys.find { it.primaryLabel == "a" }
        assertNotNull(aKey)
        assertEquals("~", aKey!!.longPressLabel)
        assertEquals('~'.code, aKey.longPressCode)
    }

    @Test
    fun `home row F long-press maps to open brace`() {
        val homeRow = layout.rows[2]
        val fKey = homeRow.keys.find { it.primaryLabel == "f" }
        assertNotNull(fKey)
        assertEquals("{", fKey!!.longPressLabel)
        assertEquals('{'.code, fKey.longPressCode)
    }

    // --- Z row (row 3) ---

    @Test
    fun `Z row is row 3 and contains Shift key of type MODIFIER`() {
        val zRow = layout.rows[3]
        val shiftKey = zRow.keys.find { it.primaryLabel == "Shift" }
        assertNotNull(shiftKey)
        assertEquals(KeyType.MODIFIER, shiftKey!!.type)
    }

    @Test
    fun `Z row Shift has no long-press in full layout`() {
        val zRow = layout.rows[3]
        val shiftKey = zRow.keys.find { it.primaryLabel == "Shift" }
        assertNotNull(shiftKey)
        assertNull(shiftKey!!.longPressLabel)
        assertNull(shiftKey.longPressCode)
    }

    @Test
    fun `Z row smart backspace key sends SMART_BACK_ESC code`() {
        val zRow = layout.rows[3]
        val smartKey = zRow.keys.find { it.primaryCode == KeyCodes.SMART_BACK_ESC }
        assertNotNull("Smart backspace/esc key should exist in Z row", smartKey)
        assertEquals(KeyType.ACTION, smartKey!!.type)
        assertTrue(smartKey.isRepeatable)
    }

    @Test
    fun `Z row smart backspace long-press maps to Escape`() {
        val zRow = layout.rows[3]
        val smartKey = zRow.keys.find { it.primaryCode == KeyCodes.SMART_BACK_ESC }
        assertNotNull(smartKey)
        assertEquals("Esc", smartKey!!.longPressLabel)
        assertEquals(KeyCodes.ESCAPE, smartKey.longPressCode)
    }

    @Test
    fun `Z row Shift weight is 1_5`() {
        val shiftKey = layout.rows[3].keys.find { it.primaryLabel == "Shift" }
        assertNotNull(shiftKey)
        assertEquals(1.5f, shiftKey!!.weight)
    }

    @Test
    fun `Z row smart backspace weight is 1_5`() {
        val smartKey = layout.rows[3].keys.find { it.primaryCode == KeyCodes.SMART_BACK_ESC }
        assertNotNull(smartKey)
        assertEquals(1.5f, smartKey!!.weight)
    }

    // --- Space row (row 4) ---

    @Test
    fun `space row is row 4 and has 6 keys`() {
        assertEquals(6, layout.rows[4].keys.size)
    }

    @Test
    fun `space row contains 123 key with SYMBOLS keycode`() {
        val spaceRow = layout.rows[4]
        val key123 = spaceRow.keys.find { it.primaryLabel == "123" }
        assertNotNull(key123)
        assertEquals(KeyCodes.SYMBOLS, key123!!.primaryCode)
        assertEquals(KeyType.TOGGLE, key123.type)
    }

    @Test
    fun `space row contains emoji key with EMOJI keycode`() {
        val spaceRow = layout.rows[4]
        val emojiKey = spaceRow.keys.find { it.primaryCode == KeyCodes.EMOJI }
        assertNotNull(emojiKey)
        assertEquals(KeyType.TOGGLE, emojiKey!!.type)
    }

    @Test
    fun `space row contains spacebar of type SPACEBAR`() {
        val spaceRow = layout.rows[4]
        val spaceKey = spaceRow.keys.find { it.type == KeyType.SPACEBAR }
        assertNotNull(spaceKey)
        assertEquals(' '.code, spaceKey!!.primaryCode)
        assertEquals(4.0f, spaceKey.weight)
    }

    @Test
    fun `space row contains comma and period`() {
        val spaceRow = layout.rows[4]
        val comma = spaceRow.keys.find { it.primaryLabel == "," }
        val period = spaceRow.keys.find { it.primaryLabel == "." }
        assertNotNull(comma)
        assertNotNull(period)
    }

    @Test
    fun `space row Enter key has ACTION type`() {
        val spaceRow = layout.rows[4]
        val enterKey = spaceRow.keys.find { it.primaryLabel == "Enter" }
        assertNotNull(enterKey)
        assertEquals(KeyType.ACTION, enterKey!!.type)
        assertEquals(KeyCodes.ENTER, enterKey.primaryCode)
    }

    // --- Utility row (row 5) ---

    @Test
    fun `utility row is row 5 and has 8 keys`() {
        assertEquals(8, layout.rows[5].keys.size)
    }

    @Test
    fun `utility row contains Ctrl key of type UTILITY`() {
        val utilRow = layout.rows[5]
        val ctrlKey = utilRow.keys.find { it.primaryLabel == "Ctrl" }
        assertNotNull(ctrlKey)
        assertEquals(KeyType.UTILITY, ctrlKey!!.type)
        assertEquals(KeyCodes.CTRL_LEFT, ctrlKey.primaryCode)
    }

    @Test
    fun `utility row contains Alt key of type UTILITY`() {
        val utilRow = layout.rows[5]
        val altKey = utilRow.keys.find { it.primaryLabel == "Alt" }
        assertNotNull(altKey)
        assertEquals(KeyType.UTILITY, altKey!!.type)
        assertEquals(KeyCodes.ALT_LEFT, altKey.primaryCode)
    }

    @Test
    fun `utility row contains Tab key of type UTILITY`() {
        val utilRow = layout.rows[5]
        val tabKey = utilRow.keys.find { it.primaryLabel == "Tab" }
        assertNotNull(tabKey)
        assertEquals(KeyType.UTILITY, tabKey!!.type)
        assertEquals(KeyCodes.TAB, tabKey.primaryCode)
    }

    @Test
    fun `utility row contains 4 arrow keys`() {
        val utilRow = layout.rows[5]
        val arrowLabels = listOf("\u2190", "\u2191", "\u2193", "\u2192")
        for (label in arrowLabels) {
            val key = utilRow.keys.find { it.primaryLabel == label }
            assertNotNull("Arrow '$label' should exist in utility row", key)
            assertEquals(KeyType.UTILITY, key!!.type)
        }
    }

    @Test
    fun `utility row left arrow is repeatable`() {
        val utilRow = layout.rows[5]
        val leftArrow = utilRow.keys.find { it.primaryLabel == "\u2190" }
        assertNotNull(leftArrow)
        assertTrue(leftArrow!!.isRepeatable)
    }

    @Test
    fun `utility row right arrow is repeatable`() {
        val utilRow = layout.rows[5]
        val rightArrow = utilRow.keys.find { it.primaryLabel == "\u2192" }
        assertNotNull(rightArrow)
        assertTrue(rightArrow!!.isRepeatable)
    }

    @Test
    fun `utility row up arrow not repeatable`() {
        val utilRow = layout.rows[5]
        val upArrow = utilRow.keys.find { it.primaryLabel == "\u2191" }
        assertNotNull(upArrow)
        assertFalse(upArrow!!.isRepeatable)
    }

    @Test
    fun `utility row down arrow not repeatable`() {
        val utilRow = layout.rows[5]
        val downArrow = utilRow.keys.find { it.primaryLabel == "\u2193" }
        assertNotNull(downArrow)
        assertFalse(downArrow!!.isRepeatable)
    }

    // --- Full QWERTY row long-press symbols ---

    @Test
    fun `full QWERTY row all 10 long-press symbols`() {
        val qwertyRow = layout.rows[1]
        val expected = mapOf(
            "q" to "!", "w" to "@", "e" to "#", "r" to "$", "t" to "%",
            "y" to "^", "u" to "&", "i" to "*", "o" to "(", "p" to ")"
        )
        for ((letter, symbol) in expected) {
            val key = qwertyRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Key '$letter' should exist", key)
            assertEquals("Key '$letter' long-press should be '$symbol'", symbol, key!!.longPressLabel)
        }
    }

    @Test
    fun `full home row all 9 long-press symbols`() {
        val homeRow = layout.rows[2]
        val expected = mapOf(
            "a" to "~", "s" to "|", "d" to "\\", "f" to "{",
            "g" to "}", "h" to "[", "j" to "]", "k" to "\"", "l" to "'"
        )
        for ((letter, symbol) in expected) {
            val key = homeRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Key '$letter' should exist", key)
            assertEquals("Key '$letter' long-press should be '$symbol'", symbol, key!!.longPressLabel)
        }
    }

    @Test
    fun `full Z row all 7 long-press symbols`() {
        val zRow = layout.rows[3]
        val expected = mapOf(
            "z" to ";", "x" to ":", "c" to "/", "v" to "?",
            "b" to "<", "n" to ">", "m" to "_"
        )
        for ((letter, symbol) in expected) {
            val key = zRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Key '$letter' should exist", key)
            assertEquals("Key '$letter' long-press should be '$symbol'", symbol, key!!.longPressLabel)
        }
    }
}
