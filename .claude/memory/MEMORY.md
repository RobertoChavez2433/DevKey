# DevKey Memory

## Project Overview
- Android keyboard (IME) forked from Hacker's Keyboard, modernized with Kotlin + Jetpack Compose
- Goal: power-user features (terminal keys, macros, command awareness) + modern typing UX (Material You, voice, autocorrect)
- All AI/ML runs on-device (no cloud). Data stays local with JSON export/import.
- User's key use cases: Moonlight, Chrome Remote Desktop, Termux, VS Code (streaming to PC from phone)

## Architecture
- **IME entry point**: `LatinIME.kt` (~2,500 lines) — fully Kotlin, uses serviceScope coroutines
- **Key synthesis**: `KeyEventSender.kt` (~330 lines) — extracted, testable, sends Ctrl+V, Alt+Tab, F-keys via `InputConnection.sendKeyEvent()`
- **JNI bridge**: C++ dictionary at old package path `org.pocketworkstation.pckeyboard.BinaryDictionary` — DO NOT rename (42 lines, stays Java forever)
- **Settings**: `SettingsRepository` — single source of truth, absorbed GlobalKeyboardSettings (Session 24)
- **Database**: Room with 4 tables (macros, learned_words, clipboard_history, command_apps), version 2
- **Compose bridge**: `KeyboardActionBridge` → `KeyboardActionListener` → `LatinIME.onKey()`. Bridge provides shift-uppercase, smart backspace, modifier consumption. Evaluated in Session 24 — stays permanently.
- **Modifier state**: `ModifierStateManager` (Shift/Ctrl/Alt/Meta) + `ChordeTracker` (Symbol/Fn) — dual system, to be unified

## Tech Stack
| Component | Version |
|-----------|---------|
| Kotlin | 2.0.21 |
| AGP | 8.5.2 |
| Gradle | 8.7 |
| KSP | 2.0.21-1.0.27 |
| Compose BOM | 2024.06.00 |
| Material 3 | 1.2.1 |
| Room | 2.6.1 |
| Coroutines | 1.8.1 |
| TF Lite | 2.16.1 / support 0.4.4 |
| kotlinx.serialization | 1.6.3 |
| Min SDK | 26 (Android 8.0) |
| Target/Compile SDK | 34 (Android 14) |

## Build
```bash
./gradlew assembleDebug    # Build debug APK
./gradlew test             # Run unit tests (355 tests)
./gradlew installDebug     # Install on device
```

## Critical Pitfalls
- **JNI bridge**: Old package path hardcoded in C++ — never rename `org.pocketworkstation.pckeyboard.BinaryDictionary`
- **Destructive migration**: `DevKeyDatabase` uses `fallbackToDestructiveMigration()` — schema change wipes data
- **Agent permissions**: Add Edit, Write, Bash(*) to `.claude/settings.local.json` BEFORE running `/implement`
- **asciiToKeyCode table**: Maps ASCII chars to Android KEYCODE_* values (IntArray(128)). Critical for Ctrl+letter synthesis.
- **Debug logging intentional**: KeyPressLogger + Log.d calls were added in Session 19 for debugging — do NOT remove or gate behind BuildConfig.DEBUG

## Java→Kotlin Migration (COMPLETE)
- **All phases executed**: Sessions 21-24 (brainstorm → plan → review → implement → deferred items → code review)
- **Only Java file remaining**: JNI bridge (`org.pocketworkstation.pckeyboard.BinaryDictionary`)
- **Key extractions**: KeyEventSender.kt (key synthesis), KeyboardActionListener.kt (interface), ModifierStateManager (unified Shift/Ctrl/Alt/Meta)
- **Settings unified**: GlobalKeyboardSettings folded into SettingsRepository (Session 24)
- **Handler replaced**: serviceScope with coroutine delay() replaces Handler/Message (Session 24)

## Layout Architecture
- `LayoutMode` enum (COMPACT, COMPACT_DEV, FULL) — separate from `KeyboardMode` (Normal, Symbols, etc.)
- `QwertyLayout.getLayout(mode: LayoutMode)` returns layout for any mode
- Row heights use weight ratios within user's percentage-based height preference
- Theme tokens in `ui/theme/DevKeyTheme.kt` (NOT ui/keyboard/)
- `KeyMapGenerator.kt` is in `debug/` package (NOT ui/keyboard/)
- 123 key uses `KeyCodes.SYMBOLS = -2` (alias for Keyboard.KEYCODE_MODE_CHANGE)

## Current State
- All implementation sessions + layout redesign + Kotlin migration + code review complete
- 3 keyboard modes: Compact (SwiftKey), Compact Dev (long-press numbers), Full (6-row + utility)
- Theme: teal monochrome design token system
- Build passing, 355 unit tests passing
- Needs: emulator regression test, commit, PluginManager security hardening
