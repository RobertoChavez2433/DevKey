"""Canonical keyboard inventory generation for DevKey E2E tests."""
from typing import Any, Dict, List, Optional, Tuple

from .keyboard_keymap import get_key_inventory
from .keyboard_layout import set_layout_mode


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
        _validate_record(mode, index, record, seen, failures)

    return {
        "ok": not failures,
        "visible_records": len(records),
        "normal_records": len(normal),
        "symbols_records": len(symbols),
        "failures": failures,
    }


def _validate_record(
    mode: str,
    index: int,
    record: Dict[str, Any],
    seen: set,
    failures: List[str],
) -> None:
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
