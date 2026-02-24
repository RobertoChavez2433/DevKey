package dev.devkey.keyboard.feature.prediction

import kotlin.math.min

/**
 * Autocorrect engine that uses dictionary suggestions and edit distance
 * to decide whether a typed word should be auto-corrected.
 *
 * @param dictionaryProvider Source of dictionary lookups.
 */
class AutocorrectEngine(private val dictionaryProvider: DictionaryProvider) {

    /**
     * Controls how aggressively the engine corrects typos.
     */
    enum class Aggressiveness {
        /** No autocorrect — only show suggestions. */
        OFF,
        /** Correct obvious typos (edit distance <= 2), user confirms. */
        MILD,
        /** Auto-apply corrections without user confirmation. */
        AGGRESSIVE
    }

    var aggressiveness: Aggressiveness = Aggressiveness.MILD

    /**
     * Determine whether the typed word should be corrected.
     *
     * @param typed The word as typed by the user.
     * @param learnedWords Words the user has previously committed (should not be corrected).
     * @return An [AutocorrectResult] indicating whether a correction is suggested.
     */
    fun getCorrection(typed: String, learnedWords: Set<String>): AutocorrectResult {
        if (aggressiveness == Aggressiveness.OFF) return AutocorrectResult.None
        if (typed.isEmpty()) return AutocorrectResult.None

        // User has typed this word before — don't correct it
        if (typed.lowercase() in learnedWords.map { it.lowercase() }.toSet()) {
            return AutocorrectResult.None
        }

        // Word is already valid — no correction needed
        if (dictionaryProvider.isValidWord(typed)) return AutocorrectResult.None

        // Get dictionary suggestions
        val suggestions = dictionaryProvider.getSuggestions(typed, maxResults = 3)
        if (suggestions.isEmpty()) return AutocorrectResult.None

        // Check if the top suggestion is close enough
        val topSuggestion = suggestions.first()
        val distance = editDistance(typed.lowercase(), topSuggestion.lowercase())

        return if (distance <= 2) {
            AutocorrectResult.Suggestion(
                correction = topSuggestion,
                autoApply = aggressiveness == Aggressiveness.AGGRESSIVE
            )
        } else {
            AutocorrectResult.None
        }
    }

    /**
     * Compute the Levenshtein edit distance between two strings.
     */
    private fun editDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[m][n]
    }
}

/**
 * Result of an autocorrect evaluation.
 */
sealed class AutocorrectResult {
    /** No correction suggested. */
    object None : AutocorrectResult()

    /**
     * A correction is suggested.
     *
     * @param correction The suggested corrected word.
     * @param autoApply Whether the correction should be applied automatically.
     */
    data class Suggestion(
        val correction: String,
        val autoApply: Boolean
    ) : AutocorrectResult()
}
