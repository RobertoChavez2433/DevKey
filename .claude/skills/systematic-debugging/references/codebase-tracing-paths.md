# Codebase Tracing Paths

All paths relative to repo root: `app/src/main/java/dev/devkey/keyboard/`

---

## Path 1 — Key Input

**Flow**: KeyView → KeyboardActionBridge → LatinIME.onKey → KeyEventSender

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| KeyView | `ui/keyboard/KeyView.kt` | Touch event handler, key down/up dispatch |
| KeyboardActionBridge | `core/KeyboardActionBridge.kt` | `onKey()`, key code translation |
| LatinIME | `LatinIME.kt` | `onKey()`, key routing logic |
| KeyEventSender | `core/KeyEventSender.kt` | `sendKeyEvent()`, InputConnection dispatch |

**DevKeyLogger category**: `DevKeyLogger.text()` (Category.TEXT_INPUT)

**Instrumentation points**:
- KeyView: log `keyCode` at touch-down and touch-up
- KeyboardActionBridge: log key code after translation
- LatinIME.onKey: log which branch handles the key
- KeyEventSender: log the final event dispatched to InputConnection

---

## Path 2 — Text Commit

**Flow**: LatinIME.onKey → InputConnection.commitText → Editor

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| LatinIME | `LatinIME.kt` | `commitTyped()`, `commitText()` calls |
| WordComposer | `WordComposer.kt` | Composing word state before commit |

**DevKeyLogger category**: `DevKeyLogger.text()` (Category.TEXT_INPUT)

**Note**: Log commit timing and composing state, NEVER the committed text content.

**Instrumentation points**:
- WordComposer: log composing word length (not content) before commit
- LatinIME: log commit path taken (autocorrect vs manual vs space-trigger)
- After `commitText()`: log InputConnection return value

---

## Path 3 — Suggestion / Autocorrect

**Flow**: WordComposer → Suggest → BinaryDictionary (JNI) → CandidateView

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| WordComposer | `WordComposer.kt` | `getTypedWord()`, composing state |
| Suggest | `Suggest.kt` | `getSuggestions()` entry/exit |
| BinaryDictionary | `BinaryDictionary.kt` | JNI call to liblatinime |
| CandidateView | `CandidateView.kt` | Suggestion list update |
| PredictionEngine | `feature/prediction/PredictionEngine.kt` | Prediction orchestration |

**DevKeyLogger category**: `DevKeyLogger.native()` (Category.NATIVE_JNI) for JNI calls; `DevKeyLogger.text()` for suggestion dispatch

**Instrumentation points**:
- Suggest: log suggestion count returned (not the words themselves)
- BinaryDictionary: log JNI call duration and result count
- CandidateView: log whether suggestion bar update was triggered

---

## Path 4 — Modifier State

**Flow**: KeyView.onModifierDown → ModifierStateManager → LatinIME meta state

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| KeyView | `ui/keyboard/KeyView.kt` | Modifier key touch handling |
| ModifierStateManager | `core/ModifierStateManager.kt` | State transitions, multitouch tracking |
| LatinIME | `LatinIME.kt` | Meta state sync to InputConnection |
| ChordeTracker | `ChordeTracker.kt` | Chord/combination key tracking |

**DevKeyLogger category**: `DevKeyLogger.modifier()` (Category.MODIFIER_STATE)

**Instrumentation points**:
- ModifierStateManager: log state before and after every transition with modifier key and trigger
- LatinIME: log meta state value passed to `updateShiftKeyState()` or `sendKeyEvent()`
- On focus change (onFinishInput / onStartInput): log modifier state at entry and exit

---

## Path 5 — Mode Switch

**Flow**: KeyView.onKey(SYMBOLS) → DevKeyKeyboard → KeyboardModeManager → QwertyLayout/SymbolsLayout

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| KeyView | `ui/keyboard/KeyView.kt` | Mode-switch key detection |
| DevKeyKeyboard | `ui/keyboard/DevKeyKeyboard.kt` | Keyboard model update |
| KeyboardModeManager | `core/KeyboardModeManager.kt` | Mode state machine |
| KeyboardSwitcher | `KeyboardSwitcher.kt` | Layout switch coordination |
| QwertyLayout | `ui/keyboard/QwertyLayout.kt` | QWERTY layout definition |
| SymbolsLayout | `ui/keyboard/SymbolsLayout.kt` | Symbols layout definition |

**DevKeyLogger category**: `DevKeyLogger.ui()` (Category.COMPOSE_UI)

**Instrumentation points**:
- KeyboardModeManager: log previous mode and new mode at every transition
- KeyboardSwitcher: log which layout is selected and why
- ComposeKeyboardViewFactory: log recompose trigger after mode change

