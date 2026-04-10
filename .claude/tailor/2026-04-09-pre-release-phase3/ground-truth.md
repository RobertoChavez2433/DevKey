# Ground Truth — v1.0 Pre-Release Phase 3

Verified against live source on 2026-04-09.

---

## 1. Phase 3 gate → mechanism → current state

| Gate | Mechanism | Current state |
|---|---|---|
| §3.1 full `/test` on FULL/COMPACT/COMPACT_DEV | `tools/e2e/e2e_runner.py` per-mode iteration | **NOT GREEN.** Tier stabilization explicitly DEFERRED per `.claude/test-flows/tier-stabilization-status.md` — needs emulator runtime. |
| §3.2 voice round-trip | `tests/test_voice.py::test_voice_round_trip_committed_text` | **BLOCKED.** Whisper model absent from `app/src/main/assets/` (only `.gitkeep` + `MODEL_PROVENANCE.md`). |
| §3.3 next-word prediction | `tests/test_next_word.py` (3 tests) | Instrumentation present (`CandidateView.kt` `candidate_strip_rendered`). Runnable pending blocker #1 fix. |
| §3.4 long-press coverage | `tests/test_long_press.py` | Expectation JSONs present for `full/compact/compact_dev/symbols`. Instrumentation present (`KeyPressLogger.kt:24`). Runnable pending blocker #1 fix. |
| §3.5 SwiftKey visual diff | `tests/test_visual_diff.py` | **PARTIAL.** Only `compact-dark.png` reference captured. Full/compact_dev will SKIP. Light theme deferred per spec §4.4.1. |
| §3.6 manual smoke | `.claude/test-flows/phase1-regression-smoke.md` + `phase1-voice-verification.md` | Both checklists exist (52 + 31 lines). Re-run required on the Phase 3 release candidate build. |
| §3.7 lint clean | `./gradlew lint` | `app/build.gradle.kts:71-74` has `abortOnError = false`. "Clean" needs manual XML parse or flag flip — see `patterns/lint-gate.md`. |
| §3.8 decision gate | Commit/state capture after all greens | No existing artifact; Phase 3 plan must define one. |

---

## 2. Critical harness blockers (discovered this tailor)

### Blocker B1 — `pytest.skip()` crashes `e2e_runner.py`

**Evidence:**
- `e2e_runner.py:96-107` — catches `AssertionError` → FAIL, `Exception` → ERROR.
- Live probe (this session): `pytest 9.0.2` installed. `_pytest.outcomes.Skipped`
  is a direct `BaseException` subclass (not `Exception`). Confirmed via
  `issubclass(Skipped, BaseException) and not issubclass(Skipped, Exception) == True`.
- Seven test modules call `import pytest; pytest.skip(...)`:
  `test_voice.py`, `test_visual_diff.py`, `test_long_press.py`, `test_clipboard.py`,
  `test_macros.py`, `test_command_mode.py`, `test_plugins.py`.
- **Impact:** any test that takes its skip branch (very likely in current
  state — see sections 3 and 4 below) bubbles a `BaseException` past the
  runner's `except Exception` and terminates the entire run. **No gate in
  Phase 3 can be reported green until this is fixed.**

**Minimal fix options:**
- (a) Import `_pytest.outcomes.Skipped` in `e2e_runner.py`, add a third
  catch branch before `Exception` that prints `SKIP` and increments a
  `skipped` counter. Adjust exit code to `1 if (failed + errors) > 0 else 0`
  (unchanged — skips are green).
- (b) Replace `pytest.skip(...)` calls with a locally-defined
  `class HarnessSkip(Exception): pass` sentinel and an explicit branch.
  Removes the `pytest` runtime dependency entirely.

**Recommendation for Phase 3 plan author:** option (a). It preserves test
portability to a real `pytest` runner (which the harness may migrate to
later) and keeps the patch surface ≤ 20 lines.

### Blocker B2 — `pytest` missing from `tools/e2e/requirements.txt`

**Evidence:**
```
# tools/e2e/requirements.txt (verbatim)
scikit-image>=0.21
Pillow>=10.0
numpy>=1.24
```
No `pytest` line. Test modules import it lazily. A fresh venv running
`pip install -r tools/e2e/requirements.txt` will `ImportError` the moment
a test hits its skip branch.

**Fix (coupled with B1):**
- If option B1(a): add `pytest>=7.0` to requirements.txt.
- If option B1(b): no requirements change; remove the `import pytest` lines
  from the test modules too.

