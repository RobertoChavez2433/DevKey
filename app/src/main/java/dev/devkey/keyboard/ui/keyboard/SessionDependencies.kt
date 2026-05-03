package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.prediction.AutocorrectResult
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.prediction.PredictionEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionLevel
import dev.devkey.keyboard.feature.smarttext.SmartTextEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextImportMetrics
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    var learningEngine: LearningEngine? = null
    @Volatile
    var smartTextEngine: SmartTextEngine? = null
    @Volatile
    var predictionEngine: PredictionEngine? = null
    @Volatile
    var smartTextCorrectionLevel: SmartTextCorrectionLevel = SmartTextCorrectionLevel.MILD
    @Volatile
    var smartTextImportMetrics: SmartTextImportMetrics? = null

    /** The word currently being composed, updated from LatinIME key handlers. */
    val composingWord = MutableStateFlow("")

    /** A pending autocorrect suggestion (non-auto-applied), for the suggestion bar. */
    val pendingCorrection = MutableStateFlow<AutocorrectResult?>(null)

    /** Next-word suggestions from the smart-text boundary, shown when composing is empty. */
    val nextWordSuggestions = MutableStateFlow<List<CharSequence>>(emptyList())

    /** Voice input engine instance, set from Compose initialization for debug access. */
    @Volatile
    var voiceInputEngine: VoiceInputEngine? = null

    /** False for password and credential-adjacent input fields. */
    val voiceInputAllowed = MutableStateFlow(true)

    /**
     * Resets the legacy ImeState prediction fields (mPredicting, mComposing, etc.).
     * Set from ImeCollaboratorWiring so the Compose suggestion bar can cleanly
     * accept a prediction without leaving stale legacy state.
     */
    @Volatile
    var resetPredictionState: (() -> Unit)? = null

    /**
     * Triggers next-word smart-text suggestions after a word is committed from the
     * Compose suggestion bar. Wired from ImeCollaboratorWiring to
     * SuggestionCoordinator.setNextSuggestions().
     */
    @Volatile
    var triggerNextSuggestions: (() -> Unit)? = null

    /**
     * Canonical word-commit handler. ALL call sites that commit a word to the
     * learning engine MUST go through this method — no direct calls to
     * LearningEngine.onWordCommitted() elsewhere.
     *
     * Tracks word frequency for predictions. Does NOT add to the autocorrect
     * suppression set — only explicit user actions (long-press "Add to dictionary")
     * should do that via LearningEngine.addCustomWord().
     *
     * @param word The committed word.
     * @param scope CoroutineScope to launch the IO work in.
     */
    fun commitWord(word: String, scope: CoroutineScope) {
        val le = learningEngine ?: return
        DevKeyLogger.text("word_committed", mapOf("word_length" to word.length))
        scope.launch(Dispatchers.IO) {
            le.onWordCommitted(
                word,
                isCommand = commandModeDetector?.isCommandMode() == true,
                contextApp = currentPackageName
            )
            DevKeyLogger.text("word_learning_recorded", mapOf("word_length" to word.length))
        }
    }
}
