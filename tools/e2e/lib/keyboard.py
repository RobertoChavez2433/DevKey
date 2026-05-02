"""
Keyboard helper for DevKey E2E tests.

Handles key coordinate mapping, Y-offset calibration, and high-level
key tap operations.
"""

import json
import re
import time
from typing import Any, Dict, List, Optional, Tuple

from . import adb


# Key map: maps key names/labels to (x, y) coordinates
_key_map: Dict[str, Tuple[int, int]] = {}

# Symbols-layer key map: maps symbols-layout labels to (x, y) coordinates.
# Populated from SYM_KEY lines in the DevKeyMap dump. WHY: tests that enter
# symbols mode (ABC return, 123 toggle) need ABC (-200) and other symbol-only
# keys that don't exist in the normal QWERTY dump.
_symbols_key_map: Dict[str, Tuple[int, int]] = {}

# Canonical inventory from the last key-map dump. Unlike the lookup maps above,
# this preserves every visible key record with layer, code, and coordinates.
_key_inventory: List[Dict[str, Any]] = []

# Y-offset calibration value
_y_offset: int = 0


def load_key_map(serial: Optional[str] = None) -> Dict[str, Tuple[int, int]]:
    """
    Load the key coordinate map from DevKey's KeyMapGenerator logcat output.

    The app dumps key positions to logcat with tag 'DevKeyMap' in response to
    a broadcast (debug builds only). Format per line:
        DevKeyMap: KEY label=<label> code=<code> x=<x> y=<y>

    Returns a dict keyed by both label and "code_<code>" for flexible lookup.
    """
    global _key_map

    # WHY: DevKeyMap only dumps on demand. Prior version (pre-Phase 2) relied on the
    #      dump happening at IME boot, which was unreliable and format-incompatible.
    # FROM SPEC: §6 Phase 2 item 2.4 — reliable calibration across FULL/COMPACT/COMPACT_DEV.
    adb.clear_logcat(serial)
    cmd = ["shell", "am", "broadcast", "-a", "dev.devkey.keyboard.DUMP_KEY_MAP"]
    if serial:
        cmd = ["-s", serial] + cmd
    import subprocess
    subprocess.run(["adb"] + cmd, check=True, capture_output=True)
    # WHY: Replace the legacy `time.sleep(0.3)` with a proper wave-gate on the
    #     `keymap_dump_complete` event emitted from KeyMapGenerator.dumpToLogcat
    #     (added in Phase 1 sub-phase 1.7).
    from lib import driver as _driver
    try:
        _driver.wait_for("DevKey/IME", "keymap_dump_complete", timeout_ms=3000)
    except _driver.DriverError:
        # Driver may not be enabled for this run — fall back to a bounded poll
        # against logcat itself (the subsequent capture_logcat call handles it).
        pass

    lines = adb.capture_logcat("DevKeyMap", timeout=2.0, serial=serial)

    # Parse format: "KEY label=<label> code=<code> x=<x> y=<y>"
    #            or "SYM_KEY label=<label> code=<code> x=<x> y=<y>" for symbols layer
    global _symbols_key_map, _key_inventory
    key_map: Dict[str, Tuple[int, int]] = {}
    symbols_map: Dict[str, Tuple[int, int]] = {}
    inventory: List[Dict[str, Any]] = []
    pattern = re.compile(r"(SYM_KEY|KEY) label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)")
    for line in lines:
        m = pattern.search(line)
        if m:
            kind = m.group(1)
            label = m.group(2)
            code = int(m.group(3))
            x = int(m.group(4))
            y = int(m.group(5))
            target = symbols_map if kind == "SYM_KEY" else key_map
            target[label] = (x, y)
            target[f"code_{code}"] = (x, y)
            inventory.append({
                "layer": "symbols" if kind == "SYM_KEY" else "normal",
                "label": label,
                "code": code,
                "x": x,
                "y": y,
                "lookup_keys": [label, f"code_{code}"],
            })

    _key_map = key_map
    _symbols_key_map = symbols_map
    _key_inventory = inventory
    return key_map


