# Session State

**Last Updated**: 2026-02-23 | **Session**: 9

## Current Phase
- **Phase**: Session 4 Designed — Voice-to-Text, Command Mode & Autocorrect
- **Status**: Session 3 fully implemented (confirmed). Session 4 design doc and 8-phase implementation plan written and committed. Ready to run `/implement` with `session4-implementation-plan.md`.

## HOT CONTEXT - Resume Here

### EXACTLY WHERE WE LEFT OFF

**Session 3 is FULLY IMPLEMENTED (verified — all 17 new files + 8 modified files present). Session 4 brainstorming complete. Design doc + implementation plan committed. No Session 4 implementation started yet.**

- Confirmed Session 3 implementation is complete (explore agent verified all files)
- Brainstormed Session 4 features via `/brainstorming` — 5 design sections approved
- Wrote design doc: `docs/plans/2026-02-23-session4-voice-command-autocorrect-design.md` (committed `c9b9c30`)
- Wrote 8-phase implementation plan: `.claude/plans/session4-implementation-plan.md` (committed `08b73c0`)

### Key Design Decisions

| Decision | Choice |
|----------|--------|
| Prediction approach | Dictionary + n-gram (no TF Lite for prediction — user doesn't use next-word much) |
| Autocorrect level | Standard: completion + basic edit-distance correction (default: mild) |
| Voice model | Whisper tiny.en (~40MB) bundled in APK assets |
| Voice model delivery | Bundled in assets/ (no download step) |
| Command mode | Full auto-detect terminal apps + manual toggle in toolbar overflow |
| Java↔Compose bridge | SessionDependencies singleton for Suggest/CommandModeDetector |

### What Needs to Happen Next Session

1. **Run `/implement`** with `.claude/plans/session4-implementation-plan.md` — 8 phases, 12 new files, 7 modified files
2. **Procure Whisper model files** — `whisper-tiny.en.tflite` + `filters_vocab_en.bin` from whisper.tflite/whisper_android repos
3. **Test on device** — Session 3 features AND Session 4 features

## Blockers

- **Whisper model files**: Need to download `whisper-tiny.en.tflite` (~40MB) and `filters_vocab_en.bin` from GitHub repos before voice can work. Plan has graceful degradation if not available.

## Recent Sessions

### Session 9 (2026-02-23)
**Work**: Confirmed Session 3 fully implemented. Brainstormed Session 4 (Voice, Command Mode, Autocorrect). Researched TF Lite models — GPT-2 too large/slow for keyboard, pivoted to dictionary-based prediction. 5 design sections approved. Wrote design doc and 8-phase implementation plan. Committed both.
**Decisions**: No TF Lite for prediction (user doesn't use next-word), Whisper tiny.en bundled in APK, dictionary + edit-distance autocorrect (mild default), auto-detect terminals + manual toggle, SessionDependencies singleton for Java↔Compose bridge.
**Next**: Run /implement on session4-implementation-plan.md, procure Whisper model files, test on device.

### Session 8 (2026-02-23)
**Work**: Brainstormed Session 3 (Macros, Ctrl Mode, Clipboard & Symbols). 8 design sections approved. Wrote design doc and 9-phase implementation plan. Committed.
**Decisions**: KeyboardMode sealed class, logical combo macro capture, both chips+grid, in-place Ctrl transform, defer clipboard encryption, include symbols layer.
**Next**: Run /implement on session3-implementation-plan.md, test on device.

### Session 7 (2026-02-23)
**Work**: Implemented Session 2 (Keyboard Layout & Rendering) via /implement orchestrator. 7 phases, 13 new Compose files, 2 modified files. All quality gates passed. Committed.
**Decisions**: Keycodes duplicated as private consts (Java package-private), detectTapGestures for long-press/repeat, ComposeKeyboardViewFactory for IME lifecycle, phases 2 & 4 parallel.
**Next**: Test on device, begin Session 3 (Macros, Ctrl Mode & Clipboard).

### Session 6 (2026-02-23)
**Work**: Brainstormed Session 2 (Keyboard Layout & Rendering) design. Wrote detailed design doc and 7-phase implementation plan. Explored codebase to understand legacy rendering pipeline (LatinKeyboardBaseView Canvas rendering, OnKeyboardActionListener interface, XML layout parsing).
**Decisions**: Clean Compose break, weight-based flex keys, ComposeView in LatinIME, direct long-press insert, key preview popup, English only.
**Next**: Run /implement on session2-implementation-plan.md.

### Session 5 (2026-02-23)
**Work**: Implemented Session 1 — Infrastructure & Project Scaffolding. Cloned Hacker's Keyboard, repackaged to dev.devkey.keyboard (44 Java files + 66 resource XMLs), converted build system to Kotlin DSL with version catalogs, created Room database (5 tables, 5 entities, 5 DAOs), migrated Keyboard.java and KeyboardSwitcher.java to Kotlin, scaffolded 14 package subdirectories, created ARCHITECTURE.md. Fixed R.styleable switch/case errors (AGP 8.x non-constant R fields), Kotlin open/final interop issues, and Kotlin 2.0 compose compiler plugin.
**Decisions**: Kotlin 2.0.21, AGP 8.5.2, JNI bridge at old package path, LatinIME stays Java for now.
**Next**: Begin Session 2 (Keyboard Layout & Rendering).

## Active Plans

- **DevKey Implementation Plan** — `.claude/plans/devkey-implementation-plan.md` — Sessions 1-3 complete.
- **Session 4 Implementation Plan** — `.claude/plans/session4-implementation-plan.md` — Ready for /implement (8 phases).
- **Session 3 Implementation Plan** — `.claude/plans/session3-implementation-plan.md` — COMPLETE.
- **Session 2 Implementation Plan** — `.claude/plans/session2-implementation-plan.md` — COMPLETE.

## Reference
- **Design doc (main)**: `docs/plans/2026-02-23-devkey-design.md`
- **Design doc (Session 2)**: `docs/plans/2026-02-23-session2-keyboard-layout-design.md`
- **Design doc (Session 3)**: `docs/plans/2026-02-23-session3-macros-ctrl-clipboard-design.md`
- **Design doc (Session 4)**: `docs/plans/2026-02-23-session4-voice-command-autocorrect-design.md`
- **Implementation plan (main)**: `.claude/plans/devkey-implementation-plan.md`
- **Implementation plan (Session 2)**: `.claude/plans/session2-implementation-plan.md`
- **Implementation plan (Session 3)**: `.claude/plans/session3-implementation-plan.md`
- **Implementation plan (Session 4)**: `.claude/plans/session4-implementation-plan.md`
- **Architecture**: `docs/ARCHITECTURE.md`
- **Research**: `docs/research/` (3 analysis files + README)
