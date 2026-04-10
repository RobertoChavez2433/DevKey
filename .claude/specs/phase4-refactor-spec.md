# Phase 4 Architecture Refactor Spec

**Date**: 2026-04-10
**Session**: 47
**Status**: APPROVED — ready for iteration
**Parent spec**: `.claude/specs/pre-release-spec.md` (Phase 4)
**Audit source**: jcodemunch index `local/Hackers_Keyboard_Fork-e04f25e5` (2026-04-10)

---

## 1. Hard Rules

- **200-line max** per `.kt` file under `app/src/main/java/`
- **~50-line target** for main entry points and initializers (`LatinIME.kt`, activities)
- No `@Composable` function with cyclomatic complexity > 15
- No function with nesting depth > 4
- `./gradlew assembleDebug` must stay green after every wave
- Preserve key dispatch chain: Compose/UI -> bridge -> LatinIME -> KeyEventSender
- Preserve JNI bridge at `org.pocketworkstation.pckeyboard.BinaryDictionary`
- `SettingsRepository` stays single source of truth for settings
- Layer boundaries (ui/core/data/feature/debug) stay clean (currently 0 violations, 0 cycles)
- Follow existing naming: `*Manager` (state), `*Engine` (compute), `*Repository`
  (data), `*Bridge` (boundary), `*Delegate` (circular dep break)
- Dual logging: legacy `DevKeyPress` tag + structured `DevKeyLogger.*`

---

## 2. Violation Inventory (33 files over 200 lines)

### Tier 1 — Massive (>600 lines, need 4+ output files each)

| File | Lines | Target outputs |
|------|------:|----------------|
| `LatinIME.kt` | 3,071 | ~50-line shell + 12 collaborators |
| `ComposeSequence.kt` | 1,144 | ~130-line runtime + data in `res/raw/` |
| `Keyboard.kt` | 1,004 | Key.kt + Row.kt + PopupContentBuilder + KeyboardXmlParser + Keyboard shell |
| `LatinKeyboard.kt` | 956 | SpaceBarRenderer + LocaleDragController + SlidingLocaleDrawable + F1KeyManager + KeyProximityResolver + shell |
| `ExpandableDictionary.kt` | 707 | TrieWalkState + BigramTraversal + shell |
| `DevKeyKeyboard.kt` | 604 | KeyboardDependencies + KeyboardPreferences + KeyboardDynamicPanel + KeyboardRenderLayer + shell |
| `Suggest.kt` | 600 | SuggestionPipeline + NextWordSuggester + SuggestionInserter + shell |

### Tier 2 — Large (400-600 lines, need 2-3 output files)

| File | Lines | Target outputs |
|------|------:|----------------|
| `KeyView.kt` | 534 | KeyGestureHandler + KeyBackground + KeyGlyph + KeyPopupPreview + shell |
| `KeyboardSwitcher.kt` | 528 | AutoModeSwitchStateMachine + KeyboardIdFactory + shell |
| `DevKeyTheme.kt` | 482 | DevKeyThemeColors + DevKeyThemeDimensions + DevKeyThemeTypography |
| `CandidateView.kt` | 482 | SuggestionRenderer + CandidateGestureHandler + PreviewPopupManager + shell |
| `QwertyLayout.kt` | 481 | QwertyLayoutFull + QwertyLayoutCompact + QwertyLayoutSharedRows |
| `KeyEventSender.kt` | 481 | TerminalSequenceTable + CharToKeyCodeMapper + ModifierChordingController + shell |
| `SettingsRepository.kt` | 402 | PrefRegistry + SettingsKeys + SettingsRepository shell |

### Tier 3 — Medium (200-400 lines, need 1-2 splits)

| File | Lines | Strategy |
|------|------:|----------|
| `UserBigramDictionary.kt` | 387 | Extract BigramDbHelper |
| `SettingsSubScreens.kt` | 363 | Split into individual screen files |
| `VoiceInputEngine.kt` | 355 | Extract AudioCaptureManager |
| `ModifierStateManager.kt` | 346 | Extract ModifierTransitionTable |
| `BinaryDictionary.kt` | 336 | Extract DictionaryLoader |
| `DevKeySettingsActivity.kt` | 324 | Extract SettingsNavGraph |
| `InputLanguageSelection.kt` | 316 | Extract LanguageListAdapter |
| `SettingsComponents.kt` | 305 | Split into individual component files |
| `KeyMapGenerator.kt` | 293 | Extract KeyCoordinateCalculator |
| `CommandAppManagerScreen.kt` | 291 | Extract CommandAppList + CommandAppDialog |
| `ClipboardPanel.kt` | 289 | Extract ClipboardList + ClipboardActions |
| `DevKeyWelcomeActivity.kt` | 287 | Extract WelcomeNavGraph + WelcomeSteps |
| `PluginManager.kt` | 278 | Extract PluginScanner + DictionaryPluginLoader |
| `AutoDictionary.kt` | 237 | Extract AutoDictionaryDbHelper |
| `MacroManagerScreen.kt` | 223 | Extract MacroList + MacroEditDialog |
| `CustomDictionaryScreen.kt` | 221 | Extract DictionaryWordList |
| `MacroGridPanel.kt` | 212 | Extract MacroGridItem |
| `ImportManager.kt` | 205 | Extract ConflictResolver |
| `LanguageSwitcher.kt` | 202 | Trim (barely over) |

