package dev.devkey.keyboard.core

import dev.devkey.keyboard.AutoDictionary
import dev.devkey.keyboard.EditingUtil
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.ASCII_SPACE
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.Suggest
import dev.devkey.keyboard.TextEntryState
import dev.devkey.keyboard.TypedWordAlternatives
import dev.devkey.keyboard.WordComposer
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles suggestion picking, acceptance, and dictionary writes.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class SuggestionPicker(
    private val ime: LatinIME,
    private val coordinator: SuggestionCoordinator
) {

    fun pickDefaultSuggestion(): Boolean {
        if (ime.suggestionsJob?.isActive == true) {
            ime.suggestionsJob?.cancel()
            coordinator.updateSuggestions()
        }
        if (ime.mBestWord != null && ime.mBestWord!!.isNotEmpty()) {
            TextEntryState.acceptedDefault(ime.mWord.getTypedWord(), ime.mBestWord!!)
            ime.mJustAccepted = true
            pickSuggestion(ime.mBestWord!!, false)
            addToDictionaries(ime.mBestWord!!, AutoDictionary.FREQUENCY_FOR_TYPED)
            return true
        }
        return false
    }

    fun pickSuggestionManually(index: Int, suggestion: CharSequence) {
        val suggestions = ime.mCandidateView!!.getSuggestions()
        val correcting = TextEntryState.isCorrecting()
        val ic = ime.currentInputConnection
        ic?.beginBatchEdit()
        if (ime.mCompletionOn && ime.mCompletions != null && index >= 0
            && index < ime.mCompletions!!.size
        ) {
            val ci = ime.mCompletions!![index]
            ic?.commitCompletion(ci)
            ime.mCommittedLength = suggestion.length
            ime.mCandidateView?.clear()
            ime.updateShiftKeyState(ime.currentInputEditorInfo)
            ic?.endBatchEdit()
            return
        }
        if (suggestion.length == 1
            && (ime.isWordSeparator(suggestion[0].code) || ime.isSuggestedPunctuation(suggestion[0].code))
        ) {
            val primaryCode = suggestion[0]
            ime.onKey(primaryCode.code, intArrayOf(primaryCode.code), -1, -1)
            ic?.endBatchEdit()
            return
        }
        ime.mJustAccepted = true
        pickSuggestion(suggestion, correcting)
        if (index == 0) {
            addToDictionaries(suggestion, AutoDictionary.FREQUENCY_FOR_PICKED)
        } else {
            addToBigramDictionary(suggestion, 1)
        }
        TextEntryState.acceptedSuggestion(ime.mComposing.toString(), suggestion)
        if (ime.mAutoSpace && !correcting) {
            ime.sendSpace()
            ime.mJustAddedAutoSpace = true
        }
        val showingAddToDictionaryHint = index == 0
            && ime.mCorrectionMode > 0 && !ime.mSuggest!!.isValidWord(suggestion)
            && !ime.mSuggest!!.isValidWord(suggestion.toString().lowercase())
        if (!correcting) {
            TextEntryState.typedCharacter(ASCII_SPACE.toChar(), true)
            coordinator.setNextSuggestions()
        } else if (!showingAddToDictionaryHint) {
            coordinator.clearSuggestions()
            coordinator.postUpdateOldSuggestions()
        }
        if (showingAddToDictionaryHint) {
            ime.mCandidateView!!.showAddToDictionaryHint(suggestion)
        }
        ic?.endBatchEdit()
    }

    fun pickSuggestion(suggestion: CharSequence, correcting: Boolean) {
        var actualSuggestion = suggestion
        val shiftState = ime.getShiftState()
        if (shiftState == Keyboard.SHIFT_LOCKED || shiftState == Keyboard.SHIFT_CAPS_LOCKED) {
            actualSuggestion = suggestion.toString().uppercase()
        }
        val ic = ime.currentInputConnection
        if (ic != null) {
            ic.commitText(actualSuggestion, 1)
        }
        ime.saveWordInHistory(actualSuggestion)
        ime.mPredicting = false
        ime.mCommittedLength = actualSuggestion.length
        val word = actualSuggestion.toString()
        SessionDependencies.learningEngine?.let { le ->
            ime.serviceScope.launch(Dispatchers.IO) {
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
        ime.updateShiftKeyState(ime.currentInputEditorInfo)
    }

    fun applyTypedAlternatives(touching: EditingUtil.SelectedWord): Boolean {
        var foundWord: WordComposer? = null
        var alternatives: dev.devkey.keyboard.WordAlternatives? = null
        for (entry in ime.mWordHistory) {
            if (entry.getChosenWord() == touching.word) {
                if (entry is TypedWordAlternatives) foundWord = entry.word
                alternatives = entry
                break
            }
        }
        val touchingWord = touching.word
        if (foundWord == null && touchingWord != null
            && (ime.mSuggest!!.isValidWord(touchingWord)
                || ime.mSuggest!!.isValidWord(touchingWord.toString().lowercase()))
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
            if (foundWord != null) ime.mWord = WordComposer(foundWord) else ime.mWord.reset()
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
        if (ime.mCorrectionMode != Suggest.CORRECTION_FULL && ime.mCorrectionMode != Suggest.CORRECTION_FULL_BIGRAM) return
        if (!addToBigramDictionary
            && ime.mAutoDictionary!!.isValidWord(suggestion)
            || (!ime.mSuggest!!.isValidWord(suggestion.toString())
                && !ime.mSuggest!!.isValidWord(suggestion.toString().lowercase()))
        ) {
            ime.mAutoDictionary!!.addWord(suggestion.toString(), frequencyDelta)
        }
        if (ime.mUserBigramDictionary != null) {
            val prevWord = EditingUtil.getPreviousWord(ime.currentInputConnection, ime.mSentenceSeparators ?: "")
            if (!prevWord.isNullOrEmpty()) {
                ime.mUserBigramDictionary!!.addBigrams(prevWord.toString(), suggestion.toString())
            }
        }
    }
}
