package dev.devkey.keyboard.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * States a modifier key can be in.
 */
enum class ModifierKeyState {
    OFF,
    ONE_SHOT,
    LOCKED,
    HELD
}

/**
 * Types of modifier keys tracked by the state machine.
 */
enum class ModifierType {
    SHIFT,
    CTRL,
    ALT
}

/**
 * Manages the state machine for modifier keys (Shift, Ctrl, Alt).
 *
 * State transitions:
 * - Tap: OFF -> ONE_SHOT -> LOCKED (double-tap within 400ms) -> OFF
 * - Hold (pointer down): -> HELD
 * - Hold (pointer up): HELD -> OFF
 * - After non-modifier key: ONE_SHOT -> OFF (consumeOneShot)
 */
class ModifierStateManager {

    companion object {
        private const val DOUBLE_TAP_WINDOW_MS = 400L
    }

    private val _shiftState = MutableStateFlow(ModifierKeyState.OFF)
    private val _ctrlState = MutableStateFlow(ModifierKeyState.OFF)
    private val _altState = MutableStateFlow(ModifierKeyState.OFF)

    val shiftState: StateFlow<ModifierKeyState> = _shiftState.asStateFlow()
    val ctrlState: StateFlow<ModifierKeyState> = _ctrlState.asStateFlow()
    val altState: StateFlow<ModifierKeyState> = _altState.asStateFlow()

    // Pointer tracking for HELD state
    private var shiftHeldPointerId: Int? = null
    private var ctrlHeldPointerId: Int? = null
    private var altHeldPointerId: Int? = null

    // Last tap time for double-tap detection
    private var shiftLastTapTime: Long = 0L
    private var ctrlLastTapTime: Long = 0L
    private var altLastTapTime: Long = 0L

    fun isShiftActive(): Boolean = _shiftState.value != ModifierKeyState.OFF
    fun isCtrlActive(): Boolean = _ctrlState.value != ModifierKeyState.OFF
    fun isAltActive(): Boolean = _altState.value != ModifierKeyState.OFF

    /**
     * Handle a modifier tap. Cycles: OFF -> ONE_SHOT, ONE_SHOT -> LOCKED (if double-tap), LOCKED -> OFF.
     */
    fun onModifierTap(type: ModifierType) {
        val flow = getFlow(type)
        val now = System.currentTimeMillis()
        val lastTap = getLastTapTime(type)

        when (flow.value) {
            ModifierKeyState.OFF -> {
                flow.value = ModifierKeyState.ONE_SHOT
            }
            ModifierKeyState.ONE_SHOT -> {
                if (now - lastTap <= DOUBLE_TAP_WINDOW_MS) {
                    flow.value = ModifierKeyState.LOCKED
                } else {
                    // Single tap again after window expired — reset to ONE_SHOT
                    flow.value = ModifierKeyState.ONE_SHOT
                }
            }
            ModifierKeyState.LOCKED -> {
                flow.value = ModifierKeyState.OFF
            }
            ModifierKeyState.HELD -> {
                // Ignore taps while held
            }
        }

        setLastTapTime(type, now)
    }

    /**
     * Handle modifier pointer down. Enters HELD state.
     */
    fun onModifierDown(type: ModifierType, pointerId: Int) {
        val flow = getFlow(type)
        // Only enter HELD if not already locked
        if (flow.value != ModifierKeyState.LOCKED) {
            flow.value = ModifierKeyState.HELD
        }
        setHeldPointerId(type, pointerId)
    }

    /**
     * Handle modifier pointer up. Exits HELD state (only if matching pointer).
     */
    fun onModifierUp(type: ModifierType, pointerId: Int) {
        val heldId = getHeldPointerId(type)
        if (heldId == pointerId) {
            val flow = getFlow(type)
            if (flow.value == ModifierKeyState.HELD) {
                flow.value = ModifierKeyState.OFF
            }
            setHeldPointerId(type, null)
        }
    }

    /**
     * Called after a non-modifier key press.
     * If any modifier is ONE_SHOT, reset it to OFF.
     */
    fun consumeOneShot() {
        if (_shiftState.value == ModifierKeyState.ONE_SHOT) {
            _shiftState.value = ModifierKeyState.OFF
        }
        if (_ctrlState.value == ModifierKeyState.ONE_SHOT) {
            _ctrlState.value = ModifierKeyState.OFF
        }
        if (_altState.value == ModifierKeyState.ONE_SHOT) {
            _altState.value = ModifierKeyState.OFF
        }
    }

    private fun getFlow(type: ModifierType): MutableStateFlow<ModifierKeyState> = when (type) {
        ModifierType.SHIFT -> _shiftState
        ModifierType.CTRL -> _ctrlState
        ModifierType.ALT -> _altState
    }

    private fun getLastTapTime(type: ModifierType): Long = when (type) {
        ModifierType.SHIFT -> shiftLastTapTime
        ModifierType.CTRL -> ctrlLastTapTime
        ModifierType.ALT -> altLastTapTime
    }

    private fun setLastTapTime(type: ModifierType, time: Long) {
        when (type) {
            ModifierType.SHIFT -> shiftLastTapTime = time
            ModifierType.CTRL -> ctrlLastTapTime = time
            ModifierType.ALT -> altLastTapTime = time
        }
    }

    private fun getHeldPointerId(type: ModifierType): Int? = when (type) {
        ModifierType.SHIFT -> shiftHeldPointerId
        ModifierType.CTRL -> ctrlHeldPointerId
        ModifierType.ALT -> altHeldPointerId
    }

    private fun setHeldPointerId(type: ModifierType, pointerId: Int?) {
        when (type) {
            ModifierType.SHIFT -> shiftHeldPointerId = pointerId
            ModifierType.CTRL -> ctrlHeldPointerId = pointerId
            ModifierType.ALT -> altHeldPointerId = pointerId
        }
    }
}
