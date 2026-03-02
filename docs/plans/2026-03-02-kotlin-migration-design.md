# Full Kotlin Migration Design

**Date:** 2026-03-02
**Status:** Approved
**Scope:** Migrate all remaining Java files to Kotlin, delete legacy code, unify architecture

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

### Modifier State Unification
Three systems collapse into one `ModifierStateManager`:
- **Before:** Compose `ModifierStateManager` (visual) + Java `ModifierKeyState` (chording) + `mModCtrl/Alt/Meta/Fn` booleans (key synthesis)
- **After:** Single `ModifierStateManager` with states: OFF, ONE_SHOT, LOCKED, HELD, CHORDING
- Drives both visual feedback (Compose recomposition) and key event meta-state
- Eliminates the `hasDistinctMultitouch()` always-false defect

### Settings Unification
`GlobalKeyboardSettings` (274 lines, 25+ public mutable fields) folds into `SettingsRepository`:
- All fields become `StateFlow` properties
- `FLAG_PREF_*` reload system replaced by `SharedFlow<Unit>` events
- Single `SharedPreferences.OnSharedPreferenceChangeListener`
- Compile-time type safety instead of string-key matching

### Legacy View Deletion
`LatinKeyboardBaseView`, `LatinKeyboardView`, `PointerTracker`, and related classes are deleted:
- The `OnKeyboardActionListener` interface extracted as a standalone Kotlin interface first
- The Compose keyboard is the sole rendering path
- `KeyboardSwitcher.getInputView()` no longer returns `LatinKeyboardView`

### JNI Bridge (unchanged)
`org.pocketworkstation.pckeyboard.BinaryDictionary` (42 lines) stays as Java:
- C++ `RegisterNatives` hardcodes the old package path
- The `dev.devkey.keyboard.BinaryDictionary` wrapper is converted to Kotlin

### Hacker's Keyboard Branding
All references to "Hacker's Keyboard", "pckeyboard", "HK" removed during migration:
- Log tags, comments, string literals, class names
- Attribution preserved in a separate credits document

## Migration Strategy: Bottom-Up Incremental

### Phase 1: Dead Code Purge (~4,070 lines deleted)
Delete 8 superseded preference Activities, legacy Canvas views, dead fields.
Extract `OnKeyboardActionListener` to standalone Kotlin interface.

### Phase 2: Simple Leaves (~750 lines converted)
Convert: ModifierKeyState, WordComposer, EditingUtil, KeyDetector, MiniKeyboardKeyDetector, ProximityKeyDetector, SwipeTracker.

### Phase 3: Dictionary Stack (~2,350 lines converted)
Convert: Dictionary → ExpandableDictionary → UserDictionary, AutoDictionary, UserBigramDictionary → BinaryDictionary (dev.devkey) → Suggest.
AsyncTask replaced with Kotlin coroutines. SQLite replaced with coroutine dispatchers.

### Phase 4: Text & Composition (~1,830 lines converted)
Convert: TextEntryState (static→instance), ComposeSequence, DeadAccentSequence, LatinIMEUtil.

### Phase 5: Settings Unification (~274 lines replaced)
Fold GlobalKeyboardSettings into SettingsRepository. Update all callers.

### Phase 6: Support Files (~2,800 lines converted)
Convert: LanguageSwitcher, PluginManager, CandidateView (→Compose), LatinKeyboard, Main (→Compose), NotificationReceiver, LatinIMEBackupAgent.

### Phase 7: LatinIME Itself (~3,627 lines converted)
Convert to Kotlin. Extract KeyEventSender. Unify ModifierStateManager. Simplify KeyboardActionBridge. Remove SessionDependencies (replace with constructor injection).

## Testing Protocol
After each phase:
1. `./gradlew test` — all unit tests pass
2. `./gradlew assembleDebug` — build succeeds
3. Install on emulator, type in multiple apps
4. Test modifier keys: Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+A
5. Test all 3 keyboard modes: Compact, Compact Dev, Full

## Files That Stay as Java
- `org/pocketworkstation/pckeyboard/BinaryDictionary.java` (42 lines) — JNI bridge, hardcoded in C++

## Risk Assessment
- **Highest risk:** Phase 7 (LatinIME itself) — the IME entry point, any regression = keyboard doesn't work
- **Mitigation:** Extract subsystems (KeyEventSender, modifier state) before converting the shell
- **Rollback:** Each phase is committed separately; `git revert` restores previous state
