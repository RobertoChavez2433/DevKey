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

    fun punctuation(request: SmartTextPunctuationRequest): SmartTextPunctuationDecision =
        SmartTextPunctuationDecision(
            apply = false,
            reason = SmartTextPunctuationReason.UNAVAILABLE,
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

data class SmartTextPunctuationRequest(
    val transform: SmartTextPunctuationTransform,
    val contextBeforeCursor: CharSequence?,
    val sentenceSeparators: String = ".!?",
)

data class SmartTextPunctuationDecision(
    val apply: Boolean,
    val reason: SmartTextPunctuationReason,
    val deleteBeforeCursor: Int = 0,
    val replacement: String = "",
    val charsRemoved: Int = 0,
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

enum class SmartTextPunctuationTransform(val wireName: String) {
    PUNCTUATION_SPACE_SWAP("punctuation_space_swap"),
    PERIOD_RESWAP("period_reswap"),
    DOUBLE_SPACE_PERIOD("double_space_period"),
    REMOVE_TRAILING_SPACE("remove_trailing_space"),
    REMOVE_PREVIOUS_PERIOD("remove_previous_period"),
}

enum class SmartTextPunctuationReason(val wireName: String) {
    MATCH("match"),
    UNAVAILABLE("unavailable"),
    EMPTY_CONTEXT("empty_context"),
    CONTEXT_TOO_SHORT("context_too_short"),
    NOT_SENTENCE_SEPARATOR("not_sentence_separator"),
    NOT_PATTERN("not_pattern"),
    NOT_WORD_BOUNDARY("not_word_boundary"),
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
