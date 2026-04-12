package dev.devkey.keyboard.core

import android.view.inputmethod.CompletionInfo
import dev.devkey.keyboard.core.text.EditingUtil
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.suggestion.word.TypedWordAlternatives
import dev.devkey.keyboard.suggestion.word.WordAlternatives
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.suggestion.word.WordComposer
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages suggestion computation, display, and next-word prediction.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class SuggestionCoordinator(
    private val state: ImeState,
    private val icProvider: InputConnectionProvider,
    private val candidateViewHost: CandidateViewHost
) {

    fun postUpdateSuggestions() {
        state.suggestionsJob?.cancel()
        state.suggestionsJob = state.serviceScope.launch {
            delay(100)
            updateSuggestions()
        }
    }

    fun postUpdateOldSuggestions() {
        state.oldSuggestionsJob?.cancel()
        state.oldSuggestionsJob = state.serviceScope.launch {
            delay(300)
            setOldSuggestions()
        }
    }

    fun isPredictionWanted(): Boolean =
        (state.mShowSuggestions || state.mSuggestionForceOn) && !state.suggestionsDisabled()

    fun isCandidateStripVisible(): Boolean = state.isPredictionOn(isPredictionWanted())

    fun clearSuggestions() {
        setSuggestions(null, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
    }

    fun setSuggestions(
        suggestions: List<CharSequence>?,
        completions: Boolean,
        typedWordValid: Boolean,
        haveMinimalSuggestion: Boolean
    ) {
        if (state.mIsShowingHint) {
            candidateViewHost.setCandidatesViewShown(true)
            state.mIsShowingHint = false
        }
        state.mCandidateView?.setSuggestions(suggestions, completions, typedWordValid, haveMinimalSuggestion)
    }

    fun updateSuggestions() {
        if (state.mSuggest == null || !state.isPredictionOn(isPredictionWanted())) return
        if (!state.mPredicting) {
            setNextSuggestions()
            return
        }
        showSuggestions(state.mWord)
    }

    fun getTypedSuggestions(word: WordComposer): List<CharSequence> {
        return state.mSuggest!!.getSuggestions(null, word, false, null)
    }

    fun showCorrections(alternatives: WordAlternatives) {
        val stringList = alternatives.getAlternatives()
        showSuggestions(stringList, alternatives.getOriginalWord(), false, false)
    }

    fun showSuggestions(word: WordComposer) {
        val prevWord = EditingUtil.getPreviousWord(icProvider.inputConnection, state.mWordSeparators ?: "")
        val stringList = state.mSuggest!!.getSuggestions(null, word, false, prevWord)
        val nextLettersFrequencies = state.mSuggest!!.getNextLettersFrequencies()

        var correctionAvailable = !state.mInputTypeNoAutoCorrect && state.mSuggest!!.hasMinimalCorrection()
        val typedWord = word.getTypedWord() ?: ""
        var typedWordValid = state.mSuggest!!.isValidWord(typedWord) ||
            (state.preferCapitalization() && state.mSuggest!!.isValidWord(typedWord.toString().lowercase()))
        if (state.mCorrectionMode == Suggest.CORRECTION_FULL
            || state.mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM
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
        state.mBestWord = if (stringList != null && stringList.isNotEmpty()) {
            if (correctionAvailable && !typedWordValid && stringList.size > 1) stringList[1] else typedWord
        } else null
        candidateViewHost.setCandidatesViewShown(isCandidateStripVisible() || state.mCompletionOn)
    }

    fun setOldSuggestions() {
        if (state.mCandidateView != null && state.mCandidateView!!.isShowingAddToDictionaryHint()) return
        val ic = icProvider.inputConnection ?: return
        if (!state.mPredicting) {
            abortCorrection(true)
            setNextSuggestions()
        } else {
            abortCorrection(true)
        }
    }

    fun saveWordInHistory(result: CharSequence?) {
        if (state.mWord.size() <= 1) {
            state.mWord.reset()
            return
        }
        if (result.isNullOrEmpty()) return
        val resultCopy = result.toString()
        val entry = TypedWordAlternatives(resultCopy, WordComposer(state.mWord), ::getTypedSuggestions)
        state.mWordHistory.add(entry)
    }

    fun abortCorrection(force: Boolean) {
        if (force || TextEntryState.isCorrecting()) {
            icProvider.inputConnection?.finishComposingText()
            clearSuggestions()
        }
    }

    fun getLastCommittedWordBeforeCursor(): CharSequence? {
        val ic = icProvider.inputConnection ?: return null
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
        val suggestImpl = state.mSuggest
        if (suggestImpl != null && state.isPredictionOn(isPredictionWanted())) {
            val prevWord = getLastCommittedWordBeforeCursor()
            if (prevWord != null) {
                val nextWords = suggestImpl.getNextWordSuggestions(prevWord)
                if (nextWords.isNotEmpty()) {
                    emitSource = "bigram_hit"; emitLen = prevWord.length; emitCount = nextWords.size
                    setSuggestions(nextWords, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
                    SessionDependencies.nextWordSuggestions.value = nextWords
                } else {
                    emitSource = "bigram_miss"; emitLen = prevWord.length
                    setSuggestions(state.mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
                    SessionDependencies.nextWordSuggestions.value = emptyList()
                }
            } else {
                setSuggestions(state.mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
                SessionDependencies.nextWordSuggestions.value = emptyList()
            }
        } else {
            setSuggestions(state.mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
            SessionDependencies.nextWordSuggestions.value = emptyList()
        }
        DevKeyLogger.text("next_word_suggestions", mapOf("prev_word_length" to emitLen, "result_count" to emitCount, "source" to emitSource))
    }

    fun onSelectionChanged(
        oldSelStart: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        if (state.mComposing.isNotEmpty() && state.mPredicting
            && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
            && state.mLastSelectionStart != newSelStart
        ) {
            state.mComposing.setLength(0)
            state.mPredicting = false
            postUpdateSuggestions()
            TextEntryState.reset()
            icProvider.inputConnection?.finishComposingText()
        } else if (!state.mPredicting && !state.mJustAccepted) {
            when (TextEntryState.getState()) {
                TextEntryState.State.ACCEPTED_DEFAULT -> {
                    TextEntryState.reset()
                    state.mJustAddedAutoSpace = false
                }
                TextEntryState.State.SPACE_AFTER_PICKED -> {
                    state.mJustAddedAutoSpace = false
                }
                else -> { /* no-op */ }
            }
        }
        state.mJustAccepted = false

        state.mLastSelectionStart = newSelStart
        state.mLastSelectionEnd = newSelEnd

        if (state.mReCorrectionEnabled) {
            if (state.mKeyboardSwitcher != null && candidateViewHost.isKeyboardVisible()) {
                if (state.isPredictionOn(isPredictionWanted())
                    && state.mJustRevertedSeparator == null
                    && (candidatesStart == candidatesEnd
                        || newSelStart != oldSelStart || TextEntryState.isCorrecting())
                    && (newSelStart < newSelEnd - 1 || !state.mPredicting)
                ) {
                    if (EditingUtil.isCursorTouchingWord(
                            icProvider.inputConnection,
                            state.mWordSeparators ?: "",
                            state.mSuggestPuncList
                        ) || state.mLastSelectionStart < state.mLastSelectionEnd
                    ) {
                        postUpdateOldSuggestions()
                    } else {
                        abortCorrection(false)
                        if (state.mCandidateView != null
                            && state.mSuggestPuncList != state.mCandidateView!!.getSuggestions() as Any?
                            && !state.mCandidateView!!.isShowingAddToDictionaryHint()
                        ) {
                            setNextSuggestions()
                        }
                    }
                }
            }
        }
    }

    fun onDisplayCompletions(completions: Array<CompletionInfo>?, showCandidates: () -> Unit) {
        if (state.mCompletionOn) {
            state.mCompletions = completions
            if (completions == null) {
                clearSuggestions()
                return
            }
            val stringList = mutableListOf<CharSequence>()
            for (ci in completions) {
                ci.text?.let { stringList.add(it) }
            }
            setSuggestions(stringList, completions = true, typedWordValid = true, haveMinimalSuggestion = true)
            state.mBestWord = null
            showCandidates()
        }
    }
}
