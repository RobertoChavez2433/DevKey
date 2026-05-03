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

    override suspend fun suggestions(request: SmartTextSuggestionRequest): List<SmartTextSuggestion> {
        if (request.currentWord.isEmpty()) {
            logSuggestions(request, emptyList(), reason = "empty")
            return emptyList()
        }
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
        if (request.inputKind != SmartTextInputKind.NORMAL) {
            logSuggestions(request, emptyList(), reason = "input_kind")
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
        logSuggestions(request, limitedResults, reason = "normal")
        return limitedResults
    }

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
    ) {
        DevKeyLogger.text(
            "smart_text_suggestions",
            mapOf(
                "engine" to "anysoftkeyboard",
                "input_kind" to request.inputKind.name.lowercase(),
                "word_length" to request.currentWord.length,
                "result_count" to suggestions.size,
                "has_autocorrect" to suggestions.any {
                    it.kind == SmartTextSuggestionKind.AUTOCORRECT
                },
                "max_results" to request.maxResults,
                "reason" to reason,
                "dictionary_source" to dictionaryProvider.activeDictionarySource(),
            )
        )
    }
}
