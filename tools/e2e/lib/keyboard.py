"""Compatibility facade for DevKey E2E keyboard helpers."""

from .keyboard_inventory import generate_canonical_inventory
from .keyboard_keymap import (
    clear_key_map_cache,
    get_key_inventory,
    get_key_map,
    get_symbols_key_map,
    load_key_map,
)
from .keyboard_layout import set_layout_mode
from .keyboard_tap import (
    calibrate_y_offset,
    tap_key,
    tap_key_by_code,
    tap_sequence,
    tap_symbols_key_by_code,
)

__all__ = [
    "calibrate_y_offset",
    "clear_key_map_cache",
    "generate_canonical_inventory",
    "get_key_inventory",
    "get_key_map",
    "get_symbols_key_map",
    "load_key_map",
    "set_layout_mode",
    "tap_key",
    "tap_key_by_code",
    "tap_sequence",
    "tap_symbols_key_by_code",
]
