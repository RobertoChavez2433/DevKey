package dev.devkey.keyboard.core

import android.os.SystemClock
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the state machine for modifier keys (Shift, Ctrl, Alt, Meta).
 *
 * State transitions:
 * - Tap: OFF -> ONE_SHOT -> LOCKED (double-tap within 400ms) -> OFF
 * - Hold (pointer down): -> HELD
 * - Hold (pointer up): HELD -> OFF
 * - Other key pressed while HELD: HELD -> CHORDING
 * - After non-modifier key: ONE_SHOT -> OFF (consumeOneShot)
 * - CHORDING -> OFF on release (handled by caller)
 *
 * Enums live in [ModifierTypes.kt]; per-type mutable state in [ModifierTransitionTable].
 */
class ModifierStateManager(
    private val timeProvider: () -> Long = { SystemClock.uptimeMillis() }
) {

    companion object {
        private const val DOUBLE_TAP_WINDOW_MS = 400L
    }

    private val table = ModifierTransitionTable()

    val shiftState: StateFlow<ModifierKeyState> = table.shiftFlow.asStateFlow()
    val ctrlState:  StateFlow<ModifierKeyState> = table.ctrlFlow.asStateFlow()
    val altState:   StateFlow<ModifierKeyState> = table.altFlow.asStateFlow()
    val metaState:  StateFlow<ModifierKeyState> = table.metaFlow.asStateFlow()

    fun isShiftActive(): Boolean = table.shiftFlow.value != ModifierKeyState.OFF
    fun isCtrlActive():  Boolean = table.ctrlFlow.value  != ModifierKeyState.OFF
    fun isAltActive():   Boolean = table.altFlow.value   != ModifierKeyState.OFF
    fun isMetaActive():  Boolean = table.metaFlow.value  != ModifierKeyState.OFF

    fun isActive(type: ModifierType): Boolean =
        table.getFlow(type).value != ModifierKeyState.OFF

    /** Returns true if the given modifier is in the CHORDING state. */
    fun isChording(type: ModifierType): Boolean =
        table.getFlow(type).value == ModifierKeyState.CHORDING

    /**
     * Handle a modifier tap. Cycles: OFF -> ONE_SHOT, ONE_SHOT -> LOCKED (double-tap), LOCKED -> OFF.
     *
     * In the real event flow (KeyView) the sequence is onModifierDown -> onModifierUp ->
     * onModifierTap. The down/up cycle converts ONE_SHOT -> HELD -> OFF, so tap sees OFF.
     * [stateBeforeDown] is used to recover the pre-down state for double-tap detection.
     */
    @MainThread
    fun onModifierTap(type: ModifierType) {
        val flow = table.getFlow(type)
        val now = timeProvider()
        val lastTap = table.getLastTapTime(type)
        val preDownState = table.getStateBeforeDown(type)
        val fromState = flow.value

        when (fromState) {
            ModifierKeyState.OFF -> {
                // Check if we were in ONE_SHOT before the down/up cycle reset us to OFF
                if (preDownState == ModifierKeyState.ONE_SHOT && now - lastTap <= DOUBLE_TAP_WINDOW_MS) {
                    flow.value = ModifierKeyState.LOCKED
                } else {
                    flow.value = ModifierKeyState.ONE_SHOT
                }
            }
            ModifierKeyState.ONE_SHOT -> {
                flow.value = if (now - lastTap <= DOUBLE_TAP_WINDOW_MS) {
                    ModifierKeyState.LOCKED
                } else {
                    ModifierKeyState.ONE_SHOT // window expired — restart
                }
            }
            ModifierKeyState.LOCKED -> flow.value = ModifierKeyState.OFF
            ModifierKeyState.HELD, ModifierKeyState.CHORDING -> { /* ignore */ }
        }

        if (flow.value != fromState) table.logTransition(type, "tap", fromState, flow.value)
        table.setLastTapTime(type, now)
    }

    /** Handle modifier pointer down. Enters HELD state (saves pre-down state for double-tap). */
    @MainThread
    fun onModifierDown(type: ModifierType, pointerId: Int) {
        val flow = table.getFlow(type)
        val fromState = flow.value
        table.setStateBeforeDown(type, fromState)
        if (fromState != ModifierKeyState.LOCKED) {
            flow.value = ModifierKeyState.HELD
            table.logTransition(type, "down", fromState, ModifierKeyState.HELD)
        }
        table.setHeldPointerId(type, pointerId)
    }

    /** Handle modifier pointer up. Exits HELD/CHORDING (only if pointer ID matches). */
    @MainThread
    fun onModifierUp(type: ModifierType, pointerId: Int) {
        if (table.getHeldPointerId(type) == pointerId) {
            val flow = table.getFlow(type)
            val fromState = flow.value
            if (fromState == ModifierKeyState.HELD || fromState == ModifierKeyState.CHORDING) {
                flow.value = ModifierKeyState.OFF
                table.logTransition(type, "up", fromState, ModifierKeyState.OFF)
            }
            table.setHeldPointerId(type, null)
        }
    }

    /**
     * Called when a non-modifier key is pressed.
     * Transitions any HELD modifier to CHORDING; ONE_SHOT modifiers are consumed in [consumeOneShot].
     */
    @MainThread
    fun onOtherKeyPressed() {
        for ((idx, flow) in table.allFlows.withIndex()) {
            if (flow.value == ModifierKeyState.HELD) {
                flow.value = ModifierKeyState.CHORDING
                table.logTransition(
                    ModifierType.values()[idx], "other_key",
                    ModifierKeyState.HELD, ModifierKeyState.CHORDING
                )
            }
        }
    }

    /** Called after a non-modifier key press. Resets any ONE_SHOT modifier to OFF. */
    @MainThread
    fun consumeOneShot() {
        for (type in ModifierType.values()) {
            val flow = table.getFlow(type)
            if (flow.value == ModifierKeyState.ONE_SHOT) {
                flow.value = ModifierKeyState.OFF
                table.logTransition(type, "consume", ModifierKeyState.ONE_SHOT, ModifierKeyState.OFF)
            }
        }
    }

    /**
     * Reset all modifier state to OFF. Used by the debug-only RESET_KEYBOARD_MODE receiver
     * so the e2e harness can recover from a prior test that left Shift LOCKED or HELD.
     */
    @MainThread
    fun resetAll() {
        table.resetAll()
    }

    /** Computes android.view.KeyEvent meta state flags from the current modifier state. */
    fun getKeyEventMetaState(shifted: Boolean): Int {
        var meta = 0
        if (shifted)        meta = meta or (android.view.KeyEvent.META_SHIFT_ON or android.view.KeyEvent.META_SHIFT_LEFT_ON)
        if (isCtrlActive()) meta = meta or (android.view.KeyEvent.META_CTRL_ON  or android.view.KeyEvent.META_CTRL_LEFT_ON)
        if (isAltActive())  meta = meta or (android.view.KeyEvent.META_ALT_ON   or android.view.KeyEvent.META_ALT_LEFT_ON)
        if (isMetaActive()) meta = meta or (android.view.KeyEvent.META_META_ON  or android.view.KeyEvent.META_META_LEFT_ON)
        return meta
    }
}
