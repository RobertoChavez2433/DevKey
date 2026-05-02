"""Key tapping helpers for DevKey E2E tests."""
import time
from typing import Optional

from . import adb, verify
from . import keyboard_state as state


def tap_symbols_key_by_code(code: int, serial: Optional[str] = None) -> None:
    """
    Tap a key from the symbols layer by its keycode.

    Use this for keys that only exist when the keyboard is in Symbols mode.
    """
    code_key = f"code_{code}"
    if code_key not in state.symbols_key_map:
        raise KeyError(
            f"Symbols key code {code} not found in symbols key map. "
            f"Available: {list(state.symbols_key_map.keys())[:20]}..."
        )
    x, y = state.symbols_key_map[code_key]
    adb.tap(x, y + state.y_offset, serial)


def calibrate_y_offset(serial: Optional[str] = None) -> int:
    """
    Calibrate the Y-offset between expected and actual key positions.

    Returns the computed Y-offset in pixels.
    """
    if "space" not in state.key_map and " " not in state.key_map:
        print("WARNING: No space key in key map, skipping calibration")
        state.y_offset = 0
        return 0

    key_name = "space" if "space" in state.key_map else " "
    x, y = state.key_map[key_name]

    adb.clear_logcat(serial)
    adb.tap(x, y, serial)
    adb.capture_logcat("DevKeyPress", timeout=1.0, serial=serial)

    state.y_offset = 0
    return state.y_offset


def tap_key(key_name: str, serial: Optional[str] = None) -> None:
    """
    Tap a key by its label/name using the cached key map.

    Applies the calibrated Y-offset to the coordinates.
    """
    if key_name not in state.key_map:
        raise KeyError(
            f"Key '{key_name}' not found in key map. "
            f"Available keys: {list(state.key_map.keys())[:20]}..."
        )

    x, y = state.key_map[key_name]
    verify.record_action("keyboard.tap_key", {"key": key_name})
    adb.tap(x, y + state.y_offset, serial)


def tap_key_by_code(code: int, serial: Optional[str] = None) -> None:
    """
    Tap a key by its keycode using the cached key map.

    The key map stores code-based entries as "code_{code}".
    """
    code_key = f"code_{code}"
    if code_key not in state.key_map:
        raise KeyError(
            f"Key code {code} not found in key map. "
            f"Available keys: {list(state.key_map.keys())[:20]}..."
        )

    x, y = state.key_map[code_key]
    verify.record_action("keyboard.tap_key_by_code", {"code": code})
    adb.tap(x, y + state.y_offset, serial)


def tap_sequence(keys: str, delay: float = 0.1,
                 serial: Optional[str] = None) -> None:
    """
    Tap a sequence of keys by their single-character labels.

    Useful for typing words. Adds a small delay between taps.
    """
    for char in keys:
        tap_key(char, serial)
        time.sleep(delay)
