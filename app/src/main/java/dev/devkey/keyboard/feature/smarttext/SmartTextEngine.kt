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

    fun candidateSuggestions(request: SmartTextSuggestionRequest): List<SmartTextSuggestion> =
        emptyList()

    suspend fun suggestions(request: SmartTextSuggestionRequest): List<SmartTextSuggestion>

    fun nextWordSuggestions(request: SmartTextNextWordRequest): SmartTextNextWordResult =
        SmartTextNextWordResult(
            suggestions = emptyList(),
            source = SmartTextNextWordSource.UNAVAILABLE,
        )
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

data class SmartTextNextWordRequest(
    val previousWord: String,
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

data class SmartTextNextWordResult(
    val suggestions: List<String>,
    val source: SmartTextNextWordSource,
)

enum class SmartTextSuggestionKind {
    AUTOCORRECT,
    COMPLETION,
    COMMAND,
}

enum class SmartTextNextWordSource(val wireName: String) {
    HIT("smart_text_hit"),
    MISS("smart_text_miss"),
    UNAVAILABLE("smart_text_pending"),
    INPUT_KIND("input_kind"),
    EMPTY_PREVIOUS_WORD("no_prev_word"),
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
