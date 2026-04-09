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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.VibrationEffect
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.core.KeyEventSender
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.core.KeyboardActionListener
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.clipboard.DevKeyClipboardManager
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.ui.keyboard.ComposeKeyboardViewFactory
import dev.devkey.keyboard.ui.keyboard.LayoutMode
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity
import org.xmlpull.v1.XmlPullParserException
import java.io.FileDescriptor
import java.io.IOException
import java.io.PrintWriter
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow

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
    private var mVibrateOn = false
    private var mVibrateLen = 0
    private var mSoundOn = false
    private var mPopupOn = false
    private var mAutoCapPref = false
    private var mAutoCapActive = false
    private var mDeadKeysActive = false
    private var mQuickFixes = false
    private var mShowSuggestions = false
    private var mIsShowingHint = false
    private var mConnectbotTabHack = false
    private var mFullscreenOverride = false
    private var mForceKeyboardOn = false
    private var mKeyboardNotification = false
    private var mSuggestionsInLandscape = false
    private var mSuggestionForceOn = false
    private var mSuggestionForceOff = false
    private var mSwipeUpAction: String? = null
    private var mSwipeDownAction: String? = null
    private var mSwipeLeftAction: String? = null
    private var mSwipeRightAction: String? = null
    private var mVolUpAction: String? = null
    private var mVolDownAction: String? = null

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

    private var mAudioManager: AudioManager? = null
    private var mSilentMode = false

    private var mVibrator: Vibrator? = null

    /* package */ internal var mWordSeparators: String? = null
    private var mSentenceSeparators: String? = null
    private var mConfigurationChanging = false

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private var mEnteredText: CharSequence? = null
    private var mRefreshKeyboardRequired = false

    private val mWordHistory = ArrayList<WordAlternatives>()

    private var mPluginManager: PluginManager? = null
    private var mNotificationReceiver: NotificationReceiver? = null
    private var keyMapDumpReceiver: BroadcastReceiver? = null
    // WHY: Holds the debug-only BroadcastReceiver so onDestroy can unregister it.
    // NOTE: Mirrors the existing keyMapDumpReceiver field pattern (LatinIME.kt same area).
    private var enableDebugServerReceiver: BroadcastReceiver? = null
    // WHY: Holds the debug-only layout-mode switch receiver for unregistration.
    private var setLayoutModeReceiver: BroadcastReceiver? = null

    /* package */ internal lateinit var mKeyEventSender: KeyEventSender

    abstract class WordAlternatives {
        protected var mChosenWord: CharSequence? = null

        constructor()

        constructor(chosenWord: CharSequence?) {
            mChosenWord = chosenWord
        }

        override fun hashCode(): Int = mChosenWord.hashCode()

        abstract fun getOriginalWord(): CharSequence?

        fun getChosenWord(): CharSequence? = mChosenWord

        abstract fun getAlternatives(): List<CharSequence>?
    }

    inner class TypedWordAlternatives : WordAlternatives {
        internal var word: WordComposer? = null

        constructor()

        constructor(chosenWord: CharSequence?, wordComposer: WordComposer?) : super(chosenWord) {
            word = wordComposer
        }

        override fun getOriginalWord(): CharSequence? = word?.getTypedWord()

        override fun getAlternatives(): List<CharSequence>? = word?.let { getTypedSuggestions(it) }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var suggestionsJob: Job? = null
    private var oldSuggestionsJob: Job? = null

    override fun onCreate() {
        Log.i(TAG, "onCreate(), os.version=${System.getProperty("os.version")}")
        super.onCreate()
        sInstance = this
        mVibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        // Initialize unified settings BEFORE KeyboardSwitcher (it reads sKeyboardSettings)
        val settingsPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        sKeyboardSettings = SettingsRepository(settingsPrefs)

        KeyboardSwitcher.init(this)

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
        mConnectbotTabHack = prefs.getBoolean(
            PREF_CONNECTBOT_TAB_HACK,
            res.getBoolean(R.bool.default_connectbot_tab_hack)
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
        mSwipeUpAction = prefs.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
        mSwipeDownAction = prefs.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
        mSwipeLeftAction = prefs.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
        mSwipeRightAction = prefs.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right))
        mVolUpAction = prefs.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
        mVolDownAction = prefs.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
        sKeyboardSettings.initPrefs(prefs, res)

        mKeyEventSender = KeyEventSender(
            inputConnectionProvider = { currentInputConnection },
            editorInfoProvider = { currentInputEditorInfo },
            modCtrlProvider = { mModCtrl },
            modAltProvider = { mModAlt },
            modMetaProvider = { mModMeta },
            shiftKeyStateProvider = { mShiftKeyState },
            ctrlKeyStateProvider = { mCtrlKeyState },
            altKeyStateProvider = { mAltKeyState },
            metaKeyStateProvider = { mMetaKeyState },
            connectbotTabHackProvider = { mConnectbotTabHack },
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

        try {
            initSuggest(inputLanguage)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during dictionary init for $inputLanguage", e)
        }

        mOrientation = conf.orientation

        // register to receive ringer mode changes for silent mode
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        prefs.registerOnSharedPreferenceChangeListener(this)
        setNotification(mKeyboardNotification)

        // Debug: register broadcast receiver for on-demand key map dump
        if (KeyMapGenerator.isDebugBuild(this)) {
            keyMapDumpReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val modeStr = PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(SettingsRepository.KEY_LAYOUT_MODE, "full") ?: "full"
                    val mode = when (modeStr) {
                        "compact" -> LayoutMode.COMPACT
                        "compact_dev" -> LayoutMode.COMPACT_DEV
                        else -> LayoutMode.FULL
                    }
                    KeyMapGenerator.dumpToLogcat(context, null, mode)
                }
            }
            registerReceiver(
                keyMapDumpReceiver,
                IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"),
                // WHY: Debug-only test broadcasts originate from the `adb shell` UID (2000),
                //      which is a different UID than the IME (10xxx). Android 13+ silently
                //      drops cross-UID broadcasts to NOT_EXPORTED receivers — observed on
                //      API 36 during Phase 3 gate (BroadcastRecord stuck with dispatchTime=--).
                //      The enclosing `isDebugBuild` gate ensures this is never live in release.
                Context.RECEIVER_EXPORTED
            )
            // WHY: Test harness pushes the driver-server URL into the running IME via broadcast.
            // FROM SPEC: §6 Phase 2 item 2.1 — "DevKeyLogger.enableServer(url) wired into /test harness"
            // NOTE: Mirrors DUMP_KEY_MAP receiver exactly. Debug-only gate is the enclosing
            //       isDebugBuild check — no additional gating needed.
            // IMPORTANT: Extras: --es url http://10.0.2.2:3948 (emulator) or equivalent host loopback.
            //            Passing no url extra OR an empty string calls disableServer() for cleanup.
            enableDebugServerReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val url = intent.getStringExtra("url")
                    if (url.isNullOrBlank()) {
                        DevKeyLogger.disableServer()
                        DevKeyLogger.ime("debug_server_disabled")
                    } else {
                        DevKeyLogger.enableServer(url)
                        // NOTE: This ime() call runs BEFORE the server is actually reachable
                        //       via the new URL on every subsequent event, so it serves as a
                        //       handshake: the first log line the driver server receives is
                        //       this one, confirming the broadcast landed.
                        // SECURITY: Scrub the URL to scheme://host:port only so a malicious
                        //           broadcaster cannot inject log-poisoning query strings.
                        val scrubbed = try {
                            java.net.URI(url).let { "${it.scheme}://${it.host}:${it.port}" }
                        } catch (_: Exception) {
                            "<invalid url>"
                        }
                        DevKeyLogger.ime("debug_server_enabled", mapOf("url" to scrubbed))
                    }
                }
            }
            registerReceiver(
                enableDebugServerReceiver,
                IntentFilter("dev.devkey.keyboard.ENABLE_DEBUG_SERVER"),
                Context.RECEIVER_EXPORTED
            )
            // WHY: Test harness needs to flip between FULL/COMPACT/COMPACT_DEV without driving the Settings UI.
            // FROM SPEC: §6 Phase 2 item 2.4 — "Existing tier stabilization — FULL + COMPACT + COMPACT_DEV to 100% green"
            // NOTE: Writing the preference directly triggers the existing SharedPreferenceChangeListener
            //       path in LatinIME (already wired — LatinIME is a SharedPreferences.OnSharedPreferenceChangeListener).
            // IMPORTANT: Valid mode values are exactly "full", "compact", "compact_dev" — any other value is ignored.
            //            These match the whitelist in the existing keyMapDumpReceiver at LatinIME.kt:407-413.
            setLayoutModeReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val mode = intent.getStringExtra("mode") ?: return
                    if (mode !in setOf("full", "compact", "compact_dev")) {
                        DevKeyLogger.error("set_layout_mode_rejected", mapOf("mode" to mode))
                        return
                    }
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putString(SettingsRepository.KEY_LAYOUT_MODE, mode)
                        .apply()
                    DevKeyLogger.ime("layout_mode_set", mapOf("mode" to mode))
                }
            }
            registerReceiver(
                setLayoutModeReceiver,
                IntentFilter("dev.devkey.keyboard.SET_LAYOUT_MODE"),
                Context.RECEIVER_EXPORTED
            )
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val description = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setNotification(visible: Boolean) {
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (visible && mNotificationReceiver == null) {
            createNotificationChannel()

            mNotificationReceiver = NotificationReceiver(this)
            val pFilter = IntentFilter(NotificationReceiver.ACTION_SHOW).apply {
                addAction(NotificationReceiver.ACTION_SETTINGS)
            }
            registerReceiver(mNotificationReceiver, pFilter, Context.RECEIVER_NOT_EXPORTED)

            val notificationIntent = Intent(NotificationReceiver.ACTION_SHOW)
            val contentIntent = PendingIntent.getBroadcast(
                applicationContext, 1, notificationIntent, PendingIntent.FLAG_IMMUTABLE
            )

            val configIntent = Intent(NotificationReceiver.ACTION_SETTINGS)
            val configPendingIntent = PendingIntent.getBroadcast(
                applicationContext, 2, configIntent, PendingIntent.FLAG_IMMUTABLE
            )

            val title = "Show DevKey"
            val body = "Select this to open the keyboard. Disable in settings."

            val mBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_hk_notification)
                .setColor(0xff220044.toInt())
                .setAutoCancel(false)
                .setTicker("Keyboard notification enabled.")
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(
                    R.drawable.icon_hk_notification,
                    getString(R.string.notification_action_settings),
                    configPendingIntent
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(NOTIFICATION_ONGOING_ID, mBuilder.build())

        } else if (mNotificationReceiver != null) {
            mNotificationManager.cancel(NOTIFICATION_ONGOING_ID)
            unregisterReceiver(mNotificationReceiver)
            mNotificationReceiver = null
        }
    }

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
        updateCorrectionMode()
        mWordSeparators = mResources!!.getString(R.string.word_separators)
        mSentenceSeparators = mResources!!.getString(R.string.sentence_separators)
        initSuggestPuncList()

        // No need to restore locale: createConfigurationContext creates a new isolated context,
        // leaving the service's own resources unchanged.
    }

    override fun onDestroy() {
        serviceScope.cancel()
        clipboardManager?.stopListening()
        keyMapDumpReceiver?.let { unregisterReceiver(it) }
        // WHY: Prevent leak of the newly added receivers across IME process restarts.
        try {
            enableDebugServerReceiver?.let { unregisterReceiver(it) }
        } catch (_: IllegalArgumentException) {
            // Already unregistered — safe to ignore.
        }
        enableDebugServerReceiver = null
        try {
            setLayoutModeReceiver?.let { unregisterReceiver(it) }
        } catch (_: IllegalArgumentException) {
            // Already unregistered — safe to ignore.
        }
        setLayoutModeReceiver = null
        mUserDictionary?.close()
        unregisterReceiver(mReceiver)
        unregisterReceiver(mPluginManager)
        if (mNotificationReceiver != null) {
            unregisterReceiver(mNotificationReceiver)
            mNotificationReceiver = null
        }
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
        return if (displayHeight > dimen || mFullscreenOverride || isConnectbot()) {
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
                if (mVolUpAction != "none" && isKeyboardVisible()) {
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (mVolDownAction != "none" && isKeyboardVisible()) {
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
                if (mVolUpAction != "none" && isKeyboardVisible()) {
                    return doSwipeAction(mVolUpAction)
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (mVolDownAction != "none" && isKeyboardVisible()) {
                    return doSwipeAction(mVolDownAction)
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
            }
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
        val ic = currentInputConnection ?: return
        val lastTwo = ic.getTextBeforeCursor(2, 0)
        if (lastTwo != null && lastTwo.length == 2
            && lastTwo[0] == ASCII_SPACE.toChar()
            && isSentenceSeparator(lastTwo[1].code)
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(2, 0)
            ic.commitText("${lastTwo[1]} ", 1)
            ic.endBatchEdit()
            updateShiftKeyState(currentInputEditorInfo)
            mJustAddedAutoSpace = true
        }
    }

    private fun reswapPeriodAndSpace() {
        val ic = currentInputConnection ?: return
        val lastThree = ic.getTextBeforeCursor(3, 0)
        if (lastThree != null && lastThree.length == 3
            && lastThree[0] == ASCII_PERIOD.toChar()
            && lastThree[1] == ASCII_SPACE.toChar()
            && lastThree[2] == ASCII_PERIOD.toChar()
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(3, 0)
            ic.commitText(" ..", 1)
            ic.endBatchEdit()
            updateShiftKeyState(currentInputEditorInfo)
        }
    }

    private fun doubleSpace() {
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return
        val ic = currentInputConnection ?: return
        val lastThree = ic.getTextBeforeCursor(3, 0)
        if (lastThree != null && lastThree.length == 3
            && Character.isLetterOrDigit(lastThree[0])
            && lastThree[1] == ASCII_SPACE.toChar()
            && lastThree[2] == ASCII_SPACE.toChar()
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(2, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            updateShiftKeyState(currentInputEditorInfo)
            mJustAddedAutoSpace = true
        }
    }

    private fun maybeRemovePreviousPeriod(text: CharSequence) {
        val ic = currentInputConnection
        if (ic == null || text.isEmpty()) return
        val lastOne = ic.getTextBeforeCursor(1, 0)
        if (lastOne != null && lastOne.length == 1
            && lastOne[0] == ASCII_PERIOD.toChar()
            && text[0] == ASCII_PERIOD.toChar()
        ) {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun removeTrailingSpace() {
        val ic = currentInputConnection ?: return
        val lastOne = ic.getTextBeforeCursor(1, 0)
        if (lastOne != null && lastOne.length == 1 && lastOne[0] == ASCII_SPACE.toChar()) {
            ic.deleteSurroundingText(1, 0)
        }
    }

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

    private fun isConnectbot(): Boolean = mKeyEventSender.isConnectbot()

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
        val distinctMultiTouch = mKeyboardSwitcher!!.hasDistinctMultitouch()
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (processMultiKey(primaryCode)) return@onKey
                handleBackspace()
                mDeleteCount++
            }
            Keyboard.KEYCODE_SHIFT -> {
                if (!distinctMultiTouch) handleShift()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                if (!distinctMultiTouch) changeKeyboardMode()
            }
            KeyCodes.CTRL_LEFT -> {
                if (!distinctMultiTouch) setModCtrl(!mModCtrl)
            }
            KeyCodes.ALT_LEFT -> {
                if (!distinctMultiTouch) setModAlt(!mModAlt)
            }
            KeyCodes.KEYCODE_META_LEFT -> {
                if (!distinctMultiTouch) setModMeta(!mModMeta)
            }
            KeyCodes.KEYCODE_FN -> {
                if (!distinctMultiTouch) setModFn(!mModFn)
            }
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

    private fun setModCtrl(enabled: Boolean) {
        mKeyboardSwitcher!!.setCtrlIndicator(enabled)
        mModCtrl = enabled
    }

    private fun setModAlt(enabled: Boolean) {
        mKeyboardSwitcher!!.setAltIndicator(enabled)
        mModAlt = enabled
    }

    private fun setModMeta(enabled: Boolean) {
        mKeyboardSwitcher!!.setMetaIndicator(enabled)
        mModMeta = enabled
    }

    private fun setModFn(enabled: Boolean) {
        mModFn = enabled
        mKeyboardSwitcher!!.setFn(enabled)
        mKeyboardSwitcher!!.setCtrlIndicator(mModCtrl)
        mKeyboardSwitcher!!.setAltIndicator(mModAlt)
        mKeyboardSwitcher!!.setMetaIndicator(mModMeta)
    }

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
                commitTyped(ic, true)
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
        val entry = TypedWordAlternatives(resultCopy, WordComposer(mWord))
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
                alternatives = TypedWordAlternatives(touching.word, foundWord)
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

    private fun isSentenceSeparator(code: Int): Boolean {
        return mSentenceSeparators?.indexOf(code.toChar())?.let { it >= 0 } ?: false
    }

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
            PREF_CONNECTBOT_TAB_HACK -> {
                mConnectbotTabHack = sharedPreferences.getBoolean(
                    PREF_CONNECTBOT_TAB_HACK, res.getBoolean(R.bool.default_connectbot_tab_hack)
                )
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
            PREF_SWIPE_UP -> mSwipeUpAction = sharedPreferences.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
            PREF_SWIPE_DOWN -> mSwipeDownAction = sharedPreferences.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
            PREF_SWIPE_LEFT -> mSwipeLeftAction = sharedPreferences.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
            PREF_SWIPE_RIGHT -> mSwipeRightAction = sharedPreferences.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right))
            PREF_VOL_UP -> mVolUpAction = sharedPreferences.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
            PREF_VOL_DOWN -> mVolDownAction = sharedPreferences.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
            PREF_VIBRATE_LEN -> {
                mVibrateLen = getPrefInt(
                    sharedPreferences, PREF_VIBRATE_LEN, resources.getString(R.string.vibrate_duration_ms)
                )
            }
        }

        updateKeyboardOptions()
        if (needReload) {
            mKeyboardSwitcher!!.makeKeyboards(true)
        }
    }

    private fun doSwipeAction(action: String?): Boolean {
        if (action.isNullOrEmpty() || action == "none") return false

        when (action) {
            "close" -> handleClose()
            "settings" -> launchSettings()
            "suggestions" -> {
                if (mSuggestionForceOn) {
                    mSuggestionForceOn = false
                    mSuggestionForceOff = true
                } else if (mSuggestionForceOff) {
                    mSuggestionForceOn = true
                    mSuggestionForceOff = false
                } else if (isPredictionWanted()) {
                    mSuggestionForceOff = true
                } else {
                    mSuggestionForceOn = true
                }
                setCandidatesViewShown(isPredictionOn())
            }
            "lang_prev" -> toggleLanguage(false, false)
            "lang_next" -> toggleLanguage(false, true)
            "full_mode" -> {
                if (isPortrait()) {
                    mKeyboardModeOverridePortrait = (mKeyboardModeOverridePortrait + 1) % mNumKeyboardModes
                } else {
                    mKeyboardModeOverrideLandscape = (mKeyboardModeOverrideLandscape + 1) % mNumKeyboardModes
                }
                toggleLanguage(reset = true, next = true)
            }
            "extension" -> {
                sKeyboardSettings.useExtension = !sKeyboardSettings.useExtension
                reloadKeyboards()
            }
            "height_up" -> {
                if (isPortrait()) {
                    mHeightPortrait += 5
                    if (mHeightPortrait > 70) mHeightPortrait = 70
                } else {
                    mHeightLandscape += 5
                    if (mHeightLandscape > 70) mHeightLandscape = 70
                }
                toggleLanguage(reset = true, next = true)
            }
            "height_down" -> {
                if (isPortrait()) {
                    mHeightPortrait -= 5
                    if (mHeightPortrait < 15) mHeightPortrait = 15
                } else {
                    mHeightLandscape -= 5
                    if (mHeightLandscape < 15) mHeightLandscape = 15
                }
                toggleLanguage(reset = true, next = true)
            }
            else -> Log.i(TAG, "Unsupported swipe action config: $action")
        }
        return true
    }

    fun swipeRight(): Boolean = doSwipeAction(mSwipeRightAction)

    fun swipeLeft(): Boolean = doSwipeAction(mSwipeLeftAction)

    fun swipeDown(): Boolean = doSwipeAction(mSwipeDownAction)

    fun swipeUp(): Boolean = doSwipeAction(mSwipeUpAction)

    override fun onPress(primaryCode: Int) {
        val ic = currentInputConnection
        if (mKeyboardSwitcher!!.isVibrateAndSoundFeedbackRequired()) {
            vibrate()
            playKeyClick(primaryCode)
        }
        val distinctMultiTouch = mKeyboardSwitcher!!.hasDistinctMultitouch()
        when {
            distinctMultiTouch && primaryCode == Keyboard.KEYCODE_SHIFT -> {
                mShiftKeyState.onPress()
                startMultitouchShift()
            }
            distinctMultiTouch && primaryCode == Keyboard.KEYCODE_MODE_CHANGE -> {
                changeKeyboardMode()
                mSymbolKeyState.onPress()
                mKeyboardSwitcher!!.setAutoModeSwitchStateMomentary()
            }
            distinctMultiTouch && primaryCode == KeyCodes.CTRL_LEFT -> {
                setModCtrl(!mModCtrl)
                mCtrlKeyState.onPress()
                mKeyEventSender.sendCtrlKey(ic, true, true)
            }
            distinctMultiTouch && primaryCode == KeyCodes.ALT_LEFT -> {
                setModAlt(!mModAlt)
                mAltKeyState.onPress()
                mKeyEventSender.sendAltKey(ic, true, true)
            }
            distinctMultiTouch && primaryCode == KeyCodes.KEYCODE_META_LEFT -> {
                setModMeta(!mModMeta)
                mMetaKeyState.onPress()
                mKeyEventSender.sendMetaKey(ic, true, true)
            }
            distinctMultiTouch && primaryCode == KeyCodes.KEYCODE_FN -> {
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
        val distinctMultiTouch = mKeyboardSwitcher!!.hasDistinctMultitouch()
        val ic = currentInputConnection
        when {
            distinctMultiTouch && primaryCode == Keyboard.KEYCODE_SHIFT -> {
                if (mShiftKeyState.isChording()) {
                    resetMultitouchShift()
                } else {
                    commitMultitouchShift()
                }
                mShiftKeyState.onRelease()
            }
            distinctMultiTouch && primaryCode == Keyboard.KEYCODE_MODE_CHANGE -> {
                if (mKeyboardSwitcher!!.isInChordingAutoModeSwitchState()) {
                    changeKeyboardMode()
                }
                mSymbolKeyState.onRelease()
            }
            distinctMultiTouch && primaryCode == KeyCodes.CTRL_LEFT -> {
                if (mCtrlKeyState.isChording()) {
                    setModCtrl(false)
                }
                mKeyEventSender.sendCtrlKey(ic, false, true)
                mCtrlKeyState.onRelease()
            }
            distinctMultiTouch && primaryCode == KeyCodes.ALT_LEFT -> {
                if (mAltKeyState.isChording()) {
                    setModAlt(false)
                }
                mKeyEventSender.sendAltKey(ic, false, true)
                mAltKeyState.onRelease()
            }
            distinctMultiTouch && primaryCode == KeyCodes.KEYCODE_META_LEFT -> {
                if (mMetaKeyState.isChording()) {
                    setModMeta(false)
                }
                mKeyEventSender.sendMetaKey(ic, false, true)
                mMetaKeyState.onRelease()
            }
            distinctMultiTouch && primaryCode == KeyCodes.KEYCODE_FN -> {
                if (mFnKeyState.isChording()) {
                    setModFn(false)
                }
                mFnKeyState.onRelease()
            }
        }
    }

    // receive ringer mode changes to detect silent mode
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateRingerMode()
        }
    }

    // update flags for silent mode
    private fun updateRingerMode() {
        if (mAudioManager == null) {
            mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        mAudioManager?.let {
            mSilentMode = it.ringerMode != AudioManager.RINGER_MODE_NORMAL
        }
    }

    private fun getKeyClickVolume(): Float {
        if (mAudioManager == null) return 0.0f

        val method = sKeyboardSettings.keyClickMethod
        if (method == 0) return FX_VOLUME

        var targetVol = sKeyboardSettings.keyClickVolume

        if (method > 1) {
            val mediaMax = mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val mediaVol = mAudioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
            val channelVol = mediaVol.toFloat() / mediaMax
            when (method) {
                2 -> targetVol *= channelVol
                3 -> {
                    if (channelVol == 0f) return 0.0f
                    targetVol = min(targetVol / channelVol, 1.0f)
                }
            }
        }
        return 10.0f.pow(FX_VOLUME_RANGE_DB * (targetVol - 1) / 20)
    }

    private fun playKeyClick(primaryCode: Int) {
        if (mAudioManager == null) {
            updateRingerMode()
        }
        if (mSoundOn && !mSilentMode) {
            val sound = when (primaryCode) {
                Keyboard.KEYCODE_DELETE -> AudioManager.FX_KEYPRESS_DELETE
                ASCII_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                ASCII_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            mAudioManager!!.playSoundEffect(sound, getKeyClickVolume())
        }
    }

    private fun vibrate() {
        if (!mVibrateOn) return
        vibrate(mVibrateLen)
    }

    internal fun vibrate(len: Int) {
        mVibrator?.vibrate(VibrationEffect.createOneShot(len.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }

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
        mVibrateOn = sp.getBoolean(PREF_VIBRATE_ON, false)
        mVibrateLen = getPrefInt(sp, PREF_VIBRATE_LEN, resources.getString(R.string.vibrate_duration_ms))
        mSoundOn = sp.getBoolean(PREF_SOUND_ON, false)
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
        updateAutoTextEnabled(mResources!!.configuration.locales[0])
        mLanguageSwitcher!!.loadLocales(sp)
        mAutoCapActive = mAutoCapPref && mLanguageSwitcher!!.allowAutoCap()
        mDeadKeysActive = mLanguageSwitcher!!.allowDeadKeys()
    }

    private fun initSuggestPuncList() {
        mSuggestPuncList = mutableListOf()
        var suggestPuncs = sKeyboardSettings.suggestedPunctuation
        val defaultPuncs = resources.getString(R.string.suggested_punctuations_default)
        if (suggestPuncs == defaultPuncs || suggestPuncs.isEmpty()) {
            suggestPuncs = resources.getString(R.string.suggested_punctuations)
        }
        for (i in suggestPuncs.indices) {
            mSuggestPuncList!!.add(suggestPuncs.subSequence(i, i + 1))
        }
        setNextSuggestions()
    }

    private fun isSuggestedPunctuation(code: Int): Boolean {
        return sKeyboardSettings.suggestedPunctuation.indexOf(code.toChar()) >= 0
    }

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
        p.println("  mSoundOn=$mSoundOn")
        p.println("  mVibrateOn=$mVibrateOn")
        p.println("  mPopupOn=$mPopupOn")
    }

    fun onAutoCompletionStateChanged(isAutoCompletion: Boolean) {
        mKeyboardSwitcher!!.onAutoCompletionStateChanged(isAutoCompletion)
    }

    companion object {
        private const val TAG = "DevKey/LatinIME"

        // Align sound effect volume on music volume
        private const val FX_VOLUME = -1.0f
        private const val FX_VOLUME_RANGE_DB = 72.0f
        private const val NOTIFICATION_CHANNEL_ID = "DevKey"
        private const val NOTIFICATION_ONGOING_ID = 1001

        private const val PREF_VIBRATE_ON = "vibrate_on"
        internal const val PREF_VIBRATE_LEN = "vibrate_len"
        private const val PREF_SOUND_ON = "sound_on"
        private const val PREF_POPUP_ON = "popup_on"
        private const val PREF_AUTO_CAP = "auto_cap"
        private const val PREF_QUICK_FIXES = "quick_fixes"
        private const val PREF_SHOW_SUGGESTIONS = "show_suggestions"
        private const val PREF_AUTO_COMPLETE = "auto_complete"
        private const val PREF_VOICE_MODE = "voice_mode"

        private const val IME_OPTION_NO_MICROPHONE = "nm"

        val PREF_SELECTED_LANGUAGES = "selected_languages"
        val PREF_INPUT_LANGUAGE = "input_language"
        private const val PREF_RECORRECTION_ENABLED = "recorrection_enabled"
        internal const val PREF_FULLSCREEN_OVERRIDE = "fullscreen_override"
        internal const val PREF_FORCE_KEYBOARD_ON = "force_keyboard_on"
        internal const val PREF_KEYBOARD_NOTIFICATION = "keyboard_notification"
        internal const val PREF_CONNECTBOT_TAB_HACK = "connectbot_tab_hack"
        internal const val PREF_FULL_KEYBOARD_IN_PORTRAIT = "full_keyboard_in_portrait"
        internal const val PREF_SUGGESTIONS_IN_LANDSCAPE = "suggestions_in_landscape"
        internal const val PREF_HEIGHT_PORTRAIT = "settings_height_portrait"
        internal const val PREF_HEIGHT_LANDSCAPE = "settings_height_landscape"
        internal const val PREF_HINT_MODE = "pref_hint_mode"
        internal const val PREF_LONGPRESS_TIMEOUT = "pref_long_press_duration"
        internal const val PREF_RENDER_MODE = "pref_render_mode"
        internal const val PREF_SWIPE_UP = "pref_swipe_up"
        internal const val PREF_SWIPE_DOWN = "pref_swipe_down"
        internal const val PREF_SWIPE_LEFT = "pref_swipe_left"
        internal const val PREF_SWIPE_RIGHT = "pref_swipe_right"
        internal const val PREF_VOL_UP = "pref_vol_up"
        internal const val PREF_VOL_DOWN = "pref_vol_down"

        // How many continuous deletes at which to start deleting at a higher speed.
        private const val DELETE_ACCELERATE_AT = 20
        // Key events coming any faster than this are long-presses.
        private const val QUICK_PRESS = 200

        // ASCII constants delegate to KeyCodes (single source of truth)
        val ASCII_ENTER = KeyCodes.ENTER
        val ASCII_SPACE = KeyCodes.ASCII_SPACE
        val ASCII_PERIOD = KeyCodes.ASCII_PERIOD

        // Contextual menu positions
        private const val POS_METHOD = 0
        private const val POS_SETTINGS = 1

        lateinit var sKeyboardSettings: SettingsRepository
            private set

        // Strong reference intentionally held for the service lifetime so that other
        // components (KeyboardSwitcher, CandidateView, etc.) can reach the service.
        // Set to null in onDestroy() to prevent leaks after the service is destroyed.
        var sInstance: LatinIME? = null
            private set

        private val NUMBER_RE = Pattern.compile("(\\d+).*")

        fun getDictionary(res: Resources): IntArray {
            val packageName = LatinIME::class.java.`package`?.name ?: ""
            val xrp = res.getXml(R.xml.dictionary)
            val dictionaries = mutableListOf<Int>()

            try {
                var current = xrp.eventType
                while (current != android.content.res.XmlResourceParser.END_DOCUMENT) {
                    if (current == android.content.res.XmlResourceParser.START_TAG) {
                        val tag = xrp.name
                        if (tag == "part") {
                            val dictFileName = xrp.getAttributeValue(null, "name")
                            dictionaries.add(res.getIdentifier(dictFileName, "raw", packageName))
                        }
                    }
                    xrp.next()
                    current = xrp.eventType
                }
            } catch (e: XmlPullParserException) {
                Log.e(TAG, "Dictionary XML parsing failure")
            } catch (e: IOException) {
                Log.e(TAG, "Dictionary XML IOException")
            }

            return dictionaries.toIntArray()
        }

        fun getIntFromString(value: String, defVal: Int): Int {
            val num = NUMBER_RE.matcher(value)
            return if (num.matches()) num.group(1)!!.toInt() else defVal
        }

        fun getPrefInt(prefs: SharedPreferences, prefName: String, defVal: Int): Int {
            val prefVal = prefs.getString(prefName, defVal.toString())
            return getIntFromString(prefVal ?: defVal.toString(), defVal)
        }

        fun getPrefInt(prefs: SharedPreferences, prefName: String, defStr: String): Int {
            val defVal = getIntFromString(defStr, 0)
            return getPrefInt(prefs, prefName, defVal)
        }

        fun getHeight(prefs: SharedPreferences, prefName: String, defVal: String): Int {
            var value = getPrefInt(prefs, prefName, defVal)
            if (value < 15) value = 15
            if (value > 75) value = 75
            return value
        }

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
