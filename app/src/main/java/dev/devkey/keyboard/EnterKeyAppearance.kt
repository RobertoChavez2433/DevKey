// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.inputmethod.EditorInfo

internal object EnterKeyAppearance {
    fun apply(key: Key, res: Resources, options: Int) {
        key.popupCharacters = null; key.popupResId = 0; key.text = null
        when (options and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            EditorInfo.IME_ACTION_GO -> { key.iconPreview = null; key.icon = null; key.label = res.getText(R.string.label_go_key) }
            EditorInfo.IME_ACTION_NEXT -> { key.iconPreview = null; key.icon = null; key.label = res.getText(R.string.label_next_key) }
            EditorInfo.IME_ACTION_DONE -> { key.iconPreview = null; key.icon = null; key.label = res.getText(R.string.label_done_key) }
            EditorInfo.IME_ACTION_SEARCH -> {
                @Suppress("DEPRECATION") key.iconPreview = res.getDrawable(R.drawable.sym_keyboard_feedback_search)
                @Suppress("DEPRECATION") key.icon = res.getDrawable(R.drawable.sym_keyboard_search)
                key.label = null
            }
            EditorInfo.IME_ACTION_SEND -> { key.iconPreview = null; key.icon = null; key.label = res.getText(R.string.label_send_key) }
            else -> {
                @Suppress("DEPRECATION") key.iconPreview = res.getDrawable(R.drawable.sym_keyboard_feedback_return)
                @Suppress("DEPRECATION") key.icon = res.getDrawable(R.drawable.sym_keyboard_return)
                key.label = null
            }
        }
        if (key.iconPreview != null) setDefaultBounds(key.iconPreview!!)
    }

    private fun setDefaultBounds(drawable: Drawable) =
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
}
