# Implementation Plan: 123 Mode Switch Fix + E2E Test Harness

**Design doc**: `docs/plans/2026-03-03-123-fix-e2e-harness-design.md`
**Date**: 2026-03-03

## Phase 1: Diagnostic — Confirm root cause (30 min)

### 1.1 Add snapshotFlow diagnostic logging
- In `DevKeyKeyboard.kt`, add a temporary `LaunchedEffect`:
  ```kotlin
  LaunchedEffect(Unit) {
      snapshotFlow { keyboardMode }
          .collect { Log.d("DevKeyMode", "snapshotFlow observed: $it") }
  }
  ```
- Also add a log AFTER the assignment in `toggleMode`:
  ```kotlin
  fun toggleMode(target: KeyboardMode) {
      val before = keyboardMode
      keyboardMode = if (keyboardMode == target) KeyboardMode.Normal else target
      Log.d("DevKeyMode", "toggleMode: $before -> $keyboardMode (post-write)")
  }
  ```
- Build, install, tap 123 key, check logcat for:
  - `toggleMode: Normal -> Symbols (post-write)` — confirms write happened
  - `snapshotFlow observed: Symbols` — confirms Compose saw the change (or absence confirms snapshot issue)

### 1.2 Evaluate result
- If snapshotFlow fires but UI doesn't change → recomposition issue elsewhere
- If snapshotFlow does NOT fire → confirmed: `pointerInput` snapshot propagation bug → proceed to Phase 2
- Remove diagnostic logging after confirmation

## Phase 2: StateFlow extraction (1 hr)

### 2.1 Create KeyboardModeManager
- New file: `app/src/main/java/dev/devkey/keyboard/core/KeyboardModeManager.kt`
- Class with `MutableStateFlow<KeyboardMode>`, `toggleMode()`, `setMode()`
- Move `KeyboardMode` sealed class import (no file move needed, just consumed by new class)

### 2.2 Update DevKeyKeyboard.kt
- Replace line 121: `var keyboardMode by remember { mutableStateOf<KeyboardMode>(KeyboardMode.Normal) }` with:
  ```kotlin
  val modeManager = remember { KeyboardModeManager() }
  val keyboardMode by modeManager.mode.collectAsState()
  ```
- Replace local `toggleMode` function (line 137-139) — delete it
- Update all call sites:
  - Line 153: `toggleMode(KeyboardMode.Clipboard)` → `modeManager.toggleMode(KeyboardMode.Clipboard)`
  - Line 157/159: voice mode toggles → `modeManager.setMode(...)`
  - Line 163: `toggleMode(KeyboardMode.Symbols)` → `modeManager.toggleMode(KeyboardMode.Symbols)`
  - Line 164: `toggleMode(KeyboardMode.MacroChips)` → `modeManager.toggleMode(KeyboardMode.MacroChips)`
  - Line 165: `toggleMode(KeyboardMode.MacroGrid)` → `modeManager.toggleMode(KeyboardMode.MacroGrid)`
  - Line 146/186/196/etc: `keyboardMode = KeyboardMode.Normal` → `modeManager.setMode(KeyboardMode.Normal)`
  - Line 183: `keyboardMode = KeyboardMode.MacroRecording` → `modeManager.setMode(KeyboardMode.MacroRecording)`
  - Line 302: `keyboardMode = KeyboardMode.Normal` (ABC key) → `modeManager.setMode(KeyboardMode.Normal)`
  - Line 306: `toggleMode(KeyboardMode.Symbols)` → `modeManager.toggleMode(KeyboardMode.Symbols)`
  - Lines 343/346/348/350: macro dialog → `modeManager.setMode(KeyboardMode.Normal)`

### 2.3 Unit test KeyboardModeManager
- New file: `app/src/test/java/dev/devkey/keyboard/core/KeyboardModeManagerTest.kt`
- Tests:
  - `toggleMode Normal→Symbols→Normal` round-trip
  - `setMode` direct set
  - `toggleMode same target` toggles back to Normal
  - Flow emission verification

### 2.4 Build and verify
- `./gradlew assembleDebug` — must compile
- `./gradlew test` — all existing tests pass + new tests pass
- Install on emulator, tap 123 → layout MUST switch to symbols
- Tap ABC → layout MUST switch back to normal

## Phase 3: Compose UI Test Infrastructure (1.5 hr)

### 3.1 Add test dependencies
- In `app/build.gradle.kts`, add to `androidTestImplementation`:
  - `androidx.compose.ui:ui-test-junit4`
  - `androidx.compose.ui:ui-test-manifest` (debugImplementation)
  - `androidx.test.ext:junit`
  - `androidx.test.espresso:espresso-core` (transitive dep)

