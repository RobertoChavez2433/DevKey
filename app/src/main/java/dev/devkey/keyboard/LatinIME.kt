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
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.Vibrator
import android.util.DisplayMetrics
import android.util.Log
import android.util.PrintWriterPrinter
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.core.FeedbackManager
import dev.devkey.keyboard.core.KeyEventSender
import dev.devkey.keyboard.core.NotificationController
import dev.devkey.keyboard.core.PunctuationHeuristics
import dev.devkey.keyboard.core.SwipeActionHandler
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.core.KeyboardActionListener
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.clipboard.DevKeyClipboardManager
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.feature.prediction.AutocorrectResult
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.prediction.PredictionEngine
import dev.devkey.keyboard.feature.prediction.TrieDictionary
import dev.devkey.keyboard.ui.keyboard.ComposeKeyboardViewFactory
import dev.devkey.keyboard.ui.keyboard.LayoutMode
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
class LatinIME : InputMethodService(),
    KeyboardActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    WordPromotionDelegate {

    private var mCandidateViewContainer: LinearLayout? = null
    private var mCandidateView: CandidateView? = null
    private var mSuggest: Suggest? = null
    private var mCompletions: Array<CompletionInfo>? = null

    private var mOptionsDialog: AlertDialog? = null

    private var clipboardManager: DevKeyClipboardManager? = null
    private var commandModeDetector: CommandModeDetector? = null

    /* package */ internal var mKeyboardSwitcher: KeyboardSwitcher? = null

    private var mUserDictionary: UserDictionary? = null
    private var mUserBigramDictionary: UserBigramDictionary? = null
    private var mAutoDictionary: AutoDictionary? = null

    private var mResources: Resources? = null

    private var mInputLocale: String? = null
    private var mSystemLocale: String? = null
    private var mLanguageSwitcher: LanguageSwitcher? = null

    private val mComposing = StringBuilder()
    private var mWord = WordComposer()
    private var mCommittedLength = 0
    private var mPredicting = false
    private var mEnableVoiceButton = false
    private var mBestWord: CharSequence? = null
    private var mPredictionOnForMode = false
    private var mPredictionOnPref = false
    private var mCompletionOn = false
    private var mHasDictionary = false
    private var mAutoSpace = false
    private var mJustAddedAutoSpace = false
    private var mAutoCorrectEnabled = false
    private var mReCorrectionEnabled = false
private var mAutoCorrectOn = false
    private var mModCtrl = false
    private var mModAlt = false
    private var mModMeta = false
    private var mModFn = false
    // Saved shift state when leaving alphabet mode, or when applying multitouch shift
    private var mSavedShiftState = 0
    private var mPasswordText = false
    private var mPopupOn = false
    private var mAutoCapPref = false
    private var mAutoCapActive = false
    private var mDeadKeysActive = false
    private var mQuickFixes = false
    private var mShowSuggestions = false
    private var mIsShowingHint = false

    private var mFullscreenOverride = false
    private var mForceKeyboardOn = false
    private var mKeyboardNotification = false
    private var mSuggestionsInLandscape = false
    private var mSuggestionForceOn = false
    private var mSuggestionForceOff = false

    private var mHeightPortrait = 0
    private var mHeightLandscape = 0
    private var mNumKeyboardModes = 3
    private var mKeyboardModeOverridePortrait = 0
    private var mKeyboardModeOverrideLandscape = 0
    private var mCorrectionMode = 0
    private var mEnableVoice = true
    private var mVoiceOnPrimary = false
    private var mOrientation = 0
    private var mSuggestPuncList: MutableList<CharSequence>? = null
    // Keep track of the last selection range to decide if we need to show word alternatives
    private var mLastSelectionStart = 0
    private var mLastSelectionEnd = 0

    // Input type is such that we should not auto-correct
    private var mInputTypeNoAutoCorrect = false

    // Indicates whether the suggestion strip is to be on in landscape
    private var mJustAccepted = false
    private var mJustRevertedSeparator: CharSequence? = null
    private var mDeleteCount = 0
    private var mLastKeyTime: Long = 0

    // Modifier keys state
    private val mShiftKeyState = ChordeTracker()
    private val mSymbolKeyState = ChordeTracker()
    private val mCtrlKeyState = ChordeTracker()
    private val mAltKeyState = ChordeTracker()
    private val mMetaKeyState = ChordeTracker()
    private val mFnKeyState = ChordeTracker()

    // Compose sequence handling
    private var mComposeMode = false
    private val mComposeBuffer = ComposeSequence(
        onComposedText = { text -> onText(text) },
        updateShiftState = { attr -> updateShiftKeyState(attr) },
        editorInfoProvider = { currentInputEditorInfo }
    )
    private val mDeadAccentBuffer: ComposeSequence = DeadAccentSequence(
        onComposedText = { text -> onText(text) },
        updateShiftState = { attr -> updateShiftKeyState(attr) },
        editorInfoProvider = { currentInputEditorInfo }
    )

    private lateinit var mFeedbackManager: FeedbackManager
    private lateinit var mSwipeHandler: SwipeActionHandler
    private lateinit var mPuncHeuristics: PunctuationHeuristics

    /* package */ internal var mWordSeparators: String? = null
    private var mSentenceSeparators: String? = null
    private var mConfigurationChanging = false

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private var mEnteredText: CharSequence? = null
    private var mRefreshKeyboardRequired = false

    private val mWordHistory = ArrayList<WordAlternatives>()

    private var mPluginManager: PluginManager? = null
    private lateinit var mNotificationController: NotificationController
    private var keyMapDumpReceiver: BroadcastReceiver? = null
    private var mDebugReceivers: dev.devkey.keyboard.debug.DebugReceiverManager? = null

    /* package */ internal lateinit var mKeyEventSender: KeyEventSender


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var suggestionsJob: Job? = null
    private var oldSuggestionsJob: Job? = null

    override fun onCreate() {
        Log.i(TAG, "onCreate(), os.version=${System.getProperty("os.version")}")
        super.onCreate()
        sInstance = this

        // Initialize unified settings BEFORE KeyboardSwitcher (it reads sKeyboardSettings)
        val settingsPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        sKeyboardSettings = SettingsRepository(settingsPrefs)
        mFeedbackManager = FeedbackManager(
            this,
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator,
            sKeyboardSettings
        )

        KeyboardSwitcher.init(this)

        // Load compose sequences from raw resource
        ComposeSequenceLoader.load(this)

        // Initialize clipboard listener
        val clipboardRepo = ClipboardRepository(
            DevKeyDatabase.getInstance(this).clipboardHistoryDao()
        )
        clipboardManager = DevKeyClipboardManager(this, clipboardRepo)
        clipboardManager?.startListening()

        // Session 4: Initialize command mode detection
        val cmdRepo = CommandModeRepository(
            DevKeyDatabase.getInstance(this).commandAppDao()
        )
        commandModeDetector = CommandModeDetector(cmdRepo)
        SessionDependencies.commandModeDetector = commandModeDetector

        mResources = resources
        val conf = mResources!!.configuration
        mOrientation = conf.orientation
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        mLanguageSwitcher = LanguageSwitcher(this)
        mLanguageSwitcher!!.loadLocales(prefs)
        mKeyboardSwitcher = KeyboardSwitcher.getInstance()
        mKeyboardSwitcher!!.setLanguageSwitcher(mLanguageSwitcher!!)
        mSystemLocale = conf.locales[0].toString()
        mLanguageSwitcher!!.setSystemLocale(conf.locales[0])
        var inputLanguage: String = mLanguageSwitcher!!.getInputLanguage()
            ?: conf.locales[0].toString()
        val res = resources
        mReCorrectionEnabled = prefs.getBoolean(
            PREF_RECORRECTION_ENABLED,
            res.getBoolean(R.bool.default_recorrection_enabled)
        )

        mFullscreenOverride = prefs.getBoolean(
            PREF_FULLSCREEN_OVERRIDE,
            res.getBoolean(R.bool.default_fullscreen_override)
        )
        mForceKeyboardOn = prefs.getBoolean(
            PREF_FORCE_KEYBOARD_ON,
            res.getBoolean(R.bool.default_force_keyboard_on)
        )
        mKeyboardNotification = prefs.getBoolean(
            PREF_KEYBOARD_NOTIFICATION,
            res.getBoolean(R.bool.default_keyboard_notification)
        )
        mSuggestionsInLandscape = prefs.getBoolean(
            PREF_SUGGESTIONS_IN_LANDSCAPE,
            res.getBoolean(R.bool.default_suggestions_in_landscape)
        )
        mHeightPortrait = getHeight(
            prefs, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait)
        )
        mHeightLandscape = getHeight(
            prefs, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape)
        )
        sKeyboardSettings.hintMode = prefs.getString(
            PREF_HINT_MODE, res.getString(R.string.default_hint_mode)
        )!!.toInt()
        sKeyboardSettings.longpressTimeout = getPrefInt(
            prefs, PREF_LONGPRESS_TIMEOUT, res.getString(R.string.default_long_press_duration)
        )
        sKeyboardSettings.renderMode = getPrefInt(
            prefs, PREF_RENDER_MODE, res.getString(R.string.default_render_mode)
        )
        mSwipeHandler = SwipeActionHandler(
            handleClose = { handleClose() },
            launchSettings = { launchSettings() },
            toggleSuggestions = {
                if (mSuggestionForceOn) { mSuggestionForceOn = false; mSuggestionForceOff = true }
                else if (mSuggestionForceOff) { mSuggestionForceOn = true; mSuggestionForceOff = false }
                else if (isPredictionWanted()) { mSuggestionForceOff = true }
                else { mSuggestionForceOn = true }
                setCandidatesViewShown(isPredictionOn())
            },
            toggleLanguagePrev = { toggleLanguage(false, false) },
            toggleLanguageNext = { toggleLanguage(false, true) },
            cycleFullMode = {
                if (isPortrait()) {
                    mKeyboardModeOverridePortrait = (mKeyboardModeOverridePortrait + 1) % mNumKeyboardModes
                } else {
                    mKeyboardModeOverrideLandscape = (mKeyboardModeOverrideLandscape + 1) % mNumKeyboardModes
                }
                toggleLanguage(reset = true, next = true)
            },
            toggleExtension = {
                sKeyboardSettings.useExtension = !sKeyboardSettings.useExtension
                reloadKeyboards()
            },
            adjustHeightUp = {
                if (isPortrait()) { mHeightPortrait += 5; if (mHeightPortrait > 70) mHeightPortrait = 70 }
                else { mHeightLandscape += 5; if (mHeightLandscape > 70) mHeightLandscape = 70 }
                toggleLanguage(reset = true, next = true)
            },
            adjustHeightDown = {
                if (isPortrait()) { mHeightPortrait -= 5; if (mHeightPortrait < 15) mHeightPortrait = 15 }
                else { mHeightLandscape -= 5; if (mHeightLandscape < 15) mHeightLandscape = 15 }
                toggleLanguage(reset = true, next = true)
            }
        )
        mSwipeHandler.swipeUpAction = prefs.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
        mSwipeHandler.swipeDownAction = prefs.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
        mSwipeHandler.swipeLeftAction = prefs.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
        mSwipeHandler.swipeRightAction = prefs.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right))
        mSwipeHandler.volUpAction = prefs.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
        mSwipeHandler.volDownAction = prefs.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
        sKeyboardSettings.initPrefs(prefs, res)

        mKeyEventSender = KeyEventSender(
            inputConnectionProvider = { currentInputConnection },
            modCtrlProvider = { mModCtrl },
            modAltProvider = { mModAlt },
            modMetaProvider = { mModMeta },
            shiftKeyStateProvider = { mShiftKeyState },
            ctrlKeyStateProvider = { mCtrlKeyState },
            altKeyStateProvider = { mAltKeyState },
            metaKeyStateProvider = { mMetaKeyState },

            shiftModProvider = { isShiftMod() },
            setModCtrl = { setModCtrl(it) },
            setModAlt = { setModAlt(it) },
            setModMeta = { setModMeta(it) },
            resetShift = { resetShift() },
            getShiftState = { getShiftState() },
            commitTyped = { ic, manual -> commitTyped(ic, manual) },
            sendKeyCharFn = { ch -> sendKeyChar(ch) },
            sendDownUpKeyEventsFn = { code -> sendDownUpKeyEvents(code) },
            settings = sKeyboardSettings,
            ctrlAToastAction = {
                Toast.makeText(
                    applicationContext,
                    resources.getString(R.string.toast_ctrl_a_override_info),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        mPuncHeuristics = PunctuationHeuristics(
            icProvider = { currentInputConnection },
            settings = sKeyboardSettings,
            updateShiftKeyState = { updateShiftKeyState(currentInputEditorInfo) }
        )

        updateKeyboardOptions()

        PluginManager.getPluginDictionaries(applicationContext)
        mPluginManager = PluginManager(this)
        val pFilter = IntentFilter().apply {
            addDataScheme("package")
            addAction("android.intent.action.PACKAGE_ADDED")
            addAction("android.intent.action.PACKAGE_REPLACED")
            addAction("android.intent.action.PACKAGE_REMOVED")
        }
        registerReceiver(mPluginManager, pFilter, Context.RECEIVER_NOT_EXPORTED)

        // WHY: Phase 3 E2E — plugin_scan_complete fires at boot before the debug
        //      server is enabled, so the test can't observe it. RESCAN_PLUGINS lets
        //      the harness re-trigger the scan after the driver is connected.
        if (KeyMapGenerator.isDebugBuild(this)) {
            registerReceiver(
                mPluginManager,
                IntentFilter("dev.devkey.keyboard.RESCAN_PLUGINS"),
                Context.RECEIVER_EXPORTED
            )
        }

        try {
            initSuggest(inputLanguage)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during dictionary init for $inputLanguage", e)
        }

        mOrientation = conf.orientation

        // register to receive ringer mode changes for silent mode
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        registerReceiver(mFeedbackManager.ringerModeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        prefs.registerOnSharedPreferenceChangeListener(this)
        mNotificationController = NotificationController(this, this)
        mNotificationController.setNotification(mKeyboardNotification)

        // Debug: register broadcast receivers for test harness
        if (KeyMapGenerator.isDebugBuild(this)) {
            keyMapDumpReceiver = null
            mDebugReceivers = dev.devkey.keyboard.debug.DebugReceiverManager(this)
            mDebugReceivers!!.registerAll()
        }
    }

    private fun getKeyboardModeNum(origMode: Int, override: Int): Int {
        var mode = origMode
        if (mNumKeyboardModes == 2 && mode == 2) mode = 1 // skip "compact". FIXME!
        var num = (mode + override) % mNumKeyboardModes
        if (mNumKeyboardModes == 2 && num == 1) num = 2 // skip "compact". FIXME!
        return num
    }

    private fun updateKeyboardOptions() {
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

    private fun isPortrait(): Boolean = mOrientation == Configuration.ORIENTATION_PORTRAIT

    private fun suggestionsDisabled(): Boolean {
        if (mSuggestionForceOff) return true
        if (mSuggestionForceOn) return false
        return !(mSuggestionsInLandscape || isPortrait())
    }

    /**
     * Loads a dictionary or multiple separated dictionary
     *
     * @return returns array of dictionary resource ids
     */
    private fun initSuggest(locale: String) {
        mInputLocale = locale

        val orig = resources
        val conf = orig.configuration
        val saveLocale = conf.locales[0]
        val localeConf = android.os.LocaleList.forLanguageTags(locale)
        val localizedConf = android.content.res.Configuration(conf).apply { setLocales(localeConf) }
        val localizedContext = createConfigurationContext(localizedConf)
        val localizedResources = localizedContext.resources
        if (mSuggest != null) {
            mSuggest!!.close()
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        mQuickFixes = sp.getBoolean(
            PREF_QUICK_FIXES,
            resources.getBoolean(R.bool.default_quick_fixes)
        )

        val dictionaries = getDictionary(localizedResources)
        mSuggest = Suggest(this, dictionaries)
        updateAutoTextEnabled(saveLocale)
        if (mUserDictionary != null) mUserDictionary!!.close()
        mUserDictionary = UserDictionary(this, mInputLocale!!)
        if (mAutoDictionary != null) {
            mAutoDictionary!!.close()
        }
        mAutoDictionary = AutoDictionary(
            this, this, mInputLocale!!, Suggest.DIC_AUTO
        )
        if (mUserBigramDictionary != null) {
            mUserBigramDictionary!!.close()
        }
        mUserBigramDictionary = UserBigramDictionary(
            this, this, mInputLocale!!, Suggest.DIC_USER
        )
        mSuggest!!.setUserBigramDictionary(mUserBigramDictionary)
        mSuggest!!.setUserDictionary(mUserDictionary)
        mSuggest!!.setAutoDictionary(mAutoDictionary)
        SessionDependencies.suggest = mSuggest

        // Initialize the modern prediction pipeline
        val db = DevKeyDatabase.getInstance(this)
        val dictProvider = DictionaryProvider(mSuggest)
        val acEngine = AutocorrectEngine(dictProvider)
        val learnEngine = LearningEngine(db.learnedWordDao())
        val predEngine = PredictionEngine(dictProvider, acEngine, learnEngine)
        SessionDependencies.dictionaryProvider = dictProvider
        SessionDependencies.autocorrectEngine = acEngine
        SessionDependencies.learningEngine = learnEngine
        SessionDependencies.predictionEngine = predEngine
        serviceScope.launch(Dispatchers.IO) {
            learnEngine.initialize()
            // Load modern Kotlin trie dictionary
            val trie = TrieDictionary()
            trie.load(this@LatinIME, R.raw.en_us_wordfreq)
            dictProvider.trieDictionary = trie
            Log.i(TAG, "TrieDictionary loaded: ${trie.size} words")
        }

        updateCorrectionMode()
        mWordSeparators = mResources!!.getString(R.string.word_separators)
        mSentenceSeparators = mResources!!.getString(R.string.sentence_separators)
        mPuncHeuristics.sentenceSeparators = mSentenceSeparators
        initSuggestPuncList()

        // No need to restore locale: createConfigurationContext creates a new isolated context,
        // leaving the service's own resources unchanged.
    }

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

    private fun resetPrediction() {
        mComposing.setLength(0)
        mPredicting = false
        mDeleteCount = 0
        mJustAddedAutoSpace = false
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        sKeyboardSettings.editorPackageName = attribute.packageName
        sKeyboardSettings.editorFieldName = attribute.fieldName
        sKeyboardSettings.editorFieldId = attribute.fieldId
        sKeyboardSettings.editorInputType = attribute.inputType

        if (mRefreshKeyboardRequired) {
            mRefreshKeyboardRequired = false
            toggleLanguage(reset = true, next = true)
        }

        mKeyboardSwitcher!!.makeKeyboards(false)

        TextEntryState.newSession(this)

        // Most such things we decide below in the switch statement, but we need to know
        // now whether this is a password text field
        mPasswordText = false
        val variation = attribute.inputType and EditorInfo.TYPE_MASK_VARIATION
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == 0xe0 /* EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD */
        ) {
            if ((attribute.inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
                mPasswordText = true
            }
        }

        mEnableVoiceButton = shouldShowVoiceButton(attribute)
        val enableVoiceButton = mEnableVoiceButton && mEnableVoice

        // Session 4: Detect command mode for the current app
        SessionDependencies.currentPackageName = attribute.packageName
        if (commandModeDetector != null) {
            val packageName = attribute.packageName
            serviceScope.launch(Dispatchers.IO) {
                try {
                    commandModeDetector!!.detectSync(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Command mode detection failed", e)
                }
            }
        }

        mInputTypeNoAutoCorrect = false
        mPredictionOnForMode = false
        mCompletionOn = false
        mCompletions = null
        mModCtrl = false
        mModAlt = false
        mModMeta = false
        mModFn = false
        mEnteredText = null
        mSuggestionForceOn = false
        mSuggestionForceOff = false
        mKeyboardModeOverridePortrait = 0
        mKeyboardModeOverrideLandscape = 0
        sKeyboardSettings.useExtension = false

        when (attribute.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER,
            EditorInfo.TYPE_CLASS_DATETIME,
            EditorInfo.TYPE_CLASS_PHONE -> {
                mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_PHONE,
                    attribute.imeOptions, enableVoiceButton
                )
            }
            EditorInfo.TYPE_CLASS_TEXT -> {
                mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton
                )
                mPredictionOnForMode = true
                // Make sure that passwords are not displayed in candidate view
                if (mPasswordText) {
                    mPredictionOnForMode = false
                }
                mAutoSpace = !(variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
                    || !mLanguageSwitcher!!.allowAutoSpace())

                when (variation) {
                    EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> {
                        mPredictionOnForMode = false
                        mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_EMAIL,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_URI -> {
                        mPredictionOnForMode = false
                        mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_URL,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> {
                        mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_IM,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_FILTER -> {
                        mPredictionOnForMode = false
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> {
                        mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_WEB,
                            attribute.imeOptions, enableVoiceButton
                        )
                        if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                            mInputTypeNoAutoCorrect = true
                        }
                    }
                }

                // If NO_SUGGESTIONS is set, don't do prediction.
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    mPredictionOnForMode = false
                    mInputTypeNoAutoCorrect = true
                }
                // If it's not multiline and the autoCorrect flag is not set, then don't correct
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
                    && (attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0
                ) {
                    mInputTypeNoAutoCorrect = true
                }
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOnForMode = false
                    mCompletionOn = isFullscreenMode
                }
            }
            else -> {
                mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton
                )
            }
        }
        resetPrediction()
        loadSettings()
        updateShiftKeyState(attribute)

        mPredictionOnPref = mCorrectionMode > 0 || mShowSuggestions
        setCandidatesViewShownInternal(
            isCandidateStripVisible() || mCompletionOn,
            needsInputViewShown = false
        )
        updateSuggestions()

        // If the dictionary is not big enough, don't auto correct
        mHasDictionary = mSuggest!!.hasMainDictionary()

        updateCorrectionMode()

        // If we just entered a text field, maybe it has some old text that requires correction
        checkReCorrectionOnStart()
    }

    private fun shouldShowVoiceButton(attribute: EditorInfo): Boolean = true

    private fun checkReCorrectionOnStart() {
        if (mReCorrectionEnabled && isPredictionOn()) {
            val ic = currentInputConnection ?: return
            val etr = ExtractedTextRequest().apply { token = 0 }
            val et = ic.getExtractedText(etr, 0) ?: return

            mLastSelectionStart = et.startOffset + et.selectionStart
            mLastSelectionEnd = et.startOffset + et.selectionEnd

            // Then look for possible corrections in a delayed fashion
            if (!et.text.isNullOrEmpty() && isCursorTouchingWord()) {
                postUpdateOldSuggestions()
            }
        }
    }

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

        if (mComposing.isNotEmpty() && mPredicting
            && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
            && mLastSelectionStart != newSelStart
        ) {
            mComposing.setLength(0)
            mPredicting = false
            postUpdateSuggestions()
            TextEntryState.reset()
            currentInputConnection?.finishComposingText()
        } else if (!mPredicting && !mJustAccepted) {
            when (TextEntryState.getState()) {
                TextEntryState.State.ACCEPTED_DEFAULT -> {
                    TextEntryState.reset()
                    mJustAddedAutoSpace = false // The user moved the cursor.
                }
                TextEntryState.State.SPACE_AFTER_PICKED -> {
                    mJustAddedAutoSpace = false
                }
                else -> { /* no-op */ }
            }
        }
        mJustAccepted = false

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart
        mLastSelectionEnd = newSelEnd

        if (mReCorrectionEnabled) {
            if (mKeyboardSwitcher != null && isKeyboardVisible()) {
                if (isPredictionOn()
                    && mJustRevertedSeparator == null
                    && (candidatesStart == candidatesEnd
                        || newSelStart != oldSelStart || TextEntryState.isCorrecting())
                    && (newSelStart < newSelEnd - 1 || !mPredicting)
                ) {
                    if (isCursorTouchingWord() || mLastSelectionStart < mLastSelectionEnd) {
                        postUpdateOldSuggestions()
                    } else {
                        abortCorrection(false)
                        if (mCandidateView != null
                            && mSuggestPuncList != mCandidateView!!.getSuggestions() as Any?
                            && !mCandidateView!!.isShowingAddToDictionaryHint()
                        ) {
                            setNextSuggestions()
                        }
                    }
                }
            }
        }
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
                clearSuggestions()
                return
            }

            val stringList = mutableListOf<CharSequence>()
            for (ci in completions) {
                ci.text?.let { stringList.add(it) }
            }
            setSuggestions(stringList, completions = true, typedWordValid = true, haveMinimalSuggestion = true)
            mBestWord = null
            setCandidatesViewShown(true)
        }
    }

    private fun setCandidatesViewShownInternal(shown: Boolean, needsInputViewShown: Boolean) {
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

    private fun reloadKeyboards() {
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

    private fun commitTyped(inputConnection: InputConnection?, manual: Boolean) {
        if (mPredicting) {
            mPredicting = false
            if (mComposing.isNotEmpty()) {
                inputConnection?.commitText(mComposing, 1)
                mCommittedLength = mComposing.length
                if (manual) {
                    TextEntryState.manualTyped(mComposing)
                } else {
                    TextEntryState.acceptedTyped(mComposing)
                }
                addToDictionaries(mComposing, AutoDictionary.FREQUENCY_FOR_TYPED)
                // Feed the modern learning engine
                val word = mComposing.toString()
                SessionDependencies.learningEngine?.let { le ->
                    serviceScope.launch(Dispatchers.IO) {
                        le.onWordCommitted(
                            word,
                            isCommand = SessionDependencies.commandModeDetector?.isCommandMode() == true,
                            contextApp = SessionDependencies.currentPackageName
                        )
                    }
                }
            }
            SessionDependencies.composingWord.value = ""
            SessionDependencies.pendingCorrection.value = null
            updateSuggestions()
        }
    }

    fun updateShiftKeyState(attr: EditorInfo?) {
        val ic = currentInputConnection
        if (ic != null && attr != null && mKeyboardSwitcher!!.isAlphabetMode()) {
            val oldState = getShiftState()
            val isShifted = mShiftKeyState.isChording()
            val isCapsLock = oldState == Keyboard.SHIFT_CAPS_LOCKED || oldState == Keyboard.SHIFT_LOCKED
            val isCaps = isCapsLock || getCursorCapsMode(ic, attr) != 0
            val newState = when {
                isShifted -> if (mSavedShiftState == Keyboard.SHIFT_LOCKED) Keyboard.SHIFT_CAPS else Keyboard.SHIFT_ON
                isCaps -> if (isCapsLock) getCapsOrShiftLockState() else Keyboard.SHIFT_CAPS
                else -> Keyboard.SHIFT_OFF
            }
            mKeyboardSwitcher!!.setShiftState(newState)
        }
        if (ic != null) {
            val states = KeyEvent.META_FUNCTION_ON or
                KeyEvent.META_ALT_MASK or
                KeyEvent.META_CTRL_MASK or
                KeyEvent.META_META_MASK or
                KeyEvent.META_SYM_ON
            ic.clearMetaKeyStates(states)
        }
    }

    private fun getShiftState(): Int {
        return mKeyboardSwitcher?.getShiftState() ?: Keyboard.SHIFT_OFF
    }

    private fun isShiftCapsMode(): Boolean {
        return mKeyboardSwitcher?.getShiftState() == Keyboard.SHIFT_CAPS_LOCKED
    }

    private fun getCursorCapsMode(ic: InputConnection, attr: EditorInfo): Int {
        val ei = currentInputEditorInfo
        return if (mAutoCapActive && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
            ic.getCursorCapsMode(attr.inputType)
        } else {
            0
        }
    }

    private fun swapPunctuationAndSpace() {
        mPuncHeuristics.swapPunctuationAndSpace()
        // mJustAddedAutoSpace handled at call site
    }
    private fun reswapPeriodAndSpace() = mPuncHeuristics.reswapPeriodAndSpace()
    private fun doubleSpace() {
        mPuncHeuristics.doubleSpace(mCorrectionMode)
        // mJustAddedAutoSpace handled at call site
    }
    private fun maybeRemovePreviousPeriod(text: CharSequence) = mPuncHeuristics.maybeRemovePreviousPeriod(text)
    private fun removeTrailingSpace() = mPuncHeuristics.removeTrailingSpace()

    fun addWordToDictionary(word: String): Boolean {
        mUserDictionary!!.addWord(word, 128)
        postUpdateSuggestions()
        return true
    }

    private fun isAlphabet(code: Int): Boolean = Character.isLetter(code)

    private fun showInputMethodPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun onOptionKeyPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startActivity(Intent(this, DevKeySettingsActivity::class.java))
        } else {
            if (!isShowingOptionDialog()) {
                showOptionsMenu()
            }
        }
    }

    private fun onOptionKeyLongPressed() {
        if (!isShowingOptionDialog()) {
            showInputMethodPicker()
        }
    }

    private fun isShowingOptionDialog(): Boolean =
        mOptionsDialog != null && mOptionsDialog!!.isShowing


    private fun isShiftMod(): Boolean {
        if (mShiftKeyState.isChording()) return true
        val shiftState = mKeyboardSwitcher?.getShiftState() ?: return false
        return shiftState == Keyboard.SHIFT_LOCKED || shiftState == Keyboard.SHIFT_CAPS_LOCKED
    }

    fun sendModifiableKeyChar(ch: Char) {
        mKeyEventSender.sendModifiableKeyChar(ch)
    }

    private fun processMultiKey(primaryCode: Int): Boolean {
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

    override fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) {
        val eventTime = SystemClock.uptimeMillis()
        Log.d("DevKeyPress", "IME   code=$primaryCode x=$x y=$y")
        // WHY: Phase 3 defect #15 — structured key_event observability for
        //      the e2e harness. `onKey` is the highest-level IME callback
        //      where primaryCode is still the semantic key code (ASCII for
        //      letters, negative codes for special keys). Logging here
        //      aligns with the `DevKeyPress IME code=N` line above and
        //      gives the driver `/wait` endpoint a matchable event for
        //      modifier-combo tests (code + ctrl/shift/alt/meta flags).
        // IMPORTANT: No content leakage — primaryCode is a structural key
        //            identifier, not typed text.
        dev.devkey.keyboard.debug.DevKeyLogger.text(
            "key_event",
            mapOf(
                "code" to primaryCode,
                "shift" to (getShiftState() != Keyboard.SHIFT_OFF),
                "ctrl" to mModCtrl,
                "alt" to mModAlt,
                "meta" to mModMeta
            )
        )
        if (primaryCode != Keyboard.KEYCODE_DELETE || eventTime > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0
        }
        mLastKeyTime = eventTime
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (processMultiKey(primaryCode)) return@onKey
                handleBackspace()
                mDeleteCount++
            }
            Keyboard.KEYCODE_SHIFT -> { /* handled in onPress/onRelease multitouch */ }
            Keyboard.KEYCODE_MODE_CHANGE -> { /* handled in onPress/onRelease multitouch */ }
            KeyCodes.CTRL_LEFT -> { /* handled in onPress/onRelease multitouch */ }
            KeyCodes.ALT_LEFT -> { /* handled in onPress/onRelease multitouch */ }
            KeyCodes.KEYCODE_META_LEFT -> { /* handled in onPress/onRelease multitouch */ }
            KeyCodes.KEYCODE_FN -> { /* handled in onPress/onRelease multitouch */ }
            Keyboard.KEYCODE_CANCEL -> {
                if (!isShowingOptionDialog()) handleClose()
            }
            KeyCodes.KEYCODE_OPTIONS -> onOptionKeyPressed()
            KeyCodes.KEYCODE_OPTIONS_LONGPRESS -> onOptionKeyLongPressed()
            KeyCodes.KEYCODE_COMPOSE -> {
                mComposeMode = !mComposeMode
                mComposeBuffer.clear()
            }
            KeyCodes.KEYCODE_NEXT_LANGUAGE -> toggleLanguage(false, true)
            KeyCodes.KEYCODE_PREV_LANGUAGE -> toggleLanguage(false, false)
            KeyCodes.KEYCODE_VOICE -> {
                // Legacy VoiceRecognitionTrigger removed; voice input handled by Compose UI
            }
            9 /* Tab */ -> {
                if (processMultiKey(primaryCode)) return@onKey
                mKeyEventSender.sendTab()
            }
            KeyCodes.ESCAPE -> {
                if (processMultiKey(primaryCode)) return@onKey
                mKeyEventSender.sendEscape()
            }
            KeyCodes.KEYCODE_DPAD_UP,
            KeyCodes.KEYCODE_DPAD_DOWN,
            KeyCodes.KEYCODE_DPAD_LEFT,
            KeyCodes.KEYCODE_DPAD_RIGHT,
            KeyCodes.KEYCODE_DPAD_CENTER,
            KeyCodes.KEYCODE_HOME,
            KeyCodes.KEYCODE_END,
            KeyCodes.KEYCODE_PAGE_UP,
            KeyCodes.KEYCODE_PAGE_DOWN,
            KeyCodes.KEYCODE_FKEY_F1,
            KeyCodes.KEYCODE_FKEY_F2,
            KeyCodes.KEYCODE_FKEY_F3,
            KeyCodes.KEYCODE_FKEY_F4,
            KeyCodes.KEYCODE_FKEY_F5,
            KeyCodes.KEYCODE_FKEY_F6,
            KeyCodes.KEYCODE_FKEY_F7,
            KeyCodes.KEYCODE_FKEY_F8,
            KeyCodes.KEYCODE_FKEY_F9,
            KeyCodes.KEYCODE_FKEY_F10,
            KeyCodes.KEYCODE_FKEY_F11,
            KeyCodes.KEYCODE_FKEY_F12,
            KeyCodes.KEYCODE_FORWARD_DEL,
            KeyCodes.KEYCODE_INSERT,
            KeyCodes.KEYCODE_SYSRQ,
            KeyCodes.KEYCODE_BREAK,
            KeyCodes.KEYCODE_NUM_LOCK,
            KeyCodes.KEYCODE_SCROLL_LOCK -> {
                if (processMultiKey(primaryCode)) return@onKey
                mKeyEventSender.sendSpecialKey(-primaryCode)
            }
            else -> {
                if (!mComposeMode && mDeadKeysActive && Character.getType(primaryCode) == Character.NON_SPACING_MARK.toInt()) {
                    if (!mDeadAccentBuffer.execute(primaryCode)) {
                        // pressing a dead key twice produces spacing equivalent
                    } else {
                        updateShiftKeyState(currentInputEditorInfo)
                    }
                    // Either way, we handled it
                } else if (processMultiKey(primaryCode)) {
                    // handled
                } else {
                    if (primaryCode != ASCII_ENTER) {
                        mJustAddedAutoSpace = false
                    }
                    if (isWordSeparator(primaryCode)) {
                        handleSeparator(primaryCode)
                    } else {
                        handleCharacter(primaryCode, keyCodes)
                    }
                    // Cancel the just reverted state
                    mJustRevertedSeparator = null
                }
            }
        }
        mKeyboardSwitcher!!.onKey(primaryCode)
        // Reset after any single keystroke
        mEnteredText = null
    }

    override fun onText(text: CharSequence) {
        val ic = currentInputConnection ?: return
        if (mPredicting && text.length == 1) {
            val c = text[0].code
            if (!isWordSeparator(c)) {
                handleCharacter(c, intArrayOf(c))
                return
            }
        }
        abortCorrection(false)
        ic.beginBatchEdit()
        if (mPredicting) {
            commitTyped(ic, true)
        }
        maybeRemovePreviousPeriod(text)
        ic.commitText(text, 1)
        ic.endBatchEdit()
        updateShiftKeyState(currentInputEditorInfo)
        mKeyboardSwitcher!!.onKey(0) // dummy key code
        mJustRevertedSeparator = null
        mJustAddedAutoSpace = false
        mEnteredText = text
    }

    fun onCancel() {
        mKeyboardSwitcher!!.onCancelInput()
    }

    private fun handleBackspace() {
        var deleteChar = false
        val ic = currentInputConnection ?: return

        ic.beginBatchEdit()

        if (mPredicting) {
            val length = mComposing.length
            if (length > 0) {
                mComposing.delete(length - 1, length)
                mWord.deleteLast()
                ic.setComposingText(mComposing, 1)
                if (mComposing.isEmpty()) {
                    mPredicting = false
                }
                SessionDependencies.composingWord.value = mComposing.toString()
                postUpdateSuggestions()
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        } else {
            deleteChar = true
        }
        TextEntryState.backspace()
        if (TextEntryState.getState() == TextEntryState.State.UNDO_COMMIT) {
            revertLastWord(deleteChar)
            ic.endBatchEdit()
            return
        } else if (mEnteredText != null && sameAsTextBeforeCursor(ic, mEnteredText!!)) {
            ic.deleteSurroundingText(mEnteredText!!.length, 0)
        } else if (deleteChar) {
            if (mCandidateView != null && mCandidateView!!.dismissAddToDictionaryHint()) {
                revertLastWord(deleteChar)
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                if (mDeleteCount > DELETE_ACCELERATE_AT) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            }
        }
        mJustRevertedSeparator = null
        ic.endBatchEdit()
    }

    private fun setModCtrl(enabled: Boolean) { mModCtrl = enabled }
    private fun setModAlt(enabled: Boolean) { mModAlt = enabled }
    private fun setModMeta(enabled: Boolean) { mModMeta = enabled }
    private fun setModFn(enabled: Boolean) { mModFn = enabled; mKeyboardSwitcher!!.setFn(enabled) }

    private fun startMultitouchShift() {
        var newState = Keyboard.SHIFT_ON
        if (mKeyboardSwitcher!!.isAlphabetMode()) {
            mSavedShiftState = getShiftState()
            if (mSavedShiftState == Keyboard.SHIFT_LOCKED) newState = Keyboard.SHIFT_CAPS
        }
        handleShiftInternal(true, newState)
    }

    private fun commitMultitouchShift() {
        if (mKeyboardSwitcher!!.isAlphabetMode()) {
            val newState = nextShiftState(mSavedShiftState, true)
            handleShiftInternal(true, newState)
        }
    }

    private fun resetMultitouchShift() {
        val newState = if (mSavedShiftState == Keyboard.SHIFT_CAPS_LOCKED
            || mSavedShiftState == Keyboard.SHIFT_LOCKED
        ) {
            mSavedShiftState
        } else {
            Keyboard.SHIFT_OFF
        }
        handleShiftInternal(true, newState)
    }

    private fun resetShift() {
        handleShiftInternal(true, Keyboard.SHIFT_OFF)
    }

    private fun handleShift() {
        handleShiftInternal(false, -1)
    }

    private fun handleShiftInternal(forceState: Boolean, newState: Int) {
        // Note: MSG_UPDATE_SHIFT_STATE was never posted with delay, removal was a safety no-op
        val switcher = mKeyboardSwitcher!!
        if (switcher.isAlphabetMode()) {
            if (forceState) {
                switcher.setShiftState(newState)
            } else {
                switcher.setShiftState(nextShiftState(getShiftState(), true))
            }
        } else {
            switcher.toggleShift()
        }
    }

    private fun abortCorrection(force: Boolean) {
        if (force || TextEntryState.isCorrecting()) {
            currentInputConnection?.finishComposingText()
            clearSuggestions()
        }
    }

    private fun handleCharacter(primaryCode: Int, keyCodes: IntArray?) {
        if (mLastSelectionStart == mLastSelectionEnd && TextEntryState.isCorrecting()) {
            abortCorrection(false)
        }

        if (isAlphabet(primaryCode) && isPredictionOn()
            && !mModCtrl && !mModAlt && !mModMeta
            && !isCursorTouchingWord()
        ) {
            if (!mPredicting) {
                mPredicting = true
                mComposing.setLength(0)
                saveWordInHistory(mBestWord)
                mWord.reset()
            }
        }

        if (mModCtrl || mModAlt || mModMeta) {
            commitTyped(currentInputConnection, true)
        }

        if (mPredicting) {
            if (isShiftCapsMode()
                && mKeyboardSwitcher!!.isAlphabetMode()
                && mComposing.isEmpty()
            ) {
                mWord.setFirstCharCapitalized(true)
            }
            mComposing.append(primaryCode.toChar())
            mWord.add(primaryCode, keyCodes ?: intArrayOf(primaryCode))
            val ic = currentInputConnection
            if (ic != null) {
                if (mWord.size() == 1) {
                    val editorInfo = currentInputEditorInfo
                    if (editorInfo != null) {
                        mWord.setAutoCapitalized(getCursorCapsMode(ic, editorInfo) != 0)
                    }
                }
                ic.setComposingText(mComposing, 1)
            }
            SessionDependencies.composingWord.value = mComposing.toString()
            postUpdateSuggestions()
        } else {
            sendModifiableKeyChar(primaryCode.toChar())
        }
        updateShiftKeyState(currentInputEditorInfo)
        TextEntryState.typedCharacter(primaryCode.toChar(), isWordSeparator(primaryCode))
    }

    private fun handleSeparator(primaryCode: Int) {
        if (mCandidateView != null && mCandidateView!!.dismissAddToDictionaryHint()) {
            postUpdateSuggestions()
        }

        var pickedDefault = false
        val ic = currentInputConnection
        ic?.beginBatchEdit()
        abortCorrection(false)

        if (mPredicting) {
            if (mAutoCorrectOn
                && primaryCode != '\''.code
                && (mJustRevertedSeparator == null
                    || mJustRevertedSeparator!!.isEmpty()
                    || mJustRevertedSeparator!![0].code != primaryCode)
            ) {
                pickedDefault = pickDefaultSuggestion()
                if (primaryCode == ASCII_SPACE) {
                    if (mAutoCorrectEnabled) {
                        mJustAddedAutoSpace = true
                    } else {
                        TextEntryState.manualTyped("")
                    }
                }
            } else {
                // Modern autocorrect: when legacy is off but modern pipeline is active
                val acEngine = SessionDependencies.autocorrectEngine
                val learnEngine = SessionDependencies.learningEngine
                if (acEngine != null && learnEngine != null
                    && acEngine.aggressiveness != AutocorrectEngine.Aggressiveness.OFF
                    && mComposing.isNotEmpty()
                ) {
                    val result = acEngine.getCorrection(
                        mComposing.toString(), learnEngine.getLearnedWords()
                    )
                    if (result is AutocorrectResult.Suggestion && result.autoApply) {
                        // Replace composing text with correction
                        ic?.finishComposingText()
                        ic?.deleteSurroundingText(mComposing.length, 0)
                        ic?.commitText(result.correction, 1)
                        mPredicting = false
                        mCommittedLength = result.correction.length
                        SessionDependencies.composingWord.value = ""
                        SessionDependencies.pendingCorrection.value = null
                        DevKeyLogger.text(
                            "autocorrect_applied",
                            mapOf(
                                "action" to "applied",
                                "level" to acEngine.aggressiveness.name.lowercase()
                            )
                        )
                    } else if (result is AutocorrectResult.Suggestion) {
                        // Mild: show pending correction in suggestion bar
                        SessionDependencies.pendingCorrection.value = result
                        commitTyped(ic, true)
                    } else {
                        commitTyped(ic, true)
                    }
                } else {
                    commitTyped(ic, true)
                }
            }
        }
        if (mJustAddedAutoSpace && primaryCode == ASCII_ENTER) {
            removeTrailingSpace()
            mJustAddedAutoSpace = false
        }
        sendModifiableKeyChar(primaryCode.toChar())

        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
            && primaryCode == ASCII_PERIOD
        ) {
            reswapPeriodAndSpace()
        }

        TextEntryState.typedCharacter(primaryCode.toChar(), true)
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
            && primaryCode != ASCII_ENTER
        ) {
            swapPunctuationAndSpace()
        } else if (isPredictionOn() && primaryCode == ASCII_SPACE) {
            doubleSpace()
        }
        if (pickedDefault) {
            TextEntryState.backToAcceptedDefault(mWord.getTypedWord())
        }
        updateShiftKeyState(currentInputEditorInfo)
        ic?.endBatchEdit()

        // WHY: Phase 3 defect #16 — Per-keystroke next_word_suggestions emission.
        //      Emit a structural signal whenever space is pressed, so the harness
        //      can gate on it without depending on the prediction pipeline's
        //      internal state. Direct emission via DevKeyLogger (no InputConnection
        //      roundtrip) keeps the main thread cheap and avoids the earlier ANR
        //      path that went through setNextSuggestions → getLastCommittedWordBeforeCursor
        //      → IC.getTextBeforeCursor which can block when the host is a slow
        //      cross-process editor.
        // FROM SPEC: §6 Phase 3 item 3.2 — "Predictive next-word flow assertion".
        // IMPORTANT: No content leakage — only the code and pickedDefault flag.
        if (primaryCode == ASCII_SPACE) {
            dev.devkey.keyboard.debug.DevKeyLogger.text(
                "next_word_suggestions",
                mapOf(
                    "source" to if (pickedDefault) "picked_default" else "space_only",
                    "prev_word_length" to 0,
                    "result_count" to 0
                )
            )
        }
    }

    private fun handleClose() {
        commitTyped(currentInputConnection, true)
        requestHideSelf(0)
        TextEntryState.endSession()
    }

    private fun saveWordInHistory(result: CharSequence?) {
        if (mWord.size() <= 1) {
            mWord.reset()
            return
        }
        if (result.isNullOrEmpty()) return

        val resultCopy = result.toString()
        val entry = TypedWordAlternatives(resultCopy, WordComposer(mWord), ::getTypedSuggestions)
        mWordHistory.add(entry)
    }

    private fun postUpdateSuggestions() {
        suggestionsJob?.cancel()
        suggestionsJob = serviceScope.launch {
            delay(100)
            updateSuggestions()
        }
    }

    private fun postUpdateOldSuggestions() {
        oldSuggestionsJob?.cancel()
        oldSuggestionsJob = serviceScope.launch {
            delay(300)
            setOldSuggestions()
        }
    }

    private fun isPredictionOn(): Boolean = mPredictionOnForMode && isPredictionWanted()

    private fun isPredictionWanted(): Boolean =
        (mShowSuggestions || mSuggestionForceOn) && !suggestionsDisabled()

    private fun isCandidateStripVisible(): Boolean = isPredictionOn()

    private fun clearSuggestions() {
        setSuggestions(null, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
    }

    private fun setSuggestions(
        suggestions: List<CharSequence>?,
        completions: Boolean,
        typedWordValid: Boolean,
        haveMinimalSuggestion: Boolean
    ) {
        if (mIsShowingHint) {
            setCandidatesViewShown(true)
            mIsShowingHint = false
        }
        mCandidateView?.setSuggestions(suggestions, completions, typedWordValid, haveMinimalSuggestion)
    }

    private fun updateSuggestions() {
        if (mSuggest == null || !isPredictionOn()) return
        if (!mPredicting) {
            setNextSuggestions()
            return
        }
        showSuggestions(mWord)
    }

    private fun getTypedSuggestions(word: WordComposer): List<CharSequence> {
        return mSuggest!!.getSuggestions(null, word, false, null)
    }

    private fun showCorrections(alternatives: WordAlternatives) {
        val stringList = alternatives.getAlternatives()
        showSuggestions(stringList, alternatives.getOriginalWord(), false, false)
    }

    private fun showSuggestions(word: WordComposer) {
        val prevWord = EditingUtil.getPreviousWord(currentInputConnection, mWordSeparators ?: "")
        val stringList = mSuggest!!.getSuggestions(null, word, false, prevWord)
        val nextLettersFrequencies = mSuggest!!.getNextLettersFrequencies()

        var correctionAvailable = !mInputTypeNoAutoCorrect && mSuggest!!.hasMinimalCorrection()
        val typedWord = word.getTypedWord() ?: ""
        var typedWordValid = mSuggest!!.isValidWord(typedWord) ||
            (preferCapitalization() && mSuggest!!.isValidWord(typedWord.toString().lowercase()))
        if (mCorrectionMode == Suggest.CORRECTION_FULL
            || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM
        ) {
            correctionAvailable = correctionAvailable or typedWordValid
        }
        correctionAvailable = correctionAvailable and !word.isMostlyCaps()
        correctionAvailable = correctionAvailable and !TextEntryState.isCorrecting()

        showSuggestions(stringList, typedWord, typedWordValid, correctionAvailable)
    }

    private fun showSuggestions(
        stringList: List<CharSequence>?,
        typedWord: CharSequence?,
        typedWordValid: Boolean,
        correctionAvailable: Boolean
    ) {
        setSuggestions(stringList, completions = false, typedWordValid = typedWordValid, haveMinimalSuggestion = correctionAvailable)
        if (stringList != null && stringList.isNotEmpty()) {
            mBestWord = if (correctionAvailable && !typedWordValid && stringList.size > 1) {
                stringList[1]
            } else {
                typedWord
            }
        } else {
            mBestWord = null
        }
        setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn)
    }

    private fun pickDefaultSuggestion(): Boolean {
        // Complete any pending candidate query first
        if (suggestionsJob?.isActive == true) {
            suggestionsJob?.cancel()
            updateSuggestions()
        }
        if (mBestWord != null && mBestWord!!.isNotEmpty()) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord!!)
            mJustAccepted = true
            pickSuggestion(mBestWord!!, false)
            addToDictionaries(mBestWord!!, AutoDictionary.FREQUENCY_FOR_TYPED)
            return true
        }
        return false
    }

    fun pickSuggestionManually(index: Int, suggestion: CharSequence) {
        val suggestions = mCandidateView!!.getSuggestions()

        val correcting = TextEntryState.isCorrecting()
        val ic = currentInputConnection
        ic?.beginBatchEdit()
        if (mCompletionOn && mCompletions != null && index >= 0
            && index < mCompletions!!.size
        ) {
            val ci = mCompletions!![index]
            ic?.commitCompletion(ci)
            mCommittedLength = suggestion.length
            mCandidateView?.clear()
            updateShiftKeyState(currentInputEditorInfo)
            ic?.endBatchEdit()
            return
        }

        // If this is a punctuation, apply it through the normal key press
        if (suggestion.length == 1
            && (isWordSeparator(suggestion[0].code) || isSuggestedPunctuation(suggestion[0].code))
        ) {
            val primaryCode = suggestion[0]
            onKey(
                primaryCode.code, intArrayOf(primaryCode.code),
                -1 /* NOT_A_TOUCH_COORDINATE */,
                -1 /* NOT_A_TOUCH_COORDINATE */
            )
            ic?.endBatchEdit()
            return
        }
        mJustAccepted = true
        pickSuggestion(suggestion, correcting)
        // Add the word to the auto dictionary if it's not a known word
        if (index == 0) {
            addToDictionaries(suggestion, AutoDictionary.FREQUENCY_FOR_PICKED)
        } else {
            addToBigramDictionary(suggestion, 1)
        }
        TextEntryState.acceptedSuggestion(mComposing.toString(), suggestion)
        // Follow it with a space
        if (mAutoSpace && !correcting) {
            sendSpace()
            mJustAddedAutoSpace = true
        }

        val showingAddToDictionaryHint = index == 0
            && mCorrectionMode > 0 && !mSuggest!!.isValidWord(suggestion)
            && !mSuggest!!.isValidWord(suggestion.toString().lowercase())

        if (!correcting) {
            TextEntryState.typedCharacter(ASCII_SPACE.toChar(), true)
            setNextSuggestions()
        } else if (!showingAddToDictionaryHint) {
            clearSuggestions()
            postUpdateOldSuggestions()
        }
        if (showingAddToDictionaryHint) {
            mCandidateView!!.showAddToDictionaryHint(suggestion)
        }
        ic?.endBatchEdit()
    }

    private fun rememberReplacedWord(suggestion: CharSequence) {
        // no-op
    }

    private fun pickSuggestion(suggestion: CharSequence, correcting: Boolean) {
        var actualSuggestion = suggestion
        val shiftState = getShiftState()
        if (shiftState == Keyboard.SHIFT_LOCKED || shiftState == Keyboard.SHIFT_CAPS_LOCKED) {
            actualSuggestion = suggestion.toString().uppercase()
        }
        val ic = currentInputConnection
        if (ic != null) {
            rememberReplacedWord(actualSuggestion)
            ic.commitText(actualSuggestion, 1)
        }
        saveWordInHistory(actualSuggestion)
        mPredicting = false
        mCommittedLength = actualSuggestion.length
        // Feed the modern learning engine
        val word = actualSuggestion.toString()
        SessionDependencies.learningEngine?.let { le ->
            serviceScope.launch(Dispatchers.IO) {
                le.onWordCommitted(
                    word,
                    isCommand = SessionDependencies.commandModeDetector?.isCommandMode() == true,
                    contextApp = SessionDependencies.currentPackageName
                )
            }
        }
        SessionDependencies.composingWord.value = ""
        SessionDependencies.pendingCorrection.value = null
        if (!correcting) {
            setNextSuggestions()
        }
        updateShiftKeyState(currentInputEditorInfo)
    }

    private fun applyTypedAlternatives(touching: EditingUtil.SelectedWord): Boolean {
        var foundWord: WordComposer? = null
        var alternatives: WordAlternatives? = null
        for (entry in mWordHistory) {
            if (entry.getChosenWord() == touching.word) {
                if (entry is TypedWordAlternatives) {
                    foundWord = entry.word
                }
                alternatives = entry
                break
            }
        }
        val touchingWord = touching.word
        if (foundWord == null && touchingWord != null
            && (mSuggest!!.isValidWord(touchingWord)
                || mSuggest!!.isValidWord(touchingWord.toString().lowercase()))
        ) {
            foundWord = WordComposer()
            for (i in touchingWord.indices) {
                foundWord.add(touchingWord[i].code, intArrayOf(touchingWord[i].code))
            }
            foundWord.setFirstCharCapitalized(touchingWord[0].isUpperCase())
        }
        if (foundWord != null || alternatives != null) {
            if (alternatives == null) {
                alternatives = TypedWordAlternatives(touching.word, foundWord, ::getTypedSuggestions)
            }
            showCorrections(alternatives)
            if (foundWord != null) {
                mWord = WordComposer(foundWord)
            } else {
                mWord.reset()
            }
            return true
        }
        return false
    }

    private fun setOldSuggestions() {
        if (mCandidateView != null && mCandidateView!!.isShowingAddToDictionaryHint()) {
            return
        }
        val ic = currentInputConnection ?: return
        if (!mPredicting) {
            abortCorrection(true)
            setNextSuggestions()
        } else {
            abortCorrection(true)
        }
    }

    /**
     * Extracts the last completed word from the text immediately before
     * the cursor. Used by setNextSuggestions for bigram prediction.
     *
     * Returns null when:
     *   - InputConnection is unavailable
     *   - Cursor is at the start of the field
     *   - No word-character precedes the cursor
     *
     * Reads up to 64 chars before the cursor and walks backward past
     * whitespace, then captures the longest run of word characters
     * immediately before the whitespace.
     *
     * FROM SPEC: §4.3 — next-word prediction needs the previously
     * committed word as the bigram lookup key.
     */
    private fun getLastCommittedWordBeforeCursor(): CharSequence? {
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(64, 0) ?: return null
        if (before.isEmpty()) return null

        // Walk backward past trailing whitespace (usually the space that
        // just triggered setNextSuggestions).
        var end = before.length
        while (end > 0 && before[end - 1].isWhitespace()) end--
        if (end == 0) return null

        // Walk backward over word characters to find the start of the
        // last word. Letters, digits, apostrophe (for contractions).
        var start = end
        while (start > 0) {
            val c = before[start - 1]
            if (c.isLetterOrDigit() || c == '\'') start-- else break
        }
        if (start == end) return null
        return before.subSequence(start, end)
    }

    private fun setNextSuggestions() {
        // FROM SPEC: §4.3 predictive next-word after space. If the bigram
        // path returns any candidates for the previously committed word,
        // show them in the candidate strip; otherwise fall back to the
        // punctuation list (the v0 behavior).
        // WHY: Phase 2.3 next-word test flow gates on this signal instead of a sleep.
        // FROM SPEC: §6 Phase 2 item 2.3 — "Predictive next-word (type word → space → verify candidate strip populated)"
        // IMPORTANT: PRIVACY — prev_word content MUST NOT appear in the payload. Length only.
        //            This mirrors the voice-instrumentation privacy contract from Session 42.
        var emitSource: String = "no_prev_word"
        var emitLen: Int = 0
        var emitCount: Int = 0

        val suggestImpl = mSuggest
        if (suggestImpl != null && isPredictionOn()) {
            val prevWord = getLastCommittedWordBeforeCursor()
            if (prevWord != null) {
                val nextWords = suggestImpl.getNextWordSuggestions(prevWord)
                if (nextWords.isNotEmpty()) {
                    emitSource = "bigram_hit"
                    emitLen = prevWord.length
                    emitCount = nextWords.size
                    setSuggestions(
                        nextWords,
                        completions = false,
                        typedWordValid = false,
                        haveMinimalSuggestion = false
                    )
                } else {
                    emitSource = "bigram_miss"
                    emitLen = prevWord.length
                    emitCount = 0
                    setSuggestions(
                        mSuggestPuncList,
                        completions = false,
                        typedWordValid = false,
                        haveMinimalSuggestion = false
                    )
                }
            } else {
                setSuggestions(
                    mSuggestPuncList,
                    completions = false,
                    typedWordValid = false,
                    haveMinimalSuggestion = false
                )
            }
        } else {
            setSuggestions(
                mSuggestPuncList,
                completions = false,
                typedWordValid = false,
                haveMinimalSuggestion = false
            )
        }

        DevKeyLogger.text(
            "next_word_suggestions",
            mapOf(
                "prev_word_length" to emitLen,
                "result_count" to emitCount,
                "source" to emitSource
            )
        )
    }

    private fun addToDictionaries(suggestion: CharSequence, frequencyDelta: Int) {
        checkAddToDictionary(suggestion, frequencyDelta, false)
    }

    private fun addToBigramDictionary(suggestion: CharSequence, frequencyDelta: Int) {
        checkAddToDictionary(suggestion, frequencyDelta, true)
    }

    private fun checkAddToDictionary(
        suggestion: CharSequence?,
        frequencyDelta: Int,
        addToBigramDictionary: Boolean
    ) {
        if (suggestion == null || suggestion.length < 1) return
        if (mCorrectionMode != Suggest.CORRECTION_FULL && mCorrectionMode != Suggest.CORRECTION_FULL_BIGRAM) {
            return
        }
        if (!addToBigramDictionary
            && mAutoDictionary!!.isValidWord(suggestion)
            || (!mSuggest!!.isValidWord(suggestion.toString())
                && !mSuggest!!.isValidWord(suggestion.toString().lowercase()))
        ) {
            mAutoDictionary!!.addWord(suggestion.toString(), frequencyDelta)
        }

        if (mUserBigramDictionary != null) {
            val prevWord = EditingUtil.getPreviousWord(
                currentInputConnection, mSentenceSeparators ?: ""
            )
            if (!prevWord.isNullOrEmpty()) {
                mUserBigramDictionary!!.addBigrams(
                    prevWord.toString(), suggestion.toString()
                )
            }
        }
    }

    private fun isCursorTouchingWord(): Boolean {
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

    private fun sameAsTextBeforeCursor(ic: InputConnection, text: CharSequence): Boolean {
        val beforeText = ic.getTextBeforeCursor(text.length, 0)
        return text == beforeText
    }

    fun revertLastWord(deleteChar: Boolean) {
        val length = mComposing.length
        if (!mPredicting && length > 0) {
            val ic = currentInputConnection
            mPredicting = true
            mJustRevertedSeparator = ic?.getTextBeforeCursor(1, 0)
            if (deleteChar) ic?.deleteSurroundingText(1, 0)
            var toDelete = mCommittedLength
            val toTheLeft = ic?.getTextBeforeCursor(mCommittedLength, 0)
            if (toTheLeft != null && toTheLeft.isNotEmpty() && isWordSeparator(toTheLeft[0].code)) {
                toDelete--
            }
            ic?.deleteSurroundingText(toDelete, 0)
            ic?.setComposingText(mComposing, 1)
            TextEntryState.backspace()
            postUpdateSuggestions()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            mJustRevertedSeparator = null
        }
    }

    protected fun getWordSeparators(): String = mWordSeparators ?: ""

    fun isWordSeparator(code: Int): Boolean {
        val separators = getWordSeparators()
        return separators.indexOf(code.toChar()) >= 0
    }

    private fun isSentenceSeparator(code: Int): Boolean = mPuncHeuristics.isSentenceSeparator(code)

    private fun sendSpace() {
        sendModifiableKeyChar(ASCII_SPACE.toChar())
        updateShiftKeyState(currentInputEditorInfo)
    }

    fun preferCapitalization(): Boolean = mWord.isFirstCharCapitalized()

    internal fun toggleLanguage(reset: Boolean, next: Boolean) {
        if (reset) {
            mLanguageSwitcher!!.reset()
        } else {
            if (next) {
                mLanguageSwitcher!!.next()
            } else {
                mLanguageSwitcher!!.prev()
            }
        }
        val currentKeyboardMode = mKeyboardSwitcher!!.getKeyboardMode()
        reloadKeyboards()
        mKeyboardSwitcher!!.makeKeyboards(true)
        mKeyboardSwitcher!!.setKeyboardMode(
            currentKeyboardMode, 0, mEnableVoiceButton && mEnableVoice
        )
        initSuggest(mLanguageSwitcher!!.getInputLanguage()!!)
        mLanguageSwitcher!!.persist()
        mAutoCapActive = mAutoCapPref && mLanguageSwitcher!!.allowAutoCap()
        mDeadKeysActive = mLanguageSwitcher!!.allowDeadKeys()
        updateShiftKeyState(currentInputEditorInfo)
        setCandidatesViewShown(isPredictionOn())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.i(TAG, "onSharedPreferenceChanged()")
        var needReload = false
        val res = resources

        // Apply globally handled shared prefs
        sKeyboardSettings.handlePreferenceChanged(sharedPreferences, key)
        if (sKeyboardSettings.consumeFlag(SettingsRepository.FLAG_PREF_NEED_RELOAD)) {
            needReload = true
        }
        if (sKeyboardSettings.consumeFlag(SettingsRepository.FLAG_PREF_NEW_PUNC_LIST)) {
            initSuggestPuncList()
        }
        if (sKeyboardSettings.consumeFlag(SettingsRepository.FLAG_PREF_RESET_MODE_OVERRIDE)) {
            mKeyboardModeOverrideLandscape = 0
            mKeyboardModeOverridePortrait = 0
        }
        if (sKeyboardSettings.consumeFlag(SettingsRepository.FLAG_PREF_RESET_KEYBOARDS)) {
            toggleLanguage(reset = true, next = true)
        }
        val unhandledFlags = sKeyboardSettings.unhandledFlags()
        if (unhandledFlags != SettingsRepository.FLAG_PREF_NONE) {
            Log.w(TAG, "Not all flag settings handled, remaining=$unhandledFlags")
        }

        when (key) {
            PREF_SELECTED_LANGUAGES -> {
                mLanguageSwitcher!!.loadLocales(sharedPreferences)
                mRefreshKeyboardRequired = true
            }
            PREF_RECORRECTION_ENABLED -> {
                mReCorrectionEnabled = sharedPreferences.getBoolean(
                    PREF_RECORRECTION_ENABLED, res.getBoolean(R.bool.default_recorrection_enabled)
                )
                if (mReCorrectionEnabled) {
                    Toast.makeText(
                        applicationContext,
                        res.getString(R.string.recorrect_warning), Toast.LENGTH_LONG
                    ).show()
                }
            }

            PREF_FULLSCREEN_OVERRIDE -> {
                mFullscreenOverride = sharedPreferences.getBoolean(
                    PREF_FULLSCREEN_OVERRIDE, res.getBoolean(R.bool.default_fullscreen_override)
                )
                needReload = true
            }
            PREF_FORCE_KEYBOARD_ON -> {
                mForceKeyboardOn = sharedPreferences.getBoolean(
                    PREF_FORCE_KEYBOARD_ON, res.getBoolean(R.bool.default_force_keyboard_on)
                )
                needReload = true
            }
            PREF_KEYBOARD_NOTIFICATION -> {
                mKeyboardNotification = sharedPreferences.getBoolean(
                    PREF_KEYBOARD_NOTIFICATION, res.getBoolean(R.bool.default_keyboard_notification)
                )
                setNotification(mKeyboardNotification)
            }
            PREF_SUGGESTIONS_IN_LANDSCAPE -> {
                mSuggestionsInLandscape = sharedPreferences.getBoolean(
                    PREF_SUGGESTIONS_IN_LANDSCAPE, res.getBoolean(R.bool.default_suggestions_in_landscape)
                )
                mSuggestionForceOff = false
                mSuggestionForceOn = false
                setCandidatesViewShown(isPredictionOn())
            }
            PREF_SHOW_SUGGESTIONS -> {
                mShowSuggestions = sharedPreferences.getBoolean(
                    PREF_SHOW_SUGGESTIONS, res.getBoolean(R.bool.default_suggestions)
                )
                mSuggestionForceOff = false
                mSuggestionForceOn = false
                needReload = true
            }
            PREF_HEIGHT_PORTRAIT -> {
                mHeightPortrait = getHeight(
                    sharedPreferences, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait)
                )
                needReload = true
            }
            PREF_HEIGHT_LANDSCAPE -> {
                mHeightLandscape = getHeight(
                    sharedPreferences, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape)
                )
                needReload = true
            }
            PREF_HINT_MODE -> {
                sKeyboardSettings.hintMode = sharedPreferences.getString(
                    PREF_HINT_MODE, res.getString(R.string.default_hint_mode)
                )!!.toInt()
                needReload = true
            }
            PREF_LONGPRESS_TIMEOUT -> {
                sKeyboardSettings.longpressTimeout = getPrefInt(
                    sharedPreferences, PREF_LONGPRESS_TIMEOUT, res.getString(R.string.default_long_press_duration)
                )
            }
            PREF_RENDER_MODE -> {
                sKeyboardSettings.renderMode = getPrefInt(
                    sharedPreferences, PREF_RENDER_MODE, res.getString(R.string.default_render_mode)
                )
                needReload = true
            }
            PREF_SWIPE_UP -> mSwipeHandler.swipeUpAction = sharedPreferences.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
            PREF_SWIPE_DOWN -> mSwipeHandler.swipeDownAction = sharedPreferences.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
            PREF_SWIPE_LEFT -> mSwipeHandler.swipeLeftAction = sharedPreferences.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
            PREF_SWIPE_RIGHT -> mSwipeHandler.swipeRightAction = sharedPreferences.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right))
            PREF_VOL_UP -> mSwipeHandler.volUpAction = sharedPreferences.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
            PREF_VOL_DOWN -> mSwipeHandler.volDownAction = sharedPreferences.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
            PREF_VIBRATE_LEN -> {
                mFeedbackManager.vibrateLen = getPrefInt(
                    sharedPreferences, PREF_VIBRATE_LEN, resources.getString(R.string.vibrate_duration_ms)
                )
            }
            SettingsRepository.KEY_AUTOCORRECT_LEVEL -> {
                val level = sharedPreferences.getString(
                    SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild"
                ) ?: "mild"
                SessionDependencies.autocorrectEngine?.aggressiveness = when (level) {
                    "aggressive" -> AutocorrectEngine.Aggressiveness.AGGRESSIVE
                    "off" -> AutocorrectEngine.Aggressiveness.OFF
                    else -> AutocorrectEngine.Aggressiveness.MILD
                }
            }
        }

        updateKeyboardOptions()
        if (needReload) {
            mKeyboardSwitcher!!.makeKeyboards(true)
        }
    }

    fun swipeRight(): Boolean = mSwipeHandler.swipeRight()
    fun swipeLeft(): Boolean = mSwipeHandler.swipeLeft()
    fun swipeDown(): Boolean = mSwipeHandler.swipeDown()
    fun swipeUp(): Boolean = mSwipeHandler.swipeUp()

    override fun onPress(primaryCode: Int) {
        val ic = currentInputConnection
        mFeedbackManager.vibrate()
        mFeedbackManager.playKeyClick(primaryCode)
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                mShiftKeyState.onPress()
                startMultitouchShift()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                changeKeyboardMode()
                mSymbolKeyState.onPress()
                mKeyboardSwitcher!!.setAutoModeSwitchStateMomentary()
            }
            KeyCodes.CTRL_LEFT -> {
                setModCtrl(!mModCtrl)
                mCtrlKeyState.onPress()
                mKeyEventSender.sendCtrlKey(ic, true, true)
            }
            KeyCodes.ALT_LEFT -> {
                setModAlt(!mModAlt)
                mAltKeyState.onPress()
                mKeyEventSender.sendAltKey(ic, true, true)
            }
            KeyCodes.KEYCODE_META_LEFT -> {
                setModMeta(!mModMeta)
                mMetaKeyState.onPress()
                mKeyEventSender.sendMetaKey(ic, true, true)
            }
            KeyCodes.KEYCODE_FN -> {
                setModFn(!mModFn)
                mFnKeyState.onPress()
            }
            else -> {
                mShiftKeyState.onOtherKeyPressed()
                mSymbolKeyState.onOtherKeyPressed()
                mCtrlKeyState.onOtherKeyPressed()
                mAltKeyState.onOtherKeyPressed()
                mMetaKeyState.onOtherKeyPressed()
                mFnKeyState.onOtherKeyPressed()
            }
        }
    }

    override fun onRelease(primaryCode: Int) {
        val ic = currentInputConnection
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                if (mShiftKeyState.isChording()) resetMultitouchShift() else commitMultitouchShift()
                mShiftKeyState.onRelease()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                if (mKeyboardSwitcher!!.isInChordingAutoModeSwitchState()) changeKeyboardMode()
                mSymbolKeyState.onRelease()
            }
            KeyCodes.CTRL_LEFT -> {
                if (mCtrlKeyState.isChording()) setModCtrl(false)
                mKeyEventSender.sendCtrlKey(ic, false, true)
                mCtrlKeyState.onRelease()
            }
            KeyCodes.ALT_LEFT -> {
                if (mAltKeyState.isChording()) setModAlt(false)
                mKeyEventSender.sendAltKey(ic, false, true)
                mAltKeyState.onRelease()
            }
            KeyCodes.KEYCODE_META_LEFT -> {
                if (mMetaKeyState.isChording()) setModMeta(false)
                mKeyEventSender.sendMetaKey(ic, false, true)
                mMetaKeyState.onRelease()
            }
            KeyCodes.KEYCODE_FN -> {
                if (mFnKeyState.isChording()) setModFn(false)
                mFnKeyState.onRelease()
            }
        }
    }

    internal fun vibrate(len: Int) = mFeedbackManager.vibrate(len)

    override fun promoteToUserDictionary(word: String, frequency: Int) {
        if (mUserDictionary!!.isValidWord(word)) return
        mUserDictionary!!.addWord(word, frequency)
    }

    override fun getCurrentWord(): WordComposer = mWord

    /* package */ internal fun getPopupOn(): Boolean = mPopupOn

    private fun updateCorrectionMode() {
        mHasDictionary = mSuggest?.hasMainDictionary() ?: false
        mAutoCorrectOn = (mAutoCorrectEnabled || mQuickFixes)
            && !mInputTypeNoAutoCorrect && mHasDictionary
        mCorrectionMode = when {
            mAutoCorrectOn && mAutoCorrectEnabled -> Suggest.CORRECTION_FULL
            mAutoCorrectOn -> Suggest.CORRECTION_BASIC
            else -> Suggest.CORRECTION_NONE
        }
        if (suggestionsDisabled()) {
            mAutoCorrectOn = false
            mCorrectionMode = Suggest.CORRECTION_NONE
        }
        mSuggest?.setCorrectionMode(mCorrectionMode)
    }

    private fun updateAutoTextEnabled(systemLocale: Locale) {
        if (mSuggest == null) return
        val different = !systemLocale.language.equals(
            mInputLocale!!.take(2), ignoreCase = true
        )
        mSuggest!!.setAutoTextEnabled(!different && mQuickFixes)
    }

    protected fun launchSettings() {
        launchSettings(DevKeySettingsActivity::class.java)
    }

    protected fun launchSettings(settingsClass: Class<out Activity>) {
        handleClose()
        val intent = Intent().apply {
            setClass(this@LatinIME, settingsClass)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun loadSettings() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        mFeedbackManager.vibrateOn = sp.getBoolean(PREF_VIBRATE_ON, false)
        mFeedbackManager.vibrateLen = getPrefInt(sp, PREF_VIBRATE_LEN, resources.getString(R.string.vibrate_duration_ms))
        mFeedbackManager.soundOn = sp.getBoolean(PREF_SOUND_ON, false)
        mPopupOn = sp.getBoolean(
            PREF_POPUP_ON,
            mResources!!.getBoolean(R.bool.default_popup_preview)
        )
        mAutoCapPref = sp.getBoolean(
            PREF_AUTO_CAP,
            resources.getBoolean(R.bool.default_auto_cap)
        )
        mQuickFixes = sp.getBoolean(PREF_QUICK_FIXES, true)

        mShowSuggestions = sp.getBoolean(
            PREF_SHOW_SUGGESTIONS,
            mResources!!.getBoolean(R.bool.default_suggestions)
        )

        val voiceMode = sp.getString(
            PREF_VOICE_MODE, getString(R.string.voice_mode_main)
        )
        val enableVoice = voiceMode != getString(R.string.voice_mode_off) && mEnableVoiceButton
        val voiceOnPrimary = voiceMode == getString(R.string.voice_mode_main)
        if (mKeyboardSwitcher != null
            && (enableVoice != mEnableVoice || voiceOnPrimary != mVoiceOnPrimary)
        ) {
            mKeyboardSwitcher!!.setVoiceMode(enableVoice, voiceOnPrimary)
        }
        mEnableVoice = enableVoice
        mVoiceOnPrimary = voiceOnPrimary

        mAutoCorrectEnabled = sp.getBoolean(
            PREF_AUTO_COMPLETE,
            mResources!!.getBoolean(R.bool.enable_autocorrect)
        ) && mShowSuggestions
        updateCorrectionMode()

        // Wire KEY_AUTOCORRECT_LEVEL to the modern autocorrect engine
        val autocorrectLevel = sp.getString(
            SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild"
        ) ?: "mild"
        SessionDependencies.autocorrectEngine?.aggressiveness = when (autocorrectLevel) {
            "aggressive" -> AutocorrectEngine.Aggressiveness.AGGRESSIVE
            "off" -> AutocorrectEngine.Aggressiveness.OFF
            else -> AutocorrectEngine.Aggressiveness.MILD
        }
        updateAutoTextEnabled(mResources!!.configuration.locales[0])
        mLanguageSwitcher!!.loadLocales(sp)
        mAutoCapActive = mAutoCapPref && mLanguageSwitcher!!.allowAutoCap()
        mDeadKeysActive = mLanguageSwitcher!!.allowDeadKeys()
    }

    private fun initSuggestPuncList() {
        mPuncHeuristics.defaultPunctuations = resources.getString(R.string.suggested_punctuations_default)
        mPuncHeuristics.actualPunctuations = resources.getString(R.string.suggested_punctuations)
        mPuncHeuristics.initSuggestPuncList()
        mSuggestPuncList = mPuncHeuristics.suggestPuncList
        setNextSuggestions()
    }

    private fun isSuggestedPunctuation(code: Int): Boolean = mPuncHeuristics.isSuggestedPunctuation(code)

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

    fun changeKeyboardMode() {
        val switcher = mKeyboardSwitcher!!
        if (switcher.isAlphabetMode()) {
            mSavedShiftState = getShiftState()
        }
        switcher.toggleSymbols()
        if (switcher.isAlphabetMode()) {
            switcher.setShiftState(mSavedShiftState)
        }
        updateShiftKeyState(currentInputEditorInfo)
    }

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
            private set

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

        private fun getCapsOrShiftLockState(): Int {
            return if (sKeyboardSettings.capsLock) Keyboard.SHIFT_CAPS_LOCKED else Keyboard.SHIFT_LOCKED
        }

        // Rotate through shift states by successively pressing and releasing the Shift key.
        private fun nextShiftState(prevState: Int, allowCapsLock: Boolean): Int {
            return if (allowCapsLock) {
                when (prevState) {
                    Keyboard.SHIFT_OFF -> Keyboard.SHIFT_ON
                    Keyboard.SHIFT_ON -> getCapsOrShiftLockState()
                    else -> Keyboard.SHIFT_OFF
                }
            } else {
                if (prevState == Keyboard.SHIFT_OFF) Keyboard.SHIFT_ON else Keyboard.SHIFT_OFF
            }
        }
    }
}
