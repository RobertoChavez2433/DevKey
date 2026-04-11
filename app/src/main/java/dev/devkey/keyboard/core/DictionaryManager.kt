package dev.devkey.keyboard.core

import android.util.Log
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.AutoDictionary
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.PREF_QUICK_FIXES
import dev.devkey.keyboard.R
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.UserBigramDictionary
import dev.devkey.keyboard.UserDictionary
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.prediction.PredictionEngine
import dev.devkey.keyboard.feature.prediction.TrieDictionary
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Initializes and reloads dictionaries and the prediction pipeline.
 * Extracted from LatinIME to keep dictionary lifecycle separate.
 */
internal class DictionaryManager(private val ime: LatinIME) {

    fun initSuggest(locale: String) {
        ime.mInputLocale = locale

        val conf = ime.resources.configuration
        val saveLocale = conf.locales[0]
        val localeConf = android.os.LocaleList.forLanguageTags(locale)
        val localizedConf = android.content.res.Configuration(conf).apply { setLocales(localeConf) }
        val localizedContext = ime.createConfigurationContext(localizedConf)
        val localizedResources = localizedContext.resources
        ime.mSuggest?.close()

        val sp = PreferenceManager.getDefaultSharedPreferences(ime)
        ime.mQuickFixes = sp.getBoolean(
            PREF_QUICK_FIXES,
            ime.resources.getBoolean(R.bool.default_quick_fixes)
        )

        val dictionaries = LatinIME.getDictionary(localizedResources)
        ime.mSuggest = Suggest(ime, dictionaries)
        ime.mPreferenceObserver.updateAutoTextEnabled(saveLocale)
        ime.mUserDictionary?.close()
        ime.mUserDictionary = UserDictionary(ime, ime.mInputLocale!!)
        ime.mAutoDictionary?.close()
        ime.mAutoDictionary = AutoDictionary(
            ime, ime, ime.mInputLocale!!, Suggest.DIC_AUTO
        )
        ime.mUserBigramDictionary?.close()
        ime.mUserBigramDictionary = UserBigramDictionary(
            ime, ime, ime.mInputLocale!!, Suggest.DIC_USER
        )
        ime.mSuggest!!.setUserBigramDictionary(ime.mUserBigramDictionary)
        ime.mSuggest!!.setUserDictionary(ime.mUserDictionary)
        ime.mSuggest!!.setAutoDictionary(ime.mAutoDictionary)
        SessionDependencies.suggest = ime.mSuggest

        // Initialize the modern prediction pipeline
        val db = DevKeyDatabase.getInstance(ime)
        val dictProvider = DictionaryProvider(ime.mSuggest)
        val acEngine = AutocorrectEngine(dictProvider)
        val learnEngine = LearningEngine(db.learnedWordDao())
        val predEngine = PredictionEngine(dictProvider, acEngine, learnEngine)
        SessionDependencies.dictionaryProvider = dictProvider
        SessionDependencies.autocorrectEngine = acEngine
        SessionDependencies.learningEngine = learnEngine
        SessionDependencies.predictionEngine = predEngine
        ime.serviceScope.launch(Dispatchers.IO) {
            learnEngine.initialize()
            val trie = TrieDictionary()
            trie.load(ime, R.raw.en_us_wordfreq)
            dictProvider.trieDictionary = trie
            Log.i(TAG, "TrieDictionary loaded: ${trie.size} words")
        }

        ime.mPreferenceObserver.updateCorrectionMode()
        ime.mWordSeparators = ime.mResources!!.getString(R.string.word_separators)
        ime.mSentenceSeparators = ime.mResources!!.getString(R.string.sentence_separators)
        ime.mPuncHeuristics.sentenceSeparators = ime.mSentenceSeparators
        ime.mPreferenceObserver.initSuggestPuncList()
    }

    fun toggleLanguage(reset: Boolean, next: Boolean) {
        if (reset) {
            ime.mLanguageSwitcher!!.reset()
        } else {
            if (next) ime.mLanguageSwitcher!!.next() else ime.mLanguageSwitcher!!.prev()
        }
        val currentKeyboardMode = ime.mKeyboardSwitcher!!.getKeyboardMode()
        ime.reloadKeyboards()
        ime.mKeyboardSwitcher!!.makeKeyboards(true)
        ime.mKeyboardSwitcher!!.setKeyboardMode(
            currentKeyboardMode, 0, ime.mEnableVoiceButton && ime.mEnableVoice
        )
        initSuggest(ime.mLanguageSwitcher!!.getInputLanguage()!!)
        ime.mLanguageSwitcher!!.persist()
        ime.mAutoCapActive = ime.mAutoCapPref && ime.mLanguageSwitcher!!.allowAutoCap()
        ime.mDeadKeysActive = ime.mLanguageSwitcher!!.allowDeadKeys()
        ime.updateShiftKeyState(ime.currentInputEditorInfo)
        ime.setCandidatesViewShown(ime.isPredictionOn())
    }

    companion object {
        private const val TAG = "DevKey/DictMgr"
    }
}
