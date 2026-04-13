# Ground Truth — Stress Test Suite

Verified from source on 2026-04-13.

## Visibility Discrepancies

These spec-targeted methods are `private` — cannot be tested directly without
visibility changes or public-API-only testing:

| Class | Method | Visibility | Line |
|-------|--------|------------|------|
| `TrieDictionary` | `editDistance(a, b)` | `private` | :161 |
| `WhisperProcessor` | `computeMelSpectrogram(audio)` | `private` | :157 |
| `VoiceInputEngine` | `runInference(audioData)` | `private suspend` | :143 |
| `VoiceInputEngine` | `readWavPcm(path)` | `private` | :243 |
| `ModifierHandler` | `nextShiftState(prevState, allowCapsLock)` | `private` | :224 |

## Verified Method Signatures

### SuggestionCoordinator (`core/SuggestionCoordinator.kt`)
- `fun setNextSuggestions()` — :158
- `fun getLastCommittedWordBeforeCursor(): CharSequence?` — :142
- `fun updateSuggestions()` — :63
- `fun showSuggestions(word: WordComposer)` — :81
- `fun showSuggestions(...)` — :100 (overload with Suggest param)
- `fun onSelectionChanged(...)` — :187

### TrieDictionary (`feature/prediction/TrieDictionary.kt`)
- `fun load(context: Context, rawResId: Int)` — :40
- `fun isValidWord(word: String): Boolean` — :105
- `fun getSuggestions(prefix: String, maxResults: Int = 5): List<Pair<String, Int>>` — :113
- `fun getFuzzyMatches(typed: String, maxDistance: Int = 2, maxResults: Int = 3): List<Pair<String, Int>>` — :139
- `private fun editDistance(a: String, b: String): Int` — :161

### WhisperProcessor (`feature/voice/WhisperProcessor.kt`)
- `fun loadResources(): Boolean` — :64
- `fun processAudio(pcmData: ShortArray, _sampleRate: Int = SAMPLE_RATE): FloatArray?` — :117
- `private fun computeMelSpectrogram(audio: FloatArray): FloatArray` — :157
- `fun decodeTokens(tokens: IntArray): String` — :260

### VoiceInputEngine (`feature/voice/VoiceInputEngine.kt`)
- `suspend fun startListening()` — :101
- `suspend fun stopListening(): String` — :128
- `fun cancelListening()` — :211
- `private suspend fun runInference(audioData: ShortArray): String` — :143
- `private fun readWavPcm(path: String): ShortArray?` — :243

### ModifierChordingController (`core/ModifierChordingController.kt`)
- `fun sendModifierKeysDown(shifted: Boolean, ic: InputConnection?)` — :91
- `fun handleModifierKeysUp(shifted: Boolean, sendKey: Boolean, ic: InputConnection?)` — :112

### ModifierHandler (`core/ModifierHandler.kt`)
- `fun onPress(primaryCode: Int)` — :26
- `fun onRelease(primaryCode: Int)` — :70
- `fun updateShiftKeyState(attr: EditorInfo?)` — :112
- `private fun nextShiftState(prevState: Int, allowCapsLock: Boolean): Int` — :224

### ModifiableKeyDispatcher (`core/ModifiableKeyDispatcher.kt`)
- Verified exists; handles Ctrl+letter, digit+modifier, non-ASCII dispatch

### InputHandlers (`core/InputHandlers.kt`)
- `fun handleDefault(primaryCode: Int, keyCodes: IntArray?)` — handleCharacter delegates here
- `fun handleCharacter(primaryCode: Int, keyCodes: IntArray?)`
- `fun handleBackspace()`
- `fun handleSeparator(primaryCode: Int)`
- `fun commitTyped(inputConnection: InputConnection?, manual: Boolean)`
- `fun revertLastWord(deleteChar: Boolean)`

### InputDispatcher (`core/InputDispatcher.kt`)
- `fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int)`
- `fun onText(text: CharSequence)`

### TextEntryState (`core/input/TextEntryState.kt`)
- `object` singleton
- States: `UNKNOWN, START, IN_WORD, ACCEPTED_DEFAULT, PICKED_SUGGESTION, PUNCTUATION_AFTER_WORD, PUNCTUATION_AFTER_ACCEPTED, SPACE_AFTER_ACCEPTED, SPACE_AFTER_PICKED, UNDO_COMMIT, CORRECTING, PICKED_CORRECTION`

### PunctuationHeuristics (`core/PunctuationHeuristics.kt`)
- `fun swapPunctuationAndSpace()`
- `fun reswapPeriodAndSpace()`
- `fun doubleSpace()`
- `fun maybeRemovePreviousPeriod(text: CharSequence)`
- `fun removeTrailingSpace()`
- `fun initSuggestPuncList()`

### ClipboardRepository (`feature/clipboard/ClipboardRepository.kt`)
- `suspend fun addEntry(content: String)`
- `suspend fun pin(id: Long)` / `suspend fun unpin(id: Long)`
- `suspend fun delete(id: Long)` / `suspend fun clearAll()`
- `fun search(query: String): Flow<List<ClipboardHistoryEntity>>`
- Constant: `MAX_ENTRIES = 50`

### MacroEngine (`feature/macro/MacroEngine.kt`)
- `fun startRecording()` / `fun stopRecording(): List<MacroStep>`
- `fun captureKey(key: String, keyCode: Int, modifiers: List<String>)`
- `fun cancelRecording()` / `fun replay(...)`
- `val isRecording: Boolean`

### AutoModeSwitchStateMachine (`keyboard/switcher/AutoModeSwitchStateMachine.kt`)
- States: `STATE_ALPHA=0, STATE_SYMBOL_BEGIN=1, STATE_SYMBOL=2, STATE_MOMENTARY=3, STATE_CHORDING=4`
- `fun setMomentary()` / `fun onToggleSymbols(isSymbols, preferSymbols)`
- `fun onCancelInput()` / `fun onKey(key, isSymbols)`

### CommandModeDetector (`feature/command/CommandModeDetector.kt`)
- `suspend fun detect(packageName: String?)`
- `fun detectSync(packageName: String?)` — blocking wrapper
- `fun toggleManualOverride()`
- `fun isCommandMode(): Boolean`
- `val inputMode: StateFlow<InputMode>`
- 9 known terminal packages in `TERMINAL_PACKAGES`

### SessionDependencies (`ui/keyboard/SessionDependencies.kt`)
- `object` singleton
- Holds: `suggest`, `commandModeDetector`, `currentPackageName`, `dictionaryProvider`, `autocorrectEngine`, `learningEngine`, `predictionEngine`, composing word state

## E2E Runner Current State

- Discovery: flat `os.listdir("tests/")` — only finds `test_*.py` in top-level
- Filter: `--test module_name` or `--test module.func_name`
- No `--feature` flag
- No subdirectory recursion
- 20 existing test files (flat)
- Pre-test focus guard via `adb.ensure_keyboard_visible`

## Test Utility State

| Proposed Utility | Status |
|-----------------|--------|
| `testutil/TestImeState.kt` | Does not exist |
| `testutil/MockInputConnection.kt` | Does not exist |
| `testutil/TestSessionDependencies.kt` | Does not exist |
| `testutil/FakeCommandAppDao.kt` | EXISTS |
| `testutil/FakeLearnedWordDao.kt` | EXISTS |
