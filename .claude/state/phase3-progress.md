# Phase 3 Regression Gate — In-Flight Progress

**Plan:** `.claude/plans/2026-04-09-pre-release-phase3.md`
**Spec:** `.claude/specs/2026-04-08-pre-release-vision-spec.md` (§6 Phase 3)
**Started:** 2026-04-09
**Status:** IN PROGRESS — Phase 1 done, Phase 2 FULL tier at 9p/7f/13e/7s of 36.

---

## Current FULL tier baseline (run-3)

```
Results: 9 passed, 7 failed, 13 errors, 7 skipped (out of 36 tests)
```

Artifacts in `.claude/test-results/2026-04-09_phase3_gate/full/`:
- `run-1.log` — initial red surface before any fixes
- `run-2.log` — after Chrome URL bar was focused
- `run-3.log` — after Bucket 1 harness fixes + Bucket 2 instrumentation
- `run-after-bucket12.log` — copy of run-3

---

## Completed work (committed)

### Phase 1 — Harness Unblock (B1/B2)
- `tools/e2e/e2e_runner.py` — `_PytestSkipped` sentinel + 4-count `run_tests` signature
- `tools/e2e/requirements.txt` — `pytest>=7.0`
- `.claude/test-results/2026-04-09_phase3_gate/README.md` — privacy banner

### Phase 3 infra — issue #24 (meta)
Eight infrastructure defects discovered and patched in-place because none of
the Phase 2 test harness could reach the driver on API 36 Pixel 7 emulator:

1. **Port 3947 occupied by jcodemunch** — migrated DevKey driver + harness
   + tests to port **3948** in all reference sites (server.js, driver.py,
   e2e_runner.py, DevKeyLoggerTest.kt, LatinIME.kt comment).
2. **server.js bound `127.0.0.1` only** — changed `server.listen(PORT, '0.0.0.0', ...)`
   so emulator NAT (10.0.2.2) can reach host.
3. **`RECEIVER_NOT_EXPORTED` blocks shell broadcasts on API 36** —
   `dumpsys activity broadcasts` shows stuck BroadcastRecords with
   `dispatchTime=--` when `uid=2000` (shell) targets NOT_EXPORTED receivers.
   Flipped three debug-only receivers in `LatinIME.kt` to
   `RECEIVER_EXPORTED` under the existing `isDebugBuild` guard:
   `DUMP_KEY_MAP`, `ENABLE_DEBUG_SERVER`, `SET_LAYOUT_MODE`.
4. **Missing `INTERNET` permission in debug build** — created
   `app/src/debug/AndroidManifest.xml` with `INTERNET` + `usesCleartextTraffic`.
5. **`DevKeyLogger.sendToServer` silently swallowed exceptions** — added
   `Log.w("DevKey/SRV", ...)` surfacing the error class + message so Phase 3
   gate can diagnose future forwarding failures.
6. **`connectTimeout = 1000` too aggressive for emulator NAT path** —
   bumped to 5000ms.
7. **IME in doze/background network restriction** (runtime only, no code
   change) — harness preflight must run:
   ```
   adb shell cmd deviceidle whitelist +dev.devkey.keyboard
   adb shell cmd netpolicy set restrict-background false
   ```
8. **`tools/debug-server/server.js` has no serial pinning for multi-device
   hosts** — launch with `ADB_SERIAL=emulator-5554 node tools/debug-server/server.js`.
   Long-term: driver should auto-detect single emulator or accept a CLI flag.

### Phase 3 Bucket 1 — harness fixes
- **#19** `tools/e2e/tests/test_smoke.py` — regex `tap.*a.*97` → `TAP\s+label=a\s+code=97`
- **#20** `tools/e2e/tests/test_modes.py` — regex `toggleMode.*Normal.*Symbols` → `setMode:.*(Normal.*Symbols|Symbols.*Normal)`
- **#21** `tools/e2e/lib/keyboard.py` — added `clear_key_map_cache()` and
  auto-clear inside `set_layout_mode()` so stale coordinates don't leak
  across tier switches
- **#22** `tools/e2e/tests/test_long_press.py::test_long_press_every_key_symbols`
  — replaced with `pytest.skip(...)` (symbols is not a valid layout mode)

### Phase 3 Bucket 2 — missing instrumentation
- **#14** `long_press_fired` — turned out to already exist in
  `KeyPressLogger.logLongPress` line 23; was only invisible to the driver
  because of the infrastructure chain from issue #24. End-to-end validated
  via `adb shell input swipe 64 1803 64 1803 500` → `{"DevKey/TXT":1}`.
