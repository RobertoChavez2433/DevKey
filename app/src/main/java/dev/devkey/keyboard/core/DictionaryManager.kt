package dev.devkey.keyboard.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.devkey.keyboard.dictionary.user.AutoDictionary
import dev.devkey.keyboard.PREF_QUICK_FIXES
import dev.devkey.keyboard.R
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.dictionary.bigram.UserBigramDictionary
import dev.devkey.keyboard.dictionary.user.UserDictionary
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.prediction.PredictionEngine
import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardDictionary
import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardSmartTextEngine
import dev.devkey.keyboard.core.prefs.ImePrefsUtil
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.suggestion.engine.WordPromotionDelegate
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Initializes and reloads dictionaries and the prediction pipeline.
 * Extracted from LatinIME to keep dictionary lifecycle separate.
 */
internal class DictionaryManager(
    private val state: ImeState,
    private val context: Context,
    private val icProvider: InputConnectionProvider,
    private val candidateViewHost: CandidateViewHost,
    private val preferenceObserver: PreferenceObserver,
    private val puncHeuristics: PunctuationHeuristics,
    private val wordPromotionDelegate: WordPromotionDelegate,
    private val reloadKeyboards: () -> Unit,
    private val updateShiftKeyState: (android.view.inputmethod.EditorInfo?) -> Unit,
    private val isPredictionOn: () -> Boolean,
    private val settingsRepository: SettingsRepository,
) {

    fun initSuggest(locale: String) {
        state.mInputLocale = locale

        val conf = context.resources.configuration
        val saveLocale = conf.locales[0]
        val localeConf = android.os.LocaleList.forLanguageTags(locale)
        val localizedConf = android.content.res.Configuration(conf).apply { setLocales(localeConf) }
        val localizedContext = context.createConfigurationContext(localizedConf)
        val localizedResources = localizedContext.resources
        state.mSuggest?.close()

        state.mQuickFixes = settingsRepository.getBoolean(
            PREF_QUICK_FIXES,
            context.resources.getBoolean(R.bool.default_quick_fixes)
        )

        val dictionaries = ImePrefsUtil.getDictionary(localizedResources)
        state.mSuggest = Suggest(context, dictionaries)
        preferenceObserver.updateAutoTextEnabled(saveLocale)
        state.mUserDictionary?.close()
        state.mUserDictionary = UserDictionary(context, state.mInputLocale!!)
        state.mAutoDictionary?.close()
        state.mAutoDictionary = AutoDictionary(
            context, wordPromotionDelegate, state.mInputLocale!!, Suggest.DIC_AUTO
        )
        state.mUserBigramDictionary?.close()
        state.mUserBigramDictionary = UserBigramDictionary(
            context, wordPromotionDelegate, state.mInputLocale!!, Suggest.DIC_USER
        )
        state.mSuggest!!.setUserBigramDictionary(state.mUserBigramDictionary)
        state.mSuggest!!.setUserDictionary(state.mUserDictionary)
        state.mSuggest!!.setAutoDictionary(state.mAutoDictionary)
        SessionDependencies.suggest = state.mSuggest

        // Initialize the modern prediction pipeline
        val db = DevKeyDatabase.getInstance(context)
        val dictProvider = DictionaryProvider(state.mSuggest)
        val learnEngine = LearningEngine(db.learnedWordDao())
        val smartTextEngine = AnySoftKeyboardSmartTextEngine(
            dictProvider,
            learnEngine,
            correctionLevel = { SessionDependencies.smartTextCorrectionLevel },
        )
        val predEngine = PredictionEngine(smartTextEngine)
        SessionDependencies.dictionaryProvider = dictProvider
        SessionDependencies.learningEngine = learnEngine
        SessionDependencies.smartTextEngine = smartTextEngine
        SessionDependencies.predictionEngine = predEngine
        state.serviceScope.launch(Dispatchers.IO) {
            learnEngine.initialize()
            val startedAt = SystemClock.elapsedRealtime()
            val memoryBefore = usedMemoryKb()
            val donorDictionary = AnySoftKeyboardDictionary()
            val stats = donorDictionary.load(
                context,
                R.raw.ask_english_wordlist_combined,
                AnySoftKeyboardDictionary.DEFAULT_ARTIFACT,
            )
            dictProvider.donorDictionary = donorDictionary
            val loadDurationMs = SystemClock.elapsedRealtime() - startedAt
            val memoryDeltaKb = usedMemoryKb() - memoryBefore
            Log.i(
                TAG,
                "AnySoftKeyboard dictionary loaded: ${stats.wordCount} words, " +
                    "locale=${stats.metadata.locale}, version=${stats.metadata.version}"
            )
            DevKeyLogger.ime(
                "dictionary_ready",
                mapOf(
                    "source" to stats.metadata.sourceName,
                    "artifact" to stats.metadata.sourceArtifact,
                    "locale" to (stats.metadata.locale ?: "unknown"),
                    "version" to (stats.metadata.version ?: -1),
                    "word_count" to stats.wordCount,
                    "artifact_bytes" to stats.sourceBytesRead,
                    "load_duration_ms" to loadDurationMs,
                    "memory_delta_kb" to memoryDeltaKb,
                )
            )
            withContext(Dispatchers.Main) {
                preferenceObserver.updateCorrectionMode()
                candidateViewHost.setCandidatesViewShown(isPredictionOn())
            }
        }

        preferenceObserver.updateCorrectionMode()
        state.mWordSeparators = context.resources.getString(R.string.word_separators)
        state.mSentenceSeparators = context.resources.getString(R.string.sentence_separators)
        puncHeuristics.sentenceSeparators = state.mSentenceSeparators
        preferenceObserver.initSuggestPuncList()
    }

    fun toggleLanguage(reset: Boolean, next: Boolean) {
        if (reset) {
            state.mLanguageSwitcher!!.reset()
        } else {
            if (next) state.mLanguageSwitcher!!.next() else state.mLanguageSwitcher!!.prev()
        }
        val currentKeyboardMode = state.mKeyboardSwitcher!!.getKeyboardMode()
        reloadKeyboards()
        state.mKeyboardSwitcher!!.makeKeyboards(true)
        state.mKeyboardSwitcher!!.setKeyboardMode(
            currentKeyboardMode, 0, state.mEnableVoiceButton && state.mEnableVoice
        )
        initSuggest(state.mLanguageSwitcher!!.getInputLanguage()!!)
        state.mLanguageSwitcher!!.persist()
        state.mAutoCapActive = state.mAutoCapPref && state.mLanguageSwitcher!!.allowAutoCap()
        state.mDeadKeysActive = state.mLanguageSwitcher!!.allowDeadKeys()
        updateShiftKeyState(icProvider.editorInfo)
        candidateViewHost.setCandidatesViewShown(isPredictionOn())
    }

    companion object {
        private const val TAG = "DevKey/DictMgr"

        private fun usedMemoryKb(): Long {
            val runtime = Runtime.getRuntime()
            return (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        }
    }
}
