"""
Macros panel smoke test.

FROM PLAN: Phase 2 sub-phase 4.7 — "tap macros button → wait_for DevKey/UI
           panel_opened{panel=macros} → dismiss → assert no crash."

Depends on:
  - A DevKeyLogger.ui("panel_opened", mapOf("panel" to "macros")) emit site at
    the macros panel composition entry point. PRIVACY: structural only —
    NEVER log macro names or bodies.
"""
import subprocess
from lib import adb, keyboard, driver

MACROS_LABEL_CANDIDATES = ["Macros", "macros", "\u2699"]  # gear


def _find_macros_key():
    km = keyboard.get_key_map()
    for candidate in MACROS_LABEL_CANDIDATES:
        if candidate in km:
            return candidate, km[candidate]
    return None, None


def test_macros_panel_opens():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    label, coords = _find_macros_key()
    if coords is None:
        import pytest
        pytest.skip("macros toolbar key not in current key map")

    driver.clear_logs()
    driver.tap(coords[0], coords[1])
    entry = driver.wait_for(
        category="DevKey/UI",
        event="panel_opened",
        match={"panel": "macros"},
        timeout_ms=2000,
    )
    data = entry.get("data", {})
    assert set(data.keys()).issubset({"panel"}), (
        f"panel_opened payload had unexpected keys: {set(data.keys()) - {'panel'}}. "
        f"PRIVACY: must be structural-only (panel name)."
    )

    back_cmd = ["adb"]
    if serial:
        back_cmd += ["-s", serial]
    back_cmd += ["shell", "input", "keyevent", "KEYCODE_BACK"]
    subprocess.run(back_cmd, check=True, capture_output=True)

    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after dismissing macros panel"
