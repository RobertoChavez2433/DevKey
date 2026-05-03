"""
Macros panel smoke test.

FROM PLAN: Phase 2 sub-phase 4.7 — "tap macros button → wait_for DevKey/UI
           panel_opened{panel=macros} → dismiss → assert no crash."

Strategy:
  - Open macros panel via SET_KEYBOARD_MODE broadcast (avoids toolbar
    coordinate dependency).
  - Assert panel_opened event fires with structural-only payload.
  - Dismiss via RESET_KEYBOARD_MODE and verify IME survives.
"""
import subprocess
from lib import adb, keyboard, driver
from lib.privacy import allowed_payload_keys


def test_macros_panel_opens():
    """Open macros grid via broadcast → panel_opened fires → dismiss cleanly."""
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
    allowed = allowed_payload_keys("panel")
    assert set(data.keys()).issubset(allowed), (
        f"panel_opened payload had unexpected keys: {set(data.keys()) - allowed}. "
        f"PRIVACY: must be structural-only (panel name plus trace metadata)."
    )

    # Dismiss via RESET_KEYBOARD_MODE → Normal.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})

    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after dismissing macros panel"
