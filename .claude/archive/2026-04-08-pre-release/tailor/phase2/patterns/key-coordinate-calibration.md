# Pattern — Key Coordinate Calibration Cascade

## Summary
DevKey resolves runtime key coordinates via a 3-tier cascade: broadcast → cache → Y-scan probe. The broadcast tier is the fast path — a debug-only IntentFilter in `LatinIME.onCreate()` triggers `KeyMapGenerator.dumpToLogcat()` which writes one `KEY label=X code=N x=P y=Q` line per key to the `DevKeyMap` logcat tag. The cache tier reads `.claude/test-flows/calibration.json` (device-specific, gitignored). The scan tier binary-searches for the keyboard-top Y by tapping candidate positions. Phase 2 tests should ALWAYS start with the broadcast tier.

## How we do it
- `KeyMapGenerator` has two map-builders: `getKeyMap(context, view, mode)` (precise, uses `getLocationOnScreen`) and `getKeyMapCalculated(context, mode)` (fallback, computes from display metrics)
- `dumpToLogcat(context, view, mode)` writes `DevKeyMap` logcat lines in a fixed format
- `dumpToLogcatWhenReady(context, view, mode)` is a suspend function that polls `keyboardView.isLaidOut` for up to 2000ms before dumping (handles the initial Compose layout race)
- The broadcast receiver at `LatinIME.kt:417` calls `dumpToLogcat(context, null, mode)` — passing `null` for view means it uses the calculated fallback (less precise but works without a live view reference during a broadcast)
- The layout mode is read from `PreferenceManager.getDefaultSharedPreferences(context).getString(SettingsRepository.KEY_LAYOUT_MODE, "full")`

## Exemplar 1 — Broadcast receiver

**File**: `app/src/main/java/dev/devkey/keyboard/LatinIME.kt:403-422`

```kotlin
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

## Exemplar 2 — dumpToLogcat output format

**File**: `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt:171-197`

```kotlin
fun dumpToLogcat(context: Context, keyboardView: View?, layoutMode: LayoutMode) {
    val dm = getDisplayMetrics(context)
    val density = dm.density

    val bounds = if (keyboardView != null) {
        getKeyMap(context, keyboardView, layoutMode)
    } else {
        getKeyMapCalculated(context, layoutMode)
    }

    Log.d(TAG, "=== DevKey Key Map ===")
    Log.d(TAG, "screen=${dm.widthPixels}x${dm.heightPixels} density=$density mode=$layoutMode")

    if (keyboardView != null) {
        val location = IntArray(2)
        keyboardView.getLocationOnScreen(location)
        Log.d(TAG, "keyboard_view_top=${location[1]}")
    } else {
        Log.d(TAG, "keyboard_view_top=estimated")
    }

    for (kb in bounds) {
        Log.d(TAG, "KEY label=${kb.adbLabel} code=${kb.code} x=${kb.centerX.toInt()} y=${kb.centerY.toInt()}")
    }

    Log.d(TAG, "=== End Key Map (${bounds.size} keys) ===")
}
```

**Format** (one `KEY` line per key):
```
DevKeyMap: KEY label=a code=97 x=54 y=1775
DevKeyMap: KEY label=Shift code=-1 x=36 cy=1899
DevKeyMap: KEY label=123 code=-2 x=54 y=2053
```

**Regex**: `r"KEY label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)"`

## Reusable methods table

| Method | Signature | Purpose |
|---|---|---|
| `KeyMapGenerator.isDebugBuild(context)` | `(Context) -> Boolean` | Gate debug code paths |
| `KeyMapGenerator.getKeyMap(context, view, mode)` | precise | Uses `View.getLocationOnScreen` |
| `KeyMapGenerator.getKeyMapCalculated(context, mode)` | fallback | No view required |
| `KeyMapGenerator.dumpToLogcat(context, view?, mode)` | writes to `DevKeyMap` tag | Broadcast handler target |
| `KeyMapGenerator.dumpToLogcatWhenReady(context, view, mode)` | suspend | Polls `isLaidOut` up to 2000ms |

## Required imports (for adding a new calibration caller in Kotlin)
```kotlin
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.ui.keyboard.LayoutMode
```

## Cache tier

**File**: `.claude/test-flows/calibration.json` (gitignored, device-specific)

Schema (from Session 32 memory):
- Device serial
- Screen size (width × height)
- Mode (FULL/COMPACT/COMPACT_DEV)
- Timestamp
- Source (broadcast/cache/scan/fallback)
- Per-row Y coordinates
- Per-key X coordinates

**Currently absent** — verified via `Glob`. Generated on first successful calibration.

## Scan-probe tier

**Documented in**: `.claude/skills/test/references/ime-testing-patterns.md`

Binary search for keyboard top Y by tapping candidate positions and checking which row's keys produce the expected `DevKeyPress` tag. Slowest path; used only when broadcast and cache both fail.

## Pre-calibrated reference (final fallback)

**File**: `.claude/docs/reference/key-coordinates.md`

Static table of coordinates calibrated on one emulator. FULL mode only. **Phase 2.4 must extend this file with COMPACT and COMPACT_DEV row tables.**

## Phase 2 implications

1. **The driver server's "calibration" operation is just** `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` followed by parsing `adb logcat -d -s DevKeyMap`. No LLM involvement needed.
2. **For each layout mode switch**, the driver server must: (a) flip `SettingsRepository.KEY_LAYOUT_MODE` (via new `SET_LAYOUT_MODE` broadcast — see `debug-broadcast-receiver.md`), (b) wait for recomposition, (c) re-issue `DUMP_KEY_MAP`, (d) reparse coordinates. Because each mode has a different key arrangement, the coordinates must be refreshed per mode.
3. **The existing `tools/e2e/lib/keyboard.py:load_key_map` regex is wrong** — Phase 2.4 must fix it.
