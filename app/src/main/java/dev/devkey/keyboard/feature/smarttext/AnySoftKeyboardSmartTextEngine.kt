package dev.devkey.keyboard.feature.smarttext

import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine

/**
 * Smart-text adapter backed by AnySoftKeyboard dictionary artifacts.
 */
class AnySoftKeyboardSmartTextEngine(
    private val dictionaryProvider: DictionaryProvider,
    private val learningEngine: LearningEngine,
    private val correctionLevel: () -> SmartTextCorrectionLevel,
) : SmartTextEngine {

    override fun correction(request: SmartTextCorrectionRequest): SmartTextCorrection? {
        val level = correctionLevel()
        val blockedReason = correctionBlockedReason(request, level)
        if (blockedReason != null) {
            logCorrection(request, corrected = false, reason = blockedReason)
            return null
        }

        val correction = dictionaryProvider.getFuzzyCorrections(
            request.typedWord,
            maxResults = 3,
        ).firstOrNull()
        if (correction == null) {
            logCorrection(request, corrected = false, reason = "none")
            return null
        }

        val autoApply = level == SmartTextCorrectionLevel.AGGRESSIVE
        logCorrection(
            request = request,
            corrected = true,
            reason = "donor_dictionary",
            autoApply = autoApply,
            resultLength = correction.length,
        )
        return SmartTextCorrection(word = correction, autoApply = autoApply)
    }

    private fun correctionBlockedReason(
        request: SmartTextCorrectionRequest,
        level: SmartTextCorrectionLevel,
    ): String? = when {
        request.inputKind != SmartTextInputKind.NORMAL -> "input_kind"
        level == SmartTextCorrectionLevel.OFF -> "off"
        request.typedWord.isEmpty() -> "empty"
        !request.typedWord.all { it.isLetter() } -> "structured_token"
        isCustomWord(request.typedWord, request.customWords) -> "custom_word"
        dictionaryProvider.isValidWord(request.typedWord) -> "valid_word"
        else -> null
    }

    private fun isCustomWord(typedWord: String, customWords: Set<String>): Boolean {
        val typedLower = typedWord.lowercase()
        return customWords.any { it.equals(typedLower, ignoreCase = true) }
    }

    override fun candidateSuggestions(request: SmartTextSuggestionRequest): List<SmartTextSuggestion> {
        return buildCandidateSuggestions(request, logResult = true)
    }

    private fun buildCandidateSuggestions(
        request: SmartTextSuggestionRequest,
        logResult: Boolean,
    ): List<SmartTextSuggestion> {
        if (request.currentWord.isEmpty()) {
            if (logResult) logSuggestions(request, emptyList(), reason = "empty")
            return emptyList()
        }
        if (request.inputKind != SmartTextInputKind.NORMAL) {
            if (logResult) logSuggestions(request, emptyList(), reason = "input_kind")
            return emptyList()
        }

        val results = mutableListOf<SmartTextSuggestion>()
        val correction = correction(
            SmartTextCorrectionRequest(
                typedWord = request.currentWord,
                customWords = learningEngine.getCustomWords(),
                inputKind = request.inputKind,
            )
        )
        if (correction != null) {
            results.add(
                SmartTextSuggestion(
                    word = correction.word,
                    kind = SmartTextSuggestionKind.AUTOCORRECT,
                )
            )
        }

        for (suggestion in dictionaryProvider.getSuggestions(request.currentWord, request.maxResults)) {
            if (results.none { it.word.equals(suggestion, ignoreCase = true) }) {
                results.add(
                    SmartTextSuggestion(
                        word = suggestion,
                        kind = SmartTextSuggestionKind.COMPLETION,
                    )
                )
            }
            if (results.size >= request.maxResults) break
        }

        val limitedResults = results.take(request.maxResults)
        if (logResult) logSuggestions(request, limitedResults, reason = "normal")
        return limitedResults
    }

    override suspend fun suggestions(request: SmartTextSuggestionRequest): List<SmartTextSuggestion> {
        if (request.inputKind == SmartTextInputKind.COMMAND) {
            val suggestions = learningEngine.getCommandSuggestions(
                request.currentWord,
                request.maxResults,
            ).map {
                SmartTextSuggestion(word = it, kind = SmartTextSuggestionKind.COMMAND)
            }
            logSuggestions(request, suggestions, reason = "command")
            return suggestions
        }
        if (request.currentWord.isEmpty() || request.inputKind != SmartTextInputKind.NORMAL) {
            return buildCandidateSuggestions(request, logResult = true)
        }

        val candidateResults = buildCandidateSuggestions(request, logResult = false)
        val learnedResults = learningEngine.getNormalSuggestions(
            request.currentWord,
            request.maxResults,
        ).map {
            SmartTextSuggestion(word = it, kind = SmartTextSuggestionKind.LEARNED)
        }
        val rankedResults = mergeRankedSuggestions(
            candidateResults = candidateResults,
            learnedResults = learnedResults,
            maxResults = request.maxResults,
        )
        logSuggestions(
            request,
            rankedResults,
            reason = "normal_ranked",
            learnedResultCount = learnedResults.size,
        )
        return rankedResults
    }

    private fun mergeRankedSuggestions(
        candidateResults: List<SmartTextSuggestion>,
        learnedResults: List<SmartTextSuggestion>,
        maxResults: Int,
    ): List<SmartTextSuggestion> {
        val ranked = mutableListOf<SmartTextSuggestion>()
        fun addUnique(suggestion: SmartTextSuggestion) {
            if (ranked.none { it.word.equals(suggestion.word, ignoreCase = true) }) {
                ranked.add(suggestion)
            }
        }

        candidateResults
            .filter { it.kind == SmartTextSuggestionKind.AUTOCORRECT }
            .forEach(::addUnique)
        learnedResults.forEach(::addUnique)
        candidateResults
            .filterNot { it.kind == SmartTextSuggestionKind.AUTOCORRECT }
            .forEach(::addUnique)
        return ranked.take(maxResults)
    }

    override fun nextWordSuggestions(request: SmartTextNextWordRequest): SmartTextNextWordResult {
        val result = when {
            request.previousWord.isBlank() -> SmartTextNextWordResult(
                suggestions = emptyList(),
                source = SmartTextNextWordSource.EMPTY_PREVIOUS_WORD,
            )
            request.inputKind != SmartTextInputKind.NORMAL -> SmartTextNextWordResult(
                suggestions = emptyList(),
                source = SmartTextNextWordSource.INPUT_KIND,
            )
            else -> SmartTextNextWordResult(
                suggestions = emptyList(),
                source = SmartTextNextWordSource.UNAVAILABLE,
            )
        }
        DevKeyLogger.text(
            "smart_text_next_word",
            mapOf(
                "engine" to "anysoftkeyboard",
                "input_kind" to request.inputKind.name.lowercase(),
                "prev_word_length" to request.previousWord.length,
                "result_count" to result.suggestions.size,
                "source" to result.source.wireName,
                "dictionary_source" to dictionaryProvider.activeDictionarySource(),
            )
        )
        return result
    }

    override fun capitalization(
        request: SmartTextCapitalizationRequest,
    ): SmartTextCapitalizationDecision {
        val decision = when {
            !request.autoCapEnabled -> SmartTextCapitalizationDecision(
                apply = false,
                reason = SmartTextCapitalizationReason.AUTO_CAP_DISABLED,
            )
            !request.editorAcceptsText -> SmartTextCapitalizationDecision(
                apply = false,
                reason = SmartTextCapitalizationReason.EDITOR_INACTIVE,
            )
            request.cursorCapsMode == 0 -> SmartTextCapitalizationDecision(
                apply = false,
                reason = SmartTextCapitalizationReason.NO_SENTENCE_CONTEXT,
            )
            else -> SmartTextCapitalizationDecision(
                apply = true,
                reason = SmartTextCapitalizationReason.CURSOR_CAPS_MODE,
            )
        }
        if (decision.apply) {
            DevKeyLogger.text(
                "smart_text_capitalization",
                mapOf(
                    "engine" to "anysoftkeyboard",
                    "applied" to true,
                    "reason" to decision.reason.wireName,
                    "cursor_caps_mode" to request.cursorCapsMode,
                )
            )
        }
        return decision
    }

    override fun punctuation(request: SmartTextPunctuationRequest): SmartTextPunctuationDecision {
        val decision = when (request.transform) {
            SmartTextPunctuationTransform.PUNCTUATION_SPACE_SWAP ->
                punctuationSpaceSwap(request.contextBeforeCursor, request.sentenceSeparators)
            SmartTextPunctuationTransform.PERIOD_RESWAP ->
                periodReswap(request.contextBeforeCursor)
            SmartTextPunctuationTransform.DOUBLE_SPACE_PERIOD ->
                doubleSpacePeriod(request.contextBeforeCursor)
            SmartTextPunctuationTransform.REMOVE_TRAILING_SPACE ->
                removeTrailingSpace(request.contextBeforeCursor)
            SmartTextPunctuationTransform.REMOVE_PREVIOUS_PERIOD ->
                removePreviousPeriod(request.contextBeforeCursor)
        }
        logPunctuation(request, decision)
        return decision
    }

    private fun punctuationSpaceSwap(
        contextBeforeCursor: CharSequence?,
        sentenceSeparators: String,
    ): SmartTextPunctuationDecision {
        if (contextBeforeCursor.isNullOrEmpty()) return punctuationNoop(SmartTextPunctuationReason.EMPTY_CONTEXT)
        if (contextBeforeCursor.length < 2) return punctuationNoop(SmartTextPunctuationReason.CONTEXT_TOO_SHORT)
        val space = contextBeforeCursor[contextBeforeCursor.length - 2]
        val punctuation = contextBeforeCursor[contextBeforeCursor.length - 1]
        return when {
            space != ' ' -> punctuationNoop(SmartTextPunctuationReason.NOT_PATTERN)
            punctuation !in sentenceSeparators -> punctuationNoop(SmartTextPunctuationReason.NOT_SENTENCE_SEPARATOR)
            else -> SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 2,
                replacement = "$punctuation ",
            )
        }
    }

    private fun periodReswap(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision {
        if (contextBeforeCursor.isNullOrEmpty()) return punctuationNoop(SmartTextPunctuationReason.EMPTY_CONTEXT)
        if (contextBeforeCursor.length < 3) return punctuationNoop(SmartTextPunctuationReason.CONTEXT_TOO_SHORT)
        val start = contextBeforeCursor.length - 3
        return if (
            contextBeforeCursor[start] == '.' &&
            contextBeforeCursor[start + 1] == ' ' &&
            contextBeforeCursor[start + 2] == '.'
        ) {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 3,
                replacement = " ..",
            )
        } else {
            punctuationNoop(SmartTextPunctuationReason.NOT_PATTERN)
        }
    }

    private fun doubleSpacePeriod(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision {
        if (contextBeforeCursor.isNullOrEmpty()) return punctuationNoop(SmartTextPunctuationReason.EMPTY_CONTEXT)
        if (contextBeforeCursor.length < 3) return punctuationNoop(SmartTextPunctuationReason.CONTEXT_TOO_SHORT)
        val start = contextBeforeCursor.length - 3
        val wordBoundary = contextBeforeCursor[start]
        return if (
            wordBoundary.isLetterOrDigit() &&
            contextBeforeCursor[start + 1] == ' ' &&
            contextBeforeCursor[start + 2] == ' '
        ) {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 2,
                replacement = ". ",
                charsRemoved = 2,
            )
        } else if (!wordBoundary.isLetterOrDigit()) {
            punctuationNoop(SmartTextPunctuationReason.NOT_WORD_BOUNDARY)
        } else {
            punctuationNoop(SmartTextPunctuationReason.NOT_PATTERN)
        }
    }

    private fun removeTrailingSpace(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision {
        if (contextBeforeCursor.isNullOrEmpty()) return punctuationNoop(SmartTextPunctuationReason.EMPTY_CONTEXT)
        return if (contextBeforeCursor.last() == ' ') {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 1,
            )
        } else {
            punctuationNoop(SmartTextPunctuationReason.NOT_PATTERN)
        }
    }

    private fun removePreviousPeriod(contextBeforeCursor: CharSequence?): SmartTextPunctuationDecision {
        if (contextBeforeCursor.isNullOrEmpty()) return punctuationNoop(SmartTextPunctuationReason.EMPTY_CONTEXT)
        return if (contextBeforeCursor.last() == '.') {
            SmartTextPunctuationDecision(
                apply = true,
                reason = SmartTextPunctuationReason.MATCH,
                deleteBeforeCursor = 1,
            )
        } else {
            punctuationNoop(SmartTextPunctuationReason.NOT_PATTERN)
        }
    }

    private fun punctuationNoop(reason: SmartTextPunctuationReason): SmartTextPunctuationDecision =
        SmartTextPunctuationDecision(apply = false, reason = reason)

    private fun logCorrection(
        request: SmartTextCorrectionRequest,
        corrected: Boolean,
        reason: String,
        autoApply: Boolean = false,
        resultLength: Int = 0,
    ) {
        DevKeyLogger.text(
            "smart_text_correction",
            mapOf(
                "engine" to "anysoftkeyboard",
                "input_kind" to request.inputKind.name.lowercase(),
                "word_length" to request.typedWord.length,
                "custom_word_count" to request.customWords.size,
                "corrected" to corrected,
                "auto_apply" to autoApply,
                "result_length" to resultLength,
                "reason" to reason,
                "dictionary_source" to dictionaryProvider.activeDictionarySource(),
            )
        )
    }

    private fun logSuggestions(
        request: SmartTextSuggestionRequest,
        suggestions: List<SmartTextSuggestion>,
        reason: String,
        learnedResultCount: Int = 0,
    ) {
        DevKeyLogger.text(
            "smart_text_suggestions",
            mapOf(
                "engine" to "anysoftkeyboard",
                "input_kind" to request.inputKind.name.lowercase(),
                "word_length" to request.currentWord.length,
                "result_count" to suggestions.size,
                "learned_result_count" to learnedResultCount,
                "has_autocorrect" to suggestions.any {
                    it.kind == SmartTextSuggestionKind.AUTOCORRECT
                },
                "max_results" to request.maxResults,
                "reason" to reason,
                "dictionary_source" to dictionaryProvider.activeDictionarySource(),
            )
        )
    }

    private fun logPunctuation(
        request: SmartTextPunctuationRequest,
        decision: SmartTextPunctuationDecision,
    ) {
        DevKeyLogger.text(
            "smart_text_punctuation",
            mapOf(
                "engine" to "anysoftkeyboard",
                "transform" to request.transform.wireName,
                "context_length" to (request.contextBeforeCursor?.length ?: 0),
                "applied" to decision.apply,
                "reason" to decision.reason.wireName,
                "delete_before_cursor" to decision.deleteBeforeCursor,
                "replacement_length" to decision.replacement.length,
                "chars_removed" to decision.charsRemoved,
            )
        )
    }
}
