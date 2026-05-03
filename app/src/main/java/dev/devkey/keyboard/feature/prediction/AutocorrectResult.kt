package dev.devkey.keyboard.feature.prediction

/**
 * Result used by the IME accept/revert path after a smart-text correction
 * decision has already been made.
 */
sealed class AutocorrectResult {
    object None : AutocorrectResult()

    data class Suggestion(
        val correction: String,
        val autoApply: Boolean,
    ) : AutocorrectResult()
}
