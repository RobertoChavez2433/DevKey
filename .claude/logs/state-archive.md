# State Archive

Archived session entries from `_state.md` rotation.

---

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
