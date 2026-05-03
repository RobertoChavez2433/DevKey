package dev.devkey.keyboard.testutil

import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionLevel
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Resets all [SessionDependencies] fields to their clean initial state.
 *
 * Call this in test setup/teardown to prevent state leakage between tests.
 *
 * This file must remain under src/test/ — never move to production source sets.
 */
fun resetSessionDependencies() {
    SessionDependencies.commandModeDetector = null
    SessionDependencies.currentPackageName = null
    SessionDependencies.dictionaryProvider = null
    SessionDependencies.learningEngine = null
    SessionDependencies.smartTextEngine = null
    SessionDependencies.predictionEngine = null
    SessionDependencies.smartTextCorrectionLevel = SmartTextCorrectionLevel.MILD
    SessionDependencies.smartTextImportMetrics = null
    SessionDependencies.voiceInputEngine = null
    SessionDependencies.resetPredictionState = null
    SessionDependencies.triggerNextSuggestions = null
    SessionDependencies.composingWord.value = ""
    SessionDependencies.pendingCorrection.value = null
    SessionDependencies.nextWordSuggestions.value = emptyList()
}
