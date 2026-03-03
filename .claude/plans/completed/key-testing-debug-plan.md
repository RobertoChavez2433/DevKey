# Key Press Testing & Debug System — Implementation Plan

**Created**: 2026-03-01 (Session 19)
**Status**: APPROVED
**Goal**: Comprehensive key press debugging + ADB coordinate mapping for reliable automated testing

## Problem Statement

1. **Zero logging** in the key press chain (KeyView -> KeyboardActionBridge -> LatinIME.onKey)
2. **Wrong ADB coordinates** when tapping keys on emulator — no reliable key-to-pixel mapping
3. **No integration test support** for automated key pressing

## Deliverables

1. **KeyPressLogger** — Debug logging at every stage of the key press chain
2. **KeyBoundsCalculator** — Pure Kotlin coordinate calculator (JVM-testable)
3. **KeyMapGenerator** — ADB-ready key-name-to-screen-pixel mapping
4. **Tests** — Full test coverage for the coordinate calculator

---

## Implementation Steps

### Step 1 (NEW): `core/KeyPressLogger.kt`

**File**: `app/src/main/java/dev/devkey/keyboard/core/KeyPressLogger.kt`

Single-object logger. Uses `Log.d()` which R8 strips from release builds.

```kotlin
package dev.devkey.keyboard.core

import android.util.Log

object KeyPressLogger {
    private const val TAG = "DevKeyPress"

    fun logKeyDown(label: String, code: Int, type: String) {
        Log.d(TAG, "DOWN  label=$label code=$code type=$type")
    }

    fun logKeyTap(label: String, code: Int) {
        Log.d(TAG, "TAP   label=$label code=$code")
    }

    fun logLongPress(label: String, code: Int, longPressCode: Int?) {
        Log.d(TAG, "LONG  label=$label code=$code lpCode=$longPressCode")
    }

    fun logRepeat(label: String, code: Int, count: Int) {
        Log.d(TAG, "RPT   label=$label code=$code count=$count")
    }

    fun logKeyUp(label: String, code: Int) {
        Log.d(TAG, "UP    label=$label code=$code")
    }

    fun logBridgeOnKey(rawCode: Int, effectiveCode: Int, shiftActive: Boolean, ctrlActive: Boolean, altActive: Boolean) {
        Log.d(TAG, "BRIDGE raw=$rawCode eff=$effectiveCode shift=$shiftActive ctrl=$ctrlActive alt=$altActive")
    }

    fun logModifierTransition(type: String, from: String, to: String) {
        Log.d(TAG, "MOD   $type: $from -> $to")
    }
}
```

**Logcat filter**: `adb logcat -s DevKeyPress:D`

---

### Step 2 (MODIFY): `ui/keyboard/KeyView.kt`

**Depends on**: Step 1

Add `import dev.devkey.keyboard.core.KeyPressLogger`

**Modifier key path** (inside `onPress` for `key.type == KeyType.MODIFIER`):
- After `isPressed = true`: `KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, "MODIFIER")`
- After `isPressed = false`: `KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)`
- After `onModifierTap()` completes, read the public StateFlow value and log:
  ```kotlin
  val currentState = when (modType) {
      ModifierType.SHIFT -> modifierState.shiftState.value
      ModifierType.CTRL -> modifierState.ctrlState.value
      ModifierType.ALT -> modifierState.altState.value
  }
  KeyPressLogger.logModifierTransition(modType.name, "tap", currentState.name)
  ```

**Non-modifier key path**:
- After `isPressed = true` + `onKeyPress()`: `KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, key.type.name)`
- After `delay(300L)` triggers long-press: `KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)`
- Inside repeat while loop, throttle to every 10th:
  ```kotlin
  var repeatCount = 0
  while (isActive) {
      repeatCount++
      onKeyAction(key.primaryCode)
      if (repeatCount % 10 == 0) {
          KeyPressLogger.logRepeat(key.primaryLabel, key.primaryCode, repeatCount)
      }
      delay(50L)
  }
  ```
- Short tap path (`if (released && !job.isCompleted)`): `KeyPressLogger.logKeyTap(key.primaryLabel, key.primaryCode)`
- After `onKeyRelease()`: `KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)`

**NOTE**: Do NOT modify `ModifierStateManager.kt` — keep it pure JVM-testable. Log modifier transitions from KeyView call sites instead.

---

### Step 3 (MODIFY): `core/KeyboardActionBridge.kt`

**Depends on**: Step 1

Add `import dev.devkey.keyboard.core.KeyPressLogger`

In `onKey()`, after computing `effectiveCode`, before `listener.onKey()`:
```kotlin
KeyPressLogger.logBridgeOnKey(code, effectiveCode, modifierState.isShiftActive(), modifierState.isCtrlActive(), modifierState.isAltActive())
```

---

### Step 4 (MODIFY): `LatinIME.java`

**Independent** (no dependencies)

