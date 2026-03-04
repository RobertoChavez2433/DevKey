# Test Coordinate Calibration Design

**Date**: 2026-03-04
**Status**: Approved
**Scope**: Fix key coordinate drift for automated ADB testing

## Problem

Pre-computed Y coordinates in `key-coordinates.md` drifted ~130px from actual screen positions. The intended self-calibration mechanism (`DevKeyMap` logcat via `KeyMapGenerator.dumpToLogcat()`) is silent — it fires once via `LaunchedEffect(Unit)` and the View may not have valid screen coordinates at that point. Test agents have no reliable way to get accurate key positions.

## Solution: Three Changes

### 1. Update `key-coordinates.md` with Calibrated Values

Replace FULL mode Y values with Session 30 calibrated values:

| Row | Old Y | New Y |
|-----|-------|-------|
| Number | 1599 | 1475 |
| QWERTY | 1745 | 1605 |
| Home | 1899 | 1775 |
| Z | 2052 | 1925 |
| Space | 2206 | 2090 |
| Utility | 2343 | 2225 |

X values unchanged. Add note that runtime coordinates should come from DevKeyMap or `calibration.json`. Mark COMPACT/COMPACT_DEV as uncalibrated.

### 2. Fix DevKeyMap Logcat + Add Broadcast Trigger

**Root cause**: `LaunchedEffect(Unit)` fires once. `LocalView.current` may not have valid screen position after only 500ms.

**Changes**:

**DevKeyKeyboard.kt**:
- `LaunchedEffect(Unit)` → `LaunchedEffect(layoutMode)` — re-dumps on mode change
- Replace fixed 500ms delay with `view.isLaidOut` polling (max 2s timeout)

**KeyMapGenerator.kt**:
- Add `dumpToLogcatWhenReady()` suspend function with layout-wait polling

**LatinIME.kt** (debug builds only):
- Register `BroadcastReceiver` for `dev.devkey.keyboard.DUMP_KEY_MAP`
- On receive → call `KeyMapGenerator.dumpToLogcat()` with current view + layout mode
- Agent usage: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`

### 3. Calibration Cascade in `ime-setup` Flow

Replace "Read DevKeyMap logcat" step with a 3-tier cascade:

**Tier 1 — FAST (DevKeyMap logcat)**:
- Send broadcast: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`
- Wait 2s, read `DevKeyMap` logcat
- If ≥10 KEY lines found → parse, save to `calibration.json`, done

**Tier 2 — CACHE (calibration.json)**:
- Read `.claude/test-flows/calibration.json`
- If exists and matches device serial + layout mode → use cached coordinates, done

**Tier 3 — SLOW (Y-scan probe)**:
- Tap X=540 at Y=1400→2300 in 50px steps
- Read `DevKeyPress` logcat after each tap
- Group detected keys by Y proximity (±30px) into rows
- Save to `calibration.json` (overwriting existing), done

**calibration.json schema**:
```json
{
  "device": "emulator-5554",
  "screen": "1080x2400",
  "density": 2.625,
  "mode": "FULL",
  "timestamp": "2026-03-04T12:00:00Z",
  "source": "devkeymap|yscan",
  "rows": {
    "number":  { "y": 1475, "keys": {"1": 151, "2": 248} },
    "qwerty":  { "y": 1605, "keys": {"q": 59, "w": 166} },
    "home":    { "y": 1775, "keys": {"a": 65, "s": 183} },
    "zrow":    { "y": 1925, "keys": {"z": 216, "x": 324} },
    "space":   { "y": 2090, "keys": {"123": 66, "space": 556} },
    "utility": { "y": 2225, "keys": {"ctrl": 82, "alt": 236} }
  },
  "textfield": { "x": 540, "y": 1356 }
}
```

File is gitignored (device-specific).

## Files Changed

| File | Change |
|------|--------|
| `.claude/logs/key-coordinates.md` | Update FULL mode Y values, add runtime note |
| `app/.../ui/keyboard/DevKeyKeyboard.kt` | LaunchedEffect key + layout-wait logic |
| `app/.../debug/KeyMapGenerator.kt` | Add `dumpToLogcatWhenReady()` |
| `app/.../LatinIME.kt` | Broadcast receiver for DUMP_KEY_MAP |
| `.claude/test-flows/registry.md` | Update ime-setup with calibration cascade |
| `.claude/skills/test/SKILL.md` | Update coordinate system docs |
| `.claude/skills/test/references/adb-commands.md` | Add broadcast command |
| `.gitignore` | Add calibration.json |

## Testing

- Build debug APK, install on emulator
- Verify `adb logcat -s DevKeyMap:D` shows key coordinates on keyboard open
- Verify broadcast trigger: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`
- Run `/test --smoke` to verify ime-setup calibration cascade
- Run `/test --full` to verify all flows use correct coordinates
