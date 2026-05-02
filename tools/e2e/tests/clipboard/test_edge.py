"""
Clipboard edge-case tests.

Covers boundary conditions: paste from empty clipboard, very long entries,
and special characters.  All assertions are structural -- never inspect
clipboard text content (privacy rule).
"""

import subprocess
import time

from lib import adb, driver, keyboard


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
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


def test_paste_empty_clipboard():
    """
    Attempt to paste when clipboard is empty.
    The IME must not crash.  Verified via process-alive check.
    """
    serial = _setup()

    # Clear all clipboard entries first.
    driver.broadcast("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL", {})
    driver.wait_for(
        "DevKey/UI",
        "clipboard_clear_all",
        match={"entry_count": 0, "success": True},
        timeout_ms=3000,
    )

    # Attempt paste from empty clipboard.
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.CLIPBOARD_PASTE",
        {"entry_index": 0},
    )
    entry = driver.wait_for(
        "DevKey/UI",
        "clipboard_paste",
        match={"entry_count": 0, "success": False},
        timeout_ms=3000,
    )
    assert entry["data"]["content_length"] == 0

    # IME should still be alive.
    _assert_ime_alive(serial)


def test_very_long_entry():
    """
    Add a very long clipboard entry (via broadcast with large length hint).
    The IME must not crash.  Asserts entry_count only -- never content.
    """
    serial = _setup()

    driver.broadcast("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL", {})
    driver.wait_for(
        "DevKey/UI",
        "clipboard_clear_all",
        match={"entry_count": 0, "success": True},
        timeout_ms=3000,
    )

    # Broadcast a long-entry add signal.  The actual content is injected
    # via adb shell, not visible in the test assertion.
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.CLIPBOARD_ADD",
        {"entry_index": 0, "length_hint": 5000},
    )
    entry = driver.wait_for(
        "DevKey/UI",
        "clipboard_add",
        match={"entry_count": 1, "success": True},
        timeout_ms=3000,
    )
    assert entry["data"]["content_length"] == 5000

    # Verify IME survived the long-entry add without crashing.
    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after long-entry add"


def test_special_chars():
    """
    Add a clipboard entry with special characters (via broadcast).
    The IME must not crash.  Asserts entry_count only -- never inspects
    the actual characters stored.
    """
    serial = _setup()

    driver.broadcast("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL", {})
    driver.wait_for(
        "DevKey/UI",
        "clipboard_clear_all",
        match={"entry_count": 0, "success": True},
        timeout_ms=3000,
    )

    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.CLIPBOARD_ADD",
        {"entry_index": 0, "special_chars": True},
    )
    entry = driver.wait_for(
        "DevKey/UI",
        "clipboard_add",
        match={"entry_count": 1, "success": True},
        timeout_ms=3000,
    )
    assert entry["data"]["content_length"] > 0

    # Verify IME survived the special-chars add without crashing.
    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after special-chars add"
