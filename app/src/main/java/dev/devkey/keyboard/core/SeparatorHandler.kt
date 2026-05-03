package dev.devkey.keyboard.core

import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.ASCII_ENTER
import dev.devkey.keyboard.ASCII_PERIOD
import dev.devkey.keyboard.ASCII_SPACE
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.feature.prediction.AutocorrectResult
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

internal class SeparatorHandler(
    private val state: ImeState,
    private val icProvider: InputConnectionProvider,
    private val suggestionCoordinator: SuggestionCoordinator,
    private val suggestionPicker: SuggestionPicker,
    private val modifierHandler: ModifierHandler,
    private val keyEventSender: KeyEventSender,
    private val puncHeuristics: PunctuationHeuristics,
    private val commitTyped: (InputConnection?, Boolean) -> Unit
) {
    fun handle(primaryCode: Int) {
        dismissDictionaryHint()
        val ic = icProvider.inputConnection
        ic?.beginBatchEdit()
        try {
            suggestionCoordinator.abortCorrection(false)
            val pickedDefault = acceptCurrentPrediction(primaryCode, ic)
            removeAutoSpaceBeforeEnter(primaryCode)
            sendSeparator(primaryCode)
            applyPunctuationHeuristics(primaryCode)
            if (pickedDefault) TextEntryState.backToAcceptedDefault(state.mWord.getTypedWord())
            modifierHandler.updateShiftKeyState(icProvider.editorInfo)
            maybeSetNextSuggestions(primaryCode)
        } finally {
            ic?.endBatchEdit()
        }
    }

    private fun dismissDictionaryHint() {
        if (state.mCandidateView != null && state.mCandidateView!!.dismissAddToDictionaryHint()) {
            suggestionCoordinator.postUpdateSuggestions()
        }
    }

    private fun acceptCurrentPrediction(primaryCode: Int, ic: InputConnection?): Boolean {
        if (!state.mPredicting) return false
        return if (shouldPickDefaultSuggestion(primaryCode)) {
            pickDefaultSuggestion(primaryCode)
        } else {
            acceptTypedOrCorrectedWord(primaryCode, ic)
        }
    }

    private fun shouldPickDefaultSuggestion(primaryCode: Int): Boolean =
        state.mAutoCorrectOn &&
            primaryCode != '\''.code &&
            (
                state.mJustRevertedSeparator == null ||
                    state.mJustRevertedSeparator!!.isEmpty() ||
                    state.mJustRevertedSeparator!![0].code != primaryCode
                )

    private fun pickDefaultSuggestion(primaryCode: Int): Boolean {
        val pickedDefault = suggestionPicker.pickDefaultSuggestion()
        if (primaryCode == ASCII_SPACE) {
            if (state.mAutoCorrectEnabled) {
                state.mJustAddedAutoSpace = true
            } else {
                TextEntryState.manualTyped("")
            }
        }
        return pickedDefault
    }

    private fun acceptTypedOrCorrectedWord(primaryCode: Int, ic: InputConnection?): Boolean {
        val typedWord = state.mComposing.toString()
        val result = findAutocorrectResult(typedWord, primaryCode)
        when {
            result is AutocorrectResult.Suggestion && result.autoApply ->
                applyAutocorrect(ic, typedWord, result)
            result is AutocorrectResult.Suggestion ->
                keepTypedWordWithPendingSuggestion(ic, typedWord, result)
            else ->
                keepTypedWord(ic, typedWord)
        }
        state.mJustAccepted = true
        if (primaryCode == ASCII_SPACE) state.mJustAddedAutoSpace = true
        return true
    }

    private fun findAutocorrectResult(typedWord: String, primaryCode: Int): AutocorrectResult? {
        val acEngine = SessionDependencies.autocorrectEngine ?: return null
        val learnEngine = SessionDependencies.learningEngine ?: return null
        if (acEngine.aggressiveness == AutocorrectEngine.Aggressiveness.OFF || state.mComposing.isEmpty()) {
            return null
        }
        val revertedSeparator = state.mJustRevertedSeparator
        if (!revertedSeparator.isNullOrEmpty() && revertedSeparator[0].code == primaryCode) {
            return null
        }
        return acEngine.getCorrection(typedWord, learnEngine.getCustomWords())
    }

    private fun applyAutocorrect(
        ic: InputConnection?,
        typedWord: String,
        result: AutocorrectResult.Suggestion
    ) {
        ic?.finishComposingText()
        ic?.deleteSurroundingText(state.mComposing.length, 0)
        ic?.commitText(result.correction, 1)
        state.mPredicting = false
        state.mCommittedLength = result.correction.length
        SessionDependencies.composingWord.value = ""
        SessionDependencies.pendingCorrection.value = null
        DevKeyLogger.text(
            "autocorrect_applied",
            mapOf("action" to "applied", "level" to currentAutocorrectLevel())
        )
        TextEntryState.acceptedDefault(typedWord, result.correction)
    }

    private fun keepTypedWordWithPendingSuggestion(
        ic: InputConnection?,
        typedWord: String,
        result: AutocorrectResult.Suggestion
    ) {
        commitTyped(ic, true)
        SessionDependencies.pendingCorrection.value = result
        TextEntryState.acceptedDefault(typedWord, typedWord)
    }

    private fun keepTypedWord(ic: InputConnection?, typedWord: String) {
        commitTyped(ic, true)
        TextEntryState.acceptedDefault(typedWord, typedWord)
    }

    private fun currentAutocorrectLevel(): String =
        SessionDependencies.autocorrectEngine?.aggressiveness?.name?.lowercase() ?: "unknown"

    private fun removeAutoSpaceBeforeEnter(primaryCode: Int) {
        if (state.mJustAddedAutoSpace && primaryCode == ASCII_ENTER) {
            puncHeuristics.removeTrailingSpace()
            state.mJustAddedAutoSpace = false
        }
    }

    private fun sendSeparator(primaryCode: Int) {
        keyEventSender.sendModifiableKeyChar(primaryCode.toChar())
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED &&
            primaryCode == ASCII_PERIOD
        ) {
            puncHeuristics.reswapPeriodAndSpace()
        }
        TextEntryState.typedCharacter(primaryCode.toChar(), true)
    }

    private fun applyPunctuationHeuristics(primaryCode: Int) {
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED &&
            primaryCode != ASCII_ENTER
        ) {
            puncHeuristics.swapPunctuationAndSpace()
        } else if (state.isPredictionOn(suggestionCoordinator.isPredictionWanted()) && primaryCode == ASCII_SPACE) {
            puncHeuristics.doubleSpace()
        }
    }

    private fun maybeSetNextSuggestions(primaryCode: Int) {
        if (!state.mPredicting &&
            primaryCode == ASCII_SPACE &&
            state.isPredictionOn(suggestionCoordinator.isPredictionWanted())
        ) {
            suggestionCoordinator.setNextSuggestions()
        }
    }
}
