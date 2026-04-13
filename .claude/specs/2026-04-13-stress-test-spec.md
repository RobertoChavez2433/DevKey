# Feature Stress Test Suite Spec

**Date**: 2026-04-13
**Status**: APPROVED

## Goal

Three-tier test pyramid (unit -> integration -> E2E stress) for all 10 DevKey
features. No testing gaps. All stress dimensions: rapid-fire, edge cases,
state exhaustion, combinatorial interaction.

---

## Infrastructure TODO

- [ ] Update `e2e_runner.py` — subdirectory discovery (`tests/**/test_*.py`)
- [ ] Add `--feature` flag to runner (`python e2e_runner.py --feature prediction`)
- [ ] Create `testutil/TestImeState.kt` — shared ImeState factory with defaults
- [ ] Create `testutil/MockInputConnection.kt` — reusable IC mock for unit tests
- [ ] Create `testutil/TestSessionDependencies.kt` — reset helper for `@Before`
- [ ] Verify existing 54 flat-file E2E tests still pass after runner update

---

## Feature 1: Next-Word Prediction

### Unit Tests (`core/SuggestionCoordinatorTest.kt`)
- [ ] `setNextSuggestions` — bigram hit path: updates legacy + SessionDependencies
- [ ] `setNextSuggestions` — bigram miss path: falls back to punctuation list
- [ ] `setNextSuggestions` — no previous word: returns punctuation list
- [ ] `setNextSuggestions` — prediction disabled: returns punctuation list
- [ ] `getLastCommittedWordBeforeCursor` — word found after whitespace
- [ ] `getLastCommittedWordBeforeCursor` — no word (empty, all whitespace)
- [ ] `getLastCommittedWordBeforeCursor` — word with apostrophe
- [ ] `getLastCommittedWordBeforeCursor` — null InputConnection
- [ ] `updateSuggestions` — mPredicting true: calls showSuggestions
- [ ] `updateSuggestions` — mPredicting false: calls setNextSuggestions
- [ ] `showSuggestions(WordComposer)` — correction modes FULL, FULL_BIGRAM
- [ ] `showSuggestions` — isMostlyCaps suppresses correction
- [ ] `onSelectionChanged` — composing out of sync clears prediction
- [ ] `onSelectionChanged` — re-correction enabled triggers old suggestions

### Unit Tests (`feature/prediction/TrieDictionaryTest.kt`)
- [ ] `editDistance` — identical strings = 0
- [ ] `editDistance` — single insertion/deletion/substitution = 1
- [ ] `editDistance` — empty string cases
- [ ] `getSuggestions` — serves from cached prefix completions
- [ ] `getSuggestions` — falls back to DFS when no cache
- [ ] `getSuggestions` — filters out exact match
- [ ] `getFuzzyMatches` — respects maxDistance threshold
- [ ] `getFuzzyMatches` — early-exit at maxResults*2
- [ ] `isValidWord` — case-insensitive lookup
- [ ] `load` — parses TSV format correctly

### Integration Tests (`integration/PredictionPipelineTest.kt`)
- [ ] Compose word -> space -> verify nextWordSuggestions populated
- [ ] Tap suggestion -> verify triggerNextSuggestions fires
- [ ] Cursor reposition -> verify onSelectionChanged clears composing

### E2E Tests (`tools/e2e/tests/prediction/`)
- [ ] `test_smoke.py` — migrate existing 3 tests from `test_next_word.py`
- [ ] `test_stress.py` — type 20 words rapidly, verify suggestions after each space
- [ ] `test_stress.py` — suggestion tap chain: tap 5x, verify each triggers next-word
- [ ] `test_stress.py` — 3-sentence paragraph with predictions throughout
- [ ] `test_stress.py` — bigram exhaustion: rare word -> graceful fallback
- [ ] `test_edge.py` — empty field + space (no prior word)
- [ ] `test_edge.py` — Unicode/accented character input
- [ ] `test_edge.py` — cursor reposition mid-sentence -> predictions update
- [ ] `test_edge.py` — very long word (50 chars) -> space -> no crash
- [ ] `test_edge.py` — rapid space-backspace-space cycle

