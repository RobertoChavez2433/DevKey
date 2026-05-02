"""Key-map loading and cache access for DevKey E2E tests."""
import re
import subprocess
from typing import Any, Dict, List, Optional, Tuple

from . import adb, verify
from . import keyboard_state as state


def load_key_map(serial: Optional[str] = None) -> Dict[str, Tuple[int, int]]:
    """
    Load the key coordinate map from DevKey's KeyMapGenerator logcat output.

    The app dumps key positions to logcat with tag 'DevKeyMap' in response to
    a debug broadcast.
    """
    adb.clear_logcat(serial)
    subprocess.run(
        adb._adb_cmd(["shell", "am", "broadcast", "-a", "dev.devkey.keyboard.DUMP_KEY_MAP"], serial),
        check=True,
        capture_output=True,
    )
    _wait_for_keymap_dump()

    key_map, symbols_map, inventory = _parse_key_map_lines(
        adb.capture_logcat("DevKeyMap", timeout=2.0, serial=serial)
    )
    state.key_map = key_map
    state.symbols_key_map = symbols_map
    state.key_inventory = inventory
    if key_map or symbols_map:
        verify.record_evidence("keyboard.key_map_loaded", {
            "normal_size": len(key_map),
            "symbols_size": len(symbols_map),
            "visible_records": len(inventory),
        })
    return key_map


def get_key_inventory() -> List[Dict[str, Any]]:
    """Return visible key records from the most recent key-map dump."""
    return list(state.key_inventory)


def get_symbols_key_map() -> Dict[str, Tuple[int, int]]:
    """Return the cached symbols-layer key map."""
    return state.symbols_key_map


def get_key_map() -> Dict[str, Tuple[int, int]]:
    """Return the cached key map. Call load_key_map() first."""
    return state.key_map


def clear_key_map_cache() -> None:
    """Clear the cached key map so the next load_key_map() re-dumps from the IME."""
    state.clear()


def _wait_for_keymap_dump() -> None:
    from lib import driver

    try:
        driver.wait_for("DevKey/IME", "keymap_dump_complete", timeout_ms=3000)
    except driver.DriverError:
        pass


def _parse_key_map_lines(lines: List[str]) -> Tuple[
    Dict[str, Tuple[int, int]],
    Dict[str, Tuple[int, int]],
    List[Dict[str, Any]],
]:
    key_map: Dict[str, Tuple[int, int]] = {}
    symbols_map: Dict[str, Tuple[int, int]] = {}
    inventory: List[Dict[str, Any]] = []
    pattern = re.compile(r"(SYM_KEY|KEY) label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)")
    for line in lines:
        match = pattern.search(line)
        if match:
            _add_key_record(match, key_map, symbols_map, inventory)
    return key_map, symbols_map, inventory


def _add_key_record(
    match: re.Match,
    key_map: Dict[str, Tuple[int, int]],
    symbols_map: Dict[str, Tuple[int, int]],
    inventory: List[Dict[str, Any]],
) -> None:
    kind = match.group(1)
    label = match.group(2)
    code = int(match.group(3))
    x = int(match.group(4))
    y = int(match.group(5))
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
