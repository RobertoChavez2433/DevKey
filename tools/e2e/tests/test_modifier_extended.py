"""
Extended modifier combo tests: Ctrl+Z undo.

Uses existing key_event bridge instrumentation — no new Kotlin needed.

Depends on:
  - FULL layout mode (Ctrl key in utility row)
  - key_event instrumentation in KeyEventSender
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver

KEYCODE_CTRL_LEFT = -113


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    # Ctrl+Z requires FULL mode for the Ctrl utility row
    keyboard.set_layout_mode("full", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(0.5)
        keyboard.load_key_map(serial)
    return serial


def test_ctrl_z_undo():
    """
    Ctrl+Z: one-shot Ctrl, tap 'z', assert key_event has ctrl=True, code=122.
    """
    serial = _setup()
    driver.clear_logs()

    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    keyboard.tap_key("z", serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="key_event",
        match={"code": 122, "ctrl": True},
        timeout_ms=2000,
    )
    data = entry.get("data", {})
    assert data.get("ctrl") is True, (
        f"Ctrl+Z: expected ctrl=True in key_event payload, got {data}"
    )
