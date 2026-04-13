# Tailor Manifest — Stress Test Suite

**Spec**: `.claude/specs/2026-04-13-stress-test-spec.md`
**Date**: 2026-04-13
**Scope**: 10 features × 3 test tiers (unit, integration, E2E stress)

## Files Analyzed

### Infrastructure
| File | Status |
|------|--------|
| `tools/e2e/e2e_runner.py` | EXISTS — flat discovery only, needs subdirectory + `--feature` flag |
| `app/src/test/.../testutil/FakeCommandAppDao.kt` | EXISTS |
| `app/src/test/.../testutil/FakeLearnedWordDao.kt` | EXISTS |
| `testutil/TestImeState.kt` | DOES NOT EXIST — needs creation |
| `testutil/MockInputConnection.kt` | DOES NOT EXIST — needs creation |
| `testutil/TestSessionDependencies.kt` | DOES NOT EXIST — needs creation |

### Feature Source Files (all verified to exist)
| Feature | Main Class(es) | Existing Tests |
|---------|---------------|----------------|
| Prediction | `core/SuggestionCoordinator.kt`, `feature/prediction/TrieDictionary.kt` | `PredictionEngineTest`, `AutocorrectEngineTest`, `DictionaryProviderTest`, `LearningEngineTest` |
| Autocorrect | `feature/prediction/AutocorrectEngine.kt`, `core/InputHandlers.kt` | `AutocorrectEngineTest` |
| Voice | `feature/voice/WhisperProcessor.kt`, `feature/voice/VoiceInputEngine.kt` | `SilenceDetectorTest` |
| Modifiers | `core/ModifierChordingController.kt`, `core/ModifiableKeyDispatcher.kt`, `core/ModifierHandler.kt` | `ModifierStateManagerTest` |
| Clipboard | `feature/clipboard/ClipboardRepository.kt` | — |
| Macros | `feature/macro/MacroEngine.kt` | `MacroEngineTest`, `MacroSerializerTest`, `DefaultMacrosTest` |
| Punctuation | `core/PunctuationHeuristics.kt` | — |
| Input | `core/InputHandlers.kt`, `core/InputDispatcher.kt`, `core/input/TextEntryState.kt` | — |
| Modes | `keyboard/switcher/AutoModeSwitchStateMachine.kt` | — |
| Command | `feature/command/CommandModeDetector.kt` | `CommandModeDetectorTest` |

### Existing E2E Tests (20 flat files in `tools/e2e/tests/`)
`test_autocorrect.py`, `test_auto_cap.py`, `test_auto_punctuation.py`,
`test_clipboard.py`, `test_command_mode.py`, `test_core_input.py`,
`test_long_press.py`, `test_macros.py`, `test_modes.py`,
`test_modifier_combos.py`, `test_modifier_extended.py`, `test_modifiers.py`,
`test_next_word.py`, `test_plugins.py`, `test_rapid.py`, `test_smoke.py`,
`test_ui_prefs.py`, `test_visual_diff.py`, `test_voice.py`

### Existing Unit Tests (21 files in `app/src/test/`)
See `app/src/test/java/dev/devkey/keyboard/` for full listing.

## Open Questions

1. **Private-method testing**: 6 methods the spec wants tested directly are
   `private`. Options: (a) test via public API, (b) make `internal` with
   `@VisibleForTesting`, (c) extract to testable classes. Needs a decision
   per-method.

2. **E2E subdirectory migration**: Moving 20 existing flat tests into
   feature subdirectories is a large rename. The spec proposes subdirectories
   (`tests/prediction/`, `tests/autocorrect/`, etc.) but only says to
   "migrate existing N tests." Confirm whether all 20 files move or only
   spec-named ones.

3. **Integration test location**: The spec proposes
   `integration/PredictionPipelineTest.kt` etc. but no integration source set
   exists in the Gradle build. Needs a decision on whether these are Robolectric
   unit tests or a new `androidTest` source set (which conflicts with the
   no-connectedAndroidTest rule).