At line ~1984 inside `onKey()`, after `long when = SystemClock.uptimeMillis();`:
```java
Log.d("DevKeyPress", "IME   code=" + primaryCode + " x=" + x + " y=" + y);
```

`android.util.Log` is already imported.

---

### Step 5 (NEW): `ui/keyboard/KeyBoundsCalculator.kt`

**Independent** — Pure Kotlin, zero Android imports, JVM-testable.

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyBoundsCalculator.kt`

```kotlin
package dev.devkey.keyboard.ui.keyboard

data class KeyBounds(
    val label: String,
    val code: Int,
    val row: Int,
    val col: Int,
    val left: Float,     // px from left edge
    val top: Float,      // px from key area top
    val right: Float,
    val bottom: Float,
    val centerX: Float,
    val centerY: Float
) {
    /** Normalized label for ADB scripts */
    val adbLabel: String get() = when {
        label == " " -> "Space"
        label == "\u2190" -> "ArrowLeft"
        label == "\u2191" -> "ArrowUp"
        label == "\u2193" -> "ArrowDown"
        label == "\u2192" -> "ArrowRight"
        code == -5 -> "Backspace"
        else -> label
    }
}

/**
 * Computes pixel bounds for every key in a layout.
 *
 * Mirrors Compose layout logic:
 * - KeyboardView: Column with Arrangement.spacedBy(rowGapPx), padding(horizontalPaddingPx)
 * - Rows: equal weight (equal height after gap subtraction)
 * - Keys: weight-based horizontal sizing with Arrangement.spacedBy(keyGapPx)
 *
 * All inputs/outputs in pixels. Caller converts dp * density.
 */
