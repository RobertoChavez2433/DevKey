# Pattern — HTTP Log Forwarding

## Summary
DevKey already has a single-producer HTTP logging pipeline in `DevKeyLogger`. Every category method (`ime`, `ui`, `modifier`, `text`, `native`, `voice`, `error`, `hypothesis`) routes through `log()` → `sendToServer()`, which POSTs JSON to `$serverUrl/log` if a URL is set. The machinery is self-contained, swallows errors, and never blocks the caller.

## How we do it
- Single `@Volatile var serverUrl: String?` guards all forwarding — null means "logcat only", non-null means "logcat + HTTP".
- Forwarding is fire-and-forget on a background `CoroutineScope(Dispatchers.IO)`.
- 1-second connect/read timeouts keep the IME responsive if the server disappears.
- JSON serialization is manual via `org.json.JSONObject` (no Moshi, no kotlinx.serialization here, no new deps).
- Every exception in `sendToServer` is silently swallowed: debug logging must NEVER crash the IME.

## Exemplar 1 — `DevKeyLogger.enableServer` + `sendToServer`

**Symbol**: `DevKeyLogger.sendToServer`
**File**: `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt:91-119`

```kotlin
@Volatile private var serverUrl: String? = null
private val scope = CoroutineScope(Dispatchers.IO)

fun enableServer(url: String) {
    serverUrl = url
}

fun disableServer() {
    serverUrl = null
}

private fun sendToServer(
    category: String,
    message: String,
    data: Map<String, Any?>,
    hypothesisId: String? = null
) {
    val url = serverUrl ?: return
    scope.launch {
        try {
            val json = JSONObject().apply {
                put("category", category)
                put("message", message)
                put("data", JSONObject(data.mapValues { it.value?.toString() }))
                hypothesisId?.let { put("hypothesis", it) }
            }
            val conn = URL("$url/log").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            conn.responseCode // trigger send
            conn.disconnect()
        } catch (_: Exception) {
            // Silent fail — debug logging must never crash the IME
        }
    }
}
```

## Exemplar 2 — Call site pattern (how Phase 1 voice instrumentation looks)

**Symbol**: `VoiceInputEngine.startListening` (state-transition emit)
**File**: `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt:181`

```kotlin
_state.value = VoiceState.LISTENING
DevKeyLogger.voice(
    "state_transition",
    mapOf("state" to "LISTENING", "source" to "startListening")
)
```

**Key properties** any new Phase 2 emit site must preserve:
1. The second argument is an `"event_name"` string, not free text
2. The third argument is a `Map<String, Any?>` with integer / short-string values only
3. **NO** user-typed content, transcript, audio samples, or buffer bytes — ever
4. Call site is inside the state transition, not outside, so the event represents ground truth

## Reusable methods table

| Method | Signature | Purpose |
|---|---|---|
| `DevKeyLogger.enableServer(url)` | `fun enableServer(url: String)` | Turn on HTTP forwarding |
| `DevKeyLogger.disableServer()` | `fun disableServer()` | Turn off HTTP forwarding |
| `DevKeyLogger.ime(msg, data)` | `fun ime(String, Map<String, Any?> = emptyMap())` | Log to `DevKey/IME` category |
| `DevKeyLogger.ui(msg, data)` | ditto | `DevKey/UI` |
| `DevKeyLogger.modifier(msg, data)` | ditto | `DevKey/MOD` |
| `DevKeyLogger.text(msg, data)` | ditto | `DevKey/TXT` — privacy-sensitive, lengths/counts only |
| `DevKeyLogger.native(msg, data)` | ditto | `DevKey/NDK` |
| `DevKeyLogger.voice(msg, data)` | ditto | `DevKey/VOX` |
| `DevKeyLogger.error(msg, data)` | ditto | `DevKey/ERR` |
| `DevKeyLogger.hypothesis(id, cat, desc, data)` | `fun hypothesis(String, Category, String, Map<String, Any?>)` | Session-scoped debug marker — MUST be removed after session |

## Required imports (for adding a new emit site)

```kotlin
import dev.devkey.keyboard.debug.DevKeyLogger
```

That's it — no other imports needed. The category methods hide everything.

## Delivery mechanisms for `enableServer(url)` (plan decision — 3 options)

Phase 2.1 must invent a way to push the server URL into the running IME process. Three options, ordered by increasing friction:

### Option A — Broadcast intent (RECOMMENDED)

Add a new debug-only broadcast receiver next to the existing `DUMP_KEY_MAP` receiver at `LatinIME.kt:404-422`:

```kotlin
if (KeyMapGenerator.isDebugBuild(this)) {
    enableDebugServerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val url = intent.getStringExtra("url")
            if (url.isNullOrBlank()) {
                DevKeyLogger.disableServer()
            } else {
                DevKeyLogger.enableServer(url)
            }
        }
    }
    registerReceiver(
        enableDebugServerReceiver,
        IntentFilter("dev.devkey.keyboard.ENABLE_DEBUG_SERVER"),
        Context.RECEIVER_NOT_EXPORTED
    )
}
```

**Invocation from the test harness**:
```bash
adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://10.0.2.2:3947
```

(`10.0.2.2` is the emulator host-loopback alias — for physical devices, use the host IP via `adb reverse tcp:3947 tcp:3947`.)

**Pros**: Mirrors existing `DUMP_KEY_MAP` pattern exactly. No state outside the IME process. Cleanup on service destroy is automatic.
**Cons**: Broadcast must be re-sent every time the IME process restarts. The test runner must re-send it after every `ime set`.

### Option B — SharedPreference + onCreate read

Write the URL to a SharedPreference via `adb shell cmd stats put` or via a debug-only Settings activity. Read once in `LatinIME.onCreate()`.

**Pros**: Persists across IME restarts.
**Cons**: New preference key to manage, harder to clear between test runs, risk of leaking into production build via default value.

### Option C — System property

Read `android.os.SystemProperties.get("devkey.debug.server")` at IME boot.

**Pros**: Survives process restart.
**Cons**: Requires root or `adb shell setprop` with sepolicy complications on API 28+. Not viable on unrooted physical devices. **Reject.**

**Recommendation**: Option A. The plan should document the `adb reverse tcp:3947 tcp:3947` setup as a prerequisite step for physical-device testing.