---

## Feature 2: Autocorrect Engine

### Unit Tests (`core/InputHandlersTest.kt` — separator paths)
- [ ] `handleSeparator` — mAutoCorrectOn picks default suggestion
- [ ] `handleSeparator` — AutocorrectEngine aggressive auto-apply path
- [ ] `handleSeparator` — AutocorrectEngine suggestion (non-auto-apply) path
- [ ] `handleSeparator` — AutocorrectEngine OFF path
- [ ] `handleSeparator` — revert separator guard skips autocorrect
- [ ] `commitTyped` — manual vs accepted typed TextEntryState paths

### Integration Tests (`integration/AutocorrectPipelineTest.kt`)
- [ ] Type misspelled word + space -> verify correction applied via IC
- [ ] Verify pendingCorrection flow for non-auto-apply suggestions
- [ ] Autocorrect then space -> verify next-word suggestions fire

### E2E Tests (`tools/e2e/tests/autocorrect/`)
- [ ] `test_smoke.py` — migrate existing 3 tests from `test_autocorrect.py`
- [ ] `test_stress.py` — aggressive mode: 10 misspelled words in sequence
- [ ] `test_stress.py` — type corrected word -> backspace -> verify revert
- [ ] `test_stress.py` — mixed correct/incorrect rapid paragraph
- [ ] `test_stress.py` — autocorrect then suggestion tap chain
- [ ] `test_edge.py` — single-char words (no correction expected)
- [ ] `test_edge.py` — numbers mixed with letters
- [ ] `test_edge.py` — autocorrect OFF -> verify no interference
- [ ] `test_edge.py` — word at correction boundary (edit distance = max)

---

## Feature 3: Voice Input

### Unit Tests (`feature/voice/WhisperProcessorTest.kt`)
- [ ] `loadResources` — valid 16-byte header parsed correctly
- [ ] `loadResources` — header too short (< 16 bytes) returns false
- [ ] `loadResources` — bad magic number returns false
- [ ] `computeMelSpectrogram` — Hann window applied correctly
- [ ] `computeMelSpectrogram` — FFT produces expected frequency bins
- [ ] `computeMelSpectrogram` — mel filter bank matrix multiplication
- [ ] `computeMelSpectrogram` — log10 clamped at 1e-10
- [ ] `computeMelSpectrogram` — Whisper normalization: (clamp(max-8)+4)/4
- [ ] `processAudio` — pads short audio to 480,000 samples
- [ ] `processAudio` — trims long audio to 480,000 samples
- [ ] `processAudio` — empty audio returns null or empty
- [ ] `decodeTokens` — maps valid token IDs to strings
- [ ] `decodeTokens` — filters out-of-range IDs

### Unit Tests (`feature/voice/VoiceInputEngineTest.kt`)
- [ ] State: IDLE -> startListening -> LISTENING
- [ ] State: LISTENING -> stopListening -> PROCESSING -> IDLE
- [ ] State: LISTENING -> cancelListening -> IDLE
- [ ] State: startListening with no permission -> ERROR
- [ ] State: startListening when already LISTENING -> no-op
- [ ] `readWavPcm` — file < 44 bytes returns null
- [ ] `readWavPcm` — valid WAV extracts correct PCM samples
- [ ] `runInference` — empty audio returns "[No speech detected]"

### Integration Tests (`integration/VoicePipelineTest.kt`)
- [ ] Full startListening -> stopListening -> transcription with fake capture
- [ ] Cancel mid-processing -> verify state returns to IDLE
- [ ] processFileForTest with known WAV -> verify non-empty result

### E2E Tests (`tools/e2e/tests/voice/`)
- [ ] `test_smoke.py` — migrate existing 4 tests from `test_voice.py`
- [ ] `test_stress.py` — start/stop 5x rapidly
- [ ] `test_stress.py` — cancel during PROCESSING state
- [ ] `test_stress.py` — file-based inference: short audio (< 1s)
- [ ] `test_stress.py` — file-based inference: 30s boundary audio
- [ ] `test_stress.py` — back-to-back inference: process, commit, process
- [ ] `test_edge.py` — cancel from IDLE (should no-op)
- [ ] `test_edge.py` — voice button during composing -> commit first
- [ ] `test_edge.py` — empty audio buffer -> "[No speech detected]"

