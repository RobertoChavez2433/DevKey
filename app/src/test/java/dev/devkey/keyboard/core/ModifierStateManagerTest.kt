package dev.devkey.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ModifierStateManager.
 *
 * Verifies the modifier key state machine transitions for Shift, Ctrl, and Alt.
 * All tests are pure JVM — no Android dependencies.
 */
class ModifierStateManagerTest {

    private lateinit var manager: ModifierStateManager

    @Before
    fun setUp() {
        manager = ModifierStateManager()
    }

    // --- Initial state ---

    @Test
    fun `initial shift state is OFF`() {
        assertEquals(ModifierKeyState.OFF, manager.shiftState.value)
    }

    @Test
    fun `initial ctrl state is OFF`() {
        assertEquals(ModifierKeyState.OFF, manager.ctrlState.value)
    }

    @Test
    fun `initial alt state is OFF`() {
        assertEquals(ModifierKeyState.OFF, manager.altState.value)
    }

    // --- OFF -> ONE_SHOT transition ---

    @Test
    fun `single tap transitions Shift from OFF to ONE_SHOT`() {
        manager.onModifierTap(ModifierType.SHIFT)
        assertEquals(ModifierKeyState.ONE_SHOT, manager.shiftState.value)
    }

    @Test
    fun `single tap transitions Ctrl from OFF to ONE_SHOT`() {
        manager.onModifierTap(ModifierType.CTRL)
        assertEquals(ModifierKeyState.ONE_SHOT, manager.ctrlState.value)
    }

    @Test
    fun `single tap transitions Alt from OFF to ONE_SHOT`() {
        manager.onModifierTap(ModifierType.ALT)
        assertEquals(ModifierKeyState.ONE_SHOT, manager.altState.value)
    }

    // --- ONE_SHOT -> LOCKED via double tap ---

    @Test
    fun `double tap within 400ms transitions Shift from ONE_SHOT to LOCKED`() {
        manager.onModifierTap(ModifierType.SHIFT) // first tap: OFF -> ONE_SHOT
        manager.onModifierTap(ModifierType.SHIFT) // second tap immediately: ONE_SHOT -> LOCKED
        assertEquals(ModifierKeyState.LOCKED, manager.shiftState.value)
    }

    @Test
    fun `double tap within 400ms transitions Ctrl from ONE_SHOT to LOCKED`() {
        manager.onModifierTap(ModifierType.CTRL)
        manager.onModifierTap(ModifierType.CTRL)
        assertEquals(ModifierKeyState.LOCKED, manager.ctrlState.value)
    }

    @Test
    fun `double tap within 400ms transitions Alt from ONE_SHOT to LOCKED`() {
        manager.onModifierTap(ModifierType.ALT)
        manager.onModifierTap(ModifierType.ALT)
        assertEquals(ModifierKeyState.LOCKED, manager.altState.value)
    }

    // --- LOCKED -> OFF transition ---

    @Test
    fun `tap on LOCKED Shift transitions to OFF`() {
        manager.onModifierTap(ModifierType.SHIFT)
        manager.onModifierTap(ModifierType.SHIFT) // -> LOCKED
        manager.onModifierTap(ModifierType.SHIFT) // -> OFF
        assertEquals(ModifierKeyState.OFF, manager.shiftState.value)
    }

    // --- HELD state ---

    @Test
    fun `pointer down transitions Shift to HELD`() {
        manager.onModifierDown(ModifierType.SHIFT, pointerId = 0)
        assertEquals(ModifierKeyState.HELD, manager.shiftState.value)
    }

    @Test
    fun `pointer down on Ctrl transitions to HELD`() {
        manager.onModifierDown(ModifierType.CTRL, pointerId = 1)
        assertEquals(ModifierKeyState.HELD, manager.ctrlState.value)
    }

    @Test
    fun `pointer up with matching id transitions HELD Shift to OFF`() {
        manager.onModifierDown(ModifierType.SHIFT, pointerId = 0)
        manager.onModifierUp(ModifierType.SHIFT, pointerId = 0)
        assertEquals(ModifierKeyState.OFF, manager.shiftState.value)
    }

    @Test
    fun `pointer up with non-matching id does not change HELD state`() {
        manager.onModifierDown(ModifierType.SHIFT, pointerId = 0)
        manager.onModifierUp(ModifierType.SHIFT, pointerId = 1) // wrong pointer id
        assertEquals(ModifierKeyState.HELD, manager.shiftState.value)
    }

    @Test
    fun `pointer down does not enter HELD when already LOCKED`() {
        manager.onModifierTap(ModifierType.SHIFT)
        manager.onModifierTap(ModifierType.SHIFT) // -> LOCKED
        manager.onModifierDown(ModifierType.SHIFT, pointerId = 0) // should not override LOCKED
        assertEquals(ModifierKeyState.LOCKED, manager.shiftState.value)
    }

    // --- consumeOneShot ---

    @Test
    fun `consumeOneShot resets ONE_SHOT Shift to OFF`() {
        manager.onModifierTap(ModifierType.SHIFT) // -> ONE_SHOT
        manager.consumeOneShot()
        assertEquals(ModifierKeyState.OFF, manager.shiftState.value)
    }

    @Test
    fun `consumeOneShot resets ONE_SHOT Ctrl to OFF`() {
        manager.onModifierTap(ModifierType.CTRL)
        manager.consumeOneShot()
        assertEquals(ModifierKeyState.OFF, manager.ctrlState.value)
    }

