# DevKey Memory

## Project Overview
- Android keyboard (IME) forked from Hacker's Keyboard, modernized with Kotlin + Jetpack Compose
- Goal: power-user features (terminal keys, macros, command awareness) + modern typing UX (Material You, voice, autocorrect)
- All AI/ML runs on-device (no cloud). Data stays local with JSON export/import.

## Architecture
- **IME entry point**: `LatinIME.java` (3566 lines) — remains Java, orchestrates everything
- **Java-Compose bridge**: `SessionDependencies` singleton injects Room DAOs/repos into Compose UI at IME startup
- **JNI bridge**: C++ dictionary at old package path `org.pocketworkstation.pckeyboard.BinaryDictionary` — DO NOT rename
- **Dual rendering**: Legacy Java `LatinKeyboardBaseView` (Canvas) coexists with new Compose keyboard UI via `ComposeKeyboardViewFactory`
- **Settings**: SharedPreferences only (no Room for settings), wrapped by `SettingsRepository` with Flow observation
- **Database**: Room with 4 tables (macros, learned_words, clipboard_history, command_apps), version 2

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
./gradlew test             # Run unit tests (28 tests)
./gradlew installDebug     # Install on device
```
- ProGuard/R8 enabled for release builds with resource shrinking
- Lint enabled on release builds
- Version catalogs in `gradle/libs.versions.toml`

## Critical Pitfalls
- **JNI bridge**: Old package path is hardcoded in C++ — never rename `org.pocketworkstation.pckeyboard.BinaryDictionary`
- **Kotlin final-by-default**: Add `open` to classes/methods that Java code extends/overrides
- **Kotlin constants**: Use `const val` (not `@JvmField val`) for primitives used in Java switch/case
- **AGP 8.x R fields**: `R.styleable.*` non-constant — use if/else if, not switch/case
- **Destructive migration**: `DevKeyDatabase` uses `fallbackToDestructiveMigration()` — any schema change wipes all user data. Must write proper migrations before changing entities.
- **Agent permissions**: Add Edit, Write, Bash(*) to `.claude/settings.local.json` BEFORE running `/implement`

## Conventions
- **DI**: Manual constructor injection (no Dagger/Hilt)
- **State**: StateFlow + collectAsState() in Compose
- **Async**: Kotlin Coroutines with structured concurrency (no GlobalScope)
- **UI**: Jetpack Compose with Material You theming
- **Serialization**: kotlinx.serialization for JSON export/import
- **40 Java files** remain from original fork — progressive migration to Kotlin

## Current State
- All 5 implementation sessions complete (v1 feature-complete)
- Code review + cleanup done. Build passing, 28 unit tests passing.
- Blocker: Whisper model files needed for voice input (graceful degradation without them)
