# Pattern: Voice Engine Integration

**Relevant to**: Phase 1.1, 1.6

## How we do it

`VoiceInputEngine` is a plain class (not a singleton) instantiated per-composition inside `DevKeyKeyboard` via `remember { VoiceInputEngine(context) }`. Its lifecycle is tied to the Compose tree via `DisposableEffect`. Audio state (`VoiceState` enum + `amplitude: Float`) is exposed as `StateFlow` and collected with `collectAsState()`. Transcription is committed back to the IME via the `KeyboardActionBridge.onText(String)` path — the same path plain-text macros use.

## Exemplar 1 — Instantiation + lifecycle + observation

`app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt:81-130`

```kotlin
val voiceInputEngine = remember { VoiceInputEngine(context) }

// Release voice engine resources when composable leaves composition
DisposableEffect(voiceInputEngine) {
    onDispose { voiceInputEngine.release() }
}

// ... later in the same composable ...
val voiceState by voiceInputEngine.state.collectAsState()
val voiceAmplitude by voiceInputEngine.amplitude.collectAsState()
```

## Exemplar 2 — Record → stop → commit

`app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt:157-249`

```kotlin
onVoice = {
    if (keyboardMode == KeyboardMode.Voice) {
        voiceInputEngine.cancelListening()
        modeManager.setMode(KeyboardMode.Normal)
    } else {
        modeManager.setMode(KeyboardMode.Voice)
        coroutineScope.launch { voiceInputEngine.startListening() }
    }
},
// ...
onStop = {
    coroutineScope.launch {
        val transcription = voiceInputEngine.stopListening()
        if (transcription.isNotEmpty()) {
            bridge.onText(transcription)
        }
    }
},
onCancel = {
    voiceInputEngine.cancelListening()
    modeManager.setMode(KeyboardMode.Normal)
}
```

## Reusable methods

| Method | Signature | Purpose |
|---|---|---|
| `startListening` | `suspend fun startListening()` | Begin audio capture. Must run in coroutine scope. |
| `stopListening` | `suspend fun stopListening(): String` | Stop capture, run inference, return transcribed text. |
| `cancelListening` | `fun cancelListening()` | Abort without running inference. |
| `release` | `fun release()` | Free TF Lite interpreter + AudioRecord. Called from DisposableEffect.onDispose. |
| `state` | `StateFlow<VoiceState>` | `IDLE / LISTENING / PROCESSING / ERROR` |
| `amplitude` | `StateFlow<Float>` | Live amplitude for waveform visualization. |

## Required imports

```kotlin
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
```

## Phase 1.1 / 1.6 implications

- Phase 1.1 does NOT touch any code — pure asset sourcing + `app/src/main/assets/` directory creation
- Phase 1.6 verification uses the existing integration as-is; no new wiring required
- `VoiceInputEngine.initialize()` (line 93-119) handles missing model gracefully — if asset files are missing, `modelLoaded = false`, exceptions are caught, recording still proceeds but inference is skipped. This is the expected state BEFORE Phase 1.1 completes.
