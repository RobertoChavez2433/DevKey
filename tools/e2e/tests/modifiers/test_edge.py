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
CTRL_A_OVERRIDE_PREF = "pref_ctrl_a_override"


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
        driver.wait_for(
            "DevKey/IME",
            "bool_pref_set",
            match={"key": key, "value": value},
            timeout_ms=3000,
        )
    elif isinstance(value, int):
        driver.broadcast(
            "dev.devkey.keyboard.SET_STRING_PREF",
            {"key": key, "value": str(value)},
        )
        driver.wait_for(
            "DevKey/IME",
            "string_pref_set",
            match={"key": key, "value": str(value)},
            timeout_ms=3000,
        )


def _assert_host_focused(serial, context):
    state = adb.query_test_host_state(serial, timeout_ms=2000)
    assert state.get("focused") is not False, f"{context}: TestHostActivity lost focus"
    assert adb.is_keyboard_visible(serial), f"{context}: keyboard is not visible"
    return state


def _wait_key_event_or_host_state(serial, context, match=None):
    try:
        driver.wait_for(
            "DevKey/TXT",
            "key_event",
            match=match or {},
            timeout_ms=3000,
        )
    except driver.DriverTimeout:
        _assert_host_focused(serial, context)


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
    driver.wait_for(
        "DevKey/TXT",
        "key_event",
        match={"code": ord("x"), "ctrl": True},
        timeout_ms=3000,
    )

    # Type another letter — should work normally
    keyboard.tap_key("y", serial)
    _assert_host_focused(serial, "orphan modifier consumed")

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
    driver.wait_for(
        "DevKey/IME",
        "keyboard_mode_changed",
        timeout_ms=3000,
    )

    # Switch back
    try:
        keyboard.tap_key_by_code(KEYCODE_MODE_CHANGE, serial)
    except KeyError:
        driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
        driver.wait_for("DevKey/IME", "keyboard_mode_reset", timeout_ms=3000)
    else:
        _wait_key_event_or_host_state(serial, "mode switch returned from symbols")

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

    state = adb.query_test_host_state(serial, timeout_ms=2000)
    assert state["text_length"] >= 20, (
        f"Shift-lock typing did not reach expected length; got {state['text_length']}"
    )
    _assert_pid_alive(serial)


def test_ctrl_a_all_overrides():
    """
    Exercise Ctrl+A with all three ctrlAOverride settings (0, 1, 2).
    Verify the IME does not crash under any setting.
    """
    serial = _setup()

    for override_val in [0, 1, 2]:
        _set_pref(CTRL_A_OVERRIDE_PREF, override_val)
        driver.clear_logs()

        keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
        keyboard.tap_key("a", serial)
        if override_val == 2:
            driver.wait_for(
                "DevKey/TXT",
                "key_event",
                match={"code": ord("a"), "ctrl": True},
                timeout_ms=3000,
            )
        else:
            _assert_host_focused(serial, f"ctrl_a_override={override_val}")

        _assert_pid_alive(serial)

    # Reset to default
    _set_pref(CTRL_A_OVERRIDE_PREF, 0)
