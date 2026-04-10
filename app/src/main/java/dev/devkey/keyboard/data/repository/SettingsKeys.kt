package dev.devkey.keyboard.data.repository

/**
 * All SharedPreferences key strings, flag bitmasks, and default constants for
 * the DevKey settings system.  The single source of string truth — do not
 * duplicate these in other files.
 *
 * Consumers that already reference `SettingsRepository.KEY_*` continue to work
 * because `SettingsRepository.Companion` re-exports every value from here.
 */
object SettingsKeys {

    // -------------------------------------------------------------------------
    // Preference change flags
    // -------------------------------------------------------------------------
    const val FLAG_PREF_NONE = 0
    const val FLAG_PREF_NEED_RELOAD = 0x1
    const val FLAG_PREF_NEW_PUNC_LIST = 0x2
    const val FLAG_PREF_RECREATE_INPUT_VIEW = 0x4
    const val FLAG_PREF_RESET_KEYBOARDS = 0x8
    const val FLAG_PREF_RESET_MODE_OVERRIDE = 0x10

    // -------------------------------------------------------------------------
    // Default constants
    // -------------------------------------------------------------------------
    const val DEFAULT_HEIGHT_PORTRAIT = 40   // percent
    const val DEFAULT_HEIGHT_LANDSCAPE = 55  // percent

    // -------------------------------------------------------------------------
    // Legacy keys (match existing SharedPreferences keys)
    // -------------------------------------------------------------------------
    const val KEY_HEIGHT_PORTRAIT = "settings_height_portrait"
    const val KEY_HEIGHT_LANDSCAPE = "settings_height_landscape"
    const val KEY_KEYBOARD_MODE_PORTRAIT = "pref_keyboard_mode_portrait"
    const val KEY_KEYBOARD_MODE_LANDSCAPE = "pref_keyboard_mode_landscape"
    const val KEY_SUGGESTIONS_IN_LANDSCAPE = "suggestions_in_landscape"
    const val KEY_HINT_MODE = "pref_hint_mode"
    const val KEY_LABEL_SCALE = "pref_label_scale_v2"
    const val KEY_CANDIDATE_SCALE = "pref_candidate_scale"
    const val KEY_TOP_ROW_SCALE = "pref_top_row_scale"
    const val KEY_KEYBOARD_LAYOUT = "pref_keyboard_layout"
    const val KEY_RENDER_MODE = "pref_render_mode"
    const val KEY_AUTO_CAP = "auto_cap"
    const val KEY_CAPS_LOCK = "pref_caps_lock"
    const val KEY_SHIFT_LOCK_MODIFIERS = "pref_shift_lock_modifiers"
    const val KEY_CTRL_A_OVERRIDE = "pref_ctrl_a_override"
    const val KEY_CHORDING_CTRL = "pref_chording_ctrl_key"
    const val KEY_CHORDING_ALT = "pref_chording_alt_key"
    const val KEY_CHORDING_META = "pref_chording_meta_key"
    const val KEY_SLIDE_KEYS = "pref_slide_keys_int"
    const val KEY_SWIPE_UP = "pref_swipe_up"
    const val KEY_SWIPE_DOWN = "pref_swipe_down"
    const val KEY_SWIPE_LEFT = "pref_swipe_left"
    const val KEY_SWIPE_RIGHT = "pref_swipe_right"
    const val KEY_VOL_UP = "pref_vol_up"
    const val KEY_VOL_DOWN = "pref_vol_down"
    const val KEY_VIBRATE_ON = "vibrate_on"
    const val KEY_VIBRATE_LEN = "vibrate_len"
    const val KEY_SOUND_ON = "sound_on"
    const val KEY_CLICK_METHOD = "pref_click_method"
    const val KEY_CLICK_VOLUME = "pref_click_volume"
    const val KEY_POPUP_ON = "popup_on"
    const val KEY_QUICK_FIXES = "quick_fixes"
    const val KEY_SHOW_SUGGESTIONS = "show_suggestions"
    const val KEY_AUTO_COMPLETE = "auto_complete"
    const val KEY_SUGGESTED_PUNCTUATION = "pref_suggested_punctuation"
    const val KEY_LONG_PRESS_DURATION = "pref_long_press_duration"
    const val KEY_VOICE_MODE = "voice_mode"
    const val KEY_SETTINGS_KEY = "settings_key"

    // -------------------------------------------------------------------------
    // New DevKey keys
    // -------------------------------------------------------------------------
    /** Deprecated: migrated to KEY_LAYOUT_MODE */
    const val KEY_COMPACT_MODE = "devkey_compact_mode"
    /** Values: "full", "compact", "compact_dev" */
    const val KEY_LAYOUT_MODE = "devkey_layout_mode"
    const val KEY_AUTOCORRECT_LEVEL = "devkey_autocorrect_level"
    const val KEY_MACRO_DISPLAY_MODE = "devkey_macro_display_mode"
    const val KEY_VOICE_MODEL = "devkey_voice_model"
    const val KEY_VOICE_AUTO_STOP_TIMEOUT = "devkey_voice_auto_stop_timeout"
    const val KEY_COMMAND_AUTO_DETECT = "devkey_command_auto_detect"
    // WHY: SwiftKey parity — default false hides the toolbar row above the keyboard
    //      (clipboard/voice/123/⚡/⋯). Users who want DevKey power features visible can
    //      flip this on in Settings. When hidden, those modes are still reachable via
    //      the standard Android IME switcher and long-press menus.
    const val KEY_SHOW_TOOLBAR = "devkey_show_toolbar"
    // WHY: SwiftKey parity — default true shows the 1-0 number row at the top of
    //      COMPACT / COMPACT_DEV layouts. Users who want the minimal 4-row compact
    //      can flip this off in Settings. FULL layout always has its own number row
    //      regardless of this flag.
    const val KEY_SHOW_NUMBER_ROW = "devkey_show_number_row"

    // -------------------------------------------------------------------------
    // Popup keyboard content flags (used by Keyboard.kt)
    // -------------------------------------------------------------------------
    const val KEY_POPUP_CONTENT = "pref_popup_content"
}
