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

import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity
import android.preference.PreferenceGroup
import android.util.Log
import androidx.preference.PreferenceManager
import java.text.Collator
import java.util.Arrays
import java.util.Locale

@Suppress("DEPRECATION")
class InputLanguageSelection : PreferenceActivity() {

    private var mAvailableLanguages = ArrayList<Loc>()

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.language_prefs)
        // Get the settings preferences
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedLanguagePref = sp.getString(LatinIME.PREF_SELECTED_LANGUAGES, "") ?: ""
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

        val dictionaries = LatinIME.getDictionary(res)
        var bd = BinaryDictionary(this, dictionaries, Suggest.DIC_MAIN)

        // Is the dictionary larger than a placeholder? Arbitrarily chose a lower limit of
        // 4000-5000 words, whereas the LARGE_DICTIONARY is about 20000+ words.
        if (bd.getSize() > Suggest.LARGE_DICTIONARY_THRESHOLD / 4) {
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
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sp.edit()
        editor.putString(LatinIME.PREF_SELECTED_LANGUAGES, result)
        editor.apply()
    }

    private fun getUniqueLocales(): ArrayList<Loc> {
        val localeSet = HashSet<String>()
        val langSet = HashSet<String>()

        // Add entries for additional languages supported by the keyboard.
        for (kl in KBD_LOCALIZATIONS) {
            var locale = kl
            if (locale.length == 2 && langSet.contains(locale)) continue
            // replace zz_rYY with zz_YY
            if (locale.length == 6) locale = locale.substring(0, 2) + "_" + locale.substring(4, 6)
            localeSet.add(locale)
        }
        Log.i(TAG, "localeSet=${asString(localeSet)}")
        Log.i(TAG, "langSet=${asString(langSet)}")

        // Now build the locale list for display
        val locales = localeSet.toTypedArray()
        Arrays.sort(locales)

        val uniqueLocales = ArrayList<Loc>()

        val origSize = locales.size
        val preprocess = arrayOfNulls<Loc>(origSize)
        var finalSize = 0
        for (i in 0 until origSize) {
            val s = locales[i]
            val len = s.length
            if (len == 2 || len == 5 || len == 6) {
                val language = s.substring(0, 2)
                val l: Locale = when (len) {
                    5 -> {
                        // zz_YY
                        val country = s.substring(3, 5)
                        Locale(language, country)
                    }
                    6 -> {
                        // zz_rYY
                        Locale(language, s.substring(4, 6))
                    }
                    else -> Locale(language)
                }

                // Exclude languages that are not relevant to LatinIME
                if (arrayContains(BLACKLIST_LANGUAGES, language)) continue

                if (finalSize == 0) {
                    preprocess[finalSize++] =
                        Loc(LanguageSwitcher.toTitleCase(l.getDisplayName(l)), l)
                } else {
                    // check previous entry:
                    //  same lang and a country -> upgrade to full name and
                    //    insert ours with full name
                    //  diff lang -> insert ours with lang-only name
                    if (preprocess[finalSize - 1]!!.locale.language == language) {
                        preprocess[finalSize - 1]!!.label = getLocaleName(preprocess[finalSize - 1]!!.locale)
                        preprocess[finalSize++] = Loc(getLocaleName(l), l)
                    } else {
                        if (s != "zz_ZZ") {
                            val displayName = getLocaleName(l)
                            preprocess[finalSize++] = Loc(displayName, l)
                        }
                    }
                }
            }
        }
        for (i in 0 until finalSize) {
            uniqueLocales.add(preprocess[i]!!)
        }
        return uniqueLocales
    }

    private fun arrayContains(array: Array<String>, value: String): Boolean {
        return array.any { it.equals(value, ignoreCase = true) }
    }

    companion object {
        private const val TAG = "DevKey/InputLanguageSelection"

        private val BLACKLIST_LANGUAGES = arrayOf("ko", "ja", "zh")

        // Languages for which auto-caps should be disabled
        val NOCAPS_LANGUAGES: Set<String> = hashSetOf("ar", "iw", "th")

        // Languages which should not use dead key logic. The modifier is entered after the base character.
        val NODEADKEY_LANGUAGES: Set<String> = hashSetOf("ar", "iw", "th")

        // Languages which should not auto-add space after completions
        val NOAUTOSPACE_LANGUAGES: Set<String> = hashSetOf("th")

        // Run the GetLanguages.sh script to update the following lists based on
        // the available keyboard resources and dictionaries.
        private val KBD_LOCALIZATIONS = arrayOf(
            "ar", "bg", "bg_ST", "ca", "cs", "cs_QY", "da", "de", "de_NE",
            "el", "en", "en_CX", "en_DV", "en_GB", "es", "es_LA", "es_US",
            "fa", "fi", "fr", "fr_CA", "he", "hr", "hu", "hu_QY", "hy", "in",
            "it", "iw", "ja", "ka", "ko", "lo", "lt", "lv", "nb", "nl", "pl",
            "pt", "pt_PT", "rm", "ro", "ru", "ru_PH", "si", "sk", "sk_QY", "sl",
            "sr", "sv", "ta", "th", "tl", "tr", "uk", "vi", "zh_CN", "zh_TW"
        )

        private val KBD_5_ROW = arrayOf(
            "ar", "bg", "bg_ST", "cs", "cs_QY", "da", "de", "de_NE", "el",
            "en", "en_CX", "en_DV", "en_GB", "es", "es_LA", "fa", "fi", "fr",
            "fr_CA", "he", "hr", "hu", "hu_QY", "hy", "it", "iw", "lo", "lt",
            "nb", "pt_PT", "ro", "ru", "ru_PH", "si", "sk", "sk_QY", "sl",
            "sr", "sv", "ta", "th", "tr", "uk"
        )

        private val KBD_4_ROW = arrayOf(
            "ar", "bg", "bg_ST", "cs", "cs_QY", "da", "de", "de_NE", "el",
            "en", "en_CX", "en_DV", "es", "es_LA", "es_US", "fa", "fr", "fr_CA",
            "he", "hr", "hu", "hu_QY", "iw", "nb", "ru", "ru_PH", "sk", "sk_QY",
            "sl", "sr", "sv", "tr", "uk"
        )

        private fun getLocaleName(l: Locale): String {
            val lang = l.language
            val country = l.country
            return when {
                lang == "en" && country == "DV" -> "English (Dvorak)"
                lang == "en" && country == "EX" -> "English (4x11)"
                lang == "en" && country == "CX" -> "English (Carpalx)"
                lang == "es" && country == "LA" -> "Espa\u00f1ol (Latinoam\u00e9rica)"
                lang == "cs" && country == "QY" -> "\u010ce\u0161tina (QWERTY)"
                lang == "de" && country == "NE" -> "Deutsch (Neo2)"
                lang == "hu" && country == "QY" -> "Magyar (QWERTY)"
                lang == "sk" && country == "QY" -> "Sloven\u010dina (QWERTY)"
                lang == "ru" && country == "PH" -> "\u0420\u0443\u0441\u0441\u043a\u0438\u0439 (Phonetic)"
                lang == "bg" -> {
                    if (country == "ST") {
                        "\u0431\u044a\u043b\u0433\u0430\u0440\u0441\u043a\u0438 \u0435\u0437\u0438\u043a (Standard)"
                    } else {
                        "\u0431\u044a\u043b\u0433\u0430\u0440\u0441\u043a\u0438 \u0435\u0437\u0438\u043a (Phonetic)"
                    }
                }
                else -> LanguageSwitcher.toTitleCase(l.getDisplayName(l))
            }
        }

        private fun asString(set: Set<String>): String {
            val out = StringBuilder()
            out.append("set(")
            val parts = set.toTypedArray()
            Arrays.sort(parts)
            for (i in parts.indices) {
                if (i > 0) out.append(", ")
                out.append(parts[i])
            }
            out.append(")")
            return out.toString()
        }
    }

    private class Loc(var label: String, val locale: Locale) : Comparable<Loc> {
        override fun toString(): String = label

        override fun compareTo(other: Loc): Int {
            return sCollator.compare(label, other.label)
        }

        companion object {
            val sCollator: Collator = Collator.getInstance()
        }
    }
}
