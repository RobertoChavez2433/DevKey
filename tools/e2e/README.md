# DevKey E2E Test Harness

ADB-based end-to-end tests for the DevKey keyboard app.

## Prerequisites

- Python 3.8+
- ADB installed and on PATH
- Android emulator running (or physical device connected)
- DevKey installed as the active keyboard
- Debug build (KeyMapGenerator requires debug mode)

## Quick Start

```bash
# From the project root
cd tools/e2e

# Run all tests
python e2e_runner.py

# Run a specific test module
python e2e_runner.py --test test_smoke

# Run a specific test function
python e2e_runner.py --test test_modes.test_symbols_mode_switch

# List all tests without running
python e2e_runner.py --list

# Target a specific device
python e2e_runner.py --device emulator-5554

# Verbose mode (full tracebacks)
python e2e_runner.py --verbose
```

## How It Works

1. **Key Map Loading**: On debug builds, DevKey dumps key coordinates to logcat via `KeyMapGenerator`. The test harness reads these to know where each key is on screen.

2. **Y-Offset Calibration**: Screen coordinates from `KeyMapGenerator` may not exactly match ADB `input tap` coordinates (due to status bar, navigation bar, etc.). The calibration step taps a known key and adjusts the offset.

3. **Test Execution**: Each test taps keys via ADB, then asserts on logcat output to verify the keyboard handled the input correctly.

## Directory Structure

```
tools/e2e/
  e2e_runner.py        # Test discovery and runner
  lib/
    __init__.py
    adb.py             # ADB shell command wrappers
    keyboard.py        # Key map loading and tap helpers
  tests/
    __init__.py
    test_smoke.py      # Basic sanity checks
    test_modifiers.py  # Shift/Ctrl/Alt state tests
    test_modes.py      # 123/Symbols mode switch tests
    test_rapid.py      # Rapid interaction stress tests
```

## Adding New Tests

1. Create a file in `tests/` named `test_*.py`
2. Define functions named `test_*`
3. Use `from lib import adb, keyboard` for helpers
4. Assert with `adb.assert_logcat_contains(tag, pattern)` or standard `assert`

Example:

```python
from lib import adb, keyboard

def test_my_feature():
    serial = adb.get_device_serial()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)
    keyboard.tap_key("a", serial)
    adb.assert_logcat_contains("DevKeyPress", r"tap.*a.*97", serial=serial)
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `DEVKEY_DEVICE_SERIAL` | ADB device serial (alternative to `--device` flag) |
| `DEVKEY_E2E_VERBOSE` | Set to `1` for full tracebacks on errors |

## Coordinate Calibration

The keyboard reports key coordinates relative to its own view. ADB `input tap` uses absolute screen coordinates. The difference depends on:

- Status bar height
- Navigation bar presence
- Keyboard position on screen

The `calibrate_y_offset()` function in `keyboard.py` handles this by tapping a known key and comparing expected vs actual results. If tests are missing keys, try re-running calibration or adjusting the offset manually.
