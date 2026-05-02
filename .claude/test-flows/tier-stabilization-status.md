# Tier Stabilization Status (Phase 2 sub-phase 5.2)

**Status:** DEFERRED to human-operator runtime verification.

**Date:** 2026-04-09

## Why deferred

Sub-phase 5.2 of the v1.0 Pre-Release Phase 2 plan
(`.claude/archive/2026-04-08-pre-release/plans/phase2.md`) calls for iteratively running
`python tools/e2e/e2e_runner.py` across each of the three layout modes
(FULL, COMPACT, COMPACT_DEV) until every test is green, classifying each failure
as either a test-infrastructure defect (fix in-plan) or an app defect
(file a GitHub issue, fix separately).

This step is inherently iterative and requires a running emulator + driver
server + installed Phase 1 APK. The implementation session in which sub-phases
5.1, 5.3, 5.4, and 5.5 were completed had **no emulator attached**, so no real
test runs could be performed. Stabilization therefore cannot be completed
inside this implementation session.

## Preconditions for the human operator

Before attempting the stabilization run, verify every item below:

- [ ] `Pixel_7_API_36` emulator booted and reachable via `adb devices`
      (S21 remains an allowed release validation target, but Windows
      automation uses the emulator).
- [ ] Phase 1 debug APK installed on the emulator
      (`./gradlew installDebug` completes clean).
- [ ] DevKey IME enabled and selected as the active input method
      (`adb shell ime enable <id>` + `adb shell ime set <id>`).
- [ ] Driver server running locally:
      `node tools/debug-server/server.js` (default port 3950).
- [ ] Debug server enabled on-device:
      `adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://10.0.2.2:3950`
      (verify `debug_server_enabled` handshake line appears in
      `curl http://127.0.0.1:3950/logs?category=DevKey/IME&last=5`).
- [ ] Python harness env ready: `pip install -r tools/e2e/requirements.txt`
      (or repo equivalent), and `DEVKEY_DRIVER_URL` / `DEVKEY_LAYOUT_MODE`
      env vars exported as needed.
- [ ] SwiftKey reference screenshots populated under the visual-diff reference
      directory — otherwise `swiftkey-visual-diff` SKIPs rather than FAILs
      (intentional per `coverage-matrix.md`).
- [ ] Whisper TF Lite model present in the on-device model location — otherwise
      `voice-round-trip` will fail at model-load, not at transcription.
- [ ] `adb logcat -c` clean slate before each run.

## Run procedure

1. Start the driver server and run `python tools/e2e/e2e_runner.py --preflight`.
2. For each of `full`, `compact`, `compact_dev`:
   - `adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode <mode>`
   - `DEVKEY_LAYOUT_MODE=<mode> python tools/e2e/e2e_runner.py`
   - Collect failures. For each failure use `driver.logcat_dump(...)` and
     the relevant `DevKey/*` categories to diagnose.
3. Classify every failure:
   - Test-infrastructure defect → fix under this plan, re-run.
   - App defect → file a GitHub issue via
     `gh issue create ... --label "defect,category:<CAT>,area:<AREA>,priority:<P>"`,
     do NOT silently patch app code in this plan.
4. Iterate until `e2e_runner.py` reports 0 failures on each of the three modes.
5. Commit any stabilized test modules under `tools/e2e/tests/`.

## Exit criterion

`python tools/e2e/e2e_runner.py` passes with zero reds on each of
FULL, COMPACT, COMPACT_DEV.

## Next step

When the human operator completes the stabilization run, update this file with
the final green commit SHAs for each tier and flip the status line at the top
to `COMPLETE`.
