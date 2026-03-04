# End-to-End Testing Observations

**Date**: 2026-03-02 (Session 25)
**Target**: emulator-5554 (1080x2400, density 2.625)
**Build**: Post Java→Kotlin migration + crash fix

## Status: FULL MODE COMPLETE — 2 bugs found, COMPACT/COMPACT_DEV pending

## Build & Install
- [x] Build debug APK — BUILD SUCCESSFUL
- [x] Install on emulator — Success
- [x] Verify keyboard appears in settings — Listed in `ime list -s`
- [x] Enable DevKey as input method — `dev.devkey.keyboard/.LatinIME`
- [x] Select DevKey as active IME — Confirmed via `dumpsys input_method`

## Critical Bug Found & Fixed

### Crash: `lateinit property sKeyboardSettings has not been initialized`
- **Root cause**: `LatinIME.onCreate()` called `KeyboardSwitcher.init()` BEFORE initializing `sKeyboardSettings`
- **Fix**: Moved `sKeyboardSettings = SettingsRepository(settingsPrefs)` BEFORE `KeyboardSwitcher.init(this)` in `LatinIME.kt:263-272`
- **Status**: FIXED, rebuild successful, no more crashes
- **5 crash loop** observed before fix (PIDs 6483, 6564, 6733, 6759, 6788)

## Coordinate Calibration

### Calculated vs Actual Y Offset
The key coordinate agent calculated positions assuming keyboard starts at y=1440 (40% of 2400).
**Actual offset: -153px** — keyboard starts ~153px higher than calculated.

