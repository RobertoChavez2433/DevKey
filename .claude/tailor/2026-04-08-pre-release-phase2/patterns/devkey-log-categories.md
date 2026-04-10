# Pattern — DevKey Log Categories

## Summary
DevKey's structured logging uses a closed enum of 8 categories. Each category has a string tag (`DevKey/<SHORTCODE>`) that doubles as the logcat tag and the HTTP `category` field. The driver server (Phase 2.2) must know every category and, critically, must also know that some legacy logcat emitters (`DevKeyPress`, `DevKeyMode`, `DevKeyMap`, `DevKeyBridge`) do NOT flow through DevKeyLogger and therefore do NOT reach the HTTP server.

## How we do it
- Enum values carry their tag string as a constructor parameter
- Category convenience methods on the `object` singleton take `(message, data)` and route to `log(Category.X, ...)`
- Payload `data: Map<String, Any?>` must contain ONLY structural fields (lengths, counts, enum names, booleans) — never text content

## Exemplar 1 — Category enum

**File**: `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt:22-31`

```kotlin
enum class Category(val tag: String) {
    IME_LIFECYCLE("DevKey/IME"),
    COMPOSE_UI("DevKey/UI"),
    MODIFIER_STATE("DevKey/MOD"),
    TEXT_INPUT("DevKey/TXT"),
    NATIVE_JNI("DevKey/NDK"),
    VOICE("DevKey/VOX"),
    BUILD_TEST("DevKey/BLD"),
    ERROR("DevKey/ERR"),
}
```

## Exemplar 2 — Category method (one per enum)

**File**: `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt:63-67`

```kotlin
fun voice(message: String, data: Map<String, Any?> = emptyMap()) =
    log(Category.VOICE, message, data)
```

## Full tag inventory (plan author reference)

### DevKeyLogger-emitted tags (HTTP-forwarded)

| Category enum | Tag string | Intended use |
|---|---|---|
| `IME_LIFECYCLE` | `DevKey/IME` | `onCreate`, `onDestroy`, `onStartInput`, `onFinishInput`, service scope events |
| `COMPOSE_UI` | `DevKey/UI` | Compose recomposition, bridge events, key view lifecycle |
| `MODIFIER_STATE` | `DevKey/MOD` | Modifier transitions (intended for Phase 2+) — **currently unused** |
| `TEXT_INPUT` | `DevKey/TXT` | InputConnection events, cursor, suggestions — privacy: lengths only |
| `NATIVE_JNI` | `DevKey/NDK` | BinaryDictionary JNI bridge |
| `VOICE` | `DevKey/VOX` | VoiceInputEngine state machine — fully instrumented Session 42 |
| `BUILD_TEST` | `DevKey/BLD` | Build/test markers |
| `ERROR` | `DevKey/ERR` | Error surface |

### Legacy direct-`Log.d` tags (NOT HTTP-forwarded)

| Tag | Emitter | Phase 2 implication |
|---|---|---|
| `DevKeyPress` | `KeyPressLogger.kt:10` — every key tap, every long-press | Phase 2.3 long-press flow still needs this — plan must either scrape ADB logcat server-side OR migrate KeyPressLogger to DevKeyLogger |
| `DevKeyMode` | `KeyboardModeManager.kt:36` — mode toggles | Phase 2.4 mode-switching flow needs this — same decision |
| `DevKeyMap` | `KeyMapGenerator.kt:27` — key map dump | Phase 2.4 calibration reads this — stays ADB-side |
| `DevKeyBridge` | (documented in test-orchestrator-agent.md:252) | Not directly used by Phase 2 flows |

### Driver-server dual-source strategy (plan decision)

The driver server must observe signals from BOTH sources. Two architectures:

**Strategy A — HTTP-only** (clean but requires code changes)
- Migrate `KeyPressLogger.logPress` / `logLongPress` and `KeyboardModeManager.Log.d("DevKeyMode", ...)` to call `DevKeyLogger.modifier(...)` or `DevKeyLogger.ui(...)`
- Driver server filters on HTTP `category` field
- Pro: One observation point
- Con: Migration churn in Session 42-landed code; risk of regressing the existing `/test` Claude-agent flows that scrape legacy tags

**Strategy B — Dual-source** (defer churn)
- Driver server spawns `adb logcat -s DevKeyPress DevKeyMode DevKeyMap DevKeyBridge *:S` and merges output with HTTP log stream in a single event queue
- Pro: Zero churn in app code
- Con: Two observation paths; harder to reason about ordering

**Recommendation**: Strategy B for initial Phase 2.1, with migration to Strategy A as an optional cleanup inside Phase 2.4 stabilization (since mode-switching is the noisiest legacy tag and benefits most from HTTP forwarding).

## Reusable methods table

| Method | Signature | Use |
|---|---|---|
| `DevKeyLogger.log(category, msg, data)` | private | Core emit — don't call directly |
| `DevKeyLogger.ime(msg, data)` | public | `DevKey/IME` |
| `DevKeyLogger.ui(msg, data)` | public | `DevKey/UI` |
| `DevKeyLogger.modifier(msg, data)` | public | `DevKey/MOD` |
| `DevKeyLogger.text(msg, data)` | public | `DevKey/TXT` — privacy-sensitive |
| `DevKeyLogger.native(msg, data)` | public | `DevKey/NDK` |
| `DevKeyLogger.voice(msg, data)` | public | `DevKey/VOX` |
| `DevKeyLogger.error(msg, data)` | public | `DevKey/ERR` |
| `DevKeyLogger.hypothesis(id, cat, desc, data)` | public | Session-scoped debug marker |

## Required imports
```kotlin
import dev.devkey.keyboard.debug.DevKeyLogger
```

## Privacy contract (MUST NOT violate in Phase 2)

- Never log typed text, transcripts, audio samples, clipboard content, password field content
- `text()` and `voice()` are the two most dangerous categories — emit lengths, counts, state names, error kinds ONLY
- Session 42 voice instrumentation was privacy-audited — every call site confirmed integer-only payloads (`result_length`, `duration_ms`, `sample_count`). Phase 2's `next_word_suggestions` instrumentation must meet the same bar (`prev_word_length`, `result_count` — never the words themselves)
