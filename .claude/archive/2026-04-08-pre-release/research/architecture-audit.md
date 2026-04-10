# DevKey Pre-Release Architecture Audit

**Date**: 2026-04-08
**Session**: 39
**Index**: `local/Hackers_Keyboard_Fork-e04f25e5` (Kotlin first-class, git HEAD 2391b5a)
**Methodology**: jcodemunch AST-derived call graph + cyclomatic/nesting/churn hotspot ranking + responsibility analysis of file outlines.

## Hard Rule: 400-Line Limit

**User mandate (Session 39):** No class, file, method, helper, controller, or any other unit may exceed **400 lines**. This is a firm, non-negotiable release criterion. All violations are decomposition targets — no exceptions.

### Complete list of 400-line violations (file level)

Scanned `app/src/main/java` for Kotlin files > 400 lines. **9 files violate the rule:**

| # | File | Lines | Over by |
|---|---|---:|---:|
| 1 | `keyboard/LatinIME.kt` | 2,679 | **+2,279** (6.7× over) |
| 2 | `keyboard/ComposeSequence.kt` | 1,144 | +744 |
| 3 | `keyboard/Keyboard.kt` | 1,004 | +604 |
| 4 | `keyboard/LatinKeyboard.kt` | 956 | +556 |
| 5 | `keyboard/ExpandableDictionary.kt` | 707 | +307 |
| 6 | `keyboard/Suggest.kt` | 533 | +133 |
| 7 | `keyboard/KeyboardSwitcher.kt` | 528 | +128 |
| 8 | `keyboard/core/KeyEventSender.kt` | 481 | +81 |
| 9 | `keyboard/CandidateView.kt` | 473 | +73 |

**Method level:** no method currently exceeds 400 lines. The longest are `LatinIME.onSharedPreferenceChanged` (~128 lines) and `LatinIME.onCreate` (~164 lines). Both are comfortably under the limit and are addressed by the file-level decomposition in §1.

**Files just under the limit (monitor — do not let them grow):** `data/repository/SettingsRepository.kt` (392), `ui/keyboard/DevKeyKeyboard.kt` (390), `UserBigramDictionary.kt` (387), `ui/settings/SettingsSubScreens.kt` (364), `ui/keyboard/QwertyLayout.kt` (351), `ui/keyboard/KeyView.kt` (346). Several of these contain extremely complex single functions — see §2 and §3 — which are release concerns on complexity grounds even though the files themselves are compliant.

---

## Executive Summary

DevKey has **9 files** in violation of the 400-line rule and **two monolithic Compose functions** whose files are compliant but whose complexity/nesting/churn make them the #1 and #2 hotspots project-wide. LatinIME.kt is the dominant risk: 2,679 lines, 141 methods, 12+ responsibilities, and the single highest-churn area of the codebase — it contains 10 of the top 25 hotspots. `KeyView` and `DevKeyKeyboard` composables have cyclomatic complexity ≥42 with nesting depth ≥8 inside single function bodies — these are UI recomposition landmines.

**Top 5 offenders (ranked by release risk):**

| # | Symbol | File | Lines | Worst Metric | Risk |
|---|--------|------|------:|--------------|------|
| 1 | `LatinIME` (class) | `LatinIME.kt` | 2,679 | 141 methods, 12 responsibilities | **CRITICAL** |
| 2 | `KeyView` (composable) | `ui/keyboard/KeyView.kt` | 49–300 | cyclo **78**, nesting **10**, churn 7 | **CRITICAL** |
| 3 | `DevKeyKeyboard` (composable) | `ui/keyboard/DevKeyKeyboard.kt` | 63–357 | cyclo 42, nesting 8, churn 9 | **HIGH** |
| 4 | `LatinKeyboard` (class) | `LatinKeyboard.kt` | 956 | `isInside` cyclo 38, nesting 9; 5+ mixed responsibilities | **HIGH** |
| 5 | `Keyboard` (class) | `Keyboard.kt` | 1,004 | `getPopupKeyboardContent` cyclo 43; `loadKeyboard` nesting 9 | **HIGH** |

