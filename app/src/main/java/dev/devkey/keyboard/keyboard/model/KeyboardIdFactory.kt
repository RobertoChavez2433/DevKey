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

package dev.devkey.keyboard.keyboard.model

import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.R
import java.util.Arrays

/**
 * Represents the parameters necessary to construct a new [LatinKeyboard],
 * which also serve as a unique identifier for each keyboard type.
 */
internal class KeyboardId(
    val mXml: Int,
    val mKeyboardMode: Int,
    /** A KEYBOARDMODE_XXX value */
    val mEnableShiftLock: Boolean,
    val mHasVoice: Boolean
) {
    val mKeyboardHeightPercent: Float = LatinIME.sKeyboardSettings.keyboardHeightPercent
    val mUsingExtension: Boolean = LatinIME.sKeyboardSettings.useExtension

    private val mHashCode: Int = Arrays.hashCode(
        arrayOf(mXml, mKeyboardMode, mEnableShiftLock, mHasVoice, mKeyboardHeightPercent)
    )

    override fun equals(other: Any?): Boolean {
        return other is KeyboardId && equals(other)
    }

    private fun equals(other: KeyboardId?): Boolean {
        return other != null
                && other.mXml == this.mXml
                && other.mKeyboardMode == this.mKeyboardMode
                && other.mUsingExtension == this.mUsingExtension
                && other.mEnableShiftLock == this.mEnableShiftLock
                && other.mHasVoice == this.mHasVoice
                && other.mKeyboardHeightPercent == this.mKeyboardHeightPercent
    }

    override fun hashCode(): Int = mHashCode
}

/**
 * Creates the [KeyboardId] for the symbols keyboard.
 *
 * @param hasVoice whether a voice button should appear on the keyboard.
 * @param fullMode 0 = standard; 1 = compact full layout; 2 = full layout.
 * @param hasSettingsKey whether the settings key variant should be used.
 */
internal fun makeSymbolsId(hasVoice: Boolean, fullMode: Int, hasSettingsKey: Boolean): KeyboardId {
    if (fullMode == 1) {
        return KeyboardId(R.xml.kbd_compact_fn, KeyboardSwitcher.KEYBOARDMODE_SYMBOLS, true, hasVoice)
    } else if (fullMode == 2) {
        return KeyboardId(R.xml.kbd_full_fn, KeyboardSwitcher.KEYBOARDMODE_SYMBOLS, true, hasVoice)
    }
    return KeyboardId(
        R.xml.kbd_symbols,
        if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY
        else KeyboardSwitcher.KEYBOARDMODE_SYMBOLS,
        false, hasVoice
    )
}

/**
 * Creates the [KeyboardId] for the shifted-symbols keyboard, or null in full mode.
 *
 * @param hasVoice whether a voice button should appear on the keyboard.
 * @param fullMode 0 = standard; 1 = compact full layout; 2 = full layout.
 * @param hasSettingsKey whether the settings key variant should be used.
 */
internal fun makeSymbolsShiftedId(hasVoice: Boolean, fullMode: Int, hasSettingsKey: Boolean): KeyboardId? {
    if (fullMode > 0) return null
    return KeyboardId(
        R.xml.kbd_symbols_shift,
        if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY
        else KeyboardSwitcher.KEYBOARDMODE_SYMBOLS,
        false, hasVoice
    )
}

/**
 * Resolves the [KeyboardId] for the given input [mode], IME options, and symbol state.
 *
 * @param mode one of the [KeyboardSwitcher].MODE_XXX values.
 * @param isSymbols whether the keyboard is in symbols mode.
 * @param hasVoice whether a voice button should appear on the keyboard.
 * @param fullMode 0 = standard; 1 = compact full layout; 2 = full layout.
 * @param hasSettingsKey whether the settings key variant should be used.
 */
internal fun getKeyboardId(
    mode: Int,
    @Suppress("UNUSED_PARAMETER") imeOptions: Int,
    isSymbols: Boolean,
    hasVoice: Boolean,
    fullMode: Int,
    hasSettingsKey: Boolean
): KeyboardId {
    if (fullMode > 0) {
        when (mode) {
            KeyboardSwitcher.MODE_TEXT,
            KeyboardSwitcher.MODE_URL,
            KeyboardSwitcher.MODE_EMAIL,
            KeyboardSwitcher.MODE_IM,
            KeyboardSwitcher.MODE_WEB ->
                return KeyboardId(
                    if (fullMode == 1) R.xml.kbd_compact else R.xml.kbd_full,
                    KeyboardSwitcher.KEYBOARDMODE_NORMAL, true, hasVoice
                )
        }
    }
    // TODO: generalize for any KeyboardId
    val keyboardRowsResId = R.xml.kbd_qwerty
    if (isSymbols) {
        return if (mode == KeyboardSwitcher.MODE_PHONE) {
            KeyboardId(R.xml.kbd_phone_symbols, 0, false, hasVoice)
        } else {
            KeyboardId(
                R.xml.kbd_symbols,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_SYMBOLS,
                false, hasVoice
            )
        }
    }
    return when (mode) {
        KeyboardSwitcher.MODE_NONE, KeyboardSwitcher.MODE_TEXT ->
            KeyboardId(keyboardRowsResId,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_NORMAL,
                true, hasVoice)
        KeyboardSwitcher.MODE_SYMBOLS ->
            KeyboardId(R.xml.kbd_symbols,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_SYMBOLS,
                false, hasVoice)
        KeyboardSwitcher.MODE_PHONE ->
            KeyboardId(R.xml.kbd_phone, 0, false, hasVoice)
        KeyboardSwitcher.MODE_URL ->
            KeyboardId(keyboardRowsResId,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_URL_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_URL,
                true, hasVoice)
        KeyboardSwitcher.MODE_EMAIL ->
            KeyboardId(keyboardRowsResId,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_EMAIL,
                true, hasVoice)
        KeyboardSwitcher.MODE_IM ->
            KeyboardId(keyboardRowsResId,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_IM_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_IM,
                true, hasVoice)
        KeyboardSwitcher.MODE_WEB ->
            KeyboardId(keyboardRowsResId,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_WEB_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_WEB,
                true, hasVoice)
        else ->
            KeyboardId(keyboardRowsResId,
                if (hasSettingsKey) KeyboardSwitcher.KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY
                else KeyboardSwitcher.KEYBOARDMODE_NORMAL,
                true, hasVoice)
    }
}
