package dev.devkey.keyboard.core

import dev.devkey.keyboard.debug.DevKeyLogger
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Holds per-type mutable state for each modifier key and provides typed
 * accessors. Extracted from [ModifierStateManager] to keep that class under
 * 200 lines; all logic remains in [ModifierStateManager].
 *
 * All fields are package-internal; callers outside this package use the public
 * API on [ModifierStateManager].
 */
internal class ModifierTransitionTable {

    // --- State flows ---
    val shiftFlow = MutableStateFlow(ModifierKeyState.OFF)
    val ctrlFlow  = MutableStateFlow(ModifierKeyState.OFF)
    val altFlow   = MutableStateFlow(ModifierKeyState.OFF)
    val metaFlow  = MutableStateFlow(ModifierKeyState.OFF)

    /** Ordered list used for iteration in [ModifierStateManager.onOtherKeyPressed]. */
    val allFlows = listOf(shiftFlow, ctrlFlow, altFlow, metaFlow)

    // --- Pointer tracking for HELD state ---
    private var shiftHeldPointerId: Int? = null
    private var ctrlHeldPointerId:  Int? = null
    private var altHeldPointerId:   Int? = null
    private var metaHeldPointerId:  Int? = null

    // --- Last tap time for double-tap detection ---
    private var shiftLastTapTime: Long = 0L
    private var ctrlLastTapTime:  Long = 0L
    private var altLastTapTime:   Long = 0L
    private var metaLastTapTime:  Long = 0L

    // --- State captured just before onModifierDown ---
    private var shiftStateBeforeDown: ModifierKeyState = ModifierKeyState.OFF
    private var ctrlStateBeforeDown:  ModifierKeyState = ModifierKeyState.OFF
    private var altStateBeforeDown:   ModifierKeyState = ModifierKeyState.OFF
    private var metaStateBeforeDown:  ModifierKeyState = ModifierKeyState.OFF

    // ---- Flow accessor ----

    fun getFlow(type: ModifierType): MutableStateFlow<ModifierKeyState> = when (type) {
        ModifierType.SHIFT -> shiftFlow
        ModifierType.CTRL  -> ctrlFlow
        ModifierType.ALT   -> altFlow
        ModifierType.META  -> metaFlow
    }

    // ---- Last-tap-time accessors ----

    fun getLastTapTime(type: ModifierType): Long = when (type) {
        ModifierType.SHIFT -> shiftLastTapTime
        ModifierType.CTRL  -> ctrlLastTapTime
        ModifierType.ALT   -> altLastTapTime
        ModifierType.META  -> metaLastTapTime
    }

    fun setLastTapTime(type: ModifierType, time: Long) {
        when (type) {
            ModifierType.SHIFT -> shiftLastTapTime = time
            ModifierType.CTRL  -> ctrlLastTapTime  = time
            ModifierType.ALT   -> altLastTapTime   = time
            ModifierType.META  -> metaLastTapTime  = time
        }
    }

    // ---- Held-pointer-id accessors ----

    fun getHeldPointerId(type: ModifierType): Int? = when (type) {
        ModifierType.SHIFT -> shiftHeldPointerId
        ModifierType.CTRL  -> ctrlHeldPointerId
        ModifierType.ALT   -> altHeldPointerId
        ModifierType.META  -> metaHeldPointerId
    }

    fun setHeldPointerId(type: ModifierType, pointerId: Int?) {
        when (type) {
            ModifierType.SHIFT -> shiftHeldPointerId = pointerId
            ModifierType.CTRL  -> ctrlHeldPointerId  = pointerId
            ModifierType.ALT   -> altHeldPointerId   = pointerId
            ModifierType.META  -> metaHeldPointerId  = pointerId
        }
    }

    // ---- State-before-down accessors ----

    fun getStateBeforeDown(type: ModifierType): ModifierKeyState = when (type) {
        ModifierType.SHIFT -> shiftStateBeforeDown
        ModifierType.CTRL  -> ctrlStateBeforeDown
        ModifierType.ALT   -> altStateBeforeDown
        ModifierType.META  -> metaStateBeforeDown
    }

    fun setStateBeforeDown(type: ModifierType, state: ModifierKeyState) {
        when (type) {
            ModifierType.SHIFT -> shiftStateBeforeDown = state
            ModifierType.CTRL  -> ctrlStateBeforeDown  = state
            ModifierType.ALT   -> altStateBeforeDown   = state
            ModifierType.META  -> metaStateBeforeDown  = state
        }
    }

    // ---- Transition logging ----

    /**
     * Emits a structured modifier transition event to the legacy `DevKeyPress` log tag
     * (harness regex compatible: `ModifierTransition <TYPE> <action> <from> -> <to>`)
     * and to the structured `DevKey/MOD` category for the driver `/wait` endpoint.
     *
     * FROM SPEC: Phase 3 defect #17 — tests match on
     * `ModifierTransition.*<TYPE>.*<action>.*<toState>`.
     * IMPORTANT: No content leakage — modifier types and states are structural.
     */
    fun logTransition(type: ModifierType, action: String, from: ModifierKeyState, to: ModifierKeyState) {
        android.util.Log.d("DevKeyPress", "ModifierTransition ${type.name} $action $from -> $to")
        DevKeyLogger.modifier(
            "modifier_transition",
            mapOf("type" to type.name, "action" to action, "from" to from.name, "to" to to.name)
        )
    }

    // ---- Full reset ----

    fun resetAll() {
        shiftFlow.value = ModifierKeyState.OFF
        ctrlFlow.value  = ModifierKeyState.OFF
        altFlow.value   = ModifierKeyState.OFF
        metaFlow.value  = ModifierKeyState.OFF

        shiftStateBeforeDown = ModifierKeyState.OFF
        ctrlStateBeforeDown  = ModifierKeyState.OFF
        altStateBeforeDown   = ModifierKeyState.OFF
        metaStateBeforeDown  = ModifierKeyState.OFF

        shiftHeldPointerId = null
        ctrlHeldPointerId  = null
        altHeldPointerId   = null
        metaHeldPointerId  = null

        shiftLastTapTime = 0L
        ctrlLastTapTime  = 0L
        altLastTapTime   = 0L
        metaLastTapTime  = 0L
    }
}
