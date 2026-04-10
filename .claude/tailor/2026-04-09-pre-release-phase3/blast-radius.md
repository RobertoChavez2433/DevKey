# Blast Radius — Phase 3 Regression Gate

Phase 3 is execution-shaped. "Blast radius" here refers to the set of files
that may be **touched by stabilization-in-place fixes** (harness patches
discovered during the gate run), and to the set of files that **must NOT
be touched** because they belong to a different phase.

---

## Expected-modify set (stabilization-in-place only)

These are the files the Phase 3 plan will legitimately need to edit as part
of the stabilization loop. All are harness/tooling — no production `.kt` files.

| File | Reason | Change size estimate |
|---|---|---|
| `tools/e2e/e2e_runner.py` | Blocker B1 — catch `pytest._Skipped` / `BaseException`, add `skipped` counter, adjust exit logic | 15–25 lines |
| `tools/e2e/requirements.txt` | Blocker B2 — add `pytest>=7.0` | 1 line |
| `tools/e2e/tests/test_modes.py` | Replace `time.sleep(0.5)` sleeps with `driver.wait_for(...)` (Phase 2 cleanup miss, fixed if stabilization uncovers flakiness) | 10–30 lines |
| `.claude/test-flows/tier-stabilization-status.md` | Flip status to COMPLETE + record green commit SHAs per tier | 5–15 lines |
| `.claude/test-flows/coverage-matrix.md` | Update "Known reference capture gaps" once any Phase 1.2 capture catches up; otherwise no change | 0–5 lines |

Additional small possibilities if stabilization uncovers defects that are
unambiguously test-bug (not app-bug):

| File | Speculative reason |
|---|---|
| Any `tools/e2e/tests/test_*.py` | Fix assertion message / timeout / coordinate resolution quirk |
| `tools/e2e/lib/keyboard.py` | Fix a calibration/load_key_map edge case |
| `tools/e2e/lib/driver.py` | Fix HTTP client timeout tuning |
| `tools/debug-server/server.js` | Fix a `/wait` matcher edge case |

**Direct dependents of these stabilization files:** none in production code.
The tooling is imported only by itself and by its own tests.

---

## Must-not-modify set (scope fence)

Phase 3 **must not** edit any of the following under pain of scope-creep:

1. **All 12 files currently > 400 lines** (see `ground-truth.md` §9). These
   are Phase 4 scope. Touching them in Phase 3 risks breaking the Phase 3
   gate by introducing new lint or regression noise that Phase 4 is tooled
   to handle.
2. **`feature/voice/VoiceInputEngine.kt`** — code-complete per spec §5.1.
   Any voice-related problem discovered during §3.2 routes to Phase 1 per
   §3.8 decision gate (bounce back to source model + harness fixes only).
3. **All files under `app/src/main/java/dev/devkey/keyboard/ui/keyboard/`**
   — Phase 4.D splits them; Phase 3 leaves them alone.
4. **`PluginManager.kt`** — GH #4 resolution is a Phase 1.8 item. If the
   plugin gate fails, bounce to Phase 1.
5. **Phase 2 test modules** (`tests/test_voice.py`, `test_next_word.py`,
   `test_long_press.py`, `test_visual_diff.py`, `test_clipboard.py`,
   `test_macros.py`, `test_command_mode.py`, `test_plugins.py`) — these
   are frozen once Phase 2 hands them over, except for stabilization-in-place
   fixes that do not change the assertion intent.
6. **`app/src/main/assets/`** — voice model sourcing is Phase 1.1. Phase 3
   does NOT land the model; it consumes it or reports blocked.
7. **`.claude/test-flows/swiftkey-reference/*.png`** — capture work is Phase 1.2.

---

## Affected tests (count)

| Tier | Count | Where |
|---|---|---|
| Python E2E test modules | 14 | `tools/e2e/tests/test_*.py` |
| Python E2E test functions | ~40 | across the 14 modules |
| Gradle lint | 1 task | `./gradlew lint` |
| Manual checklists | 2 | `phase1-regression-smoke.md`, `phase1-voice-verification.md` |
| JVM unit tests | ~361 | `./gradlew test` (ambient — Phase 3 does not formally gate on these, but a red run is a discovery-of-defect signal worth routing per §3.8) |

---

## Cleanup targets

Phase 3 explicitly **does not** run a dead-code pass. The Phase 2 tailor's
cleanup set remains deferred per `tier-stabilization-status.md` handoff.
All cleanup belongs to Phase 4.

The one clean-up item Phase 3 may safely perform:
- Remove any `time.sleep(...)` calls in `tests/test_modes.py` (3 call sites)
  that predate the Phase 2 driver-gated migration and slipped through the
  Phase 2 cleanup sweep.