- **#15** `key_event` with modifier state — added
  `DevKeyLogger.text("key_event", mapOf("code" to primaryCode, "shift"/"ctrl"/"alt"/"meta" to ...))`
  in `LatinIME.onKey` at the structural choke-point (the same site that
  already logs `DevKeyPress IME code=N x=X y=Y`). Validated — driver
  category histogram shows `{"DevKey/TXT":2}` after two 'a' taps.
- **#17** `ModifierTransition` — added `logTransition(...)` helper in
  `ModifierStateManager.kt` that emits BOTH the legacy
  `DevKeyPress: ModifierTransition SHIFT tap OFF -> ONE_SHOT` format (for
  the `capture_logcat` harness path) AND a structured
  `DevKey/MOD modifier_transition {type, action, from, to}` (for the
  driver `/wait` path). Wired into `onModifierTap`, `onModifierDown`,
  `onModifierUp`, `onOtherKeyPressed`, `consumeOneShot`. Validated via
  shift-key tap:
  ```
  DevKeyPress: ModifierTransition SHIFT down OFF -> HELD
  DevKeyPress: ModifierTransition SHIFT up HELD -> OFF
  DevKeyPress: ModifierTransition SHIFT tap OFF -> ONE_SHOT
  {category:"DevKey/MOD", ..., data:{type:SHIFT,action:tap,from:OFF,to:ONE_SHOT}}
  ```
- **#16** `next_word_suggestions` not forwarded — infrastructure part
  resolved via issue #24 chain. Remaining work: the event currently only
  fires at IME init, not on per-keystroke suggestion compute. Needs
  additional emission sites.

---

## GitHub Issues filed

| # | Title | Bucket | Status |
|---|---|---|---|
| #14 | long_press_fired structured event | 2 | Fixed (infra chain) |
| #15 | key_event structured event with modifier state | 2 | Fixed |
| #16 | next_word_suggestions not forwarded | 2 | Infra fixed; emission gaps remain |
| #17 | ModifierTransition log lines | 2 | Fixed |
| #18 | Voice key missing from KeyMapGenerator dump | 3 | Open |
| #19 | test_smoke regex case sensitivity | 1 | Fixed |
| #20 | test_modes regex format | 1 | Fixed |
| #21 | _key_map stale cache | 1 | Fixed |
| #22 | test_long_press_every_key_symbols invalid mode | 1 | Fixed |
| #23 | Visual SSIM 0.44 compact-dark regression | 3 | Open |
| #24 | Phase 2 driver-server infra never verified on API 36 | Meta | Fixed |

---

## TODO — remaining work for green gate

### Still failing after Bucket 1+2 fixes (run-3 breakdown)

1. **`test_long_press.test_long_press_every_key_{compact,compact_dev,full}`**
   (3 ERROR, DriverTimeout). Instrumentation confirmed working manually
   via `adb shell input swipe ... 500`, but harness invocation fails.
   Hypothesis: test race — `set_layout_mode` broadcasts recompose, the
   first long-press lands while keyboard is mid-recompose. Fix: have
   `_setup(mode)` call `driver.wait_for("DevKey/IME", "layout_mode_set",
   match={"mode":mode})` then re-dump key_map, THEN tap a no-op to warm
   the keyboard before starting the expectations loop.

2. **`test_modifiers.test_{shift,ctrl}_*`** (3 FAIL). Logcat shows the
   harness tapping `code=126` (tilde) instead of Shift. Root cause:
   `tap_key_by_code(-1)` maps to the key with `code=-1` in the dump,
   which is `Shift code=-1 x=83 y=1982` — correct. So the tap coords
   are right, BUT the shift key may not be getting touch-down/touch-up
   semantics correctly (the harness uses `input tap` which is a simple
   touch, not a modifier-tap). Fix: investigate `keyboard.tap_key_by_code`
   and whether it needs a different path for modifier keys. Alternative:
   use `adb shell input keyevent KEYCODE_SHIFT_LEFT`.

3. **`test_modifier_combos.test_ctrl_{a,c,v}`** (3 ERROR). key_event
   instrumentation fires now, but tests expect `ctrl:true` — which
   requires the Ctrl modifier state to be held BEFORE the 'a'/'c'/'v'
   tap. Same root cause as test_modifiers: harness isn't correctly
   activating modifier state before the letter tap.

