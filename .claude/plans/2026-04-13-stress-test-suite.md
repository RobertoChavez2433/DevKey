# Plan: Feature Stress Test Suite

**Spec**: `.claude/specs/2026-04-13-stress-test-spec.md`
**Tailor**: `.claude/tailor/2026-04-13-stress-test-suite/`
**Date**: 2026-04-13
**Status**: DRAFT

---

## Decisions

| # | Question | Decision |
|---|----------|----------|
| 1 | Private method testing | `editDistance` -> `internal @VisibleForTesting`. Other 4 (computeMelSpectrogram, runInference, readWavPcm, nextShiftState) -> test via public API. |
| 2 | E2E subdirectory migration | New subdirectories first with new tests. Migrate existing flat files into `test_smoke.py` per feature. Runner supports both layouts. |
| 3 | Integration test location | Robolectric in `app/src/test/.../integration/`. No `androidTest` source set. |

---

## Phase Ranges

```
Phase  1:  steps   1–8    Infrastructure
Phase  2:  steps   9–16   Prediction
Phase  3:  steps  17–23   Input Handling
Phase  4:  steps  24–29   Punctuation Heuristics
Phase  5:  steps  30–38   Modifiers
Phase  6:  steps  39–44   Autocorrect
Phase  7:  steps  45–51   Voice
Phase  8:  steps  52–56   Layout Modes
Phase  9:  steps  57–61   Clipboard
Phase 10:  steps  62–66   Macros
Phase 11:  steps  67–72   Command Mode
```

---

## Phase 1: Infrastructure (steps 1–8)

### Step 1 — Update E2E runner discovery

**File**: `tools/e2e/e2e_runner.py`

Replace `os.listdir("tests/")` flat discovery with `pathlib.glob` supporting
both flat files and one-level subdirectories. Discovery order:
`tests/test_*.py` (flat), then `tests/*/test_*.py` (subdirectory).

Module import path changes from `tests.{module_name}` to
`tests.{subdir}.{module_name}` for subdirectory tests.

### Step 2 — Add `--feature` flag to runner

**File**: `tools/e2e/e2e_runner.py`

Add `--feature` argparse argument. When set, discovery restricts to
`tests/<feature>/test_*.py`. Mutually exclusive with existing `--test` filter
for module-level selection.

### Step 3 — Verify existing 20 E2E tests pass

Run `PORT=3950 python e2e_runner.py` (no flags) against the `Pixel_7_API_36`
emulator. All existing test functions across the 20 flat files must still be
discovered and pass (spec states 54 test functions). Record the before-count
as a baseline gate before proceeding.

### Step 4 — Create `TestImeState.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/testutil/TestImeState.kt`

Factory that returns an `ImeState` with sensible defaults:
- `mPredicting = false`, `mAutoCorrectOn = false`
- `mWord = WordComposer()` (empty)
- All modifier flags = false, chording delays = 0
- All fields overridable via named parameters

### Step 5 — Create `MockInputConnection.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/testutil/MockInputConnection.kt`

Implements `InputConnection`. Tracks:
- `commitTextCount` and `commitTextLengths` (not content — privacy)
- Configurable `getTextBeforeCursor` / `getTextAfterCursor` return values
- `sendKeyEventCalls: List<KeyEvent>`
- `beginBatchEdit` / `endBatchEdit` nesting counter

### Step 6 — Create `TestSessionDependencies.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/testutil/TestSessionDependencies.kt`

Single `reset()` function that nulls out all `SessionDependencies` fields:
`suggest`, `commandModeDetector`, `currentPackageName`, `dictionaryProvider`,
`autocorrectEngine`, `learningEngine`, `predictionEngine`,
`voiceInputEngine`, `resetPredictionState`, `triggerNextSuggestions`, and any
composing word state. Called in `@Before` of tests that touch
`SessionDependencies`.

### Step 7 — Create E2E subdirectory scaffolding

Create `__init__.py` in each feature subdirectory:
- `tools/e2e/tests/prediction/__init__.py`
- `tools/e2e/tests/autocorrect/__init__.py`
- `tools/e2e/tests/voice/__init__.py`
- `tools/e2e/tests/modifiers/__init__.py`
- `tools/e2e/tests/clipboard/__init__.py`
- `tools/e2e/tests/macros/__init__.py`
- `tools/e2e/tests/punctuation/__init__.py`
- `tools/e2e/tests/input/__init__.py`
- `tools/e2e/tests/modes/__init__.py`
- `tools/e2e/tests/command_mode/__init__.py`

