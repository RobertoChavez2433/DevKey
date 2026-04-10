# Pattern — Debug Broadcast Receiver

## Summary
DevKey uses `RECEIVER_NOT_EXPORTED` broadcast receivers gated on `KeyMapGenerator.isDebugBuild(context)` to trigger debug behaviors from ADB. The canonical exemplar is the `DUMP_KEY_MAP` receiver — a test harness issues `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` and the IME dumps its current layout's key coordinates to logcat. Phase 2.1 must add a second receiver for `ENABLE_DEBUG_SERVER` using the same structure, and Phase 2.4 may add a third for `SET_LAYOUT_MODE`.

## How we do it
- Debug-only code path is gated on `KeyMapGenerator.isDebugBuild(this)` (checks `ApplicationInfo.FLAG_DEBUGGABLE`, works without `BuildConfig`)
- Receivers are registered in `LatinIME.onCreate()` near existing receivers, not manifest-declared
- Registration uses `Context.RECEIVER_NOT_EXPORTED` — locks out other apps
- Intent actions are namespaced `dev.devkey.keyboard.<NAME>` (matches package)
- Extras passed via `--es`, `--ei`, `--ez` at broadcast time
- Unregister on `onDestroy()` if a field holds the receiver reference

## Exemplar 1 — DUMP_KEY_MAP receiver

**Symbol**: `LatinIME.keyMapDumpReceiver`
**File**: `app/src/main/java/dev/devkey/keyboard/LatinIME.kt:403-422`

```kotlin
// Debug: register broadcast receiver for on-demand key map dump
if (KeyMapGenerator.isDebugBuild(this)) {
    keyMapDumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val modeStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(SettingsRepository.KEY_LAYOUT_MODE, "full") ?: "full"
            val mode = when (modeStr) {
                "compact" -> LayoutMode.COMPACT
                "compact_dev" -> LayoutMode.COMPACT_DEV
                else -> LayoutMode.FULL
            }
            KeyMapGenerator.dumpToLogcat(context, null, mode)
        }
    }
    registerReceiver(
        keyMapDumpReceiver,
        IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"),
        Context.RECEIVER_NOT_EXPORTED
    )
}
```

**Invocation**:
```bash
adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP
```

## Reusable structure for new receivers

```kotlin
// Field on LatinIME class:
private var <name>Receiver: BroadcastReceiver? = null

// In onCreate(), inside the isDebugBuild gate:
<name>Receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Read extras, do work
    }
}
registerReceiver(
    <name>Receiver,
    IntentFilter("dev.devkey.keyboard.<ACTION>"),
    Context.RECEIVER_NOT_EXPORTED
)

// In onDestroy():
<name>Receiver?.let { unregisterReceiver(it); <name>Receiver = null }
```

**Note**: The existing `DUMP_KEY_MAP` receiver does NOT appear to unregister in `onDestroy` in the current code. Phase 2 should fix this for any new receivers to prevent leaks.

## Reusable methods table

| Call | Purpose |
|---|---|
| `KeyMapGenerator.isDebugBuild(context)` | Gate debug-only code on `FLAG_DEBUGGABLE` |
| `registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)` | Debug-only receiver that's unreachable from other apps |
| `IntentFilter("dev.devkey.keyboard.<ACTION>")` | Namespace-matched action string |
| `intent.getStringExtra("key")` / `getBooleanExtra("key", false)` / `getIntExtra("key", 0)` | Read extras passed via `adb shell am broadcast --es/--ez/--ei` |

## Required imports
```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.devkey.keyboard.debug.KeyMapGenerator
```

All already present in `LatinIME.kt` — no new imports needed.

## ADB invocation reference

| Extra type | Flag | Example |
|---|---|---|
| String | `--es` | `--es url http://10.0.2.2:3947` |
| Boolean | `--ez` | `--ez debug true` |
| Int | `--ei` | `--ei port 3947` |
| Long | `--el` | `--el timeout 5000` |
| Float | `--ef` | `--ef scale 1.5` |

## Phase 2 new receivers

### `ENABLE_DEBUG_SERVER` (item 2.1)
```bash
adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://10.0.2.2:3947
# Or to disable:
adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER
```
Handler reads `intent.getStringExtra("url")`, calls `DevKeyLogger.enableServer(url)` or `disableServer()` on null.

### `SET_LAYOUT_MODE` (item 2.4 — optional, plan decision)
```bash
adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact
```
Handler writes `SettingsRepository.KEY_LAYOUT_MODE = "compact"` (or `"compact_dev"` / `"full"`) and triggers a reload. May also need to re-trigger Compose recomposition; plan author should verify the preference observer path hits the keyboard view.
