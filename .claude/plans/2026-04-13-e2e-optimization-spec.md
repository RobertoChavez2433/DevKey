# E2E Testing Optimization Spec

**Date**: 2026-04-13
**Status**: Draft — review only, no implementation yet
**Context**: Session 56 ran the full 171-test E2E suite 3+ times. Total wall clock for testing alone exceeded 3 hours. Root causes were identified late (run 2-3) when they could have been caught in seconds. This spec captures every lesson learned and proposes changes to the test skill, runner, harness, and rules.

---

## 1. Findings From This Session

### 1.1 Root Causes Discovered

| # | Root Cause | Tests Affected | Discovery Run | Could Have Been Found |
|---|-----------|---------------|--------------|----------------------|
| 1 | `RESET_KEYBOARD_MODE` after `set_layout_mode` invalidates key map | 13 punctuation, 5 input | Run 1 (60 min) | Pre-flight key map validation |
| 2 | Backspace code `-301` (SMART_BACK_ESC) only exists in compact_dev, not compact mode | 13 punctuation, 2 flat tests | Run 1 | Pre-flight: tap every key code used in tests |
| 3 | `RECORD_AUDIO` permission not granted on fresh emulator | All 13 voice state-transition tests | Run 2 (60 min) | Pre-flight: `pm dump | grep RECORD_AUDIO` |
| 4 | Long-press expectations file has wrong lp_code for compact 'j' | 1 long_press | Run 3 | Calibration pass before test run |
| 5 | Emulator GPU hang requiring full restart | All tests (run interrupted) | Between run 1 and 2 | Use `swiftshader_indirect` by default |
| 6 | 3000ms timeouts too aggressive for software-rendered emulator | 5-8 timing-sensitive tests | Run 2 | Adaptive timeouts based on rendering mode |

### 1.2 Time Budget Analysis (Run 3: 171 tests, ~50 min)

| Category | Count | Avg Time | Total | % |
|----------|-------|----------|-------|---|
| Punctuation (heavy setup) | 18 | 26s | 468s | 16% |
| Voice (audio round-trips) | 16 | 12s | 192s | 7% |
| Modifiers (ADB broadcast) | 20 | 10s | 200s | 7% |
| Input (composing + events) | 16 | 12s | 192s | 7% |
| Prediction (typing + wait) | 8 | 6s | 48s | 2% |
| Long press (swipe timing) | 4 | 45s | 180s | 6% |
| All others | 89 | 5s | 445s | 15% |
| **Setup overhead per test** | 171 | ~5s | **855s** | **29%** |
| **Sleep overhead (estimated)** | 171 | ~3s | **513s** | **17%** |

**Key insight**: Setup overhead (29%) + sleep overhead (17%) = **46% of total runtime is not testing anything**.

---

## 2. Proposed Changes to Test Skill (`/.claude/skills/test/SKILL.md`)

### 2.1 Add Pre-Flight Checklist (New Section)

Before running any tests, the skill should execute a pre-flight check:

```
## Pre-Flight Checklist

Before executing any test scope, verify all of the following. If any
check fails, report it immediately and stop — do not run 171 tests just
to discover a setup problem.

1. **Device connected**: `adb -s $SERIAL shell getprop sys.boot_completed` returns `1`
2. **IME installed**: `adb -s $SERIAL shell pm list packages | grep dev.devkey.keyboard`
3. **IME set as default**: `adb -s $SERIAL shell settings get secure default_input_method` contains `dev.devkey.keyboard`
4. **Permissions granted**: `adb -s $SERIAL shell dumpsys package dev.devkey.keyboard | grep "RECORD_AUDIO.*granted=true"`
5. **Debug server healthy**: `curl -s http://127.0.0.1:3950/health` returns `{"status":"ok"}`
6. **Key map loads**: Run `keyboard.load_key_map()` and verify `len(key_map) > 10`
7. **Keyboard visible**: `adb.ensure_keyboard_visible()` succeeds
8. **Voice model present**: Check logcat for "Whisper model loaded" after keyboard init (warn-only — voice tests may skip)
```

### 2.2 Add Feature-First Execution Strategy (New Section)

```
## Execution Strategy

### Scoped Runs Before Full Suite

When the user requests "run all tests" or "verify everything":

