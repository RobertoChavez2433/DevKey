# Pattern — Python ADB + logcat E2E harness

## Summary
`tools/e2e/` is a pre-existing Python test harness that does exactly what Phase 2.2's driver server conceptually needs: ADB taps, logcat assertions, coordinate loading from `DevKeyMap` broadcast output, per-test isolation. Six test modules exist (`test_smoke`, `test_modifiers`, `test_modes`, `test_rapid`, plus `__init__.py`s). The harness is sleep-heavy and unused in the current test pipeline — last exercised before Session 33.

**Phase 2 plan decision**: the driver server can EXTEND this harness (converting sleeps to log-wait calls) OR REPLACE it with a Node-based driver. Extension is lower-risk.

## How we do it
- `e2e_runner.py` — discovers test modules via `importlib`, finds `test_*` functions, runs them sequentially, prints PASS/FAIL/ERROR counts
- `lib/adb.py` — thin ADB shell wrappers: `tap`, `swipe`, `input_text`, `clear_logcat`, `capture_logcat`, `assert_logcat_contains`, `is_keyboard_visible`, `get_text_field_content`, `get_focused_window`
- `lib/keyboard.py` — key coordinate loading via `DevKeyMap` logcat parse, `calibrate_y_offset`, `tap_key(label)`, `tap_key_by_code(code)`, `tap_sequence(keys)`
- Tests import `from lib import adb, keyboard` and use assertion helpers

## Exemplar 1 — logcat assertion helper

**File**: `tools/e2e/lib/adb.py:106-120`

```python
def assert_logcat_contains(tag: str, pattern: str, timeout: float = 2.0,
                           serial: Optional[str] = None) -> bool:
    """
    Assert that logcat contains a line matching the given pattern for the tag.
    Returns True if found, raises AssertionError if not.
    """
    lines = capture_logcat(tag, timeout, serial)
    for line in lines:
        if re.search(pattern, line):
            return True
    raise AssertionError(
        f"Expected logcat tag '{tag}' to contain pattern '{pattern}', "
        f"but got {len(lines)} lines: {lines[:5]}"
    )
```

**Phase 2 upgrade opportunity**: replace `capture_logcat(tag, timeout=2.0)` (which sleeps for `timeout` seconds unconditionally) with a polling loop OR a call to the driver server's `GET /wait?...` endpoint. This is the core sleep-to-signal migration spec §2.2 wants.

## Exemplar 2 — key coordinate loader

**File**: `tools/e2e/lib/keyboard.py:23-52`

```python
def load_key_map(serial: Optional[str] = None) -> Dict[str, Tuple[int, int]]:
    """
    Load the key coordinate map from DevKey's KeyMapGenerator logcat output.
    The app dumps key positions to logcat with tag 'DevKeyMap' on startup (debug builds only).
    """
    global _key_map

    adb.clear_logcat(serial)
    time.sleep(1.0)

    lines = adb.capture_logcat("DevKeyMap", timeout=3.0, serial=serial)

    key_map = {}
    for line in lines:
        match = re.search(r"(\S+)=(\d+),(\d+)", line)
        if match:
            name = match.group(1)
            x = int(match.group(2))
            y = int(match.group(3))
            key_map[name] = (x, y)

    _key_map = key_map
    return key_map
```

**Note**: this regex `(\S+)=(\d+),(\d+)` does NOT match the current `KeyMapGenerator` output format, which is `KEY label=X code=N x=P y=Q` (verified in `KeyMapGenerator.kt:193`). **This is a stabilization bug** — the harness was written against an older format and has been silently matching nothing.

Phase 2.4 stabilization must either:
- Fix the regex to `r"KEY label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)"` and rebuild the map
- OR trigger the broadcast first (`adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`) since the harness doesn't issue it

## Reusable methods table

| Module | Function | Signature | Purpose |
|---|---|---|---|
| `lib.adb` | `tap` | `(x, y, serial=None)` | Single tap |
| `lib.adb` | `swipe` | `(x1, y1, x2, y2, duration_ms=300, serial=None)` | Swipe for long-press etc |
| `lib.adb` | `input_text` | `(text, serial=None)` | `input text` (emulator only) |
| `lib.adb` | `clear_logcat` | `(serial=None)` | `logcat -c` |
| `lib.adb` | `capture_logcat` | `(tag, timeout=2.0, serial=None) -> List[str]` | Blocking dump after sleep |
| `lib.adb` | `assert_logcat_contains` | `(tag, pattern, timeout=2.0, serial=None) -> bool` | Assertion helper |
| `lib.adb` | `is_keyboard_visible` | `(serial=None) -> bool` | dumpsys check |
| `lib.adb` | `get_text_field_content` | `(serial=None) -> str` | Parse `mText=` from dumpsys input_method |
| `lib.adb` | `get_focused_window` | `(serial=None) -> str` | Current focused window |
| `lib.keyboard` | `load_key_map` | `(serial=None) -> Dict[str, Tuple[int,int]]` | Load from DevKeyMap logcat |
| `lib.keyboard` | `get_key_map` | `() -> Dict` | Cached |
| `lib.keyboard` | `calibrate_y_offset` | `(serial=None) -> int` | Y-offset (currently a no-op stub) |
| `lib.keyboard` | `tap_key` | `(key_name, serial=None)` | Tap by label |
| `lib.keyboard` | `tap_key_by_code` | `(code, serial=None)` | Tap by code (expects `code_<N>` keys in map) |
| `lib.keyboard` | `tap_sequence` | `(keys, delay=0.1, serial=None)` | Type multi-char |

## Required imports
```python
from lib import adb, keyboard
```

## Gaps (what Phase 2 must add to this harness to satisfy spec §2.2)

| Gap | Fix |
|---|---|
| Regex mismatch against current DevKeyMap format | Update `load_key_map` regex to `r"KEY label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)"` |
| No broadcast trigger before reading DevKeyMap | `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` at start of `load_key_map` |
| `capture_logcat` sleeps unconditionally | Replace with polling-loop version OR HTTP `/wait` call against driver server |
| `calibrate_y_offset` is a no-op stub | Implement real calibration OR delete entirely since broadcast handles it now |
| No HTTP client for driver server | Add `lib/driver.py` with `wait_for(category, event, match, timeout)` |
| No screenshot diff helper | Add `lib/diff.py` with `ssim(path1, path2) -> float` (dep: `scikit-image`) |
| No voice audio injection | Add `lib/audio.py` — emulator: `adb emu avd speaker ...`; physical: Bluetooth audio or bench-test only |
| No coverage check | Add `tools/e2e/coverage_matrix.py` |

## Required new Python deps (if the plan chooses extension)

- `scikit-image` — for SSIM visual diff
- `Pillow` (pulled in by scikit-image)
- `requests` — for driver-server HTTP client

Alternatively, if the plan keeps Python dep-free, shell out to ImageMagick `compare -metric SSIM a.png b.png null:` and parse stdout.
