"""
Mode switching tests for DevKey keyboard.

Tests the 123/Symbols mode switch, ABC return, and other mode transitions.
"""

import time

from lib import adb, keyboard


def test_symbols_mode_switch():
    """
    Tap the 123 key and verify symbols mode activates.
    Expect: logcat shows toggleMode: Normal -> Symbols.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    # Tap 123 key (SYMBOLS = -2)
    keyboard.tap_key_by_code(-2, serial)
    time.sleep(0.5)

    adb.assert_logcat_contains(
        "DevKeyMode",
        r"toggleMode.*Normal.*Symbols",
        timeout=2.0,
        serial=serial
    )


def test_abc_returns_to_normal():
    """
    From symbols mode, tap ABC and verify return to Normal.
    Expect: logcat shows setMode to Normal.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    # First enter symbols mode
    adb.clear_logcat(serial)
    keyboard.tap_key_by_code(-2, serial)
    time.sleep(0.5)

    # Now reload key map for symbols layout
    symbols_map = keyboard.load_key_map(serial)

    # Tap ABC key (KEYCODE_ALPHA = -200)
    adb.clear_logcat(serial)
    keyboard.tap_key_by_code(-200, serial)
    time.sleep(0.5)

    adb.assert_logcat_contains(
        "DevKeyMode",
        r"setMode.*Normal",
        timeout=2.0,
        serial=serial
    )


def test_symbols_toggle_back():
    """
    Tap 123 twice (with ABC in between) to verify toggle round-trip.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    # Enter symbols
    keyboard.tap_key_by_code(-2, serial)
    time.sleep(0.5)

    # Return via ABC
    keyboard.tap_key_by_code(-200, serial)
    time.sleep(0.5)

    adb.clear_logcat(serial)

    # Enter symbols again
    keyboard.tap_key_by_code(-2, serial)
    time.sleep(0.5)

    adb.assert_logcat_contains(
        "DevKeyMode",
        r"toggleMode.*Normal.*Symbols",
        timeout=2.0,
        serial=serial
    )
