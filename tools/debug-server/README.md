# DevKey Debug + Test Driver Server

Local HTTP server for DevKey test infrastructure. Binds `127.0.0.1:3947`, zero npm deps, dep-free on purpose.

## Roles
1. **Log sink** — receives structured log entries from `DevKeyLogger` (Android-side HTTP client in `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`).
2. **Wave-gating primitive** — long-poll `/wait` endpoint that blocks until a matching log entry arrives, replacing sleep-based test synchronization.
3. **ADB dispatcher** — centralizes `adb` subprocess calls for the Python test harness in `tools/e2e/`.

## Run

    node server.js

With a specific device:

    ADB_SERIAL=emulator-5554 node server.js

## Enabling HTTP forwarding on the device

The driver server receives nothing until the running IME is told to forward logs. Use the `ENABLE_DEBUG_SERVER` broadcast (debug builds only):

    # Emulator (host-loopback alias)
    adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://10.0.2.2:3947

    # Physical device — forward the host port first, then broadcast the forwarded URL
    adb reverse tcp:3947 tcp:3947
    adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://127.0.0.1:3947

To disable (broadcast with no `url` extra):

    adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER

## Switching layout modes

    adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact
    adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact_dev
    adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode full

## Endpoints

### Legacy (Phase 1 debug server — keeps working)

| Method | Path | Purpose |
|---|---|---|
| POST | `/log` | Append a log entry (JSON body) |
| GET | `/logs?last=N&category=X&hypothesis=H` | Filtered tail |
| GET | `/health` | `{status, entries, maxEntries, memoryMB, uptimeSeconds}` |
| GET | `/categories` | `{category: count}` |
| POST | `/clear` | Drop buffer |

### Phase 2 extension

| Method | Path | Purpose |
|---|---|---|
| GET | `/wait?category=X&event=Y&match=<urlencoded-json>&timeout=<ms>` | Long-poll until matching entry arrives |
| POST | `/adb/tap?x=X&y=Y` | Tap screen coordinate |
| POST | `/adb/swipe?x1=&y1=&x2=&y2=&duration=` | Swipe / long-press |
| POST | `/adb/broadcast` (JSON body `{action, extras}`) | Dispatch `am broadcast` |
| GET | `/adb/logcat?tags=DevKeyPress,DevKeyMode,...` | Dump logcat for legacy tags |
| POST | `/adb/logcat/clear` | `logcat -c` |
| POST | `/wave/start?id=<name>` | Mark the start of a test wave |
| POST | `/wave/finish?id=<name>&status=pass` | Record wave completion |
| GET | `/wave/status` | List all waves |

## Privacy contract

DevKey IMEs see every keystroke, including passwords and PII. The HTTP log endpoint accepts ONLY structural data (state names, lengths, counts, durations). NEVER log typed text, transcripts, audio samples, or buffer bytes — see `DevKeyLogger.kt` privacy comment.

This server exposes a local ADB convenience layer on 127.0.0.1:3947. The `/adb/logcat` endpoint can return `DevKeyPress` KEY/LONG lines, which on debug builds contain a live stream of keystrokes. Any process on this machine can poll `/adb/logcat`. DO NOT run the driver server on a workstation where untrusted local processes exist. Stop the driver server between sessions.
