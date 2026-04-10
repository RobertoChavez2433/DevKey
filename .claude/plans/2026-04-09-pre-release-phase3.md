# v1.0 Pre-Release Phase 3 — Regression Gate Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** Drive the v1.0 pre-release Regression Gate (spec §6 Phase 3) from its
current state — Phase 2 landed, tier stabilization deferred — to a green
decision gate on §3.1–§3.7, escalating §3.2 / §3.5 gate-gaps per the
defect-triage routing rules when upstream Phase 1 work is incomplete.

**Spec:** `.claude/specs/pre-release-spec.md` (§6 Phase 3)
**Tailor:** `.claude/tailor/2026-04-09-pre-release-phase3/`

**Architecture:** Phase 3 is execution-shaped, not code-shaped. No production
Kotlin is modified in this plan. The only code changes are stabilization-in-place
fixes to the Python test harness (`tools/e2e/e2e_runner.py`,
`tools/e2e/requirements.txt`) to unblock the skip-handling bug (blocker B1/B2),
plus one conditional flip in `app/build.gradle.kts` for the lint gate (§3.7)
and one `.md` status update (`tier-stabilization-status.md`) recording the
per-tier green outcome. Every other Phase 3 activity is a runbook step executed
by the human operator against a running emulator, with Claude agents dispatched
only to classify failures per `patterns/defect-triage.md` and to write the
gate-close commit.

**Tech Stack:** Kotlin + C++ NDK, Jetpack Compose, Room, TF Lite, Gradle KTS;
Python 3.8+ (test runner), Node 18+ (driver server), scikit-image (visual diff).

**Blast Radius:** 4 direct (`tools/e2e/e2e_runner.py`,
`tools/e2e/requirements.txt`, `app/build.gradle.kts`,
`.claude/test-flows/tier-stabilization-status.md`) · 0 dependent (Kotlin
untouched) · 0 new unit tests (harness-only patches) · 0 cleanup in Phase 3
(all cleanup deferred to Phase 4).

**Ground-truth discrepancies from tailor (resolved in this plan):**
1. **B1** `pytest.skip()` crashes `e2e_runner.py` because `Skipped` is a
   `BaseException` subclass on `pytest 9.0.2` — fixed in sub-phase 1.1.
2. **B2** `pytest` missing from `tools/e2e/requirements.txt` — fixed in
   sub-phase 1.2.
3. **B3** Whisper TF Lite model absent from `app/src/main/assets/` — **NOT
   fixed in this plan**. Voice model sourcing is spec §5.2 / §6 Phase 1.1
   work. If §3.2 voice gate goes red on model-missing, the plan routes it
   per sub-phase 3.3 to a Phase 1 bounce (or spec amendment) — it is not
   patched in place.
4. **B4** SwiftKey dark-theme references incomplete (only `compact-dark.png`
   captured). Reference capture is spec §6 Phase 1.2 work. §3.5 gate runs
   in "partial coverage" mode per sub-phase 3.4 — the plan defines
   partial-green as acceptable for compact-dark only, with SKIPs on
   uncaptured modes, and flags any additional captures landed later as a
   re-run trigger.
5. **B5** `command_mode_auto_enabled` instrumentation not landed in
   `CommandModeDetector.kt`. Since `test_command_mode.py` SKIPs gracefully
   on the missing event, sub-phase 1.1 (B1 fix) is sufficient to keep the
   harness green; the missing instrumentation is routed to a Phase 2 bounce
   (per `patterns/defect-triage.md` Bucket 2) ONLY if a red is discovered —
   otherwise the command-mode feature is verified via `phase1-regression-smoke.md`
   manual checklist in Phase 5.

**Design decisions locked by plan author** (from tailor open questions):

1. **Skip-handling fix → Option A (support real pytest skip).** Rationale:
   keeps test modules portable to a real pytest runner if DevKey ever
   migrates off the custom harness; minimal patch surface (≤ 25 lines in
   `e2e_runner.py` + 1 line in `requirements.txt`). Rejected Option B
   (local `HarnessSkip` sentinel) because it would require touching 8
   test modules and diverges further from pytest conventions.
2. **§3.7 lint gate → Option A (flip `abortOnError = true`).** Rationale:
   makes the gate signal build-native and CI-portable, avoids a fragile XML
   parser, and the flip is a single-line surgical change. Commit as part of
   the Phase 3 lint-gate commit stream; if release builds need the flip
   reverted post-release, that is a Phase 5 / Phase 4 cleanup item, not a
   Phase 3 scope creep.
3. **§3.5 visual-diff interpretation → partial-green acceptable.** Green
   signal is "`swiftkey-visual-diff` runs with zero reds AND at least the
   `compact-dark` reference scored above threshold". Tests for
   `full-dark` / `compact_dev-dark` SKIP cleanly (post-B1 fix) and do not
   block the gate. Rationale: spec §4.4.1 already defers light theme and
   records dark-theme capture gaps as "Phase 1 backlog". Waiting on those
   captures would effectively block v1.0 on upstream work that the spec
   itself classifies as non-blocking. Flag for re-run if captures land
   before release.
4. **§3.2 voice gate interpretation → model-present hard requirement.**
   Green signal is "`test_voice_round_trip_committed_text` passes" — NOT
   "test SKIPs because model is missing". Rationale: spec §2.1 lists voice
   end-to-end as a feature-completeness criterion; a SKIPped gate is not
   the same as a verified gate. If the model is not sourced in time,
   sub-phase 3.3 routes this to the user for a spec amendment decision
   (Bucket 4 per defect-triage) rather than allowing a silent release.
5. **§3.8 decision gate artifact → commit updating `tier-stabilization-status.md`
   plus a new `.claude/test-results/2026-04-09_phase3_gate/` directory
   containing per-tier run logs, lint XML, and a `decision.md` summary.**
   Rationale: matches the existing Phase 2 handoff mechanism and keeps
   Phase 3 observable from `.claude/test-results/` which already has a
   retention policy (`/test` skill §Retention).
6. **Execution order: lint (§3.7) runs BEFORE manual smoke (§3.6) despite spec §6 listing manual smoke first.** Rationale: lint is fully automated and terminates in under 5 minutes; running it first catches any build-config regressions introduced by Phase 1 stabilization before burning human operator time on manual smoke. This is an execution-order refinement, not a scope or gate-criterion change. If the user prefers strict spec ordering, swap Phase 4 and Phase 5 — the gate outcomes are order-independent.

## Spec §3.8 interpretation — 4-bucket refinement

> Spec §3.8 states: *"§3.8 Decision gate: if any red, return to Phase 1. Do not proceed to Phase 4 with known regressions."*

The plan's 4-bucket triage is a **refinement** authorized by spec §2.2 ("Flaky tests must be stabilized, not suppressed") for harness-only fixes (Bucket 1), and by the general Phase 1 bounce rule for app-side and test-infra defects — Bucket 2 (test instrumentation gap → Phase 2 bounce) and Bucket 3 (app-side defect → Phase 1 bounce) collapse into the spec's "return to Phase 1" outcome at the meta-level. Bucket 1 is the only bucket that lets work continue inside Phase 3; Buckets 2 and 3 are spec-conformant Phase 1 bounces re-labeled by the upstream-phase root cause.

Bucket 4 (user escalation / spec amendment) is a **meta-level outcome**, not an alternative to Phase 1 bounce — it pauses the gate pending a spec change, after which normal routing resumes.

**This refinement requires user sign-off before Phase 3 executes.** If the user prefers the strict binary spec reading, collapse all bucket outcomes to Phase 1 bounce and remove sub-phase 6.2's routing logic.

**Out of scope for Phase 3 — track as follow-up:**

- Any modification to the 12 Kotlin files currently > 400 lines
  (Phase 4 scope per spec §2.3).
- Voice model sourcing (§5.2 / §6 Phase 1.1).
- Additional SwiftKey dark-theme reference captures beyond `compact-dark`
  (§4.4 / §6 Phase 1.2).
- `command_mode_auto_enabled` instrumentation (§6 Phase 2 sub-phase 4.8 —
  Bucket 2 bounce only if discovered as a red).
