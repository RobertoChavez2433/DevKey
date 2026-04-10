# Tailor Manifest — v1.0 Pre-Release Phase 3 (Regression Gate)

**Spec:** `.claude/specs/2026-04-08-pre-release-vision-spec.md` §6 Phase 3
**Slug:** `pre-release-phase3`
**Tailor date:** 2026-04-09
**Scope:** Phase 3 of the umbrella pre-release spec — the **Regression Gate**.

## Phase 3 is execution-shaped, not code-shaped

Phase 3 does **not** land new features, new test flows, or new production code. All
that work is owned by Phase 1 (features) and Phase 2 (regression infrastructure).
Phase 3 is:

1. Run the full Python harness (`tools/e2e/e2e_runner.py`) on all three layout
   modes (FULL / COMPACT / COMPACT_DEV) until each reports zero reds.
2. Run each named feature flow (§3.2 voice, §3.3 next-word, §3.4 long-press,
   §3.5 visual-diff) and assert green.
3. Re-run the Phase 1 manual checklists (§3.6 `phase1-voice-verification.md`,
   `phase1-regression-smoke.md`).
4. Run `./gradlew lint` and record zero remaining issues (§3.7).
5. **Decision gate (§3.8):** if any of the above is red, return to Phase 1 /
   Phase 2 (by defect class) and rerun. No Phase 4 start until Phase 3 is all-green.

The Phase 3 plan therefore has two deliverable kinds:
- **Runbooks / checklists:** how to reproducibly invoke each gate.
- **Stabilization fixes:** small, targeted repairs to the test harness or test
  modules that are discovered during the stabilization run itself. App-side
  defects are out of Phase 3 scope (they bounce back to Phase 1 per §3.8).

## Files analyzed

**Specs & checklists**
- `.claude/specs/2026-04-08-pre-release-vision-spec.md` (§6 Phase 3 focus)
- `.claude/test-flows/registry.md`
- `.claude/test-flows/tier-stabilization-status.md` (the handoff from Phase 2 sub-phase 5.2)
- `.claude/test-flows/coverage-matrix.md` (Phase 3 gate → mechanism mapping)
- `.claude/test-flows/phase1-regression-smoke.md`
- `.claude/test-flows/phase1-voice-verification.md`
- `.claude/test-flows/long-press-expectations/{compact,compact_dev,full,symbols}.json`
- `.claude/test-flows/swiftkey-reference/README.md` + `compact-dark.png` +
  `compact-dark-findings.md`
- `.claude/skills/test/SKILL.md`

**Python harness**
- `tools/e2e/e2e_runner.py`
- `tools/e2e/requirements.txt`
- `tools/e2e/lib/{driver,keyboard,diff,adb,audio}.py`
- `tools/e2e/tests/{test_voice,test_next_word,test_long_press,test_visual_diff,test_modes,test_clipboard,test_macros,test_command_mode,test_plugins}.py`

**Node driver server**
- `tools/debug-server/server.js`
- `tools/debug-server/wave-gate.js`
- `tools/debug-server/adb-exec.js`

**Build**
- `app/build.gradle.kts` (lint block, line 71)
- `app/src/main/assets/` (whisper model presence — §5 voice)

**Instrumentation cross-reference (read-only, to confirm Phase 2 signals landed)**
- `PluginManager.kt` (`plugin_scan_complete` — line 245, 257)
- `KeyPressLogger.kt` (`long_press_fired` — line 24)
- `LatinIME.kt` (`layout_mode_set` — line 479)
- `DevKeyKeyboard.kt` (`layout_mode_recomposed` — line 111)
- `KeyMapGenerator.kt` (`keymap_dump_complete` — line 200)
- `CandidateView.kt` (`candidate_strip_rendered`)
- `VoiceInputEngine.kt` (`state_transition`, `processing_complete`)
- `ClipboardPanel.kt`, `MacroGridPanel.kt` (`panel_opened`)

## Patterns discovered (execution patterns, not code patterns)

1. [Tier-stabilization iteration loop](patterns/tier-stabilization-loop.md) —
   how the human operator cycles FULL / COMPACT / COMPACT_DEV to green.
2. [Driver-gated wait pattern](patterns/driver-gated-wait.md) — how every new
   Phase 2 test replaces sleeps with `driver.wait_for(...)` assertions.
3. [Graceful skip pattern (currently broken)](patterns/graceful-skip.md) — how
   the test modules handle missing references / models, and why the current
   `e2e_runner.py` cannot honor it.
4. [Lint gate mechanism](patterns/lint-gate.md) — how §3.7 "lint clean" is
   interpreted given `abortOnError = false` in `app/build.gradle.kts`.
5. [Manual smoke re-run pattern](patterns/manual-smoke-rerun.md) — how the
   two Phase 1 checklists feed into §3.6.
6. [Defect triage classification](patterns/defect-triage.md) — §3.8 decision
   gate: which reds go back to Phase 1 vs Phase 2 vs stabilization-in-place.

## Ground-truth discrepancies / blockers (see `ground-truth.md`)

**Critical blockers discovered (MUST be resolved before Phase 3 can report green):**

