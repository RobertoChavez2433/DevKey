# DevKey E2E Test Harness

ADB-based end-to-end tests for the DevKey keyboard app.

## Prerequisites

- Python 3.8+
- ADB installed and on PATH
- Android emulator running. Release validation targets are S21 and the
  `Pixel_7_API_36` emulator; on Windows, use the emulator for automation.
- DevKey installed as the active keyboard
- Debug build (KeyMapGenerator requires debug mode)

## Quick Start

```bash
# From the project root
cd tools/e2e

# Run all tests
python e2e_runner.py

# Run only canonical feature subdirectories or legacy flat tests
python e2e_runner.py --suite features
python e2e_runner.py --suite legacy-flat

# Run a specific test module
python e2e_runner.py --test test_smoke

# Run a specific test function
python e2e_runner.py --test test_modes.test_symbols_mode_switch

# List all tests without running
python e2e_runner.py --list

# Run environment preflight only
python e2e_runner.py --preflight

# Re-run failures from a prior JSON result
python e2e_runner.py --rerun-failed .claude/test-results/e2e-results-YYYYMMDDTHHMMSSZ.json

# Target a specific device
python e2e_runner.py --device emulator-5554

# Verbose mode (full tracebacks)
python e2e_runner.py --verbose
```

## How It Works

1. **Key Map Loading**: On debug builds, DevKey dumps key coordinates to logcat via `KeyMapGenerator`. The test harness reads these to know where each key is on screen.

2. **Y-Offset Calibration**: Screen coordinates from `KeyMapGenerator` may not exactly match ADB `input tap` coordinates (due to status bar, navigation bar, etc.). The calibration step taps a known key and adjusts the offset.

3. **Test Execution**: Each test taps keys via ADB, then asserts on logcat output to verify the keyboard handled the input correctly.

4. **Preflight and Results**: Test runs preflight the device, IME, debug
   server, audio permission, key map, voice assets, and reset strategy before
   executing. Each run writes privacy-safe JSON results under
   `.claude/test-results/` unless `--results-file` is provided.

The locked full-suite discovery count is 178 tests for `--suite all`.
Use `--suite features` or `--suite legacy-flat` when you need one side of the
flat/subdirectory split explicitly.

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

## Phase 2 Infrastructure (Driver Server + Visual Diff)

Phase 2 of the v1.0 pre-release adds a driver-server architecture that replaces
sleep-based synchronization with log-event-gated waits, plus SSIM visual diff
for SwiftKey parity testing.

### Prerequisites

    pip install -r tools/e2e/requirements.txt

### Running a test suite

    # Terminal 1 — start the driver server on port 3950
    node tools/debug-server/server.js

    # Terminal 2 — run the tests (the runner auto-enables HTTP forwarding)
    python tools/e2e/e2e_runner.py

### Switching layout modes during a run

    adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact

### Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `DEVKEY_DEVICE_SERIAL` | (first device) | ADB target |
| `DEVKEY_DRIVER_URL` | `http://127.0.0.1:3950` | Driver server URL |
| `DEVKEY_TIMEOUT_MULTIPLIER` | `1.0` | Multiplies driver wait timeouts |
| `DEVKEY_SSIM_THRESHOLD` | `0.92` | Visual diff pass threshold |
| `DEVKEY_E2E_VERBOSE` | unset | Verbose tracebacks |

### Troubleshooting

If you're running diagnostics that bypass `e2e_runner.py` (e.g. invoking a test
module directly or driving the IME by hand), the runner's auto-broadcast of
`ENABLE_DEBUG_SERVER` won't fire. Issue it manually so the IME forwards events
to the driver server:

    adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER \
        --es url http://10.0.2.2:3950

Note: `10.0.2.2` is the emulator's alias for the host loopback. The harness
translates the host `DEVKEY_DRIVER_URL` automatically.

## Coordinate Calibration

The keyboard reports key coordinates relative to its own view. ADB `input tap` uses absolute screen coordinates. The difference depends on:

- Status bar height
- Navigation bar presence
- Keyboard position on screen

The `calibrate_y_offset()` function in `keyboard.py` handles this by tapping a known key and comparing expected vs actual results. If tests are missing keys, try re-running calibration or adjusting the offset manually.