- Sleep-based assertions still present in `tests/test_modes.py` — 6 call sites remain. Spec §2.2 requires the driver server to "replace any remaining polling/sleep-based synchronization"; these sleeps are a Phase 2 cleanup miss. Phase 3 inherits a user-approved waiver: the sleeps stay for now but if they produce any flakiness during Phase 2 tier stabilization they are routed as Bucket 1 stabilization-in-place (replace with `driver.wait_for(...)`) rather than tolerated. This waiver must be explicitly acknowledged by the user at Phase 3 kick-off; absent acknowledgement, escalate to Bucket 4 as a §2.2 conformance gap.
- Any Phase 4 refactor work — the 400-line rule is Phase 4's gate.

**Ground-truth anchors (from tailor `ground-truth.md`):**

- `e2e_runner.py:96-107` — current skip-incompatible `try/except` loop.
- `e2e_runner.py:182` — exit contract
  `sys.exit(1 if (failed + errors) > 0 else 0)`.
- `tools/e2e/requirements.txt` — 3 lines, no `pytest`.
- `app/build.gradle.kts:71-74` — lint block, `abortOnError = false`.
- `tools/e2e/lib/driver.py:12` —
  `DRIVER_URL = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3947")`.
- `lib/keyboard.py::set_layout_mode` valid values `("full", "compact", "compact_dev")`.
- `KEYCODE_VOICE = -102` (`KeyData.kt:148`, used in `test_voice.py:17`).
- Instrumentation emit sites verified in source:
  - `PluginManager.kt:245, 257` (`plugin_scan_complete`)
  - `KeyPressLogger.kt:24` (`long_press_fired`)
  - `LatinIME.kt:479` (`layout_mode_set`)
  - `DevKeyKeyboard.kt:111` (`layout_mode_recomposed`)
  - `KeyMapGenerator.kt:200` (`keymap_dump_complete`)
  - `VoiceInputEngine.kt` (13 `state_transition` / `processing_complete` sites)
  - `CandidateView.kt` (`candidate_strip_rendered`)
  - `ClipboardPanel.kt`, `MacroGridPanel.kt` (`panel_opened` × 2 each)
- Instrumentation emit sites NOT found (Bucket 2 route if needed):
  - `command_mode_auto_enabled` — zero hits in `app/src/main/java`.

**Entry conditions (before Phase 1 of this plan begins):**

- [ ] Android emulator booted and reachable via `adb devices`.
- [ ] Phase 1 debug APK installed (`./gradlew installDebug` clean).
- [ ] DevKey IME enabled and selected (`ime enable` + `ime set`).
- [ ] Node driver server ready to be started locally
  (`node tools/debug-server/server.js` on port 3947).
- [ ] `adb`, Python 3.8+, Node 18+, and `pip install -r tools/e2e/requirements.txt`
  (post-B2 fix) available on the host.
- [ ] Host loopback reachable from emulator at `http://10.0.2.2:3947`.
- [ ] `phase1-regression-smoke.md` + `phase1-voice-verification.md` present
  and unmodified since Phase 1.

---

## Phase 1 — Harness Unblock (stabilization-in-place)

Repairs the two critical harness blockers (B1, B2) that currently crash the
Phase 3 gate the moment any test hits a skip branch. This phase is **scoped
strictly to `tools/e2e/`** — no production code. After this phase completes,
`python tools/e2e/e2e_runner.py --list` must succeed and, on a running
driver + emulator, the runner must correctly classify SKIP paths as
non-red.

### Sub-phase 1.1: Fix `e2e_runner.py` skip handling (blocker B1)

**Files:** `tools/e2e/e2e_runner.py` (modify)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Open `tools/e2e/e2e_runner.py`. Locate the import block at the top of the
   file (lines 19-26 as of the tailor snapshot) and add a lazy import of
   pytest's skip sentinel immediately after the existing stdlib imports.
   Insert the following lines after `import traceback`:
   ```python
   # WHY: pytest.skip() raises _pytest.outcomes.Skipped which subclasses
   #      BaseException (verified on pytest 9.0.2 during tailor 2026-04-09),
   #      NOT Exception. Without an explicit handler the whole runner loop
   #      crashes on the first test that hits a skip branch.
   # FROM SPEC: Phase 3 gate depends on graceful-skip handling in
   #            test_voice.py, test_visual_diff.py, test_long_press.py,
   #            test_clipboard.py, test_macros.py, test_command_mode.py,
   #            test_plugins.py, test_modifier_combos.py.
   # IMPORTANT: Import is lazy-guarded so the harness keeps running even on
   #            a host where pytest is missing — prints a deprecation warning
   #            instead of crashing.
   try:
       from _pytest.outcomes import Skipped as _PytestSkipped
   except ImportError:  # pragma: no cover — exercised only when pytest is absent
       _PytestSkipped = None
   ```

2. Replace the `run_tests` function body's per-test try/except block (the
   `try: func() ... except Exception as e:` structure at lines ~87-107) with
   a three-branch version that catches `_PytestSkipped` between
   `AssertionError` and `Exception`, increments a new `skipped` counter,
   and prints a `SKIP` line without incrementing `errors`. Change the
   function signature to return 4 counts instead of 3. Replace lines 73-113
   with:
   ```python
   def run_tests(tests: List[Tuple[str, callable]]) -> Tuple[int, int, int, int]:
       """
       Run all discovered tests and print results.

       Returns (passed, failed, errors, skipped) counts.
       """
       passed = 0
       failed = 0
       errors = 0
       skipped = 0

       total = len(tests)
       print(f"\nRunning {total} test(s)...\n")
       print("=" * 70)

       for i, (name, func) in enumerate(tests, 1):
           print(f"  [{i}/{total}] {name} ... ", end="", flush=True)
           start = time.time()

           try:
               func()
               elapsed = time.time() - start
               print(f"PASS ({elapsed:.1f}s)")
               passed += 1
           except AssertionError as e:
               elapsed = time.time() - start
               print(f"FAIL ({elapsed:.1f}s)")
               print(f"         {e}")
               failed += 1
           except BaseException as e:
               # WHY: BaseException catches pytest.Skipped (which inherits from
               #      BaseException, NOT Exception) AND generic Exception errors
               #      in a single branch. We discriminate on type immediately
               #      below so skipped tests are not counted as errors.
               # IMPORTANT: Re-raise KeyboardInterrupt and SystemExit — those
               #            must not be swallowed by the harness.
               if isinstance(e, (KeyboardInterrupt, SystemExit)):
                   raise
               elapsed = time.time() - start
               if _PytestSkipped is not None and isinstance(e, _PytestSkipped):
                   # pytest.skip("reason") — reason is accessible via e.msg on
                   # pytest 7+, or the first arg on older versions.
                   reason = getattr(e, "msg", None) or (e.args[0] if e.args else "")
                   print(f"SKIP ({elapsed:.1f}s)")
                   if reason:
                       print(f"         {reason}")
                   skipped += 1
               else:
                   print(f"ERROR ({elapsed:.1f}s)")
                   print(f"         {type(e).__name__}: {e}")
                   if os.environ.get("DEVKEY_E2E_VERBOSE"):
                       traceback.print_exc()
                   errors += 1

       print("=" * 70)
       print(f"\nResults: {passed} passed, {failed} failed, {errors} errors, "
             f"{skipped} skipped (out of {total} tests)")

       return passed, failed, errors, skipped
   ```

3. Update the `main()` function's call site (lines ~178-182) to unpack four
   counts and keep the exit contract unchanged — skipped tests count as
   green. Replace:
   ```python
   # Run tests
   passed, failed, errors = run_tests(tests)

   # Exit with non-zero if any failures
   sys.exit(1 if (failed + errors) > 0 else 0)
   ```
   with:
   ```python
   # Run tests
   passed, failed, errors, skipped = run_tests(tests)

   # WHY: Skipped tests are NOT failures. Spec §3.5 visual-diff gate
   #      explicitly relies on SKIPs for uncaptured SwiftKey references
   #      (spec §4.4.1 "reference always wins over template"), and spec
   #      §3.2 voice gate allows SKIP on emulator without audio injection.
   # Exit with non-zero if any failures or errors — skipped tests count as green.
   sys.exit(1 if (failed + errors) > 0 else 0)
   ```