### Blocker B3 — Voice model files absent

**Evidence:**
```
$ ls -la app/src/main/assets/
.gitkeep
MODEL_PROVENANCE.md
```
Spec §5.1 states the code is complete; §5.2 lists the model-sourcing work
as Phase 1 prerequisite. Phase 3.2 ("voice round-trip flow green") cannot
report green until either the files land or §3.2 is formally demoted by a
spec amendment.

### Blocker B4 — SwiftKey reference captures incomplete

**Evidence:**
```
$ ls .claude/test-flows/swiftkey-reference/
compact-dark.png
compact-dark-cropped.png
compact-dark-findings.md
README.md
```
Spec §4.4.1 already defers light theme for v1.0. Dark-theme captures for
Qwerty / Full / Symbols / Fn / Phone are listed as "Phase 1 backlog" in
`coverage-matrix.md`. Phase 3.5 gate interpretation question: is SKIP on
unreffed modes acceptable, or do the captures need to happen first? Open
question routed to plan author (see `manifest.md`).

### Blocker B5 — `command_mode_auto_enabled` instrumentation not landed

**Evidence:** grep across `app/src/main/java` for `command_mode_auto_enabled`
returns zero emit sites. `test_command_mode.py` SKIPs on `DriverTimeout` when
the event does not fire — which, combined with B1, crashes the runner.

---

## 3. Phase 2 instrumentation — verified signals present

These were asserted by the Phase 2 plan and are confirmed live in source.
Phase 3 tests depend on them; if any is missing the corresponding test fails
deterministically.

| Signal | Category | Source (file:line) | Consumer |
|---|---|---|---|
| `long_press_fired` | `DevKey/TXT` | `core/KeyPressLogger.kt:24` | `test_long_press.py` |
| `layout_mode_set` | `DevKey/IME` | `LatinIME.kt:479` | `test_modes.py::test_layout_mode_round_trip` |
| `layout_mode_recomposed` | `DevKey/IME` | `ui/keyboard/DevKeyKeyboard.kt:111` | `lib/keyboard.py::set_layout_mode` |
| `keymap_dump_complete` | `DevKey/IME` | `debug/KeyMapGenerator.kt:200` | `lib/keyboard.py::load_key_map` |
| `plugin_scan_complete` | `DevKey/IME` | `PluginManager.kt:245, 257` | `test_plugins.py` |
| `candidate_strip_rendered` | `DevKey/UI` | `CandidateView.kt` (1 emit) | `test_next_word.py` |
| `next_word_suggestions` | `DevKey/TXT` | `LatinIME.kt` (per Phase 2 sub-phase 1.3) | `test_next_word.py` |
| `panel_opened{panel=clipboard}` | `DevKey/UI` | `ui/clipboard/ClipboardPanel.kt` (2 emits) | `test_clipboard.py` |
| `panel_opened{panel=macros}` | `DevKey/UI` | `ui/macro/MacroGridPanel.kt` (2 emits) | `test_macros.py` |
| `state_transition` / `processing_complete` | `DevKey/VOX` | `feature/voice/VoiceInputEngine.kt` (13 emits) | `test_voice.py` |

**Missing signal (blocker B5):** `command_mode_auto_enabled` on `DevKey/IME` —
zero emit sites found despite `test_command_mode.py` gating on it.

---

## 4. Driver server contract (verified `tools/debug-server/server.js`)

- Binds `127.0.0.1:3947` (line 242) — loopback only, matches Phase 2 threat model.
- Endpoints:
  - `POST /log` · `GET /logs?last=N&category=X&hypothesis=H`
  - `GET /health` · `GET /categories` · `POST /clear`
  - `GET /wait?category=X&event=Y&match=<json>&timeout=<ms>` — long-poll gate
  - `POST /adb/tap`, `POST /adb/swipe`, `POST /adb/broadcast`
  - `GET /adb/logcat?tags=...` · `POST /adb/logcat/clear`
  - `POST /wave/start?id=` · `POST /wave/finish?id=&status=` · `GET /wave/status`
- `MAX_ENTRIES = 30000` (line 9) — rotates in place via `logs.splice(...)` to
  preserve closure identity for long-polling `waitFor` callers
  (comment at line 28 — critical correctness invariant).
- Requires env var `ADB_SERIAL` when multiple devices attached (line 249).

**Python client contract (`tools/e2e/lib/driver.py`):**
- `DRIVER_URL = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3947")`
  — Python runs on the host; Android IME uses `http://10.0.2.2:3947`.
