package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.modifier.ChordeTracker
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.data.repository.SettingsRepository

/**
 * Handles modifier key sending and chording for Ctrl, Alt, Meta, and Shift.
 *
 * Delegates the actual key-event synthesis back to [KeyEventSender] via the
 * [sendKeyDown] / [sendKeyUp] callbacks so that all raw event construction
 * stays in one place.
 */
internal class ModifierChordingController(
    private val modCtrlProvider: () -> Boolean,
    private val modAltProvider: () -> Boolean,
    private val modMetaProvider: () -> Boolean,
    private val shiftKeyStateProvider: () -> ChordeTracker,
    private val ctrlKeyStateProvider: () -> ChordeTracker,
    private val altKeyStateProvider: () -> ChordeTracker,
    private val metaKeyStateProvider: () -> ChordeTracker,
    private val setModCtrl: (Boolean) -> Unit,
    private val setModAlt: (Boolean) -> Unit,
    private val setModMeta: (Boolean) -> Unit,
    private val resetShift: () -> Unit,
    private val getShiftState: () -> Int,
    private val settings: SettingsRepository,
    private val sendKeyDownFn: (InputConnection?, Int, Int) -> Unit,
    private val sendKeyUpFn: (InputConnection?, Int, Int) -> Unit,
    private val inputConnectionProvider: () -> InputConnection?
) {

    internal fun delayChordingCtrlModifier(): Boolean = settings.chordingCtrlKey == 0
    internal fun delayChordingAltModifier(): Boolean = settings.chordingAltKey == 0
    internal fun delayChordingMetaModifier(): Boolean = settings.chordingMetaKey == 0

    internal fun sendShiftKey(ic: InputConnection?, isDown: Boolean) {
        val key = KeyEvent.KEYCODE_SHIFT_LEFT
        val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (isDown) {
            sendKeyDownFn(ic, key, meta)
        } else {
            sendKeyUpFn(ic, key, meta)
        }
    }

    fun sendCtrlKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) {
        if (chording && delayChordingCtrlModifier()) return
        var key = settings.chordingCtrlKey
        if (key == 0) key = KeyEvent.KEYCODE_CTRL_LEFT
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (isDown) {
            sendKeyDownFn(ic, key, meta)
        } else {
            sendKeyUpFn(ic, key, meta)
        }
    }

    fun sendAltKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) {
        if (chording && delayChordingAltModifier()) return
        var key = settings.chordingAltKey
        if (key == 0) key = KeyEvent.KEYCODE_ALT_LEFT
        val meta = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (isDown) {
            sendKeyDownFn(ic, key, meta)
        } else {
            sendKeyUpFn(ic, key, meta)
        }
    }

    fun sendMetaKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) {
        if (chording && delayChordingMetaModifier()) return
        var key = settings.chordingMetaKey
        if (key == 0) key = KeyEvent.KEYCODE_META_LEFT
        val meta = KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        if (isDown) {
            sendKeyDownFn(ic, key, meta)
        } else {
            sendKeyUpFn(ic, key, meta)
        }
    }

    /**
     * Sends modifier key-down events for the active modifiers.
     *
     * Accepts an optional pre-fetched [ic] to avoid redundant [inputConnectionProvider] calls
     * when invoked from [KeyEventSender.sendModifiedKeyDownUp].
     */
    fun sendModifierKeysDown(shifted: Boolean, ic: InputConnection? = inputConnectionProvider()) {
        if (shifted) {
            sendShiftKey(ic, true)
        }
        if (modCtrlProvider() && (!ctrlKeyStateProvider().isChording() || delayChordingCtrlModifier())) {
            sendCtrlKey(ic, true, false)
        }
        if (modAltProvider() && (!altKeyStateProvider().isChording() || delayChordingAltModifier())) {
            sendAltKey(ic, true, false)
        }
        if (modMetaProvider() && (!metaKeyStateProvider().isChording() || delayChordingMetaModifier())) {
            sendMetaKey(ic, true, false)
        }
    }

    /**
     * Handles modifier key-up events and resets modifier state as needed.
     *
     * Accepts an optional pre-fetched [ic] to avoid redundant [inputConnectionProvider] calls
     * when invoked from [KeyEventSender.sendModifiedKeyDownUp].
     */
    fun handleModifierKeysUp(shifted: Boolean, sendKey: Boolean, ic: InputConnection? = inputConnectionProvider()) {
        if (modMetaProvider() && (!metaKeyStateProvider().isChording() || delayChordingMetaModifier())) {
            if (sendKey) sendMetaKey(ic, false, false)
            if (!metaKeyStateProvider().isChording()) setModMeta(false)
        }
        if (modAltProvider() && (!altKeyStateProvider().isChording() || delayChordingAltModifier())) {
            if (sendKey) sendAltKey(ic, false, false)
            if (!altKeyStateProvider().isChording()) setModAlt(false)
        }
        if (modCtrlProvider() && (!ctrlKeyStateProvider().isChording() || delayChordingCtrlModifier())) {
            if (sendKey) sendCtrlKey(ic, false, false)
            if (!ctrlKeyStateProvider().isChording()) setModCtrl(false)
        }
        if (shifted) {
            if (sendKey) sendShiftKey(ic, false)
            val shiftState = getShiftState()
            if (!(shiftKeyStateProvider().isChording() || shiftState == Keyboard.SHIFT_LOCKED)) {
                resetShift()
            }
        }
    }

    /**
     * Sends modifier key-up events for the active modifiers.
     *
     * Accepts an optional pre-fetched [ic] to avoid redundant [inputConnectionProvider] calls
     * when invoked from [KeyEventSender.sendModifiedKeyDownUp].
     */
    fun sendModifierKeysUp(shifted: Boolean, ic: InputConnection? = inputConnectionProvider()) {
        handleModifierKeysUp(shifted, true, ic)
    }
}