4. Verify the module still parses by running `python -c "import tools.e2e.e2e_runner"`
   is NOT the right smoke — the runner is a script, not a package. Instead,
   from the repo root run:
   ```bash
   python tools/e2e/e2e_runner.py --list
   ```
   Expected: the runner prints "Discovered N test(s):" followed by the
   module list and exits cleanly with code 0. Failure indicates a syntax
   error in the patch — re-read the modified file and fix.

### Sub-phase 1.2: Declare `pytest` in harness requirements (blocker B2)

**Files:** `tools/e2e/requirements.txt` (modify)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Open `tools/e2e/requirements.txt` (currently 3 lines — verify before
   editing). Append a `pytest` declaration at the end of the file. The
   final file must read:
   ```
   # DevKey E2E Python test harness dependencies.
   # Phase 2 introduces visual-diff and image processing.

   scikit-image>=0.21
   Pillow>=10.0
   numpy>=1.24
   # WHY: pytest is imported lazily inside 8 test modules for the skip
   #      sentinel (test_voice.py, test_visual_diff.py, test_long_press.py,
   #      test_clipboard.py, test_macros.py, test_command_mode.py,
   #      test_plugins.py, test_modifier_combos.py). Without an explicit
   #      requirements entry a fresh venv ImportErrors on the first skip
   #      path and the entire Phase 3 gate becomes un-runnable.
   # FROM SPEC: §6 Phase 3 requires a green bar across tools/e2e/tests/.
   pytest>=7.0
   ```

2. On the developer host, rebuild the harness virtualenv (or the bare
   system interpreter used by the harness — whichever is canonical for
   this operator) to ensure `pytest` is actually resolvable:
   ```bash
   pip install -r tools/e2e/requirements.txt
   ```
   Expected: `pytest-7.x` or newer installs without errors. If pytest is
   already installed at a compatible version, pip prints "Requirement
   already satisfied" — also acceptable.

3. Smoke-test the lazy import path by running the harness `--list` command
   again (from sub-phase 1.1 step 4). Expected: same green output. This
   confirms the pytest import path that the test modules rely on is fully
   resolvable and the harness no longer has an implicit dependency on a
   pre-existing pytest install.

**Verification:** `./gradlew assembleDebug`

(Rationale: Phase 1 touches only Python harness files, but a build gate
here ensures the repo still compiles — catches any accidental Kotlin touch
during the stabilization commits before they land on main.)

### Sub-phase 1.3: Gitignore hygiene for test-results directory

**Files:** `.claude/test-results/2026-04-09_phase3_gate/README.md` (create)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Create the Phase 3 gate directory and a privacy-banner README. Sub-phase 6.3 step 3 explicitly stages only the audit-trail files by name; this README is the policy header that governs what may live in this directory.
   ```bash
   mkdir -p .claude/test-results/2026-04-09_phase3_gate
   cat > .claude/test-results/2026-04-09_phase3_gate/README.md <<'EOF'
   # Phase 3 Regression Gate — Audit Trail

   **This directory is committed to the repo. Do NOT write raw logcat,
   DEVKEY_E2E_VERBOSE tracebacks, or any unscrubbed operator traces here.**

   Only structured DevKeyLogger captures fetched via the driver
   `/logs?category=...` endpoint, the per-tier run logs produced by
   `e2e_runner.py`, the lint XML report, and the per-gate `gate-*.md`
   summaries are permitted. See sub-phase 6.3 step 3 for the explicit
   allow-list of files staged into this directory.
   EOF
   ```

2. Do NOT add `.claude/test-results/2026-04-09_phase3_gate/` to `.gitignore` — some artifacts in this directory are intentionally committed. Instead, sub-phase 6.3 step 3 stages only the expected files by name (`git add` per-path, never `git add -A`). If a stray file appears in the directory at commit time, sub-phase 6.3 step 4 (`git diff --staged` review) catches it before the commit lands.

**Verification:** (none — file creation only)

---

## Phase 2 — Tier Stabilization Run (spec §3.1)

Drives `tools/e2e/e2e_runner.py` to green on all three layout modes —
FULL, COMPACT, COMPACT_DEV. This phase is iterative and owned by the human
operator + classification agents. No Kotlin code changes. Defects
discovered here are routed per `patterns/defect-triage.md`: Bucket 1
fixes (harness-only) land in this plan's stabilization commits; Bucket 2
bounces to Phase 2; Bucket 3 bounces to Phase 1; Bucket 4 escalates to the
user for a spec amendment.

### Sub-phase 2.1: Pre-flight verification

**Files:** (none — read-only verification)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Verify the driver server is reachable from the host. Start it if not
   already running:
   ```bash
   node tools/debug-server/server.js
   ```
   In a second shell, `curl http://127.0.0.1:3947/health`. Expected:
   `{"status":"ok", "entries":0, ...}`.

2. Verify the emulator / device is visible to ADB and has the Phase 1 debug
   APK installed:
   ```bash
   adb devices
   adb shell pm list packages | grep dev.devkey.keyboard
   ```
   Expected: at least one device listed as `device` (not `offline`), and
   `package:dev.devkey.keyboard` present. If absent, run
   `./gradlew installDebug` and re-verify.

3. Verify DevKey is the active IME:
   ```bash
   adb shell settings get secure default_input_method
   ```
   Expected: `dev.devkey.keyboard/.LatinIME`. If not, enable and set:
   ```bash
   adb shell ime enable dev.devkey.keyboard/.LatinIME
   adb shell ime set dev.devkey.keyboard/.LatinIME
   ```

4. Verify harness `--list` works end-to-end with driver running:
   ```bash
   python tools/e2e/e2e_runner.py --list
   ```
   Expected: test discovery prints without errors. Any `ImportError`
   related to `pytest` means sub-phase 1.2 was not applied correctly —
   return to Phase 1 before proceeding.

5. Broadcast `ENABLE_DEBUG_SERVER` from the host to force the IME to
   forward logs to the driver (this is also done automatically by the
   runner itself, but doing it once here confirms the handshake works):
   ```bash
   adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER \
       --es url http://10.0.2.2:3947
   curl 'http://127.0.0.1:3947/logs?category=DevKey/IME&last=5'
   ```
   Expected: the `debug_server_enabled` handshake entry appears in the
   logs response. Absence means the IME is not forwarding — check that
   the Phase 1 debug build is actually installed (release builds do not
   register the broadcast receiver per the `isDebugBuild` guard in
   `LatinIME.kt`).

### Sub-phase 2.2: FULL mode stabilization

**Files:** (none — runtime iteration; conditional fixes routed per Bucket classification)
**Agent:** `general-purpose` (sonnet) — dispatched per-iteration only when a failure needs classification

**Steps:**

1. Switch the IME to FULL layout:
   ```bash
   adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode full
   curl 'http://127.0.0.1:3947/logs?category=DevKey/IME&last=5'
   ```
   Expected: `layout_mode_set{mode=full}` visible in the logs response
   (the emit site is `LatinIME.kt:479`, verified in tailor ground-truth).

2. Create the Phase 3 run-results directory:
   ```bash
   mkdir -p .claude/test-results/2026-04-09_phase3_gate/full
   ```

3. Run the full harness against FULL mode, teeing output to disk for
   post-run diagnosis:
   ```bash
   DEVKEY_LAYOUT_MODE=full python tools/e2e/e2e_runner.py 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/full/run-1.log
   ```
   Expected: runner prints `N passed, 0 failed, 0 errors, M skipped`
   and exits 0. **Skipped tests are non-red** — the §3.5 visual-diff
   tests for `full_dark` and `compact_dev_dark` WILL skip because only
   `compact-dark.png` reference exists (blocker B4).

4. **If the run is green:** record the current commit SHA and the
   per-tier timestamp in a local note for sub-phase 2.5. Proceed to
   sub-phase 2.3.

