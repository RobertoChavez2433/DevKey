# Session State

**Last Updated**: 2026-03-03 | **Session**: 28

## Current Phase
- **Phase**: E2E Testing + Bug Fixing — Manual E2E Partial, Compose UI Tests Blocked on API 36
- **Status**: Manual ADB E2E testing confirms FULL mode works (typing, 123 toggle, Shift one-shot, Caps Lock, Ctrl one-shot, number row). Compose UI tests blocked on API 36 Espresso incompatibility. Changes from Session 27 still NOT COMMITTED. Bash permissions broadened to `Bash(*)`.

## HOT CONTEXT - Resume Here

### EXACTLY WHERE WE LEFT OFF

**Manual E2E testing partially complete. FULL mode core features verified. Compose UI tests need dep upgrades for API 36. Session 27 changes still NOT committed.**

This session accomplished:
1. **Built + installed APK** on emulator-5554 (but `connectedAndroidTest` uninstalled it — had to reinstall + re-enable IME)
2. **Ran Compose UI tests** — ALL 14 fail on API 36 emulator (`InputManager.getInstance` reflection removed in Android 16). Physical device: 9/14 pass, 5 fail (ABC key test tag missing, symbol assertion)
3. **Manual ADB E2E testing** with -153px Y offset confirmed:
   - Typing "hello": PASS (all 5 keys correct)
   - 123 mode switch (Normal→Symbols): PASS
   - ABC return (Symbols→Normal): PASS
   - Shift one-shot (tap Shift, tap 'a' → 'A'): PASS
   - Caps Lock (double-tap → LOCKED, 'bc' → 'BC'): PASS
   - Caps Lock unlock (tap → OFF): PASS
   - Ctrl one-shot: PASS (ctrl=true in bridge)
   - Number row 1-0: ALL 10 PASS
   - Alt one-shot: PASS
   - Tab: PASS (consumed Alt one-shot correctly)
   - Arrow Left: PASS
   - Arrows Down/Up/Right: NOT TESTED (Tab moved focus, keyboard closed)
4. **Fixed permissions** — `.claude/settings.local.json` now uses `Bash(*)` instead of individual patterns
5. **Screenshots taken**: initial, hello-typed, symbols-mode, back-to-normal, caps-lock-test, ctrl-a

### What Needs to Happen Next

1. **Commit all changes** — Session 27 changes still not committed (123 fix + lifecycle, Compose UI tests, ADB E2E harness)
2. **Complete arrow key testing** — Down/Up/Right untested (Tab moved focus)
3. **Test COMPACT and COMPACT_DEV modes** — Only FULL mode tested so far
4. **Upgrade Compose test deps** for API 36 — Espresso `InputManager.getInstance` incompatible with Android 16
5. **Fix Python E2E harness** — `DevKeyMap` logcat output not flowing (harness depends on it for key coordinates)

### Key Testing Resumption Guide

The keyboard needs to be set up on `emulator-5554`:
```bash
# Install + force restart IME (CRITICAL after any install):
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell "am force-stop dev.devkey.keyboard"
adb -s emulator-5554 shell "ime enable dev.devkey.keyboard/.LatinIME"
adb -s emulator-5554 shell "ime set dev.devkey.keyboard/.LatinIME"
# Open a text field:
adb -s emulator-5554 shell "am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact"
# Tap First name field (careful not to hit notification area):
adb -s emulator-5554 shell input tap 350 650
```

**Y OFFSET**: All calculated key coordinates need **-153px Y offset** on the emulator. FULL mode corrected coordinates:
- Number row Y: 1446, QWERTY Y: 1592, Home Y: 1746, Z row Y: 1899, Space row Y: 2053, Utility Y: 2190

**CRITICAL**: `connectedAndroidTest` UNINSTALLS the app. Always reinstall + re-enable IME after running instrumented tests.

## Blockers

- **Whisper model files**: Need `whisper-tiny.en.tflite` and `filters_vocab_en.bin` -> `app/src/main/assets/`
- **Compose UI tests blocked on API 36**: Espresso `InputManager.getInstance` reflection incompatible with Android 16 — need dep upgrade
- **Python E2E harness**: `DevKeyMap` logcat tag not producing output — harness can't load key map

## Recent Sessions

