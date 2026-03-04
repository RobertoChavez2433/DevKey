# State Archive

Archived session entries from `_state.md` rotation.

---

## March 2026

### Session 22 (2026-03-02)
**Work**: 2-wave Opus adversarial review of Kotlin migration plan. Wave 1: 27 issues (5 CRITICAL). Wave 2: validated + 15 new issues. Added Phase 0 (constant migration), KeyboardSwitcher shim, CoroutineScope strategy, swapped Phases 5/6, split Phase 7→7a/7b/7c/7d. Updated plan + design doc.
**Decisions**: Phase 0 before any deletions. shiftStateProvider lambda decouples KeyEventSender from ModifierKeyState. ServiceScope pattern for coroutines. Convert Java files before settings unification. No @JvmDefault (removed in Kotlin 2.0).
**Next**: Execute Phase 0 (constants), Phase 1 (dead code + shim), Phase 2 (simple leaves).

### Session 21 (2026-03-02)
**Work**: Full Kotlin migration brainstorming. 4 Sonnet agents mapped all 40 Java files (~12,500 lines). Designed 7-phase bottom-up incremental migration. KeyEventSender extraction for key synthesis (Ctrl+V, terminal shortcuts). Delete ~4,070 lines of legacy/dead code. Unify modifier state + settings. Design doc + implementation plan committed.
**Decisions**: Bottom-up incremental (Approach A). Delete legacy Views (not migrate). Delete 8 old pref screens. Coroutines during migration. Unify GlobalKeyboardSettings→SettingsRepository. Unify 3 modifier state machines→1. Extract KeyEventSender.kt. JNI bridge stays Java. Remove all HK branding.
**Next**: Execute Phase 1 (dead code purge), Phase 2 (simple leaves), continue through all 7 phases.

### Session 19 (2026-03-01)
**Work**: Brainstormed and implemented key press testing & debug system. 2 Haiku agents explored codebase (key layout/coordinates + logging/testing infra). Opus agent created plan + adversarial review (8 issues fixed). Implemented all 8 steps via /implement: KeyPressLogger, KeyBoundsCalculator, KeyMapGenerator, KeyBoundsCalculatorTest (11 tests), logging in KeyView/ActionBridge/LatinIME, auto-dump in DevKeyKeyboard. Build passes, 253/254 tests pass.
**Decisions**: Use Log.d() (R8 strips in release). View.getLocationOnScreen() for precise Y offset. ApplicationInfo.FLAG_DEBUGGABLE instead of BuildConfig. Don't modify ModifierStateManager — log from KeyView call sites. Normalize labels for ADB. Throttle repeat logging every 10th.
**Next**: Test logging on emulator, validate ADB coordinates, investigate modifier double-toggle with new logging.

## February 2026

### Session 1 (2026-02-23)
**Work**: Full research phase. Brainstormed product vision, architecture, feature set, command-aware autocorrect.
**Decisions**: Name=DevKey, Kotlin+C++NDK, on-device AI, Material You, progressive modernization, no swipe, smart hybrid command mode.
**Next**: Continue brainstorming.

### Session 2 (2026-02-23)
**Work**: Resumed brainstorming. Approved Full Mode as default layout.
**Decisions**: Default keyboard mode = Full Mode.
**Next**: Create mockups.

### Session 3 (2026-02-23)
**Work**: Diagnosed html-sync MCP connection failure. Created `.mcp.json` in project root. Narrowed mockup scope.
**Decisions**: Mockup scope = Main + Power User only.
**Next**: Create mockups, complete design sections.

