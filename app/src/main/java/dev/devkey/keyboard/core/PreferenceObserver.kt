package dev.devkey.keyboard.core

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.*
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.language.LanguageSwitcher
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import java.util.Locale

internal class PreferenceObserver(private val ime: LatinIME) {

    fun onPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.i(TAG, "onSharedPreferenceChanged()")
        var needReload = false
        val res = ime.resources
        val settings = LatinIME.sKeyboardSettings
        settings.handlePreferenceChanged(sharedPreferences, key)
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_NEED_RELOAD)) needReload = true
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_NEW_PUNC_LIST)) initSuggestPuncList()
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_RESET_MODE_OVERRIDE)) {
            ime.mKeyboardModeOverrideLandscape = 0
            ime.mKeyboardModeOverridePortrait = 0
        }
        if (settings.consumeFlag(SettingsRepository.FLAG_PREF_RESET_KEYBOARDS)) {
            ime.toggleLanguage(reset = true, next = true)
        }
        val unhandledFlags = settings.unhandledFlags()
        if (unhandledFlags != SettingsRepository.FLAG_PREF_NONE) {
            Log.w(TAG, "Not all flag settings handled, remaining=$unhandledFlags")
        }

        when (key) {
            PREF_SELECTED_LANGUAGES -> {
                ime.mLanguageSwitcher!!.loadLocales(sharedPreferences)
                ime.mRefreshKeyboardRequired = true
            }
            PREF_RECORRECTION_ENABLED -> {
                ime.mReCorrectionEnabled = sharedPreferences.getBoolean(
                    PREF_RECORRECTION_ENABLED, res.getBoolean(R.bool.default_recorrection_enabled)
                )
                if (ime.mReCorrectionEnabled) {
                    Toast.makeText(ime.applicationContext,
                        res.getString(R.string.recorrect_warning), Toast.LENGTH_LONG).show()
                }
            }
            PREF_FULLSCREEN_OVERRIDE -> {
                ime.mFullscreenOverride = sharedPreferences.getBoolean(PREF_FULLSCREEN_OVERRIDE, res.getBoolean(R.bool.default_fullscreen_override))
                needReload = true
            }
            PREF_FORCE_KEYBOARD_ON -> {
                ime.mForceKeyboardOn = sharedPreferences.getBoolean(PREF_FORCE_KEYBOARD_ON, res.getBoolean(R.bool.default_force_keyboard_on))
                needReload = true
            }
            PREF_KEYBOARD_NOTIFICATION -> {
                ime.mKeyboardNotification = sharedPreferences.getBoolean(PREF_KEYBOARD_NOTIFICATION, res.getBoolean(R.bool.default_keyboard_notification))
                ime.mNotificationController.setNotification(ime.mKeyboardNotification)
            }
            PREF_SUGGESTIONS_IN_LANDSCAPE -> {
                ime.mSuggestionsInLandscape = sharedPreferences.getBoolean(PREF_SUGGESTIONS_IN_LANDSCAPE, res.getBoolean(R.bool.default_suggestions_in_landscape))
                ime.mSuggestionForceOff = false; ime.mSuggestionForceOn = false
                ime.setCandidatesViewShown(ime.isPredictionOn())
            }
            PREF_SHOW_SUGGESTIONS -> {
                ime.mShowSuggestions = sharedPreferences.getBoolean(PREF_SHOW_SUGGESTIONS, res.getBoolean(R.bool.default_suggestions))
                ime.mSuggestionForceOff = false; ime.mSuggestionForceOn = false
                needReload = true
            }
            PREF_HEIGHT_PORTRAIT -> {
                ime.mHeightPortrait = LatinIME.getHeight(sharedPreferences, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait))
                needReload = true
            }
            PREF_HEIGHT_LANDSCAPE -> {
                ime.mHeightLandscape = LatinIME.getHeight(sharedPreferences, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape))
                needReload = true
            }
            PREF_HINT_MODE -> {
                settings.hintMode = sharedPreferences.getString(PREF_HINT_MODE, res.getString(R.string.default_hint_mode))!!.toInt()
                needReload = true
            }
            PREF_LONGPRESS_TIMEOUT -> settings.longpressTimeout = LatinIME.getPrefInt(sharedPreferences, PREF_LONGPRESS_TIMEOUT, res.getString(R.string.default_long_press_duration))
            PREF_RENDER_MODE -> {
                settings.renderMode = LatinIME.getPrefInt(sharedPreferences, PREF_RENDER_MODE, res.getString(R.string.default_render_mode))
                needReload = true
            }
            PREF_SWIPE_UP -> ime.mSwipeHandler.swipeUpAction = sharedPreferences.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up))
            PREF_SWIPE_DOWN -> ime.mSwipeHandler.swipeDownAction = sharedPreferences.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down))
            PREF_SWIPE_LEFT -> ime.mSwipeHandler.swipeLeftAction = sharedPreferences.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left))
            PREF_SWIPE_RIGHT -> ime.mSwipeHandler.swipeRightAction = sharedPreferences.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right))
            PREF_VOL_UP -> ime.mSwipeHandler.volUpAction = sharedPreferences.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up))
            PREF_VOL_DOWN -> ime.mSwipeHandler.volDownAction = sharedPreferences.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down))
            PREF_VIBRATE_LEN -> {
                ime.mFeedbackManager.vibrateLen = LatinIME.getPrefInt(
                    sharedPreferences, PREF_VIBRATE_LEN, ime.resources.getString(R.string.vibrate_duration_ms))
            }
            SettingsRepository.KEY_AUTOCORRECT_LEVEL -> {
                val level = sharedPreferences.getString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild") ?: "mild"
                SessionDependencies.autocorrectEngine?.aggressiveness = when (level) {
                    "aggressive" -> AutocorrectEngine.Aggressiveness.AGGRESSIVE
                    "off" -> AutocorrectEngine.Aggressiveness.OFF
                    else -> AutocorrectEngine.Aggressiveness.MILD
                }
            }
        }

        ime.updateKeyboardOptions()
        if (needReload) ime.mKeyboardSwitcher!!.makeKeyboards(true)
    }

    fun loadSettings() {
        val sp = PreferenceManager.getDefaultSharedPreferences(ime)
        val res = ime.resources
        ime.mFeedbackManager.vibrateOn = sp.getBoolean(PREF_VIBRATE_ON, false)
        ime.mFeedbackManager.vibrateLen = LatinIME.getPrefInt(sp, PREF_VIBRATE_LEN, res.getString(R.string.vibrate_duration_ms))
        ime.mFeedbackManager.soundOn = sp.getBoolean(PREF_SOUND_ON, false)
        ime.mPopupOn = sp.getBoolean(PREF_POPUP_ON, ime.mResources!!.getBoolean(R.bool.default_popup_preview))
        ime.mAutoCapPref = sp.getBoolean(PREF_AUTO_CAP, res.getBoolean(R.bool.default_auto_cap))
        ime.mQuickFixes = sp.getBoolean(PREF_QUICK_FIXES, true)
        ime.mShowSuggestions = sp.getBoolean(PREF_SHOW_SUGGESTIONS, ime.mResources!!.getBoolean(R.bool.default_suggestions))

        val voiceMode = sp.getString(PREF_VOICE_MODE, ime.getString(R.string.voice_mode_main))
        val enableVoice = voiceMode != ime.getString(R.string.voice_mode_off) && ime.mEnableVoiceButton
        val voiceOnPrimary = voiceMode == ime.getString(R.string.voice_mode_main)
        if (ime.mKeyboardSwitcher != null
            && (enableVoice != ime.mEnableVoice || voiceOnPrimary != ime.mVoiceOnPrimary)
        ) {
            ime.mKeyboardSwitcher!!.setVoiceMode(enableVoice, voiceOnPrimary)
        }
        ime.mEnableVoice = enableVoice
        ime.mVoiceOnPrimary = voiceOnPrimary

        ime.mAutoCorrectEnabled = sp.getBoolean(
            PREF_AUTO_COMPLETE, ime.mResources!!.getBoolean(R.bool.enable_autocorrect)
        ) && ime.mShowSuggestions
        updateCorrectionMode()

        val autocorrectLevel = sp.getString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild") ?: "mild"
        SessionDependencies.autocorrectEngine?.aggressiveness = when (autocorrectLevel) {
            "aggressive" -> AutocorrectEngine.Aggressiveness.AGGRESSIVE
            "off" -> AutocorrectEngine.Aggressiveness.OFF
            else -> AutocorrectEngine.Aggressiveness.MILD
        }
        updateAutoTextEnabled(ime.mResources!!.configuration.locales[0])
        ime.mLanguageSwitcher!!.loadLocales(sp)
        ime.mAutoCapActive = ime.mAutoCapPref && ime.mLanguageSwitcher!!.allowAutoCap()
        ime.mDeadKeysActive = ime.mLanguageSwitcher!!.allowDeadKeys()
    }

    fun updateCorrectionMode() {
        ime.mHasDictionary = ime.mSuggest?.hasMainDictionary() ?: false
        ime.mAutoCorrectOn = (ime.mAutoCorrectEnabled || ime.mQuickFixes)
            && !ime.mInputTypeNoAutoCorrect && ime.mHasDictionary
        ime.mCorrectionMode = when {
            ime.mAutoCorrectOn && ime.mAutoCorrectEnabled -> Suggest.CORRECTION_FULL
            ime.mAutoCorrectOn -> Suggest.CORRECTION_BASIC
            else -> Suggest.CORRECTION_NONE
        }
        if (ime.suggestionsDisabled()) {
            ime.mAutoCorrectOn = false
            ime.mCorrectionMode = Suggest.CORRECTION_NONE
        }
        ime.mSuggest?.setCorrectionMode(ime.mCorrectionMode)
    }

    fun updateAutoTextEnabled(systemLocale: Locale) {
        if (ime.mSuggest == null) return
        val different = !systemLocale.language.equals(
            ime.mInputLocale!!.take(2), ignoreCase = true
        )
        ime.mSuggest!!.setAutoTextEnabled(!different && ime.mQuickFixes)
    }

    fun initSuggestPuncList() {
        val res = ime.resources
        ime.mPuncHeuristics.defaultPunctuations = res.getString(R.string.suggested_punctuations_default)
        ime.mPuncHeuristics.actualPunctuations = res.getString(R.string.suggested_punctuations)
        ime.mPuncHeuristics.initSuggestPuncList()
        ime.mSuggestPuncList = ime.mPuncHeuristics.suggestPuncList
        ime.setNextSuggestions()
    }

    companion object {
        private const val TAG = "DevKey/PrefObserver"
    }
}
