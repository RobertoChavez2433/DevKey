package dev.devkey.keyboard.core

import android.util.Log
import dev.devkey.keyboard.dictionary.user.AutoDictionary
import dev.devkey.keyboard.EditingUtil
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.suggestion.word.WordAlternatives
import dev.devkey.keyboard.TextEntryState
import dev.devkey.keyboard.suggestion.word.WordComposer
import dev.devkey.keyboard.debug.DevKeyLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages suggestion computation, display, and next-word prediction.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class SuggestionCoordinator(private val ime: LatinIME) {

    fun postUpdateSuggestions() {
        ime.suggestionsJob?.cancel()
        ime.suggestionsJob = ime.serviceScope.launch {
            delay(100)
            updateSuggestions()
        }
    }

    fun postUpdateOldSuggestions() {
        ime.oldSuggestionsJob?.cancel()
        ime.oldSuggestionsJob = ime.serviceScope.launch {
            delay(300)
            setOldSuggestions()
        }
    }

    fun isPredictionWanted(): Boolean =
        (ime.mShowSuggestions || ime.mSuggestionForceOn) && !ime.suggestionsDisabled()

    fun isCandidateStripVisible(): Boolean = ime.isPredictionOn()

    fun clearSuggestions() {
        setSuggestions(null, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
    }

    fun setSuggestions(
        suggestions: List<CharSequence>?,
        completions: Boolean,
        typedWordValid: Boolean,
        haveMinimalSuggestion: Boolean
    ) {
        if (ime.mIsShowingHint) {
            ime.setCandidatesViewShown(true)
            ime.mIsShowingHint = false
        }
        ime.mCandidateView?.setSuggestions(suggestions, completions, typedWordValid, haveMinimalSuggestion)
    }

    fun updateSuggestions() {
        if (ime.mSuggest == null || !ime.isPredictionOn()) return
        if (!ime.mPredicting) {
            setNextSuggestions()
            return
        }
        showSuggestions(ime.mWord)
    }

    fun getTypedSuggestions(word: WordComposer): List<CharSequence> {
        return ime.mSuggest!!.getSuggestions(null, word, false, null)
    }

    fun showCorrections(alternatives: WordAlternatives) {
        val stringList = alternatives.getAlternatives()
        showSuggestions(stringList, alternatives.getOriginalWord(), false, false)
    }

    fun showSuggestions(word: WordComposer) {
        val prevWord = EditingUtil.getPreviousWord(ime.currentInputConnection, ime.mWordSeparators ?: "")
        val stringList = ime.mSuggest!!.getSuggestions(null, word, false, prevWord)
        val nextLettersFrequencies = ime.mSuggest!!.getNextLettersFrequencies()

        var correctionAvailable = !ime.mInputTypeNoAutoCorrect && ime.mSuggest!!.hasMinimalCorrection()
        val typedWord = word.getTypedWord() ?: ""
        var typedWordValid = ime.mSuggest!!.isValidWord(typedWord) ||
            (ime.preferCapitalization() && ime.mSuggest!!.isValidWord(typedWord.toString().lowercase()))
        if (ime.mCorrectionMode == Suggest.CORRECTION_FULL
            || ime.mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM
        ) {
            correctionAvailable = correctionAvailable or typedWordValid
        }
        correctionAvailable = correctionAvailable and !word.isMostlyCaps()
        correctionAvailable = correctionAvailable and !TextEntryState.isCorrecting()
        showSuggestions(stringList, typedWord, typedWordValid, correctionAvailable)
    }

    fun showSuggestions(
        stringList: List<CharSequence>?,
        typedWord: CharSequence?,
        typedWordValid: Boolean,
        correctionAvailable: Boolean
    ) {
        setSuggestions(stringList, completions = false, typedWordValid = typedWordValid, haveMinimalSuggestion = correctionAvailable)
        ime.mBestWord = if (stringList != null && stringList.isNotEmpty()) {
            if (correctionAvailable && !typedWordValid && stringList.size > 1) stringList[1] else typedWord
        } else null
        ime.setCandidatesViewShown(isCandidateStripVisible() || ime.mCompletionOn)
    }

    fun setOldSuggestions() {
        if (ime.mCandidateView != null && ime.mCandidateView!!.isShowingAddToDictionaryHint()) return
        val ic = ime.currentInputConnection ?: return
        if (!ime.mPredicting) {
            ime.abortCorrection(true)
            setNextSuggestions()
        } else {
            ime.abortCorrection(true)
        }
    }

    fun getLastCommittedWordBeforeCursor(): CharSequence? {
        val ic = ime.currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(64, 0) ?: return null
        if (before.isEmpty()) return null
        var end = before.length
        while (end > 0 && before[end - 1].isWhitespace()) end--
        if (end == 0) return null
        var start = end
        while (start > 0) {
            val c = before[start - 1]
            if (c.isLetterOrDigit() || c == '\'') start-- else break
        }
        if (start == end) return null
        return before.subSequence(start, end)
    }

    fun setNextSuggestions() {
        var emitSource = "no_prev_word"
        var emitLen = 0
        var emitCount = 0
        val suggestImpl = ime.mSuggest
        if (suggestImpl != null && ime.isPredictionOn()) {
            val prevWord = getLastCommittedWordBeforeCursor()
            if (prevWord != null) {
                val nextWords = suggestImpl.getNextWordSuggestions(prevWord)
                if (nextWords.isNotEmpty()) {
                    emitSource = "bigram_hit"; emitLen = prevWord.length; emitCount = nextWords.size
                    setSuggestions(nextWords, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
                } else {
                    emitSource = "bigram_miss"; emitLen = prevWord.length
                    setSuggestions(ime.mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
                }
            } else {
                setSuggestions(ime.mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
            }
        } else {
            setSuggestions(ime.mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
        }
        DevKeyLogger.text("next_word_suggestions", mapOf("prev_word_length" to emitLen, "result_count" to emitCount, "source" to emitSource))
    }

    fun onSelectionChanged(
        oldSelStart: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        if (ime.mComposing.isNotEmpty() && ime.mPredicting
            && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
            && ime.mLastSelectionStart != newSelStart
        ) {
            ime.mComposing.setLength(0)
            ime.mPredicting = false
            postUpdateSuggestions()
            TextEntryState.reset()
            ime.currentInputConnection?.finishComposingText()
        } else if (!ime.mPredicting && !ime.mJustAccepted) {
            when (TextEntryState.getState()) {
                TextEntryState.State.ACCEPTED_DEFAULT -> {
                    TextEntryState.reset()
                    ime.mJustAddedAutoSpace = false
                }
                TextEntryState.State.SPACE_AFTER_PICKED -> {
                    ime.mJustAddedAutoSpace = false
                }
                else -> { /* no-op */ }
            }
        }
        ime.mJustAccepted = false

        ime.mLastSelectionStart = newSelStart
        ime.mLastSelectionEnd = newSelEnd

        if (ime.mReCorrectionEnabled) {
            if (ime.mKeyboardSwitcher != null && ime.isKeyboardVisible()) {
                if (ime.isPredictionOn()
                    && ime.mJustRevertedSeparator == null
                    && (candidatesStart == candidatesEnd
                        || newSelStart != oldSelStart || TextEntryState.isCorrecting())
                    && (newSelStart < newSelEnd - 1 || !ime.mPredicting)
                ) {
                    if (ime.isCursorTouchingWord() || ime.mLastSelectionStart < ime.mLastSelectionEnd) {
                        postUpdateOldSuggestions()
                    } else {
                        ime.abortCorrection(false)
                        if (ime.mCandidateView != null
                            && ime.mSuggestPuncList != ime.mCandidateView!!.getSuggestions() as Any?
                            && !ime.mCandidateView!!.isShowingAddToDictionaryHint()
                        ) {
                            setNextSuggestions()
                        }
                    }
                }
            }
        }
    }
}
