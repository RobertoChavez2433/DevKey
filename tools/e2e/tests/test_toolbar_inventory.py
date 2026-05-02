"""Toolbar inventory and dynamic action validation."""
from lib import adb, driver
from lib.toolbar_inventory import collect_toolbar_inventory


def test_toolbar_inventory_buttons_drive_state():
    serial = adb.get_device_serial()
    inventory = collect_toolbar_inventory(serial)
    assert inventory["coverage"]["ok"], inventory["coverage"]["failures"]

    _tap_control(serial, "clipboard")
    driver.wait_for("DevKey/UI", "panel_opened", match={"panel": "clipboard"}, timeout_ms=3000)
    _reset_mode()

    _tap_control(serial, "symbols")
    entry = driver.wait_for("DevKey/IME", "keyboard_mode_changed", match={"action": "toggle"}, timeout_ms=3000)
    assert "Symbols" in entry.get("data", {}).get("to", "")
    _reset_mode()

    _tap_control(serial, "macros")
    entry = driver.wait_for("DevKey/IME", "keyboard_mode_changed", match={"action": "toggle"}, timeout_ms=3000)
    assert "MacroChips" in entry.get("data", {}).get("to", "")
    _reset_mode()

    _long_press_control(serial, "macros")
    driver.wait_for("DevKey/UI", "panel_opened", match={"panel": "macros"}, timeout_ms=3000)
    _reset_mode()

    _tap_control(serial, "overflow")
    driver.wait_for("DevKey/IME", "command_mode_manual_enabled", match={"trigger": "user_toggle"}, timeout_ms=3000)
    _tap_control(serial, "overflow")
    driver.wait_for(
        "DevKey/IME",
        "command_mode_manual_disabled",
        match={"trigger": "user_toggle"},
        timeout_ms=3000,
    )

    _tap_control(serial, "voice")
    driver.wait_for("DevKey/VOX", "state_transition", match={"state": "LISTENING"}, timeout_ms=5000)
    _reset_mode()

    _tap_control(serial, "toolbar_chevron")
    driver.wait_for(
        "DevKey/UI",
        "toolbar_control_visible",
        match={"id": "toolbar_chevron", "active": False},
        timeout_ms=3000,
    )
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


def _tap_control(serial, control_id):
    control = _fresh_control(serial, control_id)
    bounds = control["bounds"]
    driver.clear_logs()
    adb.tap(bounds["center_x"], bounds["center_y"], serial)
    driver.wait_for(
        "DevKey/UI",
        "toolbar_action",
        match={"id": control["id"], "action": control["action"]},
        timeout_ms=3000,
    )


def _long_press_control(serial, control_id):
    control = _fresh_control(serial, control_id)
    bounds = control["bounds"]
    driver.clear_logs()
    adb.swipe(
        bounds["center_x"],
        bounds["center_y"],
        bounds["center_x"],
        bounds["center_y"],
        duration_ms=800,
        serial=serial,
    )
    driver.wait_for(
        "DevKey/UI",
        "toolbar_action",
        match={"id": control["id"], "action": control["long_action"]},
        timeout_ms=3000,
    )


def _reset_mode():
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for("DevKey/IME", "keyboard_mode_reset", timeout_ms=3000)


def _fresh_control(serial, control_id):
    inventory = collect_toolbar_inventory(serial)
    assert inventory["coverage"]["ok"], inventory["coverage"]["failures"]
    controls = {control["id"]: control for control in inventory["controls"]}
    return controls[control_id]
