// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard.keyboard.latin
import dev.devkey.keyboard.R

import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat

/** Pre-loaded drawable icons for LatinKeyboard, extracted to reduce field count. */
internal class LatinKeyboardIcons(res: Resources) {
    val shiftLocked: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_shift_locked, null)!!
    val space: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_space, null)!!
    val spaceAutoCompletion: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_space_led, null)!!
    val spacePreview: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_feedback_space, null)!!
    val mic: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_mic, null)!!
    val micPreview: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_feedback_mic, null)!!.also { setDefaultBounds(it) }
    val settings: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_settings, null)!!
    val settingsPreview: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_feedback_settings, null)!!
    val arrowLeft: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_language_arrows_left, null)!!
    val arrowRight: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_language_arrows_right, null)!!
    val mic123: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_123_mic, null)!!
    val mic123Preview: Drawable = ResourcesCompat.getDrawable(res, R.drawable.sym_keyboard_feedback_123_mic, null)!!.also { setDefaultBounds(it) }
    val hint: Drawable = ResourcesCompat.getDrawable(res, R.drawable.hint_popup, null)!!
}

internal fun setDefaultBounds(drawable: Drawable) =
    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
