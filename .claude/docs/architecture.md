# DevKey Architecture

## Component Diagram

```
┌─────────────────────────────────────────────────┐
│                   DevKey IME                     │
├──────────┬──────────┬───────────┬───────────────┤
│  Input   │    UI    │    AI     │   Platform    │
│  Engine  │  Layer   │  Engine   │   Services    │
├──────────┼──────────┼───────────┼───────────────┤
│ Key      │ Material │ On-device │ Voice-to-Text │
│ dispatch │ You      │ TF Lite   │ (Android STT) │
│ Modifier │ Theme    │ Word      │ Clipboard Mgr │
│ state    │ Engine   │ predict   │ App Context   │
│ IME      │ Keyboard │ Command   │ Detection     │
│ service  │ Modes    │ predict   │ Settings/     │
│ (C++ NDK)│ (Compose)│ Learning  │ Preferences   │
└──────────┴──────────┴───────────┴───────────────┘
```

## Data Flow

```
User input → InputMethodService
  → Key dispatch (C++ NDK for performance)
  → Modifier state machine (Ctrl, Alt, Esc, Tab)
  → If command mode: Command prediction engine (trie + n-gram)
  → If text mode: Word prediction engine (TF Lite + dictionary)
  → Prediction bar update (Compose)
  → Text commit to app
```

## Threading Model

| Operation | Dispatcher | Notes |
|-----------|-----------|-------|
| Key dispatch | Main | Must be synchronous for IME |
| Prediction lookup | Default | CPU-bound, TF Lite inference |
| Dictionary search | Default | C++ NDK via JNI |
| Voice recognition | IO | Android SpeechRecognizer |
| Clipboard operations | IO | Room database |
| Theme rendering | Main | Compose UI |
| Command learning | IO | Room database writes |

## Key Modules

### Input Engine (inherited from Hacker's Keyboard)
- InputMethodService subclass
- Key dispatch pipeline
- Modifier key state machine (multitouch)
- 5-row layout engine
- Language layout switching

### UI Layer (new — Jetpack Compose)
- Material You dynamic theming
- Custom theme engine
- Keyboard mode switching (Full/One-Handed/Floating/Split/Power)
- Toolbar with configurable actions
- Settings hub

### AI Engine (new — TensorFlow Lite)
- Word prediction model (on-device)
- Command prediction (trie + n-gram)
- Personal learning (Room database)
- Emoji prediction
- Context-aware autocorrect

### Platform Services (new)
- Voice-to-text (Android SpeechRecognizer)
- Clipboard manager (Room database, 50 clips)
- App context detection (package name matching)
- Incognito mode

## Implementation Phases

1. **Foundation**: Fix Android 14/15 compat, Java→Kotlin migration, API 34+, basic Material You
2. **Modern UX**: Theme engine, keyboard modes, clipboard, toolbar, haptics
3. **Intelligence**: AI prediction, command-aware autocorrect, voice-to-text
4. **Personalization**: Advanced learning, multi-language, cloud backup, accessibility
