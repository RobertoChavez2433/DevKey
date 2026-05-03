package dev.devkey.keyboard.core

import android.os.SystemClock
import android.util.Log
import android.view.inputmethod.EditorInfo
import dev.devkey.keyboard.ASCII_ENTER
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.QUICK_PRESS
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Routes key events from the keyboard surface to the appropriate handler.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class InputDispatcher(
    private val state: ImeState,
    private val handlers: InputHandlers,
    private val icProvider: InputConnectionProvider,
    private val candidateViewHost: CandidateViewHost,
    private val keyEventSender: KeyEventSender,
    private val handleClose: () -> Unit,
    private val isShowingOptionDialog: () -> Boolean,
    private val onOptionKeyPressed: () -> Unit,
    private val onOptionKeyLongPressed: () -> Unit,
    private val toggleLanguage: (Boolean, Boolean) -> Unit,
    private val updateShiftKeyState: (EditorInfo?) -> Unit,
    private val commitTyped: (android.view.inputmethod.InputConnection?, Boolean) -> Unit,
    private val maybeRemovePreviousPeriod: (CharSequence) -> Unit,
    private val abortCorrection: (Boolean) -> Unit,
    private val getShiftState: () -> Int,
) {

    fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) {
        val eventTime = SystemClock.uptimeMillis()
        Log.d("DevKeyPress", "IME   code=$primaryCode x=$x y=$y")
        DevKeyLogger.text("key_event", mapOf(
            "code" to primaryCode,
            "shift" to (getShiftState() != Keyboard.SHIFT_OFF),
            "ctrl" to state.mModCtrl, "alt" to state.mModAlt, "meta" to state.mModMeta
        ))
        if (primaryCode != Keyboard.KEYCODE_DELETE || eventTime > state.mLastKeyTime + QUICK_PRESS) {
            state.mDeleteCount = 0
        }
        state.mLastKeyTime = eventTime
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (processComposeKey(state, primaryCode)) return
                handlers.handleBackspace()
                state.mDeleteCount++
            }
            Keyboard.KEYCODE_SHIFT, Keyboard.KEYCODE_MODE_CHANGE,
            KeyCodes.CTRL_LEFT, KeyCodes.ALT_LEFT,
            KeyCodes.KEYCODE_META_LEFT, KeyCodes.KEYCODE_FN -> { /* handled in onPress/onRelease */ }
            Keyboard.KEYCODE_CANCEL -> { if (!isShowingOptionDialog()) handleClose() }
            KeyCodes.KEYCODE_OPTIONS -> onOptionKeyPressed()
            KeyCodes.KEYCODE_OPTIONS_LONGPRESS -> onOptionKeyLongPressed()
            KeyCodes.KEYCODE_COMPOSE -> { state.mComposeMode = !state.mComposeMode; state.mComposeBuffer.clear() }
            KeyCodes.KEYCODE_NEXT_LANGUAGE -> toggleLanguage(false, true)
            KeyCodes.KEYCODE_PREV_LANGUAGE -> toggleLanguage(false, false)
            KeyCodes.KEYCODE_VOICE -> { /* voice handled by Compose UI */ }
            9 /* Tab */ -> { if (processComposeKey(state, primaryCode)) return; keyEventSender.sendTab() }
            KeyCodes.ESCAPE -> { if (processComposeKey(state, primaryCode)) return; keyEventSender.sendEscape() }
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
                if (processComposeKey(state, primaryCode)) return
                keyEventSender.sendSpecialKey(-primaryCode)
            }
            else -> handlers.handleDefault(primaryCode, keyCodes)
        }
        state.mKeyboardSwitcher!!.onKey(primaryCode)
        state.mEnteredText = null
    }

    fun onText(text: CharSequence) {
        val ic = icProvider.inputConnection ?: return
        DevKeyLogger.text(
            "text_sequence_dispatch",
            mapOf(
                "text_length" to text.length,
                "was_predicting" to state.mPredicting,
            )
        )
        if (state.mPredicting && text.length == 1) {
            val c = text[0].code
            if (!state.isWordSeparator(c)) {
                handlers.handleCharacter(c, intArrayOf(c))
                return
            }
        }
        abortCorrection(false)
        ic.beginBatchEdit()
        if (state.mPredicting) commitTyped(ic, true)
        maybeRemovePreviousPeriod(text)
        ic.commitText(text, 1)
        ic.endBatchEdit()
        updateShiftKeyState(icProvider.editorInfo)
        state.mKeyboardSwitcher!!.onKey(0)
        state.mJustRevertedSeparator = null
        state.mJustAddedAutoSpace = false
        state.mEnteredText = text
    }

    fun onCancel() = state.mKeyboardSwitcher!!.onCancelInput()

}
