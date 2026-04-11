// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard.keyboard.latin
import dev.devkey.keyboard.R

import android.content.res.Resources
import android.graphics.drawable.Drawable

/** Pre-loaded drawable icons for LatinKeyboard, extracted to reduce field count. */
@Suppress("DEPRECATION")
internal class LatinKeyboardIcons(res: Resources) {
    val shiftLocked: Drawable = res.getDrawable(R.drawable.sym_keyboard_shift_locked)
    val space: Drawable = res.getDrawable(R.drawable.sym_keyboard_space)
    val spaceAutoCompletion: Drawable = res.getDrawable(R.drawable.sym_keyboard_space_led)
    val spacePreview: Drawable = res.getDrawable(R.drawable.sym_keyboard_feedback_space)
    val mic: Drawable = res.getDrawable(R.drawable.sym_keyboard_mic)
    val micPreview: Drawable = res.getDrawable(R.drawable.sym_keyboard_feedback_mic).also { setDefaultBounds(it) }
    val settings: Drawable = res.getDrawable(R.drawable.sym_keyboard_settings)
    val settingsPreview: Drawable = res.getDrawable(R.drawable.sym_keyboard_feedback_settings)
    val arrowLeft: Drawable = res.getDrawable(R.drawable.sym_keyboard_language_arrows_left)
    val arrowRight: Drawable = res.getDrawable(R.drawable.sym_keyboard_language_arrows_right)
    val mic123: Drawable = res.getDrawable(R.drawable.sym_keyboard_123_mic)
    val mic123Preview: Drawable = res.getDrawable(R.drawable.sym_keyboard_feedback_123_mic).also { setDefaultBounds(it) }
    val hint: Drawable = res.getDrawable(R.drawable.hint_popup)
}

internal fun setDefaultBounds(drawable: Drawable) =
    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
