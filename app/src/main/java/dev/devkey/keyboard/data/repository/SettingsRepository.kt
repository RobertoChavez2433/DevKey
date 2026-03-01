package dev.devkey.keyboard.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class SettingsRepository(
    private val prefs: SharedPreferences
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val changeFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key?.let { changeFlow.tryEmit(it) }
    }

    // Typed getters
    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default

    // Typed setters
    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun setFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    // Flow observation
    fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        changeFlow
            .filter { it == key }
            .map { getBoolean(key, default) }
            .onStart { emit(getBoolean(key, default)) }

    fun observeString(key: String, default: String): Flow<String> =
        changeFlow
            .filter { it == key }
            .map { getString(key, default) }
            .onStart { emit(getString(key, default)) }

    fun observeInt(key: String, default: Int): Flow<Int> =
        changeFlow
            .filter { it == key }
            .map { getInt(key, default) }
            .onStart { emit(getInt(key, default)) }

    fun observeFloat(key: String, default: Float): Flow<Float> =
        changeFlow
            .filter { it == key }
            .map { getFloat(key, default) }
            .onStart { emit(getFloat(key, default)) }

    companion object {
        // Default height percentages (single source of truth)
        const val DEFAULT_HEIGHT_PORTRAIT = 40   // percent
        const val DEFAULT_HEIGHT_LANDSCAPE = 55  // percent

        // Legacy keys (match existing SharedPreferences keys)
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

        // New DevKey keys
        const val KEY_COMPACT_MODE = "devkey_compact_mode"
        const val KEY_AUTOCORRECT_LEVEL = "devkey_autocorrect_level"
        const val KEY_MACRO_DISPLAY_MODE = "devkey_macro_display_mode"
        const val KEY_VOICE_MODEL = "devkey_voice_model"
        const val KEY_VOICE_AUTO_STOP_TIMEOUT = "devkey_voice_auto_stop_timeout"
        const val KEY_COMMAND_AUTO_DETECT = "devkey_command_auto_detect"
    }
}