---

## 3. Execution Waves

Each wave keeps the build green. Waves are dependency-ordered. Within each wave,
files marked **parallel** can be refactored by concurrent agents without conflicts.

### Wave 0 — Pure data splits (zero logic risk)

**Parallelizable: YES (all independent files, no cross-references)**

| Task | File | Action | New files |
|------|------|--------|-----------|
| 0.1 | `DevKeyTheme.kt` (482) | Split tokens by category | `DevKeyThemeColors.kt` (~130), `DevKeyThemeDimensions.kt` (~130), `DevKeyThemeTypography.kt` (~50), `DevKeyTheme.kt` shell (~80) |
| 0.2 | `QwertyLayout.kt` (481) | Split by layout mode | `QwertyLayoutFull.kt` (~170), `QwertyLayoutCompact.kt` (~145), `QwertyLayoutSharedRows.kt` (~75), `QwertyLayout.kt` routing (~50) |
| 0.3 | `ComposeSequence.kt` (1,144) | Move sequence table to `res/raw/compose_sequences.json`, keep ~130-line runtime | `ComposeSequence.kt` (~130), `ComposeSequenceLoader.kt` (~60), `res/raw/compose_sequences.json` (data) |
| 0.4 | `SettingsSubScreens.kt` (363) | Split into one file per screen | Individual screen files (~40-80 each) |
| 0.5 | `SettingsComponents.kt` (305) | Split into individual component files | Individual component files (~40-80 each) |

**Verification:** `./gradlew assembleDebug`

### Wave 1 — Low-risk extractions (no state coupling)

**Parallelizable: YES (independent files)**

| Task | File | Action | New files |
|------|------|--------|-----------|
| 1.1 | `KeyEventSender.kt` (481) | Extract data tables + cyclo-55 mapper | `TerminalSequenceTable.kt` (~80), `CharToKeyCodeMapper.kt` (~100), `ModifierChordingController.kt` (~100), `KeyEventSender.kt` shell (~120) |
| 1.2 | `CandidateView.kt` (482) | Extract rendering, gestures, popup (actively used — 15+ LatinIME call sites) | `SuggestionRenderer.kt` (~90), `CandidateGestureHandler.kt` (~70), `PreviewPopupManager.kt` (~50), `CandidateView.kt` shell (~150) |
| 1.3 | `SettingsRepository.kt` (402) | Extract pref registry + keys (3-way split needed) | `PrefRegistry.kt` (~130), `SettingsKeys.kt` (~80), `SettingsRepository.kt` (~190) |
| 1.4 | `PluginManager.kt` (278) | Extract scanner + dictionary loader | `PluginScanner.kt` (~100), `DictionaryPluginLoader.kt` (~80), `PluginManager.kt` (~80) |
| 1.5 | `ImportManager.kt` (205) | Extract conflict resolver | `ConflictResolver.kt` (~80), `ImportManager.kt` (~130) |

**Verification:** `./gradlew assembleDebug`

### Wave 2 — Compose UI decomposition (high-value hotspots)

**Parallelizable: KeyView and DevKeyKeyboard can run in parallel (different files)**

| Task | File | Action | New files |
|------|------|--------|-----------|
| 2.1 | `KeyView.kt` (534, cyclo 93) | Extract gesture handlers (162 lines inline), background, glyph, popup preview | `KeyGestureHandler.kt` (~120), `KeyBackground.kt` (~60), `KeyGlyph.kt` (~80), `KeyPopupPreview.kt` (~50), `KeyView.kt` orchestration (~100) |
| 2.2 | `DevKeyKeyboard.kt` (604, cyclo 74) | Extract dependencies, preferences, panels, render layer | `KeyboardDependencies.kt` (~60), `KeyboardPreferences.kt` (~60), `KeyboardDynamicPanel.kt` (~130), `KeyboardRenderLayer.kt` (~80), `DevKeyKeyboard.kt` shell (~100) |
| 2.3 | `ClipboardPanel.kt` (289) | Extract list + actions | `ClipboardList.kt` (~120), `ClipboardActions.kt` (~70), `ClipboardPanel.kt` (~80) |
| 2.4 | `MacroGridPanel.kt` (212) | Extract grid item | `MacroGridItem.kt` (~80), `MacroGridPanel.kt` (~130) |