### Step 8 — Verify build

Run `./gradlew assembleDebug` to confirm test utilities compile.
Run `./gradlew test` to confirm no existing test regressions.

---

## Phase 2: Prediction (steps 9–16)

### Step 9 — Promote `editDistance` visibility

**File**: `app/src/main/java/dev/devkey/keyboard/feature/prediction/TrieDictionary.kt` line ~161

Change `private fun editDistance(a: String, b: String): Int` to
`@VisibleForTesting internal fun editDistance(a: String, b: String): Int`.

### Step 10 — Create `TrieDictionaryTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/feature/prediction/TrieDictionaryTest.kt`

Tests (10 total):
- `editDistance` — identical = 0, single edit = 1, empty strings
- `getSuggestions` — cached prefix, DFS fallback, filters exact match
- `getFuzzyMatches` — respects maxDistance, early-exit at maxResults*2
- `isValidWord` — case-insensitive
- `load` — parses TSV format

Use Robolectric for `Context` in `load()`.

### Step 11 — Create `SuggestionCoordinatorTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/SuggestionCoordinatorTest.kt`

Depends on: `TestImeState`, `MockInputConnection`, `TestSessionDependencies`

Tests (14 total):
- `setNextSuggestions` — bigram hit, bigram miss, no prev word, disabled
- `getLastCommittedWordBeforeCursor` — word found, no word, apostrophe, null IC
- `updateSuggestions` — mPredicting true vs false paths
- `showSuggestions(WordComposer)` — FULL, FULL_BIGRAM correction modes
- `showSuggestions` — isMostlyCaps suppression
- `onSelectionChanged` — composing out of sync, re-correction trigger

Mock `Suggest` (JNI boundary) and `SuggestionPicker`.
Assert suggestion counts and frequencies, never suggestion text content.

### Step 12 — Create `PredictionPipelineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/integration/PredictionPipelineTest.kt`

Robolectric integration test wiring real `SuggestionCoordinator` +
`PredictionEngine` + `TrieDictionary`. Mock only `InputConnection` and `Suggest`.

Tests (3):
- Compose word -> space -> verify nextWordSuggestions populated (count > 0)
- Tap suggestion -> verify triggerNextSuggestions fires
- Cursor reposition -> verify onSelectionChanged clears composing

### Step 13 — Migrate prediction E2E smoke tests

**File**: `tools/e2e/tests/prediction/test_smoke.py`

Copy the 3 existing tests from `tools/e2e/tests/test_next_word.py` into
subdirectory format. Verify they pass via `python e2e_runner.py --feature prediction`.

### Step 14 — Create prediction E2E stress tests

**File**: `tools/e2e/tests/prediction/test_stress.py`

Tests (4):
- Type 20 words rapidly, verify suggestions after each space (count check)
- Suggestion tap chain: tap 5x, verify each triggers next-word
- 3-sentence paragraph with predictions throughout
- Bigram exhaustion: rare word -> graceful fallback (no crash)

Use `wait_for()` helpers, no blind waits. Assert event counts only.

### Step 15 — Create prediction E2E edge tests

**File**: `tools/e2e/tests/prediction/test_edge.py`

Tests (5):
- Empty field + space (no prior word)
- Unicode/accented character input
- Cursor reposition mid-sentence -> predictions update
- Very long word (50 chars) -> space -> no crash
- Rapid space-backspace-space cycle

### Step 16 — Verify prediction phase

Run `./gradlew test --tests "*TrieDictionaryTest*"` and
`./gradlew test --tests "*SuggestionCoordinatorTest*"` and
`./gradlew test --tests "*PredictionPipelineTest*"`.
Run `PORT=3950 python e2e_runner.py --feature prediction`.

---

## Phase 3: Input Handling (steps 17–23)

### Step 17 — Create `InputHandlersTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/InputHandlersTest.kt`

Depends on: `TestImeState`, `MockInputConnection`

Constructor needs 7 mocks: `state`, `icProvider`, `suggestionCoordinator`,
`suggestionPicker`, `modifierHandler`, `keyEventSender`, `puncHeuristics`.
Use a test helper that supplies defaults for all 7.

Tests (11):
- `handleCharacter` — first char starts prediction, auto-capitalize, modifier bypass
- `handleBackspace` — composing trim, composing empty delete, UNDO_COMMIT revert,
  accelerated delete, mEnteredText match