5. **If the run is red:** for each failed/errored test, gather evidence
   from the driver server and the test run log:
   ```bash
   curl 'http://127.0.0.1:3947/logs?category=DevKey/IME&last=100' \
       > .claude/test-results/2026-04-09_phase3_gate/full/ime-logs.json
   curl 'http://127.0.0.1:3947/logs?category=DevKey/VOX&last=50' \
       > .claude/test-results/2026-04-09_phase3_gate/full/vox-logs.json
   curl 'http://127.0.0.1:3947/logs?category=DevKey/TXT&last=100' \
       > .claude/test-results/2026-04-09_phase3_gate/full/txt-logs.json
   curl 'http://127.0.0.1:3947/logs?category=DevKey/UI&last=50' \
       > .claude/test-results/2026-04-09_phase3_gate/full/ui-logs.json
   ```
   Dispatch a `general-purpose` sonnet agent with the tailored prompt:

   > Classify the following Python harness red into one of four buckets
   > per `.claude/tailor/2026-04-09-pre-release-phase3/patterns/defect-triage.md`:
   >
   > - Bucket 1 (stabilization-in-place) → fix lives in `tools/e2e/*`
   > - Bucket 2 (Phase 2 bounce) → missing instrumentation emit site
   > - Bucket 3 (Phase 1 bounce) → app-side feature defect
   > - Bucket 4 (spec amendment) → gate item is impossible as spec'd
   >
   > Test name: `<module>.<test_name>`
   > Error output: (paste from run-1.log)
   > IME logs: .claude/test-results/2026-04-09_phase3_gate/full/ime-logs.json
   > VOX logs: (similarly)
   > TXT logs: (similarly)
   > UI logs: (similarly)
   >
   > Produce a single-paragraph diagnosis and a bucket assignment.
   > Do NOT propose code changes to production Kotlin files — route
   > those as Bucket 3 for Phase 1 bounce. You may propose harness-only
   > fixes for Bucket 1 findings. Return the classification as a JSON
   > object: `{"bucket": N, "diagnosis": "...", "proposed_fix": "..."}`.

6. Apply Bucket 1 fixes in-place (if any), then re-run step 3. Iterate
   until green or until a Bucket 2/3/4 finding blocks progress. Bucket 2
   and Bucket 3 findings trigger a plan halt — commit the current
   stabilization state with `gh issue create ... --label
   "defect,category:<CAT>,area:<AREA>,priority:<P>"` and pause Phase 3
   until the upstream phase re-closes.

7. When FULL is green, write a per-tier summary to disk:
   ```bash
   cat > .claude/test-results/2026-04-09_phase3_gate/full/summary.md <<'EOF'
   # FULL tier — Phase 3 stabilization summary

   - Final run: run-<N>.log
   - Result: <passed> passed, 0 failed, 0 errors, <skipped> skipped
   - SKIP reasons:
     - <test name>: <reason>
   - Bucket 1 fixes applied: <list of commit SHAs>
   - Bucket 2/3/4 escalations: <list, or "none">
   EOF
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

### Sub-phase 2.3: COMPACT mode stabilization

**Files:** (none — runtime iteration, same pattern as 2.2)
**Agent:** `general-purpose` (sonnet) — per-iteration classification only

**Steps:**

1. Switch the IME to COMPACT layout:
   ```bash
   adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact
   ```
   Verify via `curl 'http://127.0.0.1:3947/logs?category=DevKey/IME&last=5'`
   that `layout_mode_set{mode=compact}` and `layout_mode_recomposed{mode=compact}`
   both appeared.

2. Create the COMPACT run-results directory:
   ```bash
   mkdir -p .claude/test-results/2026-04-09_phase3_gate/compact
   ```

3. Run the harness:
   ```bash
   DEVKEY_LAYOUT_MODE=compact python tools/e2e/e2e_runner.py 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/compact/run-1.log
   ```
   Expected: green, with SKIPs for `test_visual_full_dark` and
   `test_visual_compact_dev_dark` (B4), and potentially for
   `test_command_mode.py::test_command_mode_auto_enabled_on_terminal_focus`
   on emulators without a terminal package installed. The
   `test_long_press_every_key_compact` flow exercises
   `.claude/test-flows/long-press-expectations/compact.json` — every
   entry with `lp_codes` must emit a matching `long_press_fired` event
   per the instrumentation at `KeyPressLogger.kt:24`.

4. **Known COMPACT-specific gotcha** from the test files: the Tab key is
   absent from the COMPACT layout, and `test_modifier_combos.py::test_alt_tab`
   already handles this via an explicit `pytest.skip(...)` at line 89-90.
   Post-sub-phase 1.1 that skip is now honored — the test should SKIP on
   COMPACT, not fail. If you see this test FAIL instead of SKIP,
   sub-phase 1.1 was applied incorrectly — return to Phase 1.

5. Apply the same red-classification loop as sub-phase 2.2 steps 5-6 if
   reds appear.

6. Write the COMPACT summary:
   ```bash
   cat > .claude/test-results/2026-04-09_phase3_gate/compact/summary.md <<'EOF'
   # COMPACT tier — Phase 3 stabilization summary
   (same shape as FULL summary)
   EOF
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

### Sub-phase 2.4: COMPACT_DEV mode stabilization

**Files:** (none — runtime iteration, same pattern as 2.2)
**Agent:** `general-purpose` (sonnet) — per-iteration classification only

**Steps:**

1. Switch the IME to COMPACT_DEV layout:
   ```bash
   adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact_dev
   ```
   Verify via the `/logs` endpoint.

2. Create the COMPACT_DEV run-results directory:
   ```bash
   mkdir -p .claude/test-results/2026-04-09_phase3_gate/compact_dev
   ```

3. Run the harness:
   ```bash
   DEVKEY_LAYOUT_MODE=compact_dev python tools/e2e/e2e_runner.py 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/compact_dev/run-1.log
   ```
   Expected: green, with the same reference-gap SKIPs as COMPACT.

4. Same red-classification loop as sub-phase 2.2 steps 5-6 if reds appear.

5. Write the COMPACT_DEV summary following the same shape as FULL. Replace every `<placeholder>` token in this file with the actual value before staging for commit.

### Sub-phase 2.5: Update tier stabilization status document

**Files:** `.claude/test-flows/tier-stabilization-status.md` (modify)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Open `.claude/test-flows/tier-stabilization-status.md`. Currently line 1
   declares `**Status:** DEFERRED to human-operator runtime verification.`
   Replace the status line and the "Why deferred" section with a completion
   block. The top of the file must become:
   ```markdown
   # Tier Stabilization Status (Phase 2 sub-phase 5.2)

   **Status:** COMPLETE — Phase 3 sub-phase 2.5 (2026-04-09).

   ## Stabilization outcome

   | Tier | Final commit SHA | Passed | Failed | Errors | Skipped | Notes |
   |---|---|---|---|---|---|---|
   | FULL | <sha> | <n> | 0 | 0 | <n> | see `.claude/test-results/2026-04-09_phase3_gate/full/summary.md` |
   | COMPACT | <sha> | <n> | 0 | 0 | <n> | see `.claude/test-results/2026-04-09_phase3_gate/compact/summary.md` |
   | COMPACT_DEV | <sha> | <n> | 0 | 0 | <n> | see `.claude/test-results/2026-04-09_phase3_gate/compact_dev/summary.md` |

   SKIPs are spec-sanctioned (see `.claude/specs/pre-release-spec.md`
   §4.4.1 — reference capture gaps, §3.2 — voice model availability,
   `test_modifier_combos.py` — Tab absent from COMPACT layout).
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

2. **Preserve the historical context** of the "Why deferred" block by
   moving it under an `## History` heading below the outcome block rather
   than deleting it. This keeps the audit trail intact.

3. Replace the original "Run procedure" and "Exit criterion" sections with
   a short "See also" pointer:
   ```markdown
   ## See also

   - Runbook (current): `.claude/plans/2026-04-09-pre-release-phase3.md`
     §Phase 2 sub-phases 2.1-2.4
   - Per-tier logs: `.claude/test-results/2026-04-09_phase3_gate/<tier>/`
   - Defect triage rules:
     `.claude/tailor/2026-04-09-pre-release-phase3/patterns/defect-triage.md`
   ```

