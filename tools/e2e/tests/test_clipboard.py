"""
Clipboard panel smoke test.

FROM PLAN: Phase 2 sub-phase 4.6 — "tap clipboard toolbar button → wait_for
           DevKey/UI panel_opened{panel=clipboard} → dismiss → assert no crash."

Strategy:
  - Open clipboard panel via SET_KEYBOARD_MODE broadcast (avoids toolbar
    coordinate dependency).
  - Assert panel_opened event fires with structural-only payload.
  - Dismiss via RESET_KEYBOARD_MODE and verify IME survives.
"""
import subprocess
from lib import adb, keyboard, driver


def test_clipboard_panel_opens():
    """Open clipboard panel via broadcast → panel_opened fires → dismiss cleanly."""
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

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

    # Dismiss via RESET_KEYBOARD_MODE → Normal.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})

    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after dismissing clipboard panel"
