# IME Testing Patterns Reference

Guide for test-wave-agent on testing an Input Method Editor (keyboard) via ADB. Unlike normal app testing, an IME has no visible Activity — it renders as a system overlay when a text field has focus.

## IME vs Normal App Testing

| Aspect | Normal App | IME (Keyboard) |
|--------|-----------|----------------|
| Launch | `am start -n package/Activity` | `ime set package/.Service` + open text field |
| UI interaction | Tap UI elements by resource-id | Tap key coordinates (no resource-ids in IME overlay) |
| Verification | UIAutomator XML + screenshots | **Logcat tags** + screenshots |
| State | Visible in view hierarchy | Internal state machine (modifiers, modes) — observed via logcat |
| Process lifecycle | Starts/stops with activity | Persists across apps, survives `install -r` |
| Crashes | App visibly closes | Keyboard silently disappears, text field loses input |

## Key Map Coordinate System

### How Key Positions Are Determined

Debug builds of DevKey emit key coordinates via the `DevKeyMap` logcat tag when the keyboard is first rendered. The output format:

```
DevKeyMap: key=a code=97 cx=54 cy=1592 row=1
DevKeyMap: key=Shift code=-1 cx=36 cy=1899 row=3
DevKeyMap: key=123 code=-2 cx=54 cy=2053 row=4
```

Fields:
- `key`: Display label
- `code`: Primary keycode (ASCII for letters, negative for special keys)
- `cx`, `cy`: Center coordinates in pixels (within keyboard view)
- `row`: Row index (0 = top)

### Y Offset Calibration

The `cy` values from `DevKeyMap` are relative to the keyboard view's internal coordinate space. The actual screen position depends on:
- Status bar height
- Navigation bar
- Text field / app content above the keyboard

On `emulator-5554` (1080x2400, density 2.625), the empirical Y offset is **-153px**. This means:
```
actual_screen_Y = DevKeyMap_cy - 153
```

### Corrected Row Y Coordinates (FULL mode, emulator-5554)

| Row | Content | Corrected Y |
|-----|---------|-------------|
| 0 | Number row (1-0) | 1446 |
| 1 | QWERTY row | 1592 |
| 2 | Home row (ASDF...) | 1746 |
| 3 | Z row (Shift, Z-M, Backspace) | 1899 |
| 4 | Space row (123, Ctrl, Alt, Space, ...) | 2053 |
| 5 | Utility row (Esc, Tab, arrows) | 2190 |

### Layout Modes

DevKey has 3 layout modes. The active mode determines which rows and keys are present:

| Mode | Rows | Description |
|------|------|-------------|
| FULL | 6 | Number row + QWERTY + Home + Z + Space + Utility (Esc, Tab, arrows) |
| COMPACT | 4 | QWERTY + Home + Z + Space (SwiftKey-style) |
| COMPACT_DEV | 4 | Like COMPACT but number keys on long-press of top row |

## Special Key Codes

| Key | Code | Notes |
|-----|------|-------|
| Shift | -1 | Modifier: OFF → ONE_SHOT → LOCKED → OFF cycle |
| 123/Symbols | -2 | Mode toggle: Normal ↔ Symbols |
| ABC/Alpha | -200 | Return to Normal mode from Symbols |
| Ctrl | -6 | Modifier: same cycle as Shift |
| Alt | -7 | Modifier: same cycle as Shift |
| Meta | -8 | Modifier: same cycle as Shift |
| Backspace | -5 | Smart backspace (word-delete with modifier) |
| Enter | 10 | ASCII newline (NOT KeyEvent.KEYCODE_ENTER) |
| Tab | 9 | ASCII tab |
| Space | 32 | ASCII space |
| Escape | 27 | ASCII escape |

**IMPORTANT**: DevKey's internal keycodes use ASCII values, NOT Android `KeyEvent.KEYCODE_*` constants. Tab is `9` (not `61`), Enter is `10` (not `66`).

## Modifier State Machine