- `require_driver()` — called at the top of every new test; `e2e_runner.py:171`
  also calls it globally before dispatching tests, so fail-fast on missing
  driver is already enforced.
- `wait_for` timeout: client-side is `timeout_ms/1000 + 5` seconds, server-side
  uses the raw `timeout_ms`. Client outlives server by 5s to avoid a race.

---

## 5. `e2e_runner.py` auto-enables debug server

`e2e_runner.py:167-176` — right after `--list` early exit and right before
test dispatch, the runner broadcasts `dev.devkey.keyboard.ENABLE_DEBUG_SERVER`
with the configured URL. This means the Phase 3 runbook **does not** need a
separate manual "send ENABLE_DEBUG_SERVER" step — it happens automatically
each run. The tier-stabilization-status.md "Preconditions" list is slightly
out of date on this point; flag for Phase 3 plan to clarify.

---

## 6. Lint configuration (`app/build.gradle.kts:71-74`)

```kotlin
lint {
    checkReleaseBuilds = true
    abortOnError = false
}
```

**Implication for §3.7:** `./gradlew lint` will exit 0 even when lint finds
issues. Phase 3 "lint clean" must be interpreted as **"`app/build/reports/lint-results-debug.xml`
contains zero `<issue severity="Error">` entries"** (or similar XML parse),
**or** Phase 3 plan should flip `abortOnError = true` before the gate run.
See `patterns/lint-gate.md` for both options.

---

## 7. Long-press expectation files

Present and non-empty:
```
.claude/test-flows/long-press-expectations/compact.json
.claude/test-flows/long-press-expectations/compact_dev.json
.claude/test-flows/long-press-expectations/full.json
.claude/test-flows/long-press-expectations/symbols.json
```

Schema (verified):
```json
[{"label": "q", "lp_codes": [37]}, ...]
```

`test_long_press.py::_load_expectations` reads JSON by mode. `test_long_press.py:62`
uses `entry.get("lp_codes", [])` so entries without long-press are silently
skipped — matches spec §4.4.1 "reference always wins over template".

---

## 8. Test runner result-counting math

`e2e_runner.py:110` reports:
```
f"Results: {passed} passed, {failed} failed, {errors} errors "
f"(out of {total} tests)"
```

**Bug note:** there is no `skipped` bucket. After B1 is fixed, either:
- add a fourth counter and print `passed, failed, errors, skipped`, or
- count skips as `passed` (semantically correct for a gate interpretation
  where SKIP = "no signal, not a red flag").

Exit code is `1 if (failed + errors) > 0 else 0` (line 182). A skipped
test MUST count toward exit 0 or the Phase 3 gate will perpetually report
red for the (intentional) `swiftkey-visual-diff` SKIPs on uncaptured modes.

---

## 9. Ambient facts for Phase 3 plan (NOT blockers)

- **Python path for the harness:** `sys.path.insert(0, e2e_dir)` at
  `e2e_runner.py:150` — tests import as `from lib import adb, driver, ...`.
- **Layout mode constants:** `"full"`, `"compact"`, `"compact_dev"` — validated
  in `lib/keyboard.py::set_layout_mode` (line 171) and
  `LatinIME.kt:407-413` (per Phase 2 tailor's ground-truth).
- **Voice keycode:** `KEYCODE_VOICE = -102` (verified in Phase 2 tailor;
  used directly in `test_voice.py:17`).
- **400-line rule scope:** 12 files currently exceed 400 lines (LatinIME.kt
  2857, ComposeSequence.kt 1144, Keyboard.kt 1004, LatinKeyboard.kt 956,
  ExpandableDictionary.kt 707, Suggest.kt 600, KeyView.kt 529,
  KeyboardSwitcher.kt 528, CandidateView.kt 482, core/KeyEventSender.kt 481,
  ui/theme/DevKeyTheme.kt 478, ui/keyboard/QwertyLayout.kt 466). **This is
  Phase 4 scope** — recorded here only to prevent Phase 3 plan from
  accidentally bleeding into refactor. Phase 3 must not modify any of them
  except to apply a stabilization fix required to reach green.
- **Manual checklists live under `.claude/test-flows/`** — not under
  `.claude/docs/`. The §3.6 gate runs them from there.
- **Wave-gate helpers** in `tools/debug-server/wave-gate.js` are Phase 2 scope
  and not called directly by the Python harness; Phase 3 does not need to
  touch them.
