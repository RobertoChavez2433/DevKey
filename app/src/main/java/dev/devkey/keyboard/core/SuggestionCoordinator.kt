package dev.devkey.keyboard.core

import android.view.inputmethod.CompletionInfo
import dev.devkey.keyboard.core.text.EditingUtil
import dev.devkey.keyboard.suggestion.word.TypedWordAlternatives
import dev.devkey.keyboard.suggestion.word.WordAlternatives
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.suggestion.word.WordComposer
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.smarttext.SmartTextInputKind
import dev.devkey.keyboard.feature.smarttext.SmartTextNextWordRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestion
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionKind
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionRequest
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
        if (!state.isPredictionOn(isPredictionWanted())) return
        if (!state.mPredicting) {
            setNextSuggestions()
            return
        }
        showSuggestions(state.mWord)
    }

    fun getTypedSuggestions(word: WordComposer): List<CharSequence> {
        return buildSmartTextCandidateState(word).suggestions
    }

    fun showCorrections(alternatives: WordAlternatives) {
        val stringList = alternatives.getAlternatives()
        showSuggestions(stringList, alternatives.getOriginalWord(), false, false)
    }

    fun showSuggestions(word: WordComposer) {
        val candidateState = buildSmartTextCandidateState(word)
        showSuggestions(
            candidateState.suggestions,
            candidateState.typedWord,
            candidateState.typedWordValid,
            candidateState.correctionAvailable
        )
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
        val smartTextEngine = SessionDependencies.smartTextEngine
        if (smartTextEngine != null && state.isPredictionOn(isPredictionWanted())) {
            val prevWord = getLastCommittedWordBeforeCursor()
            if (prevWord != null) {
                val result = smartTextEngine.nextWordSuggestions(
                    SmartTextNextWordRequest(
                        previousWord = prevWord.toString(),
                        inputKind = currentSmartTextInputKind(prevWord.toString()),
                        maxResults = NEXT_WORD_MAX_RESULTS,
                    )
                )
                val nextWords = result.suggestions.map { it as CharSequence }
                emitSource = result.source.wireName
                emitLen = prevWord.length
                if (nextWords.isNotEmpty()) {
                    emitCount = nextWords.size
                    setSuggestions(nextWords, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
                    SessionDependencies.nextWordSuggestions.value = nextWords
                } else {
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

    private fun buildSmartTextCandidateState(word: WordComposer): CandidateSuggestionState {
        val typedWord = word.getTypedWord()?.toString().orEmpty()
        if (typedWord.isEmpty()) {
            return CandidateSuggestionState(
                suggestions = emptyList(),
                typedWord = typedWord,
                typedWordValid = false,
                correctionAvailable = false,
            )
        }

        val inputKind = currentSmartTextInputKind(typedWord)
        val smartSuggestions = SessionDependencies.smartTextEngine
            ?.candidateSuggestions(
                SmartTextSuggestionRequest(
                    currentWord = typedWord,
                    inputKind = inputKind,
                    maxResults = CANDIDATE_STRIP_MAX_RESULTS,
                )
            )
            ?: emptyList()
        val typedWordValid = isKnownWord(typedWord) ||
            (state.preferCapitalization() && isKnownWord(typedWord.lowercase()))
        val correctionAvailable = smartSuggestions.firstOrNull()?.kind == SmartTextSuggestionKind.AUTOCORRECT &&
            !typedWordValid &&
            !word.isMostlyCaps() &&
            !TextEntryState.isCorrecting() &&
            inputKind == SmartTextInputKind.NORMAL
        val suggestions = buildList<CharSequence> {
            add(typedWord)
            addAll(smartSuggestions.map(SmartTextSuggestion::word).filterNot {
                it.equals(typedWord, ignoreCase = true)
            })
        }
        return CandidateSuggestionState(
            suggestions = suggestions,
            typedWord = typedWord,
            typedWordValid = typedWordValid,
            correctionAvailable = correctionAvailable,
        )
    }

    private fun currentSmartTextInputKind(currentWord: String): SmartTextInputKind = when {
        state.mPasswordText -> SmartTextInputKind.PASSWORD
        SessionDependencies.commandModeDetector?.isCommandMode() == true -> SmartTextInputKind.COMMAND
        state.mInputTypeNoAutoCorrect -> SmartTextInputKind.CODE_LIKE
        currentWord.contains('@') -> SmartTextInputKind.EMAIL
        currentWord.startsWith("http", ignoreCase = true) -> SmartTextInputKind.URL
        currentWord.any(Char::isDigit) && currentWord.any(Char::isLetter) -> SmartTextInputKind.CODE_LIKE
        currentWord.any { it == '_' || it == '/' || it == '\\' } -> SmartTextInputKind.CODE_LIKE
        else -> SmartTextInputKind.NORMAL
    }

    private fun isKnownWord(word: CharSequence?): Boolean {
        val text = word?.toString() ?: return false
        if (text.isEmpty()) return false
        val dictionaryProvider = SessionDependencies.dictionaryProvider
        if (dictionaryProvider?.isValidWord(text) == true) return true
        return SessionDependencies.learningEngine
            ?.getCustomWords()
            ?.any { it.equals(text, ignoreCase = true) }
            ?: false
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

    private data class CandidateSuggestionState(
        val suggestions: List<CharSequence>,
        val typedWord: CharSequence,
        val typedWordValid: Boolean,
        val correctionAvailable: Boolean,
    )

    companion object {
        private const val CANDIDATE_STRIP_MAX_RESULTS = 6
        private const val NEXT_WORD_MAX_RESULTS = 3
    }
}
