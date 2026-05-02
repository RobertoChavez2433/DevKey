"""Runtime toolbar and dynamic-panel inventory for DevKey E2E tests."""
from typing import Any, Dict, List, Optional


EXPECTED_TOOLBAR_CONTROLS = {
    "toolbar_chevron": {
        "action": "toggle_toolbar",
        "active": True,
        "required": True,
    },
    "clipboard": {
        "action": "toggle_clipboard",
        "required": True,
        "opens_panel": "clipboard",
    },
    "voice": {
        "action": "toggle_voice",
        "required": True,
        "opens_mode": "Voice",
    },
    "symbols": {
        "action": "toggle_symbols",
        "required": True,
        "opens_mode": "Symbols",
    },
    "macros": {
        "action": "toggle_macro_chips",
        "long_action": "toggle_macro_grid",
        "required": True,
        "opens_mode": "MacroChips",
        "long_opens_panel": "macros",
    },
    "overflow": {
        "action": "toggle_command_mode",
        "required": True,
        "opens_mode": "command",
    },
}


DYNAMIC_PANEL_INVENTORY = [
    {
        "id": "clipboard",
        "entry_action": "toggle_clipboard",
        "evidence_category": "DevKey/UI",
        "evidence_event": "panel_opened",
        "evidence_match": {"panel": "clipboard"},
    },
    {
        "id": "macro_grid",
        "entry_action": "toggle_macro_grid",
        "evidence_category": "DevKey/UI",
        "evidence_event": "panel_opened",
        "evidence_match": {"panel": "macros"},
    },
    {
        "id": "macro_chips",
        "entry_action": "toggle_macro_chips",
        "evidence_category": "DevKey/IME",
        "evidence_event": "keyboard_mode_changed",
        "evidence_match": {"action": "toggle"},
    },
    {
        "id": "symbols",
        "entry_action": "toggle_symbols",
        "evidence_category": "DevKey/IME",
        "evidence_event": "keyboard_mode_changed",
        "evidence_match": {"action": "toggle"},
    },
    {
        "id": "voice",
        "entry_action": "toggle_voice",
        "evidence_category": "DevKey/VOX",
        "evidence_event": "state_transition",
        "evidence_match": {"state": "LISTENING"},
    },
    {
        "id": "command_mode",
        "entry_action": "toggle_command_mode",
        "evidence_category": "DevKey/IME",
        "evidence_event": "command_mode_manual_enabled",
        "evidence_match": {"trigger": "user_toggle"},
    },
]


def collect_toolbar_inventory(serial: Optional[str]) -> Dict[str, Any]:
    """
    Collect live toolbar bounds from Compose debug logs.

    Privacy: records structural control ids, actions, bounds, and active/enabled
    flags only. It never reads typed text, clipboard entries, transcripts, or
    macro bodies.
    """
    from lib import adb, driver

    adb.ensure_keyboard_visible(serial)
    driver.clear_logs()
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for("DevKey/IME", "keyboard_mode_reset", timeout_ms=3000)
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "devkey_show_toolbar", "value": False},
    )
    driver.wait_for(
        "DevKey/IME",
        "bool_pref_set",
        match={"key": "devkey_show_toolbar", "value": False},
        timeout_ms=3000,
    )

    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "devkey_show_toolbar", "value": True},
    )
    driver.wait_for(
        "DevKey/IME",
        "bool_pref_set",
        match={"key": "devkey_show_toolbar", "value": True},
        timeout_ms=3000,
    )

    controls = []
    failures = []
    for control_id, spec in EXPECTED_TOOLBAR_CONTROLS.items():
        match = {"id": control_id}
        if "active" in spec:
            match["active"] = spec["active"]
        try:
            entry = driver.wait_for(
                "DevKey/UI",
                "toolbar_control_visible",
                match=match,
                timeout_ms=5000,
            )
            controls.append(_normalize_toolbar_control(control_id, spec, entry.get("data", {}), failures))
        except driver.DriverError as exc:
            if spec.get("required"):
                failures.append(f"{control_id}: visible toolbar control was not reported: {exc}")

    seen = {control["id"] for control in controls}
    missing = [
        control_id
        for control_id, spec in EXPECTED_TOOLBAR_CONTROLS.items()
        if spec.get("required") and control_id not in seen
    ]
    for control_id in missing:
        failures.append(f"{control_id}: required toolbar control missing")

    return {
        "schema_version": 1,
        "source": "DevKey/UI toolbar_control_visible",
        "controls": controls,
        "coverage": {
            "ok": not failures,
            "expected_controls": len(EXPECTED_TOOLBAR_CONTROLS),
            "visible_controls": len(controls),
            "failures": failures,
        },
    }


def generate_dynamic_panel_inventory() -> Dict[str, Any]:
    return {
        "schema_version": 1,
        "panels": DYNAMIC_PANEL_INVENTORY,
        "coverage": {
            "ok": True,
            "expected_panels": len(DYNAMIC_PANEL_INVENTORY),
            "failures": [],
        },
    }


def _normalize_toolbar_control(
    control_id: str,
    spec: Dict[str, Any],
    data: Dict[str, Any],
    failures: List[str],
) -> Dict[str, Any]:
    action = data.get("action")
    if action != spec.get("action"):
        failures.append(f"{control_id}: action {action!r} != expected {spec.get('action')!r}")
    long_action = data.get("long_action")
    expected_long = spec.get("long_action")
    if expected_long and long_action != expected_long:
        failures.append(f"{control_id}: long_action {long_action!r} != expected {expected_long!r}")

    bounds = {
        "x": _int_field(control_id, data, "x", failures),
        "y": _int_field(control_id, data, "y", failures),
        "width": _positive_int_field(control_id, data, "width", failures),
        "height": _positive_int_field(control_id, data, "height", failures),
        "center_x": _int_field(control_id, data, "center_x", failures),
        "center_y": _int_field(control_id, data, "center_y", failures),
    }
    return {
        "id": control_id,
        "action": action,
        "long_action": long_action,
        "active": _bool_field(data, "active"),
        "enabled": _bool_field(data, "enabled"),
        "bounds": bounds,
        "opens_panel": spec.get("opens_panel"),
        "opens_mode": spec.get("opens_mode"),
        "long_opens_panel": spec.get("long_opens_panel"),
    }


def _int_field(control_id: str, data: Dict[str, Any], field: str, failures: List[str]) -> int:
    value = data.get(field)
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        failures.append(f"{control_id}: {field} is not an integer")
        return 0
    if parsed < 0:
        failures.append(f"{control_id}: {field} is negative")
    return parsed


def _positive_int_field(control_id: str, data: Dict[str, Any], field: str, failures: List[str]) -> int:
    parsed = _int_field(control_id, data, field, failures)
    if parsed <= 0:
        failures.append(f"{control_id}: {field} must be positive")
    return parsed


def _bool_field(data: Dict[str, Any], field: str) -> bool:
    value = data.get(field)
    if isinstance(value, bool):
        return value
    return str(value).lower() == "true"
