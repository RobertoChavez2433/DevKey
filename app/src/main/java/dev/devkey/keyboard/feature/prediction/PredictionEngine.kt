package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.feature.smarttext.SmartTextEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextInputKind
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionKind
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionRequest

/**
 * Orchestrates word predictions through the smart-text engine boundary.
 *
 * @param smartTextEngine Boundary that owns smart-text decisions.
 */
class PredictionEngine(
    private val smartTextEngine: SmartTextEngine,
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

        val inputKind = if (inputMode == InputMode.COMMAND || isCommand) {
            SmartTextInputKind.COMMAND
        } else {
            SmartTextInputKind.NORMAL
        }
        return smartTextEngine.suggestions(
            SmartTextSuggestionRequest(
                currentWord = currentWord,
                inputKind = inputKind,
                maxResults = 3,
            )
        )
            .map {
                PredictionResult(
                    word = it.word,
                    isAutocorrect = it.kind == SmartTextSuggestionKind.AUTOCORRECT,
                )
            }
            .take(3)
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
