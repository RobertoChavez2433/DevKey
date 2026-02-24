# DevKey Implementation Plan

**Date**: 2026-02-23
**Design Doc**: `docs/plans/2026-02-23-devkey-design.md`
**Sessions**: 5
**Status**: Active

---

## Session 1: Infrastructure & Project Scaffolding

**Goal**: Go from an empty repo to a buildable Android project with the correct structure, dependencies, and documentation so every future session knows exactly where things live.

### Tasks

#### 1.1 Fork & Project Setup
- Clone Hacker's Keyboard source into the repo
- Rename package to `dev.devkey.keyboard` (or similar)
- Update `build.gradle.kts` to Kotlin DSL with version catalogs
- Set `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`
- Configure ProGuard/R8 rules (placeholder)
- Verify the project builds and installs on an emulator/device as a baseline

#### 1.2 Kotlin Migration — Core Files Only
- Migrate `LatinIME.java` (the InputMethodService entry point) to Kotlin
- Migrate key event handling classes to Kotlin
- Keep C++ NDK layer untouched for now (dictionary lookup still works via JNI)
- Verify the keyboard still functions after migration (type, backspace, enter)

#### 1.3 Room Database Setup
- Add Room dependencies to version catalog
- Create the database class `DevKeyDatabase` with tables:
  - `macros` (id, name, key_sequence JSON, created_at, usage_count)
  - `learned_words` (id, word, frequency, context_app, is_command)
  - `clipboard_history` (id, content, timestamp, is_pinned, encrypted_content)
  - `settings` (key, value)
  - `command_apps` (package_name, mode)
- Create DAOs for each table
- Write migration strategy (version 1 baseline)
- Unit tests for all DAOs (insert, query, update, delete)

#### 1.4 Dependency Setup
- Add to version catalog:
  - Jetpack Compose (BOM)
  - Material 3
  - Room + KSP
  - Kotlin Coroutines
  - TensorFlow Lite (placeholder, not wired yet)
  - JUnit 5 + Espresso + Compose testing
- Configure KSP for Room annotation processing

#### 1.5 Project Structure
Create the following package structure:
```
dev.devkey.keyboard/
├── core/              # InputMethodService, key event handling
├── data/
│   ├── db/            # Room database, DAOs, entities
│   ├── export/        # JSON export/import logic
│   └── repository/    # Data access layer
├── feature/
│   ├── macro/         # Macro engine (record, save, replay)
│   ├── prediction/    # AI prediction engine
│   ├── clipboard/     # Clipboard manager
│   ├── voice/         # Whisper voice input
│   └── command/       # Command-aware autocorrect
├── ui/
│   ├── keyboard/      # Keyboard Compose UI (keys, rows, layout)
│   ├── toolbar/       # Toolbar Compose UI
│   ├── suggestion/    # Suggestion bar + macro chips
│   ├── settings/      # Settings screens
│   └── theme/         # Theme definitions, Clean Modern
└── util/              # Extensions, helpers
```

#### 1.6 Documentation Update
- Update `CLAUDE.md` with new source paths and build commands
- Update `.claude/autoload/_state.md` with Session 1 completion
- Create `docs/ARCHITECTURE.md` — maps the package structure, explains how IME lifecycle works, where to add new features
- Update `docs/plans/` with this plan file

### Session 1 Definition of Done
- [ ] Project builds with `./gradlew assembleDebug`
- [ ] Keyboard installs on device/emulator and basic typing works
- [ ] Room database created with all 5 tables, DAOs tested
- [ ] Package structure created (empty files/placeholders OK)
- [ ] `docs/ARCHITECTURE.md` exists and is accurate
- [ ] All dependencies in version catalog

### Key Files to Read Next Session
- `app/build.gradle.kts` — build config
- `docs/ARCHITECTURE.md` — where everything lives
- `app/src/main/java/dev/devkey/keyboard/core/` — IME entry point

---

## Session 2: Keyboard Layout & Rendering

**Goal**: Replace the old Hacker's Keyboard rendering with a Compose-based keyboard that matches Design Doc Section 3 (layout #8). User can type, use long-press, see hints, and use Shift/Ctrl/Alt.

### Prerequisites
- Session 1 complete (project builds, Room set up)

