# Source Excerpts — by File

Minimal source excerpts for the files and symbols Phase 3 must reason about.
All excerpts verified live on 2026-04-09.

---

## `tools/e2e/e2e_runner.py`

### Runner exit contract (line 87-113, 182)
```python
for i, (name, func) in enumerate(tests, 1):
    print(f"  [{i}/{total}] {name} ... ", end="", flush=True)
    start = time.time()
    try:
        func()
        elapsed = time.time() - start
        print(f"PASS ({elapsed:.1f}s)")
        passed += 1
    except AssertionError as e:
        elapsed = time.time() - start
        print(f"FAIL ({elapsed:.1f}s)")
        print(f"         {e}")
        failed += 1
    except Exception as e:
        elapsed = time.time() - start
        print(f"ERROR ({elapsed:.1f}s)")
        print(f"         {type(e).__name__}: {e}")
        errors += 1
# ...
sys.exit(1 if (failed + errors) > 0 else 0)
```

### Auto-enable driver HTTP forwarding (lines 167-176)
```python
# F7: fail-fast on missing driver + auto-enable HTTP forwarding.
from lib import driver
driver.require_driver()
_driver_url = os.environ.get("DEVKEY_DRIVER_URL", "http://10.0.2.2:3947")
driver.broadcast(
    "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
    {"url": _driver_url},
)
```

---

## `tools/e2e/lib/driver.py`

### `wait_for` signature (line 88-105)
```python
def wait_for(category: str, event: Optional[str] = None,
             match: Optional[Dict] = None, timeout_ms: int = 5000) -> Dict:
    params = {"category": category, "timeout": timeout_ms}
    if event:
        params["event"] = event
    if match:
        params["match"] = json.dumps(match)
    result = _get("/wait", params=params, timeout=(timeout_ms / 1000.0) + 5.0)
    if not result.get("matched"):
        raise DriverTimeout(result.get("error", "unknown"))
    return result["entry"]
```

### Canonical fail-fast preamble (line 68-79)
```python
def require_driver() -> None:
    """Assert the driver is reachable. Call at the start of every test."""
    try:
        h = health()
        if h.get("status") != "ok":
            raise DriverError(f"driver health check returned: {h}")
    except DriverError as e:
        raise DriverError(
            f"DevKey driver server not reachable. "
            f"Start it with: node tools/debug-server/server.js\n"
            f"Underlying error: {e}"
        )
```

---

## `tools/e2e/lib/diff.py`

### SSIM threshold contract (line 10, 49-58)
```python
DEFAULT_THRESHOLD = float(os.environ.get("DEVKEY_SSIM_THRESHOLD", "0.92"))

def assert_ssim(actual_path: str, reference_path: str,
                threshold: Optional[float] = None) -> Tuple[bool, float]:
    t = threshold if threshold is not None else DEFAULT_THRESHOLD
    score = ssim(actual_path, reference_path)
    return (score >= t, score)
```

---

## `tools/debug-server/server.js`

### `/wait` long-poll endpoint (line 90-119)
```javascript
if (req.method === 'GET' && url.pathname === '/wait') {
    const category = url.searchParams.get('category');
    const event = url.searchParams.get('event');
    const matchRaw = url.searchParams.get('match');
    const timeoutMs = parseInt(url.searchParams.get('timeout') || '5000');
    // ...
    waveGate.waitFor(logs, category, event, matchData, timeoutMs)
        .then(entry => {
            res.writeHead(200);
            res.end(JSON.stringify({ matched: true, entry }));
        })
        .catch(err => {
            res.writeHead(408);
            res.end(JSON.stringify({ matched: false, error: err.message }));
        });
    return;
}
```

### In-place log rotation (line 28-31)
```javascript
// CRITICAL: In-place splice preserves array identity so long-polling
// `waitFor` closures in wave-gate.js continue to see new appends.
// Reassigning `logs = logs.slice(...)` would break the closure's reference.
if (logs.length > MAX_ENTRIES) logs.splice(0, logs.length - MAX_ENTRIES);
```

### Loopback-only binding (line 242)
```javascript
server.listen(PORT, '127.0.0.1', () => { ... });
```

---

## `app/build.gradle.kts` — lint block (line 71-74)
```kotlin
lint {
    checkReleaseBuilds = true
    abortOnError = false
}
```

---

## `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`

### `layout_mode_set` emit (line 479)
```kotlin
DevKeyLogger.ime("layout_mode_set", mapOf("mode" to mode))
```
(Source: `Grep` confirmed single emit site.)

---

## `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt`

### `keymap_dump_complete` emit (line 200)
```kotlin
"keymap_dump_complete",
```
(The surrounding `DevKeyLogger.ime("keymap_dump_complete", ...)` call is the
wave-gate signal consumed by `lib/keyboard.py::load_key_map`.)

---

## `app/src/main/java/dev/devkey/keyboard/PluginManager.kt`

### `plugin_scan_complete` emit — two sites (line 245, 257)
```kotlin
// Phase 4.9 — structural plugin_scan_complete emit for E2E smoke tests.
DevKeyLogger.ime(
    "plugin_scan_complete",
    ...
)
```
(Two emit sites — one for the normal scan completion, one for the
early-exit/empty path. Both used by `test_plugins.py`.)

---

## `app/src/main/java/dev/devkey/keyboard/core/KeyPressLogger.kt`

### `long_press_fired` emit (line 24)
```kotlin
"long_press_fired",
```
(The surrounding `DevKeyLogger.text("long_press_fired", ...)` call is the
signal consumed by `test_long_press.py`.)

---

## `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`

### `layout_mode_recomposed` emit (line 111)
```kotlin
"layout_mode_recomposed",
```
(Used by `lib/keyboard.py::set_layout_mode` to wait on layout switch.)