**Verification:** `./gradlew assembleDebug` + visual smoke on emulator

### Wave 3 — Legacy keyboard model decomposition

**Parallelizable: Keyboard.kt and LatinKeyboard.kt can run in parallel**

| Task | File | Action | New files |
|------|------|--------|-----------|
| 3.1 | `Keyboard.kt` (1,004) | Extract Key (381 lines at L207-587), Row, popup builder, XML parser | `Key.kt` (~190), `Row.kt` (~40), `PopupContentBuilder.kt` (~120), `KeyboardXmlParser.kt` (~120), `KeyboardGeometry.kt` (~80), `Keyboard.kt` shell (~150) |
| 3.2 | `LatinKeyboard.kt` (956) | Extract space bar, locale drag, proximity, F1, SlidingLocaleDrawable (L852-955) | `SpaceBarRenderer.kt` (~100), `LocaleDragController.kt` (~80), `SlidingLocaleDrawable.kt` (~110), `F1KeyManager.kt` (~60), `KeyProximityResolver.kt` (~100), `LatinKeyboard.kt` shell (~150) |
| 3.3 | `KeyboardSwitcher.kt` (528) | Extract auto-mode state machine (L464-506) + keyboard ID factory (L300) | `AutoModeSwitchStateMachine.kt` (~60), `KeyboardIdFactory.kt` (~130), `KeyboardSwitcher.kt` shell (~180) |

**Verification:** `./gradlew assembleDebug`

### Wave 4 — Dictionary and suggestion layer

**Parallelizable: all three files are independent**

| Task | File | Action | New files |
|------|------|--------|-----------|
| 4.1 | `Suggest.kt` (600) | Extract pipeline (cyclo 53), next-word, inserter | `SuggestionPipeline.kt` (~150), `NextWordSuggester.kt` (~80), `SuggestionInserter.kt` (~100), `Suggest.kt` shell (~120) |
| 4.2 | `ExpandableDictionary.kt` (707) | Extract trie walk state (9-param getWordsRec at L218) + bigram traversal (runReverseLookUp L420, reverseLookUp L457, getBigrams L427) | `TrieWalkState.kt` (~120), `BigramTraversal.kt` (~100), `ExpandableDictionary.kt` shell (~180) |
| 4.3 | `UserBigramDictionary.kt` (387) | Extract DB helper | `BigramDbHelper.kt` (~120), `UserBigramDictionary.kt` (~180) |

**Verification:** `./gradlew assembleDebug`

### Wave 5 — LatinIME god-class decomposition (the big one)

**Must be sequential within the file, but each extraction is atomic.**
Order: start with cleanest boundaries, work inward.

| Task | Extract from LatinIME | New file | Est. lines | Lines removed |
|------|----------------------|----------|----------:|-------------:|
| 5.1 | Audio + vibration (L2711-2756) | `FeedbackManager.kt` | ~50 | ~43 |
| 5.2 | Swipe dispatch (L2532-2597) | `SwipeActionHandler.kt` | ~90 | ~66 |
| 5.3 | Dictionary writes (L1383-1387, L2271-2305, L2758-2761) | `DictionaryWriter.kt` | ~80 | ~60 |
| 5.4 | Punctuation/space heuristics (L1313-1382) | `PunctuationHeuristics.kt` | ~120 | ~120 |
| 5.5 | Suggestion coordinator (L1903-2270) | `SuggestionCoordinator.kt` | ~200 | ~250 |
| 5.6 | Shift state machine (L1272-1715) | `ShiftStateMachine.kt` (separate from Compose-side ModifierStateManager) | ~100 | ~130 |
| 5.7 | Input dispatcher (L1441-1885) | `InputDispatcher.kt` | ~200 | ~200 |
| 5.8 | Selection tracker (L1017-1082) | `SelectionTracker.kt` | ~80 | ~80 |
| 5.9 | Preference observer (L2394-2530, 137 lines) | `PreferenceObserver.kt` | ~150 | ~137 |
| 5.10 | Notification controller (L575-638) | `NotificationController.kt` | ~80 | ~60 |
| 5.11 | Lifecycle init (L277-719, 443 lines — larger than originally assumed) | Split into `ImeLifecycleController.kt` (~150) + `ImeDependencyInitializer.kt` (~150) | ~300 | remaining |
| 5.12 | `LatinIME.kt` becomes ~50-line wiring shell | — | — | — |

