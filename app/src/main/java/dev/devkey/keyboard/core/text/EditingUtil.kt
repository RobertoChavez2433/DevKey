/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package dev.devkey.keyboard.core.text

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.util.regex.Pattern

/**
 * Utility methods to deal with editing text through an InputConnection.
 */
object EditingUtil {

    /** Number of characters we want to look back in order to identify the previous word. */
    private const val LOOKBACK_CHARACTER_NUM = 15

    private val spaceRegex: Pattern = Pattern.compile("\\s+")

    private fun getCursorPosition(connection: InputConnection): Int {
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return -1
        return extracted.startOffset + extracted.selectionStart
    }

    /**
     * @param connection connection to the current text field.
     * @param separators characters which may separate words
     * @param range the range object to store the result into
     * @return the word that surrounds the cursor, including up to one trailing
     *   separator. For example, if the field contains "he|llo world", where |
     *   represents the cursor, then "hello " will be returned.
     */
    fun getWordAtCursor(connection: InputConnection, separators: String, range: Range?): String? {
        val r = getWordRangeAtCursor(connection, separators, range) ?: return null
        return r.word
    }

    /**
     * Represents a range of text, relative to the current cursor position.
     */
    class Range(
        /** Characters before selection start. */
        var charsBefore: Int = 0,
        /** Characters after selection start, including one trailing word separator. */
        var charsAfter: Int = 0,
        /** The actual characters that make up a word. */
        var word: String? = null
    ) {
        init {
            if (charsBefore < 0 || charsAfter < 0) {
                throw IndexOutOfBoundsException()
            }
        }
    }

    private fun getWordRangeAtCursor(
        connection: InputConnection?,
        sep: String?,
        range: Range?
    ): Range? {
        if (connection == null || sep == null) return null

        val before = connection.getTextBeforeCursor(1000, 0) ?: return null
        val after = connection.getTextAfterCursor(1000, 0) ?: return null

        // Find first word separator before the cursor
        var start = before.length
        while (start > 0 && !isWhitespace(before[start - 1].code, sep)) start--

        // Find last word separator after the cursor
        var end = -1
        while (++end < after.length && !isWhitespace(after[end].code, sep)) { /* advance */ }

        val cursor = getCursorPosition(connection)
        if (start >= 0 && cursor + end <= after.length + before.length) {
            val word = before.toString().substring(start, before.length) +
                    after.toString().substring(0, end)

            val returnRange = range ?: Range()
            returnRange.charsBefore = before.length - start
            returnRange.charsAfter = end
            returnRange.word = word
            return returnRange
        }

        return null
    }

    private fun isWhitespace(code: Int, whitespace: String): Boolean {
        return whitespace.indexOf(code.toChar()) >= 0
    }

    fun getPreviousWord(connection: InputConnection?, sentenceSeparators: String): CharSequence? {
        val prev = connection?.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0) ?: return null
        val w = spaceRegex.split(prev)
        if (w.size >= 2 && w[w.size - 2].isNotEmpty()) {
            val lastChar = w[w.size - 2].last()
            if (sentenceSeparators.contains(lastChar.toString())) {
                return null
            }
            return w[w.size - 2]
        }
        return null
    }

    class SelectedWord {
        var start: Int = 0
        var end: Int = 0
        var word: CharSequence? = null
    }

    /**
     * Checks if the cursor is inside a word or the current selection is a whole word.
     */
    fun getWordAtCursorOrSelection(
        ic: InputConnection,
        selStart: Int,
        selEnd: Int,
        wordSeparators: String
    ): SelectedWord? {
        if (selStart == selEnd) {
            // There is just a cursor, so get the word at the cursor
            val range = Range()
            val touching = getWordAtCursor(ic, wordSeparators, range)
            if (!touching.isNullOrEmpty()) {
                val selWord = SelectedWord()
                selWord.word = touching
                selWord.start = selStart - range.charsBefore
                selWord.end = selEnd + range.charsAfter
                return selWord
            }
        } else {
            // Is the previous character empty or a word separator?
            val charsBefore = ic.getTextBeforeCursor(1, 0)
            if (!isWordBoundary(charsBefore, wordSeparators)) return null

            // Is the next character empty or a word separator?
            val charsAfter = ic.getTextAfterCursor(1, 0)
            if (!isWordBoundary(charsAfter, wordSeparators)) return null

            // Extract the selection alone
            val touching = ic.getSelectedText(0)
            if (touching.isNullOrEmpty()) return null

            // Is any part of the selection a separator?
            for (i in 0 until touching!!.length) {
                if (wordSeparators.contains(touching.subSequence(i, i + 1))) {
                    return null
                }
            }

            val selWord = SelectedWord()
            selWord.start = selStart
            selWord.end = selEnd
            selWord.word = touching
            return selWord
        }
        return null
    }

    private fun isWordBoundary(singleChar: CharSequence?, wordSeparators: String): Boolean {
        return singleChar.isNullOrEmpty() || wordSeparators.contains(singleChar!!)
    }

}
