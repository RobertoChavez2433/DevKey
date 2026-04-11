package dev.devkey.keyboard.core

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.dictionary.user.AutoDictionary
import dev.devkey.keyboard.PREF_QUICK_FIXES
import dev.devkey.keyboard.R
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.dictionary.bigram.UserBigramDictionary
import dev.devkey.keyboard.dictionary.user.UserDictionary
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.prediction.PredictionEngine
import dev.devkey.keyboard.feature.prediction.TrieDictionary
import dev.devkey.keyboard.core.prefs.ImePrefsUtil
import dev.devkey.keyboard.suggestion.engine.WordPromotionDelegate
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        state.mQuickFixes = sp.getBoolean(
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
        val acEngine = AutocorrectEngine(dictProvider)
        val learnEngine = LearningEngine(db.learnedWordDao())
        val predEngine = PredictionEngine(dictProvider, acEngine, learnEngine)
        SessionDependencies.dictionaryProvider = dictProvider
        SessionDependencies.autocorrectEngine = acEngine
        SessionDependencies.learningEngine = learnEngine
        SessionDependencies.predictionEngine = predEngine
        state.serviceScope.launch(Dispatchers.IO) {
            learnEngine.initialize()
            val trie = TrieDictionary()
            trie.load(context, R.raw.en_us_wordfreq)
            dictProvider.trieDictionary = trie
            Log.i(TAG, "TrieDictionary loaded: ${trie.size} words")
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
    }
}
