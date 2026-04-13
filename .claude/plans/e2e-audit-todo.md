# DevKey End-to-End Audit — Canonical TODO Spec

**Created**: 2026-04-12 | **Status**: Active
**Source**: Session 56 comprehensive audit (CodeMunch index, 3 parallel agents, manual verification)

---

## Legend

- `[ ]` = Pending
- `[x]` = Completed and verified
- `[~]` = In progress
- `[!]` = Blocked

---

## 1. CRITICAL: Unblock Test Suite

### 1.1 Fix KeyEventSenderTest Compile Error
- [x] Remove stale `editorInfoProvider` parameter from `createSender()` in `KeyEventSenderTest.kt:145`
- [x] Remove unused `EditorInfo` import and `lastEditorInfo` field
- [ ] Verify all 35 KeyEventSender tests pass
- [ ] Run full `./gradlew test` and confirm compile succeeds

### 1.2 Fix Layout Test Failures (57 tests)
- [ ] **QwertyLayoutFullTest** (~45 failures): Update row indices (utility moved from row 5 to row 0)
  - [ ] Number row: `rows[0]` → `rows[1]`
  - [ ] QWERTY row: `rows[1]` → `rows[2]`
  - [ ] Home row: `rows[2]` → `rows[3]`
  - [ ] Z row: `rows[3]` → `rows[4]`
  - [ ] Space row: `rows[4]` → `rows[5]`
  - [ ] Utility row: `rows[5]` → `rows[0]`
  - [ ] Update Shift label assertion: `"Shift"` → `"\u21E7"` (⇧)
  - [ ] Update Enter label assertion: `"Enter"` → `"\u21B5"` (↵)
  - [ ] Update spacebar weight assertion: `4.0f` → `3.2f`
  - [ ] Update QWERTY row long-press: vowels now have accented chars (e→è, u→ù, i→ì, o→ò, y→ý)
  - [ ] Update home row A long-press: `"~"` → `"à"`
  - [ ] Update Z row long-press: c→ç, n→ñ (vowel accents replaced symbols)
- [ ] **QwertyLayoutCompactTest** (~12 failures): Compact now 5 rows with number row
  - [ ] Update `compact mode produces 4 rows` → expect 5 (or test without number row)
  - [ ] Update `compact mode first row is QWERTY not numbers` — row 0 is now number row
  - [ ] Update `compact mode letter keys have NO long-press` — keys DO have long-press now
  - [ ] Update compact dev tests for new long-press assignments
- [ ] **SymbolsLayoutTest** (4 failures): Update long-press mappings
  - [ ] `$` long-press: `"€"` → `"¢"` (U+00A2)
  - [ ] `(` long-press: `"<"` → `"["` 
  - [ ] `)` long-press: `">"` → `"]"`
  - [ ] `"` long-press: smart quotes → `"«"` (U+00AB)
- [ ] **KeyBoundsCalculatorTest** (4 failures): Update row structure expectations
- [ ] Run `./gradlew test` — target: 381 tests, 0 failures

---

## 2. HIGH: Architecture Issues

### 2.1 Settings Bypass — Direct SharedPreferences Reads
- [ ] `PreferenceObserver.kt` lines 54-102: reads 8+ preferences directly (worst offender)
- [ ] `InputLanguageSelection.kt` lines 44, 152: `PreferenceManager.getDefaultSharedPreferences`
- [ ] `DictionaryManager.kt` line 53: direct prefs read
- [ ] `ImeInitializer.kt` line 37: direct prefs read
- [ ] `ImeLifecycleDelegate.kt` line 65: direct prefs read

### 2.2 runBlocking on IME Thread
- [ ] `CommandModeDetector.kt` line 99: `runBlocking { detect(packageName) }` — replace with suspend path

### 2.3 Complexity Hotspot Extraction
- [ ] `KeyView.kt:43` — CC 38, hotspot 105.4 — extract sub-composables
- [ ] `InputHandlers.handleSeparator:134` — CC 42, hotspot 96.7 — extract autocorrect/commit paths
- [ ] `SuggestionPipeline.getSuggestions:55` — CC 53 — extract filter/ranking phases
- [ ] `InputViewSetup.onStartInputView:38` — CC 31 — extract config phases
- [ ] `SuggestionCoordinator.onSelectionChanged:187` — CC 29 — extract re-correction logic

### 2.4 ModifiableKeyDispatcher 14-Param Constructor
- [ ] Extract a narrow protocol interface to replace 14 individual lambdas

### 2.5 JNI BinaryDictionary close/use Race
- [ ] Add `@Volatile` or synchronized guard around `mNativeDict = 0` write in `close()`

### 2.6 Thread Safety for Modifier State
- [ ] Audit `ModifierStateManager` and `ChordeTracker` for cross-thread access
- [ ] Add `@Volatile` or `AtomicReference` where needed

---

## 3. MEDIUM: Missing Unit Tests (P0 — Zero Coverage, High Risk)

### 3.1 InputHandlers Tests
- [ ] Test `handleSeparator` with composing word (auto-correct path)
- [ ] Test `handleSeparator` with non-composing space → `setNextSuggestions` fires
- [ ] Test `handleSeparator` with space when `Suggest` is null (the known blocker)
- [ ] Test `handleCharacter` basic path
- [ ] Test `commitTyped` with learning engine

