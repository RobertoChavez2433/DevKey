# Pattern: DevKey Logger HTTP Forwarding

**Relevant to**: Phase 1.6 voice E2E verification (uses logger to observe `VoiceInputEngine` state transitions deterministically)

## How we do it

`DevKeyLogger` is a Kotlin `object` (singleton) that wraps logcat writes with category tags and optionally POSTs structured JSON to an HTTP test harness when `serverUrl` is non-null. Call sites use category helpers (`ime`, `ui`, `modifier`, `text`, `native`, `voice`, `error`) + a `hypothesis` helper for labeled assertions.

## Exemplar — enableServer setter

`app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt:37-42`

```kotlin
fun enableServer(url: String) {
    serverUrl = url
}

fun disableServer() {
    serverUrl = null
}
```

## Category helpers (excerpt)

```kotlin
fun ime(message: String, data: Map<String, Any?> = emptyMap())
fun ui(message: String, data: Map<String, Any?> = emptyMap())
fun modifier(message: String, data: Map<String, Any?> = emptyMap())
fun text(message: String, data: Map<String, Any?> = emptyMap())
fun native(message: String, data: Map<String, Any?> = emptyMap())
fun voice(message: String, data: Map<String, Any?> = emptyMap())   // ← Phase 1.6 target
fun error(message: String, data: Map<String, Any?> = emptyMap())
fun hypothesis(id: String, category: Category, description: String, data: Map<String, Any?> = emptyMap())
```

## Phase 1.6 implications

- **Instrumentation target**: `VoiceInputEngine` currently uses `android.util.Log.*` directly (`Log.i(TAG, ...)`, `Log.w(TAG, ..., e)`). To enable HTTP-observable state transitions for the test harness, either:
  - (a) Route existing `Log.i` / `Log.w` calls through `DevKeyLogger.voice(...)` (minimal change), OR
  - (b) Add `DevKeyLogger.voice(...)` calls at every `_state.value = ...` assignment point without removing existing Log calls
- **Enablement**: Test harness must invoke `DevKeyLogger.enableServer("http://host:port")` once per test run. Currently there are **zero** call sites of `enableServer` in production code, so Phase 2 will need to wire this through an adb-visible entry point (deeplink intent, ContentProvider, or ADB debug bridge broadcast). **For Phase 1, manual enable via `adb shell am broadcast` to a new debug receiver is acceptable.** Details deferred to Phase 2 of the vision spec.
- **State enum mapping**: `VoiceInputEngine.VoiceState` → `IDLE, LISTENING, PROCESSING, ERROR`. All four need `DevKeyLogger.voice("state", mapOf("state" to state.name))` instrumentation for deterministic test-side assertions.

## Required imports

```kotlin
import dev.devkey.keyboard.debug.DevKeyLogger
```

## Phase 1 scope fence

Phase 1.6 only needs the logger to **exist and be callable**. Full test-harness integration (enablement from test runner, driver-server reception of forwarded logs, wave synchronization replacing sleeps) is Phase 2 per the vision spec. Do not pull Phase 2 work forward into Phase 1.
