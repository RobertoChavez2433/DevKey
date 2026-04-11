/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package dev.devkey.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.util.PrintWriterPrinter
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.compose.ComposeSequence
import dev.devkey.keyboard.compose.DeadAccentSequence
import dev.devkey.keyboard.core.modifier.ChordeTracker
import dev.devkey.keyboard.core.FeedbackManager
import dev.devkey.keyboard.core.ImeInitializer
import dev.devkey.keyboard.core.InputDispatcher
import dev.devkey.keyboard.core.InputHandlers
import dev.devkey.keyboard.core.DictionaryManager
import dev.devkey.keyboard.core.InputViewSetup
import dev.devkey.keyboard.core.ModifierHandler
import dev.devkey.keyboard.core.KeyEventSender
import dev.devkey.keyboard.core.NotificationController
import dev.devkey.keyboard.core.PreferenceObserver
import dev.devkey.keyboard.core.PunctuationHeuristics
import dev.devkey.keyboard.core.SuggestionCoordinator
import dev.devkey.keyboard.core.SuggestionPicker
import dev.devkey.keyboard.core.SwipeActionHandler
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.core.KeyboardActionListener
import dev.devkey.keyboard.feature.clipboard.DevKeyClipboardManager
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.ui.keyboard.ComposeKeyboardViewFactory
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
class LatinIME : InputMethodService(),
    KeyboardActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    WordPromotionDelegate {

    private var mCandidateViewContainer: LinearLayout? = null
    internal var mCandidateView: CandidateView? = null
    internal var mSuggest: Suggest? = null
    internal var mCompletions: Array<CompletionInfo>? = null

    private var mOptionsDialog: AlertDialog? = null

    private var clipboardManager: DevKeyClipboardManager? = null
    internal var commandModeDetector: CommandModeDetector? = null

    /* package */ internal var mKeyboardSwitcher: KeyboardSwitcher? = null

    internal var mUserDictionary: UserDictionary? = null
    internal var mUserBigramDictionary: UserBigramDictionary? = null
    internal var mAutoDictionary: AutoDictionary? = null

    internal var mResources: Resources? = null

    internal var mInputLocale: String? = null
    internal var mSystemLocale: String? = null
    internal var mLanguageSwitcher: LanguageSwitcher? = null

    internal val mComposing = StringBuilder()
    internal var mWord = WordComposer()
    internal var mCommittedLength = 0
    internal var mPredicting = false
    internal var mEnableVoiceButton = false
    internal var mBestWord: CharSequence? = null
    internal var mPredictionOnForMode = false
    internal var mPredictionOnPref = false
    internal var mCompletionOn = false
    internal var mHasDictionary = false
    internal var mAutoSpace = false
    internal var mJustAddedAutoSpace = false
    internal var mAutoCorrectEnabled = false
    internal var mReCorrectionEnabled = false
    internal var mAutoCorrectOn = false
    internal var mModCtrl = false
    internal var mModAlt = false
    internal var mModMeta = false
    internal var mModFn = false
    // Saved shift state when leaving alphabet mode, or when applying multitouch shift
    internal var mSavedShiftState = 0
    internal var mPasswordText = false
    internal var mPopupOn = false
    internal var mAutoCapPref = false
    internal var mAutoCapActive = false
    internal var mDeadKeysActive = false
    internal var mQuickFixes = false
    internal var mShowSuggestions = false
    internal var mIsShowingHint = false

    internal var mFullscreenOverride = false
    internal var mForceKeyboardOn = false
    internal var mKeyboardNotification = false
    internal var mSuggestionsInLandscape = false
    internal var mSuggestionForceOn = false
    internal var mSuggestionForceOff = false

    internal var mHeightPortrait = 0
    internal var mHeightLandscape = 0
    internal var mNumKeyboardModes = 3
    internal var mKeyboardModeOverridePortrait = 0
    internal var mKeyboardModeOverrideLandscape = 0
    internal var mCorrectionMode = 0
    internal var mEnableVoice = true
    internal var mVoiceOnPrimary = false
    internal var mOrientation = 0
    internal var mSuggestPuncList: MutableList<CharSequence>? = null
    // Keep track of the last selection range to decide if we need to show word alternatives
    internal var mLastSelectionStart = 0
    internal var mLastSelectionEnd = 0

    // Input type is such that we should not auto-correct
    internal var mInputTypeNoAutoCorrect = false

    internal var mJustAccepted = false
    internal var mJustRevertedSeparator: CharSequence? = null
    internal var mDeleteCount = 0
    internal var mLastKeyTime: Long = 0

    // Modifier keys state
    internal val mShiftKeyState = ChordeTracker()
    internal val mSymbolKeyState = ChordeTracker()
    internal val mCtrlKeyState = ChordeTracker()
    internal val mAltKeyState = ChordeTracker()
    internal val mMetaKeyState = ChordeTracker()
    internal val mFnKeyState = ChordeTracker()

    // Compose sequence handling
    internal var mComposeMode = false
    internal val mComposeBuffer = ComposeSequence(
        onComposedText = { text -> onText(text) },
        updateShiftState = { attr -> updateShiftKeyState(attr) },
        editorInfoProvider = { currentInputEditorInfo }
    )
    internal val mDeadAccentBuffer: ComposeSequence = DeadAccentSequence(
        onComposedText = { text -> onText(text) },
        updateShiftState = { attr -> updateShiftKeyState(attr) },
        editorInfoProvider = { currentInputEditorInfo }
    )

    internal lateinit var mFeedbackManager: FeedbackManager
    internal lateinit var mSwipeHandler: SwipeActionHandler
    internal lateinit var mPuncHeuristics: PunctuationHeuristics

    /* package */ internal var mWordSeparators: String? = null
    internal var mSentenceSeparators: String? = null
    internal var mConfigurationChanging = false

    // Keeps track of most recently inserted text (multi-character key) for reverting
    internal var mEnteredText: CharSequence? = null
    internal var mRefreshKeyboardRequired = false

    internal val mWordHistory = ArrayList<WordAlternatives>()

    internal var mPluginManager: PluginManager? = null
    internal lateinit var mNotificationController: NotificationController
    private var keyMapDumpReceiver: BroadcastReceiver? = null
    private var mDebugReceivers: dev.devkey.keyboard.debug.DebugReceiverManager? = null

    /* package */ internal lateinit var mKeyEventSender: KeyEventSender
    internal lateinit var mPreferenceObserver: PreferenceObserver
    internal lateinit var mSuggestionCoordinator: SuggestionCoordinator
    internal lateinit var mSuggestionPicker: SuggestionPicker
    private lateinit var mInputDispatcher: InputDispatcher
    private lateinit var mInputHandlers: InputHandlers
    internal lateinit var mModifierHandler: ModifierHandler
    private lateinit var mInputViewSetup: InputViewSetup
    internal lateinit var mDictionaryManager: DictionaryManager

    internal val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal var suggestionsJob: Job? = null
    internal var oldSuggestionsJob: Job? = null

    override fun onCreate() {
        Log.i(TAG, "onCreate(), os.version=${System.getProperty("os.version")}")
        super.onCreate()
        sInstance = this
        mPreferenceObserver = PreferenceObserver(this)
        mSuggestionCoordinator = SuggestionCoordinator(this)
        mSuggestionPicker = SuggestionPicker(this, mSuggestionCoordinator)
        mInputHandlers = InputHandlers(this)
        mInputDispatcher = InputDispatcher(this, mInputHandlers)
        mModifierHandler = ModifierHandler(this)
        mInputViewSetup = InputViewSetup(this)
        mDictionaryManager = DictionaryManager(this)
        val result = ImeInitializer(this).initialize()
        clipboardManager = result.clipboardManager
        commandModeDetector = result.commandModeDetector
        mPluginManager = result.pluginManager
        mSystemLocale = result.systemLocale
        mDebugReceivers = result.debugReceivers
    }

    private fun getKeyboardModeNum(origMode: Int, override: Int): Int {
        var mode = origMode
        if (mNumKeyboardModes == 2 && mode == 2) mode = 1 // skip "compact". FIXME!
        var num = (mode + override) % mNumKeyboardModes
        if (mNumKeyboardModes == 2 && num == 1) num = 2 // skip "compact". FIXME!
        return num
    }

    internal fun updateKeyboardOptions() {
        val isPortrait = isPortrait()
        mNumKeyboardModes = if (sKeyboardSettings.compactModeEnabled) 3 else 2 // FIXME!
        val kbMode = if (isPortrait) {
            getKeyboardModeNum(sKeyboardSettings.keyboardModePortrait, mKeyboardModeOverridePortrait)
        } else {
            getKeyboardModeNum(sKeyboardSettings.keyboardModeLandscape, mKeyboardModeOverrideLandscape)
        }
        // Convert overall keyboard height to per-row percentage
        val screenHeightPercent = if (isPortrait) mHeightPortrait else mHeightLandscape
        sKeyboardSettings.keyboardMode = kbMode
        sKeyboardSettings.keyboardHeightPercent = screenHeightPercent.toFloat()
    }

    private fun setNotification(visible: Boolean) = mNotificationController.setNotification(visible)

    internal fun isPortrait(): Boolean = mOrientation == Configuration.ORIENTATION_PORTRAIT

    internal fun suggestionsDisabled(): Boolean {
        if (mSuggestionForceOff) return true
        if (mSuggestionForceOn) return false
        return !(mSuggestionsInLandscape || isPortrait())
    }

    internal fun initSuggest(locale: String) = mDictionaryManager.initSuggest(locale)

    override fun onDestroy() {
        serviceScope.cancel()
        clipboardManager?.stopListening()
        keyMapDumpReceiver?.let { unregisterReceiver(it) }
        mDebugReceivers?.unregisterAll()
        mUserDictionary?.close()
        unregisterReceiver(mFeedbackManager.ringerModeReceiver)
        unregisterReceiver(mPluginManager)
        mNotificationController.destroy()
        sInstance = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(conf: Configuration) {
        Log.i(TAG, "onConfigurationChanged()")
        val systemLocale = conf.locales[0].toString()
        if (systemLocale != mSystemLocale) {
            mSystemLocale = systemLocale
            if (mLanguageSwitcher != null) {
                mLanguageSwitcher!!.loadLocales(
                    PreferenceManager.getDefaultSharedPreferences(this)
                )
                mLanguageSwitcher!!.setSystemLocale(conf.locales[0])
                toggleLanguage(reset = true, next = true)
            } else {
                reloadKeyboards()
            }
        }
        // If orientation changed while predicting, commit the change
        if (conf.orientation != mOrientation) {
            val ic = currentInputConnection
            commitTyped(ic, true)
            ic?.finishComposingText()
            mOrientation = conf.orientation
            reloadKeyboards()
            removeCandidateViewContainer()
        }
        mConfigurationChanging = true
        super.onConfigurationChanged(conf)
        mConfigurationChanging = false
    }

    override fun onCreateInputView(): View {
        setCandidatesViewShown(false) // Workaround for "already has a parent" when reconfiguring

        val decor = window.window!!.decorView
        return ComposeKeyboardViewFactory.create(this, this, decor)
    }

    override fun onCreateCandidatesView(): View? {
        if (mCandidateViewContainer == null) {
            mCandidateViewContainer = layoutInflater.inflate(
                R.layout.candidates, null
            ) as LinearLayout
            mCandidateView = mCandidateViewContainer!!.findViewById(R.id.candidates)
            mCandidateView!!.setPadding(0, 0, 0, 0)
            mCandidateView!!.setService(this)
            setCandidatesView(mCandidateViewContainer)
        }
        return mCandidateViewContainer
    }

    private fun removeCandidateViewContainer() {
        if (mCandidateViewContainer != null) {
            mCandidateViewContainer!!.removeAllViews()
            val parent = mCandidateViewContainer!!.parent
            if (parent is ViewGroup) {
                parent.removeView(mCandidateViewContainer)
            }
            mCandidateViewContainer = null
            mCandidateView = null
        }
        resetPrediction()
    }

    internal fun resetPrediction() {
        mComposing.setLength(0)
        mPredicting = false
        mDeleteCount = 0
        mJustAddedAutoSpace = false
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) =
        mInputViewSetup.onStartInputView(attribute, restarting)

    override fun onFinishInput() {
        super.onFinishInput()
        onAutoCompletionStateChanged(false)
        mAutoDictionary?.flushPendingWrites()
        mUserBigramDictionary?.flushPendingWrites()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        suggestionsJob?.cancel()
        oldSuggestionsJob?.cancel()
    }

    override fun onUpdateExtractedText(token: Int, text: android.view.inputmethod.ExtractedText) {
        super.onUpdateExtractedText(token, text)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd
        )
        mSuggestionCoordinator.onSelectionChanged(
            oldSelStart, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
    }

    override fun onExtractedTextClicked() {
        if (mReCorrectionEnabled && isPredictionOn()) return
        super.onExtractedTextClicked()
    }

    override fun onExtractedCursorMovement(dx: Int, dy: Int) {
        if (mReCorrectionEnabled && isPredictionOn()) return
        super.onExtractedCursorMovement(dx, dy)
    }

    override fun hideWindow() {
        onAutoCompletionStateChanged(false)
        if (mOptionsDialog != null && mOptionsDialog!!.isShowing) {
            mOptionsDialog!!.dismiss()
            mOptionsDialog = null
        }
        mWordHistory.clear()
        super.hideWindow()
        TextEntryState.endSession()
    }

    override fun onDisplayCompletions(completions: Array<CompletionInfo>?) {
        if (mCompletionOn) {
            mCompletions = completions
            if (completions == null) {
                mSuggestionCoordinator.clearSuggestions()
                return
            }

            val stringList = mutableListOf<CharSequence>()
            for (ci in completions) {
                ci.text?.let { stringList.add(it) }
            }
            mSuggestionCoordinator.setSuggestions(stringList, completions = true, typedWordValid = true, haveMinimalSuggestion = true)
            mBestWord = null
            setCandidatesViewShown(true)
        }
    }

    internal fun setCandidatesViewShownInternal(shown: Boolean, needsInputViewShown: Boolean) {
        val visible = shown
            && onEvaluateInputViewShown()
            && isPredictionOn()
            && (if (needsInputViewShown) isInputViewShown else true)
        if (visible) {
            if (mCandidateViewContainer == null) {
                onCreateCandidatesView()
                setNextSuggestions()
            }
        } else {
            if (mCandidateViewContainer != null) {
                removeCandidateViewContainer()
                commitTyped(currentInputConnection, true)
            }
        }
        super.setCandidatesViewShown(visible)
    }

    override fun onFinishCandidatesView(finishingInput: Boolean) {
        super.onFinishCandidatesView(finishingInput)
        if (mCandidateViewContainer != null) {
            removeCandidateViewContainer()
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val parent = super.onEvaluateInputViewShown()
        return mForceKeyboardOn || parent
    }

    override fun setCandidatesViewShown(shown: Boolean) {
        setCandidatesViewShownInternal(shown, needsInputViewShown = true)
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (!isFullscreenMode) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val dm = resources.displayMetrics
        val displayHeight = dm.heightPixels.toFloat()
        val dimen = resources.getDimension(R.dimen.max_height_for_fullscreen)
        return if (displayHeight > dimen || mFullscreenOverride) {
            false
        } else {
            super.onEvaluateFullscreenMode()
        }
    }

    fun isKeyboardVisible(): Boolean = isInputViewShown

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                // Legacy view popup handling removed; Compose keyboard handles back key internally
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (mSwipeHandler.volUpAction != "none" && isKeyboardVisible()) {
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (mSwipeHandler.volDownAction != "none" && isKeyboardVisible()) {
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Enable shift key and DPAD to do selections
                if (isKeyboardVisible()
                    && mKeyboardSwitcher!!.getShiftState() == Keyboard.SHIFT_ON
                ) {
                    val modifiedEvent = KeyEvent(
                        event.downTime, event.eventTime,
                        event.action, event.keyCode,
                        event.repeatCount, event.deviceId,
                        event.scanCode,
                        KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON
                    )
                    currentInputConnection?.sendKeyEvent(modifiedEvent)
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (mSwipeHandler.volUpAction != "none" && isKeyboardVisible()) {
                    return mSwipeHandler.doSwipeAction(mSwipeHandler.volUpAction)
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (mSwipeHandler.volDownAction != "none" && isKeyboardVisible()) {
                    return mSwipeHandler.doSwipeAction(mSwipeHandler.volDownAction)
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    internal fun reloadKeyboards() {
        mKeyboardSwitcher!!.setLanguageSwitcher(mLanguageSwitcher!!)
        if (mKeyboardSwitcher!!.getKeyboardMode() != KeyboardSwitcher.MODE_NONE) {
            mKeyboardSwitcher!!.setVoiceMode(
                mEnableVoice && mEnableVoiceButton,
                mVoiceOnPrimary
            )
        }
        updateKeyboardOptions()
        mKeyboardSwitcher!!.makeKeyboards(true)
    }

    internal fun commitTyped(inputConnection: InputConnection?, manual: Boolean) =
        mInputHandlers.commitTyped(inputConnection, manual)

    fun updateShiftKeyState(attr: EditorInfo?) = mModifierHandler.updateShiftKeyState(attr)
    internal fun getShiftState(): Int = mModifierHandler.getShiftState()
    internal fun isShiftCapsMode(): Boolean = mModifierHandler.isShiftCapsMode()
    internal fun getCursorCapsMode(ic: InputConnection, attr: EditorInfo): Int = mModifierHandler.getCursorCapsMode(ic, attr)

    internal fun swapPunctuationAndSpace() {
        mPuncHeuristics.swapPunctuationAndSpace()
        // mJustAddedAutoSpace handled at call site
    }
    internal fun reswapPeriodAndSpace() = mPuncHeuristics.reswapPeriodAndSpace()
    internal fun doubleSpace() {
        mPuncHeuristics.doubleSpace(mCorrectionMode)
        // mJustAddedAutoSpace handled at call site
    }
    internal fun maybeRemovePreviousPeriod(text: CharSequence) = mPuncHeuristics.maybeRemovePreviousPeriod(text)
    internal fun removeTrailingSpace() = mPuncHeuristics.removeTrailingSpace()

    fun addWordToDictionary(word: String): Boolean {
        mUserDictionary!!.addWord(word, 128)
        mSuggestionCoordinator.postUpdateSuggestions()
        return true
    }

    internal fun isAlphabet(code: Int): Boolean = Character.isLetter(code)

    internal fun showInputMethodPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    internal fun onOptionKeyPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startActivity(Intent(this, DevKeySettingsActivity::class.java))
        } else {
            if (!isShowingOptionDialog()) {
                showOptionsMenu()
            }
        }
    }

    internal fun onOptionKeyLongPressed() {
        if (!isShowingOptionDialog()) {
            showInputMethodPicker()
        }
    }

    internal fun isShowingOptionDialog(): Boolean =
        mOptionsDialog != null && mOptionsDialog!!.isShowing


    internal fun isShiftMod(): Boolean = mModifierHandler.isShiftMod()

    fun sendModifiableKeyChar(ch: Char) {
        mKeyEventSender.sendModifiableKeyChar(ch)
    }

    internal fun processMultiKey(primaryCode: Int): Boolean {
        if (mDeadAccentBuffer.hasBufferedKeys()) {
            mDeadAccentBuffer.execute(primaryCode)
            mDeadAccentBuffer.clear()
            return true
        }
        if (mComposeMode) {
            mComposeMode = mComposeBuffer.execute(primaryCode)
            return true
        }
        return false
    }

    // Implementation of KeyboardActionListener

    override fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) =
        mInputDispatcher.onKey(primaryCode, keyCodes, x, y)
    override fun onText(text: CharSequence) = mInputDispatcher.onText(text)
    fun onCancel() = mInputDispatcher.onCancel()


    internal fun resetShift() = mModifierHandler.resetShift()
    internal fun handleShift() = mModifierHandler.handleShift()

    internal fun abortCorrection(force: Boolean) {
        if (force || TextEntryState.isCorrecting()) {
            currentInputConnection?.finishComposingText()
            mSuggestionCoordinator.clearSuggestions()
        }
    }

    internal fun handleClose() {
        commitTyped(currentInputConnection, true)
        requestHideSelf(0)
        TextEntryState.endSession()
    }

    internal fun saveWordInHistory(result: CharSequence?) {
        if (mWord.size() <= 1) {
            mWord.reset()
            return
        }
        if (result.isNullOrEmpty()) return

        val resultCopy = result.toString()
        val entry = TypedWordAlternatives(resultCopy, WordComposer(mWord), mSuggestionCoordinator::getTypedSuggestions)
        mWordHistory.add(entry)
    }

    internal fun isPredictionOn(): Boolean = mPredictionOnForMode && mSuggestionCoordinator.isPredictionWanted()
    fun pickSuggestionManually(index: Int, suggestion: CharSequence) = mSuggestionPicker.pickSuggestionManually(index, suggestion)
    internal fun setNextSuggestions() = mSuggestionCoordinator.setNextSuggestions()

    internal fun isCursorTouchingWord(): Boolean {
        val ic = currentInputConnection ?: return false
        val toLeft = ic.getTextBeforeCursor(1, 0)
        val toRight = ic.getTextAfterCursor(1, 0)
        if (!toLeft.isNullOrEmpty() && !isWordSeparator(toLeft[0].code)
            && !isSuggestedPunctuation(toLeft[0].code)
        ) {
            return true
        }
        if (!toRight.isNullOrEmpty() && !isWordSeparator(toRight[0].code)
            && !isSuggestedPunctuation(toRight[0].code)
        ) {
            return true
        }
        return false
    }

    internal fun sameAsTextBeforeCursor(ic: InputConnection, text: CharSequence): Boolean {
        val beforeText = ic.getTextBeforeCursor(text.length, 0)
        return text == beforeText
    }

    fun revertLastWord(deleteChar: Boolean) = mInputHandlers.revertLastWord(deleteChar)

    protected fun getWordSeparators(): String = mWordSeparators ?: ""

    fun isWordSeparator(code: Int): Boolean {
        val separators = getWordSeparators()
        return separators.indexOf(code.toChar()) >= 0
    }

    internal fun sendSpace() {
        sendModifiableKeyChar(ASCII_SPACE.toChar())
        updateShiftKeyState(currentInputEditorInfo)
    }

    fun preferCapitalization(): Boolean = mWord.isFirstCharCapitalized()

    internal fun toggleLanguage(reset: Boolean, next: Boolean) =
        mDictionaryManager.toggleLanguage(reset, next)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) =
        mPreferenceObserver.onPreferenceChanged(sharedPreferences, key)

    fun swipeRight(): Boolean = mSwipeHandler.swipeRight()
    fun swipeLeft(): Boolean = mSwipeHandler.swipeLeft()
    fun swipeDown(): Boolean = mSwipeHandler.swipeDown()
    fun swipeUp(): Boolean = mSwipeHandler.swipeUp()

    override fun onPress(primaryCode: Int) = mModifierHandler.onPress(primaryCode)
    override fun onRelease(primaryCode: Int) = mModifierHandler.onRelease(primaryCode)

    internal fun vibrate(len: Int) = mFeedbackManager.vibrate(len)

    override fun promoteToUserDictionary(word: String, frequency: Int) {
        if (mUserDictionary!!.isValidWord(word)) return
        mUserDictionary!!.addWord(word, frequency)
    }

    override fun getCurrentWord(): WordComposer = mWord

    /* package */ internal fun getPopupOn(): Boolean = mPopupOn

    internal fun launchSettings() {
        launchSettings(DevKeySettingsActivity::class.java)
    }

    internal fun launchSettings(settingsClass: Class<out Activity>) {
        handleClose()
        val intent = Intent().apply {
            setClass(this@LatinIME, settingsClass)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    internal fun loadSettings() = mPreferenceObserver.loadSettings()

    internal fun isSuggestedPunctuation(code: Int): Boolean = mPuncHeuristics.isSuggestedPunctuation(code)

    private fun showOptionsMenu() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setIcon(R.drawable.ic_dialog_keyboard)
        builder.setNegativeButton(android.R.string.cancel, null)
        val itemSettings = getString(R.string.english_ime_settings)
        val itemInputMethod = getString(R.string.selectInputMethod)
        builder.setItems(arrayOf<CharSequence>(itemInputMethod, itemSettings)) { di, position ->
            di.dismiss()
            when (position) {
                POS_SETTINGS -> launchSettings()
                POS_METHOD -> (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        }
        builder.setTitle(mResources!!.getString(R.string.english_ime_input_options))
        mOptionsDialog = builder.create()
        val window = mOptionsDialog!!.window!!
        val lp = window.attributes
        val imeWindow = getWindow()?.window
        if (imeWindow?.decorView != null) {
            lp.token = imeWindow.decorView.windowToken
        }
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        mOptionsDialog!!.show()
    }

    fun changeKeyboardMode() = mModifierHandler.changeKeyboardMode()

    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<out String>?) {
        super.dump(fd, fout, args)
        val p = PrintWriterPrinter(fout)
        p.println("LatinIME state :")
        p.println("  Keyboard mode = ${mKeyboardSwitcher!!.getKeyboardMode()}")
        p.println("  mComposing=[${mComposing.length} chars]")
        p.println("  mPredictionOnForMode=$mPredictionOnForMode")
        p.println("  mCorrectionMode=$mCorrectionMode")
        p.println("  mPredicting=$mPredicting")
        p.println("  mAutoCorrectOn=$mAutoCorrectOn")
        p.println("  mAutoSpace=$mAutoSpace")
        p.println("  mCompletionOn=$mCompletionOn")
        p.println("  TextEntryState.state=${TextEntryState.getState()}")
        p.println("  mSoundOn=${mFeedbackManager.soundOn}")
        p.println("  mVibrateOn=${mFeedbackManager.vibrateOn}")
        p.println("  mPopupOn=$mPopupOn")
    }

    fun onAutoCompletionStateChanged(isAutoCompletion: Boolean) {
        mKeyboardSwitcher!!.onAutoCompletionStateChanged(isAutoCompletion)
    }

    companion object {
        private const val TAG = "DevKey/LatinIME"

        // Re-export for external callers (InputLanguageSelection, LanguageSwitcher)
        @JvmField val PREF_SELECTED_LANGUAGES = dev.devkey.keyboard.PREF_SELECTED_LANGUAGES
        @JvmField val PREF_INPUT_LANGUAGE = dev.devkey.keyboard.PREF_INPUT_LANGUAGE

        lateinit var sKeyboardSettings: SettingsRepository
            internal set

        // Strong reference intentionally held for the service lifetime so that other
        // components (KeyboardSwitcher, CandidateView, etc.) can reach the service.
        // Set to null in onDestroy() to prevent leaks after the service is destroyed.
        var sInstance: LatinIME? = null
            private set

        fun getDictionary(res: Resources): IntArray = ImePrefsUtil.getDictionary(res)
        fun getIntFromString(value: String, defVal: Int): Int = ImePrefsUtil.getIntFromString(value, defVal)
        fun getPrefInt(prefs: SharedPreferences, prefName: String, defVal: Int): Int = ImePrefsUtil.getPrefInt(prefs, prefName, defVal)
        fun getPrefInt(prefs: SharedPreferences, prefName: String, defStr: String): Int = ImePrefsUtil.getPrefInt(prefs, prefName, defStr)
        fun getHeight(prefs: SharedPreferences, prefName: String, defVal: String): Int = ImePrefsUtil.getHeight(prefs, prefName, defVal)

    }
}
