# Source Excerpts — By File

All source excerpts relevant to Phase 2 organized by file path. Every excerpt is quoted verbatim from the live source at tailor time.

## `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`

**Full file is 120 lines.** The entire file is in scope for Phase 2 since it defines the HTTP-forwarding API. Key sections:

### Enum + state (lines 22-34)
```kotlin
object DevKeyLogger {
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

    @Volatile private var serverUrl: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)
```

### enableServer / disableServer (lines 36-44)
```kotlin
/** Enable HTTP server forwarding for Deep debug mode. */
fun enableServer(url: String) {
    serverUrl = url
}

/** Disable HTTP server forwarding. */
fun disableServer() {
    serverUrl = null
}
```

### sendToServer (lines 91-119)
```kotlin
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

## `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`

### Broadcast receiver for DUMP_KEY_MAP (lines 403-422) — the pattern Phase 2.1 mirrors
```kotlin
// Debug: register broadcast receiver for on-demand key map dump
if (KeyMapGenerator.isDebugBuild(this)) {
    keyMapDumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val modeStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(SettingsRepository.KEY_LAYOUT_MODE, "full") ?: "full"
            val mode = when (modeStr) {
                "compact" -> LayoutMode.COMPACT
                "compact_dev" -> LayoutMode.COMPACT_DEV
                else -> LayoutMode.FULL
            }
            KeyMapGenerator.dumpToLogcat(context, null, mode)
        }
    }
    registerReceiver(
        keyMapDumpReceiver,
        IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"),
        Context.RECEIVER_NOT_EXPORTED
    )
}
```

### shouldShowVoiceButton stub (line 817)
```kotlin
private fun shouldShowVoiceButton(attribute: EditorInfo): Boolean = true
```

### setNextSuggestions + getLastCommittedWordBeforeCursor (lines 1911-1960)
```kotlin
private fun getLastCommittedWordBeforeCursor(): CharSequence? {
    val ic = currentInputConnection ?: return null
    val before = ic.getTextBeforeCursor(64, 0) ?: return null
    if (before.isEmpty()) return null

    var end = before.length
    while (end > 0 && before[end - 1].isWhitespace()) end--
    if (end == 0) return null

    var start = end
    while (start > 0) {
        val c = before[start - 1]
        if (c.isLetterOrDigit() || c == '\'') start-- else break
    }
    if (start == end) return null
    return before.subSequence(start, end)
}

private fun setNextSuggestions() {
    val suggestImpl = mSuggest
    if (suggestImpl != null && isPredictionOn()) {
        val prevWord = getLastCommittedWordBeforeCursor()
        if (prevWord != null) {
            val nextWords = suggestImpl.getNextWordSuggestions(prevWord)
            if (nextWords.isNotEmpty()) {
                setSuggestions(
                    nextWords,
                    completions = false,
                    typedWordValid = false,
                    haveMinimalSuggestion = false
                )
                return
            }
        }
    }
    setSuggestions(
        mSuggestPuncList,
        completions = false,
        typedWordValid = false,
        haveMinimalSuggestion = false
    )
}
```

**Phase 2.3 instrumentation target**: add `DevKeyLogger.text("next_word_suggestions", mapOf("prev_word_length" to (prevWord?.length ?: 0), "result_count" to nextWords.size))` inside the `if (nextWords.isNotEmpty())` branch before `return`, and a second call in the fallback branch with `result_count = 0`.

### KEYCODE_VOICE handler (line 1302)
```kotlin
KeyCodes.KEYCODE_VOICE -> {
    // (handler body)
}
```

### setNextSuggestions callers
- Line 905, 960, 1687, 1809, 1839, 1889, 2539 — all call `setNextSuggestions()` with no arguments, no result capture

## `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt`

Full file is 206 lines. Key symbols for Phase 2.4:

### TAG + isDebugBuild (lines 27, 39-41)
```kotlin
private const val TAG = "DevKeyMap"