- `commitTyped` — commits composing, not-predicting no-op
- `revertLastWord` — restores composing

### Step 18 — Create `InputDispatcherTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/InputDispatcherTest.kt`

Constructor needs 12+ params — create a `TestInputDispatcherFactory` helper
that provides no-op lambdas and mock objects as defaults.

Tests (6):
- `onKey` — DELETE routes to handleBackspace
- `onKey` — ASCII letter routes to handleDefault
- `onKey` — ESCAPE sends escape, TAB sends tab
- `onKey` — delete acceleration: rapid deletes increase count
- `onText` — single-char separator commits typed first

### Step 19 — Create input E2E smoke tests

**File**: `tools/e2e/tests/input/test_smoke.py`

Migrate 4 existing tests from `tools/e2e/tests/test_core_input.py`.

### Step 20 — Create input E2E stress tests

**File**: `tools/e2e/tests/input/test_stress.py`

Tests (5):
- 50-word paragraph typed at speed
- Rapid backspace: type 20 chars, delete all, verify empty (field length = 0)
- Backspace during composing vs after commit
- Enter mid-composing -> verify word committed
- Mixed separators and letters rapidly

### Step 21 — Create input E2E edge tests

**File**: `tools/e2e/tests/input/test_edge.py`

Tests (4):
- Backspace in empty field
- Very long composing word (100 chars)
- Non-ASCII input (accented, CJK, RTL)
- Backspace after suggestion tap (revert path)

### Step 22 — Create input E2E state exhaustion test

**File**: `tools/e2e/tests/input/test_state_exhaustion.py`

TextEntryState has 12 states: UNKNOWN, START, IN_WORD, ACCEPTED_DEFAULT,
PICKED_SUGGESTION, PUNCTUATION_AFTER_WORD, PUNCTUATION_AFTER_ACCEPTED,
SPACE_AFTER_ACCEPTED, SPACE_AFTER_PICKED, UNDO_COMMIT, CORRECTING,
PICKED_CORRECTION.

Drive the full state cycle via keystroke sequences and verify each transition
via DevKeyLogger structural events.

### Step 23 — Verify input phase

Run `./gradlew test --tests "*InputHandlersTest*"` and
`./gradlew test --tests "*InputDispatcherTest*"`.
Run `PORT=3950 python e2e_runner.py --feature input`.

---

## Phase 4: Punctuation Heuristics (steps 24–29)

### Step 24 — Create `PunctuationHeuristicsTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/PunctuationHeuristicsTest.kt`

Depends on: `MockInputConnection`, `SettingsRepository` mock

Tests (10):
- `swapPunctuationAndSpace` — "a ." -> "a. " pattern, non-separator no swap
- `reswapPeriodAndSpace` — ". ." -> " .." pattern, different pattern no reswap
- `doubleSpace` — "a  " -> "a. ", start of field no-op, after non-alphanumeric no-op
- `maybeRemovePreviousPeriod` — ".." removes trailing
- `removeTrailingSpace` — removes if space, no-ops otherwise
- `initSuggestPuncList` — default vs custom settings

Assert via IC call counts and `getTextBeforeCursor`/`getTextAfterCursor`
changes, not content.

### Step 25 — Create `PunctuationPipelineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/integration/PunctuationPipelineTest.kt`

Wire real `PunctuationHeuristics` + `InputHandlers`. Mock IC only.

Tests (2):
- handleSeparator -> doubleSpace -> verify IC transformation (length check)
- handleSeparator -> swapPunctuationAndSpace chain

### Step 26 — Migrate punctuation E2E smoke tests

**File**: `tools/e2e/tests/punctuation/test_smoke.py`

Migrate 5 existing tests from `tools/e2e/tests/test_auto_punctuation.py`.

### Step 27 — Create punctuation E2E stress tests

**File**: `tools/e2e/tests/punctuation/test_stress.py`

Tests (4):
- 10 double-space-to-period conversions in sequence
- Alternating separators rapid: period, comma, exclamation
- Double-space after autocorrected word
- Period-reswap after "..." pattern

### Step 28 — Create punctuation E2E edge tests

**File**: `tools/e2e/tests/punctuation/test_edge.py`

Tests (4):
- Double-space at very start of empty field
- Space after emoji
- Punctuation after number
- Three rapid spaces (". " + space, not ".. ")