**Secondary concerns (medium priority):** `Suggest.getSuggestions` (cyclo 52), `KeyEventSender.sendModifiableKeyChar` (cyclo 55), `ExpandableDictionary.getWordsRec` (cyclo 40, **9 params**), `KeyboardSwitcher` (singleton god), `SettingsSubScreens.kt` (10 screens in one file).

**Architectural limit acknowledged:** Import-based jcodemunch tools (`find_references`, `get_blast_radius`, `find_importers`) cannot see same-package Kotlin references — same-package Kotlin has no import statements by language design. All findings below use the call-graph methodology (`get_call_hierarchy`, `get_impact_preview`, hotspot scores) which reports `methodology: ast_call_references`.

---

## 1. LatinIME.kt — CRITICAL GOD CLASS

**File**: `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`
**Size**: 2,679 lines (108 KB)
**Class**: `LatinIME : InputMethodService, KeyboardActionListener, SharedPreferences.OnSharedPreferenceChangeListener, WordPromotionDelegate`
**Method count**: 141 methods on the class + 2 nested classes (`WordAlternatives`, `TypedWordAlternatives`)

### Responsibilities (12 distinct concerns — all in one class)

Grouped by method cluster from the full file outline:

| # | Responsibility | Representative methods |
|---|---|---|
| 1 | **IME lifecycle** | `onCreate` (L261, 164 lines), `onDestroy`, `onConfigurationChanged`, `onCreateInputView`, `onCreateCandidatesView`, `onStartInputView` (L657, **cyclo 33**), `onFinishInput`, `onFinishInputView`, `onFinishCandidatesView` |
| 2 | **Notification / foreground service** | `createNotificationChannel`, `setNotification`, `onReceive` (nested broadcast receiver at L406) |
| 3 | **Selection / extracted-text tracking** | `onUpdateSelection` (L852, **cyclo 29**, 6 params), `onUpdateExtractedText`, `onExtractedTextClicked`, `onExtractedCursorMovement` |
| 4 | **Candidate / suggestion UI** | `setCandidatesViewShownInternal`, `setCandidatesViewShown`, `setSuggestions`, `updateSuggestions`, `showSuggestions` (×2 overloads), `showCorrections`, `postUpdateSuggestions`, `postUpdateOldSuggestions`, `setOldSuggestions`, `setNextSuggestions`, `clearSuggestions` |
| 5 | **Suggest / prediction engine wiring** | `initSuggest`, `isPredictionOn`, `isPredictionWanted`, `isCandidateStripVisible`, `pickDefaultSuggestion`, `pickSuggestionManually` (**cyclo 26**, hotspot), `pickSuggestion`, `getTypedSuggestions`, `applyTypedAlternatives` (**cyclo 16**), `saveWordInHistory`, `rememberReplacedWord` |
| 6 | **Dictionary writes** | `addWordToDictionary`, `promoteToUserDictionary`, `addToDictionaries`, `addToBigramDictionary`, `checkAddToDictionary` |
| 7 | **Key dispatch / input routing** | `onKey` (L1259, **cyclo 28**, hotspot), `onText`, `onCancel`, `onPress`, `onRelease`, `onKeyDown`, `onKeyUp`, `processMultiKey`, `handleCharacter` (L1522, **cyclo 22**), `handleSeparator` (L1570, **cyclo 25**), `handleBackspace` (**cyclo 16**), `handleClose`, `sendModifiableKeyChar`, `sendSpace`, `abortCorrection` |
| 8 | **Shift / modifier state machine** | `updateShiftKeyState` (L1090, **cyclo 18**), `getShiftState`, `isShiftCapsMode`, `getCursorCapsMode`, `startMultitouchShift`, `commitMultitouchShift`, `resetMultitouchShift`, `resetShift`, `handleShift`, `handleShiftInternal`, `setModCtrl`, `setModAlt`, `setModMeta`, `setModFn`, `getCapsOrShiftLockState`, `nextShiftState` |
| 9 | **Punctuation / space heuristics** | `swapPunctuationAndSpace`, `reswapPeriodAndSpace`, `doubleSpace`, `maybeRemovePreviousPeriod`, `removeTrailingSpace`, `initSuggestPuncList`, `isSuggestedPunctuation`, `isWordSeparator`, `isSentenceSeparator`, `getWordSeparators`, `isCursorTouchingWord`, `sameAsTextBeforeCursor`, `revertLastWord` |
| 10 | **Swipe gestures** | `doSwipeAction` (L2150, **cyclo 22**, hotspot), `swipeRight`, `swipeLeft`, `swipeUp`, `swipeDown` |
| 11 | **Audio / haptic feedback** | `updateRingerMode`, `getKeyClickVolume`, `playKeyClick`, `vibrate` (×2 overloads) |
| 12 | **Settings / prefs / options menu** | `onSharedPreferenceChanged` (L2022, **~128 lines**), `loadSettings`, `updateCorrectionMode`, `updateAutoTextEnabled`, `reloadKeyboards`, `showOptionsMenu`, `showInputMethodPicker`, `onOptionKeyPressed`, `onOptionKeyLongPressed`, `isShowingOptionDialog`, `changeKeyboardMode`, `launchSettings` (×2), `toggleLanguage`, `shouldShowVoiceButton` |

