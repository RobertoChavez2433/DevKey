package dev.devkey.keyboard.dictionary.loader
import dev.devkey.keyboard.dictionary.base.BinaryDictionary
import dev.devkey.keyboard.suggestion.engine.Suggest

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log
import java.io.IOException
import java.io.InputStream

private const val TAG = "DevKey/PluginManager"

internal abstract class DictPluginSpecBase : PluginManager.DictPluginSpec {
    var mPackageName: String = ""

    fun getResources(context: Context): Resources? {
        val packageManager = context.packageManager
        return try {
            val appInfo = packageManager.getApplicationInfo(mPackageName, 0)
            packageManager.getResourcesForApplication(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.i(TAG, "couldn't get resources")
            null
        }
    }

    abstract fun getStreams(res: Resources): Array<InputStream>?

    override fun getDict(context: Context): BinaryDictionary? {
        val res = getResources(context) ?: return null
        val dicts = getStreams(res) ?: return null
        val dict = BinaryDictionary(context, dicts, Suggest.DIC_MAIN)
        if (dict.getSize() == 0) return null
        return dict
    }
}

internal class DictPluginSpecLegacy(pkg: String, private val mRawIds: IntArray?) : DictPluginSpecBase() {
    init {
        mPackageName = pkg
    }

    override fun getStreams(res: Resources): Array<InputStream>? {
        if (mRawIds == null || mRawIds.isEmpty()) return null
        return Array(mRawIds.size) { i -> res.openRawResource(mRawIds[i]) }
    }
}

internal class DictPluginSpecSoftKeyboard(
    pkg: String,
    private val mAssetName: String?,
    private val mResId: Int
) : DictPluginSpecBase() {
    init {
        mPackageName = pkg
    }

    override fun getStreams(res: Resources): Array<InputStream>? {
        if (mAssetName == null) {
            if (mResId == 0) return null
            val a = res.obtainTypedArray(mResId)
            val resIds: IntArray
            try {
                resIds = IntArray(a.length()) { i -> a.getResourceId(i, 0) }
            } finally {
                a.recycle()
            }
            return Array(resIds.size) { i -> res.openRawResource(resIds[i]) }
        } else {
            return try {
                val input = res.assets.open(mAssetName)
                arrayOf(input)
            } catch (e: IOException) {
                Log.e(TAG, "Dictionary asset loading failure")
                null
            }
        }
    }
}