### Step 29 — Verify punctuation phase

Run `./gradlew test --tests "*PunctuationHeuristicsTest*"` and
`./gradlew test --tests "*PunctuationPipelineTest*"`.
Run `PORT=3950 python e2e_runner.py --feature punctuation`.

---

## Phase 5: Modifiers (steps 30–38)

### Step 30 — Create `ModifierChordingControllerTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/ModifierChordingControllerTest.kt`

Depends on: `MockInputConnection`, `TestImeState`

Tests (8):
- Ctrl chording delay=0 returns without sending
- Ctrl chording delay=keycode sends key
- Alt chording delay=0
- Meta chording delay=0
- `sendModifierKeysDown` — Ctrl+Shift sends both DOWN
- `sendModifierKeysDown` — no modifiers sends nothing
- `handleModifierKeysUp` — resets Ctrl state
- `handleModifierKeysUp` — shift-locked does not reset

### Step 31 — Create `ModifiableKeyDispatcherTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/ModifiableKeyDispatcherTest.kt`

Depends on: `TestImeState`, `MockInputConnection`

Tests (8):
- Plain letter + shift -> commit text (no key events)
- Ctrl+letter -> sends modified key down/up
- Ctrl+A override=0, no Alt -> shows toast
- Ctrl+A override=0, Alt active -> sends Ctrl+A
- Ctrl+A override=1 -> silently ignores
- Ctrl+A override=2 -> sends standard Ctrl+A
- Digit with modifier -> clears meta then sends char
- Non-ASCII (>127) -> falls through to sendKeyChar

### Step 32 — Create `ModifierHandlerTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/ModifierHandlerTest.kt`