### 3.2 SuggestionCoordinator Tests
- [ ] Test `setNextSuggestions` with bigram hit → suggestions populated
- [ ] Test `setNextSuggestions` with bigram miss → punctuation fallback
- [ ] Test `setNextSuggestions` with null `Suggest` → graceful fallback
- [ ] Test `onSelectionChanged` cursor movement clears composition

### 3.3 WhisperProcessor Tests
- [ ] Test `processAudio` with empty input → returns null
- [ ] Test `processAudio` with short audio → pads to 480,000 samples
- [ ] Test `processAudio` with long audio → trims to 480,000 samples
- [ ] Test `computeMelSpectrogram` output shape: 80 × 3000 = 240,000 floats
- [ ] Test mel spectrogram normalization bounds: output in [-1, 1] range
- [ ] Test `fft` with known sinusoidal input → verify peak at expected bin
- [ ] Test `loadResources` with malformed header → returns false gracefully
- [ ] Test `decodeTokens` with valid/empty/null vocabulary

### 3.4 Dictionary Subsystem Tests
- [ ] Test `BinaryDictionary` null-handle guard (mNativeDict == 0)
- [ ] Test dictionary `close()` idempotency
- [ ] Test `ExpandableDictionary` bigram lookup
- [ ] Test `UserBigramDictionary` add/retrieve cycle

### 3.5 KeyboardActionBridge Integration Test
- [ ] Test Compose key press → bridge → `onKey()` callback fires
- [ ] Test modifier key filtering (compose-local keys NOT forwarded)

### 3.6 PunctuationHeuristics Tests
- [ ] Test `swapPunctuationAndSpace` (". " → " ." swap)
- [ ] Test `doubleSpace` → period insertion
- [ ] Test `reswapPeriodAndSpace`

---

## 4. MEDIUM: Missing Unit Tests (P1 — Zero Coverage, Medium Risk)

### 4.1 Clipboard
- [ ] Test `ClipboardRepository` persistence round-trip

### 4.2 SettingsRepository Dedicated Tests
- [ ] Test default values for all keys
- [ ] Test preference observer notifications
- [ ] Test migration paths

### 4.3 ComposeSequence / DeadAccentSequence
- [ ] Test dead key → accent composition (e.g., ` + e → è)

### 4.4 ModifierChordingController
- [ ] Test chord detection: Ctrl held + letter → chord fires
- [ ] Test chord with delay mode vs immediate mode

### 4.5 LanguageSwitcher
- [ ] Test language toggle ordering
- [ ] Test locale fallback

### 4.6 KeyboardSwitcher
- [ ] Test mode transitions (Normal → Symbols → Normal)
- [ ] Test layout mode changes (Full → Compact → CompactDev)

---

## 5. MEDIUM: Next-Word Prediction Pipeline Fix

### 5.1 Diagnose Non-Composing Space Gap
- [ ] Trace `handleSeparator` when `mPredicting=false`, `primaryCode=SPACE`, `Suggest` is null
- [ ] Verify `isPredictionOn` return value in this scenario
- [ ] Fix: ensure `setNextSuggestions` fires even when `Suggest` loads late

### 5.2 Compose↔Legacy Suggestion Sync
- [ ] Verify `SessionDependencies.nextWordSuggestions` flow reaches `rememberPredictions()`
- [ ] Verify `SuggestionBar` renders next-word suggestions from legacy pipeline
- [ ] Add E2E test: type word → space → verify suggestion bar shows bigram candidates

---

## 6. LOW: Additional Gaps

### 6.1 E2E Missing Scenarios
- [ ] Dictionary plugin hot-reload
- [ ] Settings backup/restore round-trip
- [ ] IME crash recovery
- [ ] Language switching mid-sentence
- [ ] Compose-key sequences (dead keys)
- [ ] Ctrl+A/C/V integration with external apps

### 6.2 Error Handling Tests
- [ ] Corrupt dictionary binary (JNI crash path)
- [ ] `filters_vocab_en.bin` with malformed header
- [ ] OOM during mel spectrogram allocation
- [ ] InputConnection returning null mid-batch-edit
- [ ] Database migration failures
- [ ] Macro serialization with invalid JSON

### 6.3 Dead Code Cleanup
- [ ] Remove `compare_pair_capital` and `latin_tolower` from `char_utils.cpp` (unreferenced)
- [ ] `GlobalScope` in `DebugReceiverManager.kt` lines 117, 151 — replace with scoped coroutines

### 6.4 XML Parser Extraction
- [ ] `KeyboardXmlParser.loadKeyboard` CC 23, nesting 9 — extract to state machine

---

## Verification Checklist

After all fixes:
- [ ] `./gradlew assembleDebug` — green
- [ ] `./gradlew test` — all tests pass (target: 381+ tests, 0 failures)
- [ ] `./gradlew lint` — green (257 baselined warnings only)
- [ ] `./gradlew detekt` — zero findings
- [ ] Emulator verification: keyboard renders, typing works, all modes functional
- [ ] E2E harness: `python tools/e2e/e2e_runner.py` — all tests pass

---

## Repo Health Snapshot (2026-04-12)

| Metric | Value |
|--------|-------|
| Total symbols | 3,326 |
| Functions/methods | 1,709 |
| Avg complexity | 3.13 |
| Dependency cycles | 0 |
| Layer violations | 0 |
| Unstable modules | 2 |
| Unit tests | 381 (57 failing) |
| E2E test files | 20 |
| Top hotspot | KeyView.kt (105.4) |
