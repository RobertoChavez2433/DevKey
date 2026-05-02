"""
Clipboard stress tests.

Exercises the clipboard panel under sustained open/close cycling,
repeated paste, max-entry eviction, and pin-survives-clear scenarios.
All assertions use structural events and counts -- never clipboard content.
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


def _open_clipboard():
    """Open clipboard panel via broadcast."""
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "clipboard"},
    )
    driver.wait_for(
        category="DevKey/UI",
        event="panel_opened",
        match={"panel": "clipboard"},
        timeout_ms=3000,
    )


def _close_clipboard():
    """Dismiss clipboard panel via broadcast."""
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.2)


def _assert_ime_alive(serial):
    """Assert the IME process is still running."""
    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died"


def test_open_close_panel_10x():
    """
    Open and close the clipboard panel 10 times.
    Verify IME survives each cycle via process-alive check.
    """
    serial = _setup()

    for i in range(10):
        driver.clear_logs()
        _open_clipboard()
        _close_clipboard()

    _assert_ime_alive(serial)


def test_paste_same_entry_5x():
    """
    Open clipboard panel, trigger paste action 5 times via broadcast.
    Verify via clipboard_paste event count -- never check pasted content.
    """
    serial = _setup()

    driver.broadcast("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL", {})
    driver.wait_for(
        "DevKey/UI",
        "clipboard_clear_all",
        match={"entry_count": 0, "success": True},
        timeout_ms=3000,
    )

    # Seed clipboard with one entry via broadcast.
    driver.broadcast(
        "dev.devkey.keyboard.CLIPBOARD_ADD",
        {"entry_index": 0},
    )
    driver.wait_for(
        "DevKey/UI",
        "clipboard_add",
        match={"entry_count": 1, "success": True},
        timeout_ms=3000,
    )

    for _ in range(5):
        driver.broadcast(
            "dev.devkey.keyboard.CLIPBOARD_PASTE",
            {"entry_index": 0},
        )
        driver.wait_for(
            "DevKey/UI",
            "clipboard_paste",
            match={"entry_index": 0, "success": True},
            timeout_ms=3000,
        )

    # Verify IME survived 5 rapid paste actions without crashing.
    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after 5 paste actions"


def test_fill_to_max_entries():
    """
    Add entries up to MAX_ENTRIES and verify eviction fires.
    Asserts entry_count in the clipboard_state event -- never content.
    """
    serial = _setup()

    driver.broadcast("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL", {})
    driver.wait_for(
        "DevKey/UI",
        "clipboard_clear_all",
        match={"entry_count": 0, "success": True},
        timeout_ms=3000,
    )

    # Add 51 entries to trigger eviction past MAX_ENTRIES (50).
    for i in range(51):
        driver.broadcast(
            "dev.devkey.keyboard.CLIPBOARD_ADD",
            {"entry_index": i},
        )
        time.sleep(0.05)

    driver.wait_for(
        "DevKey/UI",
        "clipboard_add",
        match={"entry_count": 50, "success": True},
        timeout_ms=5000,
    )

    # Verify IME survived mass entry addition without crashing.
    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after filling clipboard to max"


def test_pin_survive_clear():
    """
    Pin entries, clear all, verify pinned entries survive via count check.
    """
    serial = _setup()

    driver.broadcast("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL", {})
    driver.wait_for(
        "DevKey/UI",
        "clipboard_clear_all",
        match={"entry_count": 0, "success": True},
        timeout_ms=3000,
    )

    # Add entries and pin some via broadcasts.
    for i in range(5):
        driver.broadcast(
            "dev.devkey.keyboard.CLIPBOARD_ADD",
            {"entry_index": i},
        )
        time.sleep(0.05)
    driver.wait_for(
        "DevKey/UI",
        "clipboard_add",
        match={"entry_count": 5, "success": True},
        timeout_ms=3000,
    )

    # Pin entries 0 and 2.
    driver.broadcast(
        "dev.devkey.keyboard.CLIPBOARD_PIN",
        {"entry_index": 0},
    )
    driver.wait_for(
        "DevKey/UI",
        "clipboard_pin",
        match={"pinned_count": 1, "success": True},
        timeout_ms=3000,
    )
    driver.broadcast(
        "dev.devkey.keyboard.CLIPBOARD_PIN",
        {"entry_index": 2},
    )
    driver.wait_for(
        "DevKey/UI",
        "clipboard_pin",
        match={"pinned_count": 2, "success": True},
        timeout_ms=3000,
    )

    # Clear all unpinned.
    driver.broadcast("dev.devkey.keyboard.CLIPBOARD_CLEAR_UNPINNED", {})
    driver.wait_for(
        "DevKey/UI",
        "clipboard_clear_unpinned",
        match={"entry_count": 2, "pinned_count": 2, "success": True},
        timeout_ms=3000,
    )

    # Verify IME survived pin + clear cycle without crashing.
    _assert_ime_alive(serial)
    assert driver.health(), "Driver not responsive after pin-survive-clear"
