"""
Smoke tests for DevKey keyboard.

Basic sanity checks that the keyboard is functional:
- Keyboard is visible
- Key map loads successfully
- Single key tap produces output
"""

from lib import adb, keyboard


def test_keyboard_visible():
    """Verify the DevKey keyboard is currently visible."""
    serial = adb.get_device_serial()
    assert adb.is_keyboard_visible(serial), "Keyboard should be visible"


def test_key_map_loads():
    """Verify the key map loads with at least 26 letter keys."""
    serial = adb.get_device_serial()
    key_map = keyboard.load_key_map(serial)
    # Should have at least the 26 lowercase letters
    letter_keys = [k for k in key_map.keys() if len(k) == 1 and k.isalpha()]
    assert len(letter_keys) >= 10, (
        f"Expected at least 10 letter keys in map, got {len(letter_keys)}: {letter_keys}"
    )


def test_tap_letter_produces_logcat():
    """Tap the 'a' key and verify logcat shows the key press."""
    serial = adb.get_device_serial()

    # Ensure key map is loaded
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)
    keyboard.tap_key("a", serial)

    adb.assert_logcat_contains(
        "DevKeyPress",
        r"tap.*a.*97",
        timeout=2.0,
        serial=serial
    )


def test_calibration():
    """Run Y-offset calibration and verify it completes without error."""
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    offset = keyboard.calibrate_y_offset(serial)
    # Offset should be reasonable (within 50 pixels)
    assert abs(offset) < 50, f"Y-offset {offset} seems unreasonable"
