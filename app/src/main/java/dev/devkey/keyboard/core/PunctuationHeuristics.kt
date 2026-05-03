package dev.devkey.keyboard.core

import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationDecision
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationReason
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationTransform
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

internal class PunctuationHeuristics(
    private val icProvider: () -> InputConnection?,
    private val settings: SettingsRepository,
    private val updateShiftKeyState: () -> Unit
) {
    var suggestPuncList: MutableList<CharSequence>? = null

    fun swapPunctuationAndSpace() {
        val ic = icProvider() ?: return
        val lastTwo = ic.getTextBeforeCursor(2, 0)
        val decision = punctuationDecision(
            SmartTextPunctuationTransform.PUNCTUATION_SPACE_SWAP,
            lastTwo,
        )
        if (decision.apply) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(decision.deleteBeforeCursor, 0)
            ic.commitText(decision.replacement, 1)
            ic.endBatchEdit()
            updateShiftKeyState()
            DevKeyLogger.text("punc_space_swapped", emptyMap())
        }
    }

    fun reswapPeriodAndSpace() {
        val ic = icProvider() ?: return
        val lastThree = ic.getTextBeforeCursor(3, 0)
        val decision = punctuationDecision(
            SmartTextPunctuationTransform.PERIOD_RESWAP,
            lastThree,
        )
        if (decision.apply) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(decision.deleteBeforeCursor, 0)
            ic.commitText(decision.replacement, 1)
            ic.endBatchEdit()
            updateShiftKeyState()
            DevKeyLogger.text("period_reswapped", emptyMap())
        }
    }

    fun doubleSpace() {
        val ic = icProvider() ?: return
        val lastThree = ic.getTextBeforeCursor(3, 0)
        val decision = punctuationDecision(
            SmartTextPunctuationTransform.DOUBLE_SPACE_PERIOD,
            lastThree,
        )
        if (decision.apply) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(decision.deleteBeforeCursor, 0)
            ic.commitText(decision.replacement, 1)
            ic.endBatchEdit()
            updateShiftKeyState()
            DevKeyLogger.text("double_space_applied", mapOf("chars_removed" to decision.charsRemoved))
        }
    }

    fun maybeRemovePreviousPeriod(text: CharSequence) {
        val ic = icProvider() ?: return
        if (text.isEmpty()) return
        val lastOne = ic.getTextBeforeCursor(1, 0)
        val decision = punctuationDecision(
            SmartTextPunctuationTransform.REMOVE_PREVIOUS_PERIOD,
            lastOne,
        )
        if (text[0] == ASCII_PERIOD && decision.apply) {
            ic.deleteSurroundingText(decision.deleteBeforeCursor, 0)
        }
    }

    fun removeTrailingSpace() {
        val ic = icProvider() ?: return
        val lastOne = ic.getTextBeforeCursor(1, 0)
        val decision = punctuationDecision(
            SmartTextPunctuationTransform.REMOVE_TRAILING_SPACE,
            lastOne,
        )
        if (decision.apply) {
            ic.deleteSurroundingText(decision.deleteBeforeCursor, 0)
            DevKeyLogger.text("trailing_space_removed", emptyMap())
        }
    }

    private fun punctuationDecision(
        transform: SmartTextPunctuationTransform,
        contextBeforeCursor: CharSequence?,
    ): SmartTextPunctuationDecision =
        SessionDependencies.smartTextEngine?.punctuation(
            SmartTextPunctuationRequest(
                transform = transform,
                contextBeforeCursor = contextBeforeCursor,
                sentenceSeparators = sentenceSeparators ?: "",
            )
        ) ?: fallbackPunctuationDecision(transform, contextBeforeCursor)

    private fun fallbackPunctuationDecision(
        transform: SmartTextPunctuationTransform,
        contextBeforeCursor: CharSequence?,
    ): SmartTextPunctuationDecision =
        when (transform) {
            SmartTextPunctuationTransform.PUNCTUATION_SPACE_SWAP ->
                fallbackPunctuationSpaceSwap(contextBeforeCursor)
            SmartTextPunctuationTransform.PERIOD_RESWAP ->
                fallbackPeriodReswap(contextBeforeCursor)
            SmartTextPunctuationTransform.DOUBLE_SPACE_PERIOD ->
                fallbackDoubleSpace(contextBeforeCursor)
            SmartTextPunctuationTransform.REMOVE_TRAILING_SPACE ->
                fallbackRemoveTrailingSpace(contextBeforeCursor)
            SmartTextPunctuationTransform.REMOVE_PREVIOUS_PERIOD ->
                fallbackRemovePreviousPeriod(contextBeforeCursor)
        }

    private fun fallbackPunctuationSpaceSwap(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision {
        if (contextBeforeCursor == null || contextBeforeCursor.length < 2) return noPunctuationTransform()
        val space = contextBeforeCursor[0]
        val punctuation = contextBeforeCursor[1]
        return if (space == ASCII_SPACE && isSentenceSeparator(punctuation.code)) {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 2,
                replacement = "$punctuation ",
            )
        } else {
            noPunctuationTransform()
        }
    }

    private fun fallbackPeriodReswap(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision =
        if (contextBeforeCursor != null && contextBeforeCursor.length == 3
            && contextBeforeCursor[0] == ASCII_PERIOD
            && contextBeforeCursor[1] == ASCII_SPACE
            && contextBeforeCursor[2] == ASCII_PERIOD
        ) {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 3,
                replacement = " ..",
            )
        } else {
            noPunctuationTransform()
        }

    private fun fallbackDoubleSpace(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision =
        if (contextBeforeCursor != null && contextBeforeCursor.length == 3
            && contextBeforeCursor[0].isLetterOrDigit()
            && contextBeforeCursor[1] == ASCII_SPACE
            && contextBeforeCursor[2] == ASCII_SPACE
        ) {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 2,
                replacement = ". ",
                charsRemoved = 2,
            )
        } else {
            noPunctuationTransform()
        }

    private fun fallbackRemoveTrailingSpace(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision =
        if (contextBeforeCursor != null && contextBeforeCursor.length == 1 && contextBeforeCursor[0] == ASCII_SPACE) {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 1,
            )
        } else {
            noPunctuationTransform()
        }

    private fun fallbackRemovePreviousPeriod(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision =
        if (contextBeforeCursor != null && contextBeforeCursor.length == 1 && contextBeforeCursor[0] == ASCII_PERIOD) {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 1,
            )
        } else {
            noPunctuationTransform()
        }

    private fun noPunctuationTransform(): SmartTextPunctuationDecision =
        SmartTextPunctuationDecision(
            apply = false,
            reason = SmartTextPunctuationReason.UNAVAILABLE,
        )

    fun isSentenceSeparator(code: Int): Boolean {
        return sentenceSeparators?.indexOf(code.toChar())?.let { it >= 0 } ?: false
    }

    fun isSuggestedPunctuation(code: Int): Boolean {
        return settings.suggestedPunctuation.indexOf(code.toChar()) >= 0
    }

    fun initSuggestPuncList() {
        suggestPuncList = mutableListOf()
        var suggestPuncs = settings.suggestedPunctuation
        val defaultPuncs = defaultPunctuations
        if (suggestPuncs == defaultPuncs || suggestPuncs.isEmpty()) {
            suggestPuncs = actualPunctuations
        }
        for (i in suggestPuncs.indices) {
            suggestPuncList!!.add(suggestPuncs.subSequence(i, i + 1))
        }
    }

    var sentenceSeparators: String? = null
    var defaultPunctuations: String = ""
    var actualPunctuations: String = ""

    companion object {
        private val ASCII_SPACE = KeyCodes.ASCII_SPACE.toChar()
        private val ASCII_PERIOD = KeyCodes.ASCII_PERIOD.toChar()
    }
}