### Session 4 (2026-02-23)
**Work**: Created 30 keyboard layout mockups across 3 rounds via html-sync MCP. Narrowed from 10+ designs to final layout (#8: Esc/Tab in number row, modifiers + arrows bottom). Completed all remaining design sections (data flow, voice, testing, distribution). Wrote full design doc (13 sections) and 5-session implementation plan.
**Decisions**: Final layout = #8. Macros = chips + grid + recording. Ctrl-held = shortcut labels. Whisper for voice. Local + export/import for data. Unit + integration tests.
**Next**: Begin Session 1 implementation (infrastructure & scaffolding).

### Session 5 (2026-02-23)
**Work**: Implemented Session 1 — Infrastructure & Project Scaffolding. Cloned Hacker's Keyboard, repackaged to dev.devkey.keyboard (44 Java files + 66 resource XMLs), converted build system to Kotlin DSL with version catalogs, created Room database (5 tables, 5 entities, 5 DAOs), migrated Keyboard.java and KeyboardSwitcher.java to Kotlin, scaffolded 14 package subdirectories, created ARCHITECTURE.md. Fixed R.styleable switch/case errors (AGP 8.x non-constant R fields), Kotlin open/final interop issues, and Kotlin 2.0 compose compiler plugin.
**Decisions**: Kotlin 2.0.21, AGP 8.5.2, JNI bridge at old package path, LatinIME stays Java for now.
**Next**: Begin Session 2 (Keyboard Layout & Rendering).

### Session 6 (2026-02-23)
**Work**: Brainstormed Session 2 (Keyboard Layout & Rendering) design. Wrote detailed design doc and 7-phase implementation plan. Explored codebase to understand legacy rendering pipeline (LatinKeyboardBaseView Canvas rendering, OnKeyboardActionListener interface, XML layout parsing).
**Decisions**: Clean Compose break, weight-based flex keys, ComposeView in LatinIME, direct long-press insert, key preview popup, English only.
**Next**: Run /implement on session2-implementation-plan.md.

### Session 7 (2026-02-23)
**Work**: Implemented Session 2 (Keyboard Layout & Rendering) via /implement orchestrator. 7 phases, 13 new Compose files, 2 modified files. All quality gates passed. Committed.
**Decisions**: Keycodes duplicated as private consts (Java package-private), detectTapGestures for long-press/repeat, ComposeKeyboardViewFactory for IME lifecycle, phases 2 & 4 parallel.
**Next**: Test on device, begin Session 3 (Macros, Ctrl Mode & Clipboard).

### Session 8 (2026-02-23)
**Work**: Brainstormed Session 3 (Macros, Ctrl Mode, Clipboard & Symbols). 8 design sections approved. Wrote design doc and 9-phase implementation plan. Committed.
**Decisions**: KeyboardMode sealed class, logical combo macro capture, both chips+grid, in-place Ctrl transform, defer clipboard encryption, include symbols layer.
**Next**: Run /implement on session3-implementation-plan.md, test on device.

### Session 9 (2026-02-23)
**Work**: Confirmed Session 3 fully implemented. Brainstormed Session 4 (Voice, Command Mode, Autocorrect). Researched TF Lite models — GPT-2 too large/slow for keyboard, pivoted to dictionary-based prediction. 5 design sections approved. Wrote design doc and 8-phase implementation plan. Committed both.
**Decisions**: No TF Lite for prediction (user doesn't use next-word), Whisper tiny.en bundled in APK, dictionary + edit-distance autocorrect (mild default), auto-detect terminals + manual toggle, SessionDependencies singleton for Java-Compose bridge.
**Next**: Run /implement on session4-implementation-plan.md, procure Whisper model files, test on device.

### Session 10 (2026-02-23)
**Work**: Implemented Session 4 via /implement orchestrator — single cycle, 0 handoffs. 13 new files + 8 modified. Dictionary prediction, autocorrect, command mode detection, Whisper voice infrastructure, Compose voice UI, IME integration via SessionDependencies.
**Decisions**: tflite-support 0.4.4 (not 2.16.1), Whisper graceful degradation, SessionDependencies singleton bridge.
**Next**: Procure Whisper model files, brainstorm Session 5, test on device.

### Session 11 (2026-02-24)
**Work**: Brainstormed Session 5 (Settings, Export/Import, Polish & Testing). 6 design sections approved. Wrote design doc and 9-phase implementation plan (14 new files, ~9 modified). Committed both.
**Decisions**: Full Compose settings replacement, SharedPreferences only (drop Room SettingsEntity), flat list navigation, keep most legacy prefs, user-data-only export via kotlinx.serialization, compact mode Esc/Tab on Shift/Backspace long-press, unit tests for new code only.
**Next**: Run /implement on session5-implementation-plan.md, procure Whisper model files, test on device.

### Session 12 (2026-02-24)
**Work**: Implemented Session 5 via /implement (all 9 phases). Ran 3-agent code review + 3 fixer agents + Round 2 review + manual fixes. 3-agent doc audit + cross-reference agent updated all documentation. Deleted 15+ dead files. Updated branding to "DevKey". Wrote ProGuard rules. 5 logical commits. Build passing, 28 unit tests passing.
**Decisions**: Drop Room settings table (SharedPreferences only). Destructive migration stays for now. Archived stale defects. Moved 4 completed plans to plans/completed/.
**Next**: Procure Whisper model files, test on device, write proper Room migrations.

## March 2026

### Session 13 (2026-02-24)
**Work**: Built APK, set up emulator ADB testing pipeline, debugged keyboard not showing. Fixed 3 critical bugs: API 36 ViewTreeLifecycleOwner crash, onStartInputView early return, NPE cascade from unguarded getInputView() calls. Keyboard now renders on emulator.
**Decisions**: Use emulator (not physical device) for ADB-driven testing. Fix null guards in legacy code rather than rewriting IME.
**Next**: Commit fixes, test keyboard input on emulator, debug remaining issues.

### Session 14 (2026-02-24)
**Work**: Reviewed emulator screenshot showing cramped keys. Brainstormed keyboard layout fix. Identified root causes: fixed 48dp rows, unused suggestion bar, 9-key bottom row with duplicate Shift. Designed fix: remove SuggestionBar, remove duplicate Shift, dynamic 40%-of-screen height with weight-based rows. Wrote design doc and 4-phase implementation plan. Committed both.
**Decisions**: Kill suggestion bar (user never uses predictions). Drop bottom-row duplicate Shift. Dynamic keyboard height = 40% of screen. Keep Ctrl/Alt/Space/arrows/Enter in bottom row.
**Next**: Commit Session 13 bug fixes, implement layout fix plan, test on emulator.

### Session 15 (2026-02-26)
**Work**: Implemented complete unit test coverage plan. Fixed 2 silent-pass tests (SilenceDetector reset), strengthened 3 weak assertions (MacroSerializer), added 73 new tests across 10 files. Total: 243 tests, 0 failures.
**Decisions**: Used deserialization-based assertions instead of String.contains(). Used Thread.sleep for timer-dependent tests. Verified compact/full layout parity in tests.
**Next**: Commit all uncommitted work, implement keyboard layout fix, test on emulator.

### Session 16 (2026-03-01)
**Work**: Implemented keyboard layout fix plan (4 phases). Set up VS Code F5 build/deploy pipeline. Full emulator audit: 8 bugs found across P0-P3. Created 7-phase fix plan with adversarial review (11 corrections incorporated). Removed ALL Hacker's Keyboard references requirement added to plan.
**Decisions**: BUG-07 (missing 0) is NOT a bug (Tab long-press). BUG-01 relabeled P1 (not P0) to match implementation order. Phase 6 (settings nav) may be deferred. All HK references must be removed — attribution in separate doc only.
**Next**: Implement emulator-audit-fix-plan.md, test on emulator, commit all work.

### Session 17 (2026-03-01)
**Work**: Implemented all 7 emulator audit fix phases + 2 waves of code review. Fixed 3 CRITICALs (PendingIntent FLAG_IMMUTABLE, registerReceiver RECEIVER_NOT_EXPORTED). Caught Tab keycode regression (61→9). Deleted 751-line dead SettingsScreen.kt. Replaced flat settings with hierarchical nav (10 categories). Added DevKey welcome activity. Rebranded all log tags. Fixed 8 deprecated APIs. 7 logical commits.
**Decisions**: Phase 6 (hierarchical settings) implemented despite "defer" option. KeyCodes.TAB=9 (ASCII HT, not Android KeyEvent 61). SettingsScreen.kt deleted (fully replaced). onBackPressed→OnBackPressedCallback.
**Next**: Test all fixes on emulator, procure Whisper model files, address deferred per-key StateFlow tech debt.

### Session 18 (2026-03-01)
**Work**: Emulator verification session. Investigated "keyboard dismiss on key tap" bug — traced full key press chain, added debug logging, tested on emulator. Confirmed keyboard typing works correctly (characters commit, keyboard stays open). The "dismiss" was Android gesture navigation from edge taps, not a keyboard bug. Discovered `hasDistinctMultitouch()` always false with Compose keyboard — potential modifier double-toggle issue.
**Decisions**: "Keyboard dismiss" is NOT a bug (gesture nav). ADB input tap uses raw pixels. `distinctMultiTouch=false` needs investigation for modifier handling.
**Next**: Test modifier keys for double-toggle, procure Whisper model files, address tech debt.

### Session 23 (2026-03-02)
**Work**: Full Kotlin migration execution via /implement skill. 6 orchestrator cycles completed all 11 phases (0-7d). Deleted ~30 legacy Java files, converted ~16 Java files to Kotlin, created KeyEventSender.kt + KeyboardActionListener.kt + tests. Only ComposeSequence.java and JNI bridge remain as Java.
**Decisions**: WordPromotionDelegate breaks circular deps. ComposeSequence.java kept as Java (static data). BinaryDictionary.kt uses JniBridge import alias. GlobalKeyboardSettings keeps @JvmField (full StateFlow deferred). Handler kept in LatinIME.kt (coroutine replacement deferred). ChordeTracker replaces old ModifierKeyState for Symbol/Fn keys.
**Next**: Commit changes, emulator regression test, convert ComposeSequence.java.
