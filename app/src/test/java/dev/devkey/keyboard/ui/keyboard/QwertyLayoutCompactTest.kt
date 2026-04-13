package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.keyboard.model.Keyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for QwertyLayout COMPACT and COMPACT_DEV modes.
 *
 * COMPACT (with number row): 5 rows — number, qwerty, home, z, space
 * COMPACT (no number row):   4 rows — qwerty, home, z, space
 * COMPACT_DEV (no number row): 4 rows — qwerty, home, z, space
 * COMPACT_DEV (with number row): 5 rows — number, qwerty, home, z, space
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

    // --- COMPACT mode (default with number row) ---

    @Test
    fun `compact mode with number row produces 5 rows`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        assertEquals(5, layout.rows.size)
    }

    @Test
    fun `compact mode without number row produces 4 rows`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT, includeNumberRow = false)
        assertEquals(4, layout.rows.size)
    }

    @Test
    fun `compact mode first row is number row`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        val firstRow = layout.rows[0]
        assertTrue("First row should be numbers", firstRow.keys.all { it.type == KeyType.NUMBER })
    }

    @Test
    fun `compact mode QWERTY row is row 1 and has long-press symbols`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        val qwertyRow = layout.rows[1]
        val qKey = qwertyRow.keys.find { it.primaryLabel == "q" }
        assertNotNull(qKey)
        assertEquals("%", qKey!!.longPressLabel)
        assertEquals('%'.code, qKey.longPressCode)
    }

    @Test
    fun `compact mode all QWERTY row SwiftKey long-press symbols`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        val qwertyRow = layout.rows[1]
        val expected = mapOf(
            "q" to "%", "w" to "^", "e" to "~", "r" to "|", "t" to "[",
            "y" to "]", "u" to "<", "i" to ">", "o" to "{", "p" to "}"
        )
        for ((letter, symbol) in expected) {
            val key = qwertyRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Key '$letter' should exist", key)
            assertEquals("Key '$letter' long-press should be '$symbol'", symbol, key!!.longPressLabel)
        }
    }

    @Test
    fun `compact mode has plain backspace with erase glyph`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
        val zRow = layout.rows[3]
        val delKey = zRow.keys.find { it.primaryCode == Keyboard.KEYCODE_DELETE }
        assertNotNull("Compact mode should have plain Del key", delKey)
        assertEquals("\u232B", delKey!!.primaryLabel) // ⌫ erase glyph
        assertNull("Compact Del should have NO long-press", delKey.longPressLabel)
    }

    @Test
    fun `compact mode has NO utility row`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT)
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
        val spaceRow = layout.rows[4] // row 4 with number row
        val key123 = spaceRow.keys.find { it.primaryLabel == "123" }
        val emojiKey = spaceRow.keys.find { it.primaryCode == KeyCodes.EMOJI }
        assertNotNull("123 key should exist", key123)
        assertNotNull("Emoji key should exist", emojiKey)
        assertEquals(KeyCodes.SYMBOLS, key123!!.primaryCode)
    }

    // --- COMPACT_DEV mode ---
    // Without number row: 4 rows (qwerty, home, z, space)
    // With number row (default): 5 rows (number, qwerty, home, z, space)

    @Test
    fun `compact dev mode without number row produces 4 rows`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV, includeNumberRow = false)
        assertEquals(4, layout.rows.size)
    }

    @Test
    fun `compact dev mode with number row produces 5 rows`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)
        assertEquals(5, layout.rows.size)
    }

    @Test
    fun `compact dev Q long-press maps to exclamation`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV, includeNumberRow = false)
        val qwertyRow = layout.rows[0]
        val qKey = qwertyRow.keys.find { it.primaryLabel == "q" }
        assertNotNull(qKey)
        assertEquals("!", qKey!!.longPressLabel)
        assertEquals('!'.code, qKey.longPressCode)
    }

    @Test
    fun `compact dev P long-press maps to close paren`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV, includeNumberRow = false)
        val qwertyRow = layout.rows[0]
        val pKey = qwertyRow.keys.find { it.primaryLabel == "p" }
        assertNotNull(pKey)
        assertEquals(")", pKey!!.longPressLabel)
        assertEquals(')'.code, pKey.longPressCode)
    }

    @Test
    fun `compact dev all QWERTY row keys map to shift-symbols`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV, includeNumberRow = false)
        val qwertyRow = layout.rows[0]
        val expectedMappings = mapOf(
            "q" to "!", "w" to "@", "e" to "#", "r" to "$", "t" to "%",
            "y" to "^", "u" to "&", "i" to "*", "o" to "(", "p" to ")"
        )
        for ((label, symbol) in expectedMappings) {
            val key = qwertyRow.keys.find { it.primaryLabel == label }
            assertNotNull("Key $label should exist", key)
            assertEquals("Key $label long-press should be $symbol", symbol, key!!.longPressLabel)
        }
    }

    @Test
    fun `compact dev has smart backspace with Esc long-press`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV, includeNumberRow = false)
        val zRow = layout.rows[2]
        val smartKey = zRow.keys.find { it.primaryCode == KeyCodes.SMART_BACK_ESC }
        assertNotNull("Compact Dev should have smart backspace/esc", smartKey)
        assertEquals("Esc", smartKey!!.longPressLabel)
        assertEquals(KeyCodes.ESCAPE, smartKey.longPressCode)
    }

    @Test
    fun `compact dev home row has symbol long-press`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV, includeNumberRow = false)
        val homeRow = layout.rows[1]
        val aKey = homeRow.keys.find { it.primaryLabel == "a" }
        assertNotNull(aKey)
        assertEquals("~", aKey!!.longPressLabel)
    }

    @Test
    fun `compact dev Z row has hacker symbols`() {
        val layout = QwertyLayout.getLayout(LayoutMode.COMPACT_DEV, includeNumberRow = false)
        val zRow = layout.rows[2]
        val expected = mapOf(
            "z" to "[", "x" to "]", "c" to "\\", "v" to ":",
            "b" to ";", "n" to "<", "m" to ">"
        )
        for ((letter, symbol) in expected) {
            val key = zRow.keys.find { it.primaryLabel == letter }
            assertNotNull("Key '$letter' should exist", key)
            assertEquals("Key '$letter' long-press should be '$symbol'", symbol, key!!.longPressLabel)
        }
    }
}
