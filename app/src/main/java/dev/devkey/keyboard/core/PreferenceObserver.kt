package dev.devkey.keyboard.core

import android.content.Context
import android.util.Log
import android.widget.Toast
import dev.devkey.keyboard.*
import dev.devkey.keyboard.core.prefs.ImePrefsUtil
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionLevel
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import java.util.Locale

internal class PreferenceObserver(
    private val state: ImeState,
    private val context: Context,
    private val candidateViewHost: CandidateViewHost,
    private val feedbackManager: FeedbackManager,
    private val swipeHandler: SwipeActionHandler,
    private val puncHeuristics: PunctuationHeuristics,
    private val notificationController: NotificationController,
    private val settings: SettingsRepository,
    private val updateKeyboardOptions: () -> Unit,
    private val toggleLanguage: () -> Unit,
    private val setNextSuggestions: () -> Unit,
    private val isPredictionOn: () -> Boolean,
) {

    fun onPreferenceChanged(changedSettings: Any, key: String?) {
        Log.i(TAG, "onSharedPreferenceChanged()")
        @Suppress("UNUSED_VARIABLE")
        val ignoredChangeSource = changedSettings
        var needReload = false
        val res = context.resources
        val settings = this.settings
        val legacyPrefs = settings.legacyPrefs()
        settings.handlePreferenceChanged(legacyPrefs, key)
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_NEED_RELOAD)) needReload = true
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_NEW_PUNC_LIST)) initSuggestPuncList()
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_RESET_MODE_OVERRIDE)) {
            state.mKeyboardModeOverrideLandscape = 0
            state.mKeyboardModeOverridePortrait = 0
        }
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_RESET_KEYBOARDS)) {
            toggleLanguage()
        }
        val unhandledFlags = settings.unhandledFlags()
        if (unhandledFlags != SettingsRepository.FLAG_PREF_NONE) {
            Log.w(TAG, "Not all flag settings handled, remaining=$unhandledFlags")
        }

        when (key) {
            PREF_SELECTED_LANGUAGES -> {
                state.mLanguageSwitcher!!.loadLocales(settings)
                state.mRefreshKeyboardRequired = true
            }
            PREF_RECORRECTION_ENABLED -> {
                state.mReCorrectionEnabled = settings.getBoolean(
                    PREF_RECORRECTION_ENABLED, res.getBoolean(R.bool.default_recorrection_enabled)
                )
                if (state.mReCorrectionEnabled) {
                    Toast.makeText(context.applicationContext,
                        res.getString(R.string.recorrect_warning), Toast.LENGTH_LONG).show()
                }
            }
            PREF_FULLSCREEN_OVERRIDE -> {
                state.mFullscreenOverride = settings.getBoolean(PREF_FULLSCREEN_OVERRIDE, res.getBoolean(R.bool.default_fullscreen_override))
                needReload = true
            }
            PREF_FORCE_KEYBOARD_ON -> {
                state.mForceKeyboardOn = settings.getBoolean(PREF_FORCE_KEYBOARD_ON, res.getBoolean(R.bool.default_force_keyboard_on))
                needReload = true
            }
            PREF_KEYBOARD_NOTIFICATION -> {
                state.mKeyboardNotification = settings.getBoolean(
                    PREF_KEYBOARD_NOTIFICATION, res.getBoolean(R.bool.default_keyboard_notification)
                )
                notificationController.setNotification(state.mKeyboardNotification)
            }
            PREF_SUGGESTIONS_IN_LANDSCAPE -> {
                state.mSuggestionsInLandscape = settings.getBoolean(
                    PREF_SUGGESTIONS_IN_LANDSCAPE,
                    res.getBoolean(R.bool.default_suggestions_in_landscape)
                )
                state.mSuggestionForceOff = false; state.mSuggestionForceOn = false
                candidateViewHost.setCandidatesViewShown(isPredictionOn())
            }
            PREF_SHOW_SUGGESTIONS -> {
                state.mShowSuggestions = settings.getBoolean(PREF_SHOW_SUGGESTIONS, res.getBoolean(R.bool.default_suggestions))
                state.mSuggestionForceOff = false; state.mSuggestionForceOn = false
                needReload = true
            }
            PREF_HEIGHT_PORTRAIT -> {
                state.mHeightPortrait = ImePrefsUtil.getHeight(
                    legacyPrefs, PREF_HEIGHT_PORTRAIT,
                    res.getString(R.string.default_height_portrait)
                )
                needReload = true
            }
            PREF_HEIGHT_LANDSCAPE -> {
                state.mHeightLandscape = ImePrefsUtil.getHeight(
                    legacyPrefs, PREF_HEIGHT_LANDSCAPE,
                    res.getString(R.string.default_height_landscape)
                )
                needReload = true
            }
            PREF_HINT_MODE -> {
                settings.hintMode = settings.getString(PREF_HINT_MODE, res.getString(R.string.default_hint_mode)).toInt()
                needReload = true
            }
            PREF_LONGPRESS_TIMEOUT -> settings.longpressTimeout = ImePrefsUtil.getPrefInt(
                legacyPrefs, PREF_LONGPRESS_TIMEOUT,
                res.getString(R.string.default_long_press_duration)
            )
            PREF_RENDER_MODE -> {
                settings.renderMode = ImePrefsUtil.getPrefInt(legacyPrefs, PREF_RENDER_MODE, res.getString(R.string.default_render_mode))
                needReload = true
            }
            PREF_SWIPE_UP -> swipeHandler.swipeUpAction = settings.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
            PREF_SWIPE_DOWN -> swipeHandler.swipeDownAction = settings.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
            PREF_SWIPE_LEFT -> swipeHandler.swipeLeftAction = settings.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
            PREF_SWIPE_RIGHT -> swipeHandler.swipeRightAction = settings.getString(
                PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right)
            )
            PREF_VOL_UP -> swipeHandler.volUpAction = settings.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
            PREF_VOL_DOWN -> swipeHandler.volDownAction = settings.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
            PREF_VIBRATE_LEN -> {
                feedbackManager.vibrateLen = ImePrefsUtil.getPrefInt(
                    legacyPrefs, PREF_VIBRATE_LEN, context.resources.getString(R.string.vibrate_duration_ms))
            }
            SettingsRepository.KEY_AUTOCORRECT_LEVEL -> {
                val level = settings.getString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild")
                SessionDependencies.smartTextCorrectionLevel = parseSmartTextCorrectionLevel(level)
            }
            PREF_AUTO_CAP -> {
                state.mAutoCapPref = settings.getBoolean(
                    PREF_AUTO_CAP, res.getBoolean(R.bool.default_auto_cap)
                )
                state.mAutoCapActive = state.mAutoCapPref && state.mLanguageSwitcher!!.allowAutoCap()
            }
        }

        updateKeyboardOptions()
        if (needReload) state.mKeyboardSwitcher!!.makeKeyboards(true)
    }

    fun loadSettings() {
        val legacyPrefs = settings.legacyPrefs()
        val res = context.resources
        feedbackManager.vibrateOn = settings.getBoolean(PREF_VIBRATE_ON, false)
        feedbackManager.vibrateLen = ImePrefsUtil.getPrefInt(legacyPrefs, PREF_VIBRATE_LEN, res.getString(R.string.vibrate_duration_ms))
        feedbackManager.soundOn = settings.getBoolean(PREF_SOUND_ON, false)
        state.mPopupOn = settings.getBoolean(PREF_POPUP_ON, res.getBoolean(R.bool.default_popup_preview))
        state.mAutoCapPref = settings.getBoolean(PREF_AUTO_CAP, res.getBoolean(R.bool.default_auto_cap))
        state.mQuickFixes = settings.getBoolean(PREF_QUICK_FIXES, true)
        state.mShowSuggestions = settings.getBoolean(PREF_SHOW_SUGGESTIONS, res.getBoolean(R.bool.default_suggestions))

        val voiceMode = settings.getString(PREF_VOICE_MODE, context.getString(R.string.voice_mode_main))
        val enableVoice = voiceMode != context.getString(R.string.voice_mode_off) && state.mEnableVoiceButton
        val voiceOnPrimary = voiceMode == context.getString(R.string.voice_mode_main)
        if (state.mKeyboardSwitcher != null
            && (enableVoice != state.mEnableVoice || voiceOnPrimary != state.mVoiceOnPrimary)
        ) {
            state.mKeyboardSwitcher!!.setVoiceMode(enableVoice, voiceOnPrimary)
        }
        state.mEnableVoice = enableVoice
        state.mVoiceOnPrimary = voiceOnPrimary

        state.mAutoCorrectEnabled = settings.getBoolean(
            PREF_AUTO_COMPLETE, res.getBoolean(R.bool.enable_autocorrect)
        ) && state.mShowSuggestions
        updateCorrectionMode()

        val autocorrectLevel = settings.getString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild")
        SessionDependencies.smartTextCorrectionLevel = parseSmartTextCorrectionLevel(autocorrectLevel)
        updateAutoTextEnabled(res.configuration.locales[0])
        state.mLanguageSwitcher!!.loadLocales(settings)
        state.mAutoCapActive = state.mAutoCapPref && state.mLanguageSwitcher!!.allowAutoCap()
        state.mDeadKeysActive = state.mLanguageSwitcher!!.allowDeadKeys()
    }

    fun updateCorrectionMode() {
        state.mHasDictionary = state.mSuggest?.hasMainDictionary() == true ||
            SessionDependencies.dictionaryProvider?.hasDictionary() == true
        state.mAutoCorrectOn = (state.mAutoCorrectEnabled || state.mQuickFixes)
            && !state.mInputTypeNoAutoCorrect && state.mHasDictionary
        state.mCorrectionMode = when {
            state.mAutoCorrectOn && state.mAutoCorrectEnabled -> Suggest.CORRECTION_FULL
            state.mAutoCorrectOn -> Suggest.CORRECTION_BASIC
            else -> Suggest.CORRECTION_NONE
        }
        if (state.suggestionsDisabled()) {
            state.mAutoCorrectOn = false
            state.mCorrectionMode = Suggest.CORRECTION_NONE
        }
        state.mSuggest?.setCorrectionMode(state.mCorrectionMode)
    }

    fun updateAutoTextEnabled(systemLocale: Locale) {
        if (state.mSuggest == null) return
        val different = !systemLocale.language.equals(
            state.mInputLocale!!.take(2), ignoreCase = true
        )
        state.mSuggest!!.setAutoTextEnabled(!different && state.mQuickFixes)
    }

    fun initSuggestPuncList() {
        val res = context.resources
        puncHeuristics.defaultPunctuations = res.getString(R.string.suggested_punctuations_default)
        puncHeuristics.actualPunctuations = res.getString(R.string.suggested_punctuations)
        puncHeuristics.initSuggestPuncList()
        state.mSuggestPuncList = puncHeuristics.suggestPuncList
        setNextSuggestions()
    }

    companion object {
        private const val TAG = "DevKey/PrefObserver"

        private fun parseSmartTextCorrectionLevel(level: String): SmartTextCorrectionLevel =
            when (level) {
                "aggressive" -> SmartTextCorrectionLevel.AGGRESSIVE
                "off" -> SmartTextCorrectionLevel.OFF
                else -> SmartTextCorrectionLevel.MILD
            }
    }
}