4. Do **not** delete the "Preconditions for the human operator" checklist
   — that list remains useful for any future re-run (e.g., post-Phase-4
   regression re-run per spec §4 "Phase 4 ends with a mandatory Phase 3
   re-run").

**Verification:** `./gradlew assembleDebug`

(Rationale: Phase 2 is runtime-iterative and does not modify Kotlin, but the
final sub-phase's commit lands the stabilization status update. A successful
build here proves none of the Bucket-1 harness fixes accidentally broke a
Kotlin file via a stray edit.)

---

## Phase 3 — Feature-Flow Gates (spec §3.2 – §3.5)

Runs each named feature flow once per layout mode where applicable,
classifies any reds per `patterns/defect-triage.md`, and produces a
per-gate green/partial-green/red assessment for §3.8 decision gate input.
This phase assumes Phase 2 tier stabilization is complete — if Phase 2
left any tier red, this phase cannot produce a trustworthy signal.

### Sub-phase 3.1: Next-word prediction gate (§3.3)

**Files:** (none — runtime verification; produces gate result artifact)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Verify instrumentation is present (this is a sanity check against the
   tailor ground-truth — if any emit site has been removed since tailor,
   the test will fail silently with `DriverTimeout`):
   ```bash
   grep -n 'next_word_suggestions' app/src/main/java/dev/devkey/keyboard/LatinIME.kt
   grep -n 'candidate_strip_rendered' app/src/main/java/dev/devkey/keyboard/CandidateView.kt
   ```
   Expected: at least one emit site each. If either is absent, route as
   Bucket 2 (Phase 2 bounce) — do NOT add the instrumentation in Phase 3.

2. Switch to FULL mode (any mode works for this test; FULL gives the
   broadest test surface):
   ```bash
   adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode full
   ```

3. Run ONLY the next-word module:
   ```bash
   python tools/e2e/e2e_runner.py --test test_next_word 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/next-word.log
   ```
   Expected: all three tests pass
   (`test_next_word_fires_after_space`,
   `test_next_word_bigram_hit_for_common_word`,
   `test_next_word_privacy_payload_is_structural_only`).

4. **If the bigram-hit test fails:** this is a Bucket 3 (Phase 1 bounce)
   per `test_next_word.py::test_next_word_bigram_hit_for_common_word`'s
   docstring which declares it a real defect rather than a test
   infrastructure problem. File the defect:
   ```bash
   gh issue create \
     --repo RobertoChavez2433/DevKey \
     --label "defect,category:IME,area:text-input,priority:high" \
     --title "next-word bigram hit fails for 'the' + space" \
     --body "Discovered during Phase 3 §3.3 gate run on 2026-04-09. See .claude/test-results/2026-04-09_phase3_gate/next-word.log"
   ```
   Pause Phase 3 and bounce to Phase 1.

5. **If the privacy payload test fails:** this is a Bucket 2 (Phase 2
   bounce) — an instrumentation regression where someone added extra keys
   to the `next_word_suggestions` payload. File the defect and pause.

6. Write the gate result to
   `.claude/test-results/2026-04-09_phase3_gate/gate-3.3.md`:
   ```markdown
   # §3.3 Next-word prediction — gate result

   **Status:** GREEN | RED
   **Run log:** next-word.log
   **Tests:**
   - test_next_word_fires_after_space: PASS / FAIL
   - test_next_word_bigram_hit_for_common_word: PASS / FAIL
   - test_next_word_privacy_payload_is_structural_only: PASS / FAIL

   **Bucket-routed defects:** <list, or "none">
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

### Sub-phase 3.2: Long-press coverage gate (§3.4)

**Files:** (none — runtime verification)
**Agent:** `general-purpose` (sonnet)

**Steps:**

0. **Theme scope.** Spec §3.4 requires long-press coverage "for all modes **and themes**". Per spec §4.4.1, light theme is DEFERRED to post-v1.0. Phase 3 therefore runs long-press coverage on dark-theme only, across the four modes (full/compact/compact_dev/symbols). Any light-theme coverage gap is inherited from the §4.4.1 deferral and does not block this gate. Record this in `gate-3.4.md` under the "Theme scope" line: **Theme scope:** dark-only per §4.4.1 deferral.

1. Verify the long-press instrumentation is live:
   ```bash
   grep -n 'long_press_fired' app/src/main/java/dev/devkey/keyboard/core/KeyPressLogger.kt
   ```
   Expected: at least one hit at line 24 per tailor ground-truth.

2. Verify the four expectation JSON files are present and non-empty:
   ```bash
   ls -la .claude/test-flows/long-press-expectations/*.json
   ```
   Expected: `compact.json`, `compact_dev.json`, `full.json`, `symbols.json`
   all present. Any absent file triggers a SKIP in the corresponding
   test — acceptable per the graceful-skip pattern but flag as a Phase 1
   backlog item for the user.

3. Run ONLY the long-press module (the test module handles layout switching
   internally via `set_layout_mode`):
   ```bash
   python tools/e2e/e2e_runner.py --test test_long_press 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/long-press.log
   ```
   Expected: four tests (one per mode) pass. Each iterates its
   mode's expectation JSON and asserts `lp_code in expected_codes`
   per `test_long_press.py:73-77`.

4. **If any test fails with `lp_code` mismatch:** the mismatch tells you
   which key's long-press wiring is wrong. This is almost certainly a
   Bucket 3 (Phase 1 bounce) — long-press data lives in
   `app/src/main/java/dev/devkey/keyboard/ui/keyboard/QwertyLayout.kt` and
   `SymbolsLayout.kt`, both Phase 4 scope, but fixing long-press data is
   a content fix, not a refactor. File as Phase 1 bounce.

5. **If any test fails with `DriverTimeout` on `long_press_fired`:**
   the instrumentation at `KeyPressLogger.kt:24` is broken or mis-wired.
   Bucket 2 (Phase 2 bounce). File and pause.

6. Write the gate result to
   `.claude/test-results/2026-04-09_phase3_gate/gate-3.4.md` following
   the same shape as 3.3. Replace every `<placeholder>` token in this file with the actual value before staging for commit.

### Sub-phase 3.3: Voice round-trip gate (§3.2) — BLOCKED on B3

**Files:** (none — runtime verification; produces blocked-gate artifact if model absent)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Check whether the Whisper model files have landed since the tailor
   snapshot (blocker B3). The tailor reported only `.gitkeep` +
   `MODEL_PROVENANCE.md` present.
   ```bash
   ls -la app/src/main/assets/
   ```
   Expected-if-unblocked: `whisper-tiny.en.tflite` and
   `filters_vocab_en.bin` both present (per spec §5.2).

2. **If the model files are absent:** do NOT source them in this plan —
   spec §5.2 assigns that work to Phase 1 sub-phase 1.1. Write a blocked
   gate artifact:
   ```bash
   cat > .claude/test-results/2026-04-09_phase3_gate/gate-3.2.md <<'EOF'
   # §3.2 Voice round-trip — gate result

   **Status:** BLOCKED (Bucket 4 — escalation required)

   **Blocker:** B3 — Whisper TF Lite model files absent from
   `app/src/main/assets/`. Only `.gitkeep` + `MODEL_PROVENANCE.md` present.

   **Spec reference:** §5.2 (Phase 1.1 sourcing plan), §2.1 feature
   completeness.

   **Route:** Per `patterns/defect-triage.md` Bucket 4 — requires a decision from the user on one of:
   (a) Source `whisper-tiny.en.tflite` + `filters_vocab_en.bin` via spec §5.2 / §6 Phase 1.1 and re-run §3.2 gate.
   (b) **Joint** spec amendment demoting voice E2E from spec §2.1 (feature completeness) AND §3.2 (Phase 3 gate). A §3.2-only amendment is NOT permitted — §2.1 lists voice E2E as a hard release criterion and cannot be silently dropped. The amendment must explicitly revise §2.1.

   Phase 3 cannot report all-green on §3.8 while §3.2 is BLOCKED. Without either (a) or (b), the gate remains open indefinitely.
   EOF
   ```
   Pause Phase 3 and surface the blocker to the user via the Phase 6
   summary. Do not proceed to §3.4 SwiftKey visual-diff until the user
   decides — the decision may also affect §3.5 gating (e.g., if the
   user decides to demote §3.2, they may also decide on §3.5 tolerance).

3. **If the model files are present:** run the voice test module against
   FULL mode (where the voice key is reliably in the layout):
   ```bash
   adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode full
   python tools/e2e/e2e_runner.py --test test_voice 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/voice.log
   ```
   Expected: three tests —
   `test_voice_state_machine_to_listening`,
   `test_voice_cancel_returns_to_idle`,
   `test_voice_round_trip_committed_text`. The first two must pass
   (state-machine only, no model-inference). The third requires audio
   injection on an emulator — if the host cannot inject audio, it SKIPs
   gracefully per `test_voice.py:83-88`.

4. **If `test_voice_round_trip_committed_text` SKIPs for audio reasons:**
   this is not a red, but it is also not a full green. Record the SKIP
   reason in the gate artifact and flag it as a manual-smoke fallback:
   §3.6's `phase1-voice-verification.md` re-run (Phase 5) must cover the
   audio injection path manually.

5. **If any voice test FAILs (not SKIPs):** Bucket 3 (Phase 1 bounce).
   Voice engine defects are code-complete per spec §5.1 — a FAIL here
   means a regression. File the defect.

6. Write the final gate result to `gate-3.2.md` (overwriting the BLOCKED
   artifact if created in step 2):
   ```markdown
   # §3.2 Voice round-trip — gate result

   **Status:** GREEN | PARTIAL_GREEN | RED | BLOCKED
   **Run log:** voice.log
   **Tests:**
   - test_voice_state_machine_to_listening: PASS / FAIL / SKIP
   - test_voice_cancel_returns_to_idle: PASS / FAIL / SKIP
   - test_voice_round_trip_committed_text: PASS / FAIL / SKIP

   **Skip reasons (if any):** <list>
   **Manual fallback:** phase1-voice-verification.md in Phase 5
   **Bucket-routed defects:** <list, or "none">
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

### Sub-phase 3.4: SwiftKey visual-diff gate (§3.5) — partial-green acceptable

**Files:** (none — runtime verification)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Check the current state of the SwiftKey reference directory (blocker B4):
   ```bash
   ls .claude/test-flows/swiftkey-reference/*.png
   ```
   Expected (tailor snapshot): `compact-dark.png`, `compact-dark-cropped.png`.
   If additional captures (`full-dark.png`, `compact_dev-dark.png`) have
   landed since tailor, record which references are present — more green
   tests are better.

2. Confirm `scikit-image`, `Pillow`, and `numpy` are installed per
   `tools/e2e/requirements.txt`:
   ```bash
   python -c "import skimage, PIL, numpy; print('ok')"
   ```
   Expected: `ok`. If this errors, the harness env is incomplete — fix
   with `pip install -r tools/e2e/requirements.txt`.

3. Run the visual-diff module:
   ```bash
   python tools/e2e/e2e_runner.py --test test_visual_diff 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/visual-diff.log
   ```
   Expected: `test_visual_compact_dark` PASSes (compact-dark reference
   exists). `test_visual_full_dark` and `test_visual_compact_dev_dark`
   SKIP gracefully (references absent per B4).

4. **If `test_visual_compact_dark` FAILs with an SSIM below 0.92:** this
   is a Bucket 3 (Phase 1 bounce) — either the theme retune from Phase 6
   was incomplete or a recent theme change drifted the compact-dark
   rendering away from the reference. File the defect with the captured
   `artifacts/compact-*.png` and the SSIM score.

5. **Do NOT lower the SSIM threshold in this plan.** The 0.92 floor was
   chosen during Phase 2 planning (per `.claude/archive/2026-04-08-pre-release/plans/phase2.md`
   design decisions) to accommodate emulator AA variance. Lowering it to
   paper over a real regression would violate spec §2.2 "Flaky tests must
   be stabilized, not suppressed." If the operator believes 0.92 is too
   strict on the current emulator, route as a Bucket 4 spec-amendment
   escalation, not a silent threshold change.

5a. **Spec §9 Q3 ratification.** Spec §9 Q3 lists "SSIM > 0.95" as an example, not a decision. The Phase 2 plan chose 0.92 based on measured emulator anti-aliasing variance. Before recording this gate as GREEN, confirm that spec §9 Q3 has been explicitly closed in the user's session notes or via a spec amendment to "SSIM >= 0.92". If §9 Q3 remains open, escalate via Bucket 4 for a short user ratification — do not silently inherit the 0.92 floor. Record the ratification (or lack thereof) in `gate-3.5.md`.

6. Write the gate result to
   `.claude/test-results/2026-04-09_phase3_gate/gate-3.5.md`:
   ```markdown
   # §3.5 SwiftKey visual diff — gate result

   **Status:** PARTIAL_GREEN (compact-dark only) | GREEN (all modes) | RED
   **Run log:** visual-diff.log
   **Tests:**
   - test_visual_compact_dark: PASS / FAIL (SSIM: <score>)
   - test_visual_full_dark: PASS / FAIL / SKIP (SSIM: <score> or "no reference")
   - test_visual_compact_dev_dark: PASS / FAIL / SKIP

   **Missing references (Phase 1.2 backlog):** <list>
   **Decision:** PARTIAL_GREEN accepted per design decision §3 (see plan header).
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

**Verification:** `./gradlew assembleDebug`

(Rationale: Phase 3 of the plan runs tests only, but the final sub-phase's
commit lands the gate-result artifacts. A successful build at the end of
this phase proves the artifacts land under `.claude/test-results/` without
accidentally touching any Kotlin. The `.claude/test-results/2026-04-09_phase3_gate/`
directory is committed to the repo as an audit trail; do NOT dump raw
`adb logcat` output here (per Phase 2 privacy guards, only structured
DevKeyLogger captures via `/logs?category=...` are safe to commit).)

---

## Phase 4 — Lint Gate (spec §3.7)

Repairs the semantic trap where `./gradlew lint` exits 0 regardless of
errors because `app/build.gradle.kts:73` has `abortOnError = false`. Per
the plan header's design decision §2, Phase 4 flips the flag to `true`
so "lint clean" becomes a build-native signal.

### Sub-phase 4.1: Flip `abortOnError` to enforce lint errors

**Files:** `app/build.gradle.kts` (modify)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Open `app/build.gradle.kts`. Locate the `lint { ... }` block at lines
   71-74 (verify via tailor ground-truth). Replace:
   ```kotlin
   lint {
       checkReleaseBuilds = true
       abortOnError = false
   }
   ```
   with:
   ```kotlin
   lint {
       // WHY: v1.0 Pre-Release Phase 3 §3.7 requires "lint clean" as a
       //      gate signal. With abortOnError=false the lint task exits 0
       //      regardless of errors — making the gate a no-op.
       // FROM SPEC: .claude/specs/pre-release-spec.md §3.7.
       // NOTE: If lint finds existing errors that predate Phase 3, triage
       //       them in sub-phase 4.3 (fix the high-severity ones, baseline
       //       the rest via updateLintBaseline and file follow-up issues).
       //       Do NOT baseline silently.
       checkReleaseBuilds = true
       abortOnError = true
   }
   ```

2. Do not touch any other block in this file. Phase 3 scope is strictly
   the lint block.

### Sub-phase 4.2: Run lint and triage

**Files:** (none — conditional on triage outcome)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Run lint from the repo root:
   ```bash
   ./gradlew lint 2>&1 \
       | tee .claude/test-results/2026-04-09_phase3_gate/lint.log
   ```
   Expected on clean green: exit code 0 with `BUILD SUCCESSFUL`.

2. **If lint exits 0:** copy the XML report into the gate results directory
   for the audit trail:
   ```bash
   cp app/build/reports/lint-results-debug.xml \
      .claude/test-results/2026-04-09_phase3_gate/lint-results-debug.xml
   ```
   Write the gate result:
   ```bash
   cat > .claude/test-results/2026-04-09_phase3_gate/gate-3.7.md <<'EOF'
   # §3.7 Lint clean — gate result

   **Status:** GREEN
   **Run log:** lint.log
   **Report:** lint-results-debug.xml
   **Config change:** app/build.gradle.kts:73 `abortOnError = true`
   EOF
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit. Proceed to Phase 5.

3. **If lint exits non-zero:** read the XML report and classify each
   `<issue severity="Error">` entry. For each error, the classification
   rule is:
   - Error is in a Phase 4-scope file (> 400 lines) → baseline it via
     `./gradlew updateLintBaseline` AND file a follow-up GH issue linking
     the baselined issue to Phase 4.
   - Error is in a file < 400 lines and is clearly a real defect → route
     as Bucket 3 (Phase 1 bounce).
   - Error is a new lint-check issue that the prior config suppressed and
     is not a defect (e.g., a new deprecation warning elevated to error) →
     suppress surgically with `@Suppress("<checkId>")` at the exact call
     site, with a `// WHY:` annotation explaining why suppression is
     correct. Do NOT add a project-wide suppression. **Exception:** If the
     lint check id belongs to Android Lint's Security category
     (`HardcodedDebugMode`, `TrustAllX509TrustManager`, `ExportedReceiver`,
     `ExportedService`, `ExportedContentProvider`, `SetJavaScriptEnabled`,
     `WorldReadableFiles`, `WorldWriteableFiles`,
     `UnsafeProtectedBroadcastReceiver`, etc.), escalate to Bucket 4 — do
     NOT suppress at the call site.
   - Error is unclear → escalate to the user (Bucket 4).

4. Re-run `./gradlew lint` after fixes/baselines land. Iterate until
   green.

5. Once green, copy the XML and write the gate result as in step 2.

**Verification:** `./gradlew assembleDebug && ./gradlew lint`

(Rationale: this phase changes build config AND runs lint. The final
sub-phase's verification runs both to prove the flip is non-destructive
and the lint gate actually closes green.)

---

## Phase 5 — Manual Smoke (spec §3.6)

Re-runs the two Phase 1 manual checklists on the Phase 3 release-candidate
build. This phase is driven by the human operator — Claude agents do not
drive manual testing. The deliverable is a commit of the ticked checklists
plus any defect filings.

### Sub-phase 5.1: Regression smoke re-run

**Files:** `.claude/test-flows/phase1-regression-smoke.md` (modify — tick boxes)
**Agent:** `general-purpose` (sonnet) — assists the operator with commit formatting only

**Steps:**

0. **Coverage rationale (§3.6 "across all features").** Spec §3.6 requires manual smoke "across all features". Per `.claude/test-flows/coverage-matrix.md`, the two manual checklists (`phase1-regression-smoke.md` + `phase1-voice-verification.md`) cover clipboard, macros, command mode, plugins, and voice. The remaining spec §2.1 features (predictive next-word, long-press coverage, modifier states, caps lock, modifier combos, basic typing, rapid input stress, mode switching, layout mode switching, SwiftKey visual parity) are verified by the automated §3.1 tier run — collectively §3.1 automation + the two manual checklists satisfy §3.6's "across all features" requirement. Record this rationale in `gate-3.6-regression.md` under a new "Coverage rationale" heading.

1. Confirm the RC build is installed and DevKey is the active IME
   (same as sub-phase 2.1 steps 2-3).

2. The operator walks `.claude/test-flows/phase1-regression-smoke.md`
   top-to-bottom on a physical device or emulator, ticking each checkbox
   as they verify the behavior. The file covers clipboard, macros,
   command mode, and plugins (per `coverage-matrix.md` Supplementary
   manual smoke section).

3. For each box NOT ticked (because the operator found a defect), file a
   GH issue per `CLAUDE.md` defect routing convention before ticking the
   box with a link to the issue:

   **PRIVACY:** describe failure modes in abstract terms — do not paste literal clipboard content, macro bodies, or voice transcripts into the issue body.
   ```bash
   gh issue create \
     --repo RobertoChavez2433/DevKey \
     --label "defect,category:<CAT>,area:<AREA>,priority:<P>" \
     --title "<title>" \
     --body "Discovered during Phase 3 §3.6 manual smoke on 2026-04-09."
   ```
   Replace the unticked `[ ]` with `[x] (blocked on #<issue-number>)`.

4. Save the modified checklist and stage it for the Phase 6 decision-gate
   commit.

5. Write the sub-gate result:
   ```bash
   cat > .claude/test-results/2026-04-09_phase3_gate/gate-3.6-regression.md <<'EOF'
   # §3.6 Manual smoke — regression checklist

   **Status:** GREEN | PARTIAL_GREEN | RED
   **Checklist:** .claude/test-flows/phase1-regression-smoke.md
   **Items ticked:** <n> / <total>
   **Defects filed:** <list of issue numbers>
   EOF
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

### Sub-phase 5.2: Voice verification re-run (conditional on B3)

**Files:** `.claude/test-flows/phase1-voice-verification.md` (modify — tick boxes)
**Agent:** `general-purpose` (sonnet) — commit formatting only

**Steps:**

1. **Skip this sub-phase entirely if sub-phase 3.3 wrote a BLOCKED
   gate artifact** (B3 still in force). The voice verification checklist
   cannot be meaningfully completed without the Whisper model. Record
   the skip:
   ```bash
   cat > .claude/test-results/2026-04-09_phase3_gate/gate-3.6-voice.md <<'EOF'
   # §3.6 Manual smoke — voice verification

   **Status:** BLOCKED on B3 (see gate-3.2.md)
   **Decision:** deferred until sub-phase 3.3 is unblocked.
   EOF
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit. Proceed to Phase 6 with §3.6 marked PARTIAL_GREEN.

2. **If sub-phase 3.3 produced a GREEN or PARTIAL_GREEN voice gate:** the
   operator walks `.claude/test-flows/phase1-voice-verification.md` on
   the RC build, ticking each checkbox. This is the manual counterpart
   to `test_voice.py` and specifically covers:
   - Microphone button visibility in a non-password text field.
   - First-tap `RECORD_AUDIO` permission prompt.
   - Permission-denied path (toast + graceful recovery).
   - Microphone-to-commit round-trip on real audio (operator speaks).

3. For any unticked box, file a defect per sub-phase 5.1 step 3.

4. Save the modified checklist and stage it for the Phase 6 commit.

5. Write the sub-gate result:
   ```bash
   cat > .claude/test-results/2026-04-09_phase3_gate/gate-3.6-voice.md <<'EOF'
   # §3.6 Manual smoke — voice verification

   **Status:** GREEN | PARTIAL_GREEN | RED
   **Checklist:** .claude/test-flows/phase1-voice-verification.md
   **Items ticked:** <n> / <total>
   **Defects filed:** <list of issue numbers>
   EOF
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

**Verification:** `./gradlew assembleDebug`

(Rationale: Phase 5 touches only `.md` files, but the final verification
ensures the RC build is still buildable at the exact commit where the
manual smoke artifacts land. This matters because the release tag will
be cut from whatever commit ultimately closes Phase 3.)

---

## Phase 6 — Decision Gate (spec §3.8)

Consolidates all six sub-gates into a single decision artifact and writes
the Phase 3 close commit. This phase is the **final phase** of the plan —
after it, the spec's Phase 4 (architecture refactor) is unblocked. Any
red from earlier phases prevents this phase from closing.

### Sub-phase 6.0: Open-defect audit (§2.4(b) and §2.4(c))

**Files:** `.claude/test-results/2026-04-09_phase3_gate/ghissue-audit.log` (create)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Run the §2.4(b) no-open-defects gate. Spec §2.4(b) blocks v1.0 release on any open critical/high defect. Capture both lists for the audit trail:
   ```bash
   gh issue list --repo RobertoChavez2433/DevKey --label "defect,priority:critical" --state open \
       > .claude/test-results/2026-04-09_phase3_gate/ghissue-audit.log
   echo "---" >> .claude/test-results/2026-04-09_phase3_gate/ghissue-audit.log
   gh issue list --repo RobertoChavez2433/DevKey --label "defect,priority:high" --state open \
       >> .claude/test-results/2026-04-09_phase3_gate/ghissue-audit.log
   ```
   **Rule:** any open issue in either list, **except #9 itself** (the Phase 3 gate tracking issue), blocks `PROCEED_TO_PHASE_4` and routes to a Phase 1 bounce. If the user wants to ship with known defects, escalate as Bucket 4.

2. Run the §2.4(c) GH #4 plugin security verification. Spec §2.4 "(b) gate plugin loading behind a debug-only flag for v1.0" requires GH #4 to be resolved (closed, or open with an explicit debug-flag-gating fix landed):
   ```bash
   gh issue view 4 --repo RobertoChavez2433/DevKey --json state,title,labels \
       >> .claude/test-results/2026-04-09_phase3_gate/ghissue-audit.log
   ```
   **Rule:** state must be `CLOSED`, OR if open the user must explicitly confirm the debug-flag gating landed per spec §2.4. Record the rationale (closed reason or user confirmation text) in the audit log.

3. Both audit results feed sub-phase 6.1 step 2 as new rows in the `decision.md` template. If either rule fires, sub-phase 6.2 routing must mark the outcome as `BOUNCE_TO_PHASE_1` (or `ESCALATE_TO_USER` if the user opts into shipping with known defects).

### Sub-phase 6.1: Consolidate sub-gate results

**Files:** `.claude/test-results/2026-04-09_phase3_gate/decision.md` (create)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Verify all sub-gate result files exist:
   ```bash
   ls .claude/test-results/2026-04-09_phase3_gate/gate-*.md
   ```
   Expected: at least `gate-3.2.md`, `gate-3.3.md`, `gate-3.4.md`,
   `gate-3.5.md`, `gate-3.6-regression.md`, `gate-3.6-voice.md`,
   `gate-3.7.md`. §3.1 tier stabilization is covered by the three tier
   summaries under `full/`, `compact/`, `compact_dev/` — verify those too.

2. Create the decision artifact by aggregating every sub-gate's Status
   field. The template:
   ```markdown
   # Phase 3 Regression Gate — Decision Artifact

   **Plan:** `.claude/plans/2026-04-09-pre-release-phase3.md`
   **Spec:** `.claude/specs/pre-release-spec.md` §6 Phase 3
   **Date:** 2026-04-09
   **Operator:** <name or handle>

   ## Sub-gate summary

   | Spec § | Gate | Status | Source |
   |---|---|---|---|
   | §3.1 | Tier stabilization — FULL | <status> | full/summary.md |
   | §3.1 | Tier stabilization — COMPACT | <status> | compact/summary.md |
   | §3.1 | Tier stabilization — COMPACT_DEV | <status> | compact_dev/summary.md |
   | §3.2 | Voice round-trip | <status> | gate-3.2.md |
   | §3.3 | Next-word prediction | <status> | gate-3.3.md |
   | §3.4 | Long-press coverage | <status> | gate-3.4.md |
   | §3.5 | SwiftKey visual diff | <status> | gate-3.5.md |
   | §3.6 | Manual regression smoke | <status> | gate-3.6-regression.md |
   | §3.6 | Manual voice verification | <status> | gate-3.6-voice.md |
   | §3.7 | Lint clean | <status> | gate-3.7.md |
   | §2.4(b) | No open critical/high defects except #9 | <status> | ghissue-audit.log |
   | §2.4(c) | GH #4 plugin security resolved | <status> | ghissue-audit.log |

   ## Decision

   **Outcome:** PROCEED_TO_PHASE_4 | BOUNCE_TO_PHASE_1 | BOUNCE_TO_PHASE_2 | ESCALATE_TO_USER

   **Rationale:** <one paragraph>

   ## Bucket-routed defects

   - Bucket 1 (stabilization-in-place, fixed in this plan): <list>
   - Bucket 2 (Phase 2 bounce): <list>
   - Bucket 3 (Phase 1 bounce): <list>
   - Bucket 4 (spec amendment): <list>

   ## Follow-ups for Phase 4

   - Phase 3 flipped `abortOnError = true` in `app/build.gradle.kts` —
     Phase 4 refactor should not revert unless explicitly part of a
     refactor-scope decision.
   - 12 Kotlin files currently > 400 lines (unchanged by Phase 3).
   - Any lint baseline entries added in Phase 4.2.
   ```
   Replace every `<placeholder>` token in this file with the actual value before staging for commit.

3. Fill in every `<status>` and `<list>` from the sub-gate files. Use
   `GREEN`, `PARTIAL_GREEN`, `RED`, or `BLOCKED` verbatim.

### Sub-phase 6.2: Decision routing

**Files:** (none — decision logic applied to pre-existing artifacts)
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Apply the decision logic over the Sub-gate summary table:
   - **PROCEED_TO_PHASE_4:** every row is GREEN or PARTIAL_GREEN, AND
     all PARTIAL_GREENs are spec-sanctioned (B4 visual references, B3
     voice demotion if accepted via spec amendment).
   - **BOUNCE_TO_PHASE_1:** any row is RED with a Bucket 3 diagnosis.
   - **BOUNCE_TO_PHASE_2:** any row is RED with a Bucket 2 diagnosis.
   - **ESCALATE_TO_USER:** any row is BLOCKED with a Bucket 4 diagnosis,
     OR B3 (voice model absent) remains unresolved, OR more than one
     cycle of Bucket 1 fixes has already run without reaching green.

2. Write the decision into the Rationale paragraph of `decision.md`
   (sub-phase 6.1 step 2).

3. **If the decision is not PROCEED_TO_PHASE_4:** do NOT write the
   close commit in sub-phase 6.3. Instead, surface the block in the
   Phase 6 summary and leave the gate open. The user decides the next
   step — re-open the relevant upstream phase, or amend the spec.

### Sub-phase 6.3: Write Phase 3 close commit

**Files:** (all prior sub-phase outputs) + git commit
**Agent:** `general-purpose` (sonnet)

**Steps:**

1. **Only run this sub-phase if sub-phase 6.2 produced
   `PROCEED_TO_PHASE_4`.** Otherwise STOP and surface via Phase 6
   summary.

2. Verify the working tree has only the expected stabilization changes:
   ```bash
   git status
   ```
   Expected modified:
   - `tools/e2e/e2e_runner.py` (sub-phase 1.1)
   - `tools/e2e/requirements.txt` (sub-phase 1.2)
   - `app/build.gradle.kts` (sub-phase 4.1)
   - `.claude/test-flows/tier-stabilization-status.md` (sub-phase 2.5)
   - `.claude/test-flows/phase1-regression-smoke.md` (sub-phase 5.1)
   - `.claude/test-flows/phase1-voice-verification.md` (sub-phase 5.2, only if B3 unblocked)
   - new under `.claude/test-results/2026-04-09_phase3_gate/`

   If any unexpected file is modified, pause and investigate before
   committing — this is the most likely place for a scope-creep escape.

3. Stage explicitly (do NOT use `git add -A` per `CLAUDE.md` safety
   protocol). Pick each path by name:
   ```bash
   git add tools/e2e/e2e_runner.py tools/e2e/requirements.txt
   git add app/build.gradle.kts
   git add .claude/test-flows/tier-stabilization-status.md
   git add .claude/test-flows/phase1-regression-smoke.md
   git add .claude/test-flows/phase1-voice-verification.md   # only if modified
   git add .claude/test-results/2026-04-09_phase3_gate/
   ```

4. Review staged diff before committing:
   ```bash
   git diff --staged
   ```
   Expected: the patch surface matches sub-phases 1.1, 1.2, 2.5, 4.1,
   5.1, 5.2, plus the new test-results directory. Reject anything else.

5. Ask the user for explicit commit authorization before running
   `git commit`. Per `CLAUDE.md`, commits are only created when the user
   explicitly asks — Phase 3 close is the kind of milestone commit that
   must have user sign-off. Do NOT self-authorize.

6. On user approval, create the commit (no Co-Authored-By per global
   instructions):
   ```bash
   git commit -m "$(cat <<'EOF'
   v1.0 Pre-Release Phase 3 Regression Gate — close

   Closes Phase 3 of the v1.0 pre-release spec (§6 Phase 3). All sub-gates
   green or spec-sanctioned partial-green. Phase 4 (architecture refactor)
   unblocked.

   Harness stabilization:
   - tools/e2e/e2e_runner.py: catch pytest.Skipped (BaseException)
   - tools/e2e/requirements.txt: declare pytest>=7.0

   Lint gate:
   - app/build.gradle.kts: flip abortOnError = true

   Tier stabilization status:
   - .claude/test-flows/tier-stabilization-status.md: mark COMPLETE
   - .claude/test-results/2026-04-09_phase3_gate/: per-tier + per-gate audit trail
   EOF
   )"
   ```

7. After commit, run `git status` to confirm a clean tree. Phase 3 closes
   here.

**Verification:** `./gradlew assembleDebug && ./gradlew lint`

(Rationale: the close commit must leave the repo in a state where both
`assembleDebug` and the new strict-lint gate pass. Running both as the
final verification of the final sub-phase proves the Phase 3 close is
durable and Phase 4 can start from a clean baseline.)
