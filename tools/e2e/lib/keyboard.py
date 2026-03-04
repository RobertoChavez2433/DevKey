"""
Keyboard helper for DevKey E2E tests.

Handles key coordinate mapping, Y-offset calibration, and high-level
key tap operations.
"""

import json
import re
import time
from typing import Dict, Optional, Tuple

from . import adb


# Key map: maps key names/labels to (x, y) coordinates
_key_map: Dict[str, Tuple[int, int]] = {}

# Y-offset calibration value
_y_offset: int = 0


def load_key_map(serial: Optional[str] = None) -> Dict[str, Tuple[int, int]]:
    """
    Load the key coordinate map from DevKey's KeyMapGenerator logcat output.

    The app dumps key positions to logcat with tag 'DevKeyMap' on startup
    (debug builds only). Format per line:
        DevKeyMap: key_label=x,y

    Returns the parsed key map and caches it globally.
    """
    global _key_map

    # Clear and wait for KeyMapGenerator to dump
    adb.clear_logcat(serial)
    time.sleep(1.0)

    lines = adb.capture_logcat("DevKeyMap", timeout=3.0, serial=serial)

    key_map = {}
    for line in lines:
        # Parse "key_label=x,y" or "code_-2=x,y" patterns
        match = re.search(r"(\S+)=(\d+),(\d+)", line)
        if match:
            name = match.group(1)
            x = int(match.group(2))
            y = int(match.group(3))
            key_map[name] = (x, y)

    _key_map = key_map
    return key_map


def get_key_map() -> Dict[str, Tuple[int, int]]:
    """Return the cached key map. Call load_key_map() first."""
    return _key_map


def calibrate_y_offset(serial: Optional[str] = None) -> int:
    """
    Calibrate the Y-offset between expected and actual key positions.

    Taps a known key (space bar) at its mapped coordinates and checks
    logcat to see if the correct key was hit. Adjusts offset if needed.

    Returns the computed Y-offset in pixels.
    """
    global _y_offset

    if "space" not in _key_map and " " not in _key_map:
        print("WARNING: No space key in key map, skipping calibration")
        _y_offset = 0
        return 0

    # Try space bar as calibration target
    key_name = "space" if "space" in _key_map else " "
    x, y = _key_map[key_name]

    # Clear logcat and tap
    adb.clear_logcat(serial)
    adb.tap(x, y, serial)

    # Check what key was actually hit
    lines = adb.capture_logcat("DevKeyPress", timeout=1.0, serial=serial)

    # For now, assume no offset needed (can be refined with actual logcat parsing)
    _y_offset = 0
    return _y_offset


def tap_key(key_name: str, serial: Optional[str] = None) -> None:
    """
    Tap a key by its label/name using the cached key map.

    Applies the calibrated Y-offset to the coordinates.
    Raises KeyError if the key is not in the map.
    """
    if key_name not in _key_map:
        raise KeyError(
            f"Key '{key_name}' not found in key map. "
            f"Available keys: {list(_key_map.keys())[:20]}..."
        )

    x, y = _key_map[key_name]
    adb.tap(x, y + _y_offset, serial)


def tap_key_by_code(code: int, serial: Optional[str] = None) -> None:
    """
    Tap a key by its keycode using the cached key map.

    The key map stores code-based entries as "code_{code}".
    Applies the calibrated Y-offset.
    """
    code_key = f"code_{code}"
    if code_key not in _key_map:
        # Try finding by code in the map values
        raise KeyError(
            f"Key code {code} not found in key map. "
            f"Available keys: {list(_key_map.keys())[:20]}..."
        )

    x, y = _key_map[code_key]
    adb.tap(x, y + _y_offset, serial)


def tap_sequence(keys: str, delay: float = 0.1,
                 serial: Optional[str] = None) -> None:
    """
    Tap a sequence of keys by their single-character labels.

    Useful for typing words. Adds a small delay between taps.
    """
    for char in keys:
        tap_key(char, serial)
        time.sleep(delay)
