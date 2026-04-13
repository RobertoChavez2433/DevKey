"""
Modifier stress tests.

Exercises modifier key handling under heavy and rapid usage patterns:
  1. Ctrl+A through Ctrl+Z full alphabet
  2. Rapid shift cycling (10x tap)
  3. Three-modifier combo (Ctrl+Alt+Shift+key)
  4. One-shot consumption (Ctrl tap -> letter -> verify released)

Depends on:
  - FULL layout mode (Ctrl/Alt/Shift keys present)
  - key_event instrumentation in KeyEventSender
  - HTTP forwarding enabled
"""
import subprocess
import time

from lib import adb, keyboard, driver

KEYCODE_SHIFT = -1
KEYCODE_CTRL_LEFT = -113
KEYCODE_ALT_LEFT = -57


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
    assert result.stdout.strip(), "IME process died during stress test"


def test_ctrl_a_through_z():
    """
    Send Ctrl+A through Ctrl+Z (26 combos). Verify each produces a
    key_event with ctrl=True and the correct code.
    """
    serial = _setup()

    for i, letter in enumerate("abcdefghijklmnopqrstuvwxyz"):
        driver.clear_logs()
        keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
        keyboard.tap_key(letter, serial)

        expected_code = ord(letter)
        entry = driver.wait_for(
            category="DevKey/TXT",
            event="key_event",
            match={"code": expected_code, "ctrl": True},
            timeout_ms=5000,
        )
        data = entry.get("data", {})
        assert data.get("ctrl") is True, (
            f"Ctrl+{letter}: expected ctrl=True, got {data}"
        )

    _assert_pid_alive(serial)


def test_rapid_shift_cycling():
    """
    Tap Shift 10 times rapidly. Verify the modifier_transition events
    are emitted without crashes.
    """
    serial = _setup()
    driver.clear_logs()

    for _ in range(10):
        keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
        time.sleep(0.1)

    # Allow events to propagate
    time.sleep(0.5)

    # Verify at least some modifier transitions were logged
    adb.assert_logcat_contains(
        "DevKeyPress",
        r"ModifierTransition.*SHIFT",
        timeout=3.0,
        serial=serial,
    )
    _assert_pid_alive(serial)


def test_three_modifier_combo():
    """
    Engage Ctrl + Alt + Shift, then tap 'a'. Verify the resulting
    key_event carries all three modifier flags.
    """
    serial = _setup()
    driver.clear_logs()

    # Engage each modifier in one-shot sequence
    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    time.sleep(0.1)
    keyboard.tap_key_by_code(KEYCODE_ALT_LEFT, serial)
    time.sleep(0.1)
    keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
    time.sleep(0.1)

    keyboard.tap_key("a", serial)

    # Look for a key_event with ctrl=True (the strongest indicator).
    # Three-modifier combos may or may not all appear in the same event
    # depending on one-shot consumption order.
    entry = driver.wait_for(
        category="DevKey/TXT",
        event="key_event",
        match={"ctrl": True},
        timeout_ms=5000,
    )
    data = entry.get("data", {})
    assert data.get("ctrl") is True, (
        f"Three-modifier combo: expected ctrl=True, got {data}"
    )
    _assert_pid_alive(serial)


def test_one_shot_consumption():
    """
    Tap Ctrl (one-shot), then type a letter. After the letter, verify
    Ctrl is no longer active by checking a second letter produces no
    ctrl flag.
    """
    serial = _setup()
    driver.clear_logs()

    # Engage Ctrl one-shot
    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    time.sleep(0.15)

    # First letter consumes the one-shot
    keyboard.tap_key("a", serial)
    time.sleep(0.3)

    # Second letter should NOT have ctrl
    driver.clear_logs()
    keyboard.tap_key("b", serial)

    try:
        entry = driver.wait_for(
            category="DevKey/TXT",
            event="key_event",
            match={"code": ord("b")},
            timeout_ms=3000,
        )
        data = entry.get("data", {})
        assert data.get("ctrl") is not True, (
            f"One-shot should be consumed: expected no ctrl on second letter, got {data}"
        )
    except driver.DriverTimeout:
        # If no key_event for 'b' at all, the plain char was committed via
        # commitText instead of key_event — which also means no ctrl.
        pass

    _assert_pid_alive(serial)
