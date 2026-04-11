package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.ui.keyboard.KeyCodes

/**
 * Manages modifier key state (Shift, Ctrl, Alt, Meta, Fn) and the
 * onPress/onRelease lifecycle for modifier and non-modifier keys.
 * Extracted from LatinIME to isolate the modifier state machine.
 */
internal class ModifierHandler(private val ime: LatinIME) {

    // ── onPress / onRelease (KeyboardActionListener) ──────────────

    fun onPress(primaryCode: Int) {
        val ic = ime.currentInputConnection
        ime.mFeedbackManager.vibrate()
        ime.mFeedbackManager.playKeyClick(primaryCode)
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                ime.mShiftKeyState.onPress()
                startMultitouchShift()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                changeKeyboardMode()
                ime.mSymbolKeyState.onPress()
                ime.mKeyboardSwitcher!!.setAutoModeSwitchStateMomentary()
            }
            KeyCodes.CTRL_LEFT -> {
                setModCtrl(!ime.mModCtrl)
                ime.mCtrlKeyState.onPress()
                ime.mKeyEventSender.sendCtrlKey(ic, true, true)
            }
            KeyCodes.ALT_LEFT -> {
                setModAlt(!ime.mModAlt)
                ime.mAltKeyState.onPress()
                ime.mKeyEventSender.sendAltKey(ic, true, true)
            }
            KeyCodes.KEYCODE_META_LEFT -> {
                setModMeta(!ime.mModMeta)
                ime.mMetaKeyState.onPress()
                ime.mKeyEventSender.sendMetaKey(ic, true, true)
            }
            KeyCodes.KEYCODE_FN -> {
                setModFn(!ime.mModFn)
                ime.mFnKeyState.onPress()
            }
            else -> {
                ime.mShiftKeyState.onOtherKeyPressed()
                ime.mSymbolKeyState.onOtherKeyPressed()
                ime.mCtrlKeyState.onOtherKeyPressed()
                ime.mAltKeyState.onOtherKeyPressed()
                ime.mMetaKeyState.onOtherKeyPressed()
                ime.mFnKeyState.onOtherKeyPressed()
            }
        }
    }

    fun onRelease(primaryCode: Int) {
        val ic = ime.currentInputConnection
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                if (ime.mShiftKeyState.isChording()) resetMultitouchShift() else commitMultitouchShift()
                ime.mShiftKeyState.onRelease()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                if (ime.mKeyboardSwitcher!!.isInChordingAutoModeSwitchState()) changeKeyboardMode()
                ime.mSymbolKeyState.onRelease()
            }
            KeyCodes.CTRL_LEFT -> {
                if (ime.mCtrlKeyState.isChording()) setModCtrl(false)
                ime.mKeyEventSender.sendCtrlKey(ic, false, true)
                ime.mCtrlKeyState.onRelease()
            }
            KeyCodes.ALT_LEFT -> {
                if (ime.mAltKeyState.isChording()) setModAlt(false)
                ime.mKeyEventSender.sendAltKey(ic, false, true)
                ime.mAltKeyState.onRelease()
            }
            KeyCodes.KEYCODE_META_LEFT -> {
                if (ime.mMetaKeyState.isChording()) setModMeta(false)
                ime.mKeyEventSender.sendMetaKey(ic, false, true)
                ime.mMetaKeyState.onRelease()
            }
            KeyCodes.KEYCODE_FN -> {
                if (ime.mFnKeyState.isChording()) setModFn(false)
                ime.mFnKeyState.onRelease()
            }
        }
    }

    // ── Modifier setters ──────────────────────────────────────────

    private fun setModCtrl(enabled: Boolean) { ime.mModCtrl = enabled }
    private fun setModAlt(enabled: Boolean) { ime.mModAlt = enabled }
    private fun setModMeta(enabled: Boolean) { ime.mModMeta = enabled }
    private fun setModFn(enabled: Boolean) { ime.mModFn = enabled; ime.mKeyboardSwitcher!!.setFn(enabled) }

    // ── Shift state machine ───────────────────────────────────────

    fun updateShiftKeyState(attr: EditorInfo?) {
        val ic = ime.currentInputConnection
        if (ic != null && attr != null && ime.mKeyboardSwitcher!!.isAlphabetMode()) {
            val oldState = getShiftState()
            val isShifted = ime.mShiftKeyState.isChording()
            val isCapsLock = oldState == Keyboard.SHIFT_CAPS_LOCKED || oldState == Keyboard.SHIFT_LOCKED
            val isCaps = isCapsLock || getCursorCapsMode(ic, attr) != 0
            val newState = when {
                isShifted -> if (ime.mSavedShiftState == Keyboard.SHIFT_LOCKED) Keyboard.SHIFT_CAPS else Keyboard.SHIFT_ON
                isCaps -> if (isCapsLock) getCapsOrShiftLockState() else Keyboard.SHIFT_CAPS
                else -> Keyboard.SHIFT_OFF
            }
            ime.mKeyboardSwitcher!!.setShiftState(newState)
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
        ime.mKeyboardSwitcher?.getShiftState() ?: Keyboard.SHIFT_OFF

    fun isShiftCapsMode(): Boolean =
        ime.mKeyboardSwitcher?.getShiftState() == Keyboard.SHIFT_CAPS_LOCKED

    fun getCursorCapsMode(ic: InputConnection, attr: EditorInfo): Int {
        val ei = ime.currentInputEditorInfo
        return if (ime.mAutoCapActive && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
            ic.getCursorCapsMode(attr.inputType)
        } else {
            0
        }
    }

    fun isShiftMod(): Boolean {
        if (ime.mShiftKeyState.isChording()) return true
        val shiftState = ime.mKeyboardSwitcher?.getShiftState() ?: return false
        return shiftState == Keyboard.SHIFT_LOCKED || shiftState == Keyboard.SHIFT_CAPS_LOCKED
    }

    fun handleShift() = handleShiftInternal(false, -1)

    fun resetShift() = handleShiftInternal(true, Keyboard.SHIFT_OFF)

    fun changeKeyboardMode() {
        val switcher = ime.mKeyboardSwitcher!!
        if (switcher.isAlphabetMode()) {
            ime.mSavedShiftState = getShiftState()
        }
        switcher.toggleSymbols()
        if (switcher.isAlphabetMode()) {
            switcher.setShiftState(ime.mSavedShiftState)
        }
        updateShiftKeyState(ime.currentInputEditorInfo)
    }

    // ── Multitouch shift ──────────────────────────────────────────

    private fun startMultitouchShift() {
        var newState = Keyboard.SHIFT_ON
        if (ime.mKeyboardSwitcher!!.isAlphabetMode()) {
            ime.mSavedShiftState = getShiftState()
            if (ime.mSavedShiftState == Keyboard.SHIFT_LOCKED) newState = Keyboard.SHIFT_CAPS
        }
        handleShiftInternal(true, newState)
    }

    private fun commitMultitouchShift() {
        if (ime.mKeyboardSwitcher!!.isAlphabetMode()) {
            val newState = nextShiftState(ime.mSavedShiftState, true)
            handleShiftInternal(true, newState)
        }
    }

    private fun resetMultitouchShift() {
        val newState = if (ime.mSavedShiftState == Keyboard.SHIFT_CAPS_LOCKED
            || ime.mSavedShiftState == Keyboard.SHIFT_LOCKED
        ) {
            ime.mSavedShiftState
        } else {
            Keyboard.SHIFT_OFF
        }
        handleShiftInternal(true, newState)
    }

    private fun handleShiftInternal(forceState: Boolean, newState: Int) {
        val switcher = ime.mKeyboardSwitcher!!
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

    companion object {
        private fun getCapsOrShiftLockState(): Int {
            return if (LatinIME.sKeyboardSettings.capsLock) Keyboard.SHIFT_CAPS_LOCKED else Keyboard.SHIFT_LOCKED
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
}
