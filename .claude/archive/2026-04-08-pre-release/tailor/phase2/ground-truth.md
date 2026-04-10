# Ground Truth — Phase 2

All constants, tag strings, intents, enum values, file paths, and API signatures that the plan will depend on. Sourced directly from the files listed — not from the spec.

## DevKeyLogger (the HTTP client the driver server receives)

**File**: `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`
**Shape**: Kotlin `object` (singleton)

### Category enum (logcat tag strings the driver server filters on)

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

**Verified**: DevKeyLogger.kt:22-31. Tags are `DevKey/<SHORTCODE>` — note that `.claude/skills/test/references/ime-testing-patterns.md` documents `DevKeyPress` / `DevKeyMode` / `DevKeyMap` / `DevKeyBridge` as logcat tags for the existing test skill. **These are SEPARATE tags, emitted by `KeyPressLogger`, `Log.d("DevKeyMode", ...)` in `KeyboardModeManager.kt:36`, and `KeyMapGenerator.kt:27` — they do NOT route through `DevKeyLogger`**. The driver server must filter on **both** sets:

| Source | Tag | Emitter |
|---|---|---|
| DevKeyLogger | `DevKey/IME`, `DevKey/UI`, `DevKey/MOD`, `DevKey/TXT`, `DevKey/NDK`, `DevKey/VOX`, `DevKey/BLD`, `DevKey/ERR` | `DevKeyLogger.kt` |
| Legacy direct `Log.d` | `DevKeyPress` | `KeyPressLogger.kt:16` |
| Legacy direct `Log.d` | `DevKeyMode` | `KeyboardModeManager.kt:36` |
| Legacy direct `Log.d` | `DevKeyMap` | `KeyMapGenerator.kt:27` |
| Legacy direct `Log.d` | `DevKeyBridge` | (documented in test-orchestrator-agent.md:252) |

**Implication for 2.1**: Only `DevKeyLogger`-emitted events go through HTTP. Legacy tags (`DevKeyPress`, `DevKeyMode`, `DevKeyMap`, `DevKeyBridge`) still require `adb logcat -d -s <tag>` polling unless the plan also adds `DevKeyLogger.forward(...)` wrappers at their emit sites. **Plan decision needed**: migrate legacy emit sites to `DevKeyLogger`, or have the driver server merge ADB-logcat and HTTP sources.

### HTTP client API

```kotlin
@Volatile private var serverUrl: String? = null
private val scope = CoroutineScope(Dispatchers.IO)

fun enableServer(url: String) { serverUrl = url }
fun disableServer() { serverUrl = null }
```

**Verified**: DevKeyLogger.kt:33-43. `enableServer(url)` is currently **never called anywhere in production code** (verified via `search_text "enableServer"` — 1 result, which is the declaration line).

### POST payload shape (what the driver server receives)

```json
{
  "category": "DevKey/VOX",
  "message": "state_transition",
  "data": {"state": "LISTENING", "source": "startListening"},
  "hypothesis": "H001"
}
```

- Endpoint: `POST $serverUrl/log`
- Content-Type: `application/json`
- Timeouts: `connectTimeout = 1000`, `readTimeout = 1000` (both ms)
- Errors silently swallowed (`catch (_: Exception) {}`). **Implication**: driver server outages never crash the IME but also never surface back to the test runner. The test runner must independently verify the HTTP endpoint is reachable before every run.

**Verified**: DevKeyLogger.kt:91-119.

## Existing debug server (`tools/debug-server/server.js`)

**File**: `tools/debug-server/server.js` (85 lines)
**Runtime**: Node HTTP, no dependencies
**Port**: `3947` (const `PORT`)
**Max entries**: `30000` (const `MAX_ENTRIES` — ring buffer, newest wins)
**Bind**: `127.0.0.1` only (loopback)

### Endpoints (all implemented)

| Method | Path | Purpose |
|---|---|---|
| POST | `/log` | Receive log entry (JSON body). Writes `entry.ts = new Date().toISOString().slice(11, 23)` and pushes. |
| GET | `/health` | Returns `{status, entries, maxEntries, memoryMB, uptimeSeconds}` |
| GET | `/logs?last=N&category=X&hypothesis=H` | Filtered tail; `last` default 50 |
| GET | `/categories` | Returns `{category: count}` map |
| POST | `/clear` | Drops buffer, returns `{cleared: N}` |

**Verified**: server.js:10-80.

**Phase 2 extension gap**: This server receives logs but does not:
- Coordinate test waves (no wave-gating logic)
- Read ADB for legacy tags (`DevKeyPress`, `DevKeyMode`)
- Expose a wait-for-signal endpoint (e.g., `GET /wait?category=DevKey/VOX&message=state_transition&match={state:LISTENING}&timeout=5000`)
- Dispatch test runners
- Replace sleeps

These gaps **are** the test driver server (spec item 2.2).

