# Session State

**Last Updated**: 2026-02-23 | **Session**: 8

## Current Phase
- **Phase**: Session 3 Designed — Macros, Ctrl Mode, Clipboard & Symbols
- **Status**: Design doc and 9-phase implementation plan written and committed. Ready to run `/implement` with `session3-implementation-plan.md`.

## HOT CONTEXT - Resume Here

### EXACTLY WHERE WE LEFT OFF

**Session 3 brainstorming complete. Design doc + implementation plan committed. No implementation started yet. On-device testing of Session 2 keyboard still pending.**

- Brainstormed Session 3 features via `/brainstorming` — 8 design sections approved
- Wrote design doc: `docs/plans/2026-02-23-session3-macros-ctrl-clipboard-design.md`
- Wrote 9-phase implementation plan: `.claude/plans/session3-implementation-plan.md`
- Committed both as `d1a9927`

### Key Design Decisions

| Decision | Choice |
|----------|--------|
| View state | Centralized `KeyboardMode` sealed class (6 modes) |
| Macro capture | Logical combos, not raw keycodes |
| Macro access | Both chips (tap ⚡) + grid panel (long-press ⚡) |
| Ctrl Mode rendering | In-place key transformation (not overlay) |
| Ctrl Mode activation | HELD state only (not ONE_SHOT/LOCKED) |
| Clipboard encryption | Deferred to Session 5 |
| Symbols layer | Included in Session 3 scope |

### What Needs to Happen Next Session

1. **Run `/implement`** with `.claude/plans/session3-implementation-plan.md` — 9 phases, 17 new files, 8 modified files
2. **Test on device** — both Session 2 keyboard rendering AND Session 3 features
3. **Fix any issues** discovered during implementation or testing

## Blockers

None.

## Recent Sessions

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

### Session 4 (2026-02-23)
**Work**: Created 30 keyboard layout mockups across 3 rounds via html-sync MCP. Narrowed from 10+ designs to final layout (#8: Esc/Tab in number row, modifiers + arrows bottom). Completed all remaining design sections (data flow, voice, testing, distribution). Wrote full design doc (13 sections) and 5-session implementation plan.
**Decisions**: Final layout = #8. Macros = chips + grid + recording. Ctrl-held = shortcut labels. Whisper for voice. Local + export/import for data. Unit + integration tests.
**Next**: Begin Session 1 implementation (infrastructure & scaffolding).

## Active Plans

- **DevKey Implementation Plan** — `.claude/plans/devkey-implementation-plan.md` — Sessions 1-2 complete.
- **Session 3 Implementation Plan** — `.claude/plans/session3-implementation-plan.md` — Ready for /implement (9 phases).
- **Session 2 Implementation Plan** — `.claude/plans/session2-implementation-plan.md` — COMPLETE.

## Reference
- **Design doc (main)**: `docs/plans/2026-02-23-devkey-design.md`
- **Design doc (Session 2)**: `docs/plans/2026-02-23-session2-keyboard-layout-design.md`
- **Design doc (Session 3)**: `docs/plans/2026-02-23-session3-macros-ctrl-clipboard-design.md`
- **Implementation plan (main)**: `.claude/plans/devkey-implementation-plan.md`
- **Implementation plan (Session 2)**: `.claude/plans/session2-implementation-plan.md`
- **Implementation plan (Session 3)**: `.claude/plans/session3-implementation-plan.md`
- **Architecture**: `docs/ARCHITECTURE.md`
- **Research**: `docs/research/` (3 analysis files + README)
