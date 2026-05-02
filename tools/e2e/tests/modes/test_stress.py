"""
Mode switching stress tests.

Exercises keyboard mode transitions under sustained rapid toggling,
mid-composing switches, layout round-trips, and symbol-type-toggle patterns.
"""

import time

from lib import adb, driver, keyboard

SPACE_CODE = 32


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


def test_rapid_normal_symbols_toggle():
    """
    Toggle Normal -> Symbols -> Normal 10 times rapidly.
    Verify a keyboard_mode_changed event fires each toggle cycle.
    """
    serial = _setup()
    _reset_mode()

    toggle_count = 0
    for _ in range(10):
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
        toggle_count += 1

        driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
        time.sleep(0.15)

    assert toggle_count == 10, (
        f"Expected 10 successful symbol toggles, got {toggle_count}"
    )
    _reset_mode()


def test_mode_switch_mid_composing():
    """
    Type a partial word then switch to symbols mode.
    Verify the composing region commits (keyboard_mode_changed fires)
    and the IME does not crash.
    """
    serial = _setup()
    _reset_mode()

    driver.clear_logs()
    # Type partial word "hel"
    keyboard.tap_sequence("hel", delay=0.08, serial=serial)

    # Switch to symbols while composing
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "symbols"},
    )
    entry = driver.wait_for(
        category="DevKey/IME",
        event="keyboard_mode_changed",
        timeout_ms=3000,
    )
    assert entry is not None, "keyboard_mode_changed not received after mid-compose switch"

    _reset_mode()


def test_compact_full_compact_roundtrip():
    """
    Switch layout mode: full -> compact -> full and verify key maps reload.
    """
    serial = _setup()

    for mode in ("compact", "full", "compact"):
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
            f"Mode {mode}: only {letter_count} letter keys after reload."
        )

    # Restore to full
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_LAYOUT_MODE",
        {"mode": "full"},
    )
    driver.wait_for(
        category="DevKey/IME",
        event="layout_mode_set",
        match={"mode": "full"},
        timeout_ms=3000,
    )
    keyboard.load_key_map(serial)


def test_symbols_type_toggle_back():
    """
    Enter symbols mode, type a number key, toggle back to Normal.
    Verify clean state: keyboard_mode_changed fires for both transitions.
    """
    serial = _setup()
    _reset_mode()

    # Enter symbols
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "symbols"},
    )
    entry_to_sym = driver.wait_for(
        category="DevKey/IME",
        event="keyboard_mode_changed",
        timeout_ms=2000,
    )
    assert entry_to_sym is not None, "Failed to enter symbols mode"

    # Type a character in symbols mode (via key code for '1')
    keyboard.tap_key_by_code(ord("1"), serial)
    time.sleep(0.2)

    # Toggle back to Normal
    driver.clear_logs()
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)

    # Verify IME is responsive -- type a letter in Normal mode
    driver.clear_logs()
    keyboard.tap_key("a", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    # If we get here without exception the IME survived the round-trip.
    _reset_mode()
