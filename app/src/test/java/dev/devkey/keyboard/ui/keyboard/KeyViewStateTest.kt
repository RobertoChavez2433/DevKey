package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.keyboard.model.Keyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyViewStateTest {

    @Test
    fun `shift modifier visual state follows shift state`() {
        val key = KeyData("Shift", Keyboard.KEYCODE_SHIFT, type = KeyType.MODIFIER)

        val active = resolveModifierVisualState(
            key,
            shiftState = ModifierKeyState.ONE_SHOT,
            ctrlState = ModifierKeyState.OFF,
            altState = ModifierKeyState.OFF
        )
        val locked = resolveModifierVisualState(
            key,
            shiftState = ModifierKeyState.LOCKED,
            ctrlState = ModifierKeyState.OFF,
            altState = ModifierKeyState.OFF
        )

        assertTrue(active.isActive)
        assertFalse(active.isLocked)
        assertTrue(locked.isActive)
        assertTrue(locked.isLocked)
    }

    @Test
    fun `utility ctrl is treated as modifier-like and uses ctrl state`() {
        val key = KeyData("Ctrl", KeyCodes.CTRL_LEFT, type = KeyType.UTILITY)

        val visual = resolveModifierVisualState(
            key,
            shiftState = ModifierKeyState.OFF,
            ctrlState = ModifierKeyState.LOCKED,
            altState = ModifierKeyState.OFF
        )
        val interaction = resolveKeyInteractionState(key, ctrlHeld = false)

        assertTrue(visual.isActive)
        assertTrue(visual.isLocked)
        assertTrue(interaction.isModifierKey)
    }

    @Test
    fun `non modifier key is not active or locked`() {
        val visual = resolveModifierVisualState(
            KeyData("a", 'a'.code),
            shiftState = ModifierKeyState.LOCKED,
            ctrlState = ModifierKeyState.LOCKED,
            altState = ModifierKeyState.LOCKED
        )

        assertFalse(visual.isActive)
        assertFalse(visual.isLocked)
    }

    @Test
    fun `multi popup requires at least two long press codes and non modifier key`() {
        val letter = KeyData("a", 'a'.code, longPressCodes = listOf('á'.code, 'à'.code))
        val singleCandidate = KeyData("e", 'e'.code, longPressCodes = listOf('é'.code))
        val modifier = KeyData(
            "Shift",
            Keyboard.KEYCODE_SHIFT,
            longPressCodes = listOf('x'.code, 'y'.code),
            type = KeyType.MODIFIER
        )

        assertTrue(resolveKeyInteractionState(letter, ctrlHeld = false).hasMultiPopup)
        assertFalse(resolveKeyInteractionState(singleCandidate, ctrlHeld = false).hasMultiPopup)
        assertFalse(resolveKeyInteractionState(modifier, ctrlHeld = false).hasMultiPopup)
    }

    @Test
    fun `ctrl shortcut resolves only for held letter keys`() {
        assertNotNull(resolveCtrlShortcut(ctrlHeld = true, KeyData("c", 'c'.code)))
        assertNull(resolveCtrlShortcut(ctrlHeld = false, KeyData("c", 'c'.code)))
        assertNull(resolveCtrlShortcut(ctrlHeld = true, KeyData("1", '1'.code, type = KeyType.NUMBER)))
    }

    @Test
    fun `preview label uses long press label only after long press fires`() {
        val key = KeyData(
            "e",
            'e'.code,
            longPressLabel = "é",
            longPressCode = 'é'.code
        )

        assertEquals("E", resolvePreviewLabel(key, displayLabel = "E", longPressFired = false))
        assertEquals("é", resolvePreviewLabel(key, displayLabel = "E", longPressFired = true))
    }
}
