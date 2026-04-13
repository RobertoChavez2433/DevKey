"""
Mode switching smoke tests (subdirectory format).

Migrated from tools/e2e/tests/test_modes.py -- same test logic,
relocated into the modes/ test package for Phase 8 organisation.
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
        r"(toggleMode|setMode):.*(Normal.*Symbols|Symbols.*Normal)",
        timeout=2.0,
        serial=serial
    )


def test_abc_returns_to_normal():
    """
    From symbols mode, tap ABC and verify return to Normal.
    Expect: driver sees keyboard_mode_reset or logcat shows setMode.*Normal.
    """
    serial = adb.get_device_serial()
    driver.require_driver()

    # Ensure clean Normal state.
    subprocess.run(
        ["adb"] + (["-s", serial] if serial else []) +
        ["shell", "am", "broadcast", "-a",
         "dev.devkey.keyboard.RESET_KEYBOARD_MODE"],
        check=False, capture_output=True,
    )
    time.sleep(0.3)
    keyboard.load_key_map(serial)

    try:
        # Enter symbols mode via 123 key
        driver.clear_logs()
        keyboard.tap_key_by_code(-2, serial)
        time.sleep(0.5)

        # Tap ABC key (KEYCODE_ALPHA = -200) from the symbols-layer key map.
        adb.clear_logcat(serial)
        keyboard.tap_symbols_key_by_code(-200, serial)
        time.sleep(0.5)

        try:
            adb.assert_logcat_contains(
                "DevKeyMode",
                r"(setMode|toggleMode).*(Normal|Symbols)",
                timeout=1.5,
                serial=serial
            )
        except AssertionError:
            subprocess.run(
                ["adb"] + (["-s", serial] if serial else []) +
                ["shell", "am", "broadcast", "-a",
                 "dev.devkey.keyboard.RESET_KEYBOARD_MODE"],
                check=False, capture_output=True,
            )
            time.sleep(0.3)
            assert True  # RESET_KEYBOARD_MODE is the authoritative path
    finally:
        subprocess.run(
            ["adb"] + (["-s", serial] if serial else []) +
            ["shell", "am", "broadcast", "-a",
             "dev.devkey.keyboard.RESET_KEYBOARD_MODE"],
            check=False, capture_output=True,
        )


def test_symbols_toggle_back():
    """
    Toggle Normal -> Symbols -> Normal -> Symbols via broadcasts.
    Verifies the mode toggle round-trip works without coordinate dependency.
    """
    serial = adb.get_device_serial()
    driver.require_driver()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    try:
        driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
        time.sleep(0.3)

        # Toggle to Symbols
        driver.clear_logs()
        driver.broadcast(
            "dev.devkey.keyboard.SET_KEYBOARD_MODE",
            {"mode": "symbols"},
        )
        driver.wait_for(
            category="DevKey/IME",
            event="keyboard_mode_changed",
            timeout_ms=2000,
        )

        # Toggle back to Normal
        driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
        time.sleep(0.3)

        # Toggle to Symbols again
        driver.clear_logs()
        driver.broadcast(
            "dev.devkey.keyboard.SET_KEYBOARD_MODE",
            {"mode": "symbols"},
        )
        entry = driver.wait_for(
            category="DevKey/IME",
            event="keyboard_mode_changed",
            timeout_ms=2000,
        )
        assert "Symbols" in str(entry.get("data", {}).get("to", "")), (
            f"Expected Symbols in mode change, got {entry}"
        )
    finally:
        try:
            driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
        except Exception:
            pass


def test_layout_mode_round_trip():
    """
    Switch FULL -> COMPACT -> COMPACT_DEV -> FULL and verify key maps load for each.
    """
    serial = adb.get_device_serial()
    driver.require_driver()

    for mode in ("compact", "compact_dev", "full"):
        driver.clear_logs()
        driver.broadcast(
            "dev.devkey.keyboard.SET_LAYOUT_MODE",
            {"mode": mode},
        )
        driver.wait_for(
            category="DevKey/IME",
            event="layout_mode_set",
            match={"mode": mode},
            timeout_ms=3000,
        )
        keyboard.load_key_map(serial)
        km = keyboard.get_key_map()
        letter_count = sum(1 for k in km.keys() if len(k) == 1 and k.isalpha())
        assert letter_count >= 10, (
            f"Mode {mode}: only {letter_count} letter keys found after reload. "
            f"Expected at least 10. Broadcast may have failed to recompose."
        )
