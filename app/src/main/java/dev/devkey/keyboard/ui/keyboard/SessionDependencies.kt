package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.Suggest
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.feature.prediction.AutocorrectResult
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.prediction.PredictionEngine
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges the Java IME lifecycle with the Compose UI layer.
 *
 * Holds references to objects created in LatinIME (Java) that need to be
 * accessed by the Compose keyboard UI. Set from LatinIME.onCreate() and
 * onStartInputView().
 *
 * This is a pragmatic singleton approach. A proper DI solution would be
 * cleaner but is overkill for this stage of the project.
 */
object SessionDependencies {
    /** The active Suggest instance from LatinIME, for dictionary lookups. */
    @Volatile
    var suggest: Suggest? = null

    /** The command mode detector, initialized in LatinIME. */
    @Volatile
    var commandModeDetector: CommandModeDetector? = null

    /** The package name of the current foreground app. */
    @Volatile
    var currentPackageName: String? = null

    /** Modern prediction pipeline components, initialized in LatinIME.initSuggest(). */
    @Volatile
    var dictionaryProvider: DictionaryProvider? = null
    @Volatile
    var autocorrectEngine: AutocorrectEngine? = null
    @Volatile
    var learningEngine: LearningEngine? = null
    @Volatile
    var predictionEngine: PredictionEngine? = null

    /** The word currently being composed, updated from LatinIME key handlers. */
    val composingWord = MutableStateFlow("")

    /** A pending autocorrect suggestion (non-auto-applied), for the suggestion bar. */
    val pendingCorrection = MutableStateFlow<AutocorrectResult?>(null)
}