Plus assorted helpers: `getPrefInt` (×2), `getIntFromString`, `getHeight`, `getDictionary`, `dump`, `onAutoCompletionStateChanged`, `getCurrentWord`, `isConnectbot`, `isShiftMod`, `isAlphabet`, `isPortrait`, `suggestionsDisabled`, `preferCapitalization`, `isKeyboardVisible`, `resetPrediction`, `removeCandidateViewContainer`, `checkReCorrectionOnStart`, `getKeyboardModeNum`, `updateKeyboardOptions`, `commitTyped`.

### Hotspot methods inside LatinIME (complexity × churn, last 180 days)

| Method | Line | Cyclo | Nesting | Churn | Score |
|---|---:|---:|---:|---:|---:|
| `onStartInputView` | 657 | 33 | 6 | 4 | 53.1 |
| `onUpdateSelection` | 852 | 29 | 6 | 4 | 46.7 |
| `onKey` | 1259 | 28 | 5 | 4 | 45.1 |
| `pickSuggestionManually` | 1757 | 26 | 4 | 4 | 41.8 |
| `handleSeparator` | 1570 | 25 | 5 | 4 | 40.2 |
| `handleCharacter` | 1522 | 22 | 6 | 4 | 35.4 |
| `doSwipeAction` | 2150 | 22 | 4 | 4 | 35.4 |
| `updateShiftKeyState` | 1090 | 18 | 3 | 4 | 29.0 |
| `handleBackspace` | 1400 | 16 | 4 | 4 | 25.8 |
| `applyTypedAlternatives` | 1844 | 16 | 5 | 4 | 25.8 |

**10 of the top 25 project-wide hotspots live inside LatinIME.** This single file is the biggest bug-introduction risk area in the codebase.

### Call graph evidence

`handleCharacter` is called by `onKey`, `onText`, and `pickSuggestionManually` — tight intra-class coupling typical of a god class. Decomposing requires carving responsibilities with care to preserve this dispatch chain.

### Suggested decomposition

Extract to peer collaborators held by LatinIME:

1. **`ImeLifecycleController`** — `onCreate`/`onDestroy`/`onConfigurationChanged`/view creation (responsibility 1)
2. **`SuggestionCoordinator`** — all candidate strip / suggestion / pick / correction logic (responsibilities 4, 5)
3. **`ShiftStateMachine`** — multitouch shift + caps state (responsibility 8 — already partially modeled in `ModifierStateManager` under `core/`; consolidate)
4. **`InputDispatcher`** — `onKey`/`onText`/`handleCharacter`/`handleSeparator`/`handleBackspace` (responsibility 7)
5. **`PunctuationHeuristics`** — swap/double-space/period heuristics (responsibility 9)
6. **`SwipeActionHandler`** — `doSwipeAction` + swipe methods (responsibility 10)
7. **`FeedbackManager`** — audio + vibration (responsibility 11)
8. **`DictionaryWriter`** — promotion / add / bigram writes (responsibility 6)
9. **`PreferenceObserver`** — extract the 128-line `onSharedPreferenceChanged` into a delegate

