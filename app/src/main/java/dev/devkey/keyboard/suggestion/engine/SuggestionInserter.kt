// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard.suggestion.engine
import dev.devkey.keyboard.*
import dev.devkey.keyboard.dictionary.base.*
import dev.devkey.keyboard.dictionary.loader.PluginManager

import android.util.Log
import kotlin.math.roundToInt

/**
 * Handles inserting candidates into the ranked suggestion lists, deduplication,
 * and pool-based garbage collection of StringBuilder instances.
 *
 * Implements [Dictionary.WordCallback] so that dictionary lookups in
 * [SuggestionPipeline] and [NextWordSuggester] can call back directly into this class.
 */
internal class SuggestionInserter(private val state: SuggestState) : Dictionary.WordCallback {

    // -------------------------------------------------------------------------
    // Dictionary.WordCallback
    // -------------------------------------------------------------------------

    override fun addWord(
        word: CharArray, wordOffset: Int, wordLength: Int, freq: Int,
        dicTypeId: Int, dataType: Dictionary.DataType
    ): Boolean {
        var frequency = freq
        val suggestions: ArrayList<CharSequence>
        val priorities: IntArray
        val prefMaxSuggestions: Int
        if (dataType == Dictionary.DataType.BIGRAM) {
            suggestions = state.bigramSuggestions
            priorities = state.bigramPriorities
            prefMaxSuggestions = SuggestState.PREF_MAX_BIGRAMS
        } else {
            suggestions = state.suggestions
            priorities = state.priorities
            prefMaxSuggestions = state.prefMaxSuggestions
        }

        var pos = 0

        // Check if it's the same word, only caps are different.
        if (compareCaseInsensitive(state.lowerOriginalWord, word, wordOffset, wordLength)) {
            pos = 0
        } else {
            if (dataType == Dictionary.DataType.UNIGRAM) {
                // Check if the word was already added before (by bigram data).
                val bigramSuggestion = searchBigramSuggestion(word, wordOffset, wordLength)
                if (bigramSuggestion >= 0) {
                    // Turn freq from bigram into multiplier specified in Suggest constants.
                    val multiplier = (state.bigramPriorities[bigramSuggestion].toDouble() /
                            Suggest.MAXIMUM_BIGRAM_FREQUENCY) *
                            (Suggest.BIGRAM_MULTIPLIER_MAX - Suggest.BIGRAM_MULTIPLIER_MIN) +
                            Suggest.BIGRAM_MULTIPLIER_MIN
                    frequency = (frequency * multiplier).roundToInt()
                }
            }

            // Check the last one's priority and bail early.
            if (priorities[prefMaxSuggestions - 1] >= frequency) return true
            while (pos < prefMaxSuggestions) {
                if (priorities[pos] < frequency ||
                    (priorities[pos] == frequency && wordLength < suggestions[pos].length)
                ) {
                    break
                }
                pos++
            }
        }
        if (pos >= prefMaxSuggestions) {
            return true
        }

        priorities.copyInto(priorities, pos + 1, pos, pos + prefMaxSuggestions - pos - 1)
        priorities[pos] = frequency
        val poolSize = state.stringPool.size
        val sb = if (poolSize > 0) {
            state.stringPool.removeAt(poolSize - 1) as StringBuilder
        } else {
            StringBuilder(Suggest.APPROX_MAX_WORD_LENGTH)
        }
        sb.setLength(0)
        if (state.isAllUpperCase) {
            sb.append(String(word, wordOffset, wordLength).uppercase())
        } else if (state.isFirstCharCapitalized) {
            sb.append(word[wordOffset].uppercaseChar())
            if (wordLength > 1) {
                sb.append(word, wordOffset + 1, wordLength - 1)
            }
        } else {
            sb.append(word, wordOffset, wordLength)
        }
        suggestions.add(pos, sb)
        if (suggestions.size > prefMaxSuggestions) {
            val garbage = suggestions.removeAt(prefMaxSuggestions)
            if (garbage is StringBuilder) {
                state.stringPool.add(garbage)
            }
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Removes duplicate entries from [state.suggestions] in-place. */
    fun removeDupes() {
        val suggestions = state.suggestions
        if (suggestions.size < 2) return
        var i = 1
        // Don't cache suggestions.size(), since we may be removing items.
        while (i < suggestions.size) {
            val cur = suggestions[i]
            for (j in 0 until i) {
                val previous = suggestions[j]
                if (cur == previous) {
                    removeFromSuggestions(i)
                    i--
                    break
                }
            }
            i++
        }
    }

    private fun removeFromSuggestions(index: Int) {
        val garbage = state.suggestions.removeAt(index)
        if (garbage is StringBuilder) {
            state.stringPool.add(garbage)
        }
    }

    /**
     * Reclaims [StringBuilder] instances from the tail of [suggestions] back into [state.stringPool],
     * then clears the list.
     */
    fun collectGarbage(suggestions: ArrayList<CharSequence>, prefMaxSuggestions: Int) {
        var poolSize = state.stringPool.size
        var garbageSize = suggestions.size
        while (poolSize < prefMaxSuggestions && garbageSize > 0) {
            val garbage = suggestions[garbageSize - 1]
            if (garbage is StringBuilder) {
                state.stringPool.add(garbage)
                poolSize++
            }
            garbageSize--
        }
        if (poolSize == prefMaxSuggestions + 1) {
            Log.w("Suggest", "String pool got too big: $poolSize")
        }
        suggestions.clear()
    }

    // -------------------------------------------------------------------------
    // Private comparison helpers
    // -------------------------------------------------------------------------

    private fun compareCaseInsensitive(
        lowerOriginalWord: String,
        word: CharArray, offset: Int, length: Int
    ): Boolean {
        val originalLength = lowerOriginalWord.length
        if (originalLength == length && word[offset].isUpperCase()) {
            for (i in 0 until originalLength) {
                if (lowerOriginalWord[i] != word[offset + i].lowercaseChar()) {
                    return false
                }
            }
            return true
        }
        return false
    }

    private fun searchBigramSuggestion(word: CharArray, offset: Int, length: Int): Int {
        // TODO This is almost O(n^2). Might need fix.
        val bigramSuggestSize = state.bigramSuggestions.size
        for (i in 0 until bigramSuggestSize) {
            if (state.bigramSuggestions[i].length == length) {
                var chk = true
                for (j in 0 until length) {
                    if (state.bigramSuggestions[i][j] != word[offset + j]) {
                        chk = false
                        break
                    }
                }
                if (chk) return i
            }
        }
        return -1
    }
}
