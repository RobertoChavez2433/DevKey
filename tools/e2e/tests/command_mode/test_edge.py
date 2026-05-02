"""
Command mode E2E edge-case tests.

Exercises boundary conditions in the command mode detector:
  1. Unknown package name — verify fallback to NORMAL
  2. Toggle override on/off rapidly

Depends on:
  - TOGGLE_COMMAND_MODE broadcast
  - SIMULATE_FOCUS_CHANGE broadcast
  - HTTP forwarding enabled

PRIVACY: structural only — NEVER log command buffers, package names, or typed content.
"""
import subprocess
import time
from lib import adb, driver, keyboard


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
    return serial


def _wait_command(event, match=None, timeout_ms=3000):
    return driver.wait_for(
        category="DevKey/IME",
        event=event,
        match=match or {},
        timeout_ms=timeout_ms,
    )


def test_unknown_package():
    """
    Simulate focus to a completely unknown package name. The detector must
    fall back to NORMAL mode without crashing.
    """
    serial = _setup()
    driver.clear_logs()

    driver.broadcast(
        "dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE",
        {"package": "com.unknown.nonexistent.app.xyz"},
    )
    _wait_command(
        "command_mode_focus_processed",
        {"mode": "normal", "terminal_detected": False},
    )

    # Verify IME did not crash
    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after unknown package focus"


def test_toggle_rapid():
    """
    Toggle command mode override on/off 20 times rapidly. The IME must
    survive without crashing or entering an inconsistent state.
    """
    serial = _setup()

    driver.clear_logs()
    for i in range(20):
        driver.broadcast("dev.devkey.keyboard.TOGGLE_COMMAND_MODE", {})
        expected_mode = "command" if i % 2 == 0 else "normal"
        _wait_command(
            "command_mode_toggle_processed",
            {"mode": expected_mode, "trigger": "user_toggle"},
            timeout_ms=3000,
        )

    # Final toggle off to leave clean state (even number of toggles = back to start)
    time.sleep(0.3)

    # Verify IME is still alive
    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after rapid command-mode toggles"

    driver.require_driver()