1. First run each feature in isolation using `--feature <name>`:
   voice, punctuation, input, prediction, autocorrect, modifiers,
   clipboard, macros, modes, command_mode
2. If any feature has >50% failure rate, stop and diagnose before
   running the full suite — a systemic issue is present.
3. Only run the full 171-test suite after all features pass in
   isolation.

### Failure Re-Run

After a full suite run with failures:

1. Categorize failures by error type (DriverTimeout, KeyError, AssertionError)
2. Re-run only the failing tests individually to distinguish flaky from broken
3. Fix root causes, then re-run only the affected feature, not the full suite
4. Only re-run the full suite as a final gate
```

### 2.3 Add Known Failure Patterns (New Section)

```
## Known Failure Patterns

### Pattern: Key Map Invalidation
- **Symptom**: KeyError for valid key codes, or key map has <10 entries
- **Root Cause**: `RESET_KEYBOARD_MODE` broadcast after `set_layout_mode`
  clears the Compose layout without reloading coordinates
- **Fix**: Never call `RESET_KEYBOARD_MODE` in `_setup()` after
  `set_layout_mode()`. The latter already handles reset + reload atomically.

### Pattern: Backspace Code Mismatch
- **Symptom**: KeyError for code -301 (SMART_BACK_ESC)
- **Root Cause**: `-301` only exists in compact_dev layout; compact layout
  uses label "Backspace" (code -5 / KEYCODE_DELETE)
- **Fix**: Use `keyboard.tap_key("Backspace", serial)` instead of
  `keyboard.tap_key_by_code(-301, serial)` in any test on compact mode

### Pattern: Voice Permission Missing
- **Symptom**: All voice state-transition tests fail with timeout waiting
  for LISTENING; file-based tests still pass
- **Root Cause**: RECORD_AUDIO is a dangerous permission requiring runtime
  grant; fresh emulator installs don't have it
- **Fix**: Grant via `pm grant` in `ensure_keyboard_visible()` or pre-flight

### Pattern: Flaky Ordering
- **Symptom**: Test passes individually but fails in full suite
- **Root Cause**: Prior test leaves keyboard in unexpected state (suggestions
  disabled, symbols mode active, composing buffer non-empty)
- **Fix**: Each `_setup()` must fully reset: `set_layout_mode("compact")`,
  enable suggestions, clear edit text, clear logs
```

---

## 3. Proposed Changes to Test Runner (`tools/e2e/e2e_runner.py`)

### 3.1 Add `--preflight` Flag
Run all 8 pre-flight checks before test discovery. Default: on.
Add `--no-preflight` to skip.

### 3.2 Add `--rerun-failed <results-file>` Flag
Read a prior results file and re-run only tests that failed/errored.

### 3.3 Add `--timeout-multiplier <float>` Flag
Multiply all `wait_for` timeouts by this factor. Default: 1.0.
Set to 1.5 or 2.0 for software-rendered emulators.

### 3.4 Add Results JSON Output
Write `results.json` alongside console output:
```json
{
  "timestamp": "2026-04-13T21:30:00",
  "device": "emulator-5554",
  "total": 171, "passed": 166, "failed": 2, "errors": 3,
  "failures": [
    {"name": "test_long_press.compact", "type": "FAIL", "duration": 40.2,
     "error": "lp_code=93, expected [61]"}
  ]
}
```

---

## 4. Proposed Changes to Harness Libraries

### 4.1 `lib/adb.py` — `ensure_keyboard_visible()`

Already done this session:
- [x] Add `pm grant dev.devkey.keyboard android.permission.RECORD_AUDIO`

Still needed:
- [ ] Return a status dict: `{"visible": bool, "permissions": {...}, "key_map_size": int}`
- [ ] Log warnings if any check fails rather than silently continuing

### 4.2 `lib/keyboard.py` — Remove Sleep Anti-Pattern

Replace blind sleeps with event-driven waits wherever the driver supports it:

| Current | Proposed |
|---------|----------|
| `time.sleep(0.3)` after broadcast | `driver.wait_for("DevKey/IME", event, timeout_ms=2000)` |
| `time.sleep(0.15)` between key taps | Keep (hardware constraint) but make configurable |
| `time.sleep(1.0)` after `load_key_map` | `driver.wait_for("DevKey/IME", "keymap_dump_complete")` |

### 4.3 `lib/driver.py` — Adaptive Timeouts

Add a module-level `TIMEOUT_MULTIPLIER` that all `wait_for` calls use:
```python
TIMEOUT_MULTIPLIER = float(os.environ.get("DEVKEY_TIMEOUT_MULTIPLIER", "1.0"))

