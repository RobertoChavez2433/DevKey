package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.feature.command.InputMode

/**
 * Orchestrates word predictions by combining dictionary completions,
 * autocorrect suggestions, and learned word data.
 *
 * @param dictionaryProvider Source of dictionary-based suggestions.
 * @param autocorrectEngine Typo correction engine.
 * @param learningEngine Learned word tracking.
 */
class PredictionEngine(
    private val dictionaryProvider: DictionaryProvider,
    private val autocorrectEngine: AutocorrectEngine,
    private val learningEngine: LearningEngine
) {

    /** The current input mode (NORMAL or COMMAND). */
    var inputMode: InputMode = InputMode.NORMAL

    /**
     * Generate predictions for the currently typed word.
     *
     * @param currentWord The word being typed.
     * @param isCommand Whether command mode is active.
     * @return Up to 3 prediction results, with autocorrect suggestion first if applicable.
     */
    suspend fun predict(currentWord: String, isCommand: Boolean = false): List<PredictionResult> {
        if (currentWord.isEmpty()) return emptyList()

        // Command mode: return command-specific suggestions
        if (inputMode == InputMode.COMMAND || isCommand) {
            return try {
                val commandSuggestions = learningEngine.getCommandSuggestions(currentWord)
                commandSuggestions.map { PredictionResult(word = it, isAutocorrect = false) }
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Normal mode: combine autocorrect + dictionary suggestions
        val results = mutableListOf<PredictionResult>()

        // Check for autocorrect suggestion
        val autocorrectResult = autocorrectEngine.getCorrection(
            currentWord,
            learningEngine.getCustomWords()
        )
        if (autocorrectResult is AutocorrectResult.Suggestion) {
            results.add(
                PredictionResult(
                    word = autocorrectResult.correction,
                    isAutocorrect = true
                )
            )
        }

        // Get dictionary completions
        val dictSuggestions = dictionaryProvider.getSuggestions(currentWord, maxResults = 3)
        for (suggestion in dictSuggestions) {
            // Avoid duplicating the autocorrect suggestion
            if (results.none { it.word.equals(suggestion, ignoreCase = true) }) {
                results.add(PredictionResult(word = suggestion, isAutocorrect = false))
            }
            if (results.size >= 3) break
        }

        return results.take(3)
    }
}

/**
 * A single prediction result.
 *
 * @param word The suggested word.
 * @param isAutocorrect Whether this is an autocorrect suggestion (shown bold/highlighted).
 */
data class PredictionResult(
    val word: String,
    val isAutocorrect: Boolean = false
)
