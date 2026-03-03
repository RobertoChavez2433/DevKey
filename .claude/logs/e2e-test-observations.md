# End-to-End Testing Observations

**Date**: 2026-03-02 (Session 25)
**Target**: emulator-5554 (1080x2400, density 2.625)
**Build**: Post Java→Kotlin migration + crash fix

## Status: IN PROGRESS — Paused after initial calibration

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

### Remaining Keys (PENDING — continue next session)
- [ ] All 26 alphabet keys (a-z)
- [ ] All 10 number keys (0-9) + backtick
- [ ] Special keys: Space, Backspace, Enter, Shift, 123
- [ ] Modifier combos: Ctrl+A/C/V/X/Z, Shift+letter
- [ ] Mode switching: 123→symbols, ABC back
- [ ] Utility row: Ctrl, Alt, Tab, arrow keys
- [ ] Long-press behavior
- [ ] Caps lock (long-press Shift)

## Logcat Analysis

### DevKeyPress Tags: WORKING
- DOWN, TAP, BRIDGE, IME, UP events all logging correctly
- Example: `DOWN label=h code=104 type=LETTER` → `TAP label=h code=104` → `BRIDGE raw=104 eff=104 shift=false ctrl=false alt=false` → `IME code=104 x=-1 y=-1` → `UP label=h code=104`

### DevKeyMap Tags: NOT TESTED YET
### Error/Warning Patterns: NONE (post-fix)

## Performance Observations
- Key press latency: ~7ms from DOWN to UP (per logcat timestamps)
- Bridge processing: <1ms (same millisecond as TAP)
- ADB tap-to-tap interval: ~370ms (dominated by ADB overhead, not keyboard)
- ANR/crash: NONE after fix
- Memory: PID 6891, 130MB RSS (normal for IME + Compose)

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
