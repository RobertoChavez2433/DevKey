package dev.devkey.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for QwertyLayout compact mode.
 */
class QwertyLayoutCompactTest {

    @Test
    fun `compactMode false produces 5 rows`() {
        val layout = QwertyLayout.getLayout(compactMode = false)
        assertEquals(5, layout.rows.size)
    }

    @Test
    fun `compactMode true produces 4 rows (no number row)`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        assertEquals(4, layout.rows.size)
    }

    @Test
    fun `compactMode true Q long-press maps to digit 1`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val qwertyRow = layout.rows[0] // first row in compact mode
        val qKey = qwertyRow.keys.find { it.primaryLabel == "q" }
        assertNotNull(qKey)
        assertEquals("1", qKey!!.longPressLabel)
        assertEquals('1'.code, qKey.longPressCode)
    }

    @Test
    fun `compactMode true W long-press maps to digit 2`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val qwertyRow = layout.rows[0]
        val wKey = qwertyRow.keys.find { it.primaryLabel == "w" }
        assertNotNull(wKey)
        assertEquals("2", wKey!!.longPressLabel)
        assertEquals('2'.code, wKey.longPressCode)
    }

    @Test
    fun `compactMode true E long-press maps to digit 3`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val qwertyRow = layout.rows[0]
        val eKey = qwertyRow.keys.find { it.primaryLabel == "e" }
        assertNotNull(eKey)
        assertEquals("3", eKey!!.longPressLabel)
        assertEquals('3'.code, eKey.longPressCode)
    }

    @Test
    fun `compactMode true P long-press maps to digit 0`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val qwertyRow = layout.rows[0]
        val pKey = qwertyRow.keys.find { it.primaryLabel == "p" }
        assertNotNull(pKey)
        assertEquals("0", pKey!!.longPressLabel)
        assertEquals('0'.code, pKey.longPressCode)
    }

    @Test
    fun `compactMode true all QWERTY row keys map to digits`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val qwertyRow = layout.rows[0]
        val expectedMappings = mapOf(
            "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
            "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0"
        )
        for ((label, digit) in expectedMappings) {
            val key = qwertyRow.keys.find { it.primaryLabel == label }
            assertNotNull("Key $label should exist", key)
            assertEquals("Key $label long-press should be $digit", digit, key!!.longPressLabel)
            assertEquals("Key $label long-press code should be ${digit[0].code}", digit[0].code, key.longPressCode)
        }
    }

    @Test
    fun `compactMode true Shift long-press maps to Esc keycode`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val zRow = layout.rows[2] // third row in compact (z row)
        val shiftKey = zRow.keys.find { it.primaryLabel == "Shift" }
        assertNotNull(shiftKey)
        assertEquals("Esc", shiftKey!!.longPressLabel)
        assertEquals(-111, shiftKey.longPressCode) // KEYCODE_ESCAPE
    }

    @Test
    fun `compactMode true Backspace long-press maps to Tab keycode`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val zRow = layout.rows[2] // third row in compact (z row)
        val delKey = zRow.keys.find { it.primaryLabel == "Del" }
        assertNotNull(delKey)
        assertEquals("Tab", delKey!!.longPressLabel)
        assertEquals(61, delKey.longPressCode) // KEYCODE_TAB = 61
    }

    @Test
    fun `compactMode false preserves original QWERTY long-press mappings`() {
        val layout = QwertyLayout.getLayout(compactMode = false)
        val qwertyRow = layout.rows[1] // second row in full layout (after number row)
        val qKey = qwertyRow.keys.find { it.primaryLabel == "q" }
        assertNotNull(qKey)
        assertEquals("!", qKey!!.longPressLabel)
        assertEquals('!'.code, qKey.longPressCode)
    }

    @Test
    fun `compactMode false Shift has no long-press`() {
        val layout = QwertyLayout.getLayout(compactMode = false)
        val zRow = layout.rows[3] // fourth row in full layout
        val shiftKey = zRow.keys.find { it.primaryLabel == "Shift" }
        assertNotNull(shiftKey)
        assertNull(shiftKey!!.longPressLabel)
        assertNull(shiftKey.longPressCode)
    }

    @Test
    fun `compactMode false Backspace has no long-press`() {
        val layout = QwertyLayout.getLayout(compactMode = false)
        val zRow = layout.rows[3] // fourth row in full layout
        val delKey = zRow.keys.find { it.primaryLabel == "Del" }
        assertNotNull(delKey)
        assertNull(delKey!!.longPressLabel)
        assertNull(delKey.longPressCode)
    }

    @Test
    fun `compactMode true first row is QWERTY not numbers`() {
        val layout = QwertyLayout.getLayout(compactMode = true)
        val firstRow = layout.rows[0]
        // First row should have letter keys, not number keys
        val hasLetters = firstRow.keys.any { it.type == KeyType.LETTER }
        val hasEsc = firstRow.keys.any { it.primaryLabel == "Esc" }
        assertEquals("First row should have letter keys", true, hasLetters)
        assertEquals("First row should not have Esc", false, hasEsc)
    }
}
