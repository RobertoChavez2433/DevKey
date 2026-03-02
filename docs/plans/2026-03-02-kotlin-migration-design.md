# Full Kotlin Migration Design

**Date:** 2026-03-02
**Status:** Approved (revised after 2-wave adversarial review)
**Scope:** Migrate all remaining Java files to Kotlin, delete legacy code, unify architecture
**Implementation plan:** `.claude/plans/kotlin-migration-plan.md`

## Goals

1. Eliminate all Java↔Kotlin boundary issues causing bugs and complexity
2. Delete ~4,000+ lines of legacy/dead code (Canvas views, old preference screens)
3. Unify the three parallel modifier state machines into one
4. Unify dual settings systems (GlobalKeyboardSettings + SettingsRepository) into one
5. Extract key synthesis into a testable `KeyEventSender.kt` class
6. Replace deprecated `AsyncTask` with Kotlin coroutines in dictionary stack
7. Remove all remaining Hacker's Keyboard branding (credit in separate docs)
8. Preserve the JNI bridge at `org.pocketworkstation.pckeyboard.BinaryDictionary` (42 lines, must stay Java)

## Architecture Decisions

### Key Synthesis System
The `sendModifiableKeyChar()`/`sendModifiedKeyDownUp()` system is extracted into `KeyEventSender.kt`:
- Preserves the exact `InputConnection.sendKeyEvent()` sequence for Ctrl+V, Alt+Tab, F-keys, etc.
- The `asciiToKeyCode[127]` table migrates intact
- ConnectBot VT escape sequences preserved for backward compatibility
- Target apps: Termux, Moonlight, Chrome Remote Desktop, VS Code — all use standard `sendKeyEvent()`
- Unit-testable with mock `InputConnection`
- **Hidden dependency:** `isShiftMod()` calls `mShiftKeyState.isChording()`. During extraction (Phase 7a), use a `shiftStateProvider: () -> Boolean` lambda to decouple from `ModifierKeyState` before it's replaced in Phase 7b.

### Modifier State Unification
Three systems collapse into one `ModifierStateManager`:
- **Before:** Compose `ModifierStateManager` (visual) + Java `ModifierKeyState` (chording) + `mModCtrl/Alt/Meta/Fn` booleans (key synthesis)
- **After:** Single `ModifierStateManager` with states: OFF, ONE_SHOT, LOCKED, HELD, CHORDING
- Drives both visual feedback (Compose recomposition) and key event meta-state
- Eliminates the `hasDistinctMultitouch()` always-false defect
- **Extraction happens in Phase 7b** (after KeyEventSender extraction in 7a, before LatinIME conversion in 7c)

### Settings Unification
`GlobalKeyboardSettings` (274 lines, 25+ public mutable fields) folds into `SettingsRepository`:
- All fields become `StateFlow` properties
- `FLAG_PREF_*` reload system replaced by `SharedFlow<Unit>` events
- Single `SharedPreferences.OnSharedPreferenceChangeListener`
- Compile-time type safety instead of string-key matching
- **Known callers** (73 occurrences across 9 files): LatinIME (39), Keyboard.kt (9), KeyboardSwitcher.kt (4), CandidateView (1), LanguageSwitcher (1), LatinKeyboardBaseView (10, deleted), LatinKeyboardView (2, deleted), PointerTracker (4, deleted)
- **Keyboard.kt constructor-time access:** Lines 195, 325, 343, 347, 418, 615 read `sKeyboardSettings` at construction time — must be changed to injected parameters

### Coroutine Strategy

**ServiceScope pattern:** Dictionary classes own `CoroutineScope(SupervisorJob() + Dispatchers.Main)`:
- Created in the dictionary constructor
- Cancelled in the dictionary's `close()` method
- LatinIME's `onDestroy()` calls `close()` on all dictionary instances

**InputConnection thread contract:**
- ALL `InputConnection` calls MUST happen on `Dispatchers.Main`
- Dictionary I/O uses `withContext(Dispatchers.IO)` only for the actual I/O
- Results/callbacks always return to `Dispatchers.Main` before touching InputConnection
- **Rule:** Never call `getCurrentInputConnection()` from a non-Main dispatcher

**Compose/InputConnection batch edit safety:**
- LatinIME uses `beginBatchEdit()`/`endBatchEdit()` for multi-step operations
- No Compose-side InputConnection reads should happen during batch edits
- Cache smart backspace resolution at start of key handling, not lazily

**Mixed async model (Phases 3-6):**
- LatinIME Handler/Message pattern coexists with dictionary coroutines until Phase 7c
- Both land on main thread — safe if documented

### Legacy View Deletion
`LatinKeyboardBaseView`, `LatinKeyboardView`, `PointerTracker`, and related classes are deleted:
- The `OnKeyboardActionListener` interface extracted as a standalone Kotlin interface first (no `@JvmDefault` — removed in Kotlin 2.0; jvmTarget 17 default methods work automatically)
- The Compose keyboard is the sole rendering path
- `KeyboardSwitcher` gets a compatibility shim: tracks shift state, keyboard mode, and modifier indicators internally (without `mInputView`) before legacy views are deleted
- **Keycode constant migration (Phase 0):** All ~45 `LatinKeyboardView.KEYCODE_*` constants move to `KeyCodes` in `KeyData.kt` BEFORE any deletions. Cross-references in 5 surviving files updated simultaneously.

