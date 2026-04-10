package dev.devkey.keyboard

import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.regex.Pattern

object ImePrefsUtil {
    private const val TAG = "DevKey/ImePrefsUtil"
    private val NUMBER_RE = Pattern.compile("(\\d+).*")

    fun getDictionary(res: Resources): IntArray {
        val packageName = LatinIME::class.java.`package`?.name ?: ""
        val xrp = res.getXml(R.xml.dictionary)
        val dictionaries = mutableListOf<Int>()
        try {
            var current = xrp.eventType
            while (current != android.content.res.XmlResourceParser.END_DOCUMENT) {
                if (current == android.content.res.XmlResourceParser.START_TAG) {
                    if (xrp.name == "part") {
                        val dictFileName = xrp.getAttributeValue(null, "name")
                        dictionaries.add(res.getIdentifier(dictFileName, "raw", packageName))
                    }
                }
                xrp.next()
                current = xrp.eventType
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Dictionary XML parsing failure")
        } catch (e: IOException) {
            Log.e(TAG, "Dictionary XML IOException")
        }
        return dictionaries.toIntArray()
    }

    fun getIntFromString(value: String, defVal: Int): Int {
        val num = NUMBER_RE.matcher(value)
        return if (num.matches()) num.group(1)!!.toInt() else defVal
    }

    fun getPrefInt(prefs: SharedPreferences, prefName: String, defVal: Int): Int {
        val prefVal = prefs.getString(prefName, defVal.toString())
        return getIntFromString(prefVal ?: defVal.toString(), defVal)
    }

    fun getPrefInt(prefs: SharedPreferences, prefName: String, defStr: String): Int {
        val defVal = getIntFromString(defStr, 0)
        return getPrefInt(prefs, prefName, defVal)
    }

    fun getHeight(prefs: SharedPreferences, prefName: String, defVal: String): Int {
        var value = getPrefInt(prefs, prefName, defVal)
        if (value < 15) value = 15
        if (value > 75) value = 75
        return value
    }
}
