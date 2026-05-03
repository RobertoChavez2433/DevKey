/*
 * Plain data holder for all mutable IME state.
 *
 * Collaborators receive this instead of LatinIME so they can be
 * unit-tested without an Android InputMethodService instance.
 * All nullable fields default to null; primitive fields have safe defaults.
 */
package dev.devkey.keyboard.core

import dev.devkey.keyboard.compose.ComposeSequence
import dev.devkey.keyboard.core.modifier.ChordeTracker
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.language.LanguageSwitcher
import dev.devkey.keyboard.legacy.CandidateView
import dev.devkey.keyboard.suggestion.word.WordAlternatives
import dev.devkey.keyboard.suggestion.word.WordComposer
import android.view.inputmethod.CompletionInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

class ImeState(dispatcher: CoroutineDispatcher = Dispatchers.Main) {

    // -- Composing & word state --
    val mComposing = StringBuilder()
    var mWord = WordComposer()
    var mCommittedLength = 0
    var mPredicting = false
    var mBestWord: CharSequence? = null
    var mPredictionOnForMode = false
    var mPredictionOnPref = false
    var mCompletionOn = false
    var mHasDictionary = false
    var mAutoSpace = false
    var mJustAddedAutoSpace = false
    var mAutoCorrectEnabled = false
    var mReCorrectionEnabled = false
    var mAutoCorrectOn = false
    var mEnteredText: CharSequence? = null

    // -- Modifier state --
    var mModCtrl = false
    var mModAlt = false
    var mModMeta = false
    var mModFn = false
    var mSavedShiftState = 0
    val mShiftKeyState = ChordeTracker()
    val mSymbolKeyState = ChordeTracker()
    val mCtrlKeyState = ChordeTracker()
    val mAltKeyState = ChordeTracker()
    val mMetaKeyState = ChordeTracker()
    val mFnKeyState = ChordeTracker()

    // -- Compose sequence handling --
    var mComposeMode = false
    lateinit var mComposeBuffer: ComposeSequence
    lateinit var mDeadAccentBuffer: ComposeSequence

    // -- Correction & capitalization --
    var mAutoCapPref = false
    var mAutoCapActive = false
    var mPasswordText = false
    var mInputTypeNoAutoCorrect = false
    var mDeadKeysActive = false
    var mQuickFixes = false
    var mCorrectionMode = 0

    // -- Keyboard display --
    var mFullscreenOverride = false
    var mForceKeyboardOn = false
    var mKeyboardNotification = false
    var mSuggestionsInLandscape = false
    var mSuggestionForceOn = false
    var mSuggestionForceOff = false
    var mShowSuggestions = false
    var mIsShowingHint = false
    var mHeightPortrait = 0
    var mHeightLandscape = 0
    var mNumKeyboardModes = 3
    var mKeyboardModeOverridePortrait = 0
    var mKeyboardModeOverrideLandscape = 0
    var mOrientation = 0
    var mPopupOn = false

    // -- Voice --
    var mEnableVoiceButton = false
    var mEnableVoice = true
    var mVoiceOnPrimary = false

    // -- Selection & history tracking --
    var mLastSelectionStart = 0
    var mLastSelectionEnd = 0
    var mJustAccepted = false
    var mJustRevertedSeparator: CharSequence? = null
    var mDeleteCount = 0
    var mLastKeyTime: Long = 0

    // -- Suggestions & dictionary state --
    var mSuggestPuncList: MutableList<CharSequence>? = null
    var mRefreshKeyboardRequired = false
    val mWordHistory = ArrayList<WordAlternatives>()
    var mCompletions: Array<CompletionInfo>? = null

    // -- Localization --
    var mInputLocale: String? = null
    var mWordSeparators: String? = null
    var mSentenceSeparators: String? = null
    var mConfigurationChanging = false

    // -- Shared complex objects (nullable, null in tests) --
    var mKeyboardSwitcher: KeyboardSwitcher? = null
    var mLanguageSwitcher: LanguageSwitcher? = null
    var mCandidateView: CandidateView? = null