---

## Feature 4: Modifier / Chording State Machine

### Unit Tests (`core/ModifierChordingControllerTest.kt`)
- [ ] Ctrl chording delay=0 returns without sending
- [ ] Ctrl chording delay=keycode sends key
- [ ] Alt chording delay=0 returns without sending
- [ ] Meta chording delay=0 returns without sending
- [ ] `sendModifierKeysDown` — Ctrl+Shift sends both DOWN events
- [ ] `sendModifierKeysDown` — no modifiers active sends nothing
- [ ] `handleModifierKeysUp` — resets Ctrl state
- [ ] `handleModifierKeysUp` — shift-locked does not reset shift

### Unit Tests (`core/ModifiableKeyDispatcherTest.kt`)
- [ ] Plain letter with only shift -> commit text (no key events)
- [ ] Ctrl+letter -> sends modified key down/up
- [ ] Ctrl+A override=0, no Alt -> shows toast
- [ ] Ctrl+A override=0, Alt active -> sends Ctrl+A
- [ ] Ctrl+A override=1 -> silently ignores
- [ ] Ctrl+A override=2 -> sends standard Ctrl+A
- [ ] Digit with modifier -> clears meta then sends char
- [ ] Non-ASCII char (>127) -> falls through to sendKeyChar

### Unit Tests (`core/ModifierHandlerTest.kt`)
- [ ] `nextShiftState` — OFF -> ON -> LOCKED -> OFF cycle (caps lock on)
- [ ] `nextShiftState` — OFF -> ON -> OFF toggle (caps lock off)
- [ ] `onPress(SHIFT)` — starts multitouch shift
- [ ] `onRelease(SHIFT)` — commits multitouch shift
- [ ] `onPress(CTRL)` — toggles mModCtrl
- [ ] `onRelease(CTRL)` — chording reset, sends key-up
- [ ] `updateShiftKeyState` — caps mode ON from EditorInfo
- [ ] `updateShiftKeyState` — chording active skips auto-cap

### Integration Tests (`integration/ModifierPipelineTest.kt`)
- [ ] Full press/release Ctrl cycle -> verify KeyEvents on IC
- [ ] Chording: Ctrl down, letter, Ctrl up -> verify modified key event
- [ ] Shift one-shot: tap shift, type letter -> uppercase, next letter lowercase

### E2E Tests (`tools/e2e/tests/modifiers/`)
- [ ] `test_smoke.py` — migrate existing modifier tests
- [ ] `test_stress.py` — Ctrl+A through Ctrl+Z full alphabet
- [ ] `test_stress.py` — rapid shift cycling: tap 10x, verify state cycle
- [ ] `test_stress.py` — three-modifier combo: Ctrl+Alt+Shift+key
- [ ] `test_stress.py` — one-shot consumption: Ctrl tap -> letter -> verify released
- [ ] `test_edge.py` — modifier press without release (orphan state)
- [ ] `test_edge.py` — mode switch while modifier held
- [ ] `test_edge.py` — shift lock -> type 20 chars -> verify all uppercase
- [ ] `test_edge.py` — Ctrl+A with all 3 override settings
- [ ] `test_state_exhaustion.py` — all modifier state transitions enumerated

---

## Feature 5: Clipboard

### Unit Tests (if needed beyond DAO tests)
- [ ] `ClipboardRepository` — addEntry, pin, unpin, delete, clearAll
- [ ] `ClipboardRepository` — search filters correctly
- [ ] `ClipboardRepository` — clearAll preserves pinned entries

