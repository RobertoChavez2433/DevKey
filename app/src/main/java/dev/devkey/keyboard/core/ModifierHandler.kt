package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.smarttext.SmartTextCapitalizationDecision
import dev.devkey.keyboard.feature.smarttext.SmartTextCapitalizationRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextCapitalizationReason
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Manages modifier key state (Shift, Ctrl, Alt, Meta, Fn) and the
 * onPress/onRelease lifecycle for modifier and non-modifier keys.
 * Extracted from LatinIME to isolate the modifier state machine.
 */
internal class ModifierHandler(
    private val state: ImeState,
    private val icProvider: InputConnectionProvider,
    private val feedbackManager: FeedbackManager,
    private val keyEventSender: KeyEventSender,
    private val settings: SettingsRepository
) {

    // ── onPress / onRelease (KeyboardActionListener) ──────────────

    fun onPress(primaryCode: Int) {
        val ic = icProvider.inputConnection
        feedbackManager.vibrate()
        feedbackManager.playKeyClick(primaryCode)
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                state.mShiftKeyState.onPress()
                startMultitouchShift()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                changeKeyboardMode()
                state.mSymbolKeyState.onPress()
                state.mKeyboardSwitcher!!.setAutoModeSwitchStateMomentary()
            }
            KeyCodes.CTRL_LEFT -> {
                setModCtrl(!state.mModCtrl)
                state.mCtrlKeyState.onPress()
                keyEventSender.sendCtrlKey(ic, true, true)
            }
            KeyCodes.ALT_LEFT -> {
                setModAlt(!state.mModAlt)
                state.mAltKeyState.onPress()
                keyEventSender.sendAltKey(ic, true, true)
            }
            KeyCodes.KEYCODE_META_LEFT -> {
                setModMeta(!state.mModMeta)
                state.mMetaKeyState.onPress()
                keyEventSender.sendMetaKey(ic, true, true)
            }
            KeyCodes.KEYCODE_FN -> {
                setModFn(!state.mModFn)
                state.mFnKeyState.onPress()
            }
            else -> {
                state.mShiftKeyState.onOtherKeyPressed()
                state.mSymbolKeyState.onOtherKeyPressed()
                state.mCtrlKeyState.onOtherKeyPressed()
                state.mAltKeyState.onOtherKeyPressed()
                state.mMetaKeyState.onOtherKeyPressed()
                state.mFnKeyState.onOtherKeyPressed()
            }
        }
    }

    fun onRelease(primaryCode: Int) {
        val ic = icProvider.inputConnection
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                if (state.mShiftKeyState.isChording()) resetMultitouchShift() else commitMultitouchShift()
                state.mShiftKeyState.onRelease()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                if (state.mKeyboardSwitcher!!.isInChordingAutoModeSwitchState()) changeKeyboardMode()
                state.mSymbolKeyState.onRelease()
            }
            KeyCodes.CTRL_LEFT -> {
                if (state.mCtrlKeyState.isChording()) setModCtrl(false)
                keyEventSender.sendCtrlKey(ic, false, true)
                state.mCtrlKeyState.onRelease()
            }
            KeyCodes.ALT_LEFT -> {
                if (state.mAltKeyState.isChording()) setModAlt(false)
                keyEventSender.sendAltKey(ic, false, true)
                state.mAltKeyState.onRelease()
            }
            KeyCodes.KEYCODE_META_LEFT -> {
                if (state.mMetaKeyState.isChording()) setModMeta(false)
                keyEventSender.sendMetaKey(ic, false, true)
                state.mMetaKeyState.onRelease()
            }
            KeyCodes.KEYCODE_FN -> {
                if (state.mFnKeyState.isChording()) setModFn(false)
                state.mFnKeyState.onRelease()
            }
        }
    }

    // ── Modifier setters ──────────────────────────────────────────

    private fun setModCtrl(enabled: Boolean) { state.mModCtrl = enabled }
    private fun setModAlt(enabled: Boolean) { state.mModAlt = enabled }
    private fun setModMeta(enabled: Boolean) { state.mModMeta = enabled }
    private fun setModFn(enabled: Boolean) { state.mModFn = enabled; state.mKeyboardSwitcher!!.setFn(enabled) }

    // ── Shift state machine ───────────────────────────────────────

    fun updateShiftKeyState(attr: EditorInfo?) {
        val ic = icProvider.inputConnection
        if (ic != null && attr != null && state.mKeyboardSwitcher!!.isAlphabetMode()) {
            val oldState = getShiftState()
            val isShifted = state.mShiftKeyState.isChording()
            val isCapsLock = oldState == Keyboard.SHIFT_CAPS_LOCKED || oldState == Keyboard.SHIFT_LOCKED
            val isCaps = isCapsLock || getCursorCapsMode(ic, attr) != 0
            val newState = when {
                isShifted -> if (state.mSavedShiftState == Keyboard.SHIFT_LOCKED) Keyboard.SHIFT_CAPS else Keyboard.SHIFT_ON
                isCaps -> if (isCapsLock) getCapsOrShiftLockState() else Keyboard.SHIFT_CAPS
                else -> Keyboard.SHIFT_OFF
            }
            state.mKeyboardSwitcher!!.setShiftState(newState)
            if (!isShifted && !isCapsLock && isCaps) {
                DevKeyLogger.ime("auto_cap_applied", mapOf("new_shift_state" to newState))
            }
        }
        if (ic != null) {
            val states = KeyEvent.META_FUNCTION_ON or
                KeyEvent.META_ALT_MASK or
                KeyEvent.META_CTRL_MASK or
                KeyEvent.META_META_MASK or
                KeyEvent.META_SYM_ON
            ic.clearMetaKeyStates(states)
        }
    }

    fun getShiftState(): Int =
        state.mKeyboardSwitcher?.getShiftState() ?: Keyboard.SHIFT_OFF

    fun isShiftCapsMode(): Boolean =
        state.mKeyboardSwitcher?.getShiftState() == Keyboard.SHIFT_CAPS_LOCKED

    fun getCursorCapsMode(ic: InputConnection, attr: EditorInfo): Int {
        val ei = icProvider.editorInfo
        val editorAcceptsText = ei != null && ei.inputType != EditorInfo.TYPE_NULL
        val cursorCapsMode = if (editorAcceptsText) ic.getCursorCapsMode(attr.inputType) else 0
        val decision = SessionDependencies.smartTextEngine?.capitalization(
            SmartTextCapitalizationRequest(
                autoCapEnabled = state.mAutoCapActive,
                editorAcceptsText = editorAcceptsText,
                cursorCapsMode = cursorCapsMode,
            )
        ) ?: defaultCapitalizationDecision(
            autoCapEnabled = state.mAutoCapActive,
            editorAcceptsText = editorAcceptsText,
            cursorCapsMode = cursorCapsMode,
        )
        return if (decision.apply) cursorCapsMode else 0
    }

    private fun defaultCapitalizationDecision(
        autoCapEnabled: Boolean,
        editorAcceptsText: Boolean,
        cursorCapsMode: Int,
    ): SmartTextCapitalizationDecision =
        SmartTextCapitalizationDecision(
            apply = autoCapEnabled && editorAcceptsText && cursorCapsMode != 0,
            reason = SmartTextCapitalizationReason.DEFAULT_RULE,
        )

    fun isShiftMod(): Boolean {
        if (state.mShiftKeyState.isChording()) return true
        val shiftState = state.mKeyboardSwitcher?.getShiftState() ?: return false
        return shiftState == Keyboard.SHIFT_LOCKED || shiftState == Keyboard.SHIFT_CAPS_LOCKED
    }

    fun handleShift() = handleShiftInternal(false, -1)

    fun resetShift() = handleShiftInternal(true, Keyboard.SHIFT_OFF)

    fun changeKeyboardMode() {
        val switcher = state.mKeyboardSwitcher!!
        if (switcher.isAlphabetMode()) {
            state.mSavedShiftState = getShiftState()
        }
        switcher.toggleSymbols()
        if (switcher.isAlphabetMode()) {
            switcher.setShiftState(state.mSavedShiftState)
        }
        updateShiftKeyState(icProvider.editorInfo)
    }

    // ── Multitouch shift ──────────────────────────────────────────

    private fun startMultitouchShift() {
        var newState = Keyboard.SHIFT_ON
        if (state.mKeyboardSwitcher!!.isAlphabetMode()) {
            state.mSavedShiftState = getShiftState()
            if (state.mSavedShiftState == Keyboard.SHIFT_LOCKED) newState = Keyboard.SHIFT_CAPS
        }
        handleShiftInternal(true, newState)
    }

    private fun commitMultitouchShift() {
        if (state.mKeyboardSwitcher!!.isAlphabetMode()) {
            val newState = nextShiftState(state.mSavedShiftState, true)
            handleShiftInternal(true, newState)
        }
    }

    private fun resetMultitouchShift() {
        val newState = if (state.mSavedShiftState == Keyboard.SHIFT_CAPS_LOCKED
            || state.mSavedShiftState == Keyboard.SHIFT_LOCKED
        ) {
            state.mSavedShiftState
        } else {
            Keyboard.SHIFT_OFF
        }
        handleShiftInternal(true, newState)
    }

    private fun handleShiftInternal(forceState: Boolean, newState: Int) {
        val switcher = state.mKeyboardSwitcher!!
        if (switcher.isAlphabetMode()) {
            if (forceState) {
                switcher.setShiftState(newState)
            } else {
                switcher.setShiftState(nextShiftState(getShiftState(), true))
            }
        } else {
            switcher.toggleShift()
        }
    }

    // ── Shift helpers (moved from companion to access settings) ──

    private fun getCapsOrShiftLockState(): Int {
        return if (settings.capsLock) Keyboard.SHIFT_CAPS_LOCKED else Keyboard.SHIFT_LOCKED
    }

    private fun nextShiftState(prevState: Int, allowCapsLock: Boolean): Int {
        return if (allowCapsLock) {
            when (prevState) {
                Keyboard.SHIFT_OFF -> Keyboard.SHIFT_ON
                Keyboard.SHIFT_ON -> getCapsOrShiftLockState()
                else -> Keyboard.SHIFT_OFF
            }
        } else {
            if (prevState == Keyboard.SHIFT_OFF) Keyboard.SHIFT_ON else Keyboard.SHIFT_OFF
        }
    }
}