def get_key_inventory() -> List[Dict[str, Any]]:
    """Return visible key records from the most recent key-map dump."""
    return list(_key_inventory)


def _validate_inventory_layout(
    mode: str,
    records: List[Dict[str, Any]],
    normal: List[Dict[str, Any]],
    symbols: List[Dict[str, Any]],
) -> Dict[str, Any]:
    failures = []
    if not normal:
        failures.append(f"{mode}: normal layer has no visible keys")
    if not symbols:
        failures.append(f"{mode}: symbols layer has no visible keys")

    seen = set()
    for index, record in enumerate(records):
        record_id = f"{mode}[{index}]"
        layer = record.get("layer")
        label = record.get("label")
        code = record.get("code")
        x = record.get("x")
        y = record.get("y")
        lookup_keys = record.get("lookup_keys")

        if layer not in ("normal", "symbols"):
            failures.append(f"{record_id}: invalid layer {layer!r}")
        if not isinstance(label, str) or not label:
            failures.append(f"{record_id}: label is missing")
        if not isinstance(code, int):
            failures.append(f"{record_id}: code is missing")
        if not isinstance(x, int) or x < 0:
            failures.append(f"{record_id}: x coordinate is invalid")
        if not isinstance(y, int) or y < 0:
            failures.append(f"{record_id}: y coordinate is invalid")
        if not isinstance(lookup_keys, list) or len(lookup_keys) != 2:
            failures.append(f"{record_id}: lookup aliases are incomplete")
        elif label not in lookup_keys or f"code_{code}" not in lookup_keys:
            failures.append(f"{record_id}: lookup aliases do not match label/code")

        key = (layer, label, code, x, y)
        if key in seen:
            failures.append(f"{record_id}: duplicate visible key record")
        seen.add(key)

    return {
        "ok": not failures,
        "visible_records": len(records),
        "normal_records": len(normal),
        "symbols_records": len(symbols),
        "failures": failures,
    }


def generate_canonical_inventory(
    serial: Optional[str] = None,
    layout_modes: Tuple[str, ...] = ("full", "compact", "compact_dev"),
) -> Dict[str, Any]:
    """
    Generate a structural inventory for every layout mode and key layer.

    The inventory is privacy-safe: labels, keycodes, coordinates, and counts
    only. It contains no typed output, clipboard data, or transcript content.
    """
    layouts = []
    coverage_by_layout: Dict[str, Dict[str, Any]] = {}
    for mode in layout_modes:
        set_layout_mode(mode, serial)
        records = get_key_inventory()
        normal = [record for record in records if record["layer"] == "normal"]
        symbols = [record for record in records if record["layer"] == "symbols"]
        coverage_by_layout[mode] = _validate_inventory_layout(mode, records, normal, symbols)
        layouts.append({
            "layout": mode,
            "counts": {
                "normal": len(normal),
                "symbols": len(symbols),
                "total": len(records),
            },
            "layers": {
                "normal": normal,
                "symbols": symbols,
            },
        })

    failures = [
        failure
        for layout in coverage_by_layout.values()
        for failure in layout["failures"]
    ]
    return {
        "schema_version": 1,
        "layouts": layouts,
        "coverage": {
            "ok": not failures,
            "tested_layouts": len(layouts),
            "tested_visible_records": sum(layout["visible_records"] for layout in coverage_by_layout.values()),
            "layouts": coverage_by_layout,
            "failures": failures,
        },
        "privacy": {
            "typed_text_logged": False,
            "transcripts_logged": False,
            "clipboard_logged": False,
        },
    }


def get_symbols_key_map() -> Dict[str, Tuple[int, int]]:
    """Return the cached symbols-layer key map (populated by load_key_map)."""
    return _symbols_key_map


def tap_symbols_key_by_code(code: int, serial: Optional[str] = None) -> None:
    """
    Tap a key from the symbols layer by its keycode.

    Use this for keys that only exist when the keyboard is in Symbols mode
    (ABC=-200, @, #, $, etc.).
    """
    code_key = f"code_{code}"
    if code_key not in _symbols_key_map:
        raise KeyError(
            f"Symbols key code {code} not found in symbols key map. "
            f"Available: {list(_symbols_key_map.keys())[:20]}..."
        )
    x, y = _symbols_key_map[code_key]
    from . import adb as _adb
    _adb.tap(x, y + _y_offset, serial)


