# 123 Mode Switch Fix + E2E Test Harness Design

**Date**: 2026-03-03
**Status**: Approved

## Problem Statement

### 123 Mode Switch (P1 Blocker)
Tapping the 123 key fires correctly (logcat confirms `toggleMode: Normal -> Symbols`) but the keyboard layout does not change. The `mutableStateOf<KeyboardMode>` write inside a `pointerInput` gesture handler does not trigger Compose recomposition. The `when (keyboardMode)` block that selects `activeLayout` never re-evaluates.

Evidence: the only confirmation is a `Log.d` inside `toggleMode()` *before* the assignment. The actual `MutableState.value` write has not been verified to propagate.

### E2E Test Harness
Testing is currently ad-hoc ADB commands with manual logcat inspection. No automated regression tests exist. Modifier combos with target apps (Termux, Moonlight, Chrome Remote Desktop) need a repeatable verification path.

## Design

### Part 1: 123 Mode Switch Fix — StateFlow Extraction

**Root cause hypothesis**: `mutableStateOf` writes from within `pointerInput`'s `detectTapGestures` coroutine scope may not propagate through Compose's snapshot system to trigger recomposition.

**Fix**: Replace `mutableStateOf<KeyboardMode>` with a `MutableStateFlow<KeyboardMode>` read via `collectAsState()`. This is the same pattern already proven to work for modifier state (`ModifierStateManager` uses `StateFlow` and recomposes reliably from `pointerInput` handlers).

#### KeyboardModeManager (new class)

New file: `core/KeyboardModeManager.kt`

```kotlin
class KeyboardModeManager {
    private val _mode = MutableStateFlow(KeyboardMode.Normal)
    val mode: StateFlow<KeyboardMode> = _mode.asStateFlow()

    fun toggleMode(target: KeyboardMode) {
        _mode.value = if (_mode.value == target) KeyboardMode.Normal else target
    }

    fun setMode(mode: KeyboardMode) {
        _mode.value = mode
    }
}
```

#### DevKeyKeyboard.kt changes

- Replace `var keyboardMode by remember { mutableStateOf<KeyboardMode>(KeyboardMode.Normal) }` with:
  ```kotlin
  val modeManager = remember { KeyboardModeManager() }
  val keyboardMode by modeManager.mode.collectAsState()
  ```
- Replace all `keyboardMode = X` with `modeManager.setMode(X)`
- Replace all `toggleMode(X)` with `modeManager.toggleMode(X)`
- Remove local `toggleMode` function

#### Diagnostic step (before fix, to confirm root cause)

Add a temporary `LaunchedEffect` that logs `snapshotFlow { keyboardMode }` to verify whether the `mutableStateOf` value actually changes post-write. Remove after confirmation.

### Part 2: E2E Test Harness — Tier 1 (Compose UI Tests)

Fast, deterministic, in-process tests using `androidTest` with Compose testing APIs. No emulator coordinates, no ADB.

#### Coverage

1. Mode switching: 123 → Symbols → ABC → Normal
2. Modifier state transitions: one-shot, lock, chording, auto-consume
3. Layout correctness: row/key counts per LayoutMode
4. Key action dispatch: tap → callback verification with correct codes
5. Modifier combos: Ctrl+letter → bridge receives ctrl=true

#### Structure

```
app/src/androidTest/java/dev/devkey/keyboard/ui/keyboard/
├── ModeSwitchTest.kt
├── ModifierStateTest.kt
├── KeyActionDispatchTest.kt
└── LayoutRenderTest.kt
```

#### Test approach

- Each test creates `DevKeyKeyboard` with a mock `KeyboardActionListener`
- Keys found by test tags (`"key_${primaryCode}"`) added to `KeyView`'s `Modifier`
- Assertions check mock listener captures and visible UI state
- No coordinate dependency

### Part 3: E2E Test Harness — Tier 2 (ADB Script Harness)

On-device integration tests verifying the full pipeline: screen tap → KeyView → Bridge → LatinIME → InputConnection → target app.

#### Coverage

1. Smoke: type "hello", verify text
2. Modifier combos: Shift+a, Ctrl+a, double-tap Shift
3. Mode switching: 123 toggle via logcat layout change event
4. Rapid typing: 16-key burst, zero dropped keys
5. Target app verification: semi-automated (human sets up app, script handles key sequence + logcat)

#### Structure

```
tools/e2e/
├── e2e_runner.py
├── lib/
│   ├── adb.py
│   └── keyboard.py
├── tests/
│   ├── test_smoke.py
│   ├── test_modifiers.py
│   ├── test_modes.py
│   └── test_rapid.py
└── README.md
```

#### Key decisions

- **Coordinate map from KeyMapGenerator**: parsed from logcat dump, not hardcoded. Adapts to screen size.
- **Logcat as assertion layer**: parse `DevKeyPress` tags for key codes, modifier state, bridge output. No OCR.
- **Auto-calibration**: runner taps a known key first, detects Y offset from logcat, stores for session.
- **Python**: `subprocess` for ADB, structured assertions, report generation.