---

## Path 6 — IME Lifecycle

**Flow**: Android → LatinIME.onCreateInputView → ComposeKeyboardViewFactory → Compose UI

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| LatinIME | `LatinIME.kt` | `onCreateInputView()`, `onStartInput()`, `onFinishInput()`, `onWindowShown()`, `onWindowHidden()` |
| ComposeKeyboardViewFactory | `ui/keyboard/ComposeKeyboardViewFactory.kt` | Compose view creation and updates |
| SessionDependencies | `ui/keyboard/SessionDependencies.kt` | Per-session dependency wiring |

**DevKeyLogger category**: `DevKeyLogger.ime()` (Category.IME_LIFECYCLE)

**Instrumentation points**:
- Every IME lifecycle callback in LatinIME: log callback name + EditorInfo attributes (input type, package — not content)
- ComposeKeyboardViewFactory: log view creation and any recreation triggers
- SessionDependencies: log initialization sequence

---

## Path 7 — Voice Input

**Flow**: VoiceInputPanel → VoiceInputEngine → WhisperProcessor (TFLite) → transcription → LatinIME.commitTyped

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| VoiceInputPanel | `ui/voice/VoiceInputPanel.kt` | Panel show/hide, recording start/stop |
| VoiceInputEngine | `feature/voice/VoiceInputEngine.kt` | Recording lifecycle, audio buffer |
| WhisperProcessor | `feature/voice/WhisperProcessor.kt` | TFLite inference call |
| SilenceDetector | `feature/voice/SilenceDetector.kt` | End-of-speech detection |
| LatinIME | `LatinIME.kt` | `commitTyped()` from voice result |

**DevKeyLogger category**: `DevKeyLogger.voice()` (Category.VOICE)

**Instrumentation points**:
- VoiceInputEngine: log recording start/stop with duration
- WhisperProcessor: log inference duration and result length (not content)
- LatinIME: log commit trigger source = voice (not the transcription text)

**Privacy note**: Never log transcription text — voice transcriptions may contain sensitive content.

---

## Path 8 — Settings

**Flow**: SettingsRepository → SharedPreferences → KeyboardSwitcher / LatinIME

| Component | File | Key Breakpoints |
|-----------|------|----------------|
| SettingsRepository | `data/repository/SettingsRepository.kt` | Preference reads and writes |
| KeyboardSwitcher | `KeyboardSwitcher.kt` | Layout selection driven by settings |
| LatinIME | `LatinIME.kt` | Settings application on init / change |

**DevKeyLogger category**: `DevKeyLogger.ime()` (Category.IME_LIFECYCLE) for settings that affect IME behavior; `DevKeyLogger.ui()` for appearance settings

**Instrumentation points**:
- SettingsRepository: log which preference key is read and the returned value
- KeyboardSwitcher: log layout selection and which setting drove the choice
- LatinIME: log settings snapshot at `onStartInput()`

---

## Quick File Lookup

```
LatinIME.kt                          — IME entry point (InputMethodService)
core/KeyboardActionBridge.kt         — Key event routing
core/KeyEventSender.kt               — InputConnection key dispatch
core/ModifierStateManager.kt         — Modifier key state machine
core/KeyboardModeManager.kt          — Keyboard mode (QWERTY/Symbols/Fn)
core/KeyboardActionListener.kt       — Action listener interface
ui/keyboard/KeyView.kt               — Compose key press handler
ui/keyboard/KeyboardView.kt          — Full keyboard Compose layout
ui/keyboard/DevKeyKeyboard.kt        — Keyboard model
ui/keyboard/ComposeKeyboardViewFactory.kt  — Android View wrapping Compose
ui/keyboard/QwertyLayout.kt          — QWERTY layout definition
ui/keyboard/SymbolsLayout.kt         — Symbols layout definition
ui/keyboard/KeyboardMode.kt          — Mode enum
ui/keyboard/SessionDependencies.kt   — Per-session DI
ui/voice/VoiceInputPanel.kt          — Voice input Compose panel
feature/voice/VoiceInputEngine.kt    — Voice recording + inference
feature/voice/WhisperProcessor.kt    — TFLite Whisper wrapper
feature/prediction/PredictionEngine.kt  — Prediction orchestration
data/repository/SettingsRepository.kt  — Preferences access
WordComposer.kt                      — Composing word state
Suggest.kt                           — Suggestion engine
BinaryDictionary.kt                  — JNI dictionary bridge
CandidateView.kt                     — Suggestion bar (legacy View)
KeyboardSwitcher.kt                  — Layout switching
ChordeTracker.kt                     — Chord key combination tracking
debug/DevKeyLogger.kt                — Logging infrastructure
```
