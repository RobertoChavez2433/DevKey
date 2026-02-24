# Session 4 Design: Voice-to-Text, Command Mode & Autocorrect

**Date**: 2026-02-23
**Status**: Approved
**Depends on**: Session 3 (Macros, Ctrl Mode, Clipboard) — complete

## Overview

Session 4 adds three capabilities: Whisper-powered voice-to-text (the primary feature), command-aware mode for terminal apps, and lightweight dictionary-based autocorrect. No heavy ML models for prediction — voice is the only TF Lite consumer.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Prediction approach | Dictionary + n-gram (no TF Lite) | User doesn't rely on next-word prediction; lightweight and ships immediately |
| Autocorrect level | Standard (completion + basic correction) | Dictionary completion + edit-distance typo correction, configurable |
| Voice model | Whisper tiny.en (~40MB), bundled in APK | Works offline immediately, no download step needed |
| Voice model delivery | Bundled in assets/ | Larger APK (~50MB total) but zero setup friction |
| Command mode | Full auto-detect + manual toggle | Auto-detect terminal apps by package name, manual override in toolbar |

---

## 1. Whisper Voice-to-Text

### Architecture

Uses the [whisper.tflite](https://github.com/nyadla-sys/whisper.tflite) pre-converted `whisper-tiny.en.tflite` model (~40MB), bundled in `assets/`. Model loads lazily on first mic tap (not at IME startup).

### Components

- **`VoiceInputEngine`** — loads TF Lite interpreter, manages AudioRecord, runs inference
- **`WhisperProcessor`** — handles mel spectrogram conversion (16kHz mono PCM -> 80-bin mel spectrogram -> model input)
- **`VoiceInputPanel`** — Compose UI replacing the keyboard when mic is active

### Flow

1. User taps mic in toolbar
2. Keyboard view swaps to `VoiceInputPanel` (pulsing mic icon, waveform visualization, stop button)
3. `AudioRecord` captures 16kHz mono PCM audio
4. On stop (manual tap or silence detection after 2s), audio is processed into mel spectrogram
5. TF Lite interpreter runs inference on background coroutine
6. Transcription text inserted into InputConnection
7. Keyboard view returns to normal

### Audio Capture

`AudioRecord` API (not MediaRecorder — Whisper needs raw PCM). Requires `RECORD_AUDIO` permission with runtime request.

### Silence Detection

Simple amplitude threshold — if RMS drops below threshold for 2 consecutive seconds, auto-stop.

### Permission Handling

`RECORD_AUDIO` is a dangerous permission requiring runtime request. Since IMEs can't launch `ActivityResultContract` directly, a small transparent `PermissionActivity` requests the permission and returns the result via broadcast/callback.

---

## 2. Dictionary Completion + Basic Autocorrect

### Architecture

Two-layer system: existing C++ `liblatinime` dictionary for word lookup/completion, plus a lightweight Kotlin autocorrect engine on top.

### Components

- **`PredictionEngine`** — orchestrates completions and corrections, feeds SuggestionBar
- **`AutocorrectEngine`** — compares typed word against dictionary, suggests corrections
- **`DictionaryProvider`** — Kotlin wrapper around `BinaryDictionary` JNI bridge
- **`LearningEngine`** — tracks typed words in `learned_words` table, boosts future suggestions

### SuggestionBar Wiring

- Replaces hardcoded `listOf("the", "I", "and")` in DevKeyKeyboard
- Slot 1: autocorrect candidate (bold), Slots 2-3: completions/alternatives
- Tapping a suggestion commits it + space
- Data source: `PredictionEngine.predict(currentWord, precedingText): List<String>`

### Autocorrect Logic

- On spacebar press, compare just-typed word against dictionary
- If edit distance <= 2 and single high-confidence match, auto-apply (aggressive) or suggest (mild)
- If word is in `learned_words`, never autocorrect it
- Settings: aggressive (auto-apply) / mild (suggest only, default) / off

### Learned Words

- On word commit (spacebar, punctuation, suggestion tap), store word + increment frequency
- In command mode, words stored with `is_command = true`
- Learned words boost suggestion ranking (frequency-weighted)

---

## 3. Command-Aware Mode

### Architecture

Detector checks current app's package name against known list + user overrides, plus behavioral changes when active.

### Components

- **`CommandModeDetector`** — checks package on `onStartInput()`, returns `InputMode.NORMAL` or `InputMode.COMMAND`
- **`CommandModeRepository`** — Room-backed CRUD for user-pinned apps (wraps `CommandAppDao`)
- **`CommandModeToggle`** — manual override in toolbar overflow menu

### Auto-Detection List

```
com.termux, org.connectbot, com.sonelli.juicessh,
com.server.auditor, com.offsec.nethunter,
jackpal.androidterm, yarolegovich.materialterminal,
com.termoneplus, com.googlecode.android_scripting
```

### Detection Flow

1. `LatinIME.onStartInputView()` gets `EditorInfo` with package name
2. `CommandModeDetector.detect(packageName)` checks: user override > auto-detect list > default NORMAL
3. Result stored in `StateFlow<InputMode>` observed by keyboard UI

### Behavioral Changes in Command Mode

- Autocorrect OFF
- Auto-capitalize OFF
- Suggestion bar sources from command history (`learned_words WHERE is_command = true`)
- Case sensitivity preserved exactly as typed
- Visual indicator: small "CMD" badge in toolbar/suggestion bar area

### Manual Toggle

Button in toolbar overflow menu — "Command Mode: ON/OFF". Overrides auto-detection for current input session. Resets on app switch.

### User-Pinned Apps

`command_apps` table and repository wired, but settings UI deferred to Session 5.

---

## 4. Integration & Wiring

### DevKeyKeyboard.kt Changes

- Add `PredictionEngine`, `CommandModeDetector`, `VoiceInputEngine` to `remember {}` block
- Replace hardcoded `defaultSuggestions` with live `PredictionEngine` output
- Add `KeyboardMode.Voice` to sealed class
- Wire toolbar mic button (`onVoice`) — currently no-op
- Observe `CommandModeDetector.inputMode` StateFlow for autocorrect toggling

### LatinIME.java Changes

- In `onStartInputView()`: call `CommandModeDetector.detect(attribute.packageName)`
- In `onCreateInputView()`: pass detector instance to ComposeKeyboardViewFactory
- Add `RECORD_AUDIO` permission request flow (lazy on first mic tap)

### New KeyboardMode Variant

```kotlin
sealed class KeyboardMode {
    // existing: Normal, MacroChips, MacroGrid, MacroRecording, Clipboard, Symbols
    data object Voice : KeyboardMode()  // NEW
}
```

### SuggestionBar

No changes needed — already takes `suggestions: List<String>` and `onSuggestionClick`. Data source changes upstream in DevKeyKeyboard.

---

## 5. Testing

### Unit Tests

- `PredictionEngineTest` — completions for prefix, respects learned words, empty for unknown
- `AutocorrectEngineTest` — corrects "teh" -> "the", skips learned words, respects aggressiveness
- `CommandModeDetectorTest` — detects Termux/ConnectBot, NORMAL for unknown, user override wins
- `LearningEngineTest` — stores words, increments frequency, separates commands from normal
- `VoiceInputEngineTest` — mel spectrogram dimensions, silence detection triggers
- `CommandModeRepositoryTest` — CRUD on command_apps table

### Integration Tests (On-Device)

- Predictions appear while typing
- Command mode activates in Termux
- Voice: tap mic -> UI swaps -> stop -> text inserted
- Autocorrect applies on spacebar, skips in command mode

### Not Tested in Code

- Whisper model accuracy (empirical, device testing)
- Audio recording quality (hardware-dependent)

---

## New Files Summary

| File | Purpose |
|------|---------|
| `feature/voice/VoiceInputEngine.kt` | TF Lite Whisper inference, AudioRecord management |
| `feature/voice/WhisperProcessor.kt` | PCM -> mel spectrogram conversion |
| `feature/voice/SilenceDetector.kt` | RMS-based silence detection |
| `feature/prediction/PredictionEngine.kt` | Orchestrates completions + corrections |
| `feature/prediction/AutocorrectEngine.kt` | Edit-distance typo correction |
| `feature/prediction/DictionaryProvider.kt` | Kotlin wrapper for BinaryDictionary JNI |
| `feature/prediction/LearningEngine.kt` | Learned words tracking + frequency |
| `feature/command/CommandModeDetector.kt` | Package-based terminal app detection |
| `feature/command/CommandModeRepository.kt` | Room-backed command app CRUD |
| `feature/command/InputMode.kt` | NORMAL/COMMAND enum |
| `ui/voice/VoiceInputPanel.kt` | Voice recording Compose UI |
| `ui/voice/PermissionActivity.kt` | Transparent activity for RECORD_AUDIO permission |

### Modified Files

| File | Changes |
|------|---------|
| `ui/keyboard/KeyboardMode.kt` | Add `Voice` variant |
| `ui/keyboard/DevKeyKeyboard.kt` | Wire prediction, command mode, voice; replace hardcoded suggestions |
| `ui/toolbar/ToolbarRow.kt` | Add CMD badge, wire overflow menu command toggle |
| `core/LatinIME.java` | Command mode detection in onStartInputView, permission flow |
| `gradle/libs.versions.toml` | Add tflite-gpu (optional), verify tflite deps |
| `app/build.gradle.kts` | Add tflite dependencies, aaptOptions for .tflite |
| `AndroidManifest.xml` | Add RECORD_AUDIO permission, PermissionActivity |

---

## References

- [whisper.tflite](https://github.com/nyadla-sys/whisper.tflite) — optimized Whisper TFLite models
- [whisper_android](https://github.com/vilassn/whisper_android) — reference Android implementation
- [tflite-android-transformers](https://github.com/huggingface/tflite-android-transformers) — HuggingFace TFLite examples (archived)
- [Federated Learning for Mobile Keyboard Prediction](https://arxiv.org/pdf/1811.03604) — CIFG-LSTM architecture reference