### E2E Tests (`tools/e2e/tests/clipboard/`)
- [ ] `test_smoke.py` — migrate existing test_clipboard.py
- [ ] `test_stress.py` — open/close panel 10x rapidly
- [ ] `test_stress.py` — paste same entry 5x -> verify text committed
- [ ] `test_stress.py` — fill to max entries -> verify eviction
- [ ] `test_stress.py` — pin 3 entries -> clear all -> pinned survive
- [ ] `test_edge.py` — paste from empty clipboard
- [ ] `test_edge.py` — very long clipboard entry (1000+ chars)
- [ ] `test_edge.py` — clipboard with special chars (newlines, tabs, emoji)

---

## Feature 6: Macro Engine

### Unit Tests (extend existing MacroEngineTest.kt)
- [ ] Record with modifier keys -> verify steps include modifiers
- [ ] Record empty macro (0 steps) -> verify no crash on replay
- [ ] Cancel recording mid-capture -> verify clean state

### E2E Tests (`tools/e2e/tests/macros/`)
- [ ] `test_smoke.py` — migrate existing test_macros.py
- [ ] `test_stress.py` — record 20-step macro -> replay -> verify output
- [ ] `test_stress.py` — replay same macro 5x rapidly
- [ ] `test_stress.py` — record with Ctrl+Shift modifiers
- [ ] `test_stress.py` — record, rename, replay -> verify name persists
- [ ] `test_edge.py` — record empty macro -> replay -> no crash
- [ ] `test_edge.py` — cancel recording mid-capture
- [ ] `test_edge.py` — delete macro -> verify removed from panel

---

## Feature 7: Punctuation Heuristics

### Unit Tests (`core/PunctuationHeuristicsTest.kt`)
- [ ] `swapPunctuationAndSpace` — "a ." -> "a. "
- [ ] `swapPunctuationAndSpace` — non-separator char -> no swap
- [ ] `reswapPeriodAndSpace` — ". ." -> " .."
- [ ] `reswapPeriodAndSpace` — different pattern -> no reswap
- [ ] `doubleSpace` — "a  " -> "a. "
- [ ] `doubleSpace` — at start of field -> no conversion
- [ ] `doubleSpace` — after non-alphanumeric -> no conversion
- [ ] `maybeRemovePreviousPeriod` — ".." -> removes trailing period
- [ ] `removeTrailingSpace` — removes if space, no-ops otherwise
- [ ] `initSuggestPuncList` — default vs custom punctuation settings

### Integration Tests (`integration/PunctuationPipelineTest.kt`)
- [ ] handleSeparator -> doubleSpace -> verify IC text transformation
- [ ] handleSeparator -> swapPunctuationAndSpace chain

### E2E Tests (`tools/e2e/tests/punctuation/`)
- [ ] `test_smoke.py` — migrate existing 5 tests from test_auto_punctuation.py
- [ ] `test_stress.py` — 10 double-space-to-period conversions in sequence
- [ ] `test_stress.py` — alternating separators rapid: period, comma, exclamation
- [ ] `test_stress.py` — double-space after autocorrected word
- [ ] `test_stress.py` — period-reswap after "..." pattern
- [ ] `test_edge.py` — double-space at very start of empty field
- [ ] `test_edge.py` — space after emoji
- [ ] `test_edge.py` — punctuation after number
- [ ] `test_edge.py` — three rapid spaces (". " + space, not ".. ")

---

## Feature 8: Input Handling

### Unit Tests (`core/InputHandlersTest.kt`)
- [ ] `handleCharacter` — first char starts prediction, sets composing
- [ ] `handleCharacter` — auto-capitalize first char after sentence
- [ ] `handleCharacter` — modifier active bypasses composing
- [ ] `handleBackspace` — composing: trims last char
- [ ] `handleBackspace` — composing empty: deletes surrounding text
- [ ] `handleBackspace` — UNDO_COMMIT state: reverts last word
- [ ] `handleBackspace` — accelerated delete after threshold
- [ ] `handleBackspace` — mEnteredText match: deletes entered text length
- [ ] `commitTyped` — commits composing, clears prediction state
- [ ] `commitTyped` — not predicting: no-op
- [ ] `revertLastWord` — restores composing text from committed

