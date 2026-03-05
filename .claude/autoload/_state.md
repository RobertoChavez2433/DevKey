# Session State

**Last Updated**: 2026-03-05 | **Session**: 35

## Current Phase
- **Phase**: Dead Code Cleanup + Kotlin Quality
- **Status**: Phases 1-5 IMPLEMENTED. Build + tests pass. Not yet committed.

## HOT CONTEXT - Resume Here

### EXACTLY WHERE WE LEFT OFF

**Dead code cleanup plan fully implemented (Phases 1-5). All quality gates pass. Changes NOT committed.**

This session accomplished:
1. **Ran `/implement` on dead-code-cleanup-plan.md** — Orchestrator dispatched agents for Phases 1-5
2. **Phase 1**: Removed dead methods (LatinIMEUtil, EditingUtil, TextEntryState), dead theme aliases (14), RingCharBuffer + caller, dead stubs/flags across LatinIME/LatinKeyboard/Suggest
3. **Phase 2**: Migrated `keyboardBackground→kbBg` (16 sites, 11 files), `keyFill→keyBg` (5 sites, 4 files), created `fontVoiceMic=18.sp` token for VoiceInputPanel
4. **Phase 3**: Removed `@JvmStatic` (24 occurrences, 10 files), `@JvmOverloads`/`@JvmField` from EditingUtil.Range+SelectedWord, `@JvmField` from 7 files. Kept CandidateView, Keyboard.Key, SettingsRepository per plan.
5. **Phase 4**: Replaced GCUtils retry loop with simple try/catch OOM handling. Deleted `LatinIMEUtil.kt` entirely (empty after Phase 1 + 4).
6. **Phase 5**: Replaced Java API calls with Kotlin equivalents (Character.*, TextUtils, Arrays.fill, System.arraycopy, Math.round). Standardized TAG naming to `DevKey/<ClassName>`.
7. **All quality gates pass**: `assembleDebug` BUILD SUCCESSFUL, `test` all passing, completeness verified

### What Needs to Happen Next

**Priority 1: Commit all changes**
Sessions 27, 31-35 all have uncommitted work. Dead code cleanup (Session 35) should be its own commit(s) — one per phase for easy bisection per plan's git strategy.

**Priority 2: On-device verification**
Install updated APK on emulator, verify typing/suggestions/keyboard switching still work after cleanup.

**Priority 3: Fix Symbols mode wasted space**
Layout doesn't fill available area in non-Normal modes — long-standing blocker.

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
- **Large uncommitted backlog**: Sessions 27, 31-35 changes not committed

## Recent Sessions

### Session 35 (2026-03-05)
**Work**: Implemented dead code cleanup Phases 1-5 via `/implement` skill. Orchestrator dispatched Sonnet agents per phase, build-verified after each. 33 files modified, 1 deleted (LatinIMEUtil.kt). All quality gates pass (build + tests + completeness).
**Decisions**: Created fontVoiceMic=18.sp token (preserves mic emoji size). Kept @JvmField on Keyboard.Key, SettingsRepository, KeyboardSwitcher (perf-sensitive). Deleted LatinIMEUtil.kt entirely. Standardized TAG to DevKey/<ClassName>.
**Next**: Commit all changes (one commit per phase), on-device verification, fix Symbols mode wasted space.

### Session 34 (2026-03-05)
**Work**: Research session. Indexed codebase with jcodemunch MCP. Ran 4 Opus agents: dead code audit, verification, plan creation+review, Kotlin quality audit. Created 6-phase cleanup plan covering dead code removal, theme alias migration, @Jvm annotation cleanup, GCUtils removal, Kotlin idiom modernization, and DRY refactoring.
**Decisions**: Java BinaryDictionary.java MUST stay (JNI bridge). CandidateView @JvmOverloads MUST stay (XML inflation). keyLabelSize→fontVoiceMic (18sp, not fontKey 14sp). Keyboard.Key @JvmField deferred (performance). Hungarian notation: opportunistic only.
**Next**: Implement Phase 1 (zero-risk dead code removal), commit all uncommitted work, continue cleanup phases.

### Session 33 (2026-03-04)
**Work**: Ran /test --full (partial). Wave 0 + Wave 1 all PASS (ime-setup, typing, modifier-states, mode-switching). Identified fundamental test skill design flaws: agents too slow, don't write docs, too expensive for mechanical ADB. Documented redesign plan.
**Decisions**: Test skill needs full redesign. Orchestrator should do ADB directly; agents only for vision. Broadcast-verified coords from S32 are wrong (~100px low); key-coordinates.md calibrated values are correct.
**Next**: Redesign test skill, commit all uncommitted work, fix Symbols mode wasted space.

### Session 32 (2026-03-04)
**Work**: Ran /test --full. DevKeyMap broadcast calibration verified ON-DEVICE (53 keys). Haiku wave agent failed (wrong sleep, confused by Symbols mode). Fixed 4 test skill issues: Sonnet agents, ADB batching, orchestrator-side calibration, Normal mode reset. Added "wasted space in Symbols mode" blocker.
**Decisions**: Wave agents use Sonnet not Haiku. Orchestrator owns calibration and passes coordinate table to agents. ADB commands batched in single Bash calls. ime-setup force-stops IME for clean Normal mode start.
**Next**: Re-run /test --full with fixed skill, commit all uncommitted work, fix Symbols mode wasted space.

## Active Plans

- **Dead Code Cleanup** — PHASES 1-5 IMPLEMENTED, NOT YET COMMITTED (Session 35). Phase 6 (DRY refactoring) deferred. Plan: `.claude/plans/dead-code-cleanup-plan.md`
- **Kotlin Migration** — FULLY COMPLETE + COMMITTED (Session 25, 4 commits)
- **123 Fix + E2E Harness** — IMPLEMENTED, NOT YET COMMITTED (Session 27)
- **E2E Testing** — IN PROGRESS (FULL mode 7/8 PASS Session 30, COMPACT/COMPACT_DEV pending)
- **Coordinate Calibration** — IMPLEMENTED + VERIFIED ON-DEVICE, NOT YET COMMITTED (Sessions 31-32)
- **Test Skill Fixes** — APPLIED but INSUFFICIENT (Sessions 32-33). Fundamental redesign needed — see `memory/test-skill-redesign.md`.

## Reference
- **Dead code cleanup plan**: `.claude/plans/dead-code-cleanup-plan.md`
- **Test results**: `.claude/test-results/2026-03-04_0954_full/` (Session 33, partial: Wave 0+1 only)
- **Test skill redesign**: `.claude/memory/test-skill-redesign.md`
- **Key coordinate map**: `.claude/logs/key-coordinates.md` (UPDATED Session 31)
- **Calibration design**: `docs/plans/2026-03-04-coordinate-calibration-design.md`
- **Architecture**: `docs/ARCHITECTURE.md`
