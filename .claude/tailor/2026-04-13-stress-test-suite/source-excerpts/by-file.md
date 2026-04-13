# Source Excerpts — By File

## E2E Runner (`tools/e2e/e2e_runner.py`)

**Discovery function** (lines 53-94):
```python
def discover_tests(test_filter: str = None) -> List[Tuple[str, callable]]:
    tests_dir = os.path.join(os.path.dirname(__file__), "tests")
    tests = []
    for filename in sorted(os.listdir(tests_dir)):          # <-- flat only
        if not filename.startswith("test_") or not filename.endswith(".py"):
            continue
        module_name = filename[:-3]
        # filter logic...
        module = importlib.import_module(f"tests.{module_name}")
        # function discovery...
    return tests
```
Key: `os.listdir` -> needs `os.walk` or `pathlib.glob` for subdirectories.
Module import pattern `tests.{module_name}` -> needs `tests.{subdir}.{module_name}`.

**CLI args** (lines ~150+): uses `argparse` with `--test` and `--device`.

## SessionDependencies (`ui/keyboard/SessionDependencies.kt`)

```kotlin
object SessionDependencies {
    @Volatile var suggest: Suggest? = null
    @Volatile var commandModeDetector: CommandModeDetector? = null
    @Volatile var currentPackageName: String? = null
    @Volatile var dictionaryProvider: DictionaryProvider? = null
    @Volatile var autocorrectEngine: AutocorrectEngine? = null
    @Volatile var learningEngine: LearningEngine? = null
    @Volatile var predictionEngine: PredictionEngine? = null
    // composing word state...
}
```
Key: `object` singleton — `TestSessionDependencies.kt` reset helper must
null-out all fields in `@Before`.

## Test Utilities That Exist

### `testutil/FakeCommandAppDao.kt`
- In-memory fake of `CommandAppDao` for Room
- Used by `CommandModeDetectorTest`

### `testutil/FakeLearnedWordDao.kt`
- In-memory fake of `LearnedWordDao` for Room
- Used by `LearningEngineTest`

## Existing Test Coverage (files that already have tests)

| Production Class | Test Class | Tests |
|-----------------|-----------|-------|
| `AutocorrectEngine` | `AutocorrectEngineTest` | Engine logic |
| `PredictionEngine` | `PredictionEngineTest` | Prediction pipeline |
| `DictionaryProvider` | `DictionaryProviderTest` | Dictionary loading |
| `LearningEngine` | `LearningEngineTest` | Word learning |
| `MacroEngine` | `MacroEngineTest` | Record/replay |
| `MacroSerializer` | `MacroSerializerTest` | Serialization |
| `DefaultMacros` | `DefaultMacrosTest` | Built-in macros |
| `CommandModeDetector` | `CommandModeDetectorTest` | Auto-detect + override |
| `ModifierStateManager` | `ModifierStateManagerTest` | State tracking |
| `SilenceDetector` | `SilenceDetectorTest` | Audio silence detection |
| `KeyEventSender` | `KeyEventSenderTest` | Key event dispatch |
| `DevKeyLogger` | `DevKeyLoggerTest` | Logging |

## Production Classes That Need New Test Files

| Production Class | Proposed Test | Notes |
|-----------------|--------------|-------|
| `SuggestionCoordinator` | `SuggestionCoordinatorTest` | Heavy dependency chain |
| `TrieDictionary` | `TrieDictionaryTest` | Needs Context for `load()` |
| `WhisperProcessor` | `WhisperProcessorTest` | Self-contained after load |
| `VoiceInputEngine` | `VoiceInputEngineTest` | State machine + audio mock |
| `ModifierChordingController` | `ModifierChordingControllerTest` | IC + KeyEventSender mocks |
| `ModifiableKeyDispatcher` | `ModifiableKeyDispatcherTest` | ImeState + IC mocks |
| `ModifierHandler` | `ModifierHandlerTest` | ImeState + callbacks |
| `InputHandlers` | `InputHandlersTest` | Heavy — 7 constructor deps |
| `InputDispatcher` | `InputDispatcherTest` | Heavy — 12 constructor deps |
| `PunctuationHeuristics` | `PunctuationHeuristicsTest` | IC + settings mocks |
| `AutoModeSwitchStateMachine` | `AutoModeSwitchStateMachineTest` | Self-contained |
| `ClipboardRepository` | `ClipboardRepositoryTest` | DAO mock needed |
