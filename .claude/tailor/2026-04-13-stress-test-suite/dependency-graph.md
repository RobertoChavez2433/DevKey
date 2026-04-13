# Dependency Graph — Stress Test Suite

## Test Infrastructure Dependencies

```
e2e_runner.py
 └── tools/e2e/tests/*.py (flat discovery — needs subdirectory support)
      └── tools/e2e/lib/ (adb, assertions, HTTP client)
           └── tools/debug-server/ (port 3950)

testutil/TestImeState.kt (NEW)
 └── core/ImeState.kt
      └── core/ImeStateLoader.kt

testutil/MockInputConnection.kt (NEW)
 └── android.view.inputmethod.InputConnection (framework)

testutil/TestSessionDependencies.kt (NEW)
 └── ui/keyboard/SessionDependencies.kt (object singleton)
      ├── feature/prediction/{AutocorrectEngine, DictionaryProvider, LearningEngine, PredictionEngine}
      ├── feature/command/CommandModeDetector
      ├── feature/voice/VoiceInputEngine
      └── suggestion/engine/Suggest
```

## Feature Dependency Chains

### Prediction Tests
```
SuggestionCoordinatorTest
 └── SuggestionCoordinator
      ├── InputConnectionProvider (mock needed)
      ├── ImeState.mPredicting, mBestWord, mWord
      ├── SessionDependencies.predictionEngine
      ├── SuggestionPicker (candidate view bridge)
      └── Suggest (JNI dictionary — needs mock)

TrieDictionaryTest
 └── TrieDictionary (self-contained, needs Context for load())
```

### Autocorrect Tests
```
InputHandlersTest (separator paths)
 └── InputHandlers
      ├── ImeState (mAutoCorrectOn, mAutoCorrectEnabled)
      ├── InputConnectionProvider (mock needed)
      ├── SuggestionCoordinator
      ├── SuggestionPicker
      ├── ModifierHandler
      ├── KeyEventSender
      └── PunctuationHeuristics
```

### Voice Tests
```
WhisperProcessorTest
 └── WhisperProcessor (self-contained, loads from raw resources)

VoiceInputEngineTest
 └── VoiceInputEngine
      ├── WhisperProcessor
      ├── AudioRecord (framework — mock needed)
      └── State machine: IDLE -> LISTENING -> PROCESSING -> IDLE
```

### Modifier Tests
```
ModifierChordingControllerTest
 └── ModifierChordingController
      ├── InputConnectionProvider
      ├── KeyEventSender
      └── ImeState (chording delays)

ModifiableKeyDispatcherTest
 └── ModifiableKeyDispatcher
      ├── ImeState (modifier flags, Ctrl+A override)
      ├── InputConnectionProvider
      ├── KeyEventSender
      └── ModifierHandler

ModifierHandlerTest
 └── ModifierHandler
      ├── ImeState (shift state, modifier flags)
      ├── KeyboardSwitcher callbacks
      └── EditorInfo (for auto-cap)
```

### Punctuation Tests
```
PunctuationHeuristicsTest
 └── PunctuationHeuristics
      ├── InputConnectionProvider (mock needed)
      ├── SettingsRepository
      └── updateShiftKeyState callback
```

### Input Handling Tests
```
InputHandlersTest
 └── (see Autocorrect — same class, different paths)

InputDispatcherTest
 └── InputDispatcher
      ├── InputHandlers
      ├── ImeState
      ├── InputConnectionProvider
      ├── CandidateViewHost
      ├── KeyEventSender
      └── many callbacks (handleClose, toggleLanguage, etc.)
```

### Modes Tests
```
AutoModeSwitchStateMachineTest
 └── AutoModeSwitchStateMachine (self-contained)
      ├── changeKeyboardMode callback
      └── getPointerCount callback
```

### Command Mode Tests
```
CommandModeDetectorTest (EXISTS — extend)
 └── CommandModeDetector
      └── CommandModeRepository
           └── FakeCommandAppDao (EXISTS in testutil/)
```

## Cross-Feature Dependencies

- **Prediction -> Autocorrect**: AutocorrectEngine uses PredictionEngine
  suggestions; autocorrect tests may trigger prediction flows.
- **Input -> Prediction**: `handleCharacter` starts prediction; `handleSeparator`
  triggers autocorrect + next-word.
- **Input -> Punctuation**: `handleSeparator` delegates to PunctuationHeuristics.
- **Modifiers -> Input**: ModifiableKeyDispatcher sits between key press and
  InputHandlers.
- **Modes -> Input**: Mode switch commits composing word via InputHandlers.
