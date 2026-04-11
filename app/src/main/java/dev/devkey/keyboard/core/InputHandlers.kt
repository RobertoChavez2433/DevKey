package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.ASCII_ENTER
import dev.devkey.keyboard.AutoDictionary
import dev.devkey.keyboard.ASCII_PERIOD
import dev.devkey.keyboard.ASCII_SPACE
import dev.devkey.keyboard.DELETE_ACCELERATE_AT
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.Suggest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.devkey.keyboard.TextEntryState
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.feature.prediction.AutocorrectResult
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Handles character input, backspace, and separator logic.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class InputHandlers(private val ime: LatinIME) {

    fun handleDefault(primaryCode: Int, keyCodes: IntArray?) {
        if (!ime.mComposeMode && ime.mDeadKeysActive
            && Character.getType(primaryCode) == Character.NON_SPACING_MARK.toInt()
        ) {
            if (!ime.mDeadAccentBuffer.execute(primaryCode)) { /* double-press */ }
            else ime.updateShiftKeyState(ime.currentInputEditorInfo)
        } else if (ime.processMultiKey(primaryCode)) {
            // handled
        } else {
            if (primaryCode != ASCII_ENTER) ime.mJustAddedAutoSpace = false
            if (ime.isWordSeparator(primaryCode)) handleSeparator(primaryCode)
            else handleCharacter(primaryCode, keyCodes)
            ime.mJustRevertedSeparator = null
        }
    }

    fun handleBackspace() {
        var deleteChar = false
        val ic = ime.currentInputConnection ?: return
        ic.beginBatchEdit()
        if (ime.mPredicting) {
            val length = ime.mComposing.length
            if (length > 0) {
                ime.mComposing.delete(length - 1, length)
                ime.mWord.deleteLast()
                ic.setComposingText(ime.mComposing, 1)
                if (ime.mComposing.isEmpty()) ime.mPredicting = false
                SessionDependencies.composingWord.value = ime.mComposing.toString()
                ime.mSuggestionCoordinator.postUpdateSuggestions()
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        } else {
            deleteChar = true
        }
        TextEntryState.backspace()
        if (TextEntryState.getState() == TextEntryState.State.UNDO_COMMIT) {
            ime.revertLastWord(deleteChar)
            ic.endBatchEdit()
            return
        } else if (ime.mEnteredText != null && ime.sameAsTextBeforeCursor(ic, ime.mEnteredText!!)) {
            ic.deleteSurroundingText(ime.mEnteredText!!.length, 0)
        } else if (deleteChar) {
            if (ime.mCandidateView != null && ime.mCandidateView!!.dismissAddToDictionaryHint()) {
                ime.revertLastWord(deleteChar)
            } else {
                ime.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                if (ime.mDeleteCount > DELETE_ACCELERATE_AT) ime.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
        ime.mJustRevertedSeparator = null
        ic.endBatchEdit()
    }

    fun handleCharacter(primaryCode: Int, keyCodes: IntArray?) {
        if (ime.mLastSelectionStart == ime.mLastSelectionEnd && TextEntryState.isCorrecting()) {
            ime.abortCorrection(false)
        }
        if (ime.isAlphabet(primaryCode) && ime.isPredictionOn()
            && !ime.mModCtrl && !ime.mModAlt && !ime.mModMeta && !ime.isCursorTouchingWord()
        ) {
            if (!ime.mPredicting) {
                ime.mPredicting = true
                ime.mComposing.setLength(0)
                ime.saveWordInHistory(ime.mBestWord)
                ime.mWord.reset()
            }
        }
        if (ime.mModCtrl || ime.mModAlt || ime.mModMeta) ime.commitTyped(ime.currentInputConnection, true)
        if (ime.mPredicting) {
            if (ime.isShiftCapsMode() && ime.mKeyboardSwitcher!!.isAlphabetMode() && ime.mComposing.isEmpty()) {
                ime.mWord.setFirstCharCapitalized(true)
            }
            ime.mComposing.append(primaryCode.toChar())
            ime.mWord.add(primaryCode, keyCodes ?: intArrayOf(primaryCode))
            val ic = ime.currentInputConnection
            if (ic != null) {
                if (ime.mWord.size() == 1) {
                    val editorInfo = ime.currentInputEditorInfo
                    if (editorInfo != null) ime.mWord.setAutoCapitalized(ime.getCursorCapsMode(ic, editorInfo) != 0)
                }
                ic.setComposingText(ime.mComposing, 1)
            }
            SessionDependencies.composingWord.value = ime.mComposing.toString()
            ime.mSuggestionCoordinator.postUpdateSuggestions()
        } else {
            ime.sendModifiableKeyChar(primaryCode.toChar())
        }
        ime.updateShiftKeyState(ime.currentInputEditorInfo)
        TextEntryState.typedCharacter(primaryCode.toChar(), ime.isWordSeparator(primaryCode))
    }

    fun handleSeparator(primaryCode: Int) {
        if (ime.mCandidateView != null && ime.mCandidateView!!.dismissAddToDictionaryHint()) {
            ime.mSuggestionCoordinator.postUpdateSuggestions()
        }
        var pickedDefault = false
        val ic = ime.currentInputConnection
        ic?.beginBatchEdit()
        ime.abortCorrection(false)
        if (ime.mPredicting) {
            if (ime.mAutoCorrectOn && primaryCode != '\''.code
                && (ime.mJustRevertedSeparator == null || ime.mJustRevertedSeparator!!.isEmpty()
                    || ime.mJustRevertedSeparator!![0].code != primaryCode)
            ) {
                pickedDefault = ime.mSuggestionPicker.pickDefaultSuggestion()
                if (primaryCode == ASCII_SPACE) {
                    if (ime.mAutoCorrectEnabled) ime.mJustAddedAutoSpace = true
                    else TextEntryState.manualTyped("")
                }
            } else {
                val acEngine = SessionDependencies.autocorrectEngine
                val learnEngine = SessionDependencies.learningEngine
                if (acEngine != null && learnEngine != null
                    && acEngine.aggressiveness != AutocorrectEngine.Aggressiveness.OFF
                    && ime.mComposing.isNotEmpty()
                ) {
                    val result = acEngine.getCorrection(ime.mComposing.toString(), learnEngine.getLearnedWords())
                    if (result is AutocorrectResult.Suggestion && result.autoApply) {
                        ic?.finishComposingText()
                        ic?.deleteSurroundingText(ime.mComposing.length, 0)
                        ic?.commitText(result.correction, 1)
                        ime.mPredicting = false
                        ime.mCommittedLength = result.correction.length
                        SessionDependencies.composingWord.value = ""
                        SessionDependencies.pendingCorrection.value = null
                        DevKeyLogger.text("autocorrect_applied", mapOf("action" to "applied", "level" to acEngine.aggressiveness.name.lowercase()))
                    } else if (result is AutocorrectResult.Suggestion) {
                        SessionDependencies.pendingCorrection.value = result
                        ime.commitTyped(ic, true)
                    } else ime.commitTyped(ic, true)
                } else ime.commitTyped(ic, true)
            }
        }
        if (ime.mJustAddedAutoSpace && primaryCode == ASCII_ENTER) {
            ime.removeTrailingSpace(); ime.mJustAddedAutoSpace = false
        }
        ime.sendModifiableKeyChar(primaryCode.toChar())
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED && primaryCode == ASCII_PERIOD) {
            ime.reswapPeriodAndSpace()
        }
        TextEntryState.typedCharacter(primaryCode.toChar(), true)
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED && primaryCode != ASCII_ENTER) {
            ime.swapPunctuationAndSpace()
        } else if (ime.isPredictionOn() && primaryCode == ASCII_SPACE) {
            ime.doubleSpace()
        }
        if (pickedDefault) TextEntryState.backToAcceptedDefault(ime.mWord.getTypedWord())
        ime.updateShiftKeyState(ime.currentInputEditorInfo)
        ic?.endBatchEdit()
        if (primaryCode == ASCII_SPACE) {
            DevKeyLogger.text("next_word_suggestions", mapOf(
                "source" to if (pickedDefault) "picked_default" else "space_only",
                "prev_word_length" to 0, "result_count" to 0
            ))
        }
    }

    fun commitTyped(inputConnection: InputConnection?, manual: Boolean) {
        if (ime.mPredicting) {
            ime.mPredicting = false
            if (ime.mComposing.isNotEmpty()) {
                inputConnection?.commitText(ime.mComposing, 1)
                ime.mCommittedLength = ime.mComposing.length
                if (manual) {
                    TextEntryState.manualTyped(ime.mComposing)
                } else {
                    TextEntryState.acceptedTyped(ime.mComposing)
                }
                ime.mSuggestionPicker.addToDictionaries(ime.mComposing, AutoDictionary.FREQUENCY_FOR_TYPED)
                val word = ime.mComposing.toString()
                SessionDependencies.learningEngine?.let { le ->
                    ime.serviceScope.launch(Dispatchers.IO) {
                        le.onWordCommitted(
                            word,
                            isCommand = SessionDependencies.commandModeDetector?.isCommandMode() == true,
                            contextApp = SessionDependencies.currentPackageName
                        )
                    }
                }
            }
            SessionDependencies.composingWord.value = ""
            SessionDependencies.pendingCorrection.value = null
            ime.mSuggestionCoordinator.updateSuggestions()
        }
    }

    fun revertLastWord(deleteChar: Boolean) {
        val length = ime.mComposing.length
        if (!ime.mPredicting && length > 0) {
            val ic = ime.currentInputConnection
            ime.mPredicting = true
            ime.mJustRevertedSeparator = ic?.getTextBeforeCursor(1, 0)
            if (deleteChar) ic?.deleteSurroundingText(1, 0)
            var toDelete = ime.mCommittedLength
            val toTheLeft = ic?.getTextBeforeCursor(ime.mCommittedLength, 0)
            if (toTheLeft != null && toTheLeft.isNotEmpty() && ime.isWordSeparator(toTheLeft[0].code)) {
                toDelete--
            }
            ic?.deleteSurroundingText(toDelete, 0)
            ic?.setComposingText(ime.mComposing, 1)
            TextEntryState.backspace()
            ime.mSuggestionCoordinator.postUpdateSuggestions()
        } else {
            ime.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            ime.mJustRevertedSeparator = null
        }
    }

}