### Unit Tests (`core/InputDispatcherTest.kt`)
- [ ] `onKey` — KEYCODE_DELETE routes to handleBackspace
- [ ] `onKey` — ASCII letter routes to handleDefault
- [ ] `onKey` — KEYCODE_ESCAPE sends escape
- [ ] `onKey` — KEYCODE_TAB sends tab
- [ ] `onKey` — delete acceleration: rapid deletes increase count
- [ ] `onText` — single-char separator commits typed first

### E2E Tests (`tools/e2e/tests/input/`)
- [ ] `test_smoke.py` — migrate existing 4 tests from test_core_input.py
- [ ] `test_stress.py` — 50-word paragraph typed at speed
- [ ] `test_stress.py` — rapid backspace: type 20 chars, delete all, verify empty
- [ ] `test_stress.py` — backspace during composing vs after commit
- [ ] `test_stress.py` — enter mid-composing -> verify word committed
- [ ] `test_stress.py` — mixed separators and letters rapidly
- [ ] `test_edge.py` — backspace in empty field
- [ ] `test_edge.py` — very long composing word (100 chars)
- [ ] `test_edge.py` — non-ASCII input (accented, CJK, RTL)
- [ ] `test_edge.py` — backspace after suggestion tap (revert path)
- [ ] `test_state_exhaustion.py` — TextEntryState full cycle through all states

---

## Feature 9: Layout Modes

### Unit Tests (`keyboard/switcher/AutoModeSwitchStateMachineTest.kt`)
- [ ] State: ALPHA -> toggleSymbols -> SYMBOL_BEGIN
- [ ] State: SYMBOL_BEGIN -> onKey -> SYMBOL
- [ ] State: SYMBOL -> toggleSymbols -> ALPHA
- [ ] State: ALPHA -> setMomentary -> MOMENTARY
- [ ] State: MOMENTARY -> pointer count > 1 -> CHORDING
- [ ] State: MOMENTARY -> onCancelInput -> ALPHA
- [ ] Full 5-state transition matrix (all valid transitions)

### E2E Tests (`tools/e2e/tests/modes/`)
- [ ] `test_smoke.py` — migrate existing 4 tests from test_modes.py
- [ ] `test_stress.py` — rapid Normal->Symbols->Normal 10x
- [ ] `test_stress.py` — mode switch mid-composing word -> verify commit
- [ ] `test_stress.py` — compact -> full -> compact roundtrip
- [ ] `test_stress.py` — symbols -> type number -> toggle back -> clean state
- [ ] `test_edge.py` — switch to symbols during modifier hold
- [ ] `test_edge.py` — mode switch with suggestion bar visible

---

## Feature 10: Command Mode

### Unit Tests (extend existing CommandModeDetectorTest.kt)
- [ ] `detectSync` — terminal package returns command mode
- [ ] `detectSync` — non-terminal package returns normal mode
- [ ] `toggleManualOverride` — flips override state
- [ ] `isCommandMode` — respects manual override over auto-detect

### E2E Tests (`tools/e2e/tests/command_mode/`)
- [ ] `test_smoke.py` — migrate existing test_command_mode.py
- [ ] `test_stress.py` — rapid focus changes between terminal and non-terminal
- [ ] `test_stress.py` — manual override -> focus change -> verify persists
- [ ] `test_stress.py` — 5 app focus switches in sequence
- [ ] `test_edge.py` — unknown package name -> verify fallback
- [ ] `test_edge.py` — toggle override on/off rapidly

---

## Constraints

- Windows Android emulator only (Pixel_7_API_36)
- Debug server on port 3950
- No connectedAndroidTest
- Privacy: assertions check lengths/counts, never typed content
- No blind waits — use harness `wait_for()` helpers

## Implementation Order

1. Infrastructure (runner update, test utilities)
2. Prediction (highest user impact)
3. Input handling (hottest code path)
4. Punctuation heuristics (easiest unit tests)
5. Modifiers (complex state machine)
6. Autocorrect (builds on prediction)
7. Voice (independent subsystem)
8. Modes (moderate complexity)
9. Clipboard (moderate complexity)
10. Macros (lower risk)
11. Command mode (lowest risk)