**Target: LatinIME.kt < 400 lines.** With 141 methods spread across 12 responsibilities, this requires ~8 collaborator extractions (see list above). Every collaborator must itself be < 400 lines.

### Blast radius

From the call graph: `handleCharacter` has 3 callers, `onKey` has 1 (`KeyboardActionBridge` via listener), `updateShiftKeyState` has many internal callers. Extraction is feasible incrementally — start with `SwipeActionHandler` (cleanest boundary, 5 small methods) and `FeedbackManager` (no caller coupling) to build confidence.

---

## 2. KeyView.kt — CRITICAL MONOLITHIC COMPOSABLE

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyView.kt`
**Symbol**: `KeyView` composable (L49)
**Metrics**: **cyclomatic 78**, **max nesting 10**, churn 7 → **hotspot score 162.2 (#1 in project)**

### Problem

`KeyView` is a ~250-line single function body. Cyclomatic 78 means ~78 decision points; max nesting 10 means up to 10 levels of `if`/`when`/`Box`/`Row`/`Column`/`remember`/`LaunchedEffect` nested inside each other. Only 3 helper functions exist (`getKeyColors`, `getActiveKeyColor`, `getModifierType` at L301/331/341) and they cover only color selection. Everything else — popup preview, long-press handling, press animation, modifier glyph rendering, label/hint positioning, swipe tracking, haptic triggering — is inline.

### Recomposition risk

At this complexity + nesting, any state read inside `KeyView` invalidates the entire key on change. Given keys are rendered in grids of 30+ per layout, this is a per-frame recomposition tax across the whole keyboard.

### Suggested decomposition

Split into layered composables:

- `KeyBackground(key, pressed, theme)` — shape + color + ripple
- `KeyGlyph(key, modifierState)` — primary label, hint label, modifier indicators
- `KeyPopupPreview(key, pressState)` — long-press preview / accent popup
- `KeyPressDetector(key, onPress, onLongPress, onSwipe)` — gesture handling wrapper
- `KeyView(...) = Box { KeyPressDetector { KeyBackground(); KeyGlyph(); KeyPopupPreview() } }` — orchestration only

Target: no single function > cyclo 15, no nesting > 4.

---

## 3. DevKeyKeyboard.kt — HIGH MONOLITHIC COMPOSABLE

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`
**Symbol**: `DevKeyKeyboard` composable (L63)
**Metrics**: cyclomatic 42, nesting 8, churn **9** → hotspot score 96.7 (**#2 in project**, highest churn of any symbol)

### Problem

Second-largest Compose function. Only one private helper (`isComposeLocalCode` at L358). Holds the entire keyboard layout pipeline in one body: row layout, compose-sequence integration, key sizing, modifier routing, theme application.

Highest churn in the entire project (9 changes in 180 days) — this is the most-edited function, and its complexity amplifies the chance each edit introduces a regression.

### Suggested decomposition

- `KeyboardGrid(layout, theme, onKey)` — row/column positioning
- `ComposeSequenceHost(content)` — dead-key / compose state wrapper
- `KeyboardScaffold(modifiers, candidateStrip, keyboard)` — outer layout

---

## 4. LatinKeyboard.kt — HIGH GOD CHILD CLASS

**File**: `app/src/main/java/dev/devkey/keyboard/LatinKeyboard.kt`
**Size**: 956 lines; extends `Keyboard`
**Worst method**: `isInside` (L645, **cyclo 38, nesting 9**)

### Mixed responsibilities (5+)

1. **F1 dynamic key swapping** — `updateDynamicKeys`, `update123Key`, `updateF1Key`, `setMicF1Key`, `setSettingsF1Key`, `setNonMicF1Key`, `isF1Key`
2. **Space bar rendering** — `drawSpaceBar`, `updateSpaceBarForLocale`, `layoutSpaceBar`, `getTextWidth`, `getTextSizeFromTheme`, `getSpacePreviewWidth`, `drawSynthesizedSettingsHintImage`
3. **Language switcher drag UI** — `updateLocaleDrag`, `getLanguageChangeDirection`, `setLanguageSwitcher`, `isCurrentlyInSpace`, inner class `SlidingLocaleDrawable` (100+ lines: `draw`, `setDiff`, `getLanguageName`, alpha/filter/opacity/intrinsic overrides)
4. **Touch proximity detection** — `isInside` (cyclo 38!), `getNearestKeys`, `distanceFrom`, `inPrefList`, `setPreferredLetters`, inner `LatinKey.isInside`, `LatinKey.squaredDistanceFrom`, `LatinKey.isInsideSuper`
5. **Voice / shift / IME options wiring** — `setVoiceMode`, `setImeOptions`, `enableShiftLock`, `setShiftState`, `setExtension`, `getExtension`, `updateSymbolIcons`, `onAutoCompletionStateChanged`, `keyReleased`

### Suggested decomposition

- Extract `SpaceBarRenderer` (space bar + locale hint drawing)
- Extract `LocaleDragController` + keep `SlidingLocaleDrawable` as a focused peer class
- Extract `F1KeyDynamicContent` strategy
- Extract `KeyProximityResolver` (nearest keys + isInside logic)
- LatinKeyboard shrinks to layout/state orchestration

---

## 5. Keyboard.kt — HIGH GOD CLASS

**File**: `app/src/main/java/dev/devkey/keyboard/Keyboard.kt`
**Size**: 1,004 lines
**Worst methods**: `Key.getPopupKeyboardContent` (L405, **cyclo 43**), `Key.toString` (L568, **cyclo 27**), `loadKeyboard` (L866, **cyclo 25, nesting 9**), `fixAltChars` (L701, cyclo 19)

### Mixed responsibilities

1. **Key data model** — inner `Key` class with 15+ methods (`getCaseLabel`, `getPrimaryCode` ×2, `isDeadKey`, `getFromString`, `parseCSV`, `getHintLabel`, `getAltHintLabel`, `onPressed`, `onReleased`, `isDistinctCaps`, `isShifted`)
2. **Popup keyboard content generation** — `getPopupKeyboardContent` (cyclo 43 — huge case expression), `getPopupKeyboard`
3. **Row data model** — inner `Row` class
4. **XML parsing** — `loadKeyboard` (nesting 9 — deeply nested XML traversal), `parseKeyboardAttributes`, `skipToEndOfRow`, `is7BitAscii`, `getDimensionOrFraction`
5. **Key geometry** — `computeNearestNeighbors`, `setEdgeFlags`, `fixAltChars`
6. **Modifier indicators** — `setCtrlIndicator`, `setAltIndicator`, `setMetaIndicator`
7. **Shift state queries** — `isShifted`, `isShiftCaps`, `getShiftState`, `getShiftKeyIndex`

### Suggested decomposition

- Extract `KeyboardXmlParser` (loadKeyboard + helpers)
- Extract `PopupContentBuilder` for the cyclo-43 `getPopupKeyboardContent` — data-driven via a map rather than a giant when-expression
- Move `Key` and `Row` to their own files (data classes / sealed where possible)
- Keep `Keyboard` as the owning aggregate only

---

## 6. Suggest.kt — MEDIUM

**File**: `app/src/main/java/dev/devkey/keyboard/Suggest.kt`
**Size**: 533 lines
**Worst method**: `getSuggestions` (L183, **cyclo 52, 4 params**)

Structurally clean (~20 methods, single dictionary-coordination responsibility) but `getSuggestions` is a 130-line procedural blob combining: main dictionary lookup → user dictionary lookup → auto-dictionary lookup → common-prefix filtering → capitalization matching → bigram lookup → dedup → ranking.

**Suggestion**: Extract per-dictionary `SuggestionSource` strategy and compose results via a pipeline. `addWord` (cyclo 25, 6 params) similarly benefits from a small `SuggestionBuilder` aggregator.

---

## 7. KeyEventSender.kt — MEDIUM

**File**: `app/src/main/java/dev/devkey/keyboard/core/KeyEventSender.kt`
**Size**: 481 lines
**Worst method**: `sendModifiableKeyChar` (L396, **cyclo 55**)

### Mixed responsibilities

1. **Escape/Ctrl sequence lookup tables** — `buildEscSequences`, `buildCtrlSequences` (companion) — pure data
2. **Connectbot detection** — `isConnectbot`
3. **Meta state calculation** — `getMetaState`
4. **Raw key send** — `sendKeyDown`, `sendKeyUp`, `sendModifiedKeyDownUp` (×2)
5. **Modifier chording** — `sendShiftKey`, `sendCtrlKey`, `sendAltKey`, `sendMetaKey`, `sendModifierKeysDown`, `handleModifierKeysUp`, `sendModifierKeysUp`, `delayChording*` (×3)
6. **Special key dispatch** — `sendSpecialKey`, `sendTab`, `sendEscape`
7. **Character-to-keycode mapping** — `sendModifiableKeyChar` (cyclo 55 — the huge Char→keycode dispatch)

### Suggested decomposition

- `TerminalSequenceTable` (data only — esc + ctrl maps)
- `ModifierChordingController` (all modifier up/down chording + delays)
- `CharToKeyCodeMapper` (the cyclo-55 blob) — convert to a table lookup
- `KeyEventSender` becomes a thin dispatcher

⚠️ **Critical**: this class lives in `core/` and is called from multiple places including `LatinIME.onKey`. Decomposition here needs a focused plan to avoid breaking modifier state consistency — see the ADR log in `.claude/architecture-decisions/`.

---

## 8. ExpandableDictionary.kt — MEDIUM

**File**: `app/src/main/java/dev/devkey/keyboard/ExpandableDictionary.kt`
**Size**: 707 lines
**Worst method**: `getWordsRec` (L218, **cyclo 40, nesting 9, 9 parameters**)

A 9-parameter recursive trie descent is a classic code smell. The rest of the class is reasonable: inner `Node` / `NodeArray` / `NextWord` data classes, separate methods for bigram handling, clean `close`/`loadDictionary` lifecycle.

**Suggestion**: Wrap recursive state in a `TrieWalkState` holder so `getWordsRec` becomes a 2-param function on that holder. Extract bigram-specific traversal (`runReverseLookUp`, `reverseLookUp`, `searchBigramSuggestion`) into a peer `BigramTraversal`.

---

## 9. KeyboardSwitcher.kt — MEDIUM (singleton god)

**File**: `app/src/main/java/dev/devkey/keyboard/KeyboardSwitcher.kt`
**Size**: 528 lines
**Worst methods**: `getKeyboardId` (L300, **cyclo 27, churn 5**), `onKey` (L464, **cyclo 23**)

Private-constructor singleton with 30+ methods managing: keyboard cache keyed by `KeyboardId`, mode switching (alpha/symbols/fn/full), shift state routing, ctrl/alt/meta indicator forwarding, voice mode, settings-key visibility, momentary auto-mode-switch state machine, chording detection, vibration/sound decision.

Not as dire as LatinIME (methods are mostly small), but it has three problems:

1. **Singleton** — `getInstance()` makes testing hard and couples it to `LatinIME.init()` order (known `sKeyboardSettings`-before-init bug from CLAUDE.md).
2. **Mode state machine mixed with cache** — `getKeyboardId` encodes the entire keyboard identity matrix; splitting cache from identity clarifies both.
3. **Auto-mode-switch logic** — the momentary/chording/pointer-count methods are a small state machine that deserves its own class.

**Suggestion**: Break into `KeyboardCache` + `KeyboardModeController` + `AutoModeSwitchStateMachine`. Make `LatinIME` own instances directly (no singleton).

---

## 10. CandidateView.kt — HIGH (400-rule violation)

**File**: `app/src/main/java/dev/devkey/keyboard/CandidateView.kt`
**Size**: 473 lines (over the limit by 73)
**Class**: `CandidateView` (legacy Android View — not Compose)

### Mixed responsibilities (5)

1. **Drawing** — `onDraw`, `drawSuggestions` (L197 — the main 80-line rendering loop)
2. **Gesture handling** — `onLongPress`, `onDown`, `onScroll` (inner `GestureDetector.OnGestureListener`), `onTouchEvent` (L357, 50 lines)
3. **Preview popup** — `showPreview`, `hidePreview`
4. **Add-to-dictionary hint state** — `showAddToDictionaryHint`, `dismissAddToDictionaryHint`, `isShowingAddToDictionaryHint`, `longPressFirstWord`
5. **Scroll animation** — `scrollToTarget`, `computeHorizontalScrollRange`

### Suggested decomposition

- Extract `SuggestionRenderer` (drawing)
- Extract `CandidateGestureHandler` (gesture + touch)
- Extract `SuggestionPreviewPopup` (preview)
- Extract `AddToDictionaryHintController`

**Note:** This class is the legacy Android View implementation. Before decomposing, verify whether `CandidateView` is even still used on the Compose path — if not, delete it entirely (biggest possible win). Confirm via `search_text` for "CandidateView" usage outside the class file.

---

## 11. ComposeSequence.kt — DEFERRED (data-heavy but still violates rule)

**File**: `app/src/main/java/dev/devkey/keyboard/ComposeSequence.kt`
**Size**: 1,144 lines

Most of the 1,144 lines are dead-key / compose-sequence static data. Logic is small: `clear`, `bufferKey`, `executeToString`, `execute`, `get`, `format`, `put`. One concern: `reset` (L257) is cyclo 42 — surprising for a reset.

**The 400-line rule still applies.** Decomposition path:

- Move all static compose-sequence data to a resource file (JSON/XML in `res/raw/` or `assets/`) and load at init time
- Keep `ComposeSequence` as a thin runtime holding only: buffer state, `bufferKey`, `execute*`, `clear`, `reset`, and the loaded sequence map
- Investigate `reset` cyclo 42 — it may be mislabeled data initialization that also belongs in the resource file

Expected post-decomposition: ComposeSequence.kt < 200 lines, pure runtime logic. The data moves out of `.kt` entirely.

---

## Cross-Cutting Observations

### a. Churn concentration
The top 10 hotspots are spread across only 5 files (LatinIME, KeyView, DevKeyKeyboard, Keyboard, Suggest). These 5 files contain the majority of bug-introduction risk. Regression testing, code review focus, and decomposition effort should prioritize them proportionally.

### b. Kotlin-migration scars
Several files look like direct Java→Kotlin ports: inner classes holding data, manual getters (`getKeyboardMode()`, `getShiftState()`), `setFoo/getFoo` pairs where a `var` property would suffice. Worth noting but **not blocking release** — idiomatic cleanup can happen post-release.

### c. `core/` package discipline
`KeyEventSender` lives under `core/` but has 481 lines and cyclo-55 methods. `core/` should contain small focused primitives. Either shrink `KeyEventSender` or move it out of `core/`.

### d. Test coverage gaps
None of the hotspot methods have corresponding unit tests (per the existing test layout). Decomposition is a test-writing opportunity — each extracted class should ship with unit tests.

### e. JNI bridge is clean
`pckeyboard/` (JNI boundary) was not flagged by any hotspot. Confirms the hardcoded class name rule in CLAUDE.md is being respected. ✅

---

## Prioritized Decomposition Roadmap

**Release criterion: all 9 files below 400 lines.** All items are P0 — the 400-line rule is non-negotiable. Ordering is by execution strategy (easy wins first to build refactor confidence and tests).

### Phase A — compliance quick wins (low blast radius)
1. **`KeyEventSender.kt` 481 → < 400** — extract `TerminalSequenceTable` (companion data) + `CharToKeyCodeMapper` (the cyclo-55 blob). Both are pure-data extractions with zero state coupling.
2. **`CandidateView.kt` 473 → < 400** — **first verify if class is still used**; if not, delete. Otherwise extract `SuggestionRenderer` + `CandidateGestureHandler`.
3. **`Suggest.kt` 533 → < 400** — extract `SuggestionSource` strategy per dictionary type, compose in a pipeline. `getSuggestions` (cyclo 52) shrinks naturally.
4. **`KeyboardSwitcher.kt` 528 → < 400** — extract `AutoModeSwitchStateMachine` + `KeyboardCache`. De-singleton is P2 (higher risk), do layout-only split first.

### Phase B — medium complexity (require test coverage first)
5. **`ExpandableDictionary.kt` 707 → < 400** — extract `TrieWalkState` + `BigramTraversal`. Needs unit tests before touching.
6. **`Keyboard.kt` 1,004 → < 400** — extract `KeyboardXmlParser`, `PopupContentBuilder` (data-table conversion for cyclo-43 method), move `Key`/`Row` to own files.
7. **`LatinKeyboard.kt` 956 → < 400** — extract `SpaceBarRenderer`, `LocaleDragController` + `SlidingLocaleDrawable`, `F1KeyDynamicContent`, `KeyProximityResolver`. The `isInside` cyclo-38 goes into `KeyProximityResolver` and gets broken down there.
8. **`ComposeSequence.kt` 1,144 → < 400** — move static data to a resource file, retain runtime only. Probe `reset` cyclo 42 during extraction.

### Phase C — the large one (requires dedicated plan)
9. **`LatinIME.kt` 2,679 → < 400** — 8-collaborator extraction outlined in §1. This is its own `/writing-plans` effort. Start with `SwipeActionHandler` + `FeedbackManager` as risk-free warm-ups, then `SuggestionCoordinator`, `InputDispatcher`, `ShiftStateMachine`, `PunctuationHeuristics`, `DictionaryWriter`, `PreferenceObserver`, `ImeLifecycleController`.

### Phase D — complexity hotspots that are under 400 but still release-risk
These files are 400-rule compliant but have individual functions with release-blocking cyclomatic/nesting scores:

- **`KeyView.kt` (346 lines)** — composable at L49 has cyclo 78, nesting 10 → split into `KeyBackground` / `KeyGlyph` / `KeyPopupPreview` / `KeyPressDetector`. Complexity target: cyclo < 15, nesting < 4 per composable.
- **`DevKeyKeyboard.kt` (390 lines)** — composable at L63 has cyclo 42, nesting 8, churn 9 (#1 churn) → split into `KeyboardGrid` / `ComposeSequenceHost` / `KeyboardScaffold`.

---

## Release-Readiness Implications (feeds into /brainstorming)

The user's three explicit pre-release concerns:

1. **Voice feature (Whisper / TF Lite) end-to-end** — `shouldShowVoiceButton` is in LatinIME; `VoiceInputSettingsScreen` exists but Whisper model files are still missing from `app/src/main/assets/` (carried blocker from `_state.md`). **Cannot verify voice works until assets are present.**
2. **SwiftKey layout fidelity** — the layout pipeline is `DevKeyKeyboard` + `KeyView` + `Keyboard.xml` resources. Both Compose functions are in the top 3 hotspots. **Layout fidelity audits will be noisy until these are split** because small changes trigger recomposition across the grid.
3. **All keyboard functions work (regression coverage)** — 10 of the top 25 hotspots live in LatinIME. Automated regression coverage on the key-dispatch path (`onKey` → `handleCharacter` → `handleSeparator` → `handleBackspace`) is the single highest-leverage test investment before release.

Follow-up questions for `/brainstorming` (beyond the user's 3 explicit topics):

- Decompose-then-release vs release-then-decompose? The P0 decomposition list is non-trivial; the user may want a minimal "stop the bleeding" pass (just the two Compose functions) and ship.
- Scope of SwiftKey fidelity: pixel-perfect clone, or "feels like SwiftKey"? Pixel-perfect blocks on layout XML + `KeyView` color/shape tokens.
- Voice feature gating: is voice blocking release, or a post-release feature-flag?
- Regression suite target: existing `/test` tier alone, or do we need a golden-path recording test covering the 10 LatinIME hotspots?
- Minimum API surface that must work on the first public release (SwiftKey parity? Hacker's Keyboard parity + one delta?).

---

## Evidence source

- **Index**: `local/Hackers_Keyboard_Fork-e04f25e5` (rebuilt 2026-04-08 with patched Kotlin extractor)
- **Hotspot data**: `get_hotspots(top_n=25, days=180)` — methodology `complexity_x_churn`
- **File outlines**: `get_file_outline` for LatinIME.kt; Grep-based signature scan for the other 9 files
- **Call graph**: `get_call_hierarchy(handleCharacter, callers)` returned 3 same-package callers (confirmed Kotlin extractor patches are live)
- **Line counts**: retained from Session 38 size scan in `.claude/autoload/_state.md`

All cyclomatic / nesting / churn numbers are jcodemunch index v8 metrics. Any symbol referenced by name in this report has been verified to exist in the current index.
