package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.core.modifier.ChordeTracker

class KeyEventSender(
    private val inputConnectionProvider: () -> InputConnection?,
    private val modCtrlProvider: () -> Boolean,
    private val modAltProvider: () -> Boolean,
    private val modMetaProvider: () -> Boolean,
    private val shiftKeyStateProvider: () -> ChordeTracker,
    private val ctrlKeyStateProvider: () -> ChordeTracker,
    private val altKeyStateProvider: () -> ChordeTracker,
    private val metaKeyStateProvider: () -> ChordeTracker,
    private val shiftModProvider: () -> Boolean,
    private val setModCtrl: (Boolean) -> Unit,
    private val setModAlt: (Boolean) -> Unit,
    private val setModMeta: (Boolean) -> Unit,
    private val resetShift: () -> Unit,
    private val getShiftState: () -> Int,
    private val commitTyped: (InputConnection?, Boolean) -> Unit,
    private val sendKeyCharFn: (Char) -> Unit,
    private val sendDownUpKeyEventsFn: (Int) -> Unit,
    private val settings: SettingsRepository,
    private val ctrlAToastAction: () -> Unit
) {
    private val mcc = ModifierChordingController(
        modCtrlProvider, modAltProvider, modMetaProvider,
        shiftKeyStateProvider, ctrlKeyStateProvider, altKeyStateProvider, metaKeyStateProvider,
        setModCtrl, setModAlt, setModMeta, resetShift, getShiftState, settings,
        ::sendKeyDown, ::sendKeyUp, inputConnectionProvider
    )
    private val mkd = ModifiableKeyDispatcher(
        inputConnectionProvider, modCtrlProvider, modAltProvider, modMetaProvider,
        shiftModProvider, altKeyStateProvider, setModAlt, settings, ctrlAToastAction,
        ::sendModifiedKeyDownUp, { s -> sendModifierKeysDown(s) },
        { s -> sendModifierKeysUp(s) }, { s, k -> handleModifierKeysUp(s, k) }, sendKeyCharFn
    )

    fun getMetaState(shifted: Boolean): Int {
        var meta = 0
        if (shifted) meta = meta or (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        if (modCtrlProvider()) meta = meta or (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        if (modAltProvider()) meta = meta or (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
        if (modMetaProvider()) meta = meta or (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON)
        return meta
    }

    fun sendKeyDown(ic: InputConnection?, key: Int, meta: Int) {
        ic?.sendKeyEvent(KeyEvent(System.currentTimeMillis(), System.currentTimeMillis(), KeyEvent.ACTION_DOWN, key, 0, meta))
    }

    fun sendKeyUp(ic: InputConnection?, key: Int, meta: Int) {
        ic?.sendKeyEvent(KeyEvent(System.currentTimeMillis(), System.currentTimeMillis(), KeyEvent.ACTION_UP, key, 0, meta))
    }

    fun sendModifiedKeyDownUp(key: Int, shifted: Boolean) {
        val ic = inputConnectionProvider()
        val meta = getMetaState(shifted)
        sendModifierKeysDown(shifted, ic)
        sendKeyDown(ic, key, meta)
        sendKeyUp(ic, key, meta)
        sendModifierKeysUp(shifted, ic)
    }

    fun sendModifiedKeyDownUp(key: Int) = sendModifiedKeyDownUp(key, shiftModProvider())
    fun sendCtrlKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) = mcc.sendCtrlKey(ic, isDown, chording)
    fun sendAltKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) = mcc.sendAltKey(ic, isDown, chording)
    fun sendMetaKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) = mcc.sendMetaKey(ic, isDown, chording)
    fun sendModifierKeysDown(shifted: Boolean, ic: InputConnection? = inputConnectionProvider()) = mcc.sendModifierKeysDown(shifted, ic)
    fun handleModifierKeysUp(
        shifted: Boolean, sendKey: Boolean, ic: InputConnection? = inputConnectionProvider()
    ) = mcc.handleModifierKeysUp(shifted, sendKey, ic)
    fun sendModifierKeysUp(shifted: Boolean, ic: InputConnection? = inputConnectionProvider()) = mcc.sendModifierKeysUp(shifted, ic)

    fun sendSpecialKey(code: Int) {
        commitTyped(inputConnectionProvider(), true)
        sendModifiedKeyDownUp(code)
    }

    fun sendTab() = sendModifiedKeyDownUp(KeyEvent.KEYCODE_TAB)
    fun sendEscape() = sendModifiedKeyDownUp(KeyEvent.KEYCODE_ESCAPE)
    fun sendModifiableKeyChar(ch: Char) = mkd.dispatch(ch)
}
