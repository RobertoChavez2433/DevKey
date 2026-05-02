"""
Command mode E2E stress tests.

Exercises the command mode detector under rapid and sustained operations:
  1. Rapid focus changes between terminal and non-terminal packages
  2. Manual override persists across focus changes within same package
  3. Five app focus switches in sequence

Depends on:
  - TOGGLE_COMMAND_MODE broadcast
  - SIMULATE_FOCUS_CHANGE broadcast
  - command_mode_manual_enabled / command_mode_auto_enabled structural events
  - HTTP forwarding enabled

PRIVACY: structural only — NEVER log command buffers, package names, or typed content.
"""
import time
from lib import adb, driver, keyboard


TERMINAL_PACKAGES = {
    "com.termux",
    "org.connectbot",
    "com.sonelli.juicessh",
    "com.server.auditor",
    "com.offsec.nethunter",
    "jackpal.androidterm",
    "yarolegovich.materialterminal",
    "com.termoneplus",
    "com.googlecode.android_scripting",
}


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    # Reset to normal mode
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
    return serial


def _wait_focus_processed(package_name, expected_mode=None, timeout_ms=3000):
    if expected_mode is None:
        expected_mode = "command" if package_name in TERMINAL_PACKAGES else "normal"
    return driver.wait_for(
        category="DevKey/IME",
        event="command_mode_focus_processed",
        match={
            "mode": expected_mode,
            "terminal_detected": package_name in TERMINAL_PACKAGES,
        },
        timeout_ms=timeout_ms,
    )


def _wait_toggle(expected_mode, timeout_ms=3000):
    return driver.wait_for(
        category="DevKey/IME",
        event="command_mode_toggle_processed",
        match={"mode": expected_mode, "trigger": "user_toggle"},
        timeout_ms=timeout_ms,
    )


def test_rapid_focus_changes():
    """
    Rapidly switch between terminal and non-terminal package focus 10 times.
    Verify the IME stays responsive and does not crash.
    """
    serial = _setup()

    packages = [
        "com.termux",
        "com.example.notes",
        "org.connectbot",
        "com.example.calculator",
        "jackpal.androidterm",
    ]

    for i in range(10):
        pkg = packages[i % len(packages)]
        driver.broadcast(
            "dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE",
            {"package": pkg},
        )
        _wait_focus_processed(pkg)

    # Verify the IME is still alive
    driver.require_driver()


def test_manual_override_persists():
    """
    Set manual override to COMMAND mode, then simulate focus changes within
    the same package. Verify the override persists (no app switch clears it).
    """
    serial = _setup()
    driver.clear_logs()

    # Simulate initial focus
    driver.broadcast(
        "dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE",
        {"package": "com.example.notes"},
    )
    _wait_focus_processed("com.example.notes", expected_mode="normal")

    # Toggle command mode manually
    driver.broadcast("dev.devkey.keyboard.TOGGLE_COMMAND_MODE", {})
    entry = driver.wait_for(
        category="DevKey/IME",
        event="command_mode_manual_enabled",
        timeout_ms=3000,
    )
    assert entry["data"]["trigger"] == "user_toggle"

    # Re-focus same package — override should persist
    driver.broadcast(
        "dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE",
        {"package": "com.example.notes"},
    )
    _wait_focus_processed("com.example.notes", expected_mode="command")

    # Verify IME is still responsive
    driver.require_driver()

    # Clean up — toggle off
    driver.broadcast("dev.devkey.keyboard.TOGGLE_COMMAND_MODE", {})
    _wait_toggle("normal")


def test_5_app_switches():
    """
    Perform 5 sequential app focus switches and verify the IME processes
    each switch without crashing.
    """
    serial = _setup()

    apps = [
        "com.termux",
        "com.example.browser",
        "com.sonelli.juicessh",
        "com.example.email",
        "com.offsec.nethunter",
    ]

    for pkg in apps:
        driver.clear_logs()
        driver.broadcast(
            "dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE",
            {"package": pkg},
        )
        _wait_focus_processed(pkg)

    # Verify IME survived all switches
    driver.require_driver()
