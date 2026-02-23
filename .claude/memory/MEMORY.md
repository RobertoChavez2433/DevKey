# DevKey Memory

## Project Overview
- **Name**: DevKey
- **Type**: Android keyboard (IME) — fork of Hacker's Keyboard
- **Goal**: Bridge power-user productivity (terminal keys, command awareness) with modern typing UX (AI prediction, voice, Material You)
- **Unique features**: Command-aware autocorrect, recordable macro system, Ctrl-held shortcut mode

## Key Architecture Decisions
- Kotlin + C++ NDK (progressive modernization from Java)
- Jetpack Compose for UI (replacing XML layouts)
- TensorFlow Lite for on-device AI (no cloud dependency)
- Whisper (TF Lite, MIT license) for voice-to-text — fully offline
- Room database for macros, learned commands, clipboard, settings
- Data: local only + JSON export/import (no cloud sync)

## Final Layout Design
- SwiftKey-inspired dark layout (Clean Modern theme)
- Number row with Esc (left) and Tab (right) replacing backtick/0
- Long-press on ALL keys with visible hints (symbols on QWERTY, home, Z rows)
- Bottom row: Shift, Ctrl, Alt, Spacebar, ←↑↓→, Enter
- Toolbar: Clipboard, Voice, 123 (symbols), Macros (⚡)
- NO F-keys, NO swipe typing, NO oversized accent keys

## User Preferences
- Does NOT want swipe/flow typing
- Uses voice-to-text frequently
- Wants Material You + custom theme engine (later phase)
- Values privacy (fully on-device AI)
- Prefers progressive modernization (ship early, iterate)
- Doesn't want oversized or accent-colored keys — keep standard sizing
- Toolbar should be streamlined: clipboard, voice, symbols, macros only

## Key Documents
- **Design doc**: `docs/plans/2026-02-23-devkey-design.md`
- **Implementation plan**: `.claude/plans/devkey-implementation-plan.md` (5 sessions)
- **Research**: `docs/research/` (3 analysis files + README)
- **State**: `.claude/autoload/_state.md`

## Implementation Plan (5 Sessions)
1. ~~Infrastructure & Project Scaffolding~~ — **DONE** (Session 5)
2. Keyboard Layout & Rendering (Compose UI, touch, modifiers, theme)
3. Macros, Ctrl Mode & Clipboard (record/replay, chips, grid, clipboard manager)
4. AI Prediction, Command Mode & Voice (TF Lite, Whisper, command-aware)
5. Settings, Export/Import, Polish & Testing (settings hub, performance, docs)

## Build & Migration Patterns
- **Kotlin version**: 2.0.21 (required for `kotlin.plugin.compose`)
- **AGP**: 8.5.2, **Gradle**: 8.7, **KSP**: 2.0.21-1.0.27
- **JNI bridge**: Old package path (`org.pocketworkstation.pckeyboard.BinaryDictionary`) wraps native methods — DO NOT rename
- **Kotlin→Java interop**: Mark classes/methods `open` that Java subclasses override. Use `const val` for constants used in Java switch/case.
- **AGP 8.x migration**: R.styleable fields are non-constant — convert switch/case to if/else if
- **/implement permissions**: Add Edit, Write, Bash(*) to settings.local.json BEFORE running orchestrator agents
