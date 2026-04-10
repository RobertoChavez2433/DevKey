package dev.devkey.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.devkey.keyboard.BuildConfig
import dev.devkey.keyboard.debug.DevKeyLogger

class PluginManager(private val mIME: LatinIME) : BroadcastReceiver() {

    interface DictPluginSpec {
        fun getDict(context: Context): BinaryDictionary?
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Package information changed, updating dictionaries.")
        getPluginDictionaries(context)
        Log.i(TAG, "Finished updating dictionaries.")
        if (intent.action != "dev.devkey.keyboard.RESCAN_PLUGINS") {
            mIME.toggleLanguage(true, true)
        }
    }

    companion object {
        private const val TAG = "DevKey/PluginManager"

        private val mPluginDicts = HashMap<String, DictPluginSpec>()

        /**
         * GH #4: plugin loading is disabled in release builds. Debug builds
         * retain the current behavior so local development and dictionary
         * plugin authors can keep testing.
         *
         * Compile-time gate via BuildConfig.DEBUG. A re-signed APK with
         * debuggable=true cannot flip this flag — strictly stronger than
         * the runtime ApplicationInfo.FLAG_DEBUGGABLE check used by
         * KeyMapGenerator.isDebugBuild.
         */
        private fun arePluginsEnabled(): Boolean = BuildConfig.DEBUG

        fun getPluginDictionaries(context: Context) {
            // WHY: GH #4 — plugins load untrusted foreign-package resources
            // into the native dictionary via BinaryDictionary without signature
            // verification. Per spec §2.4(b), disable the load path entirely in
            // release builds. Full signature verification is a v1.1 target.
            if (!arePluginsEnabled()) {
                Log.i(TAG, "Plugin dictionaries disabled in release build (GH #4)")
                mPluginDicts.clear()
                // Phase 4.9 — structural plugin_scan_complete emit for E2E smoke tests.
                // PRIVACY: count only — NEVER plugin metadata / names / paths.
                DevKeyLogger.ime(
                    "plugin_scan_complete",
                    mapOf("plugin_count" to 0)
                )
                return
            }
            mPluginDicts.clear()
            val packageManager = context.packageManager
            getSoftKeyboardDictionaries(packageManager, mPluginDicts)
            getLegacyDictionaries(packageManager, mPluginDicts)
            // Phase 4.9 — structural plugin_scan_complete emit for E2E smoke tests.
            // PRIVACY: count only — NEVER plugin metadata / names / paths.
            DevKeyLogger.ime(
                "plugin_scan_complete",
                mapOf("plugin_count" to mPluginDicts.size)
            )
        }

        fun getDictionary(context: Context, lang: String): BinaryDictionary? {
            // WHY: GH #4 — defense in depth. Refuse to hand back any plugin
            // dictionary in release builds even if mPluginDicts is somehow populated.
            if (!arePluginsEnabled()) return null
            var spec = mPluginDicts[lang]
            if (spec == null && lang.length >= 2) spec = mPluginDicts[lang.substring(0, 2)]
            if (spec == null) {
                return null
            }
            val dict = spec.getDict(context)
            Log.i(TAG, "Found plugin dictionary for $lang${if (dict == null) " is null" else ", size=${dict.getSize()}"}")
            return dict
        }
    }
}