## KEYCODE_VOICE and voice button path

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt:148`

```kotlin
const val KEYCODE_VOICE = -102
```

**Handler path**:
- `app/src/main/java/dev/devkey/keyboard/LatinIME.kt:1302` — `KeyCodes.KEYCODE_VOICE -> { ... }` in the `onKey` dispatch
- `app/src/main/java/dev/devkey/keyboard/LatinKeyboard.kt:414` — `key.codes = intArrayOf(KeyCodes.KEYCODE_VOICE)`

**Voice button visibility**:
```kotlin
// LatinIME.kt:817
private fun shouldShowVoiceButton(attribute: EditorInfo): Boolean = true
```

**Known defect** (pre-existing, not Phase 2 scope): returns `true` unconditionally, so voice button appears on password fields. Tracked in Phase 1 regression-smoke checklist, not blocking Phase 2. See `.claude/test-flows/phase1-voice-verification.md` note.

**Implication for Phase 2.3 voice round-trip flow**: the flow tests `KEYCODE_VOICE = -102` specifically, and observes `DevKey/VOX` category events to gate assertions.

## VoiceInputEngine state transitions (already instrumented)

**File**: `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt`

### VoiceState enum (VoiceInputEngine.kt:49-58)

```kotlin
enum class VoiceState { IDLE, LISTENING, PROCESSING, ERROR }
```

### Instrumented emit sites (all via `DevKeyLogger.voice(...)`)

| Line | Event | Data payload fields (keys only) |
|---|---|---|
| 117 | `"error"` | `kind="model_missing"`, `source="initialize"` |
| 141 | `"state_transition"` | `state`, `source`, `reason="permission_denied"` |
| 166 | `"state_transition"` | `state`, `source`, `reason="audiorecord_init_failed"` |
| 172 | `"state_transition"` | `state`, `source`, `reason="audiorecord_exception"` |
| 181 | `"state_transition"` | `state="LISTENING"`, `source="startListening"` |
| 212 | `"state_transition"` | `state="PROCESSING"`, `source="recordingLoop"`, `trigger="silence_detected"`, `sample_count` |
| 227 | `"state_transition"` | `state="PROCESSING"`, `source="stopListening"` |
| 236 | `"error"` | `kind="audiorecord_stop_failed"`, `source="stopListening"` |
| 251 | `"state_transition"` | `state="IDLE"`, `source="stopListening"`, `reason="empty_audio"` |
| 261 | `"state_transition"` | `state="IDLE"`, `source="stopListening"`, `reason="model_unavailable"` |
| 272 | `"state_transition"` | `state="IDLE"`, `source="stopListening"`, `reason="audio_processing_failed"` |
| 291 | `"processing_complete"` | `result_length`, `duration_ms`, `source` |
| 299 | `"state_transition"` | `state="IDLE"`, `source="stopListening"`, `reason="inference_complete"` |
| 307 | `"error"` | `kind="inference_failed"`, `source="stopListening"` |
| 311 | `"state_transition"` | `state="IDLE"`, `source`, `reason="inference_exception"`, `duration_ms` |
| 341 | `"state_transition"` | `state="IDLE"`, `source="cancelListening"` |

**Privacy contract**: NO transcript, audio samples, or buffer bytes ever appear in the payload. `processing_complete` carries only the integer `result_length` and `duration_ms`. This is a load-bearing invariant from the Session 42 privacy audit — the Phase 2 driver server MUST NOT log-scrape for transcript content either.

## Key coordinate calibration (already in place)

### Broadcast intent (LatinIME.kt:417-421)

```kotlin
registerReceiver(
    keyMapDumpReceiver,
    IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"),
    Context.RECEIVER_NOT_EXPORTED
)
```

**Receiver**: debug-builds-only, gated on `KeyMapGenerator.isDebugBuild(this)` at LatinIME.kt:404. Reads `SettingsRepository.KEY_LAYOUT_MODE` to pick `LayoutMode.COMPACT` / `COMPACT_DEV` / `FULL`, then calls `KeyMapGenerator.dumpToLogcat(context, null, mode)`.

**Invocation**: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`

### DevKeyMap logcat format (KeyMapGenerator.kt:181-196)

```
DevKeyMap: === DevKey Key Map ===
DevKeyMap: screen=1080x2340 density=3.0 mode=FULL
DevKeyMap: keyboard_view_top=1456
DevKeyMap: KEY label=a code=97 x=54 y=1775
...
DevKeyMap: === End Key Map (42 keys) ===
```

**Implication for Phase 2**: the driver server can issue the broadcast and parse `KEY label=X code=N x=P y=Q` lines directly instead of maintaining a separate coordinate table. This matches what `tools/e2e/lib/keyboard.py:load_key_map` already does (tested, working).

### Layout mode preference

**File**: `app/src/main/java/dev/devkey/keyboard/data/repository/SettingsRepository.kt`
**Key name**: `SettingsRepository.KEY_LAYOUT_MODE` (exact constant name)
**Values**: `"full"` / `"compact"` / `"compact_dev"` (verified LatinIME.kt:407-413)
**Default**: `"full"`

