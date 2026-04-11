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

package dev.devkey.keyboard.keyboard.latin
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.R
import dev.devkey.keyboard.keyboard.model.Key

import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import dev.devkey.keyboard.ui.keyboard.KeyCodes

/**
 * Manages the F1 key, which changes its appearance and behavior based on
 * keyboard mode (URL, email, mic, settings, comma).
 */
class F1KeyManager(
    private val mRes: Resources,
    private val mF1Key: Key,
    private val mMicIcon: Drawable?,
    private val mMicPreviewIcon: Drawable?,
    private val mSettingsIcon: Drawable?,
    private val mSettingsPreviewIcon: Drawable?,
    private val mHintIcon: Drawable,
    private val drawSynthesizedSettingsHintImage: (width: Int, height: Int, main: Drawable?, hint: Drawable?) -> android.graphics.Bitmap?
) {

    fun isF1Key(key: Key): Boolean = key === mF1Key

    fun updateF1Key(
        isAlphaKeyboard: Boolean,
        isAlphaFullKeyboard: Boolean,
        isFnFullKeyboard: Boolean,
        mode: Int,
        voiceEnabled: Boolean,
        hasVoiceButton: Boolean
    ) {
        if (isAlphaKeyboard) {
            when {
                mode == KeyboardSwitcher.MODE_URL -> setNonMicF1Key("/", R.xml.popup_slash)
                mode == KeyboardSwitcher.MODE_EMAIL -> setNonMicF1Key("@", R.xml.popup_at)
                voiceEnabled && hasVoiceButton -> setMicF1Key()
                else -> setNonMicF1Key(",", R.xml.popup_comma)
            }
        } else if (isAlphaFullKeyboard) {
            if (voiceEnabled && hasVoiceButton) setMicF1Key() else setSettingsF1Key()
        } else if (isFnFullKeyboard) {
            setMicF1Key()
        } else { // Symbols keyboard
            if (voiceEnabled && hasVoiceButton) setMicF1Key()
            else setNonMicF1Key(",", R.xml.popup_comma)
        }
    }

    private fun setMicF1Key() {
        // HACK: draw mMicIcon and mHintIcon at the same time
        val micWithSettingsHintDrawable = BitmapDrawable(
            mRes,
            drawSynthesizedSettingsHintImage(mF1Key.width, mF1Key.height, mMicIcon, mHintIcon)
        )

        if (mF1Key.popupResId == 0) {
            mF1Key.popupResId = R.xml.popup_mic
        } else {
            mF1Key.modifier = true
            if (mF1Key.label != null) {
                mF1Key.popupCharacters = if (mF1Key.popupCharacters == null)
                    mF1Key.label.toString() + mF1Key.shiftLabel.toString()
                else
                    mF1Key.label.toString() + mF1Key.shiftLabel.toString() + mF1Key.popupCharacters.toString()
            }
        }
        mF1Key.label = null
        mF1Key.shiftLabel = null
        mF1Key.codes = intArrayOf(KeyCodes.KEYCODE_VOICE)
        mF1Key.icon = micWithSettingsHintDrawable
        mF1Key.iconPreview = mMicPreviewIcon
    }

    private fun setSettingsF1Key() {
        if (mF1Key.shiftLabel != null && mF1Key.label != null) {
            mF1Key.codes = intArrayOf(mF1Key.label!![0].code)
            return // leave key otherwise unmodified
        }
        val settingsHintDrawable = BitmapDrawable(
            mRes,
            drawSynthesizedSettingsHintImage(mF1Key.width, mF1Key.height, mSettingsIcon, mHintIcon)
        )
        mF1Key.label = null
        mF1Key.icon = settingsHintDrawable
        mF1Key.codes = intArrayOf(KeyCodes.KEYCODE_OPTIONS)
        mF1Key.popupResId = R.xml.popup_mic
        mF1Key.iconPreview = mSettingsPreviewIcon
    }

    private fun setNonMicF1Key(label: String, popupResId: Int) {
        if (mF1Key.shiftLabel != null) {
            mF1Key.codes = intArrayOf(mF1Key.label!![0].code)
            return // leave key unmodified
        }
        mF1Key.label = label
        mF1Key.codes = intArrayOf(label[0].code)
        mF1Key.popupResId = popupResId
        mF1Key.icon = mHintIcon
        mF1Key.iconPreview = null
    }
}
