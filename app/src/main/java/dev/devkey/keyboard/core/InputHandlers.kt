package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.ASCII_ENTER
import dev.devkey.keyboard.DELETE_ACCELERATE_AT
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.core.text.EditingUtil
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Handles character input, backspace, and separator logic.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class InputHandlers(
    private val state: ImeState,
    private val icProvider: InputConnectionProvider,
    private val suggestionCoordinator: SuggestionCoordinator,
    private val suggestionPicker: SuggestionPicker,
    private val modifierHandler: ModifierHandler,
    private val keyEventSender: KeyEventSender,
    private val puncHeuristics: PunctuationHeuristics
) {
    private val separatorHandler = SeparatorHandler(
        state = state,
        icProvider = icProvider,
        suggestionCoordinator = suggestionCoordinator,
        modifierHandler = modifierHandler,
        keyEventSender = keyEventSender,
        puncHeuristics = puncHeuristics,
        commitTyped = ::commitTyped
    )

    fun handleDefault(primaryCode: Int, keyCodes: IntArray?) {
        if (!state.mComposeMode && state.mDeadKeysActive
            && Character.getType(primaryCode) == Character.NON_SPACING_MARK.toInt()
        ) {
            if (!state.mDeadAccentBuffer.execute(primaryCode)) { /* double-press */ }
            else modifierHandler.updateShiftKeyState(icProvider.editorInfo)
        } else if (processComposeKey(state, primaryCode)) {
            // handled
        } else {
            if (primaryCode != ASCII_ENTER) state.mJustAddedAutoSpace = false
            if (state.isWordSeparator(primaryCode)) handleSeparator(primaryCode)
            else handleCharacter(primaryCode, keyCodes)
            state.mJustRevertedSeparator = null
        }
    }

    fun handleBackspace() {
        var deleteChar = false
        val wasComposing = state.mPredicting
        val ic = icProvider.inputConnection
        if (ic == null) {
            DevKeyLogger.text("backspace_handled", mapOf("was_composing" to wasComposing, "ic_null" to true))
            return
        }
        ic.beginBatchEdit()
        if (state.mPredicting) {
            val length = state.mComposing.length
            if (length > 0) {
                state.mComposing.delete(length - 1, length)
                state.mWord.deleteLast()
                ic.setComposingText(state.mComposing, 1)
                if (state.mComposing.isEmpty()) state.mPredicting = false
                SessionDependencies.composingWord.value = state.mComposing.toString()
                suggestionCoordinator.postUpdateSuggestions()
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        } else {
            deleteChar = true
        }
        TextEntryState.backspace()
        if (TextEntryState.getState() == TextEntryState.State.UNDO_COMMIT) {
            revertLastWord(deleteChar)
            ic.endBatchEdit()
            DevKeyLogger.text("backspace_handled", mapOf("was_composing" to wasComposing))
            return
        } else if (state.mEnteredText != null && EditingUtil.sameAsTextBeforeCursor(ic, state.mEnteredText!!)) {
            ic.deleteSurroundingText(state.mEnteredText!!.length, 0)
        } else if (deleteChar) {
            if (state.mCandidateView != null && state.mCandidateView!!.dismissAddToDictionaryHint()) {
                revertLastWord(deleteChar)
            } else {
                icProvider.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                if (state.mDeleteCount > DELETE_ACCELERATE_AT) icProvider.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
        state.mJustRevertedSeparator = null
        ic.endBatchEdit()
        DevKeyLogger.text("backspace_handled", mapOf("was_composing" to wasComposing))
    }

    fun handleCharacter(primaryCode: Int, keyCodes: IntArray?) {
        if (state.mLastSelectionStart == state.mLastSelectionEnd && TextEntryState.isCorrecting()) {
            suggestionCoordinator.abortCorrection(false)
        }
        val predOn = state.isPredictionOn(suggestionCoordinator.isPredictionWanted())
        if (state.isAlphabet(primaryCode) && predOn
            && !state.mModCtrl && !state.mModAlt && !state.mModMeta
            && !EditingUtil.isCursorTouchingWord(icProvider.inputConnection, state.mWordSeparators ?: "", state.mSuggestPuncList)
        ) {
            if (!state.mPredicting) {
                state.mPredicting = true
                state.mComposing.setLength(0)
                suggestionCoordinator.saveWordInHistory(state.mBestWord)
                state.mWord.reset()
            }
        }
        if (state.mModCtrl || state.mModAlt || state.mModMeta) commitTyped(icProvider.inputConnection, true)
        if (state.mPredicting) {
            if (modifierHandler.isShiftCapsMode() && state.mKeyboardSwitcher!!.isAlphabetMode() && state.mComposing.isEmpty()) {
                state.mWord.setFirstCharCapitalized(true)
            }
            state.mComposing.append(primaryCode.toChar())
            state.mWord.add(primaryCode, keyCodes ?: intArrayOf(primaryCode))
            val ic = icProvider.inputConnection
            if (ic != null) {
                if (state.mWord.size() == 1) {
                    val editorInfo = icProvider.editorInfo
                    if (editorInfo != null) state.mWord.setAutoCapitalized(modifierHandler.getCursorCapsMode(ic, editorInfo) != 0)
                }
                ic.setComposingText(state.mComposing, 1)
            }
            SessionDependencies.composingWord.value = state.mComposing.toString()
            suggestionCoordinator.postUpdateSuggestions()
        } else {
            keyEventSender.sendModifiableKeyChar(primaryCode.toChar())
        }
        modifierHandler.updateShiftKeyState(icProvider.editorInfo)
        TextEntryState.typedCharacter(primaryCode.toChar(), state.isWordSeparator(primaryCode))
    }

    fun handleSeparator(primaryCode: Int) {
        separatorHandler.handle(primaryCode)
    }

    fun commitTyped(inputConnection: InputConnection?, manual: Boolean) {
        if (state.mPredicting) {
            state.mPredicting = false
            if (state.mComposing.isNotEmpty()) {
                inputConnection?.commitText(state.mComposing, 1)
                state.mCommittedLength = state.mComposing.length
                if (manual) {
                    TextEntryState.manualTyped(state.mComposing)
                } else {
                    TextEntryState.acceptedTyped(state.mComposing)
                }
                SessionDependencies.commitWord(state.mComposing.toString(), state.serviceScope)
            }
            SessionDependencies.composingWord.value = ""
            SessionDependencies.pendingCorrection.value = null
            suggestionCoordinator.updateSuggestions()
        }
    }

    fun revertLastWord(deleteChar: Boolean) {
        val length = state.mComposing.length
        if (!state.mPredicting && length > 0) {
            val ic = icProvider.inputConnection
            state.mPredicting = true
            state.mJustRevertedSeparator = ic?.getTextBeforeCursor(1, 0)
            if (deleteChar) ic?.deleteSurroundingText(1, 0)
            var toDelete = state.mCommittedLength
            val toTheLeft = ic?.getTextBeforeCursor(state.mCommittedLength, 0)
            if (toTheLeft != null && toTheLeft.isNotEmpty() && state.isWordSeparator(toTheLeft[0].code)) {
                toDelete--
            }
            ic?.deleteSurroundingText(toDelete, 0)
            ic?.setComposingText(state.mComposing, 1)
            TextEntryState.backspace()
            suggestionCoordinator.postUpdateSuggestions()
        } else {
            icProvider.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            state.mJustRevertedSeparator = null
        }
    }

}
