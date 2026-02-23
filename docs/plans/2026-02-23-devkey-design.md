# DevKey Design Document

**Date**: 2026-02-23
**Status**: Approved
**Authors**: Human + Claude brainstorming sessions (Sessions 1-4)

## 1. Product Vision

DevKey is an open-source Android keyboard that bridges power-user productivity with modern typing UX. It forks Hacker's Keyboard and progressively modernizes it with AI prediction, voice input, a macro system, and Material You design.

**Target users**: Both power users (developers, sysadmins, terminal users) and general users equally.

**Core differentiator**: Command-aware autocorrect that auto-detects terminal apps and switches prediction mode, plus a recordable macro system for keyboard shortcuts.

## 2. Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin (migrated from Java) + C++ NDK |
| UI | Jetpack Compose |
| AI/ML | TensorFlow Lite (on-device, no cloud) |
| Voice | Whisper model via TF Lite (MIT license, free, on-device) |
| Database | Room |
| Build | Gradle Kotlin DSL |
| Min API | 26 (Android 8.0) |
| Target API | 34 (Android 14) |
| Base fork | github.com/klausw/hackerskeyboard |

## 3. Keyboard Layout

### 3.1 Base Layout

SwiftKey-inspired dark layout with the following structure (top to bottom):

1. **Toolbar row**: Clipboard, Voice (mic), Symbols (123), Macros (lightning bolt), overflow menu (...)
2. **Suggestion bar**: 3 predictions with collapse arrow on left, dividers between suggestions
3. **Number row**: Esc (long-press: backtick), 1-9, Tab (long-press: 0)
4. **QWERTY row**: Q-P with long-press symbols (! @ # $ % ^ & * ( ))
5. **Home row**: A-L with long-press symbols (~ | \ { } [ ] " ')
6. **Z-row**: Shift, Z-M with long-press symbols (; : / ? < > _), Backspace
7. **Bottom row**: Shift, Ctrl, Alt, Spacebar (DevKey), ← ↑ ↓ →, Enter

### 3.2 Key Properties

- **All keys have long-press secondary characters**, shown as small hints in the top-right corner
- Number row: Esc and Tab replace backtick and 0 positions; originals available via long-press
- Bottom row has three modifiers always visible: Shift, Ctrl, Alt
- Arrow cluster (4 keys) on the right side of the bottom row
- Standard-size Enter and Backspace (not oversized)

### 3.3 Long-Press Symbol Map

**QWERTY row** (Q through P):
```
Q→!  W→@  E→#  R→$  T→%  Y→^  U→&  I→*  O→(  P→)
```

**Home row** (A through L):
```
A→~  S→|  D→\  F→{  G→}  H→[  J→]  K→"  L→'
```

**Z-row** (Z through M):
```
Z→;  X→:  C→/  V→?  B→<  N→>  M→_
```

### 3.4 Compact Mode (Optional)

4-row layout without the dedicated number row. Numbers become long-press on Q-P (1-0). Saves vertical screen space. User toggles in settings.

### 3.5 Theme

**Clean Modern** (default): Dark background (#1b1b1b), rounded keys (6px radius), neutral colors. No accent-colored oversized keys. Toolbar and suggestion bar separated by subtle 1px borders. Monochrome with color-coded special keys only:
- Esc: dark red background (#3a2020), red text
- Tab: dark blue background (#203040), blue text
- Ctrl: dark purple background (#2a2a3a), purple text
- Alt: dark green background (#2a3a2a), green text
- Arrows: dark blue-grey background (#2a2a3a), blue text

Custom theme engine planned for later phases (Material You dynamic colors + user-created themes).

## 4. Toolbar

Streamlined to 4 core functions plus overflow:

| Button | Icon | Function |
|--------|------|----------|
| Clipboard | 📋 | Opens clipboard manager |
| Voice | 🎤 | Activates Whisper voice input |
| Symbols | 123 | Switches to symbol/number layer |
| Macros | ⚡ | Opens macro chips or grid panel |
| Overflow | ··· | Settings, themes, other options |

## 5. Macro System

### 5.1 Overview

Users can record, save, and replay keyboard macro sequences. Macros capture key events (including modifier combinations) and replay them on demand.

### 5.2 Macro Storage

Each macro stores:
- **name**: User-defined label (e.g., "Copy All")
- **sequence**: Array of key events with modifiers (e.g., `[{ctrl: true, key: "a"}, {ctrl: true, key: "c"}]`)
- **created**: Timestamp
- **usage_count**: Incremented on each use, used to sort by frequency

### 5.3 Macro Access — Chips in Suggestion Bar

Tapping the ⚡ toolbar button swaps the suggestion bar to show saved macros as horizontally scrollable chips. Each chip shows the macro name and key combo. Tap a chip to execute. Tap ⚡ again to return to suggestions. A "+ Add" chip at the end opens the recording flow.

Most frequently used macros appear first (sorted by usage_count).

### 5.4 Macro Access — Grid Panel

Alternative view: tapping ⚡ opens a 4-column grid panel between the toolbar and keyboard. Shows macros with labels and key combos. Includes a "+ Record" button. User can choose between chip view and grid view in settings.

### 5.5 Macro Recording Mode

When recording:
1. Red pulsing indicator replaces the toolbar/suggestion area
2. "Cancel" and "Stop" buttons visible
3. Live preview shows keys captured so far (e.g., `Ctrl + A → Ctrl + C`)
4. Every keypress (including modifiers) is captured
5. On "Stop", user is prompted to name the macro
6. Saved to Room database

### 5.6 Default Macros

Pre-loaded macros that ship with the app:
- Copy (Ctrl+C)
- Paste (Ctrl+V)
- Cut (Ctrl+X)
- Undo (Ctrl+Z)
- Select All (Ctrl+A)
- Save (Ctrl+S)
- Find (Ctrl+F)

Users can delete or edit these.

## 6. Ctrl-Held Shortcut Mode

When the user holds Ctrl, the keyboard transforms into a visual shortcut reference:

- Keys with known shortcuts light up with blue/purple backgrounds
- Each lit key shows the shortcut label below the letter (e.g., C → "Copy", V → "Paste")
- Keys without shortcuts dim to 30% opacity
- The number row dims fully
- A banner reads "CTRL MODE — tap a key for shortcut"
- Copy and Paste keys get special red-tinted highlight for visibility
- Release Ctrl to return to normal

This is an **optional feature** toggled in settings. When disabled, Ctrl acts as a pure modifier (hold + tap sends the combo directly without visual transformation).

**Shortcut labels shown**:
```
Q→Quit  W→Close  R→Redo  T→Tab  Y→Redo  O→Open  P→Print
A→Sel All  S→Save  D→Dup  F→Find  H→Replace  L→GoTo
Z→Undo  X→Cut  C→Copy  V→Paste  N→New
```

## 7. Command-Aware Autocorrect

### 7.1 Concept

DevKey detects when the user is typing in a terminal app and switches prediction behavior:

- **Normal mode**: Standard autocorrect and word prediction (aggressive corrections, dictionary-based)
- **Command mode**: Suppresses autocorrect, suggests commands/paths/flags instead, respects case sensitivity

### 7.2 Detection

Smart hybrid approach:
1. **Auto-detect**: Recognizes known terminal apps by package name (Termux, ConnectBot, JuiceSSH, etc.)
2. **Manual toggle**: User can force command mode for any app
3. **User-pinned apps**: Mark specific apps as "always command mode" in settings

### 7.3 Command Mode Behavior

- No autocorrect (won't change `chmod` to `change`)
- Suggests common commands, paths, and flags in the suggestion bar
- Learns user's command history per-app
- Case-sensitive (won't capitalize after period)
- Tilde, pipe, slash, and other shell characters given priority in long-press

## 8. Voice-to-Text

### 8.1 Engine

OpenAI Whisper model (tiny or base variant) converted to TensorFlow Lite format. Runs fully on-device — no network required, no API costs.

- **Model size**: ~40MB (tiny) or ~140MB (base)
- **Bundling**: Tiny model bundled with APK. Base model available as optional download in settings.
- **License**: MIT (fully open-source and free)

### 8.2 UX Flow

1. Tap 🎤 in toolbar
2. Mic indicator appears (pulsing animation)
3. User speaks
4. Auto-stop on silence detection (configurable timeout)
5. Transcription streams into the text field progressively
6. Tap mic again to stop manually

### 8.3 Offline Capability

Works fully offline on all supported API levels (26+). No fallback to cloud services.

## 9. Data Architecture

### 9.1 Storage

All data stored locally using Room database:

| Table | Contents |
|-------|----------|
| macros | name, key_sequence (JSON), created_at, usage_count |
| learned_words | word, frequency, context_app, is_command |
| clipboard_history | content, timestamp, is_pinned |
| settings | key-value pairs for all preferences |
| command_apps | package_name, mode (auto/manual/pinned) |

### 9.2 Export/Import

- **Format**: Single JSON file containing all tables
- **Export**: User triggers from Settings → Backup → Export. File saved to user-chosen location via SAF (Storage Access Framework)
- **Import**: User picks a JSON file. App validates schema, merges with existing data (no duplicates). Conflict resolution: user picks "keep existing", "replace", or "merge"
- **Scope**: Macros, learned words, settings, pinned clipboard items, command app list

### 9.3 Encryption

- Clipboard history encrypted at rest using Android Keystore
- Export files are NOT encrypted (plain JSON for portability). User can encrypt externally if needed.

### 9.4 AI Model Data

- TF Lite model files stored in app internal storage (`/data/data/...`)
- Learned prediction patterns stored in Room (linked to learned_words table)
- Whisper model: bundled tiny model in APK assets; optional base model downloaded to internal storage

## 10. Testing Strategy

### 10.1 Unit Tests

- Macro engine: record, save, replay, serialize/deserialize
- Key mapping: long-press resolver, symbol map, Ctrl-held mode
- Prediction engine: autocorrect, command detection, suggestion ranking
- Export/import: JSON serialization, schema validation, merge logic
- Clipboard manager: add, retrieve, pin, encryption/decryption

### 10.2 Integration Tests

- IME lifecycle: InputMethodService start/stop, configuration changes, input connection
- Room database: CRUD operations, migration scripts, export/import roundtrip
- Key event dispatch: modifier key state machine (Shift/Ctrl/Alt combos), multitouch
- Ctrl-held mode: state transitions, visual updates, shortcut execution
- Command mode detection: auto-detect terminal apps, mode switching

### 10.3 Manual Testing

- Touch target accuracy on real devices (various screen sizes)
- Key press latency measurement
- Voice input quality across noise environments
- Macro recording reliability
- Theme rendering across Android versions

## 11. Architecture Patterns

| Pattern | Usage |
|---------|-------|
| DI | Manual constructor injection |
| UI | Jetpack Compose + Material You |
| State | StateFlow + collectAsState() |
| Async | Kotlin Coroutines (structured concurrency, no GlobalScope) |
| Database | Room with migrations |
| IME Core | InputMethodService |
| Native | C++ NDK for dictionary lookup (liblatinime) |

## 12. Feature Phases

### Phase 1: Foundation
- Fork Hacker's Keyboard, migrate to Kotlin build
- Implement base layout (#8: Esc/Tab number row, full modifiers, arrows)
- Clean Modern theme
- Long-press on all keys with visible hints
- Basic suggestion bar

### Phase 2: Core Features
- Macro system (record, save, replay, chips, grid panel)
- Ctrl-held shortcut mode
- Clipboard manager
- Streamlined toolbar (clipboard, voice, symbols, macros)
- Room database + export/import

### Phase 3: Intelligence
- TF Lite prediction engine
- Command-aware autocorrect (auto-detect + manual + pinned)
- Whisper voice-to-text (on-device)
- Learned words and command history

### Phase 4: Polish
- Custom theme engine (Material You dynamic colors + user themes)
- Settings hub
- Performance optimization
- Distribution (TBD)
- Compact mode (no number row option)

## 13. Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Name | DevKey | Short, memorable, signals developer focus |
| Base | Fork Hacker's Keyboard | Proven IME foundation, 30+ languages, full modifier support |
| Layout model | SwiftKey-inspired | Most popular keyboard layout, familiar to users |
| Bottom-left keys | Shift + Ctrl (replacing 123/emoji) | Power users need modifiers always visible |
| Number row | Esc/Tab integrated (replace ` and 0) | Terminal keys always accessible without extra row |
| F-keys | Not included | Rarely pressed, waste of space |
| Swipe typing | Not included | User not interested |
| Symbols toggle | In toolbar (not bottom row) | Bottom row reserved for modifiers |
| Macro UX | Chips in suggestions + grid panel + recording mode | Multiple access patterns for different workflows |
| Voice engine | Whisper on-device via TF Lite | Free (MIT), accurate, fully offline |
| Data sync | Local only + JSON export/import | Privacy-first, simple, no cloud dependency |
| Testing | Unit + integration tests | Good coverage without slowing initial development |
| Distribution | TBD | Not a priority for v1 |
| AI processing | Fully on-device | Privacy, no API costs, works offline |
| Accent key color | No oversized accent keys | User feedback: keep keys standard-sized and neutral |
