# Session State

**Last Updated**: 2026-03-02 | **Session**: 22

## Current Phase
- **Phase**: Full Kotlin Migration — Plan Hardened, Ready for Implementation
- **Status**: Migration plan revised after 2-wave Opus adversarial review (42 issues found, all addressed). Now 9 phases (0-7d) instead of 7. Phase 0 migrates keycode constants before deletions. Phase 1 includes KeyboardSwitcher compatibility shim. Phases 5/6 swapped. Phase 7 split into 4 sub-phases. CoroutineScope strategy and InputConnection thread contract defined.

## HOT CONTEXT - Resume Here

### EXACTLY WHERE WE LEFT OFF

**2-wave adversarial review of Kotlin migration plan. Both plan and design doc updated with all findings.**

This session accomplished:
1. Launched Opus-level Wave 1 adversarial review — found 27 issues (5 CRITICAL, 9 HIGH, 10 MEDIUM, 2 LOW)
2. Launched Opus-level Wave 2 review — validated Wave 1, found 15 new issues (1 CRITICAL, 6 HIGH, 6 MEDIUM, 1 LOW, 1 invalid)
3. Wave 2 confirmed 19 Wave 1 issues as-is, downgraded 5, invalidated 1
4. Top finding: Phase 1 deletions break 5+ surviving files because keycode constants referenced across phases
5. Added Phase 0 (constant migration before deletions) — moves ~45 LatinKeyboardView.KEYCODE_* to KeyCodes + updates 5 files
6. Added KeyboardSwitcher compatibility shim (Phase 1) — tracks mode/shift/indicators without mInputView
7. Defined CoroutineScope strategy — ServiceScope pattern, InputConnection thread contract, batch edit safety
8. Swapped Phases 5/6 — convert Java files to Kotlin BEFORE unifying settings
9. Split Phase 7 into 4 sub-phases (7a/7b/7c/7d) with correct dependency ordering
10. Updated both implementation plan and design doc with all 42 issue resolutions

### What Needs to Happen Next

1. **Execute Phase 0: Constant Migration** — Move ~45 keycode constants to KeyCodes, update 5 files, zero behavior change
2. **Execute Phase 1: Dead Code Purge** — KeyboardSwitcher shim, delete legacy Views + prefs + dead code, analyze Keyboard.kt/LatinKeyboard.java survivability
3. **Execute Phase 2: Simple Leaves** — Convert ModifierKeyState, WordComposer, EditingUtil to Kotlin

## Blockers

- **Whisper model files**: Need `whisper-tiny.en.tflite` and `filters_vocab_en.bin` -> `app/src/main/assets/`
- **Debug logging not working**: DevKeyPress/DevKeyMap logcat tags produce zero output despite code being in place
- **Emulator quirk**: `show_ime_with_hard_keyboard` must be set to `1` on emulator

## Recent Sessions

### Session 22 (2026-03-02)
**Work**: 2-wave Opus adversarial review of Kotlin migration plan. Wave 1: 27 issues (5 CRITICAL). Wave 2: validated + 15 new issues. Added Phase 0 (constant migration), KeyboardSwitcher shim, CoroutineScope strategy, swapped Phases 5/6, split Phase 7→7a/7b/7c/7d. Updated plan + design doc.
**Decisions**: Phase 0 before any deletions. shiftStateProvider lambda decouples KeyEventSender from ModifierKeyState. ServiceScope pattern for coroutines. Convert Java files before settings unification. No @JvmDefault (removed in Kotlin 2.0).
**Next**: Execute Phase 0 (constants), Phase 1 (dead code + shim), Phase 2 (simple leaves).

### Session 21 (2026-03-02)
**Work**: Full Kotlin migration brainstorming. 4 Sonnet agents mapped all 40 Java files (~12,500 lines). Designed 7-phase bottom-up incremental migration. KeyEventSender extraction for key synthesis (Ctrl+V, terminal shortcuts). Delete ~4,070 lines of legacy/dead code. Unify modifier state + settings. Design doc + implementation plan committed.
**Decisions**: Bottom-up incremental (Approach A). Delete legacy Views (not migrate). Delete 8 old pref screens. Coroutines during migration. Unify GlobalKeyboardSettings→SettingsRepository. Unify 3 modifier state machines→1. Extract KeyEventSender.kt. JNI bridge stays Java. Remove all HK branding.
**Next**: Execute Phase 1 (dead code purge), Phase 2 (simple leaves), continue through all 7 phases.