    // -- Coroutines --
    val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
    var suggestionsJob: Job? = null
    var oldSuggestionsJob: Job? = null

    // -- Derived state methods --

    fun isWordSeparator(code: Int): Boolean {
        val separators = mWordSeparators ?: ""
        return separators.indexOf(code.toChar()) >= 0
    }

    fun isSuggestedPunctuation(code: Int): Boolean {
        val puncList = mSuggestPuncList ?: return false
        return puncList.any { it.length == 1 && it[0].code == code }
    }

    fun isPortrait(): Boolean = mOrientation == ORIENTATION_PORTRAIT

    fun suggestionsDisabled(): Boolean {
        if (mSuggestionForceOff) return true
        if (mSuggestionForceOn) return false
        return !(mSuggestionsInLandscape || isPortrait())
    }

    fun isPredictionOn(predictionWanted: Boolean): Boolean =
        mPredictionOnForMode && predictionWanted

    fun resetPrediction() {
        mComposing.setLength(0)
        mPredicting = false
        mDeleteCount = 0
        mJustAddedAutoSpace = false
    }

    fun preferCapitalization(): Boolean = mWord.isFirstCharCapitalized()

    fun isAlphabet(code: Int): Boolean = Character.isLetter(code)

    fun reloadKeyboards(settings: dev.devkey.keyboard.data.repository.SettingsRepository) {
        mKeyboardSwitcher!!.setLanguageSwitcher(mLanguageSwitcher!!)
        if (mKeyboardSwitcher!!.getKeyboardMode() != dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher.MODE_NONE) {
            mKeyboardSwitcher!!.setVoiceMode(mEnableVoice && mEnableVoiceButton, mVoiceOnPrimary)
        }
        updateKeyboardOptions(settings)
        mKeyboardSwitcher!!.makeKeyboards(true)
    }

    fun updateKeyboardOptions(settings: dev.devkey.keyboard.data.repository.SettingsRepository) {
        val portrait = isPortrait()
        mNumKeyboardModes = if (settings.compactModeEnabled) 3 else 2
        val kbMode = if (portrait) {
            getKeyboardModeNum(settings.keyboardModePortrait, mKeyboardModeOverridePortrait)
        } else {
            getKeyboardModeNum(settings.keyboardModeLandscape, mKeyboardModeOverrideLandscape)
        }
        val screenHeightPercent = if (portrait) mHeightPortrait else mHeightLandscape
        settings.keyboardMode = kbMode
        settings.keyboardHeightPercent = screenHeightPercent.toFloat()
    }

    private fun getKeyboardModeNum(origMode: Int, override: Int): Int {
        var mode = origMode
        if (mNumKeyboardModes == 2 && mode == 2) mode = 1
        var num = (mode + override) % mNumKeyboardModes
        if (mNumKeyboardModes == 2 && num == 1) num = 2
        return num
    }

    internal fun dump(p: android.util.Printer, feedbackManager: FeedbackManager) {
        p.println("LatinIME state :")
        p.println("  Keyboard mode = ${mKeyboardSwitcher!!.getKeyboardMode()}")
        p.println("  mComposing=[${mComposing.length} chars]")
        p.println("  mPredictionOnForMode=$mPredictionOnForMode")
        p.println("  mCorrectionMode=$mCorrectionMode")
        p.println("  mPredicting=$mPredicting")
        p.println("  mAutoCorrectOn=$mAutoCorrectOn")
        p.println("  mAutoSpace=$mAutoSpace")
        p.println("  mCompletionOn=$mCompletionOn")
        p.println("  TextEntryState.state=${dev.devkey.keyboard.core.input.TextEntryState.getState()}")
        p.println("  mSoundOn=${feedbackManager.soundOn}")
        p.println("  mVibrateOn=${feedbackManager.vibrateOn}")
        p.println("  mPopupOn=$mPopupOn")
    }

    companion object {
        private const val ORIENTATION_PORTRAIT = 1 // Configuration.ORIENTATION_PORTRAIT
    }
}
