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
./gradlew test             # Run unit tests (361 tests)
./gradlew installDebug     # Install on device
```

## Critical Pitfalls
- **JNI bridge**: Old package path hardcoded in C++ — never rename `org.pocketworkstation.pckeyboard.BinaryDictionary`
- **Destructive migration**: `DevKeyDatabase` uses `fallbackToDestructiveMigration()` — schema change wipes data
- **Agent permissions**: Add Edit, Write, Bash(*) to `.claude/settings.local.json` BEFORE running `/implement`
- **asciiToKeyCode table**: Maps ASCII chars to Android KEYCODE_* values (IntArray(128)). Critical for Ctrl+letter synthesis.
- **Debug logging intentional**: KeyPressLogger + Log.d calls were added in Session 19 for debugging — do NOT remove or gate behind BuildConfig.DEBUG
- **Init order in LatinIME.onCreate()**: `sKeyboardSettings` MUST be initialized BEFORE `KeyboardSwitcher.init()` — the switcher reads settings during init (Session 25 crash fix)
- **Background agents CAN run ADB** (corrected Session 33): Background Task agents with `test-wave-agent` subagent_type successfully execute ADB commands. Previous issue was permission-related, now resolved.
- **Keyboard Y coordinates**: Use calibrated values from `key-coordinates.md` (FULL mode). Broadcast-verified values from Session 32 are ~100px LOW on the emulator — agents confirmed key-coordinates.md values are correct (Session 33). Contacts "First name" field tap target: (540, 640) for current emulator state.
- **Test skill needs redesign (Session 33)**: Current wave-agent approach is fundamentally flawed — LLM agents are too slow/expensive for mechanical ADB work, don't reliably write documentation, and spend most tokens thinking instead of doing. See `memory/test-skill-redesign.md` for redesign plan.
- **DevKeyMap logcat fixed (Session 31)**: `LaunchedEffect(layoutMode)` + `dumpToLogcatWhenReady()` now waits for view layout. Broadcast trigger: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`. 3-tier calibration cascade in ime-setup: broadcast → calibration.json cache → Y-scan probe.
- **Calibration persistence**: Runtime coordinates saved to `.claude/test-flows/calibration.json` (gitignored, device-specific). Schema includes device serial, screen size, mode, timestamp, source, per-row Y + per-key X.
- **ModifierStateManager double-tap**: The KeyView flow is always `onModifierDown → onModifierUp → onModifierTap`. The down/up cycle resets state before tap runs. Fixed by adding `stateBeforeDown` tracking so tap can detect double-tap from the saved pre-down state.
- **IME Compose lifecycle pitfall (Session 27 root cause)**: In `InputMethodService`, Compose's `WindowRecomposer` resolves from the **decor/root view's lifecycle**, NOT the ComposeView's. If the decor has a DESTROYED lifecycle (even if ComposeView has a RESUMED one), recomposition silently never runs — initial composition works (forced by `setContent`) but NO state change triggers recomposition. Fix: share ONE lifecycle owner between decor and ComposeView via `ComposeKeyboardViewFactory.create(ctx, listener, decorView)`.
- **`adb install -r` does NOT restart the IME process**: The keyboard process survives APK replacement. Always `am force-stop` + `ime set` after install to ensure fresh code loads.
- **Bridge interference with legacy IME**: `bridge.onKeyPress(code)` and `bridge.onKeyRelease(code)` forward ALL keycodes to LatinIME. For keys handled locally by Compose (SYMBOLS, EMOJI, KEYCODE_ALPHA), this causes LatinIME's legacy `changeKeyboardMode()` to fire, interfering with Compose state. Fixed with `isComposeLocalCode()` filter in DevKeyKeyboard.kt.
- **`connectedAndroidTest` uninstalls the app**: Running instrumented tests removes the debug APK from the device. Must reinstall + `ime enable` + `ime set` after.
- **Espresso incompatible with API 36**: `InputManager.getInstance()` reflection fails on Android 16. Compose BOM 2024.06.00 Espresso deps need upgrading for API 36 emulators.

## Systematic Debugging

- **DevKeyLogger**: `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt` — structured debug logging class with session-scoped log capture and logcat integration.
- **HTTP debug server**: `tools/debug-server/server.js` — Node.js server on port 3947. Serves logcat streams and device state over HTTP for remote debugging sessions.
- Use `/systematic-debugging` skill for 10-phase root cause analysis on hard bugs.

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
- Migration COMMITTED: 4 commits on main (migration, tests, plans, crash fix)
- **.claude/ restructure COMPLETE (Phase 1-6 of 2026-03-17 plan)**: New dirs added — `rules/`, `hooks/`, `architecture-decisions/`, `agent-memory/`, `specs/`, `adversarial_reviews/`, `code-reviews/`, `dependency_graphs/`, `outputs/`. CLAUDE.md rewritten with 9-skill table, domain rules, context efficiency, expanded directory reference.
- 3 keyboard modes: Compact (SwiftKey), Compact Dev (long-press numbers), Full (6-row + utility)
- Theme: teal monochrome design token system
- Build passing, 361 unit tests passing (355 + 6 new ModifierStateManager double-tap tests)
- Keyboard RUNNING on emulator — FULL mode E2E mostly verified (Session 28: typing, 123 toggle, modifiers, numbers all PASS)
- Caps Lock fix APPLIED + E2E verified (ONE_SHOT→LOCKED→OFF cycle)
- 123 mode switch FIXED + E2E verified (Normal→Symbols→Normal round-trip)
- Performance: 6.8ms avg key latency, 0 ANRs, 0 dropped keys
- Compose UI tests: 14 tests written, ALL fail on API 36 emulator (Espresso compat), 9/14 pass on physical device
- Needs: Commit Session 27 changes, COMPACT/COMPACT_DEV testing, arrow key completion, Compose test dep upgrade
