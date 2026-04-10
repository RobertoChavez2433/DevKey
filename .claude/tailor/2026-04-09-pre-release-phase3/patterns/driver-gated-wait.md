# Pattern — Driver-Gated Wait (sleep-free assertions)

## Summary
Every Phase 2 test replaces time-based sleeps with long-poll `driver.wait_for(...)`
assertions against named events forwarded over HTTP from `DevKeyLogger` to
the local driver server. Phase 3 tests inherit this contract — a test that
times out on a wait is a **deterministic** red, not a flake.

## How we do it
A test clears the driver log buffer (`driver.clear_logs()`), performs an ADB
action (`driver.tap`, `keyboard.tap_key`, `driver.broadcast`, etc.), then
calls `driver.wait_for(category, event, match, timeout_ms)` to block until a
matching structured log entry arrives. Match payloads are JSON subsets —
`{"state": "LISTENING"}` matches an entry whose `data.state == "LISTENING"`
regardless of other fields. The Python client's timeout is always server
timeout + 5s, so the client outlives the server on the race.

## Exemplar 1 — voice state transition (`tests/test_voice.py:35-45`)
```python
def test_voice_state_machine_to_listening():
    serial, voice_key = _setup_voice_button()
    driver.clear_logs()
    keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
    driver.wait_for(
        category="DevKey/VOX",
        event="state_transition",
        match={"state": "LISTENING", "source": "startListening"},
        timeout_ms=3000,
    )
```

## Exemplar 2 — layout mode switch round-trip (`tests/test_modes.py:98-133`)
```python
def test_layout_mode_round_trip():
    serial = adb.get_device_serial()
    driver.require_driver()
    for mode in ("compact", "compact_dev", "full"):
        driver.clear_logs()
        driver.broadcast("dev.devkey.keyboard.SET_LAYOUT_MODE", {"mode": mode})
        driver.wait_for(
            category="DevKey/IME",
            event="layout_mode_set",
            match={"mode": mode},
            timeout_ms=3000,
        )
        keyboard.load_key_map(serial)
        km = keyboard.get_key_map()
        letter_count = sum(1 for k in km.keys() if len(k) == 1 and k.isalpha())
        assert letter_count >= 10
```

## Reusable operations

| Operation | Signature | Where |
|---|---|---|
| Require a running driver | `driver.require_driver() -> None` | `tools/e2e/lib/driver.py:68` |
| Clear buffer before an action | `driver.clear_logs() -> None` | `driver.py:82` |
| Long-poll for event | `driver.wait_for(category, event=None, match=None, timeout_ms=5000) -> dict` | `driver.py:88` |
| Broadcast an intent via ADB | `driver.broadcast(action, extras=None) -> dict` | `driver.py:120` |
| Tap via ADB | `driver.tap(x, y) -> dict` | `driver.py:110` |
| Dump legacy logcat tags | `driver.logcat_dump(tags: list) -> str` | `driver.py:124` |
| Clear legacy logcat | `driver.logcat_clear() -> None` | `driver.py:132` |

## Required imports
```python
from lib import adb, keyboard, driver
```

## Anti-patterns (Phase 3 must-nots)
- **No `time.sleep(...)` between an action and an assertion.** If a test
  needs to wait, it waits on an event. Existing sleep call sites in
  `tests/test_modes.py` (lines around 26/49/57) are stabilization-fix
  candidates.
- **No asserting on `adb logcat` output directly.** Use the driver's
  `/wait` endpoint; legacy logcat is only for tags that do not yet go
  through `DevKeyLogger` (`DevKeyPress`, `DevKeyMode`, `DevKeyMap`,
  `DevKeyBridge`) per the Phase 2 "legacy tag migration deferred" decision.
- **Never assert on PII inside matched payloads.** Voice tests assert on
  `result_length` only, next-word tests assert `source` only, long-press
  tests assert `label` + `lp_code` — never content.
