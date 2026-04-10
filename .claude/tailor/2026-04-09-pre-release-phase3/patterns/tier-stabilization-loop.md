# Pattern — Tier Stabilization Iteration Loop

## Summary
How the Phase 3 gate drives `tools/e2e/e2e_runner.py` across the three DevKey
layout modes (FULL / COMPACT / COMPACT_DEV) until each reports zero reds.
Inherits directly from Phase 2 sub-phase 5.2 (which was deferred to the human
operator).

## How we do it
A human operator runs the emulator, starts `node tools/debug-server/server.js`
as a long-lived session process, then for each layout mode exports
`DEVKEY_LAYOUT_MODE=<mode>` and runs `python tools/e2e/e2e_runner.py`. Failures
are classified immediately as either a test-infrastructure defect (fix in-plan
as stabilization-in-place) or an app defect (file a GitHub issue and bounce to
Phase 1 per §3.8). Iteration continues until all three modes report
`0 failed, 0 errors`. The runner auto-broadcasts `ENABLE_DEBUG_SERVER` on
every start (`e2e_runner.py:167-176`), so no manual broadcast step is needed.

## Exemplar 1 — canonical per-mode loop (from `tier-stabilization-status.md:47-61`)
```
1. Start the driver server, ensure ENABLE_DEBUG_SERVER broadcast was sent.
2. For each of `full`, `compact`, `compact_dev`:
   - adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode <mode>
   - DEVKEY_LAYOUT_MODE=<mode> python tools/e2e/e2e_runner.py
   - Collect failures. Classify each.
3. Iterate until e2e_runner.py reports 0 failures on each tier.
4. Commit any stabilized test modules under tools/e2e/tests/.
```

## Exemplar 2 — the runner's per-test exit contract (`e2e_runner.py:87-113, 182`)
```python
for i, (name, func) in enumerate(tests, 1):
    try:
        func()
        passed += 1
    except AssertionError as e:
        failed += 1
    except Exception as e:
        errors += 1
# ...
sys.exit(1 if (failed + errors) > 0 else 0)
```
**Ground-truth note:** this loop is currently incompatible with `pytest.skip()`
(see `ground-truth.md` blocker B1) — the skip bubbles a `BaseException` past
the `except Exception` handler and crashes the whole run. The stabilization
loop cannot complete until B1 is fixed.

## Reusable operations

| Operation | Mechanism | Where |
|---|---|---|
| Switch layout for an entire run | `DEVKEY_LAYOUT_MODE=<mode> python e2e_runner.py` | manual env export |
| Switch layout mid-run | `driver.broadcast("...SET_LAYOUT_MODE", {"mode": mode})` + `driver.wait_for("DevKey/IME", "layout_mode_set", ...)` | `tools/e2e/lib/keyboard.py::set_layout_mode` |
| Classify a failure | grep flow report under `tools/e2e/artifacts/` (when present) or driver `GET /logs?category=<X>&last=50` | operator judgement |
| Record green | Update `.claude/test-flows/tier-stabilization-status.md` with per-tier commit SHA | `gh issue`-style commit message |

## Required imports / commands
```bash
# One-time per session
node tools/debug-server/server.js &           # long-lived
./gradlew installDebug
adb shell ime enable dev.devkey.keyboard/.LatinIME
adb shell ime set dev.devkey.keyboard/.LatinIME

# Per mode
adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode <mode>
DEVKEY_LAYOUT_MODE=<mode> python tools/e2e/e2e_runner.py
```

## Anti-patterns
- **Do not** silently patch `app/src/main/java/...` inside a stabilization
  commit. App defects route to Phase 1 per `§3.8`.
- **Do not** delete or comment out a failing test to make a tier green —
  that violates spec §2.2 "Flaky tests must be stabilized, not suppressed."
- **Do not** run the harness without the driver server — fail-fast at
  `e2e_runner.py:171`'s `driver.require_driver()` will abort immediately.
