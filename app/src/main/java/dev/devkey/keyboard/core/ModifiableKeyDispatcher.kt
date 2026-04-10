package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.ChordeTracker
import dev.devkey.keyboard.data.repository.SettingsRepository

/**
 * Handles dispatching a printable character with active modifier state.
 * Extracted from [KeyEventSender] to keep the shell thin.
 */
internal class ModifiableKeyDispatcher(
    private val inputConnectionProvider: () -> InputConnection?,
    private val modCtrlProvider: () -> Boolean,
    private val modAltProvider: () -> Boolean,
    private val modMetaProvider: () -> Boolean,
    private val shiftModProvider: () -> Boolean,
    private val altKeyStateProvider: () -> ChordeTracker,
    private val setModAlt: (Boolean) -> Unit,
    private val settings: SettingsRepository,
    private val ctrlAToastAction: () -> Unit,
    private val sendModifiedKeyDownUp: (Int, Boolean) -> Unit,
    private val sendModifierKeysDown: (Boolean) -> Unit,
    private val sendModifierKeysUp: (Boolean) -> Unit,
    private val handleModifierKeysUp: (Boolean, Boolean) -> Unit,
    private val sendKeyCharFn: (Char) -> Unit
) {
    fun dispatch(ch: Char) {
        val modShift = shiftModProvider()
        if ((modShift || modCtrlProvider() || modAltProvider() || modMetaProvider()) && ch.code > 0 && ch.code < 127) {
            val ic = inputConnectionProvider()
            val combinedCode = CharToKeyCodeMapper.asciiToKeyCode[ch.code]
            if (combinedCode > 0) {
                val code = combinedCode and CharToKeyCodeMapper.KF_MASK
                val shiftable = (combinedCode and CharToKeyCodeMapper.KF_SHIFTABLE) > 0
                val upper = (combinedCode and CharToKeyCodeMapper.KF_UPPER) > 0
                val letter = (combinedCode and CharToKeyCodeMapper.KF_LETTER) > 0
                val shifted = modShift && (upper || shiftable)
                if (letter && !modCtrlProvider() && !modAltProvider() && !modMetaProvider()) {
                    ic?.commitText(ch.toString(), 1)
                    handleModifierKeysUp(false, false)
                } else if ((ch == 'a' || ch == 'A') && modCtrlProvider()) {
                    if (settings.ctrlAOverride == 0) {
                        if (modAltProvider()) {
                            val isChordingAlt = altKeyStateProvider().isChording()
                            setModAlt(false)
                            sendModifiedKeyDownUp(code, shifted)
                            if (isChordingAlt) setModAlt(true)
                        } else {
                            ctrlAToastAction()
                            sendModifierKeysDown(shifted)
                            sendModifierKeysUp(shifted)
                            return
                        }
                    } else if (settings.ctrlAOverride == 1) {
                        sendModifierKeysDown(shifted)
                        sendModifierKeysUp(shifted)
                        return
                    } else {
                        sendModifiedKeyDownUp(code, shifted)
                    }
                } else {
                    sendModifiedKeyDownUp(code, shifted)
                }
                return
            }
        }

        if (ch in '0'..'9') {
            val ic = inputConnectionProvider()
            ic?.clearMetaKeyStates(
                KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON
            )
        }

        sendKeyCharFn(ch)
    }
}
