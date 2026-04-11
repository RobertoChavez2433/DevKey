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
import dev.devkey.keyboard.dictionary.loader.PluginManager
import dev.devkey.keyboard.suggestion.word.WordComposer

import android.text.AutoText
import android.view.View

/**
 * Core suggestion generation pipeline.
 *
 * Gathers candidates from all dictionaries, scores and ranks them, applies
 * AutoText corrections, and returns the final ordered list.
 *
 * Delegates insertion/ranking callbacks to [SuggestionInserter] and
 * commonality checks to [NextWordSuggester].
 */
internal class SuggestionPipeline(
    private val state: SuggestState,
    private val inserter: SuggestionInserter,
    private val nextWordSuggester: NextWordSuggester,
    private val mainDict: BinaryDictionary,
    private val dictionaries: DictionaryRefs,
    private val correctionModeProvider: () -> Int,
    private val autoTextEnabledProvider: () -> Boolean
) {

    /**
     * Returns a list of words that match the list of character codes passed in.
     * This list will be overwritten the next time this function is called.
     *
     * @param view a view for retrieving the context for AutoText
     * @param wordComposer contains what is currently being typed
     * @param includeTypedWordIfValid whether to include the typed word when it is valid
     * @param prevWordForBigram previous word (used only for bigram)
     * @return list of suggestions.
     */
    fun getSuggestions(
        view: View?,
        wordComposer: WordComposer,
        includeTypedWordIfValid: Boolean,
        prevWordForBigram: CharSequence?
    ): List<CharSequence> {
        val correctionMode = correctionModeProvider()

        state.haveCorrection = false
        state.isFirstCharCapitalized = wordComposer.isFirstCharCapitalized()
        state.isAllUpperCase = wordComposer.isAllUpperCase()
        inserter.collectGarbage(state.suggestions, state.prefMaxSuggestions)
        state.priorities.fill(0)
        state.nextLettersFrequencies.fill(0)

        // Save a lowercase version of the original word.
        state.originalWord = wordComposer.getTypedWord()
        if (state.originalWord != null) {
            val originalWordString = state.originalWord.toString()
            state.originalWord = originalWordString
            state.lowerOriginalWord = originalWordString.lowercase()
        } else {
            state.lowerOriginalWord = ""
        }

        var mutablePrevWordForBigram = prevWordForBigram

        if (wordComposer.size() == 1 && (correctionMode == Suggest.CORRECTION_FULL_BIGRAM ||
                    correctionMode == Suggest.CORRECTION_BASIC)
        ) {
            // At first character typed, search only the bigrams.
            state.bigramPriorities.fill(0)
            inserter.collectGarbage(state.bigramSuggestions, SuggestState.PREF_MAX_BIGRAMS)

            if (!mutablePrevWordForBigram.isNullOrEmpty()) {
                val lowerPrevWord: CharSequence = mutablePrevWordForBigram.toString().lowercase()
                if (mainDict.isValidWord(lowerPrevWord)) {
                    mutablePrevWordForBigram = lowerPrevWord
                }
                dictionaries.userBigramDictionary?.getBigrams(
                    wordComposer, mutablePrevWordForBigram!!, inserter,
                    state.nextLettersFrequencies
                )
                dictionaries.contactsDictionary?.getBigrams(
                    wordComposer, mutablePrevWordForBigram!!, inserter,
                    state.nextLettersFrequencies
                )
                mainDict.getBigrams(
                    wordComposer, mutablePrevWordForBigram!!, inserter,
                    state.nextLettersFrequencies
                )
                val currentChar = wordComposer.getTypedWord()!![0]
                val currentCharUpper = currentChar.uppercaseChar()
                var count = 0
                val bigramSuggestionSize = state.bigramSuggestions.size
                for (i in 0 until bigramSuggestionSize) {
                    if (state.bigramSuggestions[i][0] == currentChar ||
                        state.bigramSuggestions[i][0] == currentCharUpper
                    ) {
                        val poolSize = state.stringPool.size
                        val sb = if (poolSize > 0) {
                            state.stringPool.removeAt(poolSize - 1) as StringBuilder
                        } else {
                            StringBuilder(Suggest.APPROX_MAX_WORD_LENGTH)
                        }
                        sb.setLength(0)
                        sb.append(state.bigramSuggestions[i])
                        state.suggestions.add(count++, sb)
                        if (count > state.prefMaxSuggestions) break
                    }
                }
            }
        } else if (wordComposer.size() > 1) {
            // At second character typed, search the unigrams (scores being affected by bigrams).
            if (dictionaries.userDictionary != null || dictionaries.contactsDictionary != null) {
                dictionaries.userDictionary?.getWords(wordComposer, inserter, state.nextLettersFrequencies)
                dictionaries.contactsDictionary?.getWords(wordComposer, inserter, state.nextLettersFrequencies)

                if (state.suggestions.size > 0 && isValidWord(state.originalWord!!) &&
                    (correctionMode == Suggest.CORRECTION_FULL ||
                            correctionMode == Suggest.CORRECTION_FULL_BIGRAM)
                ) {
                    state.haveCorrection = true
                }
            }
            mainDict.getWords(wordComposer, inserter, state.nextLettersFrequencies)
            if ((correctionMode == Suggest.CORRECTION_FULL ||
                        correctionMode == Suggest.CORRECTION_FULL_BIGRAM) &&
                state.suggestions.size > 0
            ) {
                state.haveCorrection = true
            }
        }
        if (state.originalWord != null) {
            state.suggestions.add(0, state.originalWord.toString())
        }

        // Check if the first suggestion has a minimum number of characters in common.
        if (wordComposer.size() > 1 && state.suggestions.size > 1 &&
            (correctionMode == Suggest.CORRECTION_FULL ||
                    correctionMode == Suggest.CORRECTION_FULL_BIGRAM)
        ) {
            if (!nextWordSuggester.haveSufficientCommonality(
                    state.lowerOriginalWord, state.suggestions[1]
                )
            ) {
                state.haveCorrection = false
            }
        }
        if (autoTextEnabledProvider() && view != null) {
            var i = 0
            var max = 6
            // Don't autotext the suggestions from the dictionaries.
            if (correctionMode == Suggest.CORRECTION_BASIC) max = 1
            while (i < state.suggestions.size && i < max) {
                val suggestedWord = state.suggestions[i].toString().lowercase()
                val autoText = AutoText.get(suggestedWord, 0, suggestedWord.length, view)
                // Is there an AutoText correction?
                var canAdd = autoText != null
                // Is that correction already the current prediction (or original word)?
                canAdd = canAdd && autoText != state.suggestions[i]
                // Is that correction already the next predicted word?
                if (canAdd && i + 1 < state.suggestions.size && correctionMode != Suggest.CORRECTION_BASIC) {
                    canAdd = canAdd && autoText != state.suggestions[i + 1]
                }
                if (canAdd) {
                    state.haveCorrection = true
                    state.suggestions.add(i + 1, autoText!!)
                    i++
                }
                i++
            }
        }
        inserter.removeDupes()
        return state.suggestions
    }

    private fun isValidWord(word: CharSequence): Boolean {
        if (word.isEmpty()) return false
        return mainDict.isValidWord(word) ||
                (dictionaries.userDictionary != null && dictionaries.userDictionary!!.isValidWord(word)) ||
                (dictionaries.autoDictionary != null && dictionaries.autoDictionary!!.isValidWord(word)) ||
                (dictionaries.contactsDictionary != null && dictionaries.contactsDictionary!!.isValidWord(word))
    }
}
