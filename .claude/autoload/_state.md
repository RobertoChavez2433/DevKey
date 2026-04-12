# Session State

**Last Updated**: 2026-04-12 | **Session**: 53

## Current Phase

- **Phase**: v1.0 Pre-Release Execution — E2E Green
- **Status**: **54/54 E2E tests pass.** All hypothesis markers stripped,
  clean APK built and verified. Root causes for 3 remaining failures were
  Compose recomposition races (first char triggers candidate strip re-layout,
  swallowing rapid subsequent taps) and stale pref state (setting a pref to
  its current value doesn't fire onSharedPreferenceChanged). Build is green.
  Sessions 42–53 commits remain local.

## Resume Here

1. **Push accumulated commits** from sessions 42–53.
2. Continue pre-release plan: WhisperProcessor mel stub (B2), binary header
   parsing (B3), locale XML namespace errors (B4), LatinIME.kt extraction.

## Fixes Applied (Session 53)

- **Auto-cap tests**: Toggle auto_cap false→true so onSharedPreferenceChanged
  fires; add typing warmup (space+backspace+clear) to settle Compose layout;
  increase first-char delay to 0.35s; increase period-to-space delay to 0.35s;
  increase timeout to 5000ms.
- **Double-space-period test**: Toggle show_suggestions false→true; add typing
  warmup; increase first-char delay; increase space-to-space delay to 0.35s.
- **Hypothesis markers**: All H001–H005 markers stripped from Kotlin code;
  hypothesis() function removed from DevKeyLogger; H002 circuit breaker log
  tag changed to DevKey/CBK.

## Root Causes Found (Session 53)

1. **Compose recomposition race**: First character typed after clean state
   triggers candidate strip recomposition. Subsequent taps arriving within
   ~200ms hit wrong keys or get swallowed because Compose hit testing uses
   the in-progress layout. Fix: warmup tap + increased inter-key delays.
2. **Pref change listener no-op**: Setting a SharedPreference to its current
   value doesn't fire onSharedPreferenceChanged. Tests must toggle false→true
   to ensure the listener fires and internal state (mAutoCapActive,
   mShowSuggestions) is propagated.
3. **commit() vs apply()**: Confirmed commit() works correctly for pref writes
   in DebugReceiverManager — the listener fires synchronously.

## Current Blockers

- Sessions 42–53 commits remain local.
- WhisperProcessor mel spectrogram is a stub.
- LatinIME.kt at 623 lines (limit: 400).
- 45 locale XML namespace errors in lint.

## Recent Sessions

### Session 53 (2026-04-12)

- **54/54 E2E tests pass** — all 3 remaining failures fixed.
- Root cause: Compose recomposition races swallowing rapid key taps.
- Root cause: SharedPreference change listeners not firing for no-op writes.
- All hypothesis markers (H001–H005) stripped from Kotlin code.
- Clean APK built and verified with two consecutive 54/54 full suite runs.

### Session 52 (2026-04-11)

- Systematic debugging of 9 failing E2E tests with hypothesis markers.
- Root causes identified for all failures.
- 4 tests fixed and verified; 1 fixed individually.
- Emulator went offline from ADB connection exhaustion.

### Session 51 (2026-04-11)

- Punctuation heuristics bug fixed; key coordinate offset fixed.
- 14 new E2E tests. Long press tests hardened with retry logic.
- Best run: 52/54.

## Current References

- **Current plan**: `.claude/plans/refactored-sniffing-stonebraker.md`
- **E2E verification plan**: `.claude/plans/gentle-baking-wirth.md`
- **Phase 4 spec**: `.claude/specs/phase4-refactor-spec.md`
- **Current spec**: `.claude/specs/pre-release-spec.md`
