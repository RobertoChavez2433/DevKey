package dev.devkey.keyboard.keyboard.switcher

import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AutoModeSwitchStateMachine].
 *
 * Verifies the 5-state transition matrix: ALPHA, SYMBOL_BEGIN, SYMBOL,
 * MOMENTARY, and CHORDING.  Callbacks are tracked via counters so we never
 * inspect typed text or clipboard content.
 */
class AutoModeSwitchStateMachineTest {

    private var modeChangeCount = 0
    private var pointerCount = 1

    private lateinit var sm: AutoModeSwitchStateMachine

    @Before
    fun setUp() {
        modeChangeCount = 0
        pointerCount = 1
        sm = AutoModeSwitchStateMachine(
            changeKeyboardMode = { modeChangeCount++ },
            getPointerCount = { pointerCount }
        )
    }

    // --- Test 1: ALPHA -> toggleSymbols -> SYMBOL_BEGIN ---

    @Test
    fun `ALPHA to SYMBOL_BEGIN via onToggleSymbols`() {
        assertEquals(AutoModeSwitchStateMachine.STATE_ALPHA, sm.state)
        sm.onToggleSymbols(isSymbols = true, preferSymbols = false)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL_BEGIN, sm.state)
    }

    // --- Test 2: SYMBOL_BEGIN -> onKey -> SYMBOL ---

    @Test
    fun `SYMBOL_BEGIN to SYMBOL on printable key`() {
        sm.onToggleSymbols(isSymbols = true, preferSymbols = false)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL_BEGIN, sm.state)

        // A printable key (not space, not enter, and >= 0) advances to SYMBOL.
        sm.onKey('1'.code, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL, sm.state)
    }

    // --- Test 3: SYMBOL -> toggleSymbols -> ALPHA ---

    @Test
    fun `SYMBOL to ALPHA via onToggleSymbols`() {
        // Drive to SYMBOL state first.
        sm.onToggleSymbols(isSymbols = true, preferSymbols = false)
        sm.onKey('1'.code, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL, sm.state)

        // Toggle back: isSymbols=false means we're going to alpha.
        sm.onToggleSymbols(isSymbols = false, preferSymbols = false)
        assertEquals(AutoModeSwitchStateMachine.STATE_ALPHA, sm.state)
    }

    // --- Test 4: ALPHA -> setMomentary -> MOMENTARY ---

    @Test
    fun `ALPHA to MOMENTARY via setMomentary`() {
        assertEquals(AutoModeSwitchStateMachine.STATE_ALPHA, sm.state)
        sm.setMomentary()
        assertEquals(AutoModeSwitchStateMachine.STATE_MOMENTARY, sm.state)
    }

    // --- Test 5: MOMENTARY -> pointer count > 1 -> CHORDING ---

    @Test
    fun `MOMENTARY to CHORDING when pointer count greater than 1`() {
        sm.setMomentary()
        assertEquals(AutoModeSwitchStateMachine.STATE_MOMENTARY, sm.state)

        pointerCount = 2
        // Pressing a non-mode-change key with 2 pointers enters CHORDING.
        sm.onKey('a'.code, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_CHORDING, sm.state)
    }

    // --- Test 6: MOMENTARY -> onCancelInput -> ALPHA ---

    @Test
    fun `MOMENTARY to ALPHA via onCancelInput with single pointer`() {
        sm.setMomentary()
        assertEquals(AutoModeSwitchStateMachine.STATE_MOMENTARY, sm.state)

        pointerCount = 1
        sm.onCancelInput()
        // changeKeyboardMode should have been called once.
        assertEquals(1, modeChangeCount)
    }

    // --- Test 7: Full 5-state transition matrix ---

    @Test
    fun `full transition matrix covers all valid transitions`() {
        // ALPHA -> SYMBOL_BEGIN
        assertEquals(AutoModeSwitchStateMachine.STATE_ALPHA, sm.state)
        sm.onToggleSymbols(isSymbols = true, preferSymbols = false)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL_BEGIN, sm.state)

        // SYMBOL_BEGIN -> SYMBOL (printable key)
        sm.onKey('x'.code, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL, sm.state)

        // SYMBOL -> changeKeyboardMode on space (snap-back)
        val countBefore = modeChangeCount
        sm.onKey(KeyCodes.ASCII_SPACE, isSymbols = true)
        assertEquals(countBefore + 1, modeChangeCount)

        // Reset and test MOMENTARY path.
        sm.reset()
        assertEquals(AutoModeSwitchStateMachine.STATE_ALPHA, sm.state)

        // ALPHA -> MOMENTARY
        sm.setMomentary()
        assertEquals(AutoModeSwitchStateMachine.STATE_MOMENTARY, sm.state)

        // MOMENTARY -> SYMBOL_BEGIN via MODE_CHANGE key (isSymbols=true)
        sm.onKey(Keyboard.KEYCODE_MODE_CHANGE, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL_BEGIN, sm.state)

        // Back to MOMENTARY for chording test.
        sm.reset()
        sm.setMomentary()
        pointerCount = 2
        sm.onKey('z'.code, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_CHORDING, sm.state)

        // SYMBOL_BEGIN stays in SYMBOL_BEGIN for space/enter keys.
        sm.reset()
        sm.onToggleSymbols(isSymbols = true, preferSymbols = false)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL_BEGIN, sm.state)
        sm.onKey(KeyCodes.ASCII_SPACE, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL_BEGIN, sm.state)
        sm.onKey(KeyCodes.ENTER, isSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_SYMBOL_BEGIN, sm.state)

        // preferSymbols=true keeps ALPHA
        sm.reset()
        sm.onToggleSymbols(isSymbols = true, preferSymbols = true)
        assertEquals(AutoModeSwitchStateMachine.STATE_ALPHA, sm.state)
    }
}
