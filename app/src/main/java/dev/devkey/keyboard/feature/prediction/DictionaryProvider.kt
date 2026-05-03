package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardDictionary

/**
 * Dictionary provider backed by the imported smart-text donor dictionary.
 */
open class DictionaryProvider {

    @Volatile
    var donorDictionary: AnySoftKeyboardDictionary? = null

    fun hasDictionary(): Boolean = (donorDictionary?.size ?: 0) > 0

    fun activeDictionarySource(): String =
        donorDictionary?.metadata?.sourceName ?: "none"

    /**
     * Get word suggestions for the given typed word.
     */
    open fun getSuggestions(word: String, maxResults: Int = 3): List<String> {
        if (word.isEmpty()) return emptyList()
        return donorDictionary
            ?.getSuggestions(word, maxResults)
            ?.map { it.first }
            ?: emptyList()
    }

    /**
     * Fuzzy matching for autocorrect: find words within edit distance 2
     * of the typed word, sorted by frequency.
     */
    open fun getFuzzyCorrections(word: String, maxResults: Int = 3): List<String> {
        if (word.isEmpty()) return emptyList()
        return donorDictionary
            ?.getFuzzyCorrections(word, maxDistance = 2, maxResults = maxResults)
            ?.map { it.first }
            ?: emptyList()
    }

    /**
     * Check whether the given word is a valid dictionary word.
     */
    open fun isValidWord(word: String): Boolean {
        if (word.isEmpty()) return false
        return donorDictionary?.isValidWord(word) ?: false
    }
}