    @Test
    fun `consumeOneShot resets ONE_SHOT Alt to OFF`() {
        manager.onModifierTap(ModifierType.ALT)
        manager.consumeOneShot()
        assertEquals(ModifierKeyState.OFF, manager.altState.value)
    }

    @Test
    fun `consumeOneShot does not affect LOCKED modifier`() {
        manager.onModifierTap(ModifierType.SHIFT)
        manager.onModifierTap(ModifierType.SHIFT) // -> LOCKED
        manager.consumeOneShot()
        assertEquals(ModifierKeyState.LOCKED, manager.shiftState.value)
    }

    @Test
    fun `consumeOneShot resets all ONE_SHOT modifiers simultaneously`() {
        manager.onModifierTap(ModifierType.SHIFT)
        manager.onModifierTap(ModifierType.CTRL)
        manager.onModifierTap(ModifierType.ALT)
        manager.consumeOneShot()
        assertEquals(ModifierKeyState.OFF, manager.shiftState.value)
        assertEquals(ModifierKeyState.OFF, manager.ctrlState.value)
        assertEquals(ModifierKeyState.OFF, manager.altState.value)
    }

    // --- isActive helpers ---

    @Test
    fun `isShiftActive returns true when Shift is ONE_SHOT`() {
        manager.onModifierTap(ModifierType.SHIFT)
        assertTrue(manager.isShiftActive())
    }

    @Test
    fun `isCtrlActive returns false when Ctrl is OFF`() {
        assertFalse(manager.isCtrlActive())
    }

    @Test
    fun `isAltActive returns true when Alt is LOCKED`() {
        manager.onModifierTap(ModifierType.ALT)
        manager.onModifierTap(ModifierType.ALT)
        assertTrue(manager.isAltActive())
    }

    // --- Modifier independence ---

    @Test
    fun `Shift and Ctrl are independent — activating one does not affect the other`() {
        manager.onModifierTap(ModifierType.SHIFT)
        assertEquals(ModifierKeyState.ONE_SHOT, manager.shiftState.value)
        assertEquals(ModifierKeyState.OFF, manager.ctrlState.value)
    }

    // --- Double tap after window expires ---

    @Test
    fun `double tap after window expires stays ONE_SHOT`() {
        manager.onModifierTap(ModifierType.SHIFT) // -> ONE_SHOT
        Thread.sleep(450) // exceed 400ms window
        manager.onModifierTap(ModifierType.SHIFT) // window expired -> stays ONE_SHOT
        assertEquals(ModifierKeyState.ONE_SHOT, manager.shiftState.value)
    }

    // --- Tap when HELD ---

    @Test
    fun `tap when HELD is ignored`() {
        manager.onModifierDown(ModifierType.SHIFT, pointerId = 0) // -> HELD
        manager.onModifierTap(ModifierType.SHIFT) // should be ignored
        assertEquals(ModifierKeyState.HELD, manager.shiftState.value)
    }

    // --- LOCKED to OFF for Ctrl and Alt ---

    @Test
    fun `LOCKED to OFF for Ctrl`() {
        manager.onModifierTap(ModifierType.CTRL) // -> ONE_SHOT
        manager.onModifierTap(ModifierType.CTRL) // -> LOCKED
        manager.onModifierTap(ModifierType.CTRL) // -> OFF
        assertEquals(ModifierKeyState.OFF, manager.ctrlState.value)
    }

    @Test
    fun `LOCKED to OFF for Alt`() {
        manager.onModifierTap(ModifierType.ALT) // -> ONE_SHOT
        manager.onModifierTap(ModifierType.ALT) // -> LOCKED
        manager.onModifierTap(ModifierType.ALT) // -> OFF
        assertEquals(ModifierKeyState.OFF, manager.altState.value)
    }

    // --- isShiftActive for HELD and OFF ---

    @Test
    fun `isShiftActive true for HELD`() {
        manager.onModifierDown(ModifierType.SHIFT, pointerId = 0)
        assertTrue(manager.isShiftActive())
    }

    @Test
    fun `isShiftActive false for OFF`() {
        assertFalse(manager.isShiftActive())
    }

    // --- isAltActive for HELD ---

    @Test
    fun `isAltActive true for HELD`() {
        manager.onModifierDown(ModifierType.ALT, pointerId = 0)
        assertTrue(manager.isAltActive())
    }

    // --- onModifierDown for Alt ---

    @Test
    fun `onModifierDown for Alt enters HELD`() {
        manager.onModifierDown(ModifierType.ALT, pointerId = 1)
        assertEquals(ModifierKeyState.HELD, manager.altState.value)
    }

    // --- onModifierUp for Ctrl ---

    @Test
    fun `onModifierUp for Ctrl exits HELD`() {
        manager.onModifierDown(ModifierType.CTRL, pointerId = 2)
        manager.onModifierUp(ModifierType.CTRL, pointerId = 2)
        assertEquals(ModifierKeyState.OFF, manager.ctrlState.value)
    }

    @Test
    fun `onModifierUp when not HELD is ignored`() {
        manager.onModifierTap(ModifierType.ALT) // -> ONE_SHOT
        manager.onModifierUp(ModifierType.ALT, pointerId = 0) // not HELD, should be ignored
        assertEquals(ModifierKeyState.ONE_SHOT, manager.altState.value)
    }
}
