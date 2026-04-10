# Pattern — Voice State Instrumentation

## Summary
`VoiceInputEngine` is fully state-transition-instrumented as of Session 42. Every mutation of `_state.value` is followed by a `DevKeyLogger.voice("state_transition", ...)` call carrying the new state name, source function, and optional reason/trigger/duration fields. Phase 2.3's voice round-trip flow can gate assertions entirely on these emitted events, eliminating sleeps.

## How we do it
- Every `_state.value = VoiceState.X` is paired with a `DevKeyLogger.voice("state_transition", ...)` on the very next line
- Payload includes `state` (the new state name), `source` (the enclosing function), and optional `reason` / `trigger` / `duration_ms` / `sample_count` fields
- Errors use a separate event type `"error"` with `kind` and `source` — kept distinct from state-transition events
- Terminal processing events use `"processing_complete"` with `result_length`, `duration_ms`, `source` — NO transcript content

## Exemplar 1 — state-transition emit (LISTENING)

**File**: `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt:180-181`

```kotlin
_state.value = VoiceState.LISTENING
DevKeyLogger.voice("state_transition", mapOf("state" to "LISTENING", "source" to "startListening"))
```

## Exemplar 2 — terminal processing event with metadata

**File**: `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt:291-298`

```kotlin
DevKeyLogger.voice(
    "processing_complete",
    mapOf(
        "result_length" to transcription.length,
        "duration_ms" to (System.currentTimeMillis() - processingStartMs),
        "source" to "stopListening"
    )
)
```

## Reusable methods table

| Event name | When | Payload keys |
|---|---|---|
| `state_transition` | Every `_state.value = X` assignment | `state`, `source`, optional `reason`, `trigger`, `sample_count`, `duration_ms` |
| `processing_complete` | Whisper inference succeeds | `result_length`, `duration_ms`, `source` |
| `error` | Exception caught or failure branch | `kind` (short error code), `source` |

## Required imports
```kotlin
import dev.devkey.keyboard.debug.DevKeyLogger
```

## Privacy invariant
- `result_length: Int` — the character count of the transcription — is the MAXIMUM information allowed about the transcript
- `duration_ms: Long` — timing, not content
- `sample_count: Int` — how many audio samples, not the audio itself
- The actual `transcription: String` is NEVER placed in the payload

## Full emit-site inventory

Already documented in `ground-truth.md#VoiceInputEngine state transitions`. Every site is verified against source.

## Phase 2.3 voice round-trip flow — how it uses these signals

```
Driver server:
  1. POST /clear (debug server)
  2. adb: tap voice button (KEYCODE_VOICE = -102)
  3. GET /wait?category=DevKey/VOX&event=state_transition&match.state=LISTENING&timeout=2000
  4. Inject audio via `adb shell media` or emulator audio pipe
  5. GET /wait?category=DevKey/VOX&event=state_transition&match.state=PROCESSING&timeout=10000
  6. GET /wait?category=DevKey/VOX&event=processing_complete&timeout=15000
  7. Read InputConnection via `dumpsys input_method` to verify text committed
  8. GET /wait?category=DevKey/VOX&event=state_transition&match.state=IDLE&match.reason=inference_complete&timeout=5000
  9. PASS if all waits succeed AND committed text is non-empty
```

**Zero sleeps**. Every delay is bounded by a `GET /wait` that either returns on the signal or times out.
