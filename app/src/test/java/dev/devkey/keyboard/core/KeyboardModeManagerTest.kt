package dev.devkey.keyboard.core

import dev.devkey.keyboard.ui.keyboard.KeyboardMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [KeyboardModeManager].
 *
 * Verifies toggle, set, and flow emission behavior for keyboard mode state.
 */
class KeyboardModeManagerTest {

    private lateinit var manager: KeyboardModeManager

    @Before
    fun setUp() {
        manager = KeyboardModeManager()
    }

    @Test
    fun `initial mode is Normal`() {
        assertEquals(KeyboardMode.Normal, manager.currentMode)
    }

    @Test
    fun `toggleMode Normal to Symbols`() {
        manager.toggleMode(KeyboardMode.Symbols)
        assertEquals(KeyboardMode.Symbols, manager.currentMode)
    }

    @Test
    fun `toggleMode Symbols back to Normal`() {
        manager.toggleMode(KeyboardMode.Symbols)
        manager.toggleMode(KeyboardMode.Symbols)
        assertEquals(KeyboardMode.Normal, manager.currentMode)
    }

    @Test
    fun `toggleMode round-trip Normal-Symbols-Normal`() {
        assertEquals(KeyboardMode.Normal, manager.currentMode)
        manager.toggleMode(KeyboardMode.Symbols)
        assertEquals(KeyboardMode.Symbols, manager.currentMode)
        manager.toggleMode(KeyboardMode.Symbols)
        assertEquals(KeyboardMode.Normal, manager.currentMode)
    }

    @Test
    fun `setMode direct set`() {
        manager.setMode(KeyboardMode.Clipboard)
        assertEquals(KeyboardMode.Clipboard, manager.currentMode)
    }

    @Test
    fun `setMode overrides current mode`() {
        manager.setMode(KeyboardMode.Symbols)
        manager.setMode(KeyboardMode.Voice)
        assertEquals(KeyboardMode.Voice, manager.currentMode)
    }

    @Test
    fun `toggleMode same target toggles back to Normal`() {
        manager.setMode(KeyboardMode.MacroChips)
        manager.toggleMode(KeyboardMode.MacroChips)
        assertEquals(KeyboardMode.Normal, manager.currentMode)
    }

    @Test
    fun `toggleMode different target switches to new target`() {
        manager.setMode(KeyboardMode.Symbols)
        manager.toggleMode(KeyboardMode.Clipboard)
        assertEquals(KeyboardMode.Clipboard, manager.currentMode)
    }

    @Test
    fun `flow emits current value`() = runTest {
        val initialValue = manager.mode.first()
        assertEquals(KeyboardMode.Normal, initialValue)
    }

    @Test
    fun `flow emits after toggleMode`() = runTest {
        manager.toggleMode(KeyboardMode.Symbols)
        val value = manager.mode.first()
        assertEquals(KeyboardMode.Symbols, value)
    }

    @Test
    fun `flow emits after setMode`() = runTest {
        manager.setMode(KeyboardMode.MacroGrid)
        val value = manager.mode.first()
        assertEquals(KeyboardMode.MacroGrid, value)
    }
}
