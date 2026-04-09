"""
Mode switching tests for DevKey keyboard.

Tests the 123/Symbols mode switch, ABC return, and other mode transitions.
"""

import time

from lib import adb, driver, keyboard


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
        r"setMode:.*(Normal.*Symbols|Symbols.*Normal)",
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
        r"setMode:.*(Normal.*Symbols|Symbols.*Normal)",
        timeout=2.0,
        serial=serial
    )


def test_layout_mode_round_trip():
    """
    Switch FULL -> COMPACT -> COMPACT_DEV -> FULL and verify key maps load for each.

    FROM SPEC: §6 Phase 2 item 2.4 — FULL + COMPACT + COMPACT_DEV 100% green.

    NOTE: This test DOES NOT call `keyboard.set_layout_mode(...)` because that helper
          has its own wave-gate and loads the key map internally. Instead we drive
          the broadcast + wait_for + load_key_map sequence explicitly so we can assert
          on the `layout_mode_set` event BEFORE any recomposition blocks.
    """
    serial = adb.get_device_serial()
    driver.require_driver()

    for mode in ("compact", "compact_dev", "full"):
        driver.clear_logs()
        # Broadcast directly via the driver — no sleeps.
        driver.broadcast(
            "dev.devkey.keyboard.SET_LAYOUT_MODE",
            {"mode": mode},
        )
        # Wait for the IME-side receiver to log the preference write.
        driver.wait_for(
            category="DevKey/IME",
            event="layout_mode_set",
            match={"mode": mode},
            timeout_ms=3000,
        )
        # Now load the key map for the new mode.
        keyboard.load_key_map(serial)
        km = keyboard.get_key_map()
        letter_count = sum(1 for k in km.keys() if len(k) == 1 and k.isalpha())
        assert letter_count >= 10, (
            f"Mode {mode}: only {letter_count} letter keys found after reload. "
            f"Expected at least 10. Broadcast may have failed to recompose."
        )