1. **`pytest.skip()` incompatibility with `e2e_runner.py`.** Modern pytest
   (installed 9.0.2) raises `_pytest.outcomes.Skipped`, a `BaseException`
   subclass. `e2e_runner.py:101` catches only `Exception`, so any test that
   calls `pytest.skip(...)` (voice, visual-diff, long-press, clipboard,
   macros, command-mode, plugins — 7 of 9 new test modules) will bubble out
   of the runner loop and crash the whole harness run. Fix in Phase 3 as
   stabilization-in-place: catch `BaseException` (or import `Skipped` and
   classify as a third `skipped` bucket).

2. **`pytest` is not declared in `tools/e2e/requirements.txt`.** Modules
   lazy-import it for the skip sentinel only, but a clean venv that installs
   `-r requirements.txt` will ImportError on the first test that hits a skip
   path. Add `pytest>=7.0` to requirements OR replace with a local
   `HarnessSkip(Exception)` sentinel and a matching runner branch.

3. **Whisper TF Lite model files ABSENT** from `app/src/main/assets/`.
   Only `.gitkeep` + `MODEL_PROVENANCE.md` present. Spec §5.2 (Phase 1.1)
   is prerequisite work for Phase 3.2 voice round-trip gate — cannot report
   green on §3.2 until either (a) model files land, or (b) voice gate is
   explicitly deferred to v1.1 via a spec amendment.

4. **SwiftKey reference capture gaps.** Only `compact-dark.png` exists.
   Spec §4.4.1 already DEFERS light theme. `test_visual_full_dark` and
   `test_visual_compact_dev_dark` will SKIP (once blocker #1 is fixed).
   §3.5 "SwiftKey visual diff within tolerance" can only gate on the
   captured mode set unless Phase 1.2 capture work lands the remaining
   dark-theme references first.

5. **`command_mode_auto_enabled` instrumentation not landed.** Grep of
   `app/src/main/java` finds zero emit sites. `test_command_mode.py` SKIPs
   on timeout, but that SKIP will crash the runner per blocker #1. Even
   after fixing #1, §3.6 manual-smoke must cover command mode instead of
   the automated flow until Phase 2 sub-phase 4.8 instrumentation lands.

**Non-blocking discrepancies:**

6. `test_modes.py::test_abc_returns_to_normal` uses `KEYCODE_ALPHA = -200`.
   Not verified against `KeyData.kt`. Phase 3 stabilization must confirm
   this is the correct constant or fix it before the test is trusted.
7. `test_modes.py` mixes `time.sleep(0.5)` (three call sites) with
   driver-gated waits. This is a sleep-based path that the Phase 2 plan
   §2.2 explicitly calls out as the thing being eliminated; acceptable for
   Phase 3 if the test is green, but flag as a Phase-2 cleanup miss.
8. 12 Kotlin files currently > 400 lines — exclusively Phase 4 scope, but
   recorded in `ground-truth.md` so the Phase 3 plan does not accidentally
   bleed into refactor work.

## Open questions for plan author

- How does Phase 3 report "tier green" artifact? (proposal: commit an updated
  `tier-stabilization-status.md` with the green SHAs, per its own "Next step"
  section.)
- Does §3.7 "lint clean" require `abortOnError = true` to be flipped, or is
  "zero reported issues in lint-results.xml" the success criterion? (see
  `patterns/lint-gate.md` for options.)
- Does the §3.5 visual-diff gate pass if only `compact-dark` is asserted and
  the other modes SKIP? (spec §4.4.1 defers light; dark-mode capture gaps
  are still listed as Phase 1 backlog — ambiguous.)
- Should Phase 3.2 voice gate be demoted to "model-present smoke" if
  `whisper-tiny.en.tflite` is not sourced in time? Spec §5.2 makes it a
  prerequisite but does not address the schedule risk.

## Confirmation: Phase 2 jcodemunch research steps

Phase 2 of the tailor skill was partially applied (Phase 3 is execution, so
some steps reduce to no-ops):

- [x] Step 1 — `mcp__jcodemunch__index_folder` run (incremental, 2026-04-09)
- [x] Step 2 — File outlines: not applicable (no production `.kt` files under
      modification; instrumentation signals verified by `Grep` instead).
- [x] Step 3 — Dependency graph: covered via Phase 2 tailor artifacts
      (`.claude/tailor/2026-04-08-pre-release-phase2/dependency-graph.md`) —
      Phase 3 relies on the same graph, no new nodes.
- [x] Step 4 — Blast radius: N/A (Phase 3 does not mutate production code).
- [x] Step 5 — Find importers: N/A.
- [x] Step 6 — Class hierarchy: N/A.
- [x] Step 7 — Dead code: N/A (Phase 3 cleanup = Phase 4).
- [x] Step 8 — Symbol search: `Grep` for every Phase 2 instrumentation event
      confirmed 10 emit sites across the affected files (see manifest "Files
      analyzed" block).
- [x] Step 9 — Symbol source: inlined into `ground-truth.md` where relevant
      (KeyMapGenerator.kt:200, LatinIME.kt:479, PluginManager.kt:245,
      DevKeyKeyboard.kt:111, KeyPressLogger.kt:24, CandidateView.kt).

Justification for partial application: Phase 3 has no "files to modify" list
in the spec. Its artifacts are runbooks, checklists, and the decision-gate
outcome. jcodemunch's Kotlin-first toolchain adds no signal to test-harness
(Python / Node) files; direct reads + Grep are the correct substitutes.

## Phase 5 gap-fill

Not dispatched. All blockers and ground-truth items were resolved by direct
reads + Grep + a live `pytest` import check for blocker #1.
