// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard.keyboard.switcher
import dev.devkey.keyboard.LanguageSwitcher
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.keyboard.model.KeyboardId
import dev.devkey.keyboard.keyboard.model.KeyboardModes
import dev.devkey.keyboard.keyboard.model.getKeyboardId
import dev.devkey.keyboard.keyboard.model.makeSymbolsId
import dev.devkey.keyboard.keyboard.model.makeSymbolsShiftedId

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import android.util.Log

class KeyboardSwitcher private constructor() :
        SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "DevKey/KeyboardSwitcher"
        // Re-export constants so existing callers via KeyboardSwitcher.MODE_* still compile.
        const val MODE_NONE = KeyboardModes.MODE_NONE
        const val MODE_TEXT = KeyboardModes.MODE_TEXT
        const val MODE_SYMBOLS = KeyboardModes.MODE_SYMBOLS
        const val MODE_PHONE = KeyboardModes.MODE_PHONE
        const val MODE_URL = KeyboardModes.MODE_URL
        const val MODE_EMAIL = KeyboardModes.MODE_EMAIL
        const val MODE_IM = KeyboardModes.MODE_IM
        const val MODE_WEB = KeyboardModes.MODE_WEB
        @JvmField val KEYBOARDMODE_NORMAL = KeyboardModes.KEYBOARDMODE_NORMAL
        @JvmField val KEYBOARDMODE_SYMBOLS = KeyboardModes.KEYBOARDMODE_SYMBOLS
        @JvmField val KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY = KeyboardModes.KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY
        @JvmField val KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY = KeyboardModes.KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY
        @JvmField val KEYBOARDMODE_URL = KeyboardModes.KEYBOARDMODE_URL
        @JvmField val KEYBOARDMODE_URL_WITH_SETTINGS_KEY = KeyboardModes.KEYBOARDMODE_URL_WITH_SETTINGS_KEY
        @JvmField val KEYBOARDMODE_EMAIL = KeyboardModes.KEYBOARDMODE_EMAIL
        @JvmField val KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY = KeyboardModes.KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY
        @JvmField val KEYBOARDMODE_IM = KeyboardModes.KEYBOARDMODE_IM
        @JvmField val KEYBOARDMODE_IM_WITH_SETTINGS_KEY = KeyboardModes.KEYBOARDMODE_IM_WITH_SETTINGS_KEY
        @JvmField val KEYBOARDMODE_WEB = KeyboardModes.KEYBOARDMODE_WEB
        @JvmField val KEYBOARDMODE_WEB_WITH_SETTINGS_KEY = KeyboardModes.KEYBOARDMODE_WEB_WITH_SETTINGS_KEY

        private val sInstance = KeyboardSwitcher()
        fun getInstance(): KeyboardSwitcher = sInstance

        fun init(ims: LatinIME) {
            sInstance.mInputMethodService = ims
            sInstance.mKeyboardCache = KeyboardCache(
                imeProvider = { sInstance.mInputMethodService!! },
                languageSwitcherProvider = { sInstance.mLanguageSwitcher },
                isAutoCompletionActiveProvider = { sInstance.mIsAutoCompletionActive },
                hasVoiceButtonFn = { isSymbols -> sInstance.mHasVoice && (isSymbols != sInstance.mVoiceOnPrimary) },
                hasVoiceProvider = { sInstance.mHasVoice }
            )
            val prefs = PreferenceManager.getDefaultSharedPreferences(ims)
            sInstance.updateSettingsKeyState(prefs)
            prefs.registerOnSharedPreferenceChangeListener(sInstance)
            sInstance.mSymbolsId = makeSymbolsId(false, sInstance.mFullMode, sInstance.mHasSettingsKey)
            sInstance.mSymbolsShiftedId = makeSymbolsShiftedId(false, sInstance.mFullMode, sInstance.mHasSettingsKey)
        }
    }

    private var mInputMethodService: LatinIME? = null
    private var mShiftState: Int = Keyboard.SHIFT_OFF
    private var mSymbolsId: KeyboardId? = null
    private var mSymbolsShiftedId: KeyboardId? = null
    private var mCurrentId: KeyboardId? = null
    private var mKeyboardCache: KeyboardCache? = null
    private var mMode = MODE_NONE
    private var mImeOptions = 0
    private var mIsSymbols = false
    var mFullMode = 0; private set
    private var mIsAutoCompletionActive = false
    private var mHasVoice = false
    private var mVoiceOnPrimary = false
    private var mPreferSymbols = false
    private val mAutoModeSwitcher = AutoModeSwitchStateMachine(
        changeKeyboardMode = { mInputMethodService!!.changeKeyboardMode() },
        getPointerCount = { 1 }
    )
    var mHasSettingsKey = false; private set
    private var mLastDisplayWidth = 0
    private var mLanguageSwitcher: LanguageSwitcher? = null

    fun setLanguageSwitcher(languageSwitcher: LanguageSwitcher) {
        mLanguageSwitcher = languageSwitcher
        languageSwitcher.getInputLocale()
    }

    fun makeKeyboards(forceCreate: Boolean) {
        mFullMode = LatinIME.sKeyboardSettings.keyboardMode
        mSymbolsId = makeSymbolsId(mHasVoice && !mVoiceOnPrimary, mFullMode, mHasSettingsKey)
        mSymbolsShiftedId = makeSymbolsShiftedId(mHasVoice && !mVoiceOnPrimary, mFullMode, mHasSettingsKey)
        mKeyboardCache!!.refreshIfNeeded(forceCreate, mInputMethodService!!.maxWidth)
    }

    fun setVoiceMode(enableVoice: Boolean, voiceOnPrimary: Boolean) {
        if (enableVoice != mHasVoice || voiceOnPrimary != mVoiceOnPrimary) mKeyboardCache?.clear()
        mHasVoice = enableVoice; mVoiceOnPrimary = voiceOnPrimary
        setKeyboardMode(mMode, mImeOptions, mHasVoice, mIsSymbols)
    }

    fun setKeyboardMode(mode: Int, imeOptions: Int, enableVoice: Boolean) {
        mAutoModeSwitcher.reset()
        mPreferSymbols = mode == MODE_SYMBOLS
        val effectiveMode = if (mode == MODE_SYMBOLS) MODE_TEXT else mode
        try { setKeyboardMode(effectiveMode, imeOptions, enableVoice, mPreferSymbols) }
        catch (e: RuntimeException) { Log.e(TAG, "Got exception: $effectiveMode,$imeOptions,$mPreferSymbols msg=${e.message}") }
    }

    private fun setKeyboardMode(mode: Int, imeOptions: Int, enableVoice: Boolean, isSymbols: Boolean) {
        mMode = mode; mImeOptions = imeOptions
        if (enableVoice != mHasVoice) setVoiceMode(enableVoice, mVoiceOnPrimary)
        mIsSymbols = isSymbols
        mCurrentId = getKeyboardId(mode, imeOptions, isSymbols, mHasVoice && (isSymbols != mVoiceOnPrimary), mFullMode, mHasSettingsKey)
        mShiftState = Keyboard.SHIFT_OFF
    }

    fun isFullMode(): Boolean = mFullMode > 0
    fun getKeyboardMode(): Int = mMode

    fun isAlphabetMode(): Boolean {
        val currentId = mCurrentId ?: return false
        if (mFullMode > 0 && currentId.mKeyboardMode == KEYBOARDMODE_NORMAL) return true
        return currentId.mKeyboardMode in KeyboardModes.ALPHABET_MODES
    }

    fun setShiftState(shiftState: Int) { mShiftState = shiftState }
    fun getShiftState(): Int = mShiftState

    fun setFn(useFn: Boolean) {
        val oldShiftState = mShiftState
        if (useFn) mCurrentId = mSymbolsId
        else setKeyboardMode(mMode, mImeOptions, mHasVoice, false)
        mShiftState = oldShiftState
    }

    fun toggleShift() {
        if (isAlphabetMode()) return
        if (mFullMode > 0) {
            val shifted = mShiftState == Keyboard.SHIFT_ON || mShiftState == Keyboard.SHIFT_LOCKED || mShiftState == Keyboard.SHIFT_CAPS_LOCKED
            mShiftState = if (shifted) Keyboard.SHIFT_OFF else Keyboard.SHIFT_ON
            return
        }
        if (mCurrentId == mSymbolsId || mCurrentId != mSymbolsShiftedId) {
            mCurrentId = mSymbolsShiftedId; mShiftState = Keyboard.SHIFT_LOCKED
        } else {
            mCurrentId = mSymbolsId; mShiftState = Keyboard.SHIFT_OFF
        }
    }

    fun onCancelInput() { mAutoModeSwitcher.onCancelInput() }

    fun toggleSymbols() {
        setKeyboardMode(mMode, mImeOptions, mHasVoice, !mIsSymbols)
        mAutoModeSwitcher.onToggleSymbols(mIsSymbols, mPreferSymbols)
    }

    fun setAutoModeSwitchStateMomentary() { mAutoModeSwitcher.setMomentary() }
    fun isInMomentaryAutoModeSwitchState(): Boolean = mAutoModeSwitcher.isInMomentaryState()
    fun isInChordingAutoModeSwitchState(): Boolean = mAutoModeSwitcher.isInChordingState()
    fun onKey(key: Int) { mAutoModeSwitcher.onKey(key, mIsSymbols) }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (KeyboardModes.PREF_SETTINGS_KEY == key) updateSettingsKeyState(sharedPreferences)
    }

    fun onAutoCompletionStateChanged(isAutoCompletion: Boolean) { mIsAutoCompletionActive = isAutoCompletion }

    private fun updateSettingsKeyState(prefs: SharedPreferences) {
        val resources = mInputMethodService!!.resources
        val settingsKeyMode = prefs.getString(
            KeyboardModes.PREF_SETTINGS_KEY,
            resources.getString(KeyboardModes.DEFAULT_SETTINGS_KEY_MODE))
        mHasSettingsKey = settingsKeyMode == resources.getString(KeyboardModes.SETTINGS_KEY_MODE_ALWAYS_SHOW)
                || settingsKeyMode == resources.getString(KeyboardModes.SETTINGS_KEY_MODE_AUTO)
    }
}
