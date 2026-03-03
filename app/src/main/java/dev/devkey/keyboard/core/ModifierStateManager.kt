package dev.devkey.keyboard.core

import android.os.SystemClock
import androidx.annotation.MainThread
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
    HELD,
    /** Modifier is held while another key is being pressed (chording). */
    CHORDING
}

/**
 * Types of modifier keys tracked by the state machine.
 */
enum class ModifierType {
    SHIFT,
    CTRL,
    ALT,
    META
}

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
 */
class ModifierStateManager(
    private val timeProvider: () -> Long = { SystemClock.uptimeMillis() }
) {

    companion object {
        private const val DOUBLE_TAP_WINDOW_MS = 400L
    }

    private val _shiftState = MutableStateFlow(ModifierKeyState.OFF)
    private val _ctrlState = MutableStateFlow(ModifierKeyState.OFF)
    private val _altState = MutableStateFlow(ModifierKeyState.OFF)
    private val _metaState = MutableStateFlow(ModifierKeyState.OFF)

    val shiftState: StateFlow<ModifierKeyState> = _shiftState.asStateFlow()
    val ctrlState: StateFlow<ModifierKeyState> = _ctrlState.asStateFlow()
    val altState: StateFlow<ModifierKeyState> = _altState.asStateFlow()
    val metaState: StateFlow<ModifierKeyState> = _metaState.asStateFlow()

    private val allModifierFlows = listOf(_shiftState, _ctrlState, _altState, _metaState)

    // Pointer tracking for HELD state
    private var shiftHeldPointerId: Int? = null
    private var ctrlHeldPointerId: Int? = null
    private var altHeldPointerId: Int? = null
    private var metaHeldPointerId: Int? = null

    // Last tap time for double-tap detection
    private var shiftLastTapTime: Long = 0L
    private var ctrlLastTapTime: Long = 0L
    private var altLastTapTime: Long = 0L
    private var metaLastTapTime: Long = 0L

    fun isShiftActive(): Boolean = _shiftState.value != ModifierKeyState.OFF
    fun isCtrlActive(): Boolean = _ctrlState.value != ModifierKeyState.OFF
    fun isAltActive(): Boolean = _altState.value != ModifierKeyState.OFF
    fun isMetaActive(): Boolean = _metaState.value != ModifierKeyState.OFF

    fun isActive(type: ModifierType): Boolean = getFlow(type).value != ModifierKeyState.OFF

    /**
     * Returns true if the given modifier is in the CHORDING state
     * (being held while another key is pressed).
     */
    fun isChording(type: ModifierType): Boolean = getFlow(type).value == ModifierKeyState.CHORDING

    /**
     * Handle a modifier tap. Cycles: OFF -> ONE_SHOT, ONE_SHOT -> LOCKED (if double-tap), LOCKED -> OFF.
     */
    @MainThread
    fun onModifierTap(type: ModifierType) {
        val flow = getFlow(type)
        val now = timeProvider()
        val lastTap = getLastTapTime(type)

        when (flow.value) {
            ModifierKeyState.OFF -> {
                flow.value = ModifierKeyState.ONE_SHOT
            }
            ModifierKeyState.ONE_SHOT -> {
                if (now - lastTap <= DOUBLE_TAP_WINDOW_MS) {
                    flow.value = ModifierKeyState.LOCKED
                } else {
                    // Single tap again after window expired -- reset to ONE_SHOT
                    flow.value = ModifierKeyState.ONE_SHOT
                }
            }
            ModifierKeyState.LOCKED -> {
                flow.value = ModifierKeyState.OFF
            }
            ModifierKeyState.HELD, ModifierKeyState.CHORDING -> {
                // Ignore taps while held or chording
            }
        }

        setLastTapTime(type, now)
    }

    /**
     * Handle modifier pointer down. Enters HELD state.
     */
    @MainThread
    fun onModifierDown(type: ModifierType, pointerId: Int) {
        val flow = getFlow(type)
        // Only enter HELD if not already locked
        if (flow.value != ModifierKeyState.LOCKED) {
            flow.value = ModifierKeyState.HELD
        }
        setHeldPointerId(type, pointerId)
    }

    /**
     * Handle modifier pointer up. Exits HELD/CHORDING state (only if matching pointer).
     */
    @MainThread
    fun onModifierUp(type: ModifierType, pointerId: Int) {
        val heldId = getHeldPointerId(type)
        if (heldId == pointerId) {
            val flow = getFlow(type)
            if (flow.value == ModifierKeyState.HELD || flow.value == ModifierKeyState.CHORDING) {
                flow.value = ModifierKeyState.OFF
            }
            setHeldPointerId(type, null)
        }
    }

    /**
     * Called when a non-modifier key is pressed.
     * Transitions any HELD modifier to CHORDING state.
     * Also consumes ONE_SHOT modifiers (resets to OFF).
     */
    @MainThread
    fun onOtherKeyPressed() {
        for (flow in allModifierFlows) {
            if (flow.value == ModifierKeyState.HELD) {
                flow.value = ModifierKeyState.CHORDING
            }
        }
    }

    /**
     * Called after a non-modifier key press.
     * If any modifier is ONE_SHOT, reset it to OFF.
     */
    @MainThread
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
        if (_metaState.value == ModifierKeyState.ONE_SHOT) {
            _metaState.value = ModifierKeyState.OFF
        }
    }

    /**
     * Computes the android.view.KeyEvent meta state flags from the current modifier state.
     */
    fun getKeyEventMetaState(shifted: Boolean): Int {
        var meta = 0
        if (shifted) meta = meta or (android.view.KeyEvent.META_SHIFT_ON or android.view.KeyEvent.META_SHIFT_LEFT_ON)
        if (isCtrlActive()) meta = meta or (android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON)
        if (isAltActive()) meta = meta or (android.view.KeyEvent.META_ALT_ON or android.view.KeyEvent.META_ALT_LEFT_ON)
        if (isMetaActive()) meta = meta or (android.view.KeyEvent.META_META_ON or android.view.KeyEvent.META_META_LEFT_ON)
        return meta
    }

    private fun getFlow(type: ModifierType): MutableStateFlow<ModifierKeyState> = when (type) {
        ModifierType.SHIFT -> _shiftState
        ModifierType.CTRL -> _ctrlState
        ModifierType.ALT -> _altState
        ModifierType.META -> _metaState
    }

    private fun getLastTapTime(type: ModifierType): Long = when (type) {
        ModifierType.SHIFT -> shiftLastTapTime
        ModifierType.CTRL -> ctrlLastTapTime
        ModifierType.ALT -> altLastTapTime
        ModifierType.META -> metaLastTapTime
    }

    private fun setLastTapTime(type: ModifierType, time: Long) {
        when (type) {
            ModifierType.SHIFT -> shiftLastTapTime = time
            ModifierType.CTRL -> ctrlLastTapTime = time
            ModifierType.ALT -> altLastTapTime = time
            ModifierType.META -> metaLastTapTime = time
        }
    }

    private fun getHeldPointerId(type: ModifierType): Int? = when (type) {
        ModifierType.SHIFT -> shiftHeldPointerId
        ModifierType.CTRL -> ctrlHeldPointerId
        ModifierType.ALT -> altHeldPointerId
        ModifierType.META -> metaHeldPointerId
    }

    private fun setHeldPointerId(type: ModifierType, pointerId: Int?) {
        when (type) {
            ModifierType.SHIFT -> shiftHeldPointerId = pointerId
            ModifierType.CTRL -> ctrlHeldPointerId = pointerId
            ModifierType.ALT -> altHeldPointerId = pointerId
            ModifierType.META -> metaHeldPointerId = pointerId
        }
    }
}
