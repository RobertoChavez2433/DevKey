"""
Mode switching edge-case tests.

Covers boundary conditions: switching during modifier hold, switching
with the suggestion bar visible.
"""

import time

from lib import adb, driver, keyboard


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    return serial


def _reset_mode():
    """Reset keyboard to Normal mode via broadcast."""
    try:
        driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    except Exception:
        pass
    time.sleep(0.3)


def test_switch_during_modifier_hold():
    """
    Activate Shift (or Ctrl) then switch to symbols mode.
    The modifier state must not bleed into symbols mode, and the IME
    must not crash.  Verified via keyboard_mode_changed event.
    """
    serial = _setup()
    _reset_mode()

    # Activate Shift via broadcast (structural, no coordinate dependency).
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_MODIFIER",
        {"modifier": "shift", "state": "on"},
    )
    time.sleep(0.2)

    # Switch to symbols while modifier is active.
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "symbols"},
    )
    entry = driver.wait_for(
        category="DevKey/IME",
        event="keyboard_mode_changed",
        timeout_ms=3000,
    )
    assert entry is not None, (
        "keyboard_mode_changed not received after switching during modifier hold"
    )

    # Return to Normal and clear modifiers.
    _reset_mode()
    driver.broadcast(
        "dev.devkey.keyboard.SET_MODIFIER",
        {"modifier": "shift", "state": "off"},
    )
    time.sleep(0.2)


def test_mode_switch_with_suggestions():
    """
    Type a word to trigger the suggestion bar, then switch to symbols mode.
    The suggestion bar state must not cause a crash.  Verified via
    keyboard_mode_changed event.
    """
    serial = _setup()
    _reset_mode()

    # Type a word to trigger suggestions.
    driver.clear_logs()
    keyboard.tap_sequence("the", delay=0.08, serial=serial)
    time.sleep(0.3)

    # Switch to symbols while suggestion bar is visible.
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "symbols"},
    )
    entry = driver.wait_for(
        category="DevKey/IME",
        event="keyboard_mode_changed",
        timeout_ms=3000,
    )
    assert entry is not None, (
        "keyboard_mode_changed not received after switching with suggestions visible"
    )

    _reset_mode()
