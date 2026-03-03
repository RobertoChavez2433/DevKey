package dev.devkey.keyboard.data.repository

import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import dev.devkey.keyboard.R
import dev.devkey.keyboard.ui.keyboard.LayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.Locale

class SettingsRepository(
    private val prefs: SharedPreferences
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val changeFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        migrateCompactModeToLayoutMode()
    }

    // ========================================================================
    // Cached keyboard settings (absorbed from GlobalKeyboardSettings)
    // All field reads/writes are main-thread only.
    // ========================================================================

    // Read by Keyboard
    @JvmField var popupKeyboardFlags = 0x1
    @JvmField var topRowScale = 1.0f

    // Read by LatinIME
    @JvmField var suggestedPunctuation = "!?,."
    @JvmField var keyboardModePortrait = 0
    @JvmField var keyboardModeLandscape = 2
    @JvmField var compactModeEnabled = true // always on
    @JvmField var ctrlAOverride = 0
    @JvmField var chordingCtrlKey = 0
    @JvmField var chordingAltKey = 0
    @JvmField var chordingMetaKey = 0
    @JvmField var keyClickVolume = 0.0f
    @JvmField var keyClickMethod = 0
    @JvmField var capsLock = true
    @JvmField var shiftLockModifiers = false

    // Read by CandidateView
    @JvmField var candidateScalePref = 1.0f

    // Legacy view settings (kept for compatibility)
    @JvmField var showTouchPos = false
    @JvmField var labelScalePref = 1.0f
    @JvmField var sendSlideKeys = 0

    // Updated by LatinIME, read by KeyboardSwitcher
    @JvmField var keyboardMode = 0
    @JvmField var useExtension = false
    @JvmField var keyboardHeightPercent = 40.0f

    // Legacy view settings
    @JvmField var hintMode = 0
    @JvmField var renderMode = 1
    @JvmField var longpressTimeout = 400

    // Cached editor info
    @JvmField var editorPackageName: String? = null
    @JvmField var editorFieldName: String? = null
    @JvmField var editorFieldId = 0
    @JvmField var editorInputType = 0

    // Updated by LanguageSwitcher
    @JvmField var inputLocale: Locale = Locale.getDefault()

    // ========================================================================
    // Pref registration system (absorbed from GlobalKeyboardSettings)
    // ========================================================================

    private val mBoolPrefs = HashMap<String, BooleanPref>()
    private val mStringPrefs = HashMap<String, StringPref>()
    private var mCurrentFlags = 0

    private fun interface BooleanPref {
        fun set(value: Boolean)
        fun getDefault(): Boolean = false
        fun getFlags(): Int = FLAG_PREF_NONE
    }

    private fun interface StringPref {
        fun set(value: String)
        fun getDefault(): String = ""
        fun getFlags(): Int = FLAG_PREF_NONE
    }

    fun initPrefs(prefs: SharedPreferences, resources: Resources) {
        addStringPref("pref_keyboard_mode_portrait", object : StringPref {
            override fun set(value: String) { keyboardModePortrait = value.toIntOrNull() ?: keyboardModePortrait }
            override fun getDefault() = resources.getString(R.string.default_keyboard_mode_portrait)
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS or FLAG_PREF_RESET_MODE_OVERRIDE
        })

        addStringPref("pref_keyboard_mode_landscape", object : StringPref {
            override fun set(value: String) { keyboardModeLandscape = value.toIntOrNull() ?: keyboardModeLandscape }
            override fun getDefault() = resources.getString(R.string.default_keyboard_mode_landscape)
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS or FLAG_PREF_RESET_MODE_OVERRIDE
        })

        addStringPref("pref_slide_keys_int", object : StringPref {
            override fun set(value: String) { sendSlideKeys = value.toIntOrNull() ?: sendSlideKeys }
            override fun getDefault() = "0"
            override fun getFlags() = FLAG_PREF_NONE
        })

        addBooleanPref("pref_touch_pos", object : BooleanPref {
            override fun set(value: Boolean) { showTouchPos = value }
            override fun getDefault() = false
            override fun getFlags() = FLAG_PREF_NONE
        })

        addStringPref("pref_popup_content", object : StringPref {
            override fun set(value: String) { popupKeyboardFlags = value.toIntOrNull() ?: popupKeyboardFlags }
            override fun getDefault() = resources.getString(R.string.default_popup_content)
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS
        })

        addStringPref("pref_suggested_punctuation", object : StringPref {
            override fun set(value: String) { suggestedPunctuation = value }
            override fun getDefault() = resources.getString(R.string.suggested_punctuations_default)
            override fun getFlags() = FLAG_PREF_NEW_PUNC_LIST
        })

        addStringPref("pref_label_scale_v2", object : StringPref {
            override fun set(value: String) { labelScalePref = value.toFloatOrNull() ?: labelScalePref }
            override fun getDefault() = "1.0"
            override fun getFlags() = FLAG_PREF_RECREATE_INPUT_VIEW
        })

        addStringPref("pref_candidate_scale", object : StringPref {
            override fun set(value: String) { candidateScalePref = value.toFloatOrNull() ?: candidateScalePref }
            override fun getDefault() = "1.0"
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS
        })

        addStringPref("pref_top_row_scale", object : StringPref {
            override fun set(value: String) { topRowScale = value.toFloatOrNull() ?: topRowScale }
            override fun getDefault() = "1.0"
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS
        })

        addStringPref("pref_ctrl_a_override", object : StringPref {
            override fun set(value: String) { ctrlAOverride = value.toIntOrNull() ?: ctrlAOverride }
            override fun getDefault() = resources.getString(R.string.default_ctrl_a_override)
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS
        })

        addStringPref("pref_chording_ctrl_key", object : StringPref {
            override fun set(value: String) { chordingCtrlKey = value.toIntOrNull() ?: chordingCtrlKey }
            override fun getDefault() = resources.getString(R.string.default_chording_ctrl_key)
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS
        })

        addStringPref("pref_chording_alt_key", object : StringPref {
            override fun set(value: String) { chordingAltKey = value.toIntOrNull() ?: chordingAltKey }
            override fun getDefault() = resources.getString(R.string.default_chording_alt_key)
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS
        })

        addStringPref("pref_chording_meta_key", object : StringPref {
            override fun set(value: String) { chordingMetaKey = value.toIntOrNull() ?: chordingMetaKey }
            override fun getDefault() = resources.getString(R.string.default_chording_meta_key)
            override fun getFlags() = FLAG_PREF_RESET_KEYBOARDS
        })

        addStringPref("pref_click_volume", object : StringPref {
            override fun set(value: String) { keyClickVolume = value.toFloatOrNull() ?: keyClickVolume }
            override fun getDefault() = resources.getString(R.string.default_click_volume)
            override fun getFlags() = FLAG_PREF_NONE
        })

        addStringPref("pref_click_method", object : StringPref {
            override fun set(value: String) { keyClickMethod = value.toIntOrNull() ?: keyClickMethod }
            override fun getDefault() = resources.getString(R.string.default_click_method)
            override fun getFlags() = FLAG_PREF_NONE
        })

        addBooleanPref("pref_caps_lock", object : BooleanPref {
            override fun set(value: Boolean) { capsLock = value }
            override fun getDefault() = resources.getBoolean(R.bool.default_caps_lock)
            override fun getFlags() = FLAG_PREF_NONE
        })

        addBooleanPref("pref_shift_lock_modifiers", object : BooleanPref {
            override fun set(value: Boolean) { shiftLockModifiers = value }
            override fun getDefault() = resources.getBoolean(R.bool.default_shift_lock_modifiers)
            override fun getFlags() = FLAG_PREF_NONE
        })

        // Set initial values
        for ((key, pref) in mBoolPrefs) {
            pref.set(prefs.getBoolean(key, pref.getDefault()))
        }
        for ((key, pref) in mStringPrefs) {
            pref.set(prefs.getString(key, pref.getDefault()) ?: pref.getDefault())
        }
    }

    fun handlePreferenceChanged(prefs: SharedPreferences, key: String?) {
        mCurrentFlags = FLAG_PREF_NONE
        val bPref = mBoolPrefs[key]
        if (bPref != null) {
            bPref.set(prefs.getBoolean(key, bPref.getDefault()))
            mCurrentFlags = mCurrentFlags or bPref.getFlags()
        }
        val sPref = mStringPrefs[key]
        if (sPref != null) {
            sPref.set(prefs.getString(key, sPref.getDefault()) ?: sPref.getDefault())
            mCurrentFlags = mCurrentFlags or sPref.getFlags()
        }
    }

    fun consumeFlag(flag: Int): Boolean {
        if (mCurrentFlags and flag != 0) {
            mCurrentFlags = mCurrentFlags and flag.inv()
            return true
        }
        return false
    }

    fun unhandledFlags(): Int = mCurrentFlags

    private fun addBooleanPref(key: String, setter: BooleanPref) {
        mBoolPrefs[key] = setter
    }

    private fun addStringPref(key: String, setter: StringPref) {
        mStringPrefs[key] = setter
    }

    // ========================================================================
    // Original SettingsRepository API
    // ========================================================================

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

    companion object {
        private const val TAG = "DevKey/Settings"

        // Preference change flags
        const val FLAG_PREF_NONE = 0
        const val FLAG_PREF_NEED_RELOAD = 0x1
        const val FLAG_PREF_NEW_PUNC_LIST = 0x2
        const val FLAG_PREF_RECREATE_INPUT_VIEW = 0x4
        const val FLAG_PREF_RESET_KEYBOARDS = 0x8
        const val FLAG_PREF_RESET_MODE_OVERRIDE = 0x10

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
        const val KEY_COMPACT_MODE = "devkey_compact_mode"  // deprecated, migrated to KEY_LAYOUT_MODE
        const val KEY_LAYOUT_MODE = "devkey_layout_mode"    // values: "full", "compact", "compact_dev"
        const val KEY_AUTOCORRECT_LEVEL = "devkey_autocorrect_level"
        const val KEY_MACRO_DISPLAY_MODE = "devkey_macro_display_mode"
        const val KEY_VOICE_MODEL = "devkey_voice_model"
        const val KEY_VOICE_AUTO_STOP_TIMEOUT = "devkey_voice_auto_stop_timeout"
        const val KEY_COMMAND_AUTO_DETECT = "devkey_command_auto_detect"

        // Popup keyboard content flags (used by Keyboard.kt)
        const val KEY_POPUP_CONTENT = "pref_popup_content"
    }
}
