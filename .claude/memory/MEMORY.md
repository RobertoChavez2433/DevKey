# DevKey Memory

## Project Overview
- Android keyboard (IME) forked from Hacker's Keyboard, modernized with Kotlin + Jetpack Compose
- Goal: power-user features (terminal keys, macros, command awareness) + modern typing UX (Material You, voice, autocorrect)
- All AI/ML runs on-device (no cloud). Data stays local with JSON export/import.
- User's key use cases: Moonlight, Chrome Remote Desktop, Termux, VS Code (streaming to PC from phone)

## Architecture
- **IME entry point**: `LatinIME.java` (3627 lines) — remains Java, being migrated to Kotlin
- **Key synthesis**: `sendModifiableKeyChar()`/`sendModifiedKeyDownUp()` in LatinIME — sends Ctrl+V, Alt+Tab, F-keys via `InputConnection.sendKeyEvent()`. Being extracted to `KeyEventSender.kt`.
- **JNI bridge**: C++ dictionary at old package path `org.pocketworkstation.pckeyboard.BinaryDictionary` — DO NOT rename (42 lines, stays Java forever)
- **Settings**: SharedPreferences only, wrapped by `SettingsRepository` with Flow observation. `GlobalKeyboardSettings` being unified into `SettingsRepository`.
- **Database**: Room with 4 tables (macros, learned_words, clipboard_history, command_apps), version 2
- **Compose bridge**: `KeyboardActionBridge` → `OnKeyboardActionListener` → `LatinIME.onKey()`. Being simplified during migration.

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
./gradlew test             # Run unit tests (265 tests)
./gradlew installDebug     # Install on device
```

## Critical Pitfalls
- **JNI bridge**: Old package path hardcoded in C++ — never rename `org.pocketworkstation.pckeyboard.BinaryDictionary`
- **Destructive migration**: `DevKeyDatabase` uses `fallbackToDestructiveMigration()` — schema change wipes data
- **Agent permissions**: Add Edit, Write, Bash(*) to `.claude/settings.local.json` BEFORE running `/implement`
- **asciiToKeyCode table**: Maps ASCII chars to Android KEYCODE_* values. Critical for Ctrl+letter synthesis.
- **Dual modifier state**: 3 parallel systems until unified in migration Phase 7

## Java→Kotlin Migration (Active)
- **Plan**: `.claude/plans/kotlin-migration-plan.md` (7 phases, bottom-up incremental)
- **Design**: `docs/plans/2026-03-02-kotlin-migration-design.md`
- **40 Java files** total (~12,500 lines). 1 stays Java (JNI bridge).
- **Phase 1**: Delete legacy Views + old pref screens (~4,070 lines)
- **Phase 7**: Convert LatinIME, extract KeyEventSender.kt, unify ModifierStateManager
- **Key constraint**: `sendModifiableKeyChar()`/`sendModifiedKeyDownUp()` must produce identical `KeyEvent` sequences for Ctrl+V, F-keys, etc.

## Layout Architecture
- `LayoutMode` enum (COMPACT, COMPACT_DEV, FULL) — separate from `KeyboardMode` (Normal, Symbols, etc.)
- `QwertyLayout.getLayout(mode: LayoutMode)` returns layout for any mode
- Row heights use weight ratios within user's percentage-based height preference
- Theme tokens in `ui/theme/DevKeyTheme.kt` (NOT ui/keyboard/)
- `KeyMapGenerator.kt` is in `debug/` package (NOT ui/keyboard/)
- 123 key uses `KeyCodes.SYMBOLS = -2` (alias for Keyboard.KEYCODE_MODE_CHANGE)

## Current State
- All 5 implementation sessions + layout redesign complete
- 3 keyboard modes: Compact (SwiftKey), Compact Dev (long-press numbers), Full (6-row + utility)
- Theme: teal monochrome design token system
- Build passing, 265 unit tests passing
- Kotlin migration plan approved, ready for Phase 1 execution