Tests `nextShiftState` indirectly via `onPress`/`onRelease` state cycles
(private method — tested through public API per Decision #1).

Tests (8):
- `nextShiftState` via press/release — OFF->ON->LOCKED->OFF (caps on)
- `nextShiftState` via press/release — OFF->ON->OFF (caps off)
- `onPress(SHIFT)` — starts multitouch shift
- `onRelease(SHIFT)` — commits multitouch shift
- `onPress(CTRL)` — toggles mModCtrl
- `onRelease(CTRL)` — chording reset, sends key-up
- `updateShiftKeyState` — caps mode ON from EditorInfo
- `updateShiftKeyState` — chording active skips auto-cap

### Step 33 — Create `ModifierPipelineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/integration/ModifierPipelineTest.kt`

Wire real `ModifierHandler` + `ModifierChordingController` + `KeyEventSender`.
Mock IC.

Tests (3):
- Full press/release Ctrl cycle -> verify KeyEvents on IC
- Chording: Ctrl down, letter, Ctrl up -> verify modified key event
- Shift one-shot: tap shift, type letter -> uppercase, next lowercase

### Step 34 — Migrate modifier E2E smoke tests

**File**: `tools/e2e/tests/modifiers/test_smoke.py`

Migrate existing tests from `test_modifiers.py`, `test_modifier_combos.py`,
`test_modifier_extended.py`.

### Step 35 — Create modifier E2E stress tests

**File**: `tools/e2e/tests/modifiers/test_stress.py`

Tests (4):
- Ctrl+A through Ctrl+Z full alphabet
- Rapid shift cycling: tap 10x, verify state cycle
- Three-modifier combo: Ctrl+Alt+Shift+key
- One-shot consumption: Ctrl tap -> letter -> verify released

### Step 36 — Create modifier E2E edge tests

**File**: `tools/e2e/tests/modifiers/test_edge.py`

Tests (4):
- Modifier press without release (orphan state)
- Mode switch while modifier held
- Shift lock -> type 20 chars -> verify all uppercase (length check)
- Ctrl+A with all 3 override settings

### Step 37 — Create modifier state exhaustion test

**File**: `tools/e2e/tests/modifiers/test_state_exhaustion.py`

Enumerate all modifier state transitions — all combinations of shift, ctrl,
alt, meta states and the transitions between them.

### Step 38 — Verify modifier phase

Run `./gradlew test --tests "*ModifierChordingControllerTest*"` and
`./gradlew test --tests "*ModifiableKeyDispatcherTest*"` and
`./gradlew test --tests "*ModifierHandlerTest*"` and
`./gradlew test --tests "*ModifierPipelineTest*"`.
Run `PORT=3950 python e2e_runner.py --feature modifiers`.

---

## Phase 6: Autocorrect (steps 39–44)

### Step 39 — Add separator-path tests to `InputHandlersTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/core/InputHandlersTest.kt`
(extends file from Step 17)

Tests (6):
- `handleSeparator` — mAutoCorrectOn picks default suggestion
- `handleSeparator` — AutocorrectEngine aggressive auto-apply path
- `handleSeparator` — AutocorrectEngine suggestion (non-auto-apply) path
- `handleSeparator` — AutocorrectEngine OFF path
- `handleSeparator` — revert separator guard skips autocorrect
- `commitTyped` — manual vs accepted typed TextEntryState paths

### Step 40 — Create `AutocorrectPipelineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/integration/AutocorrectPipelineTest.kt`

Wire real `InputHandlers` + `AutocorrectEngine` + `SuggestionCoordinator`.
Mock IC.

Tests (3):
- Type misspelled word + space -> verify correction applied (IC call count)
- pendingCorrection flow for non-auto-apply suggestions
- Autocorrect then space -> verify next-word suggestions fire (count > 0)

### Step 41 — Migrate autocorrect E2E smoke tests

**File**: `tools/e2e/tests/autocorrect/test_smoke.py`

Migrate 3 existing tests from `tools/e2e/tests/test_autocorrect.py`.

### Step 42 — Create autocorrect E2E stress tests

**File**: `tools/e2e/tests/autocorrect/test_stress.py`

Tests (4):
- Aggressive mode: 10 misspelled words in sequence
- Type corrected word -> backspace -> verify revert
- Mixed correct/incorrect rapid paragraph
- Autocorrect then suggestion tap chain

### Step 43 — Create autocorrect E2E edge tests

**File**: `tools/e2e/tests/autocorrect/test_edge.py`

Tests (4):
- Single-char words (no correction expected)
- Numbers mixed with letters
- Autocorrect OFF -> verify no interference
- Word at correction boundary (edit distance = max)

### Step 44 — Verify autocorrect phase

Run `./gradlew test --tests "*InputHandlersTest*"` and
`./gradlew test --tests "*AutocorrectPipelineTest*"`.
Run `PORT=3950 python e2e_runner.py --feature autocorrect`.

---

## Phase 7: Voice (steps 45–51)

### Step 45 — Create `WhisperProcessorTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/feature/voice/WhisperProcessorTest.kt`

Tests `computeMelSpectrogram` indirectly via `processAudio` output shape
(private method — tested through public API per Decision #1).

Tests (13):
- `loadResources` — valid 16-byte header parsed correctly
- `loadResources` — header too short (< 16 bytes) returns false
- `loadResources` — bad magic number returns false
- `computeMelSpectrogram` via `processAudio` — verify output array shape
  (Hann window, FFT bins, mel filter, log10 clamp, Whisper normalization
  all tested by checking output dimensions and value ranges)
- `processAudio` — pads short audio to 480,000 samples
- `processAudio` — trims long audio to 480,000 samples
- `processAudio` — empty audio returns null or empty
- `decodeTokens` — maps valid token IDs to strings
- `decodeTokens` — filters out-of-range IDs

Use Robolectric for Context in `loadResources`.

### Step 46 — Create `VoiceInputEngineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/feature/voice/VoiceInputEngineTest.kt`

Tests `runInference` and `readWavPcm` indirectly via public API
(private methods — tested through `stopListening` and `processFileForTest`).

Tests (8):
- IDLE -> startListening -> LISTENING
- LISTENING -> stopListening -> PROCESSING -> IDLE
- LISTENING -> cancelListening -> IDLE
- startListening with no permission -> ERROR
- startListening when already LISTENING -> no-op
- `readWavPcm` via file-based test -> file < 44 bytes returns null equivalent
- `readWavPcm` via file-based test -> valid WAV extracts correct PCM (length check)
- `runInference` via stopListening -> empty audio returns "[No speech detected]"

Mock `AudioRecord` (framework boundary).

### Step 47 — Create `VoicePipelineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/integration/VoicePipelineTest.kt`

Wire real `VoiceInputEngine` + `WhisperProcessor`. Mock AudioRecord.

Tests (3):
- Full startListening -> stopListening -> transcription with fake capture
- Cancel mid-processing -> verify state = IDLE
- processFileForTest with known WAV -> verify non-empty result (length > 0)

### Step 48 — Migrate voice E2E smoke tests

**File**: `tools/e2e/tests/voice/test_smoke.py`

Migrate 4 existing tests from `tools/e2e/tests/test_voice.py`.

### Step 49 — Create voice E2E stress tests

**File**: `tools/e2e/tests/voice/test_stress.py`

Tests (5):
- Start/stop 5x rapidly
- Cancel during PROCESSING state
- File-based inference: short audio (< 1s)
- File-based inference: 30s boundary audio
- Back-to-back inference: process, commit, process

### Step 50 — Create voice E2E edge tests

**File**: `tools/e2e/tests/voice/test_edge.py`

Tests (3):
- Cancel from IDLE (should no-op)
- Voice button during composing -> commit first
- Empty audio buffer -> "[No speech detected]"

### Step 51 — Verify voice phase

Run `./gradlew test --tests "*WhisperProcessorTest*"` and
`./gradlew test --tests "*VoiceInputEngineTest*"` and
`./gradlew test --tests "*VoicePipelineTest*"`.
Run `PORT=3950 python e2e_runner.py --feature voice`.

---

## Phase 8: Layout Modes (steps 52–56)

### Step 52 — Create `AutoModeSwitchStateMachineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/keyboard/switcher/AutoModeSwitchStateMachineTest.kt`

Self-contained — only needs `changeKeyboardMode` and `getPointerCount` callbacks.

Tests (7):
- ALPHA -> toggleSymbols -> SYMBOL_BEGIN
- SYMBOL_BEGIN -> onKey -> SYMBOL
- SYMBOL -> toggleSymbols -> ALPHA
- ALPHA -> setMomentary -> MOMENTARY
- MOMENTARY -> pointer count > 1 -> CHORDING
- MOMENTARY -> onCancelInput -> ALPHA
- Full 5-state transition matrix (all valid transitions)

### Step 53 — Migrate modes E2E smoke tests

**File**: `tools/e2e/tests/modes/test_smoke.py`

Migrate 4 existing tests from `tools/e2e/tests/test_modes.py`.

### Step 54 — Create modes E2E stress tests

**File**: `tools/e2e/tests/modes/test_stress.py`

Tests (4):
- Rapid Normal->Symbols->Normal 10x
- Mode switch mid-composing word -> verify commit
- Compact -> full -> compact roundtrip
- Symbols -> type number -> toggle back -> clean state

### Step 55 — Create modes E2E edge tests

**File**: `tools/e2e/tests/modes/test_edge.py`

Tests (2):
- Switch to symbols during modifier hold
- Mode switch with suggestion bar visible

### Step 56 — Verify modes phase

Run `./gradlew test --tests "*AutoModeSwitchStateMachineTest*"`.
Run `PORT=3950 python e2e_runner.py --feature modes`.

---

## Phase 9: Clipboard (steps 57–61)

### Step 57 — Create `ClipboardRepositoryTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/feature/clipboard/ClipboardRepositoryTest.kt`

Needs a fake or in-memory DAO for `ClipboardHistoryDao`.

Tests (3):
- `addEntry`, `pin`, `unpin`, `delete`, `clearAll` — CRUD cycle
- `search` — filters correctly (result count check, not content)
- `clearAll` — preserves pinned entries

### Step 58 — Migrate clipboard E2E smoke tests

**File**: `tools/e2e/tests/clipboard/test_smoke.py`

Migrate existing tests from `tools/e2e/tests/test_clipboard.py`.

### Step 59 — Create clipboard E2E stress tests

**File**: `tools/e2e/tests/clipboard/test_stress.py`

Tests (4):
- Open/close panel 10x rapidly
- Paste same entry 5x -> verify text committed (length check)
- Fill to MAX_ENTRIES (50) -> verify eviction
- Pin 3 entries -> clear all -> pinned survive

### Step 60 — Create clipboard E2E edge tests

**File**: `tools/e2e/tests/clipboard/test_edge.py`

Tests (3):
- Paste from empty clipboard
- Very long clipboard entry (1000+ chars)
- Clipboard with special chars (newlines, tabs, emoji)

### Step 61 — Verify clipboard phase

Run `./gradlew test --tests "*ClipboardRepositoryTest*"`.
Run `PORT=3950 python e2e_runner.py --feature clipboard`.

---

## Phase 10: Macros (steps 62–66)

### Step 62 — Extend `MacroEngineTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/feature/macro/MacroEngineTest.kt`
(existing file — add tests)

Tests (3):
- Record with modifier keys -> verify steps include modifiers
- Record empty macro (0 steps) -> verify no crash on replay
- Cancel recording mid-capture -> verify clean state

### Step 63 — Migrate macros E2E smoke tests

**File**: `tools/e2e/tests/macros/test_smoke.py`

Migrate existing tests from `tools/e2e/tests/test_macros.py`.

### Step 64 — Create macros E2E stress tests

**File**: `tools/e2e/tests/macros/test_stress.py`

Tests (4):
- Record 20-step macro -> replay -> verify output (length check)
- Replay same macro 5x rapidly
- Record with Ctrl+Shift modifiers
- Record, rename, replay -> verify name persists

### Step 65 — Create macros E2E edge tests

**File**: `tools/e2e/tests/macros/test_edge.py`

Tests (3):
- Record empty macro -> replay -> no crash
- Cancel recording mid-capture
- Delete macro -> verify removed from panel

### Step 66 — Verify macros phase

Run `./gradlew test --tests "*MacroEngineTest*"`.
Run `PORT=3950 python e2e_runner.py --feature macros`.

---

## Phase 11: Command Mode (steps 67–72)

### Step 67 — Extend `CommandModeDetectorTest.kt`

**File**: `app/src/test/java/dev/devkey/keyboard/feature/command/CommandModeDetectorTest.kt`
(existing file — add tests)

Tests (4):
- `detectSync` — terminal package returns command mode
- `detectSync` — non-terminal package returns normal mode
- `toggleManualOverride` — flips override state
- `isCommandMode` — respects manual override over auto-detect

### Step 68 — Migrate command mode E2E smoke tests

**File**: `tools/e2e/tests/command_mode/test_smoke.py`

Migrate existing tests from `tools/e2e/tests/test_command_mode.py`.

### Step 69 — Create command mode E2E stress tests

**File**: `tools/e2e/tests/command_mode/test_stress.py`

Tests (3):
- Rapid focus changes between terminal and non-terminal
- Manual override -> focus change -> verify persists
- 5 app focus switches in sequence

### Step 70 — Create command mode E2E edge tests

**File**: `tools/e2e/tests/command_mode/test_edge.py`

Tests (2):
- Unknown package name -> verify fallback
- Toggle override on/off rapidly

### Step 71 — Verify command mode phase

Run `./gradlew test --tests "*CommandModeDetectorTest*"`.
Run `PORT=3950 python e2e_runner.py --feature command_mode`.

### Step 72 — Final full-suite verification

Run `./gradlew assembleDebug` (build gate).
Run `./gradlew test` (all unit + integration tests).
Run `PORT=3950 python e2e_runner.py` on `Pixel_7_API_36` (all E2E tests,
flat + subdirectory). Verify total test count matches spec expectations.

---

## Constraints

- All E2E runs target `Pixel_7_API_36` emulator (Windows only)
- Debug server always on `PORT=3950`
- No `connectedAndroidTest` anywhere
- Use `wait_for()` harness helpers, never blind waits/sleeps

## Privacy Checklist

- [ ] No assertion checks typed content — only counts, lengths, state names
- [ ] No debug logs record typed text
- [ ] `MockInputConnection` tracks call counts, not committed strings
- [ ] E2E assertions use structural events from DevKeyLogger
- [ ] Clipboard tests assert entry count and pin status, not content
- [ ] Clipboard E2E test setup does not log entry content via debug server
- [ ] Punctuation test fixture strings use single chars / generic markers, not realistic words
- [ ] `TestSessionDependencies.kt` stays in `src/test/` only — add placement guard comment

## Implementation Notes

- `processFileForTest` in `VoiceInputEngine` — if it does not already exist as
  a production method, do NOT add it. Test `readWavPcm`/`runInference`
  indirectly through the public `stopListening` path with a fake audio file.

## Files Created (Summary)

| Category | Count |
|----------|-------|
| Test utilities | 3 (`TestImeState`, `MockInputConnection`, `TestSessionDependencies`) |
| New unit test files | 12 |
| New integration test files | 5 |
| New E2E test files | ~30 |
| Modified production files | 1 (`TrieDictionary.kt` — visibility only) |
| Modified test files | 2 (`MacroEngineTest.kt`, `CommandModeDetectorTest.kt`) |
| Modified infrastructure | 1 (`e2e_runner.py`) |
