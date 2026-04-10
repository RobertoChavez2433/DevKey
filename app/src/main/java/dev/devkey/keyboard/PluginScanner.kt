package dev.devkey.keyboard

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

private const val TAG = "DevKey/PluginManager"

private const val LEGACY_INTENT_DICT = "org.pocketworkstation.DICT"
private const val SOFTKEYBOARD_INTENT_DICT = "com.menny.android.anysoftkeyboard.DICTIONARY"
private const val SOFTKEYBOARD_DICT_RESOURCE_METADATA_NAME =
    "com.menny.android.anysoftkeyboard.dictionaries"

// Apparently anysoftkeyboard doesn't use ISO 639-1 language codes for its locales?
// Add exceptions as needed.
private val SOFTKEYBOARD_LANG_MAP = mapOf("dk" to "da")

internal fun getSoftKeyboardDictionaries(
    packageManager: PackageManager,
    mPluginDicts: HashMap<String, PluginManager.DictPluginSpec>
) {
    val dictIntent = Intent(SOFTKEYBOARD_INTENT_DICT)
    val dictPacks = packageManager.queryBroadcastReceivers(
        dictIntent, PackageManager.GET_META_DATA
    )
    for (ri in dictPacks) {
        val appInfo = ri.activityInfo.applicationInfo
        val pkgName = appInfo.packageName
        var success = false
        try {
            val res = packageManager.getResourcesForApplication(appInfo)
            var dictId = res.getIdentifier("dictionaries", "xml", pkgName)
            if (dictId == 0) {
                try {
                    dictId = ri.activityInfo.metaData.getInt(
                        SOFTKEYBOARD_DICT_RESOURCE_METADATA_NAME
                    )
                } catch (_: Exception) {
                }
            }
            if (dictId == 0) continue
            val xrp = res.getXml(dictId)

            var assetName: String? = null
            var resId = 0
            var lang: String? = null
            try {
                var current = xrp.eventType
                while (current != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (current == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        val tag = xrp.name
                        if (tag == "Dictionary") {
                            lang = xrp.getAttributeValue(null, "locale")
                            val convLang = SOFTKEYBOARD_LANG_MAP[lang]
                            if (convLang != null) lang = convLang
                            val type = xrp.getAttributeValue(null, "type")
                            if (type == null || type == "raw" || type == "binary" || type == "binary_resource") {
                                assetName = xrp.getAttributeValue(null, "dictionaryAssertName") // sic
                                resId = xrp.getAttributeResourceValue(null, "dictionaryResourceId", 0)
                            } else {
                                Log.w(TAG, "Unsupported AnySoftKeyboard dict type $type")
                            }
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

            if ((assetName == null && resId == 0) || lang == null) continue
            val spec = DictPluginSpecSoftKeyboard(pkgName, assetName, resId)
            mPluginDicts[lang] = spec
            Log.i(TAG, "Found plugin dictionary: lang=$lang, pkg=$pkgName")
            success = true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $pkgName", e)
        } finally {
            if (!success) {
                Log.i(TAG, "failed to load plugin dictionary spec from $pkgName")
            }
        }
    }
}

internal fun getLegacyDictionaries(
    packageManager: PackageManager,
    mPluginDicts: HashMap<String, PluginManager.DictPluginSpec>
) {
    val dictIntent = Intent(LEGACY_INTENT_DICT)
    val dictPacks = packageManager.queryIntentActivities(dictIntent, 0)
    for (ri in dictPacks) {
        val appInfo = ri.activityInfo.applicationInfo
        val pkgName = appInfo.packageName
        var success = false
        try {
            val res = packageManager.getResourcesForApplication(appInfo)
            val langId = res.getIdentifier("dict_language", "string", pkgName)
            if (langId == 0) continue
            val lang = res.getString(langId)
            var rawIds: IntArray?

            // Try single-file version first
            val rawId = res.getIdentifier("main", "raw", pkgName)
            if (rawId != 0) {
                rawIds = intArrayOf(rawId)
            } else {
                // try multi-part version
                val ids = ArrayList<Int>()
                var parts = 0
                while (true) {
                    val id = res.getIdentifier("main$parts", "raw", pkgName)
                    if (id == 0) break
                    ids.add(id)
                    parts++
                }
                if (parts == 0) continue // no parts found
                rawIds = IntArray(parts) { i -> ids[i] }
            }
            val spec = DictPluginSpecLegacy(pkgName, rawIds)
            mPluginDicts[lang] = spec
            Log.i(TAG, "Found plugin dictionary: lang=$lang, pkg=$pkgName")
            success = true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $pkgName", e)
        } finally {
            if (!success) {
                Log.i(TAG, "failed to load plugin dictionary spec from $pkgName")
            }
        }
    }
}
