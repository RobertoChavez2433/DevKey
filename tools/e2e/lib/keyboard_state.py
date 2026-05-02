"""Shared keyboard helper cache state."""
from typing import Any, Dict, List, Tuple

key_map: Dict[str, Tuple[int, int]] = {}
symbols_key_map: Dict[str, Tuple[int, int]] = {}
key_inventory: List[Dict[str, Any]] = []
y_offset: int = 0


def clear() -> None:
    global key_map, symbols_key_map, key_inventory, y_offset
    key_map = {}
    symbols_key_map = {}
    key_inventory = []
    y_offset = 0
