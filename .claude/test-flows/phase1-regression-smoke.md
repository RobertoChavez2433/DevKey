# Phase 1.7 — Existing Feature Regression Smoke

**Spec**: `.claude/specs/2026-04-08-pre-release-vision-spec.md` §2.1
**Plan**: `.claude/plans/2026-04-08-pre-release-phase1.md` Phase 8
**When to run**: AFTER Phase 1-7 of this plan have shipped AND before
the Phase-2 umbrella spec (regression infrastructure) begins.

## Surfaces in scope

### Clipboard panel (ClipboardPanel.kt)
- [ ] Open panel via toolbar
- [ ] Copy external text → entry appears
- [ ] Tap entry → inserted at cursor
- [ ] Pin / unpin / delete entries
- [ ] Panel closes cleanly on backspace / back gesture

### Macro system (MacroManagerScreen.kt)
- [ ] Record a macro from settings screen
- [ ] Replay macro via long-press key
- [ ] Edit / delete macro
- [ ] Macro survives IME restart

### Command mode (CommandAppManagerScreen.kt)
- [ ] Auto-detects terminal apps on install
- [ ] Manual toggle in settings
- [ ] Command palette opens / closes
- [ ] Common commands execute

### Plugin system (PluginManager.kt) — post Phase 3 gate
- [ ] Release build: `logcat` shows `"Plugin dictionaries disabled in release build (GH #4)"` at onCreate
- [ ] Release build: no foreign-package resources are loaded (`logcat` has no `DevKey/PluginManager` load-success messages)
- [ ] Debug build: plugin loading still works — any previously-installed dictionary plugin is discoverable
- [ ] Both builds: `Suggest.getDictionary` null-safe path still returns valid main dict

### Keyboard mode switching — spec §2.1 "all three keyboard modes"
- [ ] Switch Qwerty → Compact → Full via the mode key; each layout renders without crash
- [ ] Input works in each of the three base modes (type, backspace, suggestions appear)
- [ ] Toggle to Symbols variant in each mode; layout renders correctly
- [ ] Toggle to Fn variant in each mode; layout renders correctly
- [ ] Return from Symbols/Fn back to letter mode — no stuck-state bugs
- [ ] Long-press popups still work in COMPACT mode after Phase 5 retune (pick any vowel → verify accent popup opens; pick any consonant → verify shift-symbol long-press fires)

### Existing /test tiers (run via /test skill)
- [ ] FULL tier green
- [ ] COMPACT tier green
- [ ] COMPACT_DEV tier green
- [ ] No new regressions vs. last green run logged in `.claude/test-results/`

## Exit criteria

All checkboxes ticked AND no new defects filed. Failures → file a
`defect` issue per `.claude/docs/defects.md` conventions.
