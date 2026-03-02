package dev.devkey.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CtrlShortcutMap.
 *
 * Verifies shortcut presence, count, specific mappings (Ctrl+C=Copy, Ctrl+V=Paste, etc.),
 * null for unmapped keys, case sensitivity, and highlight colors.
 */
class CtrlShortcutMapTest {

    // --- Shortcut count ---

    @Test
    fun `shortcuts map contains 18 entries`() {
        assertEquals(18, CtrlShortcutMap.shortcuts.size)
    }

    // --- Specific shortcut labels ---

    @Test
    fun `Ctrl+C maps to Copy with RED highlight`() {
        val info = CtrlShortcutMap.getShortcut("c")
        assertNotNull(info)
        assertEquals("Copy", info!!.label)
        assertEquals(HighlightType.RED, info.highlight)
    }

    @Test
    fun `Ctrl+V maps to Paste with RED highlight`() {
        val info = CtrlShortcutMap.getShortcut("v")
        assertNotNull(info)
        assertEquals("Paste", info!!.label)
        assertEquals(HighlightType.RED, info.highlight)
    }

    @Test
    fun `Ctrl+A maps to Sel All with NORMAL highlight`() {
        val info = CtrlShortcutMap.getShortcut("a")
        assertNotNull(info)
        assertEquals("Sel All", info!!.label)
        assertEquals(HighlightType.NORMAL, info.highlight)
    }

    @Test
    fun `Ctrl+X maps to Cut with NORMAL highlight`() {
        val info = CtrlShortcutMap.getShortcut("x")
        assertNotNull(info)
        assertEquals("Cut", info!!.label)
        assertEquals(HighlightType.NORMAL, info.highlight)
    }

    @Test
    fun `Ctrl+Z maps to Undo`() {
        val info = CtrlShortcutMap.getShortcut("z")
        assertNotNull(info)
        assertEquals("Undo", info!!.label)
    }

    @Test
    fun `Ctrl+S maps to Save`() {
        val info = CtrlShortcutMap.getShortcut("s")
        assertNotNull(info)
        assertEquals("Save", info!!.label)
    }

    @Test
    fun `Ctrl+F maps to Find`() {
        val info = CtrlShortcutMap.getShortcut("f")
        assertNotNull(info)
        assertEquals("Find", info!!.label)
    }

    // --- Null for unmapped keys ---

    @Test
    fun `unmapped key returns null`() {
        assertNull(CtrlShortcutMap.getShortcut("e"))
    }

    @Test
    fun `unmapped key m returns null`() {
        assertNull(CtrlShortcutMap.getShortcut("m"))
    }

    // --- Case sensitivity ---

    @Test
    fun `getShortcut is case-insensitive for uppercase C`() {
        val infoLower = CtrlShortcutMap.getShortcut("c")
        val infoUpper = CtrlShortcutMap.getShortcut("C")
        assertNotNull(infoLower)
        assertNotNull(infoUpper)
        assertEquals(infoLower!!.label, infoUpper!!.label)
    }

    @Test
    fun `getShortcut is case-insensitive for uppercase V`() {
        val infoLower = CtrlShortcutMap.getShortcut("v")
        val infoUpper = CtrlShortcutMap.getShortcut("V")
        assertNotNull(infoLower)
        assertNotNull(infoUpper)
        assertEquals(infoLower!!.label, infoUpper!!.label)
    }

    // --- Highlight types ---

    @Test
    fun `only Copy and Paste have RED highlight`() {
        val redKeys = CtrlShortcutMap.shortcuts.entries
            .filter { it.value.highlight == HighlightType.RED }
            .map { it.key }
            .toSet()
        assertEquals(setOf("c", "v"), redKeys)
    }

    @Test
    fun `default highlight for ShortcutInfo is NORMAL`() {
        val info = ShortcutInfo("Test")
        assertEquals(HighlightType.NORMAL, info.highlight)
    }

    // --- Additional coverage ---

    @Test
    fun `all 18 shortcuts have non-blank labels`() {
        for ((key, info) in CtrlShortcutMap.shortcuts) {
            assertTrue("Shortcut for '$key' should have non-blank label", info.label.isNotBlank())
        }
    }

    @Test
    fun `Ctrl+N is New`() {
        val info = CtrlShortcutMap.getShortcut("n")
        assertNotNull(info)
        assertEquals("New", info!!.label)
    }

    @Test
    fun `Ctrl+D is Dup`() {
        val info = CtrlShortcutMap.getShortcut("d")
        assertNotNull(info)
        assertEquals("Dup", info!!.label)
    }

    @Test
    fun `empty string returns null`() {
        assertNull(CtrlShortcutMap.getShortcut(""))
    }
}