### Session 20 (2026-03-01)
**Work**: Full layout redesign session. Tested debug logging on emulator (FAILED — zero output). Identified missing 0 key as real problem. Brainstormed 10 layouts via HTML MCP mockups, selected #8 Hybrid. Designed 3-mode system (Compact/CompactDev/Full) + teal monochrome theme with design tokens. 2-pass adversarial review (15 issues). Implemented all 7 phases via /implement: KeyData.kt (LayoutMode enum, new KeyTypes/KeyCodes), QwertyLayout.kt (3 modes, 6-row full), DevKeyTheme.kt (teal tokens), KeyView/KeyRow/KeyboardView (rendering), DevKeyKeyboard (mode switching), SettingsSubScreens (3-way selector), 4 test files rewritten. 265 tests pass, build passes.
**Decisions**: Layout #8 Hybrid. Teal monochrome (Option A). SwiftKey-style spacebar row (123 ☺ , Space . Enter). Smart ⌫/Esc via SMART_BACK_ESC=-301 keycode resolved in bridge. Keep percentage-based height (tokens as ratios). Old theme names as deprecated aliases. Smart app detection for Termux/CRD/Moonlight/VSCode (feature layer, not layout).
**Next**: Deploy to emulator and test new layout, debug logging system, investigate modifier double-toggle.

### Session 19 (2026-03-01)
**Work**: Brainstormed and implemented key press testing & debug system. 2 Haiku agents explored codebase (key layout/coordinates + logging/testing infra). Opus agent created plan + adversarial review (8 issues fixed). Implemented all 8 steps via /implement: KeyPressLogger, KeyBoundsCalculator, KeyMapGenerator, KeyBoundsCalculatorTest (11 tests), logging in KeyView/ActionBridge/LatinIME, auto-dump in DevKeyKeyboard. Build passes, 253/254 tests pass.
**Decisions**: Use Log.d() (R8 strips in release). View.getLocationOnScreen() for precise Y offset. ApplicationInfo.FLAG_DEBUGGABLE instead of BuildConfig. Don't modify ModifierStateManager — log from KeyView call sites. Normalize labels for ADB. Throttle repeat logging every 10th.
**Next**: Test logging on emulator, validate ADB coordinates, investigate modifier double-toggle with new logging.

### Session 18 (2026-03-01)
**Work**: Emulator verification session. Investigated "keyboard dismiss on key tap" bug — traced full key press chain, added debug logging, tested on emulator. Confirmed keyboard typing works correctly (characters commit, keyboard stays open). The "dismiss" was Android gesture navigation from edge taps, not a keyboard bug. Discovered `hasDistinctMultitouch()` always false with Compose keyboard — potential modifier double-toggle issue.
**Decisions**: "Keyboard dismiss" is NOT a bug (gesture nav). ADB input tap uses raw pixels. `distinctMultiTouch=false` needs investigation for modifier handling.
**Next**: Test modifier keys for double-toggle, procure Whisper model files, address tech debt.

## Active Plans

- **Kotlin Migration** — `.claude/plans/kotlin-migration-plan.md` — READY (Session 21 design, Session 22 adversarial review, 9 phases)
- **Layout Redesign** — `.claude/plans/layout-redesign-implementation-plan.md` — IMPLEMENTED (Session 20, all 7 phases)
- **Key Testing & Debug** — `.claude/plans/key-testing-debug-plan.md` — IMPLEMENTED (Session 19, all 8 steps)
- **Emulator Audit Fix** — `.claude/plans/emulator-audit-fix-plan.md` — IMPLEMENTED (Session 17, all 7 phases)
- **Completed plans** — `.claude/plans/completed/` (Sessions 1-5 plans archived)

## Reference
- **Kotlin migration design**: `docs/plans/2026-03-02-kotlin-migration-design.md`
- **Layout design doc**: `docs/plans/2026-03-01-layout-redesign-design.md`
- **Bug report**: `.claude/logs/emulator-audit-wave1.md`
- **Emulator test log**: `.claude/logs/emulator-test-log.json`
- **Screenshots**: `.claude/screenshots/screen_*.png`
- **Design doc (main)**: `docs/plans/2026-02-23-devkey-design.md`
- **Architecture**: `docs/ARCHITECTURE.md`
- **Research**: `docs/research/`
