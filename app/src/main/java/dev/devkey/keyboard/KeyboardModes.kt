// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard

/** Input mode and keyboard mode constants shared by KeyboardSwitcher, KeyboardIdFactory, and LatinIME. */
object KeyboardModes {
    const val MODE_NONE = 0
    const val MODE_TEXT = 1
    const val MODE_SYMBOLS = 2
    const val MODE_PHONE = 3
    const val MODE_URL = 4
    const val MODE_EMAIL = 5
    const val MODE_IM = 6
    const val MODE_WEB = 7

    @JvmField val KEYBOARDMODE_NORMAL = R.id.mode_normal
    @JvmField val KEYBOARDMODE_URL = R.id.mode_url
    @JvmField val KEYBOARDMODE_EMAIL = R.id.mode_email
    @JvmField val KEYBOARDMODE_IM = R.id.mode_im
    @JvmField val KEYBOARDMODE_WEB = R.id.mode_webentry
    @JvmField val KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY = R.id.mode_normal_with_settings_key
    @JvmField val KEYBOARDMODE_URL_WITH_SETTINGS_KEY = R.id.mode_url_with_settings_key
    @JvmField val KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY = R.id.mode_email_with_settings_key
    @JvmField val KEYBOARDMODE_IM_WITH_SETTINGS_KEY = R.id.mode_im_with_settings_key
    @JvmField val KEYBOARDMODE_WEB_WITH_SETTINGS_KEY = R.id.mode_webentry_with_settings_key
    @JvmField val KEYBOARDMODE_SYMBOLS = R.id.mode_symbols
    @JvmField val KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY = R.id.mode_symbols_with_settings_key

    val ALPHABET_MODES = intArrayOf(
        KEYBOARDMODE_NORMAL,
        KEYBOARDMODE_URL, KEYBOARDMODE_EMAIL, KEYBOARDMODE_IM,
        KEYBOARDMODE_WEB, KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_URL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_IM_WITH_SETTINGS_KEY,
        KEYBOARDMODE_WEB_WITH_SETTINGS_KEY
    )

    const val DEFAULT_LAYOUT_ID = "0"
    const val PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout"
    const val PREF_SETTINGS_KEY = "settings_key"

    val SETTINGS_KEY_MODE_AUTO = R.string.settings_key_mode_auto
    val SETTINGS_KEY_MODE_ALWAYS_SHOW = R.string.settings_key_mode_always_show
    val DEFAULT_SETTINGS_KEY_MODE = SETTINGS_KEY_MODE_AUTO
}
