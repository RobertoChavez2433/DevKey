package dev.devkey.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SymbolsLayout.
 *
 * Verifies row count, key presence, long-press mappings, key types,
 * and the KEYCODE_ALPHA constant value.
 */
class SymbolsLayoutTest {

    private val layout = SymbolsLayout.layout

    // --- KEYCODE_ALPHA constant ---

    @Test
    fun `KEYCODE_ALPHA constant is -200`() {
        assertEquals(-200, SymbolsLayout.KEYCODE_ALPHA)
    }

    // --- Row structure ---

    @Test
    fun `layout has 5 rows`() {
        assertEquals(5, layout.rows.size)
    }

    @Test
    fun `row 1 (number row) has 10 keys`() {
        assertEquals(10, layout.rows[0].keys.size)
    }

    @Test
    fun `row 1 keys are all of type NUMBER`() {
        val numberRow = layout.rows[0]
        assertTrue(numberRow.keys.all { it.type == KeyType.NUMBER })
    }

    @Test
    fun `row 1 contains digits 0 through 9`() {
        val numberRow = layout.rows[0]
        val labels = numberRow.keys.map { it.primaryLabel }.toSet()
        assertTrue(labels.containsAll(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")))
    }

    // --- Symbol row key presence ---

    @Test
    fun `row 2 contains @ # $ _ & - + ( ) and slash`() {
        val row = layout.rows[1]
        val labels = row.keys.map { it.primaryLabel }
        assertTrue(labels.contains("@"))
        assertTrue(labels.contains("#"))
        assertTrue(labels.contains("$"))
        assertTrue(labels.contains("_"))
        assertTrue(labels.contains("&"))
        assertTrue(labels.contains("-"))
        assertTrue(labels.contains("+"))
        assertTrue(labels.contains("("))
        assertTrue(labels.contains(")"))
        assertTrue(labels.contains("/"))
    }

    // --- Long-press mappings ---

    @Test
    fun `dollar sign long-press maps to euro sign`() {
        val row = layout.rows[1]
        val dollarKey = row.keys.find { it.primaryLabel == "$" }
        assertNotNull(dollarKey)
        assertEquals("\u20AC", dollarKey!!.longPressLabel)
        assertEquals('\u20AC'.code, dollarKey.longPressCode)
    }

    @Test
    fun `open paren long-press maps to less-than sign`() {
        val row = layout.rows[1]
        val openParenKey = row.keys.find { it.primaryLabel == "(" }
        assertNotNull(openParenKey)
        assertEquals("<", openParenKey!!.longPressLabel)
        assertEquals('<'.code, openParenKey.longPressCode)
    }

    @Test
    fun `close paren long-press maps to greater-than sign`() {
        val row = layout.rows[1]
        val closeParenKey = row.keys.find { it.primaryLabel == ")" }
        assertNotNull(closeParenKey)
        assertEquals(">", closeParenKey!!.longPressLabel)
        assertEquals('>'.code, closeParenKey.longPressCode)
    }

    @Test
    fun `slash long-press maps to backslash`() {
        val row = layout.rows[1]
        val slashKey = row.keys.find { it.primaryLabel == "/" }
        assertNotNull(slashKey)
        assertEquals("\\", slashKey!!.longPressLabel)
        assertEquals('\\'.code, slashKey.longPressCode)
    }

    // --- Bottom row (ABC button) ---

    @Test
    fun `bottom row contains ABC button with KEYCODE_ALPHA`() {
        val bottomRow = layout.rows[4]
        val abcKey = bottomRow.keys.find { it.primaryLabel == "ABC" }
        assertNotNull(abcKey)
        assertEquals(SymbolsLayout.KEYCODE_ALPHA, abcKey!!.primaryCode)
    }

    @Test
    fun `ABC button type is ACTION`() {
        val bottomRow = layout.rows[4]
        val abcKey = bottomRow.keys.find { it.primaryLabel == "ABC" }
        assertNotNull(abcKey)
        assertEquals(KeyType.ACTION, abcKey!!.type)
    }

    @Test
    fun `bottom row contains emoji key`() {
        val bottomRow = layout.rows[4]
        val emojiKey = bottomRow.keys.find { it.primaryCode == KeyCodes.EMOJI }
        assertNotNull("Emoji key should be in symbols bottom row", emojiKey)
        assertEquals(KeyType.TOGGLE, emojiKey!!.type)
    }

    @Test
    fun `bottom row contains spacebar key`() {
        val bottomRow = layout.rows[4]
        val spaceKey = bottomRow.keys.find { it.type == KeyType.SPACEBAR }
        assertNotNull(spaceKey)
        assertEquals(' '.code, spaceKey!!.primaryCode)
    }

    @Test
    fun `bottom row contains Enter key`() {
        val bottomRow = layout.rows[4]
        val enterKey = bottomRow.keys.find { it.primaryLabel == "Enter" }
        assertNotNull(enterKey)
        assertEquals(KeyCodes.ENTER, enterKey!!.primaryCode)
    }

    // --- Key type verification ---

    @Test
    fun `row 2 keys are all SPECIAL type`() {
        val row = layout.rows[1]
        assertTrue(row.keys.all { it.type == KeyType.SPECIAL })
    }

    @Test
    fun `at-sign key has no long-press`() {
        val row = layout.rows[1]
        val atKey = row.keys.find { it.primaryLabel == "@" }
        assertNotNull(atKey)
        assertNull(atKey!!.longPressLabel)
        assertNull(atKey.longPressCode)
    }

    // --- Row 3 (punctuation row, index 2) ---

    @Test
    fun `row 3 has 8 punctuation keys`() {
        assertEquals(8, layout.rows[2].keys.size)
    }

    @Test
    fun `row 3 has asterisk star`() {
        val key = layout.rows[2].keys.find { it.primaryLabel == "*" }
        assertNotNull(key)
        assertEquals('*'.code, key!!.primaryCode)
    }

    @Test
    fun `row 3 double-quote has smart quote long-press`() {
        val key = layout.rows[2].keys.find { it.primaryLabel == "\"" }
        assertNotNull(key)
        assertEquals("\u201C", key!!.longPressLabel)
        assertEquals('\u201C'.code, key.longPressCode)
    }

    @Test
    fun `row 3 single-quote has smart quote long-press`() {
        val key = layout.rows[2].keys.find { it.primaryLabel == "'" }
        assertNotNull(key)
        assertEquals("\u2018", key!!.longPressLabel)
        assertEquals('\u2018'.code, key.longPressCode)
    }

    @Test
    fun `row 3 exclamation has inverted long-press`() {
        val key = layout.rows[2].keys.find { it.primaryLabel == "!" }
        assertNotNull(key)
        assertEquals("\u00A1", key!!.longPressLabel)
        assertEquals('\u00A1'.code, key.longPressCode)
    }

    @Test
    fun `row 3 question has inverted long-press`() {
        val key = layout.rows[2].keys.find { it.primaryLabel == "?" }
        assertNotNull(key)
        assertEquals("\u00BF", key!!.longPressLabel)
        assertEquals('\u00BF'.code, key.longPressCode)
    }

    @Test
    fun `row 3 colon and semicolon have no long-press`() {
        val colon = layout.rows[2].keys.find { it.primaryLabel == ":" }
        val semi = layout.rows[2].keys.find { it.primaryLabel == ";" }
        assertNotNull(colon)
        assertNotNull(semi)
        assertNull(colon!!.longPressLabel)
        assertNull(semi!!.longPressLabel)
    }

    @Test
    fun `row 3 comma has no long-press`() {
        val key = layout.rows[2].keys.find { it.primaryLabel == "," }
        assertNotNull(key)
        assertNull(key!!.longPressLabel)
    }

    // --- Row 4 (extended symbols, index 3) ---

    @Test
    fun `row 4 has 10 extended symbol keys`() {
        assertEquals(10, layout.rows[3].keys.size)
    }

    @Test
    fun `row 4 tilde has euro long-press`() {
        val key = layout.rows[3].keys.find { it.primaryLabel == "~" }
        assertNotNull(key)
        assertEquals("\u20AC", key!!.longPressLabel)
        assertEquals('\u20AC'.code, key.longPressCode)
    }

    @Test
    fun `row 4 backtick has pound long-press`() {
        val key = layout.rows[3].keys.find { it.primaryLabel == "`" }
        assertNotNull(key)
        assertEquals("\u00A3", key!!.longPressLabel)
        assertEquals('\u00A3'.code, key.longPressCode)
    }

    @Test
    fun `row 4 pipe has yen long-press`() {
        val key = layout.rows[3].keys.find { it.primaryLabel == "|" }
        assertNotNull(key)
        assertEquals("\u00A5", key!!.longPressLabel)
        assertEquals('\u00A5'.code, key.longPressCode)
    }

    @Test
    fun `row 4 bullet has cent long-press`() {
        val key = layout.rows[3].keys.find { it.primaryLabel == "\u2022" }
        assertNotNull(key)
        assertEquals("\u00A2", key!!.longPressLabel)
        assertEquals('\u00A2'.code, key.longPressCode)
    }

    @Test
    fun `row 4 sqrt has won long-press`() {
        val key = layout.rows[3].keys.find { it.primaryLabel == "\u221A" }
        assertNotNull(key)
        assertEquals("\u20A9", key!!.longPressLabel)
        assertEquals('\u20A9'.code, key.longPressCode)
    }

    @Test
    fun `row 4 pi div mult para delta have no long-press`() {
        val noLongPress = listOf("\u03C0", "\u00F7", "\u00D7", "\u00B6", "\u2206")
        for (label in noLongPress) {
            val key = layout.rows[3].keys.find { it.primaryLabel == label }
            assertNotNull("Key '$label' should exist in row 4", key)
            assertNull("Key '$label' should have no long-press", key!!.longPressLabel)
        }
    }

    // --- Bottom row additions ---

    @Test
    fun `bottom row comma and period present`() {
        val bottomRow = layout.rows[4]
        val comma = bottomRow.keys.find { it.primaryLabel == "," }
        val period = bottomRow.keys.find { it.primaryLabel == "." }
        assertNotNull(comma)
        assertNotNull(period)
        assertEquals(','.code, comma!!.primaryCode)
        assertEquals('.'.code, period!!.primaryCode)
    }

    @Test
    fun `bottom row ABC weight is 1_0`() {
        val abcKey = layout.rows[4].keys.find { it.primaryLabel == "ABC" }
        assertNotNull(abcKey)
        assertEquals(1.0f, abcKey!!.weight)
    }

    @Test
    fun `bottom row Enter weight is 1_6`() {
        val enterKey = layout.rows[4].keys.find { it.primaryLabel == "Enter" }
        assertNotNull(enterKey)
        assertEquals(1.6f, enterKey!!.weight)
    }

    @Test
    fun `bottom row has 6 keys`() {
        assertEquals(6, layout.rows[4].keys.size)
    }
}
