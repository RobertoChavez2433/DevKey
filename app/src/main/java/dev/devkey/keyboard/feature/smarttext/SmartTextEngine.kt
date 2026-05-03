package dev.devkey.keyboard.feature.smarttext

/**
 * Boundary for autocorrect, suggestions, capitalization, punctuation, and
 * learned-word ranking decisions owned by a smart-text engine.
 *
 * DevKey keeps keyboard rendering, modifier state, toolbar modes, command mode,
 * macros, clipboard, voice entry points, and privacy policy outside this
 * boundary.
 */
interface SmartTextEngine {
    fun correction(request: SmartTextCorrectionRequest): SmartTextCorrection?

    suspend fun suggestions(request: SmartTextSuggestionRequest): List<SmartTextSuggestion>
}

data class SmartTextCorrectionRequest(
    val typedWord: String,
    val customWords: Set<String>,
    val inputKind: SmartTextInputKind = SmartTextInputKind.NORMAL,
)

data class SmartTextSuggestionRequest(
    val currentWord: String,
    val inputKind: SmartTextInputKind = SmartTextInputKind.NORMAL,
    val maxResults: Int = 3,
)

data class SmartTextCorrection(
    val word: String,
    val autoApply: Boolean,
)

data class SmartTextSuggestion(
    val word: String,
    val kind: SmartTextSuggestionKind,
)

enum class SmartTextSuggestionKind {
    AUTOCORRECT,
    COMPLETION,
    COMMAND,
}

enum class SmartTextCorrectionLevel {
    OFF,
    MILD,
    AGGRESSIVE,
}

enum class SmartTextInputKind {
    NORMAL,
    PASSWORD,
    URL,
    EMAIL,
    COMMAND,
    CODE_LIKE,
}
