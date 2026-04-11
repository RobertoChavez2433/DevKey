package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.suggestion.word.WordComposer

/**
 * Dictionary provider that uses a modern Kotlin [TrieDictionary] as the
 * primary source and falls back to the legacy [Suggest] JNI pipeline.
 *
 * @param suggest The existing Suggest instance (nullable — may not be initialized yet).
 */
open class DictionaryProvider(private var suggest: Suggest?) {

    /** Modern Kotlin-native trie dictionary. Set after async load completes. */
    @Volatile
    var trieDictionary: TrieDictionary? = null

    fun updateSuggest(newSuggest: Suggest?) {
        suggest = newSuggest
    }

    /**
     * Get word suggestions for the given typed word.
     * Prefers the modern TrieDictionary; falls back to legacy Suggest.
     */
    open fun getSuggestions(word: String, maxResults: Int = 3): List<String> {
        if (word.isEmpty()) return emptyList()

        // Primary: modern trie dictionary
        val trie = trieDictionary
        if (trie != null && trie.size > 0) {
            val results = trie.getSuggestions(word, maxResults)
            if (results.isNotEmpty()) {
                return results.map { it.first }
            }
        }

        // Fallback: legacy Suggest/JNI pipeline
        val s = suggest ?: return emptyList()
        return try {
            val wordComposer = WordComposer()
            for (char in word) {
                val code = char.code
                wordComposer.add(code, intArrayOf(code))
            }
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
     * Fuzzy matching for autocorrect: find words within edit distance 2
     * of the typed word, sorted by frequency.
     */
    open fun getFuzzyCorrections(word: String, maxResults: Int = 3): List<String> {
        if (word.isEmpty()) return emptyList()
        val trie = trieDictionary
        if (trie != null && trie.size > 0) {
            return trie.getFuzzyMatches(word, maxDistance = 2, maxResults = maxResults)
                .map { it.first }
        }
        return emptyList()
    }

    /**
     * Check whether the given word is a valid dictionary word.
     */
    open fun isValidWord(word: String): Boolean {
        if (word.isEmpty()) return false

        // Primary: modern trie dictionary
        val trie = trieDictionary
        if (trie != null && trie.size > 0) {
            return trie.isValidWord(word)
        }

        // Fallback: legacy Suggest
        val s = suggest ?: return false
        return try {
            s.isValidWord(word)
        } catch (e: Exception) {
            false
        }
    }
}
