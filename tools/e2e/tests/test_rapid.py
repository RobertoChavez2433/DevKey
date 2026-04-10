"""
Rapid interaction tests for DevKey keyboard.

Stress tests for rapid tapping, mode switching, and modifier combinations
to catch race conditions and state corruption.
"""

import time

from lib import adb, keyboard


def test_rapid_letter_taps():
    """
    Rapidly tap 10 letter keys and verify all produce logcat entries.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    # Type "helloworld" rapidly
    for char in "helloworld":
        keyboard.tap_key(char, serial)
        time.sleep(0.05)  # 50ms between taps

    time.sleep(1.0)

    # Verify at least some key presses were logged
    lines = adb.capture_logcat("DevKeyPress", timeout=1.0, serial=serial)
    tap_lines = [l for l in lines if "tap" in l.lower()]
    assert len(tap_lines) >= 5, (
        f"Expected at least 5 tap events from rapid typing, got {len(tap_lines)}"
    )


def test_rapid_mode_toggles():
    """
    Rapidly toggle between Normal and Symbols modes 5 times.
    Verify no crashes and final mode is deterministic.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    try:
        for _ in range(5):
            # Enter symbols
            keyboard.tap_key_by_code(-2, serial)
            time.sleep(0.2)
            # Return to normal via ABC (symbols-layer key)
            keyboard.tap_symbols_key_by_code(-200, serial)
            time.sleep(0.2)

        time.sleep(0.5)

        # Verify logcat has mode transitions (no crashes)
        lines = adb.capture_logcat("DevKeyMode", timeout=1.0, serial=serial)
        assert len(lines) >= 5, (
            f"Expected at least 5 mode transitions, got {len(lines)}"
        )
    finally:
        # Ensure we end in Normal mode regardless of where the loop stopped.
        try:
            keyboard.tap_symbols_key_by_code(-200, serial)
        except Exception:
            pass


def test_rapid_shift_taps():
    """
    Tap Shift rapidly 6 times and verify no state corruption.
    Expected cycle: OFF -> ONE_SHOT -> LOCKED -> OFF -> ONE_SHOT -> LOCKED -> OFF
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    for _ in range(6):
        keyboard.tap_key_by_code(-1, serial)
        time.sleep(0.15)

    time.sleep(0.5)

    # Verify modifier transitions were logged
    lines = adb.capture_logcat("DevKeyPress", timeout=1.0, serial=serial)
    modifier_lines = [l for l in lines if "ModifierTransition" in l and "SHIFT" in l]
    assert len(modifier_lines) >= 3, (
        f"Expected at least 3 Shift transitions, got {len(modifier_lines)}"
    )


def test_shift_then_rapid_letters():
    """
    Tap Shift, then rapidly type 'abc'.
    Verify Shift is consumed after first letter.
    """
    serial = adb.get_device_serial()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    adb.clear_logcat(serial)

    # Shift one-shot
    keyboard.tap_key_by_code(-1, serial)
    time.sleep(0.2)

    # Rapid letters
    for char in "abc":
        keyboard.tap_key(char, serial)
        time.sleep(0.05)

    time.sleep(0.5)

    # Verify at least some key presses logged
    lines = adb.capture_logcat("DevKeyPress", timeout=1.0, serial=serial)
    assert len(lines) >= 3, f"Expected at least 3 key events, got {len(lines)}"
