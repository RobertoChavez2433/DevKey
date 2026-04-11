package dev.devkey.keyboard.core

import android.view.inputmethod.EditorInfo
import dev.devkey.keyboard.dictionary.user.AutoDictionary
import dev.devkey.keyboard.core.text.EditingUtil
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.ASCII_SPACE
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.suggestion.word.WordAlternatives
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.suggestion.word.TypedWordAlternatives
import dev.devkey.keyboard.suggestion.word.WordComposer
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles suggestion picking, acceptance, and dictionary writes.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class SuggestionPicker(
    private val state: ImeState,
    private val coordinator: SuggestionCoordinator,
    private val icProvider: InputConnectionProvider,
    private val candidateViewHost: CandidateViewHost,
    private val updateShiftKeyState: (EditorInfo?) -> Unit,
    private val sendSpace: () -> Unit,
    private val onKey: (Int, IntArray?, Int, Int) -> Unit,
    private val getShiftState: () -> Int,
) {

    fun pickDefaultSuggestion(): Boolean {
        if (state.suggestionsJob?.isActive == true) {
            state.suggestionsJob?.cancel()
            coordinator.updateSuggestions()
        }
        if (state.mBestWord != null && state.mBestWord!!.isNotEmpty()) {
            TextEntryState.acceptedDefault(state.mWord.getTypedWord(), state.mBestWord!!)
            state.mJustAccepted = true
            pickSuggestion(state.mBestWord!!, false)
            addToDictionaries(state.mBestWord!!, AutoDictionary.FREQUENCY_FOR_TYPED)
            return true
        }
        return false
    }

    fun pickSuggestionManually(index: Int, suggestion: CharSequence) {
        val suggestions = state.mCandidateView!!.getSuggestions()
        val correcting = TextEntryState.isCorrecting()
        val ic = icProvider.inputConnection
        ic?.beginBatchEdit()
        if (state.mCompletionOn && state.mCompletions != null && index >= 0
            && index < state.mCompletions!!.size
        ) {
            val ci = state.mCompletions!![index]
            ic?.commitCompletion(ci)
            state.mCommittedLength = suggestion.length
            state.mCandidateView?.clear()
            updateShiftKeyState(icProvider.editorInfo)
            ic?.endBatchEdit()
            return
        }
        if (suggestion.length == 1
            && (state.isWordSeparator(suggestion[0].code) || state.isSuggestedPunctuation(suggestion[0].code))
        ) {
            val primaryCode = suggestion[0]
            onKey(primaryCode.code, intArrayOf(primaryCode.code), -1, -1)
            ic?.endBatchEdit()
            return
        }
        state.mJustAccepted = true
        pickSuggestion(suggestion, correcting)
        if (index == 0) {
            addToDictionaries(suggestion, AutoDictionary.FREQUENCY_FOR_PICKED)
        } else {
            addToBigramDictionary(suggestion, 1)
        }
        TextEntryState.acceptedSuggestion(state.mComposing.toString(), suggestion)
        if (state.mAutoSpace && !correcting) {
            sendSpace()
            state.mJustAddedAutoSpace = true
        }
        val showingAddToDictionaryHint = index == 0
            && state.mCorrectionMode > 0 && !state.mSuggest!!.isValidWord(suggestion)
            && !state.mSuggest!!.isValidWord(suggestion.toString().lowercase())
        if (!correcting) {
            TextEntryState.typedCharacter(ASCII_SPACE.toChar(), true)
            coordinator.setNextSuggestions()
        } else if (!showingAddToDictionaryHint) {
            coordinator.clearSuggestions()
            coordinator.postUpdateOldSuggestions()
        }
        if (showingAddToDictionaryHint) {
            state.mCandidateView!!.showAddToDictionaryHint(suggestion)
        }
        ic?.endBatchEdit()
    }

    fun pickSuggestion(suggestion: CharSequence, correcting: Boolean) {
        var actualSuggestion = suggestion
        val shiftState = getShiftState()
        if (shiftState == Keyboard.SHIFT_LOCKED || shiftState == Keyboard.SHIFT_CAPS_LOCKED) {
            actualSuggestion = suggestion.toString().uppercase()
        }
        val ic = icProvider.inputConnection
        if (ic != null) {
            ic.commitText(actualSuggestion, 1)
        }
        coordinator.saveWordInHistory(actualSuggestion)
        state.mPredicting = false
        state.mCommittedLength = actualSuggestion.length
        val word = actualSuggestion.toString()
        SessionDependencies.learningEngine?.let { le ->
            state.serviceScope.launch(Dispatchers.IO) {
                le.onWordCommitted(
                    word,
                    isCommand = SessionDependencies.commandModeDetector?.isCommandMode() == true,
                    contextApp = SessionDependencies.currentPackageName
                )
            }
        }
        SessionDependencies.composingWord.value = ""
        SessionDependencies.pendingCorrection.value = null
        if (!correcting) coordinator.setNextSuggestions()
        updateShiftKeyState(icProvider.editorInfo)
    }

    fun applyTypedAlternatives(touching: EditingUtil.SelectedWord): Boolean {
        var foundWord: WordComposer? = null
        var alternatives: WordAlternatives? = null
        for (entry in state.mWordHistory) {
            if (entry.getChosenWord() == touching.word) {
                if (entry is TypedWordAlternatives) foundWord = entry.word
                alternatives = entry
                break
            }
        }
        val touchingWord = touching.word
        if (foundWord == null && touchingWord != null
            && (state.mSuggest!!.isValidWord(touchingWord)
                || state.mSuggest!!.isValidWord(touchingWord.toString().lowercase()))
        ) {
            foundWord = WordComposer()
            for (i in touchingWord.indices) {
                foundWord.add(touchingWord[i].code, intArrayOf(touchingWord[i].code))
            }
            foundWord.setFirstCharCapitalized(touchingWord[0].isUpperCase())
        }
        if (foundWord != null || alternatives != null) {
            if (alternatives == null) {
                alternatives = TypedWordAlternatives(touching.word, foundWord, coordinator::getTypedSuggestions)
            }
            coordinator.showCorrections(alternatives)
            if (foundWord != null) state.mWord = WordComposer(foundWord) else state.mWord.reset()
            return true
        }
        return false
    }

    fun addToDictionaries(suggestion: CharSequence, frequencyDelta: Int) =
        checkAddToDictionary(suggestion, frequencyDelta, false)

    fun addToBigramDictionary(suggestion: CharSequence, frequencyDelta: Int) =
        checkAddToDictionary(suggestion, frequencyDelta, true)

    private fun checkAddToDictionary(suggestion: CharSequence?, frequencyDelta: Int, addToBigramDictionary: Boolean) {
        if (suggestion == null || suggestion.length < 1) return
        if (state.mCorrectionMode != Suggest.CORRECTION_FULL && state.mCorrectionMode != Suggest.CORRECTION_FULL_BIGRAM) return
        if (!addToBigramDictionary
            && state.mAutoDictionary!!.isValidWord(suggestion)
            || (!state.mSuggest!!.isValidWord(suggestion.toString())
                && !state.mSuggest!!.isValidWord(suggestion.toString().lowercase()))
        ) {
            state.mAutoDictionary!!.addWord(suggestion.toString(), frequencyDelta)
        }
        if (state.mUserBigramDictionary != null) {
            val prevWord = EditingUtil.getPreviousWord(icProvider.inputConnection, state.mSentenceSeparators ?: "")
            if (!prevWord.isNullOrEmpty()) {
                state.mUserBigramDictionary!!.addBigrams(prevWord.toString(), suggestion.toString())
            }
        }
    }
}
