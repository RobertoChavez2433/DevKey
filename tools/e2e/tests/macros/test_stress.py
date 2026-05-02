"""
Macros E2E stress tests.

Exercises the macro engine under sustained and rapid operations:
  1. Record 20 key steps, replay, verify IME health
  2. Replay same macro 5 times rapidly
  3. Record macro with Ctrl/Shift modifiers
  4. Record, rename, replay

Depends on:
  - Macro panel broadcast infrastructure
  - HTTP forwarding enabled

PRIVACY: structural only — NEVER log macro contents or typed text.
"""
import subprocess
import time
from lib import adb, keyboard, driver


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
    return serial


def _assert_ime_alive(serial):
    """Assert the IME process is still running."""
    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died"


def _wait_macro(event, match=None, timeout_ms=5000):
    return driver.wait_for(
        category="DevKey/UI",
        event=event,
        match=match or {},
        timeout_ms=timeout_ms,
    )


def test_record_20_step_replay():
    """
    Record a macro with 20 key steps, stop recording, replay.
    Verify the IME stays alive through the entire sequence.
    """
    serial = _setup()
    driver.clear_logs()

    driver.broadcast("dev.devkey.keyboard.MACRO_START_RECORD", {"name": "stress20"})
    _wait_macro("macro_record_start", {"success": True, "recording": True})

    for i in range(20):
        keyboard.tap_key(chr(ord("a") + (i % 26)), serial)
        time.sleep(0.1)

    driver.broadcast("dev.devkey.keyboard.MACRO_STOP_RECORD", {})
    _wait_macro("macro_record_stop", {"success": True, "step_count": 20, "recording": False})

    _assert_ime_alive(serial)

    driver.broadcast("dev.devkey.keyboard.MACRO_REPLAY", {"name": "stress20"})
    _wait_macro("macro_replay", {"success": True, "step_count": 20})

    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after 20-step record+replay"


def test_replay_5x_rapidly():
    """
    Record a short macro, then replay it 5 times rapidly.
    Verify the IME stays responsive after each replay.
    """
    serial = _setup()
    driver.clear_logs()

    driver.broadcast("dev.devkey.keyboard.MACRO_START_RECORD", {"name": "rapid5"})
    _wait_macro("macro_record_start", {"success": True, "recording": True})
    for ch in "abc":
        keyboard.tap_key(ch, serial)
        time.sleep(0.1)
    driver.broadcast("dev.devkey.keyboard.MACRO_STOP_RECORD", {})
    _wait_macro("macro_record_stop", {"success": True, "step_count": 3, "recording": False})

    for _ in range(5):
        driver.broadcast("dev.devkey.keyboard.MACRO_REPLAY", {"name": "rapid5"})
        _wait_macro("macro_replay", {"success": True, "step_count": 3})

    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after 5 rapid replays"


def test_record_with_modifiers():
    """
    Record a macro that includes Ctrl+C and Ctrl+V modifier key sequences.
    Verify the IME does not crash.
    """
    serial = _setup()
    driver.clear_logs()

    driver.broadcast("dev.devkey.keyboard.MACRO_START_RECORD", {"name": "modmacro"})
    _wait_macro("macro_record_start", {"success": True, "recording": True})

    # Send modifier key events via broadcast
    driver.broadcast(
        "dev.devkey.keyboard.MACRO_INJECT_KEY",
        {"key": "c", "keyCode": 99, "modifiers": "ctrl"},
    )
    _wait_macro("macro_inject_key", {"success": True, "step_count": 1, "modifier_count": 1})
    driver.broadcast(
        "dev.devkey.keyboard.MACRO_INJECT_KEY",
        {"key": "v", "keyCode": 118, "modifiers": "ctrl"},
    )
    _wait_macro("macro_inject_key", {"success": True, "step_count": 2, "modifier_count": 1})

    driver.broadcast("dev.devkey.keyboard.MACRO_STOP_RECORD", {})
    _wait_macro("macro_record_stop", {"success": True, "step_count": 2, "recording": False})

    driver.broadcast("dev.devkey.keyboard.MACRO_REPLAY", {"name": "modmacro"})
    _wait_macro("macro_replay", {"success": True, "step_count": 2})

    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after modifier macro replay"


def test_record_rename_replay():
    """
    Record a macro, rename it, then replay using the new name.
    Verify the IME stays alive through the rename+replay cycle.
    """
    serial = _setup()
    driver.clear_logs()

    driver.broadcast("dev.devkey.keyboard.MACRO_START_RECORD", {"name": "oldname"})
    _wait_macro("macro_record_start", {"success": True, "recording": True})
    for ch in "xy":
        keyboard.tap_key(ch, serial)
        time.sleep(0.1)
    driver.broadcast("dev.devkey.keyboard.MACRO_STOP_RECORD", {})
    _wait_macro("macro_record_stop", {"success": True, "step_count": 2, "recording": False})

    driver.broadcast(
        "dev.devkey.keyboard.MACRO_RENAME",
        {"old_name": "oldname", "new_name": "newname"},
    )
    _wait_macro("macro_rename", {"success": True})

    driver.broadcast("dev.devkey.keyboard.MACRO_REPLAY", {"name": "newname"})
    _wait_macro("macro_replay", {"success": True, "step_count": 2})

    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after rename+replay"