### Corrected FULL Mode Y Coordinates (subtract 153 from calculated)
| Row | Calculated Y | Actual Y |
|-----|-------------|----------|
| Number (` 1-9 0) | 1599 | 1446 |
| QWERTY (q-p) | 1745 | 1592 |
| Home (a-l) | 1899 | 1746 |
| Z row (Shift, z-m, ⌫) | 2052 | 1899 |
| Space row (123, emoji, ',', space, '.', Enter) | 2206 | 2053 |
| Utility (Ctrl, Alt, Tab, arrows) | ~2340 | ~2187 |

### X Coordinates (no offset needed)
X coordinates from the calculated key-coordinates.md are accurate. See `.claude/logs/key-coordinates.md` for full table.

## Key Testing Matrix

### Verified Keys (via "hello" test)
| Key | Code | Tap X | Tap Y | Logcat | Status |
|-----|------|-------|-------|--------|--------|
| h | 104 | 659 | 1746 | TAP label=h code=104 | PASS |
| e | 101 | 273 | 1592 | TAP label=e code=101 | PASS |
| l | 108 | 1016 | 1746 | TAP label=l code=108 | PASS |
| o | 111 | 914 | 1592 | TAP label=o code=111 | PASS |

### Bridge Verified (all keys above)
- `BRIDGE raw=104 eff=104 shift=false ctrl=false alt=false` — correct for lowercase
- No modifier contamination observed
- Key press timing: ~370ms between taps (includes ADB overhead)

### Full Key Matrix (Session 26 — ALL 44 keys tested)

#### Number Row (11/11 PASS)
| Key | Code | X | Y | Status |
|-----|------|---|---|--------|
| ` | 96 | 54 | 1446 | PASS |
| 1 | 49 | 151 | 1446 | PASS |
| 2 | 50 | 248 | 1446 | PASS |
| 3 | 51 | 346 | 1446 | PASS |
| 4 | 52 | 443 | 1446 | PASS |
| 5 | 53 | 540 | 1446 | PASS |
| 6 | 54 | 637 | 1446 | PASS |
| 7 | 55 | 735 | 1446 | PASS |
| 8 | 56 | 832 | 1446 | PASS |
| 9 | 57 | 929 | 1446 | PASS |
| 0 | 48 | 1027 | 1446 | PASS |

#### QWERTY Row (10/10 PASS)
| Key | Code | X | Y | Status |
|-----|------|---|---|--------|
| q | 113 | 59 | 1592 | PASS |
| w | 119 | 166 | 1592 | PASS |
| e | 101 | 273 | 1592 | PASS |
| r | 114 | 380 | 1592 | PASS |
| t | 116 | 487 | 1592 | PASS |
| y | 121 | 594 | 1592 | PASS |
| u | 117 | 700 | 1592 | PASS |
| i | 105 | 807 | 1592 | PASS |
| o | 111 | 914 | 1592 | PASS |
| p | 112 | 1021 | 1592 | PASS |

#### Home Row (9/9 PASS)
| Key | Code | X | Y | Status |
|-----|------|---|---|--------|
| a | 97 | 65 | 1746 | PASS |
| s | 115 | 183 | 1746 | PASS |
| d | 100 | 302 | 1746 | PASS |
| f | 102 | 421 | 1746 | PASS |
| g | 103 | 540 | 1746 | PASS |
| h | 104 | 659 | 1746 | PASS |
| j | 106 | 778 | 1746 | PASS |
| k | 107 | 897 | 1746 | PASS |
| l | 108 | 1016 | 1746 | PASS |

#### Z Row Letters (7/7 PASS)
| Key | Code | X | Y | Status |
|-----|------|---|---|--------|
| z | 122 | 216 | 1899 | PASS |
| x | 120 | 324 | 1899 | PASS |
| c | 99 | 432 | 1899 | PASS |
| v | 118 | 540 | 1899 | PASS |
| b | 98 | 648 | 1899 | PASS |
| n | 110 | 756 | 1899 | PASS |
| m | 109 | 864 | 1899 | PASS |

#### Special Keys (7/7 PASS)
| Key | Code | X | Y | Status |
|-----|------|---|---|--------|
| Shift | -1 | 84 | 1899 | PASS |
| Backspace | -301 | 997 | 1899 | PASS |
| Space | 32 | 556 | 2053 | PASS |
| Enter | 10 | 984 | 2053 | PASS |
| Comma | 44 | 277 | 2053 | PASS |
| Period | 46 | 835 | 2053 | PASS |
| 123 | -2 | 66 | 2053 | PASS |

### Modifier & Functional Tests (Session 26)

#### Shift + Letter (One-Shot): PASS
| Test | Result | Details |
|------|--------|---------|
| Shift+a | PASS | shift=true, raw=97, eff=65 ('A') |
| Shift+h | PASS | shift=true, raw=104, eff=72 ('H') |
| Shift+z | PASS | shift=true, raw=122, eff=90 ('Z') |
| Auto-release | PASS | Next key after Shift+letter shows shift=false |

#### Caps Lock (Double-tap Shift): **FAIL** — BUG
- Double-tap Shift (110ms gap): Did NOT enter LOCKED state
- Long-press Shift (800ms): Also only produced ONE_SHOT
- **Root cause**: `ModifierStateManager.onModifierDown()` line ~126 converts ONE_SHOT→HELD before `onModifierTap()` can detect double-tap and transition to LOCKED
- **File**: `app/src/main/java/dev/devkey/keyboard/core/ModifierStateManager.kt`
- **Screenshot**: `.claude/screenshots/symbols-mode-test.png` (shows only one-shot uppercase)

#### 123 Mode Switching: **FAIL** — BUG
- 123 key fires correctly (code=-2, type=TOGGLE)
- Layout does NOT switch — alpha keys still produce letters after toggle
- Compose keyboard layout does not respond to mode change event
- **Screenshot**: `.claude/screenshots/bug-123-mode-no-switch.png`

#### Utility Row (7/7 PASS)
| Key | Code | X | Y | Status |
|-----|------|---|---|--------|
| Ctrl | -113 | 82 | 2190 | PASS |
| Alt | -57 | 236 | 2190 | PASS |
| Tab | 9 | 391 | 2190 | PASS |
| ArrowLeft | -21 | 698 | 2190 | PASS |
| ArrowDown | -20 | 823 | 2190 | PASS |
| ArrowUp | -19 | 949 | 2190 | PASS |
| ArrowRight | -22 | 1023 | 2190 | PASS |

#### Ctrl Combos: PASS
- Ctrl+A: ctrl=true in BRIDGE, one-shot Ctrl consumed correctly
- **Screenshot**: `.claude/screenshots/ctrl-a-test.png`

### Remaining Tests (PENDING)
- [ ] COMPACT mode key test
- [ ] COMPACT_DEV mode key test + long-press numbers
- [ ] Long-press behavior (all modes)
- [ ] Ctrl+C/V/X/Z combos
- [ ] Alt combos

## Logcat Analysis

### DevKeyPress Tags: WORKING
- DOWN, TAP, BRIDGE, IME, UP events all logging correctly
- Example: `DOWN label=h code=104 type=LETTER` → `TAP label=h code=104` → `BRIDGE raw=104 eff=104 shift=false ctrl=false alt=false` → `IME code=104 x=-1 y=-1` → `UP label=h code=104`

### DevKeyMap Tags: NOT TESTED YET
### Error/Warning Patterns: NONE (post-fix)

## Performance Observations

### Session 25 (Initial)
- Key press latency: ~7ms from DOWN to UP (per logcat timestamps)
- Bridge processing: <1ms (same millisecond as TAP)
- ADB tap-to-tap interval: ~370ms (dominated by ADB overhead, not keyboard)
- ANR/crash: NONE after fix
- Memory: PID 6891, 130MB RSS (normal for IME + Compose)

### Session 26 (Rapid Typing Test)
- Test phrase: "thequickbrownfox" (16 keys, 0.1s ADB delay)
- Keys typed: 16/16 — all correct, zero missed
- DOWN→UP latency: min=5ms, max=9ms, avg=6.8ms
- Key-to-key interval: min=169ms, max=177ms, avg=171.5ms
- Effective speed: ~6 keys/sec (~70 WPM equivalent)
- Total time: 2581ms for 16 keys
- ANR warnings: NONE

## IME State (from dumpsys)
```
mCurId=dev.devkey.keyboard/.LatinIME
mBoundToMethod=true
mInputShown=true (when text field focused)
mSystemReady=true
```

## Issues Found

### 1. FIXED: Initialization order crash (P0)
- `sKeyboardSettings` must be initialized before `KeyboardSwitcher.init()`
- Fixed in LatinIME.kt, needs commit

### 2. OBSERVATION: Keyboard shows in FULL mode (not COMPACT)
- The keyboard defaults to FULL mode (6 rows + toolbar) on the emulator
- This includes number row, QWERTY, home, Z, space, utility (Ctrl/Alt/Tab/arrows)
- Need to verify COMPACT and COMPACT_DEV modes separately

### 3. OBSERVATION: Text field shows "khello"
- "k" from miscalibrated first test attempt, "hello" from corrected test
- Confirms text input pipeline works end-to-end: tap → KeyView → Bridge → LatinIME → InputConnection

### 4. BUG: Caps Lock unreachable (P1)
- Double-tap Shift does NOT enter LOCKED state
- `ModifierStateManager.onModifierDown()` overwrites ONE_SHOT→HELD before `onModifierTap()` can detect double-tap
- **File**: `app/src/main/java/dev/devkey/keyboard/core/ModifierStateManager.kt` lines ~123-147
- **Fix**: Track previous state separately, or don't transition ONE_SHOT→HELD in onModifierDown
- **Screenshot**: `.claude/screenshots/symbols-mode-test.png`

### 5. BUG: 123 mode switching does not change layout (P1)
- 123 key fires correctly (code=-2, type=TOGGLE) in logcat
- Compose keyboard layout does NOT respond — alpha keys still produce letters
- Layout switching likely not wired up in Compose keyboard views
- **Screenshot**: `.claude/screenshots/bug-123-mode-no-switch.png`

## Raw Test Log

### 19:47 — First keyboard appearance
Keyboard visible after crash fix. FULL mode layout confirmed:
- Toolbar: clipboard, voice, 123, lightning, ...
- 6 key rows visible
- Teal theme applied

### 19:48 — Miscalibrated test (Y offset not yet known)
Tapped at calculated coordinates. Results revealed systematic Y offset:
- (659, 1899) → expected h, got b (Z row instead of home row)
- (273, 1745) → expected e, got d (home row instead of QWERTY)
- Determined offset: actual positions are 153px higher than calculated

### 19:50 — Calibrated test: "hello" PASS
Applied -153px Y offset. All 5 keys correct:
- h(659,1746)=104 ✓, e(273,1592)=101 ✓, l(1016,1746)=108 ✓, l(1016,1746)=108 ✓, o(914,1592)=111 ✓
- Text appeared in field as "khello" (includes earlier miscalibrated "k")
