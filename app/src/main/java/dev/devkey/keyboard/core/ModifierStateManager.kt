package dev.devkey.keyboard.core

import android.os.SystemClock
import androidx.annotation.MainThread
import dev.devkey.keyboard.debug.DevKeyLogger
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

    // State before the most recent onModifierDown call.
    // Used by onModifierTap to detect double-tap when down/up cycle
    // has already reset the state back to OFF.
    private var shiftStateBeforeDown: ModifierKeyState = ModifierKeyState.OFF
    private var ctrlStateBeforeDown: ModifierKeyState = ModifierKeyState.OFF
    private var altStateBeforeDown: ModifierKeyState = ModifierKeyState.OFF
    private var metaStateBeforeDown: ModifierKeyState = ModifierKeyState.OFF

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
     *
     * Note: In the real event flow (KeyView), the sequence is always
     * onModifierDown → onModifierUp → onModifierTap. The down/up cycle converts
     * ONE_SHOT → HELD → OFF, so by the time tap runs the state is OFF. We use
     * [stateBeforeDown] to recover the pre-down state for double-tap detection.
     */
    @MainThread
    fun onModifierTap(type: ModifierType) {
        val flow = getFlow(type)
        val now = timeProvider()
        val lastTap = getLastTapTime(type)
        val preDownState = getStateBeforeDown(type)
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

        if (flow.value != fromState) {
            logTransition(type, "tap", fromState, flow.value)
        }
        setLastTapTime(type, now)
    }

    /**
     * Emit a structured modifier transition event to both the legacy
     * `DevKeyPress` tag (format `ModifierTransition SHIFT tap ONE_SHOT` —
     * harness regex compatible) AND the structured `DevKey/MOD` category so
     * the driver `/wait` endpoint can gate on it.
     *
     * FROM SPEC: Phase 3 defect #17 (`.claude/plans/2026-04-09-pre-release-phase3.md`)
     *            — Tests `test_modifiers.test_{shift,ctrl}_*` match on the
     *            `ModifierTransition.*<TYPE>.*<action>.*<toState>` pattern.
     * IMPORTANT: No content leakage — modifier types and states are structural.
     */
    private fun logTransition(
        type: ModifierType,
        action: String,
        from: ModifierKeyState,
        to: ModifierKeyState
    ) {
        // Legacy DevKeyPress tag for the capture_logcat harness path.
        android.util.Log.d(
            "DevKeyPress",
            "ModifierTransition ${type.name} $action $from -> $to"
        )
        // Structured category for the driver-server /wait path.
        DevKeyLogger.modifier(
            "modifier_transition",
            mapOf(
                "type" to type.name,
                "action" to action,
                "from" to from.name,
                "to" to to.name
            )
        )
    }

    /**
     * Handle modifier pointer down. Enters HELD state.
     * Saves the pre-down state so [onModifierTap] can detect double-taps.
     */
    @MainThread
    fun onModifierDown(type: ModifierType, pointerId: Int) {
        val flow = getFlow(type)
        val fromState = flow.value
        setStateBeforeDown(type, fromState)
        // Only enter HELD if not already locked
        if (fromState != ModifierKeyState.LOCKED) {
            flow.value = ModifierKeyState.HELD
            logTransition(type, "down", fromState, ModifierKeyState.HELD)
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
            val fromState = flow.value
            if (fromState == ModifierKeyState.HELD || fromState == ModifierKeyState.CHORDING) {
                flow.value = ModifierKeyState.OFF
                logTransition(type, "up", fromState, ModifierKeyState.OFF)
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
        for ((idx, flow) in allModifierFlows.withIndex()) {
            if (flow.value == ModifierKeyState.HELD) {
                flow.value = ModifierKeyState.CHORDING
                logTransition(
                    ModifierType.values()[idx],
                    "other_key",
                    ModifierKeyState.HELD,
                    ModifierKeyState.CHORDING
                )
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
            logTransition(ModifierType.SHIFT, "consume", ModifierKeyState.ONE_SHOT, ModifierKeyState.OFF)
        }
        if (_ctrlState.value == ModifierKeyState.ONE_SHOT) {
            _ctrlState.value = ModifierKeyState.OFF
            logTransition(ModifierType.CTRL, "consume", ModifierKeyState.ONE_SHOT, ModifierKeyState.OFF)
        }
        if (_altState.value == ModifierKeyState.ONE_SHOT) {
            _altState.value = ModifierKeyState.OFF
            logTransition(ModifierType.ALT, "consume", ModifierKeyState.ONE_SHOT, ModifierKeyState.OFF)
        }
        if (_metaState.value == ModifierKeyState.ONE_SHOT) {
            _metaState.value = ModifierKeyState.OFF
            logTransition(ModifierType.META, "consume", ModifierKeyState.ONE_SHOT, ModifierKeyState.OFF)
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

    private fun getStateBeforeDown(type: ModifierType): ModifierKeyState = when (type) {
        ModifierType.SHIFT -> shiftStateBeforeDown
        ModifierType.CTRL -> ctrlStateBeforeDown
        ModifierType.ALT -> altStateBeforeDown
        ModifierType.META -> metaStateBeforeDown
    }

    private fun setStateBeforeDown(type: ModifierType, state: ModifierKeyState) {
        when (type) {
            ModifierType.SHIFT -> shiftStateBeforeDown = state
            ModifierType.CTRL -> ctrlStateBeforeDown = state
            ModifierType.ALT -> altStateBeforeDown = state
            ModifierType.META -> metaStateBeforeDown = state
        }
    }
}
