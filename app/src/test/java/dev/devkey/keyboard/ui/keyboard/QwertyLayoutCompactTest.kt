package dev.devkey.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for QwertyLayout COMPACT and COMPACT_DEV modes.
 */
class QwertyLayoutCompactTest {

    // --- LayoutMode enum ---

    @Test
    fun `LayoutMode has exactly 3 values`() {
        assertEquals(3, LayoutMode.values().size)
    }

    @Test
    fun `LayoutMode values are COMPACT, COMPACT_DEV, FULL`() {
        val names = LayoutMode.values().map { it.name }.toSet()
        assertTrue(names.contains("COMPACT"))
        assertTrue(names.contains("COMPACT_DEV"))
        assertTrue(names.contains("FULL"))
    }

    // --- COMPACT mode ---

    @Test
    fun `compact mode produces 4 rows`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        assertEquals(4, layout.rows.size)
    }

    @Test
    fun `compact mode letter keys have NO long-press`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        // Row 0: QWERTY, Row 1: home, Row 2: Z (letters only, not Shift/Del)
        for (rowIndex in 0..2) {
            for (key in layout.rows[rowIndex].keys) {
                if (key.type == KeyType.LETTER) {
                    assertNull(
                        "Compact mode key '${key.primaryLabel}' should have NO long-press",
                        key.longPressLabel
                    )
                }
            }
        }
    }

    @Test
    fun `compact mode first row is QWERTY not numbers`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        val firstRow = layout.rows[0]
        val hasLetters = firstRow.keys.any { it.type == KeyType.LETTER }
        assertTrue("First row should have letter keys", hasLetters)
    }

    @Test
    fun `compact mode has plain backspace not smart`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        val zRow = layout.rows[2]
        val delKey = zRow.keys.find { it.primaryLabel == "Del" }
        assertNotNull("Compact mode should have plain Del key", delKey)
        assertEquals(-5, delKey!!.primaryCode) // Keyboard.KEYCODE_DELETE
        assertNull("Compact Del should have NO long-press", delKey.longPressLabel)
    }

    @Test
    fun `compact mode has NO utility row`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        // Should be 4 rows, no UTILITY type keys
        for (row in layout.rows) {
            for (key in row.keys) {
                assertTrue(
                    "Compact mode should not have UTILITY keys, found: ${key.primaryLabel}",
                    key.type != KeyType.UTILITY
                )
            }
        }
    }

    @Test
    fun `compact mode space row has 123 and emoji keys`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        val spaceRow = layout.rows[3]
        val key123 = spaceRow.keys.find { it.primaryLabel == "123" }
        val emojiKey = spaceRow.keys.find { it.primaryCode == KeyCodes.EMOJI }
        assertNotNull("123 key should exist", key123)
        assertNotNull("Emoji key should exist", emojiKey)
        assertEquals(KeyCodes.SYMBOLS, key123!!.primaryCode)
    }

    // --- COMPACT_DEV mode ---

    @Test
    fun `compact dev mode produces 4 rows`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
        assertEquals(4, layout.rows.size)
    }

    @Test
    fun `compact dev Q long-press maps to digit 1`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
        val qwertyRow = layout.rows[0]
        val qKey = qwertyRow.keys.find { it.primaryLabel == "q" }
        assertNotNull(qKey)
        assertEquals("1", qKey!!.longPressLabel)
        assertEquals('1'.code, qKey.longPressCode)
    }

    @Test
    fun `compact dev P long-press maps to digit 0`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
        val qwertyRow = layout.rows[0]
        val pKey = qwertyRow.keys.find { it.primaryLabel == "p" }
        assertNotNull(pKey)
        assertEquals("0", pKey!!.longPressLabel)
        assertEquals('0'.code, pKey.longPressCode)
    }

    @Test
    fun `compact dev all QWERTY row keys map to digits`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
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
    fun `compact dev has smart backspace with Esc long-press`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
        val zRow = layout.rows[2]
        val smartKey = zRow.keys.find { it.primaryCode == KeyCodes.SMART_BACK_ESC }
        assertNotNull("Compact Dev should have smart backspace/esc", smartKey)
        assertEquals("Esc", smartKey!!.longPressLabel)
        assertEquals(KeyCodes.ESCAPE, smartKey.longPressCode)
    }

    @Test
    fun `compact dev home row has symbol long-press`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
        val homeRow = layout.rows[1]
        val aKey = homeRow.keys.find { it.primaryLabel == "a" }
        assertNotNull(aKey)
        assertEquals("~", aKey!!.longPressLabel)
    }

    @Test
    fun `compact dev Z row letter long-press same as full`() {
        val devLayout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
        val fullLayout = QwertyLayout.getLayout(LayoutMode.FULL)
        val devZRow = devLayout.rows[2]
        val fullZRow = fullLayout.rows[3]
        val letters = listOf("z", "x", "c", "v", "b", "n", "m")
        for (letter in letters) {
            val devKey = devZRow.keys.find { it.primaryLabel == letter }
            val fullKey = fullZRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Dev '$letter' should exist", devKey)
            assertNotNull("Full '$letter' should exist", fullKey)
            assertEquals(
                "Long-press for '$letter' should match",
                fullKey!!.longPressLabel,
                devKey!!.longPressLabel
            )
        }
    }
}
