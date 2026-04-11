/*
 * Copyright (C) 2008 The Android Open Source Project
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

package dev.devkey.keyboard.suggestion.engine
import dev.devkey.keyboard.*
import dev.devkey.keyboard.dictionary.base.*
import dev.devkey.keyboard.dictionary.expandable.ExpandableDictionary
import dev.devkey.keyboard.dictionary.loader.PluginManager
import dev.devkey.keyboard.suggestion.word.WordComposer

/**
 * Handles next-word / bigram-only suggestion when the user has just committed
 * a word and pressed space (zero characters typed so far).
 *
 * FROM SPEC: §4.3 "predictive next-word after space".
 *
 * NOTE: BinaryDictionary.getBigrams guards getCodesAt(0) with
 * `if (codesSize > 0)` and dictionary.cpp skips checkFirstCharacter when
 * mInputLength == 0, so all three dictionaries can participate in next-word
 * prediction with an empty [WordComposer].
 */
internal class NextWordSuggester(
    private val state: SuggestState,
    private val inserter: SuggestionInserter,
    private val mainDict: BinaryDictionary,
    private val dictionaries: DictionaryRefs
) {

    /**
     * Returns up to [SuggestState.prefMaxSuggestions] bigram candidates ordered by
     * frequency for the word that follows [prevWord].
     *
     * Returns an empty list when [prevWord] is null or empty.
     */
    fun getNextWordSuggestions(prevWord: CharSequence?): List<CharSequence> {
        if (prevWord.isNullOrEmpty()) return emptyList()

        state.isFirstCharCapitalized = false
        state.isAllUpperCase = false
        state.lowerOriginalWord = ""
        state.originalWord = null
        state.nextLettersFrequencies.fill(0)
        state.bigramPriorities.fill(0)
        inserter.collectGarbage(state.bigramSuggestions, SuggestState.PREF_MAX_BIGRAMS)
        inserter.collectGarbage(state.suggestions, state.prefMaxSuggestions)

        var lookupPrev: CharSequence = prevWord
        val lowerPrevWord = prevWord.toString().lowercase()
        if (mainDict.isValidWord(lowerPrevWord)) {
            lookupPrev = lowerPrevWord
        }

        dictionaries.userBigramDictionary?.getBigrams(
            state.emptyComposer, lookupPrev, inserter, state.nextLettersFrequencies
        )
        dictionaries.contactsDictionary?.getBigrams(
            state.emptyComposer, lookupPrev, inserter, state.nextLettersFrequencies
        )
        mainDict.getBigrams(
            state.emptyComposer, lookupPrev, inserter, state.nextLettersFrequencies
        )

        val cap = minOf(state.bigramSuggestions.size, state.prefMaxSuggestions)
        val result = ArrayList<CharSequence>(cap)
        for (i in 0 until cap) {
            result.add(state.bigramSuggestions[i].toString())
        }
        return result
    }

    /**
     * Returns true if [original] and [suggestion] share enough common characters
     * to be considered plausible corrections of each other.
     *
     * Used in [SuggestionPipeline] to suppress spurious auto-corrections.
     */
    fun haveSufficientCommonality(original: String, suggestion: CharSequence): Boolean {
        val originalLength = original.length
        val suggestionLength = suggestion.length
        val minLength = minOf(originalLength, suggestionLength)
        if (minLength <= 2) return true
        var matching = 0
        var lessMatching = 0 // Count matches if we skip one character
        for (i in 0 until minLength) {
            val origChar = ExpandableDictionary.toLowerCase(original[i])
            if (origChar == ExpandableDictionary.toLowerCase(suggestion[i])) {
                matching++
                lessMatching++
            } else if (i + 1 < suggestionLength &&
                origChar == ExpandableDictionary.toLowerCase(suggestion[i + 1])
            ) {
                lessMatching++
            }
        }
        matching = maxOf(matching, lessMatching)

        return if (minLength <= 4) {
            matching >= 2
        } else {
            matching > minLength / 2
        }
    }
}
