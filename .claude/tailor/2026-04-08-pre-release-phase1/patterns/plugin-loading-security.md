# Pattern: Plugin Loading & Debug-Build Gate

**Relevant to**: Phase 1.8 (GH #4 resolution)

## Current situation (GH #4)

`PluginManager` discovers dictionary plugins installed as separate APKs by querying the `PackageManager` for two intent actions:
- `org.pocketworkstation.DICT` (legacy HK plugin format)
- `com.menny.android.anysoftkeyboard.DICTIONARY` (AnySoftKeyboard format)

For each matching package, it calls `PackageManager.getResourcesForApplication(appInfo)` to load foreign-package resources, then reads raw byte streams (XML or binary) into a native `BinaryDictionary`. **No signature verification. No allowlist. No hash check.** Any app the user installs that declares one of these intents can supply a dictionary which is then loaded into the IME's native dictionary process.

This is the vulnerability tracked in GH #4.

## Spec guidance

Spec §2.4:
> Plugin system security: GH #4 (plugins load untrusted packages without signature verification) must be resolved. Since plugins are in-scope for v1.0, this cannot be deferred. Either (a) add signature verification, or (b) gate plugin loading behind a debug-only flag for v1.0.

**Minimal fix** (option b): gate all plugin load methods on a debug-build flag, so plugin loading is a no-op in release builds.

## Exemplar — Existing debug-build check

`app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt:38-42`

```kotlin
fun isDebugBuild(context: Context): Boolean {
    return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
```

This helper is reusable from any class with a `Context`.

## Current `getSoftKeyboardDictionaries` (the unsafe load path)

`app/src/main/java/dev/devkey/keyboard/PluginManager.kt:107-173` (abbreviated)

```kotlin
fun getSoftKeyboardDictionaries(packageManager: PackageManager) {
    val dictIntent = Intent(SOFTKEYBOARD_INTENT_DICT)
    val dictPacks = packageManager.queryBroadcastReceivers(
        dictIntent, PackageManager.GET_META_DATA
    )
    for (ri in dictPacks) {
        val appInfo = ri.activityInfo.applicationInfo
        val pkgName = appInfo.packageName
        try {
            val res = packageManager.getResourcesForApplication(appInfo)  // ← foreign resources
            // ... parse XML, extract dict asset name, build DictPluginSpecSoftKeyboard ...
            mPluginDicts[lang] = spec
        } catch (e: PackageManager.NameNotFoundException) { ... }
    }
}
```

`getLegacyDictionaries` has the same shape for `LEGACY_INTENT_DICT`.

Callers:
- `LatinIME.kt:379` — `PluginManager.getPluginDictionaries(applicationContext)` (during onCreate)
- `Suggest.kt:70` — `PluginManager.getDictionary(context, locale.language)`
- `InputLanguageSelection.kt:111` — same

## Suggested Phase 1.8 shape (option b)

```kotlin
// PluginManager.kt — near the top
companion object {
    private const val TAG = "DevKey/PluginManager"
    private const val LEGACY_INTENT_DICT = "org.pocketworkstation.DICT"
    private const val SOFTKEYBOARD_INTENT_DICT = "com.menny.android.anysoftkeyboard.DICTIONARY"

    /** GH #4: plugins load untrusted code. In release builds, disable entirely. */
    private fun arePluginsEnabled(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun getPluginDictionaries(context: Context) {
        if (!arePluginsEnabled(context)) {
            Log.i(TAG, "Plugin dictionaries disabled in release build (GH #4)")
            return
        }
        val pm = context.packageManager
        getSoftKeyboardDictionaries(pm)
        getLegacyDictionaries(pm)
    }

    fun getDictionary(context: Context, lang: String): BinaryDictionary? {
        if (!arePluginsEnabled(context)) return null
        return mPluginDicts[lang]?.getDict(context)
    }
}
```

## Reusable methods / flags

| Symbol | Location | Use |
|---|---|---|
| `ApplicationInfo.FLAG_DEBUGGABLE` | `android.content.pm.ApplicationInfo` | Bitmask to check debug-build state |
| `KeyMapGenerator.isDebugBuild(context)` | `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt:38` | Existing helper — can be reused OR inlined in PluginManager (prefer inline to keep PluginManager independent of debug module) |
| `BuildConfig.DEBUG` | Generated | Alternative to runtime flag — simpler but requires `buildFeatures.buildConfig = true` in gradle |

## Required imports (additional)

```kotlin
import android.content.pm.ApplicationInfo
```

## Phase 1.8 implications

1. **Scope**: Changes live entirely in `PluginManager.kt`. Two or three early-returns. No caller-side changes required — `getDictionary` returning `null` is a pre-existing case that `Suggest.kt:70-74` already handles (falls back to built-in dictionary).
2. **Testing**: Must verify the no-op path (release-build behavior) and the permissive path (debug-build behavior) both work. Debug path maintains existing behavior; release path is a pure no-op.
3. **Follow-up** (out of scope for v1.0): full signature verification with trusted signing certs is the v1.1 target. Document this in the code comment linking back to GH #4.
4. **Alternative — BuildConfig.DEBUG**: Simpler and catch at compile time, but requires `buildFeatures.buildConfig = true` in `app/build.gradle.kts`. Check gradle file during plan.