def wait_for(..., timeout_ms=5000):
    effective_timeout = int(timeout_ms * TIMEOUT_MULTIPLIER)
    ...
```

### 4.4 Shared `_setup()` Template

Create `lib/setup.py` with a canonical setup function:
```python
def standard_setup(serial=None, layout="compact", suggestions=True, autocorrect="mild"):
    serial = serial or adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode(layout, serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    _clear_edit_text(serial)
    if suggestions:
        _enable_suggestions(serial)
    if autocorrect:
        _set_autocorrect(serial, autocorrect)
    return serial
```

This eliminates the duplicated 30-50 line `_setup()` in every test file and ensures consistent reset behavior.

---

## 5. Proposed Changes to Testing Rules (`/.claude/rules/testing-infra.md`)

### 5.1 Add E2E Anti-Patterns Section

```
## E2E Anti-Patterns

- Never call `RESET_KEYBOARD_MODE` in `_setup()` after `set_layout_mode()`.
- Never use key code `-301` (SMART_BACK_ESC) outside compact_dev mode.
  Use the label `"Backspace"` for portable backspace taps.
- Never assume permissions are pre-granted on a fresh emulator.
  The harness grants RECORD_AUDIO automatically; add new permissions
  to `ensure_keyboard_visible()` as needed.
- Never use `time.sleep()` where `driver.wait_for()` can confirm the
  same state transition.
- Never run the full 171-test suite as a first diagnostic step.
  Run the affected feature scope first.
```

### 5.2 Add Emulator Configuration Section

```
## Emulator Configuration

- Default GPU: `swiftshader_indirect` (reliable, no GPU hangs)
- Default port: 5554 (standard ADB port)
- Always pass `-no-boot-anim` for faster cold boot
- Audio: leave enabled (required for voice tests)
- After fresh install: grant RECORD_AUDIO, set IME, launch TestHostActivity
- After emulator crash: full restart + reinstall + permission re-grant
```

---

## 6. Proposed Changes to Long-Press Expectations

### 6.1 Calibration-Before-Run

Add a `--calibrate` flag to the runner that:
1. Enters each layout mode
2. Long-presses every key in the expectations file
3. Records actual lp_codes
4. Diffs against expectations and reports mismatches
5. Optionally auto-updates the expectations JSON

This prevents stale data from causing false failures.

---

## 7. Impact Estimate

| Change | Effort | Time Saved Per Session |
|--------|--------|----------------------|
| Pre-flight checklist | Small | 30-60 min (catches setup issues instantly) |
| Feature-first strategy | Small | 30-60 min (avoids full re-runs for localized failures) |
| `--rerun-failed` | Medium | 20-40 min (avoids re-running 160 passing tests) |
| Shared `_setup()` | Medium | Prevents future setup bugs across all test files |
| Adaptive timeouts | Small | Eliminates software-rendering flakiness |
| Remove sleep anti-pattern | Large | ~8 min per full run (17% of runtime) |
| Calibration pass | Medium | Prevents long-press false failures |

**Total estimated improvement**: Full verification cycle drops from ~3 hours to ~30-45 minutes.

---

## 8. Current Test Results (Run 3 — Final)

```
Results: 166 passed, 2 failed, 3 errors, 0 skipped (out of 171 tests)

Failures:
  test_long_press.compact — lp_code data mismatch for 'j' (FIXED in expectations)
  input.test_smoke.test_word_learning_commits_word — flaky ordering (passes individually)

Errors (all timing/flaky — pass individually):
  test_auto_punctuation.test_punctuation_space_swap — DriverTimeout
  test_autocorrect.test_autocorrect_privacy_no_words_logged — DriverTimeout
  test_core_input.test_backspace_plain — DriverTimeout
```

Unit tests: 523 passing (all green).
Voice tests: 19/19 passing (12 subdirectory + 4 flat + 3 file-based).
New voice state exhaustion tests: 7/7 passing.