fun isDebugBuild(context: Context): Boolean {
    return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
```

### dumpToLogcat output loop (lines 181-196)
```kotlin
Log.d(TAG, "=== DevKey Key Map ===")
Log.d(TAG, "screen=${dm.widthPixels}x${dm.heightPixels} density=$density mode=$layoutMode")

if (keyboardView != null) {
    val location = IntArray(2)
    keyboardView.getLocationOnScreen(location)
    Log.d(TAG, "keyboard_view_top=${location[1]}")
} else {
    Log.d(TAG, "keyboard_view_top=estimated")
}

for (kb in bounds) {
    Log.d(TAG, "KEY label=${kb.adbLabel} code=${kb.code} x=${kb.centerX.toInt()} y=${kb.centerY.toInt()}")
}

Log.d(TAG, "=== End Key Map (${bounds.size} keys) ===")
```

## `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt`

### VoiceState enum (lines 49-58)
```kotlin
enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}
```

### All 16 DevKeyLogger.voice call sites are listed in `ground-truth.md` under "VoiceInputEngine state transitions (already instrumented)". Not duplicated here.

## `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt`

### KEYCODE_VOICE constant (line 148)
```kotlin
const val KEYCODE_VOICE = -102
```

### KeyData long-press fields (lines 38-58)
```kotlin
/**
 * @param longPressCode Optional keycode sent on long-press.
 * @param longPressCodes Optional list of keycodes for multi-char long-press popup.
 */
data class KeyData(
    ...
    val longPressCode: Int? = null,
    // longPressCode remains the "primary" (first) long-press for
    // backward compatibility...
    val longPressCodes: List<Int>? = null,
)
```

## `app/src/main/java/dev/devkey/keyboard/core/KeyboardModeManager.kt`

### toggleMode emit (lines 33-37)
```kotlin
fun toggleMode(target: KeyboardMode) {
    val before = _mode.value
    // ... state mutation ...
    Log.d("DevKeyMode", "toggleMode: $before -> ${_mode.value} (post-write)")
}
```

## `app/src/main/java/dev/devkey/keyboard/core/KeyPressLogger.kt`

### logLongPress emit (lines 16-17)
```kotlin
fun logLongPress(label: String, code: Int, longPressCode: Int?) {
    Log.d(TAG, "LONG  label=$label code=$code lpCode=$longPressCode")
}
```

(TAG = `"DevKeyPress"` — verified by prior read.)

## `tools/debug-server/server.js`

Full file is 85 lines. Every line is in scope for Phase 2.2 extension. Key sections:

### Constants + state (lines 4-8)
```javascript
const http = require('http');
const PORT = 3947;
const MAX_ENTRIES = 30000;

let logs = [];
```

### POST /log handler (lines 17-34)
```javascript
if (req.method === 'POST' && url.pathname === '/log') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
        try {
            const entry = JSON.parse(body);
            entry.ts = new Date().toISOString().slice(11, 23);
            logs.push(entry);
            if (logs.length > MAX_ENTRIES) logs = logs.slice(-MAX_ENTRIES);
            res.writeHead(200);
            res.end('{"ok":true}');
        } catch (e) {
            res.writeHead(400);
            res.end(JSON.stringify({error: e.message}));
        }
    });
    return;
}
```

### GET /logs filter (lines 50-60)
```javascript
if (url.pathname === '/logs') {
    let filtered = logs;
    const cat = url.searchParams.get('category');
    const hyp = url.searchParams.get('hypothesis');
    const last = parseInt(url.searchParams.get('last') || '50');
    if (cat) filtered = filtered.filter(e => e.category === cat);
    if (hyp) filtered = filtered.filter(e => e.hypothesis === hyp);
    filtered = filtered.slice(-last);
    res.end(filtered.map(e => JSON.stringify(e)).join('\n'));
    return;
}
```

### Listen binding (lines 82-84)
```javascript
server.listen(PORT, '127.0.0.1', () => {
    console.log(`Debug server listening on http://127.0.0.1:${PORT}`);
});
```

## `tools/e2e/lib/adb.py`

See `patterns/python-adb-e2e.md` for the key excerpts — not duplicated here to keep this file manageable.

## `tools/e2e/lib/keyboard.py`

### Buggy regex (line 45) — must be fixed in Phase 2.4
```python
match = re.search(r"(\S+)=(\d+),(\d+)", line)
```

Current `KeyMapGenerator` output is `KEY label=X code=N x=P y=Q` — this regex matches nothing. Correct form: `r"KEY label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)"`.
