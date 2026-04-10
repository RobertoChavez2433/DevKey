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

package dev.devkey.keyboard

/**
 * Shared mutable state for the suggestion pipeline.
 *
 * All four suggestion classes (Suggest, SuggestionPipeline, NextWordSuggester,
 * SuggestionInserter) operate on this single bag of state so that the original
 * behaviour — where addWord() callbacks mutate the same arrays that getSuggestions()
 * reads — is preserved without any public/internal leakage on Suggest itself.
 */
internal class SuggestState(initialMaxSuggestions: Int) {

    companion object {
        const val PREF_MAX_BIGRAMS = 60
    }

    var prefMaxSuggestions: Int = initialMaxSuggestions

    var priorities: IntArray = IntArray(prefMaxSuggestions)
    var bigramPriorities: IntArray = IntArray(PREF_MAX_BIGRAMS)

    // Handle predictive correction for only the first 1280 characters for performance reasons.
    val nextLettersFrequencies: IntArray = IntArray(1280)

    val suggestions: ArrayList<CharSequence> = ArrayList()
    val bigramSuggestions: ArrayList<CharSequence> = ArrayList()

    // Object pool — reused StringBuilders to reduce GC pressure.
    val stringPool: ArrayList<CharSequence> = ArrayList()

    var haveCorrection: Boolean = false
    var originalWord: CharSequence? = null
    var lowerOriginalWord: String = ""

    var isFirstCharCapitalized: Boolean = false
    var isAllUpperCase: Boolean = false

    /** A permanently empty composer used by next-word bigram lookups. */
    val emptyComposer: WordComposer = WordComposer()

    fun resizePriorities() {
        priorities = IntArray(prefMaxSuggestions)
        bigramPriorities = IntArray(PREF_MAX_BIGRAMS)
    }
}
