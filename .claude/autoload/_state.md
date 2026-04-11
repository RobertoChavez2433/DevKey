# Session State

**Last Updated**: 2026-04-11 | **Session**: 50

## Current Phase

- **Phase**: v1.0 Pre-Release Execution — Feature Fixes + Test Stabilization
- **Status**: All 40 E2E tests green. Word learning consolidated to single
  canonical source. Autocorrect suppression fixed to only honor explicit
  user additions. Build green.

## Resume Here

1. Continue pre-release plan Phase A: fix WhisperProcessor mel spectrogram stub
   (B2) and binary header parsing (B3).
2. Phase B: fix 45 locale XML namespace errors (B4), add POST_NOTIFICATIONS
   permission (B5), retune keyBgSpecial token (S1).
3. Phase C: extract ~206 lines from LatinIME.kt (currently 623, target ≤400).
4. Push accumulated commits from sessions 42-50.
5. Close GH #9 after architecture gate passes.

## Important Facts

- Always use the Windows Android emulator, never the Samsung physical device.
- The emulator IME reaches the host debug server via `10.0.2.2`, not
  `127.0.0.1`; the harness translates automatically.
- Debug server port is **3950** — all E2E harness defaults updated to 3950.
- `SessionDependencies.commitWord()` is the ONLY canonical path for word
  learning. No direct calls to `LearningEngine.onWordCommitted()` elsewhere.
- `AutocorrectEngine.getCorrection()` uses `getCustomWords()` (user-added only),
  not `getLearnedWords()` (all committed words). Only explicit long-press
  "Add to dictionary" suppresses autocorrect.
- Root package now has exactly 6 files: LatinIME, LatinIMEBackupAgent,
  LatinIMEPrefs, Main, NotificationReceiver, InputLanguageSelection.
- GH #27: Voice 30-second limit — chunking deferred to post-v1.0.

## Current Blockers

- WhisperProcessor mel spectrogram is a stub (returns zeros).
- LatinIME.kt at 623 lines (limit: 400).
- 45 locale XML namespace errors in lint.
- POST_NOTIFICATIONS permission missing.
- Sessions 42-50 commits remain local.

## Recent Sessions

### Session 50 (2026-04-11)

- **Consolidated word learning**: created `SessionDependencies.commitWord()` as
  the single canonical path. Replaced 3 scattered `onWordCommitted()` call sites
  (InputHandlers, SuggestionPicker, KeyboardDynamicPanel).
- **Fixed autocorrect suppression**: added `customWordsCache` + `getCustomWords()`
  to LearningEngine. AutocorrectEngine now only suppresses corrections for
  explicitly user-added words, not auto-learned words.
- **Fixed E2E port defaults**: updated 4 hardcoded `3948` references to `3950`
  across driver.py, adb.py, test_voice.py, e2e_runner.py.
- All 40 E2E tests green. Build green.

### Session 49 (2026-04-11)

- Full codebase audit against pre-release-spec.md — produced comprehensive
  implementation plan covering all remaining blockers (B1-B9) and should-fix
  items (S1-S4).
- Wave 8 DI seam refactor confirmed complete.

### Session 48 (2026-04-11)

- Completed Wave 8 DI seam refactor: all collaborators decoupled from LatinIME,
  ImeState extracted, interfaces defined.
- All 40 E2E tests passing (0 failures, 0 xfailed) after fixing autocorrect
  test infrastructure (dictionary gate, learned words clearing, circuit breaker
  tuning).
- Delete key long-press fixed (accelerating repeat instead of escape).
- Key press preview popup implemented.
- Voice E2E fully verified with punctuation/proper nouns.

## Current References

- **Current plan**: `.claude/plans/refactored-sniffing-stonebraker.md`
- **Phase 4 spec**: `.claude/specs/phase4-refactor-spec.md`
- **Current spec**: `.claude/specs/pre-release-spec.md`
- **Key coordinate map**: `.claude/docs/reference/key-coordinates.md`
