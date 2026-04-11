package dev.devkey.keyboard.core

import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.*
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.language.LanguageSwitcher
import dev.devkey.keyboard.compose.ComposeSequenceLoader
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.clipboard.DevKeyClipboardManager
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.ui.keyboard.SessionDependencies

/**
 * Performs all dependency initialization for LatinIME.onCreate().
 * Extracted from LatinIME to reduce god-class size.
 */
internal class ImeInitializer(private val ime: LatinIME) {

    fun initialize(): InitResult {
        val settingsPrefs = PreferenceManager.getDefaultSharedPreferences(ime)
        LatinIME.sKeyboardSettings = SettingsRepository(settingsPrefs)
        ime.mFeedbackManager = FeedbackManager(
            ime, ime.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator, LatinIME.sKeyboardSettings
        )

        KeyboardSwitcher.init(ime)
        ComposeSequenceLoader.load(ime)

        val clipboardRepo = ClipboardRepository(DevKeyDatabase.getInstance(ime).clipboardHistoryDao())
        val clipboardManager = DevKeyClipboardManager(ime, clipboardRepo)
        clipboardManager.startListening()

        val cmdRepo = CommandModeRepository(DevKeyDatabase.getInstance(ime).commandAppDao())
        val commandModeDetector = CommandModeDetector(cmdRepo)
        SessionDependencies.commandModeDetector = commandModeDetector

        ime.mResources = ime.resources
        val conf = ime.mResources!!.configuration
        ime.mOrientation = conf.orientation
        val prefs = PreferenceManager.getDefaultSharedPreferences(ime)
        ime.mLanguageSwitcher = LanguageSwitcher(ime)
        ime.mLanguageSwitcher!!.loadLocales(prefs)
        ime.mKeyboardSwitcher = KeyboardSwitcher.getInstance()
        ime.mKeyboardSwitcher!!.setLanguageSwitcher(ime.mLanguageSwitcher!!)
        val systemLocale = conf.locales[0]
        ime.mLanguageSwitcher!!.setSystemLocale(systemLocale)
        val inputLanguage = ime.mLanguageSwitcher!!.getInputLanguage() ?: systemLocale.toString()
        val res = ime.resources

        ime.mReCorrectionEnabled = prefs.getBoolean(PREF_RECORRECTION_ENABLED, res.getBoolean(R.bool.default_recorrection_enabled))
        ime.mFullscreenOverride = prefs.getBoolean(PREF_FULLSCREEN_OVERRIDE, res.getBoolean(R.bool.default_fullscreen_override))
        ime.mForceKeyboardOn = prefs.getBoolean(PREF_FORCE_KEYBOARD_ON, res.getBoolean(R.bool.default_force_keyboard_on))
        ime.mKeyboardNotification = prefs.getBoolean(PREF_KEYBOARD_NOTIFICATION, res.getBoolean(R.bool.default_keyboard_notification))
        ime.mSuggestionsInLandscape = prefs.getBoolean(PREF_SUGGESTIONS_IN_LANDSCAPE, res.getBoolean(R.bool.default_suggestions_in_landscape))
        ime.mHeightPortrait = LatinIME.getHeight(prefs, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait))
        ime.mHeightLandscape = LatinIME.getHeight(prefs, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape))
        LatinIME.sKeyboardSettings.hintMode = prefs.getString(PREF_HINT_MODE, res.getString(R.string.default_hint_mode))!!.toInt()
        LatinIME.sKeyboardSettings.longpressTimeout = LatinIME.getPrefInt(prefs, PREF_LONGPRESS_TIMEOUT, res.getString(R.string.default_long_press_duration))
        LatinIME.sKeyboardSettings.renderMode = LatinIME.getPrefInt(prefs, PREF_RENDER_MODE, res.getString(R.string.default_render_mode))

        ime.mSwipeHandler = SwipeActionHandler(
            handleClose = { ime.handleClose() },
            launchSettings = { ime.launchSettings() },
            toggleSuggestions = {
                if (ime.mSuggestionForceOn) { ime.mSuggestionForceOn = false; ime.mSuggestionForceOff = true }
                else if (ime.mSuggestionForceOff) { ime.mSuggestionForceOn = true; ime.mSuggestionForceOff = false }
                else if (ime.mSuggestionCoordinator.isPredictionWanted()) { ime.mSuggestionForceOff = true }
                else { ime.mSuggestionForceOn = true }
                ime.setCandidatesViewShown(ime.isPredictionOn())
            },
            toggleLanguagePrev = { ime.toggleLanguage(false, false) },
            toggleLanguageNext = { ime.toggleLanguage(false, true) },
            cycleFullMode = {
                if (ime.isPortrait()) ime.mKeyboardModeOverridePortrait = (ime.mKeyboardModeOverridePortrait + 1) % ime.mNumKeyboardModes
                else ime.mKeyboardModeOverrideLandscape = (ime.mKeyboardModeOverrideLandscape + 1) % ime.mNumKeyboardModes
                ime.toggleLanguage(reset = true, next = true)
            },
            toggleExtension = { LatinIME.sKeyboardSettings.useExtension = !LatinIME.sKeyboardSettings.useExtension; ime.reloadKeyboards() },
            adjustHeightUp = {
                if (ime.isPortrait()) { ime.mHeightPortrait += 5; if (ime.mHeightPortrait > 70) ime.mHeightPortrait = 70 }
                else { ime.mHeightLandscape += 5; if (ime.mHeightLandscape > 70) ime.mHeightLandscape = 70 }
                ime.toggleLanguage(reset = true, next = true)
            },
            adjustHeightDown = {
                if (ime.isPortrait()) { ime.mHeightPortrait -= 5; if (ime.mHeightPortrait < 15) ime.mHeightPortrait = 15 }
                else { ime.mHeightLandscape -= 5; if (ime.mHeightLandscape < 15) ime.mHeightLandscape = 15 }
                ime.toggleLanguage(reset = true, next = true)
            }
        )
        ime.mSwipeHandler.swipeUpAction = prefs.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
        ime.mSwipeHandler.swipeDownAction = prefs.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
        ime.mSwipeHandler.swipeLeftAction = prefs.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
        ime.mSwipeHandler.swipeRightAction = prefs.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right))
        ime.mSwipeHandler.volUpAction = prefs.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
        ime.mSwipeHandler.volDownAction = prefs.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
        LatinIME.sKeyboardSettings.initPrefs(prefs, res)

        ime.mKeyEventSender = KeyEventSender(
            inputConnectionProvider = { ime.currentInputConnection },
            modCtrlProvider = { ime.mModCtrl }, modAltProvider = { ime.mModAlt },
            modMetaProvider = { ime.mModMeta }, shiftKeyStateProvider = { ime.mShiftKeyState },
            ctrlKeyStateProvider = { ime.mCtrlKeyState }, altKeyStateProvider = { ime.mAltKeyState },
            metaKeyStateProvider = { ime.mMetaKeyState },
            shiftModProvider = { ime.isShiftMod() },
            setModCtrl = { ime.mModCtrl = it }, setModAlt = { ime.mModAlt = it },
            setModMeta = { ime.mModMeta = it }, resetShift = { ime.resetShift() },
            getShiftState = { ime.getShiftState() },
            commitTyped = { ic, manual -> ime.commitTyped(ic, manual) },
            sendKeyCharFn = { ch -> ime.sendKeyChar(ch) },
            sendDownUpKeyEventsFn = { code -> ime.sendDownUpKeyEvents(code) },
            settings = LatinIME.sKeyboardSettings,
            ctrlAToastAction = { Toast.makeText(ime.applicationContext, res.getString(R.string.toast_ctrl_a_override_info), Toast.LENGTH_LONG).show() }
        )

        ime.mPuncHeuristics = PunctuationHeuristics(
            icProvider = { ime.currentInputConnection },
            settings = LatinIME.sKeyboardSettings,
            updateShiftKeyState = { ime.updateShiftKeyState(ime.currentInputEditorInfo) }
        )
        ime.updateKeyboardOptions()

        PluginManager.getPluginDictionaries(ime.applicationContext)
        val pluginManager = PluginManager(ime)
        val pFilter = IntentFilter().apply {
            addDataScheme("package")
            addAction("android.intent.action.PACKAGE_ADDED")
            addAction("android.intent.action.PACKAGE_REPLACED")
            addAction("android.intent.action.PACKAGE_REMOVED")
        }
        ime.registerReceiver(pluginManager, pFilter, Context.RECEIVER_NOT_EXPORTED)
        if (KeyMapGenerator.isDebugBuild(ime)) {
            ime.registerReceiver(pluginManager, IntentFilter("dev.devkey.keyboard.RESCAN_PLUGINS"), Context.RECEIVER_EXPORTED)
        }

        try { ime.initSuggest(inputLanguage) } catch (e: OutOfMemoryError) { Log.e(TAG, "OOM during dictionary init for $inputLanguage", e) }
        ime.mOrientation = conf.orientation
        val ringerFilter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        ime.registerReceiver(ime.mFeedbackManager.ringerModeReceiver, ringerFilter, Context.RECEIVER_NOT_EXPORTED)
        prefs.registerOnSharedPreferenceChangeListener(ime)
        ime.mNotificationController = NotificationController(ime, ime)
        ime.mNotificationController.setNotification(ime.mKeyboardNotification)

        var debugReceivers: dev.devkey.keyboard.debug.DebugReceiverManager? = null
        if (KeyMapGenerator.isDebugBuild(ime)) {
            debugReceivers = dev.devkey.keyboard.debug.DebugReceiverManager(ime)
            debugReceivers.registerAll()
        }

        return InitResult(clipboardManager, commandModeDetector, pluginManager, systemLocale.toString(), debugReceivers)
    }

    data class InitResult(
        val clipboardManager: DevKeyClipboardManager,
        val commandModeDetector: CommandModeDetector,
        val pluginManager: PluginManager,
        val systemLocale: String,
        val debugReceivers: dev.devkey.keyboard.debug.DebugReceiverManager?
    )

    companion object {
        private const val TAG = "DevKey/ImeInit"
    }
}
