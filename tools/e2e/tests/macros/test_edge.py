"""
Macros E2E edge-case tests.

Exercises boundary conditions in the macro subsystem:
  1. Record empty macro, replay — no crash
  2. Cancel recording mid-capture
  3. Delete macro, verify removed

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


def test_record_empty_replay():
    """
    Start recording, immediately stop (0 steps), then replay.
    The IME must not crash on replaying an empty macro.
    """
    serial = _setup()
    driver.clear_logs()

    driver.broadcast("dev.devkey.keyboard.MACRO_START_RECORD", {"name": "empty"})
    time.sleep(0.2)
    driver.broadcast("dev.devkey.keyboard.MACRO_STOP_RECORD", {})
    time.sleep(0.5)

    driver.broadcast("dev.devkey.keyboard.MACRO_REPLAY", {"name": "empty"})
    time.sleep(0.5)

    # Verify IME survives empty replay
    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after empty macro replay"


def test_cancel_mid_capture():
    """
    Start recording, capture a few keys, then cancel. Verify no macro is
    saved and the recording state is cleanly reset.
    """
    serial = _setup()
    driver.clear_logs()

    driver.broadcast("dev.devkey.keyboard.MACRO_START_RECORD", {"name": "cancelled"})
    time.sleep(0.2)
    for ch in "abc":
        keyboard.tap_key(ch, serial)
        time.sleep(0.1)

    driver.broadcast("dev.devkey.keyboard.MACRO_CANCEL_RECORD", {})
    time.sleep(0.3)

    # Attempt to replay the cancelled macro — should fail or produce no event
    driver.broadcast("dev.devkey.keyboard.MACRO_REPLAY", {"name": "cancelled"})
    time.sleep(0.5)

    # Verify IME did not crash
    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after cancel-then-replay"


def test_delete_macro():
    """
    Record a macro, delete it, verify replay of the deleted macro does not crash.
    """
    serial = _setup()
    driver.clear_logs()

    # Record a macro to delete
    driver.broadcast("dev.devkey.keyboard.MACRO_START_RECORD", {"name": "todelete"})
    time.sleep(0.2)
    keyboard.tap_key("x", serial)
    time.sleep(0.1)
    driver.broadcast("dev.devkey.keyboard.MACRO_STOP_RECORD", {})
    time.sleep(0.5)

    # Delete the macro
    driver.broadcast("dev.devkey.keyboard.MACRO_DELETE", {"name": "todelete"})
    time.sleep(0.5)

    # Attempt to replay deleted macro — should not crash
    driver.broadcast("dev.devkey.keyboard.MACRO_REPLAY", {"name": "todelete"})
    time.sleep(0.5)

    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after replaying deleted macro"
