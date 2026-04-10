package dev.devkey.keyboard.data.repository

import android.content.SharedPreferences
import android.content.res.Resources
import dev.devkey.keyboard.R
import dev.devkey.keyboard.data.repository.SettingsKeys.FLAG_PREF_NEED_RELOAD
import dev.devkey.keyboard.data.repository.SettingsKeys.FLAG_PREF_NEW_PUNC_LIST
import dev.devkey.keyboard.data.repository.SettingsKeys.FLAG_PREF_NONE
import dev.devkey.keyboard.data.repository.SettingsKeys.FLAG_PREF_RECREATE_INPUT_VIEW
import dev.devkey.keyboard.data.repository.SettingsKeys.FLAG_PREF_RESET_KEYBOARDS
import dev.devkey.keyboard.data.repository.SettingsKeys.FLAG_PREF_RESET_MODE_OVERRIDE

/**
 * Preference observation and registration infrastructure.
 *
 * Maintains a registry of boolean and string preferences, applies initial
 * values on [initPrefs], and propagates runtime changes via
 * [handlePreferenceChanged].  The [consumeFlag] / [unhandledFlags] API lets
 * callers drain change-flag bits one at a time.
 *
 * This class is internal to the settings layer; extend it via
 * [SettingsRepository] only.
 */
abstract class PrefRegistry : KeyboardSettingsState() {

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

        // Apply initial values
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
}
