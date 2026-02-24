package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.Suggest
import dev.devkey.keyboard.WordComposer

/**
 * Kotlin-friendly wrapper for the legacy Suggest/BinaryDictionary subsystem.
 *
 * Provides a simplified API for getting word suggestions and checking word
 * validity, abstracting away the WordComposer / View dependencies.
 *
 * @param suggest The existing Suggest instance (nullable — may not be initialized yet).
 */
class DictionaryProvider(private var suggest: Suggest?) {

    /**
     * Update the underlying Suggest reference (e.g., when LatinIME reinitializes).
     */
    fun updateSuggest(newSuggest: Suggest?) {
        suggest = newSuggest
    }

    /**
     * Get word suggestions for the given typed word.
     *
     * @param word The word currently being typed.
     * @param maxResults Maximum number of suggestions to return.
     * @return List of suggested words, or empty list if unavailable.
     */
    fun getSuggestions(word: String, maxResults: Int = 3): List<String> {
        val s = suggest ?: return emptyList()
        if (word.isEmpty()) return emptyList()

        return try {
            // Build a WordComposer from the typed characters
            val wordComposer = WordComposer()
            for (char in word) {
                val code = char.code
                wordComposer.add(code, intArrayOf(code))
            }

            // Get suggestions — pass null view and no bigram context
            val suggestions = s.getSuggestions(null, wordComposer, false, null)
            suggestions
                ?.filterNotNull()
                ?.map { it.toString() }
                ?.filter { it.isNotEmpty() && it != word }
                ?.take(maxResults)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check whether the given word is a valid dictionary word.
     *
     * @param word The word to check.
     * @return true if the word is recognized by any dictionary, false otherwise.
     */
    fun isValidWord(word: String): Boolean {
        val s = suggest ?: return false
        if (word.isEmpty()) return false
        return try {
            s.isValidWord(word)
        } catch (e: Exception) {
            false
        }
    }
}
