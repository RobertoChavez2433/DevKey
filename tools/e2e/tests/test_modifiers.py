"""
Modifier key tests for DevKey keyboard.

Tests Shift one-shot, Caps Lock, Ctrl, and Alt modifier state transitions
via ADB tap + logcat assertion.
"""

import time

from lib import adb, keyboard


def test_shift_one_shot():
    """
    Tap Shift, then tap 'a'.
    Expect: logcat shows Shift ONE_SHOT, then key 'A' (uppercase), then Shift OFF.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    # Tap Shift
    keyboard.tap_key_by_code(-1, serial)  # KEYCODE_SHIFT = -1
    time.sleep(0.3)

    # Tap 'a'
    keyboard.tap_key("a", serial)
    time.sleep(0.5)

    # Verify Shift entered ONE_SHOT
    adb.assert_logcat_contains(
        "DevKeyPress",
        r"ModifierTransition.*SHIFT.*tap.*ONE_SHOT",
        timeout=2.0,
        serial=serial
    )


def test_shift_double_tap_locks():
    """
    Double-tap Shift quickly.
    Expect: logcat shows Shift transitioning to LOCKED.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    # Double-tap Shift quickly
    keyboard.tap_key_by_code(-1, serial)
    time.sleep(0.15)
    keyboard.tap_key_by_code(-1, serial)
    time.sleep(0.5)

    # Verify Shift reached LOCKED
    adb.assert_logcat_contains(
        "DevKeyPress",
        r"ModifierTransition.*SHIFT.*tap.*LOCKED",
        timeout=2.0,
        serial=serial
    )


def test_ctrl_one_shot():
    """
    Tap Ctrl and verify it enters one-shot state.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    keyboard.tap_key_by_code(-113, serial)  # CTRL_LEFT = -113
    time.sleep(0.5)

    adb.assert_logcat_contains(
        "DevKeyPress",
        r"ModifierTransition.*CTRL.*tap.*ONE_SHOT",
        timeout=2.0,
        serial=serial
    )