### Dead Code Analysis: Keyboard.kt + LatinKeyboard.java
After Phase 1 deletes the legacy view layer:
- `Keyboard.kt` (1004 lines, AOSP XML parser) may be dead code — the Compose keyboard uses `QwertyLayout.kt`/`SymbolsLayout.kt`
- `LatinKeyboard.java` (~890 lines, extends Keyboard) may also be unreachable
- Phase 1 includes an analysis step to determine if these should be deleted early or kept for conversion

### JNI Bridge (unchanged)
`org.pocketworkstation.pckeyboard.BinaryDictionary` (42 lines) stays as Java:
- C++ `RegisterNatives` hardcodes the old package path
- The `dev.devkey.keyboard.BinaryDictionary` wrapper is converted to Kotlin
- `Class.forName()` works identically in Kotlin (JVM class names are the same)

### Hacker's Keyboard Branding
All references to "Hacker's Keyboard", "pckeyboard", "HK" removed during migration:
- Log tags, comments, string literals, class names
- Attribution preserved in a separate credits document

## Migration Strategy: Bottom-Up Incremental (9 phases)

### Phase 0: Constant & Cross-Reference Migration (new — pre-deletion refactor)
Move all ~45 `LatinKeyboardView.KEYCODE_*` constants to `KeyCodes` object. Update 5 surviving files. Zero behavior change.

### Phase 1: Dead Code Purge (~4,070 lines deleted)
Extract `OnKeyboardActionListener`. Create `KeyboardSwitcher` compatibility shim (mode/shift/indicators without mInputView). Delete legacy Views + 17 XML layouts. Delete 8 preference screens + ProGuard rules + manifest entries. Analyze Keyboard.kt/LatinKeyboard.java survivability.

### Phase 2: Simple Leaves (~530 lines converted)
Convert: ModifierKeyState, WordComposer, EditingUtil. (KeyDetector files deleted in Phase 1, not converted.)

### Phase 3: Dictionary Stack (~2,350 lines converted)
Convert with ServiceScope coroutine pattern. InputConnection thread contract enforced.

### Phase 4: Text & Composition (~1,830 lines converted)
Convert: TextEntryState, ComposeSequence, DeadAccentSequence, LatinIMEUtil.

### Phase 5: Support Files (~2,800 lines converted) [was Phase 6]
Convert: LanguageSwitcher, PluginManager, CandidateView (→Compose), LatinKeyboard, Main, NotificationReceiver, LatinIMEBackupAgent, InputLanguageSelection.

### Phase 6: Settings Unification (~274 lines replaced) [was Phase 5]
Fold GlobalKeyboardSettings into SettingsRepository. All callers already Kotlin (converted in Phase 5).

### Phase 7a: Extract KeyEventSender
Extract key synthesis from LatinIME. Comprehensive tests including VT escape sequences. LatinIME stays Java.

### Phase 7b: Enhance ModifierStateManager
Add CHORDING state. Delete ModifierKeyState. Unify modifier tracking.

### Phase 7c: Convert LatinIME to Kotlin
J2K conversion. Replace Handler with coroutines. Replace sInstance singleton. Fix null safety.

### Phase 7d: Final Cleanup
Simplify KeyboardActionBridge. Remove HK branding. Lint pass.

## Testing Protocol
After each phase/sub-phase:
1. `./gradlew test` — all unit tests pass
2. `./gradlew assembleDebug` — build succeeds
3. Install on emulator, type in multiple apps
4. Test modifier keys: Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+A
5. Test all 3 keyboard modes: Compact, Compact Dev, Full
6. Verify auto-capitalization works (sentence-start caps)
7. Verify ConnectBot/Termux escape sequences (post-Phase 7a)

## Files That Stay as Java
- `org/pocketworkstation/pckeyboard/BinaryDictionary.java` (42 lines) — JNI bridge, hardcoded in C++

## Risk Assessment
- **Highest risk:** Phase 7c (LatinIME conversion) — the IME entry point, any regression = keyboard doesn't work
- **Mitigation:** Extract subsystems (KeyEventSender in 7a, modifier state in 7b) before converting the shell in 7c
- **Phase 1 risk:** KeyboardSwitcher shim must correctly preserve mode switching + shift state WITHOUT mInputView. Test auto-capitalization thoroughly.
- **Rollback:** Each phase/sub-phase is committed separately. Rolling back requires reverting in **reverse order** (later phases depend on earlier ones). Each phase MUST leave the codebase in a compilable, testable state.

## Adversarial Review Summary

A 2-wave Opus-level adversarial review identified 42 issues (6 CRITICAL, 15 HIGH, 13 MEDIUM, 4 LOW, 1 INVALID, 3 OVERSTATED). All validated issues are addressed in the implementation plan. Key structural changes from the review:
1. **Phase 0 added** — Constant migration must precede any file deletions
2. **KeyboardSwitcher shim** — Phase 1 can't just delete mInputView; mode/shift state must be tracked independently
3. **CoroutineScope strategy defined** — ServiceScope pattern with InputConnection thread contract
4. **Phases 5/6 swapped** — Convert Java files to Kotlin BEFORE unifying settings
5. **Phase 7 split into 4 sub-phases** — Independent commits for extraction, unification, conversion, cleanup
