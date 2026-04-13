"""
Clipboard panel smoke tests (subdirectory format).

Migrated from tools/e2e/tests/test_clipboard.py -- same test logic,
relocated into the clipboard/ test package for Phase 9 organisation.
"""

import subprocess

from lib import adb, keyboard, driver


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    return serial


def test_clipboard_panel_opens():
    """Open clipboard panel via broadcast, panel_opened fires, dismiss cleanly."""
    serial = _setup()

    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "clipboard"},
    )
    entry = driver.wait_for(
        category="DevKey/UI",
        event="panel_opened",
        match={"panel": "clipboard"},
        timeout_ms=3000,
    )
    # PRIVACY: panel_opened payload must never contain clipboard contents.
    data = entry.get("data", {})
    assert set(data.keys()).issubset({"panel"}), (
        f"panel_opened payload had unexpected keys: {set(data.keys()) - {'panel'}}. "
        f"PRIVACY: must be structural-only (panel name)."
    )

    # Dismiss via RESET_KEYBOARD_MODE.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})

    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after dismissing clipboard panel"