**After 5.12:** LatinIME.kt is ~50 lines: `onCreate` wires collaborators,
lifecycle methods delegate, `onKey` routes to `InputDispatcher`.

**Verification after each extraction:** `./gradlew assembleDebug`
**After full wave:** E2E regression on emulator

### Wave 6 — Remaining Tier 3 splits

**Parallelizable: all independent files**

| Task | File | Lines | Action |
|------|------|------:|--------|
| 6.1 | `VoiceInputEngine.kt` | 355 | Extract `AudioCaptureManager.kt` (~100) |
| 6.2 | `ModifierStateManager.kt` | 346 | Extract `ModifierTransitionTable.kt` (~80) |
| 6.3 | `BinaryDictionary.kt` | 336 | Extract `DictionaryLoader.kt` (~100) |
| 6.4 | `DevKeySettingsActivity.kt` | 324 | Extract `SettingsNavGraph.kt` (~130) |
| 6.5 | `InputLanguageSelection.kt` | 316 | Extract `LanguageListAdapter.kt` (~120) |
| 6.6 | `KeyMapGenerator.kt` | 293 | Extract `KeyCoordinateCalculator.kt` (~100) |
| 6.7 | `CommandAppManagerScreen.kt` | 291 | Extract `CommandAppList.kt` + `CommandAppDialog.kt` |
| 6.8 | `DevKeyWelcomeActivity.kt` | 287 | Extract `WelcomeNavGraph.kt` + `WelcomeSteps.kt` |
| 6.9 | `AutoDictionary.kt` | 237 | Extract `AutoDictionaryDbHelper.kt` (~80) |
| 6.10 | `MacroManagerScreen.kt` | 223 | Extract `MacroList.kt` + `MacroEditDialog.kt` |
| 6.11 | `CustomDictionaryScreen.kt` | 221 | Extract `DictionaryWordList.kt` |
| 6.12 | `LanguageSwitcher.kt` | 202 | Trim inline (~2 lines over, minor) |

**Verification:** `./gradlew assembleDebug`

### Wave 7 — Final gate

- Full E2E regression: `tools/e2e/e2e_runner.py` on emulator
- `./gradlew lint`
- Verify: `find app/src/main/java -name "*.kt" -exec wc -l {} + | awk '$1 > 200'` returns empty
- Re-run codemunch hotspots: verify no composable with cyclo >15
- After Phase 4: **re-run Phase 3 gate in full** per pre-release spec

---

## 4. Critical Files (must preserve / read before modifying)

- `LatinIME.kt` — IME boundary, 4 interfaces (InputMethodService, KeyboardActionListener, OnSharedPreferenceChangeListener, WordPromotionDelegate)
- `core/KeyboardActionBridge.kt` — bridge chain (Compose -> LatinIME)
- `core/KeyboardActionListener.kt` — interface contract (4 methods)
- `core/ModifierStateManager.kt` — Compose-side modifier state (StateFlow-based)
- `ui/keyboard/SessionDependencies.kt` — IME->Compose bridge (volatile fields)
- `ui/keyboard/ComposeKeyboardViewFactory.kt` — lifecycle sharing

### Reuse existing patterns:
- `ModifierStateManager` — model for state machine extractions (StateFlow)
- `KeyboardActionBridge` — model for boundary delegation
- `WordPromotionDelegate` — model for interface-based circular dep breaking
- `ClipboardRepository` — model for Repository pattern (suspend + Flow)
- `AutocorrectEngine` — model for Engine pattern (sealed result types)

---

## 5. Risk Mitigation

| Wave | Risk | Mitigation |
|------|------|-----------|
| 0 | Data split breaks theme token references | Grep all token usages before splitting; use `replace_all` for import updates |
| 2 | Compose recomposition regression from KeyView split | Visual smoke test on emulator after wave |
| 3 | XML parsing order dependency in Keyboard.kt | Keep virtual method override points intact; test all 6 keyboard modes |
| 5 | LatinIME collaborator wiring breaks key dispatch | Extract one at a time, build after each; test modifier+key combos |
| 5 | onCreate is 443 lines (larger than assumed) | Split ImeLifecycleController into two files if >200 |
| 5 | Shift state consolidation with ModifierStateManager | Keep separate (legacy vs Compose paths); share via interface only |
| 7 | E2E regression from cumulative changes | Run E2E after waves 2, 5, and 7 (not just at end) |

