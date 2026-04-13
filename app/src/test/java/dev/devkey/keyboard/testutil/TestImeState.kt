package dev.devkey.keyboard.testutil

import dev.devkey.keyboard.core.ImeState
import dev.devkey.keyboard.suggestion.word.WordComposer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Factory that returns an [ImeState] with sensible test defaults.
 *
 * All fields are overridable via named parameters. Uses [Dispatchers.Unconfined]
 * by default for deterministic test execution.
 */
fun testImeState(
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    mPredicting: Boolean = false,
    mAutoCorrectOn: Boolean = false,
    mAutoCorrectEnabled: Boolean = false,
    mReCorrectionEnabled: Boolean = false,
    mCompletionOn: Boolean = false,
    mHasDictionary: Boolean = false,
    mAutoSpace: Boolean = false,
    mJustAddedAutoSpace: Boolean = false,
    mPredictionOnForMode: Boolean = false,
    mPredictionOnPref: Boolean = false,
    mCommittedLength: Int = 0,
    mBestWord: CharSequence? = null,
    mEnteredText: CharSequence? = null,
    mModCtrl: Boolean = false,
    mModAlt: Boolean = false,
    mModMeta: Boolean = false,
    mModFn: Boolean = false,
    mSavedShiftState: Int = 0,
    mComposeMode: Boolean = false,
    mAutoCapPref: Boolean = false,
    mAutoCapActive: Boolean = false,
    mPasswordText: Boolean = false,
    mInputTypeNoAutoCorrect: Boolean = false,
    mDeadKeysActive: Boolean = false,
    mQuickFixes: Boolean = false,
    mCorrectionMode: Int = 0,
    mShowSuggestions: Boolean = false,
    mSuggestionForceOn: Boolean = false,
    mSuggestionForceOff: Boolean = false,
    mSuggestionsInLandscape: Boolean = false,
    mOrientation: Int = 0,
    mDeleteCount: Int = 0,
    mLastSelectionStart: Int = 0,
    mLastSelectionEnd: Int = 0,
    mWordSeparators: String? = null,
    mSentenceSeparators: String? = null,
): ImeState = ImeState(dispatcher).apply {
    this.mWord = WordComposer()
    this.mPredicting = mPredicting
    this.mAutoCorrectOn = mAutoCorrectOn
    this.mAutoCorrectEnabled = mAutoCorrectEnabled
    this.mReCorrectionEnabled = mReCorrectionEnabled
    this.mCompletionOn = mCompletionOn
    this.mHasDictionary = mHasDictionary
    this.mAutoSpace = mAutoSpace
    this.mJustAddedAutoSpace = mJustAddedAutoSpace
    this.mPredictionOnForMode = mPredictionOnForMode
    this.mPredictionOnPref = mPredictionOnPref
    this.mCommittedLength = mCommittedLength
    this.mBestWord = mBestWord
    this.mEnteredText = mEnteredText
    this.mModCtrl = mModCtrl
    this.mModAlt = mModAlt
    this.mModMeta = mModMeta
    this.mModFn = mModFn
    this.mSavedShiftState = mSavedShiftState
    this.mComposeMode = mComposeMode
    this.mAutoCapPref = mAutoCapPref
    this.mAutoCapActive = mAutoCapActive
    this.mPasswordText = mPasswordText
    this.mInputTypeNoAutoCorrect = mInputTypeNoAutoCorrect
    this.mDeadKeysActive = mDeadKeysActive
    this.mQuickFixes = mQuickFixes
    this.mCorrectionMode = mCorrectionMode
    this.mShowSuggestions = mShowSuggestions
    this.mSuggestionForceOn = mSuggestionForceOn
    this.mSuggestionForceOff = mSuggestionForceOff
    this.mSuggestionsInLandscape = mSuggestionsInLandscape
    this.mOrientation = mOrientation
    this.mDeleteCount = mDeleteCount
    this.mLastSelectionStart = mLastSelectionStart
    this.mLastSelectionEnd = mLastSelectionEnd
    this.mWordSeparators = mWordSeparators
    this.mSentenceSeparators = mSentenceSeparators
}
