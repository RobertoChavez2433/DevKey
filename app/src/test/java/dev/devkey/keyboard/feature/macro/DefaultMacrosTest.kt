package dev.devkey.keyboard.feature.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DefaultMacros.
 *
 * Tests count, names, valid keySequence JSON, usageCount=0, createdAt>0,
 * and specific key sequences for Copy and Paste.
 *
 * Note: MacroEntity uses Room annotations but these do NOT require an Android context
 * for construction — they are plain data classes with annotations that are
 * processed at build time only.
 */
class DefaultMacrosTest {

    private val defaults = DefaultMacros.getDefaults()

    // --- Count ---

    @Test
    fun `getDefaults returns exactly 7 macros`() {
        assertEquals(7, defaults.size)
    }

    // --- Names ---

    @Test
    fun `default macros include expected names`() {
        val names = defaults.map { it.name }.toSet()
        assertTrue(names.contains("Copy"))
        assertTrue(names.contains("Paste"))
        assertTrue(names.contains("Cut"))
        assertTrue(names.contains("Undo"))
        assertTrue(names.contains("Select All"))
        assertTrue(names.contains("Save"))
        assertTrue(names.contains("Find"))
    }

    // --- usageCount ---

    @Test
    fun `all default macros have usageCount of 0`() {
        assertTrue(defaults.all { it.usageCount == 0 })
    }

    // --- createdAt ---

    @Test
    fun `all default macros have createdAt greater than 0`() {
        assertTrue(defaults.all { it.createdAt > 0L })
    }

    // --- keySequence JSON validity ---

    @Test
    fun `all default macros have non-blank keySequence`() {
        assertTrue(defaults.all { it.keySequence.isNotBlank() })
    }

    @Test
    fun `Copy macro has correct keySequence JSON`() {
        val copy = defaults.find { it.name == "Copy" }
        assertNotNull(copy)
        // Should be a Ctrl+C sequence: key "c", keyCode 99, modifiers ["ctrl"]
        val steps = MacroSerializer.deserialize(copy!!.keySequence)
        assertEquals(1, steps.size)
        assertEquals("c", steps[0].key)
        assertEquals(99, steps[0].keyCode)
        assertTrue(steps[0].modifiers.contains("ctrl"))
    }

    @Test
    fun `Paste macro has correct keySequence JSON`() {
        val paste = defaults.find { it.name == "Paste" }
        assertNotNull(paste)
        val steps = MacroSerializer.deserialize(paste!!.keySequence)
        assertEquals(1, steps.size)
        assertEquals("v", steps[0].key)
        assertEquals(118, steps[0].keyCode)
        assertTrue(steps[0].modifiers.contains("ctrl"))
    }

    // --- Specific macro key sequences ---

    @Test
    fun `Cut macro has ctrl+x`() {
        val macro = defaults.find { it.name == "Cut" }
        assertNotNull(macro)
        val steps = MacroSerializer.deserialize(macro!!.keySequence)
        assertEquals(1, steps.size)
        assertEquals("x", steps[0].key)
        assertEquals(120, steps[0].keyCode)
        assertTrue(steps[0].modifiers.contains("ctrl"))
    }

    @Test
    fun `Undo macro has ctrl+z`() {
        val macro = defaults.find { it.name == "Undo" }
        assertNotNull(macro)
        val steps = MacroSerializer.deserialize(macro!!.keySequence)
        assertEquals(1, steps.size)
        assertEquals("z", steps[0].key)
        assertEquals(122, steps[0].keyCode)
        assertTrue(steps[0].modifiers.contains("ctrl"))
    }

    @Test
    fun `Select All macro has ctrl+a`() {
        val macro = defaults.find { it.name == "Select All" }
        assertNotNull(macro)
        val steps = MacroSerializer.deserialize(macro!!.keySequence)
        assertEquals(1, steps.size)
        assertEquals("a", steps[0].key)
        assertEquals(97, steps[0].keyCode)
        assertTrue(steps[0].modifiers.contains("ctrl"))
    }

    @Test
    fun `Save macro has ctrl+s`() {
        val macro = defaults.find { it.name == "Save" }
        assertNotNull(macro)
        val steps = MacroSerializer.deserialize(macro!!.keySequence)
        assertEquals(1, steps.size)
        assertEquals("s", steps[0].key)
        assertEquals(115, steps[0].keyCode)
        assertTrue(steps[0].modifiers.contains("ctrl"))
    }

    @Test
    fun `Find macro has ctrl+f`() {
        val macro = defaults.find { it.name == "Find" }
        assertNotNull(macro)
        val steps = MacroSerializer.deserialize(macro!!.keySequence)
        assertEquals(1, steps.size)
        assertEquals("f", steps[0].key)
        assertEquals(102, steps[0].keyCode)
        assertTrue(steps[0].modifiers.contains("ctrl"))
    }
}