fun computeKeyBounds(
    layout: KeyboardLayoutData,
    keyboardWidthPx: Float,
    keyboardHeightPx: Float,
    horizontalPaddingPx: Float,
    rowGapPx: Float,
    keyGapPx: Float
): List<KeyBounds> {
    val rowCount = layout.rows.size
    if (rowCount == 0) return emptyList()

    val totalRowGaps = rowGapPx * (rowCount - 1)
    val rowHeight = (keyboardHeightPx - totalRowGaps) / rowCount
    val availableWidth = keyboardWidthPx - (2 * horizontalPaddingPx)

    val result = mutableListOf<KeyBounds>()

    for ((rowIndex, row) in layout.rows.withIndex()) {
        val rowTop = rowIndex * (rowHeight + rowGapPx)
        val keyCount = row.keys.size
        if (keyCount == 0) continue

        val totalKeyGaps = keyGapPx * (keyCount - 1)
        val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
        val weightedWidth = availableWidth - totalKeyGaps

        var keyLeft = horizontalPaddingPx
        for ((colIndex, key) in row.keys.withIndex()) {
            val keyWidth = (key.weight / totalWeight) * weightedWidth
            val keyRight = keyLeft + keyWidth
            val keyBottom = rowTop + rowHeight

            result.add(KeyBounds(
                label = key.primaryLabel,
                code = key.primaryCode,
                row = rowIndex,
                col = colIndex,
                left = keyLeft,
                top = rowTop,
                right = keyRight,
                bottom = keyBottom,
                centerX = (keyLeft + keyRight) / 2f,
                centerY = (rowTop + keyBottom) / 2f
            ))

            keyLeft = keyRight + keyGapPx
        }
    }

    return result
}
```

---

### Step 6 (NEW): `test/.../KeyBoundsCalculatorTest.kt`

**Depends on**: Step 5

**File**: `app/src/test/java/dev/devkey/keyboard/ui/keyboard/KeyBoundsCalculatorTest.kt`

Emulator spec: 1080x2400 screen, 2.625 density.
keyAreaHeight: `(2400/2.625 * 0.40 - 36) * 2.625 = 865.5px`

**Tests** (11 tests):

| Test | What It Validates |
|------|-------------------|
| `returns one KeyBounds per key in full layout` | Count matches total keys |
| `returns one KeyBounds per key in compact layout` | Compact mode too |
| `all keys have positive dimensions` | width > 0, height > 0 |
| `space bar is widest key in bottom row` | Weight 5.0 > all others |
| `key centers within keyboard bounds` | centerX in 0..width, centerY in 0..height |
| `shift key is 1.5x width of letter key` | Weight ratio validated |
| `rows do not overlap vertically` | row[n].bottom <= row[n+1].top |
| `keys within a row do not overlap horizontally` | key[n].right <= key[n+1].left |
| `adbLabel normalizes space and arrows` | Space, ArrowLeft/Up/Down/Right |
| `empty layout returns empty list` | Edge case |
| `bottom row total width fills available space` | Sum of key widths = available width |

---

### Step 7 (NEW): `debug/KeyMapGenerator.kt`

**Depends on**: Step 5

**File**: `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt`

Two modes:
1. **Precise**: Uses `View.getLocationOnScreen()` for exact keyboard Y offset
2. **Fallback**: Calculates from screen dimensions (less accurate with non-standard system bars)

Key features:
- `getKeyMap(context, keyboardView, compactMode)` — precise coordinates
- `getKeyMapCalculated(context, compactMode)` — fallback
- `dumpToLogcat(context, keyboardView?, compactMode)` — outputs to logcat
- `isDebugBuild(context)` — uses `ApplicationInfo.FLAG_DEBUGGABLE` (no BuildConfig needed)
- Reads height preference from SharedPreferences (`KEY_HEIGHT_PORTRAIT` / `KEY_HEIGHT_LANDSCAPE`)
- Mirrors KeyboardView.kt calculation: `rawDp = screenHeightDp * heightPercent - toolbarHeightDp`

**Logcat output format**:
```
=== DevKey Key Map ===
screen=1080x2400 density=2.625
keyboard_view_top=1534
KEY label=Esc code=-111 x=55 y=1620
KEY label=1 code=49 x=162 y=1620
...
KEY label=Space code=32 x=520 y=2318
KEY label=Enter code=10 x=1013 y=2318
=== End Key Map (47 keys) ===
```

**Logcat filter**: `adb logcat -s DevKeyMap:D`

---

### Step 8 (MODIFY): `ui/keyboard/DevKeyKeyboard.kt`

**Depends on**: Step 7

After line ~82 (`val compactMode = ...`), add:

```kotlin
val currentView = LocalView.current
LaunchedEffect(Unit) {
    if (KeyMapGenerator.isDebugBuild(context)) {
        kotlinx.coroutines.delay(500L)  // wait for layout
        KeyMapGenerator.dumpToLogcat(context, currentView, compactMode.value)
    }
}
```

Add imports:
```kotlin
import androidx.compose.ui.platform.LocalView
import dev.devkey.keyboard.debug.KeyMapGenerator
```

---

## Implementation Order

| Step | File | Type | Depends On | Parallel Group |
|------|------|------|------------|----------------|
| 1 | `core/KeyPressLogger.kt` | NEW | None | A |
| 2 | `ui/keyboard/KeyView.kt` | MODIFY | Step 1 | A |
| 3 | `core/KeyboardActionBridge.kt` | MODIFY | Step 1 | A |
| 4 | `LatinIME.java` | MODIFY | None | A or B |
| 5 | `ui/keyboard/KeyBoundsCalculator.kt` | NEW | None | B |
| 6 | `test/.../KeyBoundsCalculatorTest.kt` | NEW | Step 5 | B |
| 7 | `debug/KeyMapGenerator.kt` | NEW | Step 5 | B |
| 8 | `ui/keyboard/DevKeyKeyboard.kt` | MODIFY | Step 7 | B |

Groups A and B are independent and can be implemented in parallel.

---

## Adversarial Review Findings (Addressed)

| Issue | Resolution |
|-------|------------|
| Keyboard Y offset varies by nav bar / system bars | Use `View.getLocationOnScreen()` with calculation fallback |
| `DisplayMetrics.heightPixels` may exclude nav bar | `getLocationOnScreen()` gives absolute position regardless |
| Space label is `" "` — hard to match in scripts | `adbLabel` property normalizes to "Space", arrow Unicode to "ArrowLeft" etc. |
| `BuildConfig.DEBUG` needs `buildConfig = true` in AGP 8+ | Use `ApplicationInfo.FLAG_DEBUGGABLE` instead |
| Repeat logging at 50ms = 20 lines/sec | Throttled to every 10th repeat (2 lines/sec) |
| Modifying `ModifierStateManager.kt` breaks JVM tests | Log modifier transitions from KeyView.kt call sites instead |
| Toolbar may be hidden during macro recording | Document: key map assumes Normal mode with toolbar visible |
| `proguard-android-optimize.txt` strips `Log.d` | Confirmed: no additional toggle needed |

## What This Does NOT Cover (Intentionally)

- **Instrumented tests** (androidTest) — not needed; pure JVM tests + ADB logcat dump
- **Runtime bounds via `onGloballyPositioned`** — future enhancement if calculator diverges from actual rendering
- **ADB shell script** — optional; `adb logcat -s DevKeyMap:D` is sufficient
- **ProGuard changes** — `Log.d()` already stripped by default optimize rules

## Usage After Implementation

```bash
# See all key presses in real-time:
adb logcat -s DevKeyPress:D

# Dump full key coordinate map:
# (automatically dumps on keyboard startup in debug builds)
adb logcat -s DevKeyMap:D

# Extract key map to file:
adb logcat -d -s DevKeyMap:D > keymap.txt

# Find a specific key's coordinates:
adb logcat -d -s DevKeyMap:D | grep "label=q "
# Output: KEY label=q code=113 x=162 y=1987

# Tap that key:
adb shell input tap 162 1987
```