Each modifier (Shift, Ctrl, Alt, Meta) follows this state machine:

```
OFF ──tap──> ONE_SHOT ──tap──> LOCKED ──tap──> OFF
         └──hold──> HELD ──release──> OFF
         └──chord──> CHORDING ──release──> OFF
```

- **ONE_SHOT**: Next non-modifier key consumes the modifier, then returns to OFF
- **LOCKED**: Stays active until explicitly tapped again
- **HELD**: Active while physically held down (multitouch)
- **CHORDING**: Modifier held + another key pressed simultaneously

### Double-Tap Detection

Double-tap (ONE_SHOT → LOCKED) requires the second tap within 400ms of the first. The `KeyView` event flow is always:
```
onModifierDown → onModifierUp → onModifierTap
```
The down/up cycle resets state before tap runs. `ModifierStateManager` uses `stateBeforeDown` tracking to detect double-tap from the saved pre-down state.

### Logcat Verification for Modifiers

```
DevKeyPress: ModifierTransition SHIFT: OFF -> ONE_SHOT
DevKeyPress: ModifierTransition SHIFT: ONE_SHOT -> LOCKED
DevKeyPress: tap code=97 label=a shift=true ctrl=false alt=false
```

## Compose Recomposition Verification

The keyboard UI uses Jetpack Compose. Mode switches (Normal ↔ Symbols) are driven by `KeyboardModeManager` StateFlow. If the lifecycle owner on the decor view is not RESUMED, recomposition silently never runs.

### Signs of broken recomposition:
- Logcat shows `toggleMode: Normal -> Symbols` but screenshot still shows QWERTY layout
- Keys respond to taps (logcat events fire) but visual state doesn't change
- Initial render is correct, but no state changes take effect

### Verification approach:
1. **Logcat** confirms the state transition happened internally
2. **Screenshot** confirms the visual change rendered
3. Both must agree — logcat PASS + screenshot FAIL indicates a Compose lifecycle issue

## Testing Flows That Require State Setup

### Testing Shift + Letter
```
1. Clear logcat
2. Tap Shift key → verify ONE_SHOT in logcat
3. Tap 'a' → verify logcat shows shift=true, code=97
4. Tap 'a' again → verify logcat shows shift=false (consumed)
```

### Testing Caps Lock
```
1. Clear logcat
2. Tap Shift key → ONE_SHOT
3. Tap Shift key again within 400ms → LOCKED
4. Tap 'a', 'b', 'c' → all should show shift=true
5. Tap Shift → OFF
6. Tap 'a' → shift=false
```

### Testing Ctrl+C (Modifier Combo)
```
1. Clear logcat
2. Tap Ctrl key → ONE_SHOT
3. Tap 'c' → verify logcat shows ctrl=true, code=99
4. Verify LatinIME sends KeyEvent with META_CTRL_ON
```

### Testing Mode Round-Trip
```
1. Clear logcat
2. Verify QWERTY layout visible (screenshot)
3. Tap 123 → verify logcat "toggleMode: Normal -> Symbols"
4. Verify Symbols layout visible (screenshot)
5. Tap ABC → verify logcat "setMode: Normal"
6. Verify QWERTY layout visible again (screenshot)
```

## Known Issues

### connectedAndroidTest uninstalls the app
Running `./gradlew connectedAndroidTest` removes the debug APK after tests complete. Must reinstall + `ime enable` + `ime set` after.

### Espresso incompatible with API 36
Compose UI instrumented tests fail on Android 16 emulators due to `InputManager.getInstance()` reflection removal. Use ADB-based E2E testing instead.

### Tab key moves focus
Tapping Tab via `adb shell input keyevent KEYCODE_TAB` moves focus away from the text field, closing the keyboard. To test Tab key output, tap it on the keyboard overlay and verify via logcat only.

### Keyboard process survives APK replacement
`adb install -r` does NOT restart the IME process. Old code continues running until `am force-stop` + `ime set`.
