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

package dev.devkey.keyboard.language

import android.util.Log
import java.text.Collator
import java.util.Arrays
import java.util.Locale

private const val TAG = "DevKey/LanguageListAdapter"

internal class Loc(var label: String, val locale: Locale) : Comparable<Loc> {
    override fun toString(): String = label

    override fun compareTo(other: Loc): Int {
        return sCollator.compare(label, other.label)
    }

    companion object {
        val sCollator: Collator = Collator.getInstance()
    }
}

internal val KBD_5_ROW = arrayOf(
    "ar", "bg", "bg_ST", "cs", "cs_QY", "da", "de", "de_NE", "el",
    "en", "en_CX", "en_DV", "en_GB", "es", "es_LA", "fa", "fi", "fr",
    "fr_CA", "he", "hr", "hu", "hu_QY", "hy", "it", "iw", "lo", "lt",
    "nb", "pt_PT", "ro", "ru", "ru_PH", "si", "sk", "sk_QY", "sl",
    "sr", "sv", "ta", "th", "tr", "uk"
)

internal val KBD_4_ROW = arrayOf(
    "ar", "bg", "bg_ST", "cs", "cs_QY", "da", "de", "de_NE", "el",
    "en", "en_CX", "en_DV", "es", "es_LA", "es_US", "fa", "fr", "fr_CA",
    "he", "hr", "hu", "hu_QY", "iw", "nb", "ru", "ru_PH", "sk", "sk_QY",
    "sl", "sr", "sv", "tr", "uk"
)

private val KBD_LOCALIZATIONS = arrayOf(
    "ar", "bg", "bg_ST", "ca", "cs", "cs_QY", "da", "de", "de_NE",
    "el", "en", "en_CX", "en_DV", "en_GB", "es", "es_LA", "es_US",
    "fa", "fi", "fr", "fr_CA", "he", "hr", "hu", "hu_QY", "hy", "in",
    "it", "iw", "ja", "ka", "ko", "lo", "lt", "lv", "nb", "nl", "pl",
    "pt", "pt_PT", "rm", "ro", "ru", "ru_PH", "si", "sk", "sk_QY", "sl",
    "sr", "sv", "ta", "th", "tl", "tr", "uk", "vi", "zh_CN", "zh_TW"
)

private val BLACKLIST_LANGUAGES = arrayOf("ko", "ja", "zh")

internal fun arrayContains(array: Array<String>, value: String): Boolean {
    return array.any { it.equals(value, ignoreCase = true) }
}

internal fun getLocaleName(l: Locale): String {
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

internal fun getUniqueLocales(): ArrayList<Loc> {
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
