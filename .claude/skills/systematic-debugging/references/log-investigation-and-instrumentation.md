# Log Investigation and Instrumentation

## DevKeyLogger Categories

Each category maps to a logcat tag and a debug-server category filter.

| Method | Category Enum | Logcat Tag | Use For |
|--------|--------------|------------|---------|
| `DevKeyLogger.ime()` | `Category.IME_LIFECYCLE` | `DevKey/IME` | InputMethodService lifecycle: onCreateInputView, onStartInput, onFinishInput, onWindowShown/Hidden |
| `DevKeyLogger.ui()` | `Category.COMPOSE_UI` | `DevKey/UI` | Compose recomposition, keyboard view rendering, panel show/hide |
| `DevKeyLogger.modifier()` | `Category.MODIFIER_STATE` | `DevKey/MOD` | Shift/Ctrl/Alt/Meta state transitions, multitouch modifier events |
| `DevKeyLogger.text()` | `Category.TEXT_INPUT` | `DevKey/TXT` | commitText, setComposingText, key code dispatch — **key codes only, never characters** |
| `DevKeyLogger.native()` | `Category.NATIVE_JNI` | `DevKey/JNI` | JNI calls into liblatinime, dictionary lookup, BinaryDictionary |
| `DevKeyLogger.voice()` | `Category.VOICE` | `DevKey/VOI` | VoiceInputEngine lifecycle, TFLite inference, transcription commits |
| `DevKeyLogger.error()` | `Category.ERROR` | `DevKey/ERR` | Exceptions, unexpected states, recoverable errors |
| `DevKeyLogger.hypothesis()` | `Category.*` | `DevKey/H{NNN}` | Debug-session markers — MUST be removed before session close |

---

## Hypothesis Tagging

### H-ID Convention

Each debug session assigns sequential IDs starting at H001. The ID is passed as the first argument to `DevKeyLogger.hypothesis()` and appears as the logcat tag suffix.

```kotlin
// Format
DevKeyLogger.hypothesis(
    id = "H001",
    category = Category.MODIFIER_STATE,
    message = "shift state at focus change",
    data = mapOf("shift_active" to shiftActive, "focus_target" to packageName)
)
```

Logcat output: `D DevKey/H001: shift state at focus change | shift_active=true focus_target=com.example`

### Rules for Hypothesis Markers

- One H-ID per hypothesis (H001, H002, etc.). Multiple markers may share an ID.
- Place markers at entry AND exit of the suspected function.
- Log state that would confirm OR deny — not just state that confirms.
- **Never log text content** (see Privacy Restrictions below).

---

## Privacy Restrictions

**CRITICAL — IME-specific**: DevKey processes every keystroke the user types, including passwords, banking PINs, and private messages. Violating these rules creates a privacy-sensitive logging artifact.

- **NEVER log the text content of what the user is typing.** Log key codes (e.g., `keyCode=KEYCODE_A`) not characters or words.
- NEVER log passwords, tokens, API keys, or credentials.
- NEVER log clipboard content.
- NEVER log app-specific text from `InputConnection.getTextBeforeCursor()` or `getSelectedText()` in production-facing code paths.

For hypothesis markers, log structural state (flags, enum values, counts, timing) not content.

---

## ADB Logcat Filters

```bash
# All DevKey logs
adb logcat -s "DevKey:D"

# Single hypothesis
adb logcat -s "DevKey/H001:D"

# Multiple categories
adb logcat -s "DevKey/IME:D" -s "DevKey/MOD:D"

# Errors only
adb logcat -s "DevKey/ERR:E"
```

---

## Debug Server API (Deep Mode)

Server runs at `http://localhost:3950`.

### Endpoints

```bash
# Health check
curl http://localhost:3950/health

# Last N log entries
curl "http://localhost:3950/logs?last=50"

# Filter by category
curl "http://localhost:3950/logs?last=100&category=IME_LIFECYCLE"

# Filter by hypothesis ID
curl "http://localhost:3950/logs?last=100&hypothesis=H001"

# Combined filter
curl "http://localhost:3950/logs?last=100&category=MODIFIER_STATE&hypothesis=H001"

# List available categories
curl http://localhost:3950/categories

# Clear log buffer
curl -X POST http://localhost:3950/clear
```

### Response format

```json
{
  "entries": [
    {
      "timestamp": "2026-04-08T14:32:01.123Z",
      "hypothesisId": "H001",
      "category": "MODIFIER_STATE",
      "message": "shift state at focus change",
      "data": { "shift_active": true, "focus_target": "com.example" }
    }
  ],
  "total": 1
}
```

---

## Cleanup Verification

Before closing any debug session, run:

```bash
Grep "hypothesis(" app/src/
```

This must return zero matches. If any remain, remove those `DevKeyLogger.hypothesis()` calls before committing or closing the session.
