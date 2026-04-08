# Blast Radius — Phase 1

**⚠️ METHODOLOGY NOTE**: jcodemunch `get_blast_radius` returned `direct_dependents_count = 0` for every symbol queried in this Kotlin repo due to broken import resolution. Values below are reconstructed via `search_text` call-site analysis.

---

## `setNextSuggestions` (LatinIME.kt:1895)

**Internal call sites (within LatinIME.kt only)**: 7 (lines 905, 960, 1687, 1809, 1839, 1889, 2476)
**External importers**: 0
**Modification blast**: Contained entirely within LatinIME.kt. Safe to modify in place.

## `Suggest.getSuggestions(view, wordComposer, includeTypedWordIfValid, prevWordForBigram)`

**Call sites** (searched):
- `LatinIME.kt:1687`-area — `updateSuggestions()` is the main caller
- Likely a few more inside LatinIME — not fully enumerated (broken import graph)
- `PredictionEngine.kt` has a wrapper (`DictionaryProvider.getSuggestions`) but uses a different path

**Modification strategy for next-word**: Add new method `getNextWordSuggestions(prevWord: CharSequence): List<CharSequence>` — NO existing signature change, zero risk to current callers.

## `VoiceInputEngine` class

**Instantiation sites**: 1 (`DevKeyKeyboard.kt:81`)
**Method call sites**:
- `startListening` — 1 (DevKeyKeyboard.kt:161)
- `stopListening` — 1 (DevKeyKeyboard.kt:241)
- `cancelListening` — 2 (DevKeyKeyboard.kt:157, 249)
- `release` — 1 (DevKeyKeyboard.kt:85)
- `.state.collectAsState()` — 1 (DevKeyKeyboard.kt:129)
- `.amplitude.collectAsState()` — 1 (DevKeyKeyboard.kt:130)
- `VoiceInputEngine.VoiceState.*` enum refs in `VoiceInputPanel.kt` (5 sites)

**Modification blast for Phase 1.1/1.6**: Adding asset files requires ZERO code changes. Phase 1 does not touch VoiceInputEngine source. If behavioral tweaks are needed in 1.6 verification, they're contained in VoiceInputEngine.kt itself.

## `PluginManager` class

**Instantiation sites**: 1 (`LatinIME.kt:380`)
**Static method calls**:
- `PluginManager.getPluginDictionaries(ctx)` — 1 (`LatinIME.kt:379`)
- `PluginManager.getDictionary(ctx, lang)` — 2 (`Suggest.kt:70`, `InputLanguageSelection.kt:111`)

**Modification strategy for GH #4 debug gate**: Wrap early-return inside `getPluginDictionaries` body + `getDictionary` body. All callers continue to work — method returns empty map / null in release builds, same as if no plugins were installed. Zero caller-side churn.

## `KeyData` data class

**Usage**: ~every layout builder (QwertyLayout, SymbolsLayout) and KeyView, KeyRow, KeyboardView, test files.
**Field adds (like a `longPressCodes: List<Int>` for multi-char popup)**: Data class has default-parameter constructor. Adding a new optional field with default = null is backward-compatible with all call sites.

## `QwertyLayout` / `SymbolsLayout`

**Usage**: Called from `DevKeyKeyboard.kt` via `QwertyLayout.getLayout(mode)`. Test coverage in `QwertyLayoutFullTest.kt`, `QwertyLayoutCompactTest.kt`.
**Modification blast for long-press data fill**: Contained in layout files. Tests will catch structural regressions.

## `DevKeyTheme`

**Modification blast for Phase 1.3 layout fidelity**: Any token value change is a pure visual tweak. Referenced across ~15 UI files. No functional risk; visual regression risk must be caught by SwiftKey reference diff tests (Phase 1.2 / 1.3 output).

## `DevKeyLogger.enableServer(url)`

**Call sites in production**: 0
**Required plan action**: Add 1 call in test bootstrap path (TBD in Phase 2; for Phase 1 verification, this can be a manual `adb shell am start` + test runner invocation).

---

## Cleanup targets (from dead_code analysis)

**⚠️ find_dead_code reported 362 files as "dead"** — this is false (jcodemunch Kotlin limitation). No cleanup targets can be extracted from Step 7 output. Manual dead-code review is deferred to Phase 4 architecture refactor per spec.

---

## Summary

All Phase 1 modifications are **low blast-radius**:
- Long-press data fills: contained in layout files
- Next-word wiring: new method on Suggest + 1-2 call sites in LatinIME
- Plugin debug gate: 1-2 early-returns in PluginManager
- Voice asset add: zero source-code change
- Theme token tweaks: contained in DevKeyTheme.kt, visually-tested
- SwiftKey reference capture: test-flow files only, no source code changes
