# Session State

**Last Updated**: 2026-04-10 | **Session**: 47

## Current Phase

- **Phase**: v1.0 Pre-Release Execution — Phase 4 (Root Package Reorganization)
- **Status**: Package reorganization **COMPLETE**. 60 files migrated across 9
  commits, build green, lint clean. Root package reduced from ~70 files to 6.

## Resume Here

1. Review the 11 skipped E2E tests and decide which need resolution before v1.0.
2. Fix the utility row (Ctrl/Alt/Tab/Arrows) being occluded by the navigation
   bar — the row renders but is hidden behind the system nav bar.
3. Push sessions 42-47 commits.
4. Update CLAUDE.md architecture section — the package list has expanded from
   `core/`, `data/`, `feature/`, `ui/`, `debug/` to include `compose/`,
   `keyboard/`, `language/`, `suggestion/`, `dictionary/`, `legacy/`.

## Important Facts

- Always use the Windows Android emulator, never the Samsung physical device.
- The emulator IME reaches the host debug server via `10.0.2.2`, not
  `127.0.0.1`; the harness translates automatically.
- The debug server port is configurable via `PORT` env var (default 3948).
- Port 3948 is reserved for Samsung/manual testing; use 3950+ for emulator runs.
- `Suggest.getSuggestions()` null-checks `view` before `AutoText.get()` (crash fix session 46).
- Standalone mic key removed from space row per SwiftKey parity (session 46).
- Root package now has exactly 6 files: LatinIME, LatinIMEBackupAgent,
  LatinIMEPrefs, Main, NotificationReceiver, InputLanguageSelection.

## Current Blockers

- Utility row occluded by navigation bar (WindowInsets issue).
- SwiftKey reference coverage incomplete (compact-dev-dark, full-dark).
- Sessions 42-47 commits remain local.
- CLAUDE.md architecture list needs updating post-reorganization.

## Recent Sessions

### Session 47 (2026-04-10)

- **Root package reorganization** (Phase 4 spec): migrated 60 files from the
  flat root package into organized sub-packages across 9 build-safe batches.
- New packages: `compose/`, `keyboard/{model,latin,switcher,xml,proximity}/`,
  `language/`, `suggestion/{engine,word,renderer}/`,
  `dictionary/{base,expandable,user,bigram,loader,trie}/`, `legacy/`.
- Moved ChordeTracker to `core/modifier/`; TextEntryState, EditingUtil,
  ImePrefsUtil to `core/{input,text,prefs}/`.
- Removed all Darren Salt copyright notices (7 files).
- Build green + lint clean after all batches.

### Session 46 (2026-04-10)

- Fixed crash: `NullPointerException` in `Suggest.getSuggestions()` — `AutoText.get()`
  received null View. Added null-check guard.
- Fixed debug server URL: harness was sending `127.0.0.1` to the IME inside the
  emulator; now auto-translates to `10.0.2.2`.
- Made debug server port configurable via `PORT` env var.
- Removed standalone mic key from space row per SwiftKey parity — mic belongs in
  toolbar only. Comma key now has `'` long-press.
- E2E suite: 25 passed / 0 failed / 0 errors / 11 skipped.

### Session 45 (2026-04-10)

- Stabilized the Samsung E2E flow and landed three commits around logging,
  modifier reset behavior, and harness recovery.
- Confirmed the remaining failures were narrowed to FULL-mode CTRL coordinates.

## Current References

- **Current plan**: `.claude/plans/2026-04-09-pre-release-phase3.md`
- **Phase 4 spec**: `.claude/specs/phase4-refactor-spec.md`
- **Current tailor**: `.claude/tailor/2026-04-09-pre-release-phase3/`
- **Current spec**: `.claude/specs/pre-release-spec.md`
- **Current progress note**: `.claude/state/phase3-progress.md`
- **Key coordinate map**: `.claude/docs/reference/key-coordinates.md`
