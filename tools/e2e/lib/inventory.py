"""Canonical key and action inventory generation for DevKey E2E checks."""
import json
from datetime import datetime, timezone
from typing import Any, Dict, Optional, Set

from .paths import LONG_PRESS_EXPECTATIONS_DIR, PROJECT_ROOT
from .preflight import device_metadata


def generate_inventory_payload(serial: Optional[str], preflight: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    from lib import keyboard

    started_at = datetime.now(timezone.utc)
    inventory = keyboard.generate_canonical_inventory(serial)
    action_inventory = generate_action_inventory(inventory)
    ended_at = datetime.now(timezone.utc)
    inventory.update({
        "started_at": started_at.isoformat(),
        "ended_at": ended_at.isoformat(),
        "duration_seconds": round((ended_at - started_at).total_seconds(), 3),
        "device": device_metadata(serial),
        "preflight": preflight,
        "action_inventory": action_inventory,
    })
    return inventory


def generate_action_inventory(inventory: Dict[str, Any]) -> Dict[str, Any]:
    expected_modes = ("full", "compact", "compact_dev", "symbols")
    modes: Dict[str, Any] = {}
    failures = []
    total_keys = 0
    total_actions = 0

    for mode in expected_modes:
        path = LONG_PRESS_EXPECTATIONS_DIR / f"{mode}.json"
        visible_labels = _visible_labels_for_action_mode(inventory, mode)
        if not path.exists():
            failures.append(f"{mode}: long-press expectation file missing")
            modes[mode] = {
                "source": str(path.relative_to(PROJECT_ROOT)),
                "keys_with_actions": 0,
                "configured_actions": 0,
                "actions": [],
            }
            continue

        try:
            entries = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            failures.append(f"{mode}: invalid long-press expectation JSON: {exc}")
            entries = []

        actions = []
        for index, entry in enumerate(entries):
            label = entry.get("label") if isinstance(entry, dict) else None
            lp_codes = entry.get("lp_codes") if isinstance(entry, dict) else None
            record_id = f"{mode}[{index}]"

            if not isinstance(label, str) or not label:
                failures.append(f"{record_id}: label is missing")
                label = ""
            if not isinstance(lp_codes, list) or not all(isinstance(code, int) for code in lp_codes):
                failures.append(f"{record_id}: lp_codes must be integer list")
                lp_codes = []
            if label and label not in visible_labels:
                failures.append(f"{record_id}: label {label!r} is not visible in inventory")

            total_keys += 1
            total_actions += len(lp_codes)
            actions.append({
                "label": label,
                "lp_codes": lp_codes,
                "action_count": len(lp_codes),
                "visible": bool(label and label in visible_labels),
            })

        modes[mode] = {
            "source": str(path.relative_to(PROJECT_ROOT)),
            "keys_with_actions": len(actions),
            "configured_actions": sum(action["action_count"] for action in actions),
            "actions": actions,
        }

    return {
        "schema_version": 1,
        "coverage": {
            "ok": not failures,
            "tested_modes": len(expected_modes),
            "keys_with_actions": total_keys,
            "configured_actions": total_actions,
            "failures": failures,
        },
        "long_press": {
            "modes": modes,
        },
    }


def _visible_labels_for_action_mode(inventory: Dict[str, Any], mode: str) -> Set[str]:
    target_layout = "full" if mode == "symbols" else mode
    target_layer = "symbols" if mode == "symbols" else "normal"
    for layout in inventory.get("layouts", []):
        if layout.get("layout") != target_layout:
            continue
        return {
            record.get("label")
            for record in layout.get("layers", {}).get(target_layer, [])
            if record.get("label")
        }
    return set()
