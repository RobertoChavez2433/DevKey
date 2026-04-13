# Source Excerpts — By Concern

## Concern: Test Utility Dependencies

### ImeState Construction
`ImeState` is a data-heavy class holding all mutable IME state. Constructor
requires many fields. A `TestImeState.kt` factory should provide sensible
defaults:
- `mPredicting = false`
- `mAutoCorrectOn = false`
- `mWord = WordComposer()` (empty)
- All modifier flags = false
- Chording delays = 0

### MockInputConnection
Many unit tests need a fake `InputConnection` that:
- Tracks `commitText` calls (count + length, not content for privacy)
- Returns configurable `getTextBeforeCursor` / `getTextAfterCursor`
- Tracks `sendKeyEvent` calls
- Supports `beginBatchEdit` / `endBatchEdit` nesting

### TestSessionDependencies Reset
```kotlin
object TestSessionDependencies {
    fun reset() {
        SessionDependencies.suggest = null
        SessionDependencies.commandModeDetector = null
        SessionDependencies.currentPackageName = null
        SessionDependencies.dictionaryProvider = null
        SessionDependencies.autocorrectEngine = null
        SessionDependencies.learningEngine = null
        SessionDependencies.predictionEngine = null
        // null remaining fields...
    }
}
```

## Concern: Constructor Complexity

### High-Dependency Classes (need careful mock setup)

**InputHandlers** — 7 constructor params:
`(state, icProvider, suggestionCoordinator, suggestionPicker, modifierHandler, keyEventSender, puncHeuristics)`

**InputDispatcher** — 12+ constructor params:
`(state, handlers, icProvider, candidateViewHost, keyEventSender, handleClose, isShowingOptionDialog, onOptionKeyPressed, onOptionKeyLongPressed, toggleLanguage, updateShiftKeyState, commitTyped, maybeRemovePreviousPeriod, abortCorrection, getShiftState)`

These need builder-pattern test factories or Kotlin default-arg wrappers to
keep test setup manageable.

## Concern: E2E Test File Counts

| Feature | Existing E2E Files | Spec Proposes |
|---------|-------------------|---------------|
| Prediction | `test_next_word.py` (1) | `test_smoke.py`, `test_stress.py`, `test_edge.py` (3) |
| Autocorrect | `test_autocorrect.py` (1) | 3 files |
| Voice | `test_voice.py` (1) | 3 files |
| Modifiers | `test_modifiers.py`, `test_modifier_combos.py`, `test_modifier_extended.py` (3) | 4 files |
| Clipboard | `test_clipboard.py` (1) | 3 files |
| Macros | `test_macros.py` (1) | 3 files |
| Punctuation | `test_auto_punctuation.py` (1) | 3 files |
| Input | `test_core_input.py` (1) | 4 files (incl. state_exhaustion) |
| Modes | `test_modes.py` (1) | 2 files |
| Command | `test_command_mode.py` (1) | 2 files |

**Total**: 12 existing -> ~30 new E2E files

## Concern: State Machine Exhaustion Tests

Two features specify exhaustive state transition tests:

1. **Modifiers** (`test_state_exhaustion.py`) — all modifier state transitions
2. **Input** (`test_state_exhaustion.py`) — TextEntryState full cycle

TextEntryState has 12 states with defined transitions. The state machine is
in `core/input/TextEntryState.kt` as an `object` singleton.

AutoModeSwitchStateMachine has 5 states with defined transitions via
`onToggleSymbols`, `onKey`, `setMomentary`, `onCancelInput`.