4. **`test_modes.test_{abc_returns_to_normal,symbols_toggle_back}` +
   `test_rapid.test_rapid_mode_toggles`** (3 ERROR, KeyError -200).
   Symbols-mode key map never dumped. After switching to Symbols via
   123 key, harness needs to re-dump the key map. Fix: inside
   `test_modes.test_abc_returns_to_normal`, after `tap_key_by_code(-2)`
   (123 key to enter Symbols), call `keyboard.load_key_map(serial)` to
   refresh. Already attempted on line 52 but my cache-clear fix may
   break that call path. Verify.

5. **`test_modes.test_layout_mode_round_trip`** (1 ERROR, DriverTimeout
   on `layout_mode_set match={mode:compact}`). The broadcast fires but
   the IME-side log doesn't arrive in time. May be that the driver's
   `startIdx` filter excludes logs from BEFORE the wait_for call by a
   millisecond. Check timing.

6. **`test_next_word.*`** (3 ERROR, DriverTimeout on
   `next_word_suggestions`). Add `DevKeyLogger.text("next_word_suggestions",
   ...)` emission to the suggest-compute code path in `LatinIME.kt`
   (or `Suggest.kt`) on every keystroke that triggers a suggestion
   refresh. Current emission is only at IME init.

7. **`test_visual_diff.test_visual_compact_dark`** (1 FAIL, SSIM=0.45).
   Bucket 3 — either real visual regression or stale SwiftKey reference.
   Requires visual comparison of
   `tools/e2e/artifacts/compact-compact-dark.png` vs
   `.claude/test-flows/swiftkey-reference/compact-dark.png`.

8. **`test_voice.*`** (3 FAIL). Voice key code -102 missing from all
   KeyMapGenerator dumps. Investigate whether voice key is:
   - In the `DevKeyToolbar` (not part of KeyMapGenerator scope)
   - In a Full-mode toolbar that Compact doesn't show
   - Missing from `QwertyLayout` data entirely
   Fix depends on root cause.

### Skipped (spec-sanctioned, no action)

- `test_clipboard.test_clipboard_panel_opens` — clipboard toolbar key absent from compact top level
- `test_command_mode.test_command_mode_auto_enabled_on_terminal_focus` — no terminal-class package
- `test_macros.test_macros_panel_opens` — macros toolbar key absent
- `test_plugins.test_plugin_scan_complete_event_fires` — race window
- `test_visual_diff.test_visual_{full_dark,compact_dev_dark}` — B4 reference gap
- `test_long_press.test_long_press_every_key_symbols` — fixed: now skips

### Not yet attempted

- **Phase 2 COMPACT tier run** — waiting on FULL green
- **Phase 2 COMPACT_DEV tier run** — waiting on FULL green
- **Phase 2 sub-phase 2.5** — `tier-stabilization-status.md` update
- **Phase 3 sub-phases 3.1–3.5** — feature-flow gates
- **Phase 4** — lint gate (flip `abortOnError = true`)
- **Phase 5** — manual smoke
- **Phase 6** — decision gate commit

---

## How to resume

1. **Driver:**
   ```
   ADB_SERIAL=emulator-5554 node tools/debug-server/server.js
   ```

2. **Emulator preflight:**
   ```
   adb -s emulator-5554 shell cmd deviceidle whitelist +dev.devkey.keyboard
   adb -s emulator-5554 shell cmd netpolicy set restrict-background false
   adb -s emulator-5554 shell am force-stop dev.devkey.keyboard
   adb -s emulator-5554 shell ime set dev.devkey.keyboard/.LatinIME
   ```

3. **Open Chrome URL bar (to ensure keyboard has a focused EditText):**
   ```
   adb -s emulator-5554 shell am start -n com.android.chrome/com.google.android.apps.chrome.Main
   # Wait ~3s, then:
   adb -s emulator-5554 shell input tap 444 210
   ```

4. **Enable debug forwarding:**
   ```
   adb -s emulator-5554 shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://10.0.2.2:3948
   ```

5. **Run harness:**
   ```
   DEVKEY_DRIVER_URL=http://127.0.0.1:3948 \
     DEVKEY_DEVICE_SERIAL=emulator-5554 \
     ANDROID_SERIAL=emulator-5554 \
     DEVKEY_LAYOUT_MODE=full \
     python tools/e2e/e2e_runner.py
   ```

---

## Related artifacts

- Commits: see `git log --oneline` after this state file lands
- Test results: `.claude/test-results/2026-04-09_phase3_gate/full/`
- Checkpoint: `.claude/state/implement-checkpoint.json`
- Issues filed: https://github.com/RobertoChavez2433/DevKey/issues?q=label%3Adefect+created%3A2026-04-09
