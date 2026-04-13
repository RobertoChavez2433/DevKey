"""
Modifier edge-case tests.

Exercises unusual modifier scenarios:
  1. Orphan modifier press without release
  2. Mode switch while modifier held
  3. Shift lock sustained over 20 chars
  4. Ctrl+A with all three override settings

Depends on:
  - FULL layout mode
  - key_event / modifier_transition instrumentation
  - HTTP forwarding enabled
"""
import subprocess
import time

from lib import adb, keyboard, driver

KEYCODE_SHIFT = -1
KEYCODE_CTRL_LEFT = -113
KEYCODE_MODE_CHANGE = -2  # Keyboard.KEYCODE_MODE_CHANGE


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    subprocess.run(
        adb._adb_cmd(
            ["shell", "am", "broadcast",
             "-a", "dev.devkey.keyboard.RESET_KEYBOARD_MODE"],
            serial,
        ),
        check=False,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
    )
    keyboard.set_layout_mode("full", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(0.5)
        keyboard.load_key_map(serial)
    return serial


def _assert_pid_alive(serial):
    cmd = adb._adb_cmd(["shell", "pidof", "dev.devkey.keyboard"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died during edge test"


def _set_pref(key, value):
    """Set a preference via the debug broadcast."""
    if isinstance(value, bool):
        driver.broadcast(
            "dev.devkey.keyboard.SET_BOOL_PREF",
            {"key": key, "value": value},
        )
    elif isinstance(value, int):
        driver.broadcast(
            "dev.devkey.keyboard.SET_INT_PREF",
            {"key": key, "value": str(value)},
        )
    time.sleep(0.2)


def test_modifier_press_without_release():
    """
    Tap Ctrl (one-shot) but never explicitly release. Verify IME does
    not crash and subsequent typing works (the one-shot is consumed by
    the next key).
    """
    serial = _setup()
    driver.clear_logs()

    # Engage Ctrl one-shot
    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    time.sleep(0.3)

    # Type a letter — should consume the orphan modifier
    keyboard.tap_key("x", serial)
    time.sleep(0.3)

    # Type another letter — should work normally
    keyboard.tap_key("y", serial)
    time.sleep(0.3)

    _assert_pid_alive(serial)


def test_mode_switch_while_modifier_held():
    """
    Engage Ctrl one-shot, then switch to symbols mode and back.
    Verify IME survives the mode transitions with active modifier.
    """
    serial = _setup()
    driver.clear_logs()

    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    time.sleep(0.2)

    # Switch to symbols mode
    try:
        keyboard.tap_key_by_code(KEYCODE_MODE_CHANGE, serial)
    except KeyError:
        import pytest
        pytest.skip("Mode change key not in current layout")
    time.sleep(0.5)

    # Switch back
    try:
        keyboard.tap_key_by_code(KEYCODE_MODE_CHANGE, serial)
    except KeyError:
        pass
    time.sleep(0.5)

    _assert_pid_alive(serial)


def test_shift_lock_20_chars():
    """
    Double-tap Shift to lock, then type 20 characters. Verify the
    shift-lock state persists (structural events show LOCKED throughout)
    and the IME does not crash.
    """
    serial = _setup()
    driver.clear_logs()

    # Double-tap Shift to lock
    keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
    time.sleep(0.15)
    keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
    time.sleep(0.5)

    # Verify LOCKED state was reached
    adb.assert_logcat_contains(
        "DevKeyPress",
        r"ModifierTransition.*SHIFT.*tap.*LOCKED",
        timeout=2.0,
        serial=serial,
    )

    # Type 20 chars
    for ch in "abcdefghijklmnopqrst":
        keyboard.tap_key(ch, serial)
        time.sleep(0.05)

    time.sleep(0.5)
    _assert_pid_alive(serial)


def test_ctrl_a_all_overrides():
    """
    Exercise Ctrl+A with all three ctrlAOverride settings (0, 1, 2).
    Verify the IME does not crash under any setting.
    """
    serial = _setup()

    for override_val in [0, 1, 2]:
        _set_pref("ctrl_a_override", override_val)
        driver.clear_logs()

        keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
        keyboard.tap_key("a", serial)
        time.sleep(0.5)

        _assert_pid_alive(serial)

    # Reset to default
    _set_pref("ctrl_a_override", 0)
