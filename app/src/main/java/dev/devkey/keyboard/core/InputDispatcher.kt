package dev.devkey.keyboard.core

import android.os.SystemClock
import android.util.Log
import dev.devkey.keyboard.ASCII_ENTER
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.QUICK_PRESS
import dev.devkey.keyboard.TextEntryState
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Routes key events from the keyboard surface to the appropriate handler.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class InputDispatcher(
    private val ime: LatinIME,
    private val handlers: InputHandlers
) {

    fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) {
        val eventTime = SystemClock.uptimeMillis()
        Log.d("DevKeyPress", "IME   code=$primaryCode x=$x y=$y")
        DevKeyLogger.text("key_event", mapOf(
            "code" to primaryCode,
            "shift" to (ime.getShiftState() != Keyboard.SHIFT_OFF),
            "ctrl" to ime.mModCtrl, "alt" to ime.mModAlt, "meta" to ime.mModMeta
        ))
        if (primaryCode != Keyboard.KEYCODE_DELETE || eventTime > ime.mLastKeyTime + QUICK_PRESS) {
            ime.mDeleteCount = 0
        }
        ime.mLastKeyTime = eventTime
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (ime.processMultiKey(primaryCode)) return
                handlers.handleBackspace()
                ime.mDeleteCount++
            }
            Keyboard.KEYCODE_SHIFT, Keyboard.KEYCODE_MODE_CHANGE,
            KeyCodes.CTRL_LEFT, KeyCodes.ALT_LEFT,
            KeyCodes.KEYCODE_META_LEFT, KeyCodes.KEYCODE_FN -> { /* handled in onPress/onRelease */ }
            Keyboard.KEYCODE_CANCEL -> { if (!ime.isShowingOptionDialog()) ime.handleClose() }
            KeyCodes.KEYCODE_OPTIONS -> ime.onOptionKeyPressed()
            KeyCodes.KEYCODE_OPTIONS_LONGPRESS -> ime.onOptionKeyLongPressed()
            KeyCodes.KEYCODE_COMPOSE -> { ime.mComposeMode = !ime.mComposeMode; ime.mComposeBuffer.clear() }
            KeyCodes.KEYCODE_NEXT_LANGUAGE -> ime.toggleLanguage(false, true)
            KeyCodes.KEYCODE_PREV_LANGUAGE -> ime.toggleLanguage(false, false)
            KeyCodes.KEYCODE_VOICE -> { /* voice handled by Compose UI */ }
            9 /* Tab */ -> { if (ime.processMultiKey(primaryCode)) return; ime.mKeyEventSender.sendTab() }
            KeyCodes.ESCAPE -> { if (ime.processMultiKey(primaryCode)) return; ime.mKeyEventSender.sendEscape() }
            KeyCodes.KEYCODE_DPAD_UP, KeyCodes.KEYCODE_DPAD_DOWN,
            KeyCodes.KEYCODE_DPAD_LEFT, KeyCodes.KEYCODE_DPAD_RIGHT,
            KeyCodes.KEYCODE_DPAD_CENTER, KeyCodes.KEYCODE_HOME, KeyCodes.KEYCODE_END,
            KeyCodes.KEYCODE_PAGE_UP, KeyCodes.KEYCODE_PAGE_DOWN,
            KeyCodes.KEYCODE_FKEY_F1, KeyCodes.KEYCODE_FKEY_F2, KeyCodes.KEYCODE_FKEY_F3,
            KeyCodes.KEYCODE_FKEY_F4, KeyCodes.KEYCODE_FKEY_F5, KeyCodes.KEYCODE_FKEY_F6,
            KeyCodes.KEYCODE_FKEY_F7, KeyCodes.KEYCODE_FKEY_F8, KeyCodes.KEYCODE_FKEY_F9,
            KeyCodes.KEYCODE_FKEY_F10, KeyCodes.KEYCODE_FKEY_F11, KeyCodes.KEYCODE_FKEY_F12,
            KeyCodes.KEYCODE_FORWARD_DEL, KeyCodes.KEYCODE_INSERT,
            KeyCodes.KEYCODE_SYSRQ, KeyCodes.KEYCODE_BREAK,
            KeyCodes.KEYCODE_NUM_LOCK, KeyCodes.KEYCODE_SCROLL_LOCK -> {
                if (ime.processMultiKey(primaryCode)) return
                ime.mKeyEventSender.sendSpecialKey(-primaryCode)
            }
            else -> handlers.handleDefault(primaryCode, keyCodes)
        }
        ime.mKeyboardSwitcher!!.onKey(primaryCode)
        ime.mEnteredText = null
    }

    fun onText(text: CharSequence) {
        val ic = ime.currentInputConnection ?: return
        if (ime.mPredicting && text.length == 1) {
            val c = text[0].code
            if (!ime.isWordSeparator(c)) {
                handlers.handleCharacter(c, intArrayOf(c))
                return
            }
        }
        ime.abortCorrection(false)
        ic.beginBatchEdit()
        if (ime.mPredicting) ime.commitTyped(ic, true)
        ime.maybeRemovePreviousPeriod(text)
        ic.commitText(text, 1)
        ic.endBatchEdit()
        ime.updateShiftKeyState(ime.currentInputEditorInfo)
        ime.mKeyboardSwitcher!!.onKey(0)
        ime.mJustRevertedSeparator = null
        ime.mJustAddedAutoSpace = false
        ime.mEnteredText = text
    }

    fun onCancel() = ime.mKeyboardSwitcher!!.onCancelInput()

}