---

## 6. Parallelization Strategy

Within each wave, agents work on **different files** to avoid conflicts:

- Wave 0: up to 5 agents (all independent data files)
- Wave 1: up to 5 agents (all independent)
- Wave 2: 2 agents (KeyView || DevKeyKeyboard; then ClipboardPanel || MacroGridPanel)
- Wave 3: 3 agents (Keyboard || LatinKeyboard || KeyboardSwitcher)
- Wave 4: 3 agents (Suggest || ExpandableDictionary || UserBigramDictionary)
- Wave 5: **1 agent** (sequential within LatinIME — each step changes the same file)
- Wave 6: up to 6 agents (all independent; batch as 2 rounds of 3)

**Total estimated new files:** ~70
**Total estimated tasks:** ~50

---

## 7. Verified Findings (post-plan audit, 2026-04-10)

Corrections from line-by-line verification agents applied to this spec:

| Original claim | Verified actual | Spec adjustment |
|----------------|----------------|-----------------|
| LatinIME audio/vibration ~70 lines | 43 lines (L2711-2756) | FeedbackManager.kt ~50 lines |
| LatinIME onCreate L277-440 | L277-719 (443 lines) | ImeLifecycleController split into 2 files |
| LatinIME onSharedPreferenceChanged ~130 lines | 137 lines (L2394-2530) | Accurate |
| ComposeSequence "~50 lines logic" | ~130 lines logic + 885 data entries | Shell ~130, not ~80 |
| SettingsRepository "extract to <200" | Yields ~270 after one extract | 3-way split: PrefRegistry + SettingsKeys + shell |
| Keyboard.kt Key class "458 lines" | 381 lines (L207-587) | Key.kt target ~190 |
| `searchBigramSuggestion` exists | Does NOT exist in ExpandableDictionary | BigramTraversal uses `runReverseLookUp` (L420), `reverseLookUp` (L457), `getBigrams` (L427) |
| CandidateView "may be dead code" | **Actively used** — 15+ call sites in LatinIME | Must decompose, cannot delete |
| DevKeyTheme.kt "100% pure data" | Confirmed — zero logic, all val declarations | Clean split |
| QwertyLayout.kt "95% pure data" | Confirmed — ~40 lines routing, rest KeyData constructors | Clean split |

---

## 8. Codemunch Audit Snapshot (2026-04-10)

- **Index**: `local/Hackers_Keyboard_Fork-e04f25e5`
- **Total files**: 417 | **Symbols**: 2,888
- **Dependency cycles**: 0
- **Layer violations**: 0 (ui/core/data/feature/debug boundaries clean)
- **Avg cyclomatic complexity**: 3.31

### Top 10 Hotspots (complexity x churn, 180 days)

| # | Symbol | File | Cyclo | Nesting | Churn | Score |
|---|--------|------|------:|--------:|------:|------:|
| 1 | `KeyView` | ui/keyboard/KeyView.kt:66 | 93 | 10 | 10 | 223.0 |
| 2 | `DevKeyKeyboard` | ui/keyboard/DevKeyKeyboard.kt:69 | 74 | 9 | 13 | 195.3 |
| 3 | `handleSeparator` | LatinIME.kt:1773 | 45 | 7 | 9 | 103.6 |
| 4 | `getSuggestions` | Suggest.kt:189 | 53 | 7 | 4 | 85.3 |
| 5 | `onStartInputView` | LatinIME.kt:826 | 33 | 6 | 9 | 76.0 |
| 6 | `onKey` | LatinIME.kt:1441 | 33 | 5 | 9 | 76.0 |
| 7 | `onUpdateSelection` | LatinIME.kt:1021 | 29 | 6 | 9 | 66.8 |
| 8 | `pickSuggestionManually` | LatinIME.kt:2016 | 26 | 4 | 9 | 59.9 |
| 9 | `getPopupKeyboardContent` | Keyboard.kt:405 | 43 | 6 | 3 | 59.6 |
| 10 | `handleCharacter` | LatinIME.kt:1724 | 22 | 6 | 9 | 50.7 |

---

## 9. References

- Parent spec: `.claude/specs/pre-release-spec.md` (Phase 4 section)
- Architecture audit: `.claude/archive/2026-04-08-pre-release/research/architecture-audit.md`
- GH issue #9: umbrella refactor issue
- GH issue #8: LatinIME god class (superseded by #9 Phase C)
- Plan file: `.claude/plans/polymorphic-whistling-petal.md` (working plan)