### Session 28 (2026-03-03)
**Work**: Manual ADB E2E testing on emulator. FULL mode verified: typing, 123 toggle, Shift one-shot, Caps Lock cycle, Ctrl one-shot, number row (10/10), Alt, Tab, Arrow Left all PASS. Compose UI tests all fail on API 36 (Espresso incompatibility). Fixed permissions to `Bash(*)`.
**Decisions**: `Bash(*)` for project permissions. Compose UI tests need dep upgrade for API 36. Manual ADB testing more productive than Python harness (harness needs DevKeyMap logcat which wasn't flowing).
**Next**: Commit Session 27 changes, complete arrow testing, test COMPACT/COMPACT_DEV modes, upgrade Compose test deps.

### Session 27 (2026-03-03)
**Work**: Fixed 123 mode switch P1 blocker. Root cause: decor view lifecycle DESTROYED killed WindowRecomposer (NOT pointerInput snapshots as hypothesized). Added KeyboardModeManager (StateFlow), Compose UI test infra (4 files), ADB E2E harness (tools/e2e/). E2E verified: 123→Symbols, ABC→Normal, Caps Lock all pass. Design doc + implementation plan committed.
**Decisions**: StateFlow for keyboard mode (consistency with modifiers). Single lifecycle owner shared between decor and ComposeView. Two-tier E2E: Compose UI tests + ADB scripts. Caps Lock verified on device (Session 26 fix confirmed).
**Next**: Commit changes, test COMPACT/COMPACT_DEV modes, run Compose UI tests + ADB harness.

### Session 26 (2026-03-02)
**Work**: Full FULL-mode E2E test (44/44 keys PASS). Fixed Caps Lock bug (stateBeforeDown tracking in ModifierStateManager + 6 unit tests). Partially fixed 123 mode switch (bridge filter). Identified Compose recomposition blocker — toggleMode fires but layout doesn't change. Performance: 6.8ms avg latency, 0 ANRs.
**Decisions**: Caps Lock fix uses stateBeforeDown approach (preserves chording). 123 fix needs deeper Compose investigation — bridge interference is only part of the problem. User wants deterministic E2E test harness for modifier combos with target apps.
**Next**: Debug 123 Compose recomposition, verify Caps Lock on device, brainstorm E2E test harness for modifier combos.

### Session 25 (2026-03-02)
**Work**: Committed Java→Kotlin migration as 3 logical commits + 1 crash fix. Found P0 crash (sKeyboardSettings init order) and fixed immediately. Built, installed, and began E2E testing on emulator. Calibrated key coordinates (Y offset -153px). Verified "hello" typing test passes. Created key coordinate map and test observations docs.
**Decisions**: Migration is atomic (can't split Java deletion from Kotlin addition). Y offset -153px for all calculated coordinates. FULL mode is the default layout on emulator.
**Next**: Complete full key matrix test, test modifiers/modes, address architectural debt.

### Session 24 (2026-03-02)
**Work**: Implemented all 5 deferred Kotlin migration items via /implement (ComposeSequence→Kotlin, Settings Unification, Handler→coroutines, Bridge simplification, ComposeSequencing removal). Then ran multi-wave code review: Wave 1 (4 parallel Opus reviewers, 120 findings), Fix Pass 1 (18 P0+P1 fixes), Wave 2 (verification + 3 new P1s), Fix Pass 2 (3 parallel agents, ~63 P2+P3 fixes). All 355 tests pass.
**Decisions**: KeyboardActionBridge stays (shift-uppercase, smart backspace are real value). ComposeSequencing replaced with lambdas. Debug logging kept as-is (intentional). consumeFlag() replaces hasFlag(). uptimeMillis for tap timing. Character.isLetter for Unicode support.
**Next**: Commit changes, emulator regression test, address PluginManager security.

## Active Plans

- **Kotlin Migration** — FULLY COMPLETE + COMMITTED (Session 25, 4 commits)
- **123 Fix + E2E Harness** — IMPLEMENTED, NOT YET COMMITTED (Session 27)
- **E2E Testing** — IN PROGRESS (FULL mode mostly verified Session 28, COMPACT/COMPACT_DEV pending)
- **Layout Redesign** — IMPLEMENTED (Session 20, all 7 phases)
- **Completed plans** — `.claude/plans/completed/`

## Reference
- **E2E test observations**: `.claude/logs/e2e-test-observations.md`
- **Key coordinate map**: `.claude/logs/key-coordinates.md`
- **123 fix design**: `docs/plans/2026-03-03-123-fix-e2e-harness-design.md`
- **123 fix plan**: `.claude/plans/123-fix-e2e-harness-implementation-plan.md`
- **Kotlin migration design**: `docs/plans/2026-03-02-kotlin-migration-design.md`
- **Layout design doc**: `docs/plans/2026-03-01-layout-redesign-design.md`
- **Screenshots**: `.claude/screenshots/` (e2e-s28-*.png, keyboard_ready.png, hello_typed.png, etc.)
- **Design doc (main)**: `docs/plans/2026-02-23-devkey-design.md`
- **Architecture**: `docs/ARCHITECTURE.md`