### Tasks

#### 2.1 Key Data Model
- Define `KeyData` class: primary char, long-press char, display label, key type (letter, modifier, action, symbol, arrow, special)
- Define `KeyboardLayout` class: list of rows, each row is a list of `KeyData`
- Build the full layout from Design Doc 3.3:
  - Number row: Esc, 1-9, Tab (with long-press ` and 0)
  - QWERTY row: Q-P with long-press !@#$%^&*()
  - Home row: A-L with long-press ~|\{}[]"'
  - Z-row: Shift, Z-M with long-press ;:/?<>_, Backspace
  - Bottom row: Shift, Ctrl, Alt, Space, ←↑↓→, Enter

#### 2.2 Keyboard Compose UI
- `KeyboardView` composable: renders the full keyboard
- `KeyRow` composable: renders a single row
- `KeyView` composable: renders a single key with:
  - Primary character (center)
  - Long-press hint (top-right, 9px, #666 color)
  - Press state animation (darken on press)
  - Key sizing: flex-based width, configurable height
- Apply Clean Modern theme:
  - Background: #1b1b1b
  - Key fill: #2a2a2a, rounded 6px
  - Special key colors per Design Doc 3.5
  - No borders, no shadows

#### 2.3 Touch Handling
- Tap: sends primary character
- Long-press: sends secondary character (with haptic feedback)
- Long-press duration: configurable (default 300ms)
- Key preview popup on press (optional, can be disabled)

#### 2.4 Modifier Key State Machine
- Shift: tap = one-shot shift (next char uppercase, then revert). Double-tap = caps lock. Visual indicator on key.
- Ctrl: tap = sticky modifier (stays active for next key, sends Ctrl+X combo). Hold = hold modifier. Visual indicator (highlighted background).
- Alt: same behavior as Ctrl
- Multi-modifier: Ctrl+Shift, Ctrl+Alt, etc. must work via multitouch
- State tracked in `ModifierStateManager` (StateFlow-based)

#### 2.5 Suggestion Bar Shell
- `SuggestionBar` composable: shows 3 suggestion slots with dividers
- Collapse arrow on left
- Hardcoded suggestions for now ("the", "I", "and") — prediction engine comes in Session 4
- Tappable: inserts the suggestion

#### 2.6 Toolbar Shell
- `ToolbarRow` composable: 4 buttons + overflow
- Clipboard button (📋) — no-op for now
- Voice button (🎤) — no-op for now
- Symbols button (123) — no-op for now
- Macro button (⚡) — no-op for now
- Overflow button (···) — no-op for now
- Buttons will be wired in Sessions 3 and 4

#### 2.7 Integration Tests
- IME lifecycle: keyboard appears when text field focused, disappears on back
- Key press: tap Q sends "q" to InputConnection
- Long-press: hold Q sends "!" to InputConnection
- Shift: tap Shift then Q sends "Q"
- Ctrl+C: hold Ctrl, tap C sends correct key event

### Session 2 Definition of Done
- [ ] Full keyboard renders matching design doc layout #8
- [ ] All long-press hints visible on all keys
- [ ] Typing works: letters, numbers, Esc, Tab, Backspace, Enter, arrows
- [ ] Shift one-shot + caps lock works
- [ ] Ctrl and Alt modifier combos work
- [ ] Suggestion bar renders (static suggestions)
- [ ] Toolbar renders (buttons visible, no-op)
- [ ] Integration tests pass for typing and modifiers

### Key Files to Read Next Session
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/` — all keyboard UI
- `app/src/main/java/dev/devkey/keyboard/core/ModifierStateManager.kt` — modifier logic
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyboardLayout.kt` — layout data

---

## Session 3: Macro System, Ctrl Mode & Clipboard

**Goal**: The three power-user features that make DevKey unique — macros (record, save, replay), Ctrl-held shortcut mode, and clipboard manager.

### Prerequisites
- Session 2 complete (keyboard renders, typing works, modifiers work)

### Tasks

#### 3.1 Macro Engine
- `MacroEngine` class:
  - `startRecording()`: begins capturing key events
  - `stopRecording(): MacroSequence`: returns captured sequence
  - `cancelRecording()`
  - `replay(macro: Macro)`: sends saved key events to InputConnection
- `MacroRepository`: CRUD operations via Room DAO
- Key event serialization: `Macro.key_sequence` stored as JSON array of `{modifiers: [], key: "c"}` objects
- Unit tests: record, serialize, deserialize, replay produces correct events

#### 3.2 Macro Recording UI
- Recording mode replaces toolbar + suggestion bar:
  - Red pulsing dot + "Recording macro…" label
  - "Cancel" button (discards)
  - "Stop" button (saves)
  - Live preview strip showing keys captured so far (e.g., `Ctrl + A → Ctrl + C`)
- On stop: dialog to name the macro, then save to Room
- Triggered from macro grid panel "+ Record" button

#### 3.3 Macro Chips in Suggestion Bar
- When ⚡ toolbar button tapped, suggestion bar swaps to `MacroChipStrip`:
  - Horizontally scrollable row of chips
  - Each chip: lightning icon + name + key combo in grey
  - Sorted by `usage_count` descending (most used first)
  - "+ Add" chip at the end (opens recording)
  - Tap chip → `MacroEngine.replay(macro)` + increment usage_count
- Tap ⚡ again → return to normal suggestion bar

#### 3.4 Macro Grid Panel
- When ⚡ long-pressed (or via setting), opens `MacroGridPanel`:
  - 4-column grid between toolbar and keyboard
  - Each cell: key combo (bold, amber) + label below
  - "+ Record" cell with dashed border
  - Long-press a macro → edit/delete dialog
- User can set default ⚡ behavior (chips vs grid) in settings

#### 3.5 Ctrl-Held Shortcut Mode
- When `ModifierStateManager` detects Ctrl held:
  - `CtrlModeOverlay` composable renders over the keyboard
  - Keys with known shortcuts: blue/purple tinted background, label below letter
  - Keys without shortcuts: 30% opacity
  - Number row: fully dimmed
  - Banner: "CTRL MODE — tap a key for shortcut"
  - Copy/Paste keys: red-tinted highlight
- Shortcut map defined in `CtrlShortcutMap.kt` (Design Doc Section 6)
- Setting: "Show shortcut labels when Ctrl held" (default: on)

#### 3.6 Clipboard Manager
- `ClipboardManager` class:
  - Monitors system clipboard via `ClipboardManager.OnPrimaryClipChangedListener`
  - Saves entries to Room `clipboard_history` table
  - Configurable max entries (default: 50)
  - Pin entries to prevent auto-deletion
  - Encrypt content at rest via Android Keystore
- `ClipboardPanel` composable:
  - Triggered by 📋 toolbar button
  - Vertical list of clipboard entries (newest first)
  - Tap to paste, long-press for pin/delete
  - Search bar at top
  - "Clear all" button (with confirmation)

#### 3.7 Symbols Layer (123)
- Tap 123 in toolbar → keyboard switches to symbols layout
- Layout: common symbols, math operators, currency signs
- Tap 123 again or ABC → back to QWERTY
- Long-press on symbol keys for variants (e.g., long-press $ for €, £, ¥)

#### 3.8 Tests
- Unit: MacroEngine record/replay, serialization, ClipboardManager encrypt/decrypt
- Integration: macro recording flow end-to-end, Ctrl-held overlay renders, clipboard save/retrieve

### Session 3 Definition of Done
- [ ] Macros: record, name, save, replay working end-to-end
- [ ] Macro chips appear in suggestion bar, sorted by usage
- [ ] Macro grid panel opens with saved macros
- [ ] Ctrl-held mode shows shortcut labels on keys
- [ ] Clipboard manager saves entries, paste works, encryption at rest
- [ ] Symbols (123) layer toggles correctly
- [ ] Default macros pre-loaded (Copy, Paste, Cut, Undo, Select All, Save, Find)
- [ ] Unit + integration tests pass

### Key Files to Read Next Session
- `app/src/main/java/dev/devkey/keyboard/feature/macro/` — macro engine + UI
- `app/src/main/java/dev/devkey/keyboard/feature/clipboard/` — clipboard
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/CtrlModeOverlay.kt` — Ctrl mode

---

## Session 4: AI Prediction, Command Mode & Voice

**Goal**: The intelligence layer — AI-powered word prediction, command-aware autocorrect that auto-detects terminal apps, and Whisper voice-to-text.

### Prerequisites
- Session 3 complete (macros, clipboard, Ctrl mode working)

### Tasks

#### 4.1 TF Lite Prediction Engine
- `PredictionEngine` class:
  - Load TF Lite model from assets
  - `predict(context: String, maxResults: Int): List<Prediction>`
  - Returns ranked word predictions based on preceding text
  - Runs inference on background coroutine (no UI jank)
- Model: start with a small n-gram or LSTM model trained on common English text
  - If a pre-trained TF Lite language model is available, use it
  - Otherwise, build a simple bigram/trigram predictor as v1 and upgrade later
- Wire predictions into `SuggestionBar`: replace hardcoded suggestions with live predictions

#### 4.2 Autocorrect Engine
- `AutocorrectEngine` class:
  - Compares typed word against dictionary + prediction model
  - Suggests corrections in suggestion bar (first slot = autocorrect, slots 2-3 = predictions)
  - Auto-apply top correction on space (configurable: aggressive, mild, off)
  - Respect learned words (never autocorrect a word the user has added to dictionary)

#### 4.3 Command-Aware Autocorrect
- `CommandModeDetector` class:
  - Maintains a list of known terminal package names (Termux, ConnectBot, JuiceSSH, etc.)
  - On `onStartInput()`, checks current app package against list
  - Returns `InputMode.NORMAL` or `InputMode.COMMAND`
- `CommandModeRepository`: Room-backed list of user-pinned apps + mode overrides
- Command mode behavior:
  - Autocorrect OFF
  - Predictions change to command/path/flag suggestions
  - Case sensitivity preserved (no auto-capitalize after period)
  - Suggestion bar sources: user's command history (from `learned_words` where `is_command = true`)
- Manual toggle: button in toolbar overflow menu to force command mode for current session

#### 4.4 Learned Words
- `LearningEngine` class:
  - Tracks words the user types (on commit, not every keystroke)
  - Stores in `learned_words` table with frequency count and app context
  - In command mode: stores commands separately (`is_command = true`)
  - Feeds into prediction engine as a personalized boost signal
- Privacy: all data local, included in export/import

#### 4.5 Whisper Voice-to-Text
- `VoiceInputEngine` class:
  - Load Whisper tiny model (TF Lite) from assets (~40MB)
  - `startListening()`: opens AudioRecord, captures PCM audio
  - `stopListening()`: runs inference, returns transcription
  - Auto-stop on silence (configurable timeout, default 2s)
- UI:
  - Tap 🎤 in toolbar → keyboard replaced with voice input UI
  - Pulsing mic animation
  - Waveform or volume indicator
  - Tap again to stop manually
  - Transcription inserted into text field
- Optional base model: downloadable from settings (~140MB), better accuracy

#### 4.6 Tests
- Unit: PredictionEngine returns ranked results, AutocorrectEngine respects learned words, CommandModeDetector identifies terminal apps
- Integration: prediction suggestions appear while typing, command mode activates in Termux, voice input produces text

### Session 4 Definition of Done
- [ ] AI predictions appear in suggestion bar while typing
- [ ] Autocorrect suggests corrections (configurable aggressiveness)
- [ ] Command mode auto-detects terminal apps, switches predictions
- [ ] Learned words tracked and boost future predictions
- [ ] Whisper voice-to-text works (tap mic, speak, text appears)
- [ ] Manual command mode toggle works
- [ ] Unit + integration tests pass

### Key Files to Read Next Session
- `app/src/main/java/dev/devkey/keyboard/feature/prediction/` — AI engine
- `app/src/main/java/dev/devkey/keyboard/feature/command/` — command mode
- `app/src/main/java/dev/devkey/keyboard/feature/voice/` — Whisper integration

---

## Session 5: Settings, Export/Import, Polish & Testing

**Goal**: Complete the app — settings hub, data export/import, compact mode, performance tuning, comprehensive testing, and final documentation.

### Prerequisites
- Session 4 complete (prediction, command mode, voice all working)

### Tasks

#### 5.1 Settings Hub
- `SettingsActivity` (Compose-based):
  - **General**: language, autocorrect aggressiveness (aggressive/mild/off), key press sound, haptic feedback, long-press duration slider
  - **Layout**: compact mode toggle (no number row), key height slider
  - **Modifiers**: Ctrl-held shortcut labels (on/off)
  - **Macros**: default ⚡ behavior (chips vs grid), manage macros (edit/delete/reorder)
  - **Clipboard**: max entries, auto-clear interval, encryption toggle
  - **Command Mode**: manage pinned apps, manual override list
  - **Voice**: model selection (tiny vs base), auto-stop timeout, download base model
  - **Backup**: export all data, import from file
  - **About**: version, open-source licenses, link to repo

#### 5.2 Export/Import
- `ExportManager` class:
  - Collects all Room data: macros, learned_words, clipboard (pinned only), settings, command_apps
  - Serializes to JSON with schema version header
  - Uses SAF (Storage Access Framework) for file picker
  - `exportToFile(uri: Uri)`
- `ImportManager` class:
  - `importFromFile(uri: Uri): ImportResult`
  - Validates JSON schema version
  - Conflict resolution: prompt user with "Keep existing", "Replace", "Merge"
  - Merge: combines without duplicates (macros matched by name, words by word+context)
- Unit tests: roundtrip export→import produces identical data

#### 5.3 Compact Mode
- Setting toggle: "Hide number row"
- When enabled: number row hidden, numbers become long-press on Q-P (1-0)
- Esc and Tab move to long-press on Shift and Backspace (or configurable)
- Layout dynamically switches based on setting

#### 5.4 Performance Optimization
- Profile keyboard rendering: target <16ms frame time
- Profile TF Lite inference: target <50ms per prediction
- Profile Whisper inference: target <2s for 5s of audio
- Lazy-load Whisper model (only when mic tapped for first time)
- Memory profiling: target <50MB RSS in normal use
- Battery: ensure no background drain when keyboard not visible

#### 5.5 Comprehensive Test Pass
- Run all unit tests, fix failures
- Run all integration tests, fix failures
- Manual test matrix:
  - Devices: small phone, large phone, tablet (if applicable)
  - Android versions: 8.0 (API 26), 12 (API 31), 14 (API 34)
  - Scenarios: normal typing, command mode in Termux, macro record+replay, voice input, clipboard, export/import, settings changes, compact mode toggle
- Accessibility pass: TalkBack compatibility, content descriptions on all buttons

#### 5.6 Final Documentation
- Update `docs/ARCHITECTURE.md` with final package structure
- Update `CLAUDE.md` with build commands, test commands, key paths
- Write `docs/USER-GUIDE.md` — brief guide for users (how to enable, use macros, command mode, etc.)
- Update `.claude/autoload/_state.md` — mark project as v1 complete
- Clean up `.claude/plans/` — move this plan to `completed/`

### Session 5 Definition of Done
- [ ] Settings hub fully functional with all sections
- [ ] Export/import works end-to-end (export → clear → import → all data restored)
- [ ] Compact mode toggles correctly
- [ ] Performance targets met (rendering, prediction, voice)
- [ ] All unit + integration tests pass
- [ ] Manual test matrix completed
- [ ] Documentation updated and accurate
- [ ] App is feature-complete for v1

---

## Cross-Session Reference

### Where to Find Things

| What | Where |
|------|-------|
| Design doc (PRD) | `docs/plans/2026-02-23-devkey-design.md` |
| This plan | `.claude/plans/devkey-implementation-plan.md` |
| Architecture | `docs/ARCHITECTURE.md` (created in Session 1) |
| Research | `docs/research/` (3 analysis files) |
| State tracking | `.claude/autoload/_state.md` |
| Defects | `.claude/autoload/_defects.md` |
| Memory | `.claude/memory/MEMORY.md` |

### Build Commands
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on connected device
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run integration tests
```

### Session Dependency Chain
```
Session 1 (Infrastructure)
    → Session 2 (Layout & Rendering)
        → Session 3 (Macros, Ctrl Mode, Clipboard)
            → Session 4 (AI, Command Mode, Voice)
                → Session 5 (Settings, Polish, Testing)
```

Each session builds on the previous. Do not skip sessions. Read "Key Files to Read Next Session" at the end of each session to get oriented quickly.