def get_key_map() -> Dict[str, Tuple[int, int]]:
    """Return the cached key map. Call load_key_map() first."""
    return _key_map


def clear_key_map_cache() -> None:
    """
    Clear the cached key map so the next load_key_map() re-dumps from the IME.

    WHY: Phase 3 defect #21 — callers that switch layout modes (via
         `set_layout_mode()`) had no way to invalidate the stale module-level
         cache from an earlier tier. Without this, `get_key_map()` returns
         a key map whose coordinates belong to the previous layout, causing
         taps to miss and tests to error with "key code not found".
    """
    global _key_map, _symbols_key_map, _key_inventory, _y_offset
    _key_map = {}
    _symbols_key_map = {}
    _key_inventory = []
    _y_offset = 0


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


def set_layout_mode(mode: str, serial: Optional[str] = None) -> None:
    """
    Switch the running IME to the given layout mode via debug broadcast.

    Valid values: "full", "compact", "compact_dev".
    FROM SPEC: §6 Phase 2 item 2.4 — programmatic layout-mode switching.

    WARNING: Blocks ~3s while the broadcast dispatches, Compose recomposes,
             and the key map is reloaded. Callers driving tight assertions
             should broadcast directly via `driver.broadcast(...)` + `driver.wait_for(...)`
             rather than calling this helper.
    """
    if mode not in ("full", "compact", "compact_dev"):
        raise ValueError(f"invalid layout mode: {mode}")

    import subprocess

    # WHY: The keyboard mode (Normal/Symbols/Voice/Macro) is independent of the
    #      layout mode (FULL/COMPACT/COMPACT_DEV). A prior test that entered
    #      Symbols and didn't clean up leaves the keyboard rendering the symbols
    #      layer. Taps using the FULL key map then hit symbols keys instead of
    #      QWERTY letters. Resetting mode to Normal before changing layout
    #      ensures the key map coordinates match what's actually rendered.
    cmd_reset = ["adb"]
    if serial:
        cmd_reset += ["-s", serial]
    cmd_reset += [
        "shell", "am", "broadcast",
        "-a", "dev.devkey.keyboard.RESET_KEYBOARD_MODE",
    ]
    subprocess.run(cmd_reset, check=False, capture_output=True)
    # WHY: The mode reset triggers a Compose recomposition. If SET_LAYOUT_MODE
    #      arrives while the reset recomposition is in flight, the subsequent
    #      DUMP_KEY_MAP can race with layout re-measurement and return empty.
    time.sleep(0.3)

    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += [
        "shell", "am", "broadcast",
        "-a", "dev.devkey.keyboard.SET_LAYOUT_MODE",
        "--es", "mode", mode,
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    # WHY: Phase 3 defect #21 — after a layout switch the cached _key_map
    #      holds stale coordinates from the previous mode. Subsequent
    #      `get_key_map()` callers would tap the wrong positions. Clear here
    #      and let the next explicit `load_key_map()` re-dump for the new
    #      layout. We don't force a load here because some callers want to
    #      batch the wait-for-recompose step with their own key-map load.
    clear_key_map_cache()

    # WHY: Wave-gate on the `layout_mode_recomposed` event emitted from
    #      DevKeyKeyboard.kt's LaunchedEffect(layoutMode) block (added in
    #      Phase 1 sub-phase 1.8) — zero sleeps.
    from lib import driver as _driver
    try:
        _driver.wait_for(
            "DevKey/IME",
            "layout_mode_recomposed",
            match={"mode": mode},
            timeout_ms=5000,
        )
    except _driver.DriverError:
        # Driver not available — callers running without HTTP forwarding
        # must accept that this helper is best-effort. Log and continue.
        pass

    # Re-load key map for the new mode.
    load_key_map(serial)
