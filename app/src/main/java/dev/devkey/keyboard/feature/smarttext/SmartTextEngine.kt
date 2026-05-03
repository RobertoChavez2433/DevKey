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

    fun capitalization(request: SmartTextCapitalizationRequest): SmartTextCapitalizationDecision =
        SmartTextCapitalizationDecision(
            apply = request.autoCapEnabled &&
                request.editorAcceptsText &&
                request.cursorCapsMode != 0,
            reason = SmartTextCapitalizationReason.DEFAULT_RULE,
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

data class SmartTextCapitalizationRequest(
    val autoCapEnabled: Boolean,
    val editorAcceptsText: Boolean,
    val cursorCapsMode: Int,
)

data class SmartTextCapitalizationDecision(
    val apply: Boolean,
    val reason: SmartTextCapitalizationReason,
)

enum class SmartTextSuggestionKind {
    AUTOCORRECT,
    COMPLETION,
    LEARNED,
    COMMAND,
}

enum class SmartTextNextWordSource(val wireName: String) {
    HIT("smart_text_hit"),
    MISS("smart_text_miss"),
    UNAVAILABLE("smart_text_pending"),
    INPUT_KIND("input_kind"),
    EMPTY_PREVIOUS_WORD("no_prev_word"),
}

enum class SmartTextCapitalizationReason(val wireName: String) {
    DEFAULT_RULE("default_rule"),
    AUTO_CAP_DISABLED("auto_cap_disabled"),
    EDITOR_INACTIVE("editor_inactive"),
    NO_SENTENCE_CONTEXT("no_sentence_context"),
    CURSOR_CAPS_MODE("cursor_caps_mode"),
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
