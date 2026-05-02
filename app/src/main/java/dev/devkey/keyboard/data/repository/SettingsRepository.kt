package dev.devkey.keyboard.data.repository

import android.content.SharedPreferences
import android.util.Log
import dev.devkey.keyboard.ui.keyboard.LayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class SettingsRepository(
    private val prefs: SharedPreferences
) : PrefRegistry(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val changeFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        migrateCompactModeToLayoutMode()
    }

    // =========================================================================
    // Migration
    // =========================================================================

    /**
     * One-time migration: reads old KEY_COMPACT_MODE boolean and writes
     * KEY_LAYOUT_MODE string if it doesn't already exist.
     * true -> "compact_dev", false -> "full"
     */
    private fun migrateCompactModeToLayoutMode() {
        if (!prefs.contains(KEY_LAYOUT_MODE) && prefs.contains(KEY_COMPACT_MODE)) {
            val wasCompact = prefs.getBoolean(KEY_COMPACT_MODE, false)
            val newMode = if (wasCompact) "compact_dev" else "full"
            prefs.edit().putString(KEY_LAYOUT_MODE, newMode).apply()
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key?.let { changeFlow.tryEmit(it) }
    }

    // =========================================================================
    // Typed getters
    // =========================================================================

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default

    fun legacyPrefs(): SharedPreferences = prefs

    // =========================================================================
    // Typed setters
    // =========================================================================

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

    // =========================================================================
    // Flow observation
    // =========================================================================

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

    /**
     * Observe the current layout mode as a [Flow] of [LayoutMode].
     */
    fun getLayoutMode(): Flow<LayoutMode> =
        observeString(KEY_LAYOUT_MODE, "full").map { value ->
            when (value) {
                "compact" -> LayoutMode.COMPACT
                "compact_dev" -> LayoutMode.COMPACT_DEV
                else -> LayoutMode.FULL
            }
        }

    // =========================================================================
    // Companion: re-exports from SettingsKeys for backward compatibility.
    // All consumers that reference SettingsRepository.KEY_* continue to work
    // without any changes.
    // =========================================================================
    companion object {
        @Suppress("unused")
        private const val TAG = "DevKey/Settings"

        // Preference change flags
        const val FLAG_PREF_NONE                = SettingsKeys.FLAG_PREF_NONE
        const val FLAG_PREF_NEED_RELOAD         = SettingsKeys.FLAG_PREF_NEED_RELOAD
        const val FLAG_PREF_NEW_PUNC_LIST       = SettingsKeys.FLAG_PREF_NEW_PUNC_LIST
        const val FLAG_PREF_RECREATE_INPUT_VIEW = SettingsKeys.FLAG_PREF_RECREATE_INPUT_VIEW
        const val FLAG_PREF_RESET_KEYBOARDS     = SettingsKeys.FLAG_PREF_RESET_KEYBOARDS
        const val FLAG_PREF_RESET_MODE_OVERRIDE = SettingsKeys.FLAG_PREF_RESET_MODE_OVERRIDE

        // Default height percentages
        const val DEFAULT_HEIGHT_PORTRAIT  = SettingsKeys.DEFAULT_HEIGHT_PORTRAIT
        const val DEFAULT_HEIGHT_LANDSCAPE = SettingsKeys.DEFAULT_HEIGHT_LANDSCAPE

        // Legacy keys
        const val KEY_HEIGHT_PORTRAIT          = SettingsKeys.KEY_HEIGHT_PORTRAIT
        const val KEY_HEIGHT_LANDSCAPE         = SettingsKeys.KEY_HEIGHT_LANDSCAPE
        const val KEY_KEYBOARD_MODE_PORTRAIT   = SettingsKeys.KEY_KEYBOARD_MODE_PORTRAIT
        const val KEY_KEYBOARD_MODE_LANDSCAPE  = SettingsKeys.KEY_KEYBOARD_MODE_LANDSCAPE
        const val KEY_SUGGESTIONS_IN_LANDSCAPE = SettingsKeys.KEY_SUGGESTIONS_IN_LANDSCAPE
        const val KEY_HINT_MODE                = SettingsKeys.KEY_HINT_MODE
        const val KEY_LABEL_SCALE              = SettingsKeys.KEY_LABEL_SCALE
        const val KEY_CANDIDATE_SCALE          = SettingsKeys.KEY_CANDIDATE_SCALE
        const val KEY_TOP_ROW_SCALE            = SettingsKeys.KEY_TOP_ROW_SCALE
        const val KEY_KEYBOARD_LAYOUT         = SettingsKeys.KEY_KEYBOARD_LAYOUT
        const val KEY_RENDER_MODE              = SettingsKeys.KEY_RENDER_MODE
        const val KEY_AUTO_CAP                 = SettingsKeys.KEY_AUTO_CAP
        const val KEY_CAPS_LOCK                = SettingsKeys.KEY_CAPS_LOCK
        const val KEY_SHIFT_LOCK_MODIFIERS     = SettingsKeys.KEY_SHIFT_LOCK_MODIFIERS
        const val KEY_CTRL_A_OVERRIDE          = SettingsKeys.KEY_CTRL_A_OVERRIDE
        const val KEY_CHORDING_CTRL            = SettingsKeys.KEY_CHORDING_CTRL
        const val KEY_CHORDING_ALT             = SettingsKeys.KEY_CHORDING_ALT
        const val KEY_CHORDING_META            = SettingsKeys.KEY_CHORDING_META
        const val KEY_SLIDE_KEYS               = SettingsKeys.KEY_SLIDE_KEYS
        const val KEY_SWIPE_UP                 = SettingsKeys.KEY_SWIPE_UP
        const val KEY_SWIPE_DOWN               = SettingsKeys.KEY_SWIPE_DOWN
        const val KEY_SWIPE_LEFT               = SettingsKeys.KEY_SWIPE_LEFT
        const val KEY_SWIPE_RIGHT              = SettingsKeys.KEY_SWIPE_RIGHT
        const val KEY_VOL_UP                   = SettingsKeys.KEY_VOL_UP
        const val KEY_VOL_DOWN                 = SettingsKeys.KEY_VOL_DOWN
        const val KEY_VIBRATE_ON               = SettingsKeys.KEY_VIBRATE_ON
        const val KEY_VIBRATE_LEN              = SettingsKeys.KEY_VIBRATE_LEN
        const val KEY_SOUND_ON                 = SettingsKeys.KEY_SOUND_ON
        const val KEY_CLICK_METHOD             = SettingsKeys.KEY_CLICK_METHOD
        const val KEY_CLICK_VOLUME             = SettingsKeys.KEY_CLICK_VOLUME
        const val KEY_POPUP_ON                 = SettingsKeys.KEY_POPUP_ON
        const val KEY_QUICK_FIXES              = SettingsKeys.KEY_QUICK_FIXES
        const val KEY_SHOW_SUGGESTIONS         = SettingsKeys.KEY_SHOW_SUGGESTIONS
        const val KEY_AUTO_COMPLETE            = SettingsKeys.KEY_AUTO_COMPLETE
        const val KEY_SUGGESTED_PUNCTUATION    = SettingsKeys.KEY_SUGGESTED_PUNCTUATION
        const val KEY_LONG_PRESS_DURATION      = SettingsKeys.KEY_LONG_PRESS_DURATION
        const val KEY_VOICE_MODE               = SettingsKeys.KEY_VOICE_MODE
        const val KEY_SETTINGS_KEY             = SettingsKeys.KEY_SETTINGS_KEY

        // New DevKey keys
        const val KEY_COMPACT_MODE             = SettingsKeys.KEY_COMPACT_MODE
        const val KEY_LAYOUT_MODE              = SettingsKeys.KEY_LAYOUT_MODE
        const val KEY_AUTOCORRECT_LEVEL        = SettingsKeys.KEY_AUTOCORRECT_LEVEL
        const val KEY_MACRO_DISPLAY_MODE       = SettingsKeys.KEY_MACRO_DISPLAY_MODE
        const val KEY_VOICE_MODEL              = SettingsKeys.KEY_VOICE_MODEL
        const val KEY_VOICE_AUTO_STOP_TIMEOUT  = SettingsKeys.KEY_VOICE_AUTO_STOP_TIMEOUT
        const val KEY_COMMAND_AUTO_DETECT      = SettingsKeys.KEY_COMMAND_AUTO_DETECT
        const val KEY_SHOW_TOOLBAR             = SettingsKeys.KEY_SHOW_TOOLBAR
        const val KEY_SHOW_NUMBER_ROW          = SettingsKeys.KEY_SHOW_NUMBER_ROW

        // Popup keyboard content flags (used by Keyboard.kt)
        const val KEY_POPUP_CONTENT            = SettingsKeys.KEY_POPUP_CONTENT
    }
}
