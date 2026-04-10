package dev.devkey.keyboard.core

import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.keyboard.KeyCodes

internal class PunctuationHeuristics(
    private val icProvider: () -> InputConnection?,
    private val settings: SettingsRepository,
    private val updateShiftKeyState: () -> Unit
) {
    var suggestPuncList: MutableList<CharSequence>? = null

    fun swapPunctuationAndSpace() {
        val ic = icProvider() ?: return
        val lastTwo = ic.getTextBeforeCursor(2, 0)
        if (lastTwo != null && lastTwo.length == 2
            && lastTwo[0] == ASCII_SPACE
            && isSentenceSeparator(lastTwo[1].code)
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(2, 0)
            ic.commitText("${lastTwo[1]} ", 1)
            ic.endBatchEdit()
            updateShiftKeyState()
        }
    }

    fun reswapPeriodAndSpace() {
        val ic = icProvider() ?: return
        val lastThree = ic.getTextBeforeCursor(3, 0)
        if (lastThree != null && lastThree.length == 3
            && lastThree[0] == ASCII_PERIOD
            && lastThree[1] == ASCII_SPACE
            && lastThree[2] == ASCII_PERIOD
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(3, 0)
            ic.commitText(" ..", 1)
            ic.endBatchEdit()
            updateShiftKeyState()
        }
    }

    fun doubleSpace(correctionMode: Int) {
        if (correctionMode == CORRECTION_NONE) return
        val ic = icProvider() ?: return
        val lastThree = ic.getTextBeforeCursor(3, 0)
        if (lastThree != null && lastThree.length == 3
            && Character.isLetterOrDigit(lastThree[0])
            && lastThree[1] == ASCII_SPACE
            && lastThree[2] == ASCII_SPACE
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(2, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            updateShiftKeyState()
        }
    }

    fun maybeRemovePreviousPeriod(text: CharSequence) {
        val ic = icProvider() ?: return
        if (text.isEmpty()) return
        val lastOne = ic.getTextBeforeCursor(1, 0)
        if (lastOne != null && lastOne.length == 1
            && lastOne[0] == ASCII_PERIOD
            && text[0] == ASCII_PERIOD
        ) {
            ic.deleteSurroundingText(1, 0)
        }
    }

    fun removeTrailingSpace() {
        val ic = icProvider() ?: return
        val lastOne = ic.getTextBeforeCursor(1, 0)
        if (lastOne != null && lastOne.length == 1 && lastOne[0] == ASCII_SPACE) {
            ic.deleteSurroundingText(1, 0)
        }
    }

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
        const val CORRECTION_NONE = 0
    }
}