**Implication for 2.4**: switching between COMPACT / COMPACT_DEV / FULL is a preference write. The driver server can drive it via:
- `adb shell am startservice ...` (not supported — no service exposes it)
- `adb shell settings put` (wrong — Android Settings.Global, not SharedPreferences)
- **Intent broadcast** added specifically for test runs (e.g., `dev.devkey.keyboard.SET_LAYOUT_MODE` with `extra_mode=compact`) — mirrors `DUMP_KEY_MAP` pattern, debug-only

**Plan decision needed**: add a debug-only layout-mode broadcast receiver OR use the existing Settings UI automation via UIAutomator.

## Next-word prediction path (Phase 2.3 next-word flow will exercise this)

**File**: `app/src/main/java/dev/devkey/keyboard/LatinIME.kt:1933-1960`

```kotlin
private fun setNextSuggestions() {
    val suggestImpl = mSuggest
    if (suggestImpl != null && isPredictionOn()) {
        val prevWord = getLastCommittedWordBeforeCursor()
        if (prevWord != null) {
            val nextWords = suggestImpl.getNextWordSuggestions(prevWord)
            if (nextWords.isNotEmpty()) {
                setSuggestions(nextWords, ..., haveMinimalSuggestion = false)
                return
            }
        }
    }
    setSuggestions(mSuggestPuncList, ...)
}
```

**Callsites**: LatinIME.kt:905, 960, 1687, 1809, 1839, 1889, 2539 — `setNextSuggestions()` is invoked from the typed-character path, swipe-action path, and multiple reset paths.

**No instrumentation on this path** — the plan must add `DevKeyLogger.text("next_word_suggestions", mapOf("prev_word_length" to N, "result_count" to M))` (length and count only, NOT the words themselves) so the driver server can gate the Phase 2.3 next-word flow on an observable signal instead of a sleep.

**Privacy**: word CONTENT must not enter the log payload — only the length of `prevWord` and the count of results. This mirrors the voice-instrumentation rule from Session 42.

## Long-press popup data (Phase 2.3 long-press coverage flow)

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt:37-58`

```kotlin
data class KeyData(
    ...
    val longPressCode: Int? = null,       // primary long-press keycode
    val longPressCodes: List<Int>? = null // multi-char popup list
)
```

**Emitter**: `KeyPressLogger.logLongPress(label, code, longPressCode)` at `KeyPressLogger.kt:16` — emits `DevKeyPress: LONG label=X code=N lpCode=M`.

**Phase 2.3 long-press coverage flow** must:
1. Enumerate every key across FULL / COMPACT / COMPACT_DEV and both SYMBOLS / FN pages
2. Long-press each key via `adb shell input swipe X Y X Y 500`
3. Assert `DevKeyPress: LONG label=... lpCode=<expected>` via logcat (or HTTP if the plan migrates KeyPressLogger to DevKeyLogger)
4. For multi-char popups (`longPressCodes != null`), screenshot the popup and visual-diff against SwiftKey reference

**Ground-truth source for expected popup content**: `QwertyLayout.kt` and `SymbolsLayout.kt` — every `longPressCode` / `longPressCodes` entry is the oracle.

## `toggleMode` / KeyboardModeManager (Phase 2.4 mode-switching stabilization)

**File**: `app/src/main/java/dev/devkey/keyboard/core/KeyboardModeManager.kt:33-36`

```kotlin
fun toggleMode(target: KeyboardMode) {
    ...
    Log.d("DevKeyMode", "toggleMode: $before -> ${_mode.value} (post-write)")
}
```

**Logcat format**: `DevKeyMode: toggleMode: <from> -> <to> (post-write)`

**Callers verified** (DevKeyKeyboard.kt): clipboard toggle (154), symbols toggle (164), macro chips (165), macro grid long-press (166), symbols on 123 tap (306).

**Phase 2.4 implication**: mode-switch flow already has a deterministic signal. Driver server can gate on it without a sleep.

## LayoutMode enum

```kotlin
// LayoutMode.kt
enum class LayoutMode { FULL, COMPACT, COMPACT_DEV }
```

## Modifier transition signal

**Format**: `ModifierTransition <TYPE>: <from> -> <to>`
- **TYPE** ∈ `{SHIFT, CTRL, ALT, META}`
- **states** ∈ `{OFF, ONE_SHOT, LOCKED, HELD, CHORDING}`
- Emitted by `ModifierStateManager` — tag is legacy `DevKeyPress`, NOT `DevKey/MOD` (verified in `.claude/skills/test/references/ime-testing-patterns.md:98-104`)

**Phase 2.4 implication**: modifier flows already have deterministic signals. Wave gating just needs to tail these logcat lines.

## Summary — no spec-to-source discrepancies

Every constant, tag string, intent, keycode, and enum value in the Phase 2 spec scope matches the current source. The only "gap" is that `DevKeyLogger.enableServer(url)` has no production caller — which is the work Phase 2.1 must do.
