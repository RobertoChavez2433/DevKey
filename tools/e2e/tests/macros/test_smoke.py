"""
Macros panel smoke tests — migrated from test_macros.py.

Covers the basic macros panel lifecycle:
  1. Open macros panel via broadcast
  2. Verify panel_opened event with structural-only payload
  3. Dismiss and verify IME survives

PRIVACY: structural only — NEVER log macro contents or typed text.
"""
import subprocess
from lib import adb, keyboard, driver


def test_macros_panel_opens():
    """Open macros grid via broadcast -> panel_opened fires -> dismiss cleanly."""
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "macro_grid"},
    )
    entry = driver.wait_for(
        category="DevKey/UI",
        event="panel_opened",
        match={"panel": "macros"},
        timeout_ms=3000,
    )
    data = entry.get("data", {})
    assert set(data.keys()).issubset({"panel"}), (
        f"panel_opened payload had unexpected keys: {set(data.keys()) - {'panel'}}. "
        f"PRIVACY: must be structural-only (panel name)."
    )

    # Dismiss via RESET_KEYBOARD_MODE -> Normal.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})

    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after dismissing macros panel"
