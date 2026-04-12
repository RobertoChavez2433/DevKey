package dev.devkey.keyboard.core

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.widget.Toast
import dev.devkey.keyboard.*
import dev.devkey.keyboard.core.prefs.ImePrefsUtil
import dev.devkey.keyboard.data.repository.SettingsRepository

/**
 * Loads initial IME state from SharedPreferences and constructs
 * SwipeActionHandler + KeyEventSender + PunctuationHeuristics.
 *
 * Split from [ImeInitializer] to keep all initializers under 100 lines.
 */
internal class ImeStateLoader(
    private val ime: LatinIME,
    private val state: ImeState,
    private val col: ImeCollaborators,
    private val settingsRepository: SettingsRepository
) {

    fun loadPrefs(prefs: SharedPreferences, res: Resources) {
        state.mReCorrectionEnabled = prefs.getBoolean(PREF_RECORRECTION_ENABLED, res.getBoolean(R.bool.default_recorrection_enabled))
        state.mFullscreenOverride = prefs.getBoolean(PREF_FULLSCREEN_OVERRIDE, res.getBoolean(R.bool.default_fullscreen_override))
        state.mForceKeyboardOn = prefs.getBoolean(PREF_FORCE_KEYBOARD_ON, res.getBoolean(R.bool.default_force_keyboard_on))
        state.mKeyboardNotification = prefs.getBoolean(PREF_KEYBOARD_NOTIFICATION, res.getBoolean(R.bool.default_keyboard_notification))
        state.mSuggestionsInLandscape = prefs.getBoolean(PREF_SUGGESTIONS_IN_LANDSCAPE, res.getBoolean(R.bool.default_suggestions_in_landscape))
        state.mHeightPortrait = ImePrefsUtil.getHeight(prefs, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait))
        state.mHeightLandscape = ImePrefsUtil.getHeight(prefs, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape))
        settingsRepository.hintMode = prefs.getString(PREF_HINT_MODE, res.getString(R.string.default_hint_mode))!!.toInt()
        settingsRepository.longpressTimeout = ImePrefsUtil.getPrefInt(
            prefs, PREF_LONGPRESS_TIMEOUT, res.getString(R.string.default_long_press_duration)
        )
        settingsRepository.renderMode = ImePrefsUtil.getPrefInt(prefs, PREF_RENDER_MODE, res.getString(R.string.default_render_mode))
        settingsRepository.initPrefs(prefs, res)
    }

    fun createSwipeHandler(prefs: SharedPreferences, res: Resources): SwipeActionHandler {
        val handler = SwipeActionHandler(
            handleClose = { ime.handleClose() },
            launchSettings = { col.optionsMenuHandler.launchSettings() },
            toggleSuggestions = {
                if (state.mSuggestionForceOn) { state.mSuggestionForceOn = false; state.mSuggestionForceOff = true }
                else if (state.mSuggestionForceOff) { state.mSuggestionForceOn = true; state.mSuggestionForceOff = false }
                else if (col.suggestionCoordinator.isPredictionWanted()) { state.mSuggestionForceOff = true }
                else { state.mSuggestionForceOn = true }
                ime.setCandidatesViewShown(state.isPredictionOn(col.suggestionCoordinator.isPredictionWanted()))
            },
            toggleLanguagePrev = { col.dictionaryManager.toggleLanguage(false, false) },
            toggleLanguageNext = { col.dictionaryManager.toggleLanguage(false, true) },
            cycleFullMode = {
                if (state.isPortrait()) state.mKeyboardModeOverridePortrait = (state.mKeyboardModeOverridePortrait + 1) % state.mNumKeyboardModes
                else state.mKeyboardModeOverrideLandscape = (state.mKeyboardModeOverrideLandscape + 1) % state.mNumKeyboardModes
                col.dictionaryManager.toggleLanguage(true, true)
            },
            toggleExtension = { settingsRepository.useExtension = !settingsRepository.useExtension; state.reloadKeyboards(settingsRepository) },
            adjustHeightUp = {
                if (state.isPortrait()) { state.mHeightPortrait += 5; if (state.mHeightPortrait > 70) state.mHeightPortrait = 70 }
                else { state.mHeightLandscape += 5; if (state.mHeightLandscape > 70) state.mHeightLandscape = 70 }
                col.dictionaryManager.toggleLanguage(true, true)
            },
            adjustHeightDown = {
                if (state.isPortrait()) { state.mHeightPortrait -= 5; if (state.mHeightPortrait < 15) state.mHeightPortrait = 15 }
                else { state.mHeightLandscape -= 5; if (state.mHeightLandscape < 15) state.mHeightLandscape = 15 }
                col.dictionaryManager.toggleLanguage(true, true)
            }
        )
        handler.swipeUpAction = prefs.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
        handler.swipeDownAction = prefs.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
        handler.swipeLeftAction = prefs.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
        handler.swipeRightAction = prefs.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right))
        handler.volUpAction = prefs.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
        handler.volDownAction = prefs.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
        return handler
    }

    fun createKeyEventSender(context: Context, res: Resources): KeyEventSender = KeyEventSender(
        inputConnectionProvider = { ime.currentInputConnection },
        modCtrlProvider = { state.mModCtrl }, modAltProvider = { state.mModAlt },
        modMetaProvider = { state.mModMeta }, shiftKeyStateProvider = { state.mShiftKeyState },
        ctrlKeyStateProvider = { state.mCtrlKeyState }, altKeyStateProvider = { state.mAltKeyState },
        metaKeyStateProvider = { state.mMetaKeyState },
        shiftModProvider = { col.modifierHandler.isShiftMod() },
        setModCtrl = { state.mModCtrl = it }, setModAlt = { state.mModAlt = it },
        setModMeta = { state.mModMeta = it }, resetShift = { col.modifierHandler.resetShift() },
        getShiftState = { col.modifierHandler.getShiftState() },
        commitTyped = { ic, manual -> col.inputHandlers.commitTyped(ic, manual) },
        sendKeyCharFn = { ch -> ime.sendKeyChar(ch) },
        sendDownUpKeyEventsFn = { code -> ime.sendDownUpKeyEvents(code) },
        settings = settingsRepository,
        ctrlAToastAction = {
            Toast.makeText(
                context.applicationContext,
                res.getString(R.string.toast_ctrl_a_override_info),
                Toast.LENGTH_LONG
            ).show()
        }
    )

    fun createPuncHeuristics(): PunctuationHeuristics = PunctuationHeuristics(
        icProvider = { ime.currentInputConnection },
        settings = settingsRepository,
        updateShiftKeyState = { col.modifierHandler.updateShiftKeyState(ime.currentInputEditorInfo) }
    )
}
