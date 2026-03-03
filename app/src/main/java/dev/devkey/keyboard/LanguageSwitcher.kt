/*
 * Copyright (C) 2010 Google Inc.
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

package dev.devkey.keyboard

import android.content.SharedPreferences
import android.text.TextUtils
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * Keeps track of list of selected input languages and the current
 * input language that the user has selected.
 */
class LanguageSwitcher(private val mIme: LatinIME) {

    private var mLocales: Array<Locale> = emptyArray()
    private var mSelectedLanguageArray: Array<String> = emptyArray()
    private var mSelectedLanguages: String = ""
    private var mCurrentIndex: Int = 0
    private var mDefaultInputLanguage: String = ""
    private var mDefaultInputLocale: Locale? = null
    private var mSystemLocale: Locale? = null

    fun getLocales(): Array<Locale> = mLocales

    fun getLocaleCount(): Int = mLocales.size

    /**
     * Loads the currently selected input languages from shared preferences.
     * @return whether there was any change
     */
    fun loadLocales(sp: SharedPreferences): Boolean {
        val selectedLanguages = sp.getString(LatinIME.PREF_SELECTED_LANGUAGES, null)
        val currentLanguage = sp.getString(LatinIME.PREF_INPUT_LANGUAGE, null)
        if (selectedLanguages.isNullOrEmpty()) {
            loadDefaults()
            if (mLocales.isEmpty()) {
                return false
            }
            mLocales = emptyArray()
            return true
        }
        if (selectedLanguages == mSelectedLanguages) {
            return false
        }
        mSelectedLanguageArray = selectedLanguages.split(",").toTypedArray()
        mSelectedLanguages = selectedLanguages // Cache it for comparison later
        constructLocales()
        mCurrentIndex = 0
        if (currentLanguage != null) {
            // Find the index
            mCurrentIndex = 0
            for (i in mLocales.indices) {
                if (mSelectedLanguageArray[i] == currentLanguage) {
                    mCurrentIndex = i
                    break
                }
            }
            // If we didn't find the index, use the first one
        }
        return true
    }

    private fun loadDefaults() {
        mDefaultInputLocale = mIme.resources.configuration.locale
        val country = mDefaultInputLocale!!.country
        mDefaultInputLanguage = mDefaultInputLocale!!.language +
                if (TextUtils.isEmpty(country)) "" else "_$country"
    }

    private fun constructLocales() {
        mLocales = Array(mSelectedLanguageArray.size) { i ->
            val lang = mSelectedLanguageArray[i]
            if (lang.isBlank()) return@Array Locale.getDefault()
            Locale(
                lang.take(2),
                if (lang.length > 4) lang.substring(3, 5) else ""
            )
        }
    }

    /**
     * Returns the currently selected input language code, or the display language code if
     * no specific locale was selected for input.
     */
    fun getInputLanguage(): String {
        if (getLocaleCount() == 0) return mDefaultInputLanguage
        return mSelectedLanguageArray[mCurrentIndex]
    }

    private fun getLanguageCode(): String {
        val lang = getInputLanguage()
        return if (lang.length > 2) lang.substring(0, 2) else lang
    }

    fun allowAutoCap(): Boolean {
        return !InputLanguageSelection.NOCAPS_LANGUAGES.contains(getLanguageCode())
    }

    fun allowDeadKeys(): Boolean {
        return !InputLanguageSelection.NODEADKEY_LANGUAGES.contains(getLanguageCode())
    }

    fun allowAutoSpace(): Boolean {
        return !InputLanguageSelection.NOAUTOSPACE_LANGUAGES.contains(getLanguageCode())
    }

    /**
     * Returns the list of enabled language codes.
     */
    fun getEnabledLanguages(): Array<String> = mSelectedLanguageArray

    /**
     * Returns the currently selected input locale, or the display locale if no specific
     * locale was selected for input.
     */
    // SIDE EFFECT: updates sKeyboardSettings.inputLocale
    fun getInputLocale(): Locale? {
        val locale = if (getLocaleCount() == 0) {
            mDefaultInputLocale
        } else {
            mLocales[mCurrentIndex]
        }
        LatinIME.sKeyboardSettings.inputLocale = locale ?: Locale.getDefault()
        return locale
    }

    /**
     * Returns the next input locale in the list. Wraps around to the beginning of the
     * list if we're at the end of the list.
     */
    fun getNextInputLocale(): Locale? {
        if (getLocaleCount() == 0) return mDefaultInputLocale
        return mLocales[(mCurrentIndex + 1) % mLocales.size]
    }

    /**
     * Sets the system locale (display UI) used for comparing with the input language.
     */
    fun setSystemLocale(locale: Locale) {
        mSystemLocale = locale
    }

    /**
     * Returns the system locale.
     */
    fun getSystemLocale(): Locale? = mSystemLocale

    /**
     * Returns the previous input locale in the list. Wraps around to the end of the
     * list if we're at the beginning of the list.
     */
    fun getPrevInputLocale(): Locale? {
        if (getLocaleCount() == 0) return mDefaultInputLocale
        return mLocales[(mCurrentIndex - 1 + mLocales.size) % mLocales.size]
    }

    fun reset() {
        mCurrentIndex = 0
        mSelectedLanguages = ""
        loadLocales(PreferenceManager.getDefaultSharedPreferences(mIme))
    }

    fun next() {
        mCurrentIndex++
        if (mCurrentIndex >= mLocales.size) mCurrentIndex = 0 // Wrap around
    }

    fun prev() {
        mCurrentIndex--
        if (mCurrentIndex < 0) mCurrentIndex = mLocales.size - 1 // Wrap around
    }

    fun persist() {
        val sp = PreferenceManager.getDefaultSharedPreferences(mIme)
        val editor = sp.edit()
        editor.putString(LatinIME.PREF_INPUT_LANGUAGE, getInputLanguage())
        editor.apply()
    }

    companion object {
        private const val TAG = "DevKey/LanguageSwitcher"

        @JvmStatic
        fun toTitleCase(s: String): String {
            if (s.isEmpty()) return s
            return s.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