### 3.2 Add test tags to KeyView
- In `KeyView.kt`, add `Modifier.testTag("key_${key.primaryCode}")` to the root `Box`
- Import `androidx.compose.ui.platform.testTag`

### 3.3 Create test mock
- New file: `app/src/androidTest/java/dev/devkey/keyboard/test/MockKeyboardActionListener.kt`
- Implements `KeyboardActionListener`, captures all calls to lists for assertion

### 3.4 Write ModeSwitchTest
- File: `app/src/androidTest/java/dev/devkey/keyboard/ui/keyboard/ModeSwitchTest.kt`
- Tests:
  - `tapSymbolsKey_switchesToSymbolsLayout`: tap 123 → verify symbol keys visible (e.g., `!`, `@`, `#`)
  - `tapAbcKey_switchesBackToNormal`: 123 → symbols → ABC → verify alpha keys visible
  - `tapSymbolsKey_togglesBackToNormal`: 123 → symbols → 123 → verify alpha keys visible

### 3.5 Write ModifierStateTest
- File: `app/src/androidTest/java/dev/devkey/keyboard/ui/keyboard/ModifierStateTest.kt`
- Tests:
  - `tapShift_entersOneShot`: tap Shift → verify Shift visual active
  - `doubleTapShift_entersLocked`: tap Shift, tap Shift (within 400ms) → verify locked indicator
  - `shiftOneShot_consumedAfterLetterTap`: Shift → tap 'a' → next tap produces lowercase

### 3.6 Write KeyActionDispatchTest
- File: `app/src/androidTest/java/dev/devkey/keyboard/ui/keyboard/KeyActionDispatchTest.kt`
- Tests:
  - `tapLetterKey_firesOnKeyAction`: tap 'a' → mock listener received code 97
  - `tapSpecialKey_firesCorrectCode`: tap Backspace → code -301
  - `tapSymbolsKey_doesNotForwardToBridge`: tap 123 → mock listener did NOT receive code -2

### 3.7 Write LayoutRenderTest
- File: `app/src/androidTest/java/dev/devkey/keyboard/ui/keyboard/LayoutRenderTest.kt`
- Tests:
  - `fullMode_renders6Rows`: verify 6 rows visible
  - `fullMode_hasNumberRow`: verify keys ` 1 2 3 ... 0 visible
  - `fullMode_hasUtilityRow`: verify Ctrl, Alt, Tab, arrows visible

## Phase 4: ADB Script Harness (1.5 hr)

### 4.1 Create directory structure
```
tools/e2e/
├── e2e_runner.py
├── lib/
│   ├── __init__.py
│   ├── adb.py
│   └── keyboard.py
├── tests/
│   ├── __init__.py
│   ├── test_smoke.py
│   ├── test_modifiers.py
│   ├── test_modes.py
│   └── test_rapid.py
└── README.md
```

### 4.2 Implement adb.py
- `tap(x, y)` — `adb shell input tap`
- `clear_logcat()` — `adb logcat -c`
- `capture_logcat(tag, timeout)` — `adb logcat -d -s TAG:*`, parse output
- `get_text_field_content()` — `adb shell dumpsys` to read focused field text
- Device serial configurable via env var or arg

### 4.3 Implement keyboard.py
- `load_key_map(device)` — trigger `KeyMapGenerator` dump, parse logcat for coordinates
- `calibrate_y_offset(device)` — tap a known key, compare expected vs actual logcat, compute offset
- `tap_key(device, key_name)` — look up coordinates, apply offset, tap
- `tap_key_by_code(device, code)` — same but by keycode

### 4.4 Implement test files
- Each test file has functions following `test_*` naming
- `e2e_runner.py` discovers and runs all tests, prints pass/fail summary
- Tests use `assert_logcat_contains(tag, pattern)` style assertions

### 4.5 Write README
- Prerequisites (Python 3, ADB, emulator running, DevKey installed and active)
- Usage: `python e2e_runner.py [--device SERIAL] [--test TEST_NAME]`
- How to add new tests
- Coordinate calibration explanation

## Phase 5: Verify and commit

### 5.1 Full verification
- `./gradlew test` — all unit tests pass
- `./gradlew connectedAndroidTest` — all Compose UI tests pass (requires running emulator)
- `python tools/e2e/e2e_runner.py` — all ADB tests pass
- Manual: tap 123 on emulator, confirm symbols layout appears

### 5.2 Commit
- Commit 1: "Fix 123 mode switch with StateFlow extraction" (KeyboardModeManager + DevKeyKeyboard changes + unit tests)
- Commit 2: "Add Compose UI test infrastructure" (test deps + test tags + test files)
- Commit 3: "Add ADB E2E test harness" (tools/e2e/)
