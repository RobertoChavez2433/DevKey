/*
 * Copyright (C) 2008-2009 Google Inc.
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
import dev.devkey.keyboard.language.KBD_4_ROW
import dev.devkey.keyboard.language.KBD_5_ROW
import dev.devkey.keyboard.language.LanguageSwitcher
import dev.devkey.keyboard.language.Loc
import dev.devkey.keyboard.language.arrayContains
import dev.devkey.keyboard.language.getUniqueLocales
import dev.devkey.keyboard.dictionary.base.BinaryDictionary
import dev.devkey.keyboard.dictionary.base.DictionaryType
import dev.devkey.keyboard.dictionary.loader.PluginManager

import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity
import android.util.Log
import dev.devkey.keyboard.data.repository.SettingsRepository
import java.util.Locale

@Suppress("DEPRECATION")
class InputLanguageSelection : PreferenceActivity() {

    private var mAvailableLanguages = ArrayList<Loc>()

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.language_prefs)
        // Get the settings preferences
        val settings = SettingsRepository.from(this)
        val selectedLanguagePref = settings.getString(LatinIME.PREF_SELECTED_LANGUAGES, "")
        Log.i(TAG, "selected languages: $selectedLanguagePref")
        val languageList = selectedLanguagePref.split(",")

        mAvailableLanguages = getUniqueLocales()

        // Compatibility hack for v1.22 and older - if a selected language 5-code isn't
        // found in the current list of available languages, try adding the 2-letter
        // language code. For example, "en_US" is no longer listed, so use "en" instead.
        val availableLanguages = HashSet<String>()
        for (i in mAvailableLanguages.indices) {
            val locale = mAvailableLanguages[i].locale
            availableLanguages.add(get5Code(locale))
        }
        val languageSelections = HashSet<String>()
        for (spec in languageList) {
            if (availableLanguages.contains(spec)) {
                languageSelections.add(spec)
            } else if (spec.length > 2) {
                val lang = spec.substring(0, 2)
                if (availableLanguages.contains(lang)) languageSelections.add(lang)
            }
        }

        val parent = preferenceScreen
        for (i in mAvailableLanguages.indices) {
            val pref = CheckBoxPreference(this)
            val locale = mAvailableLanguages[i].locale
            pref.title = mAvailableLanguages[i].label + " [" + locale.toString() + "]"
            val fivecode = get5Code(locale)
            val language = locale.language
            val checked = languageSelections.contains(fivecode)
            pref.isChecked = checked
            val has4Row = arrayContains(KBD_4_ROW, fivecode) || arrayContains(KBD_4_ROW, language)
            val has5Row = arrayContains(KBD_5_ROW, fivecode) || arrayContains(KBD_5_ROW, language)
            val summaries = ArrayList<String>(3)
            if (has5Row) summaries.add("5-row")
            if (has4Row) summaries.add("4-row")
            if (hasDictionary(locale)) {
                summaries.add(resources.getString(R.string.has_dictionary))
            }
            if (summaries.isNotEmpty()) {
                val summary = StringBuilder()
                for (j in summaries.indices) {
                    if (j > 0) summary.append(", ")
                    summary.append(summaries[j])
                }
                pref.summary = summary.toString()
            }
            parent.addPreference(pref)
        }
    }

    private fun hasDictionary(locale: Locale): Boolean {
        val res = resources
        val conf = res.configuration
        @Suppress("DEPRECATION")
        val saveLocale = conf.locale
        var haveDictionary = false
        @Suppress("DEPRECATION")
        conf.locale = locale
        @Suppress("DEPRECATION")
        res.updateConfiguration(conf, res.displayMetrics)

        val dictionaries = dev.devkey.keyboard.core.prefs.ImePrefsUtil.getDictionary(res)
        var bd = BinaryDictionary(this, dictionaries, DictionaryType.MAIN)

        // Is the dictionary larger than a placeholder? Arbitrarily chose a lower limit of
        // 4000-5000 words, whereas the LARGE_DICTIONARY is about 20000+ words.
        if (bd.getSize() > DictionaryType.LARGE_DICTIONARY_THRESHOLD / 4) {
            haveDictionary = true
        } else {
            val plug = PluginManager.getDictionary(applicationContext, locale.language)
            if (plug != null) {
                bd.close()
                bd = plug
                haveDictionary = true
            }
        }

        bd.close()
        @Suppress("DEPRECATION")
        conf.locale = saveLocale
        @Suppress("DEPRECATION")
        res.updateConfiguration(conf, res.displayMetrics)
        return haveDictionary
    }

    private fun get5Code(locale: Locale): String {
        val country = locale.country
        return locale.language + if (country.isNullOrEmpty()) "" else "_$country"
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        // Save the selected languages
        val parent = preferenceScreen
        val count = parent.preferenceCount
        val checkedCodes = (0 until count)
            .map { i -> parent.getPreference(i) as CheckBoxPreference to mAvailableLanguages[i].locale }
            .filter { (pref, _) -> pref.isChecked }
            .map { (_, locale) -> get5Code(locale) }
        val result: String? = if (checkedCodes.isEmpty()) null else checkedCodes.joinToString(",")
        SettingsRepository.from(this).setString(LatinIME.PREF_SELECTED_LANGUAGES, result ?: "")
    }

    companion object {
        private const val TAG = "DevKey/InputLanguageSelection"

        // Languages for which auto-caps should be disabled
        val NOCAPS_LANGUAGES: Set<String> = hashSetOf("ar", "iw", "th")

        // Languages which should not use dead key logic. The modifier is entered after the base character.
        val NODEADKEY_LANGUAGES: Set<String> = hashSetOf("ar", "iw", "th")

        // Languages which should not auto-add space after completions
        val NOAUTOSPACE_LANGUAGES: Set<String> = hashSetOf("th")
    }
}
