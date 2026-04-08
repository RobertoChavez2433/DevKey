# Session State

**Last Updated**: 2026-04-08 | **Session**: 37

## Current Phase
- **Phase**: .claude Directory Restructure
- **Status**: Plan heavily revised + rescoped (938 → 1545 lines). Ready for `/implement` once GH labels are created and jcodemunch re-indexed. Live skills updated for interim use.

## HOT CONTEXT - Resume Here

### EXACTLY WHERE WE LEFT OFF

**Session 37 was a plan-revision + live-skill-update session. No source code changed. Plan at `.claude/plans/2026-03-17-claude-directory-restructure.md` is now the spec for the restructure and reflects all current decisions.**

This session accomplished:
1. **jcodemunch MCP verified functional** — Standalone Python server at `C:\Users\rseba\Projects\jcodemunch-mcp\`, registered in `.mcp.json:10` and `~\.claude.json`. 15 repos indexed. Local HK index is stale (2026-03-05, empty git_head) — needs refresh before `/tailor` is useful.
2. **Staleness audit against 2026-03-17 plan** — Found: all 4 in-flight plans (kotlin-migration, dead-code-cleanup, coordinate-calibration, 123-fix-e2e-harness) are actually committed (22a3050, 73e211b, 31d33f7, 386a25b) and need to move to `completed/`; `.claude/plans/completed/` already exists with 9 archived plans; `docs/research/` and `.claude/docs/guides/` must be preserved; one of the P0 stale refs (`_state.md:85` test-results path) no longer exists and SKIP it; other 5 P0 refs confirmed still broken.
3. **Plan rescoped to include full tailor + writing-plans pipeline** (Sub-phases 5.4a/b/c) — adapted from Field Guide, DevKey-specific: 9-step jcodemunch sequence with Kotlin credential blocklist (local.properties, keystores, signing), 9 DevKey patterns (IME lifecycle, modifier state, key dispatch, Compose layout, theme tokens, JNI, coroutines, Room, settings), 12-row DevKey ground-truth table (KeyCodes, LayoutMode, DevKeyTheme, JNI package paths, calibration.json), IME privacy rule (never log user text). Created plan-writer-agent, plan-fixer-agent, completeness-review-agent definitions.
4. **Implement pipeline fully rewritten** (Sub-phases 4.5 + 5.8) — **NO HEADLESS MODE.** Everything via Agent tool (Task). New per-phase loop: implementer → build → **one** completeness-review-agent (spec intent vs. phase) → checkpoint. End-of-plan gate runs ONCE after all phases: `./gradlew lint` + parallel 3-sweep (code-review + security + completeness) + fixer loop, max 3 cycles with monotonicity check. Checkpoint JSON schema included in plan.
5. **Defects migrated from files to GitHub Issues** — Entirely eliminated per-concern defect files. Sub-phase 1.1 drops `.claude/defects/` creation. Sub-phase 1.3 rewritten: creates GH labels (`defect`, `category:{IME,UI,MODIFIER,NATIVE,BUILD,TEXT,VOICE,ANDROID}`, `area:{7 values}`, `priority:{4}`), files all 7 existing defects from `_defects.md` as `gh issue create` commands (including closing the already-fixed ComposeView lifecycle defect with 386a25b ref), archives `_defects.md` → `logs/defects-archive-final.md`. `defects-integration.md` reference renamed to `github-issues-integration.md`. All agent context_loading blocks replaced with `gh issue list --label "area:*"` queries.
6. **Live skills updated for immediate use** — Updated `.claude/CLAUDE.md`, `skills/implement/SKILL.md`, `skills/end-session/SKILL.md`, `skills/resume-session/SKILL.md`, `agents/test-wave-agent.md`, `agents/test-orchestrator-agent.md`, `skills/test/references/output-format.md`, `logs/README.md` — all now reference GH Issues via `gh` CLI instead of `_defects.md`. Grep for `_defects.md` in live files is now clean (only plan + to-be-archived file + completed/archived plans remain).

### What Needs to Happen Next

**Priority 1: Set up GH labels and migrate existing defects**
Manual/scripted step before running `/implement`:
1. Create all labels per Sub-phase 1.3 Step 1 (`gh label create ...`)
2. File the 7 existing defects from `_defects.md` as issues per the table in Sub-phase 1.3 Step 2 (close the ComposeView lifecycle one immediately with ref 386a25b)
3. Archive `_defects.md` → `logs/defects-archive-final.md`

**Priority 2: Re-index jcodemunch with source_root**
`mcp__jcodemunch__index_folder(path="C:\\Users\\rseba\\Projects\\Hackers_Keyboard_Fork", incremental=false, use_ai_summaries=true)`. Record the new repo key (`local/Hackers_Keyboard_Fork-<hash>`) — needed by debug-research-agent and tailor skill.

**Priority 3: Execute restructure plan**
Run `/implement .claude/plans/2026-03-17-claude-directory-restructure.md`. Plan is now 1545 lines, 6 phases, ~59 files created / ~18 modified. Plan counts as its own spec.

**Priority 4: Commit backlog**
Still untouched from Sessions 27, 31-36. Plus all of Session 37's live-skill edits. Plan says one commit per phase during /implement; backlog should be its own commit(s) first.

### Key Testing Resumption Guide

```bash
# Install + force restart IME:
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell "am force-stop dev.devkey.keyboard"
adb -s emulator-5554 shell "ime enable dev.devkey.keyboard/.LatinIME"
adb -s emulator-5554 shell "ime set dev.devkey.keyboard/.LatinIME"
```

## Blockers

- **Wasted space in Symbols/other keyboard modes**: Layout doesn't fill available area in non-Normal modes
- **Whisper model files**: Need `whisper-tiny.en.tflite` and `filters_vocab_en.bin` -> `app/src/main/assets/`
- **Compose UI tests blocked on API 36**: Espresso incompatible with Android 16
- **Uncommitted state/docs backlog**: Session 36 restructure plan + all Session 37 live-skill edits + session state files not yet committed. Most source-code backlog (dead code, calibration, 123 fix, Kotlin migration) is already committed (73e211b, 31d33f7, 386a25b, 22a3050).

## Recent Sessions

### Session 37 (2026-04-08)
**Work**: Plan-revision + live-skill-update session. Staleness-audited the 2026-03-17 restructure plan against current repo (4 committed plans need moving, P0 refs still broken minus one). Rescoped plan (938 → 1545 lines) to add full tailor + writing-plans pipeline adapted from Field Guide for Kotlin/Android/jcodemunch, including plan-writer/plan-fixer/completeness-review agents. Rewrote implement pipeline to drop headless mode entirely (Agent tool only) and restructure reviews: one completeness-review-agent per phase, full 3-sweep + fixer loop only at end of plan (max 3 cycles, monotonicity check). Eliminated per-concern defect files: all defect tracking now GitHub Issues on `RobertoChavez2433/DevKey` with `defect` + `category:*` + `area:*` + `priority:*` label taxonomy. Updated 8 live files (CLAUDE.md, 3 skills, 2 agents, output-format, logs/README) to use `gh` CLI interim. jcodemunch MCP confirmed functional but HK index stale.
**Decisions**: No headless `claude --bare` anywhere. Per-phase review is completeness-only (spec intent vs phase); 3-agent adversarial sweep moved to end of plan. Defects = GH Issues, no file-based tracking, no rotation. Plan serves as its own spec (no separate spec file). `gh issue` commands explicitly allowed in end-session/resume-session despite NO-git-commands rule (which applies only to code git ops). `.claude/plans/completed/` already exists — don't recreate.
**Next**: (1) Create GH labels + file 7 existing defects as issues, (2) re-index jcodemunch with source_root, (3) run `/implement` on the restructure plan, (4) commit Sessions 27/31-37 backlog.

### Session 36 (2026-03-17)
**Work**: Audited DevKey + Field Guide App .claude/ directories with 4 Sonnet agents. Brainstormed restructure via `/brainstorming`. Wrote 6-phase implementation plan mirroring Field Guide patterns: systematic-debug skill (10-phase, HTTP server, DevKeyLogger), specialist agents, rules extraction, hooks, writing-plans separation.
**Decisions**: Scaled to DevKey (not full Field Guide mirror). HTTP debug server + DevKeyLogger class. 7 concern-area defect files. 3 specialist agents + code-review + security. Split brainstorming/writing-plans with adversarial review only in writing-plans. block-orchestrator-writes + pre-agent-dispatch hooks.
**Next**: Execute restructure plan via `/implement`, commit all changes, on-device verification.

### Session 35 (2026-03-05)
**Work**: Implemented dead code cleanup Phases 1-5 via `/implement` skill. Orchestrator dispatched Sonnet agents per phase, build-verified after each. 33 files modified, 1 deleted (LatinIMEUtil.kt). All quality gates pass (build + tests + completeness).
**Decisions**: Created fontVoiceMic=18.sp token (preserves mic emoji size). Kept @JvmField on Keyboard.Key, SettingsRepository, KeyboardSwitcher (perf-sensitive). Deleted LatinIMEUtil.kt entirely. Standardized TAG to DevKey/<ClassName>.
**Next**: Commit all changes (one commit per phase), on-device verification, fix Symbols mode wasted space.

### Session 34 (2026-03-05)
**Work**: Research session. Indexed codebase with jcodemunch MCP. Ran 4 Opus agents: dead code audit, verification, plan creation+review, Kotlin quality audit. Created 6-phase cleanup plan covering dead code removal, theme alias migration, @Jvm annotation cleanup, GCUtils removal, Kotlin idiom modernization, and DRY refactoring.
**Decisions**: Java BinaryDictionary.java MUST stay (JNI bridge). CandidateView @JvmOverloads MUST stay (XML inflation). keyLabelSize→fontVoiceMic (18sp, not fontKey 14sp). Keyboard.Key @JvmField deferred (performance). Hungarian notation: opportunistic only.
**Next**: Implement Phase 1 (zero-risk dead code removal), commit all uncommitted work, continue cleanup phases.

## Active Plans

- **.claude Directory Restructure** — PLAN REVISED (Session 37), NOT YET IMPLEMENTED. 6 phases, 1545 lines. Now includes full tailor + writing-plans pipeline and Agent-tool-only implement flow. Plan: `.claude/plans/2026-03-17-claude-directory-restructure.md`
- **E2E Testing** — IN PROGRESS (FULL mode 7/8 PASS Session 30, COMPACT/COMPACT_DEV pending)
- **Dead Code Cleanup** — COMPLETE + COMMITTED (73e211b, Session 35). Phase 6 deferred. Plan to be moved to `plans/completed/` in restructure Phase 1.4.
- **Coordinate Calibration** — COMPLETE + COMMITTED (31d33f7, Sessions 31-32). Plan to be moved to `plans/completed/` in restructure Phase 1.4.
- **123 Fix + E2E Harness** — COMPLETE + COMMITTED (386a25b, Session 27). Plan to be moved to `plans/completed/` in restructure Phase 1.4.
- **Kotlin Migration** — FULLY COMPLETE + COMMITTED (22a3050, Session 25). Plan still at top level of `plans/`; move to `completed/` in restructure Phase 1.4.

## Reference
- **Restructure plan**: `.claude/plans/2026-03-17-claude-directory-restructure.md`
- **Dead code cleanup plan**: `.claude/plans/completed/dead-code-cleanup-plan.md`
- **Test skill redesign**: `.claude/memory/test-skill-redesign.md`
- **Key coordinate map**: `.claude/logs/key-coordinates.md` (UPDATED Session 31)
- **Calibration design**: `docs/plans/2026-03-04-coordinate-calibration-design.md`
- **Architecture**: `docs/ARCHITECTURE.md`
