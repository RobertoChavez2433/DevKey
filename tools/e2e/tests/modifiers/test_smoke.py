"""
Modifier smoke tests — migrated from test_modifiers.py, test_modifier_combos.py,
and test_modifier_extended.py into the modifiers/ package.

Covers:
  - Shift one-shot and double-tap lock
  - Ctrl one-shot
  - Ctrl+A/C/V combos
  - Alt+Tab smoke
  - Ctrl+Z undo
"""
import subprocess
import time

from lib import adb, keyboard, driver

KEYCODE_SHIFT = -1
KEYCODE_CTRL_LEFT = -113
KEYCODE_ALT_LEFT = -57
KEYCODE_TAB = 9


def _setup_full(serial):
    """Ensure FULL layout with clean modifier state."""
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


def _assert_pid_alive(serial):
    cmd = adb._adb_cmd(["shell", "pidof", "dev.devkey.keyboard"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died during modifier test"


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    _setup_full(serial)
    return serial


# --- Shift tests (from test_modifiers.py) ---

def test_shift_one_shot():
    """Tap Shift, then tap 'a'. Expect ONE_SHOT transition in logs."""
    serial = _setup()
    adb.clear_logcat(serial)

    keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
    time.sleep(0.3)
    keyboard.tap_key("a", serial)
    time.sleep(0.5)

    adb.assert_logcat_contains(
        "DevKeyPress",
        r"ModifierTransition.*SHIFT.*tap.*ONE_SHOT",
        timeout=2.0,
        serial=serial,
    )


def test_shift_updates_visible_letter_labels():
    """Shift must visibly change letter labels before and after one-shot use."""
    serial = _setup()
    driver.clear_logs()

    keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
    shifted = driver.wait_for(
        "DevKey/UI",
        "key_display_label",
        match={"code": 97, "label": "A", "shift_state": "ONE_SHOT"},
        timeout_ms=5000,
    )
    assert shifted.get("data", {}).get("label") == "A", (
        f"Expected visible A after Shift, got {shifted.get('data', {})}"
    )

    keyboard.tap_key("a", serial)
    unshifted = driver.wait_for(
        "DevKey/UI",
        "key_display_label",
        match={"code": 97, "label": "a", "shift_state": "OFF"},
        timeout_ms=5000,
    )
    assert unshifted.get("data", {}).get("label") == "a", (
        f"Expected visible a after one-shot consumption, got {unshifted.get('data', {})}"
    )


def test_shift_double_tap_locks():
    """Double-tap Shift quickly. Expect LOCKED transition."""
    serial = _setup()
    adb.clear_logcat(serial)

    keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
    time.sleep(0.15)
    keyboard.tap_key_by_code(KEYCODE_SHIFT, serial)
    time.sleep(0.5)

    adb.assert_logcat_contains(
        "DevKeyPress",
        r"ModifierTransition.*SHIFT.*tap.*LOCKED",
        timeout=2.0,
        serial=serial,
    )


def test_ctrl_one_shot():
    """Tap Ctrl and verify one-shot state via driver."""
    serial = _setup()
    driver.clear_logs()
    adb.clear_logcat(serial)

    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    time.sleep(0.5)

    entry = driver.wait_for(
        "DevKey/MOD",
        "modifier_transition",
        match={"type": "CTRL", "action": "tap", "to": "ONE_SHOT"},
        timeout_ms=3000,
    )
    data = entry.get("data", {})
    assert data.get("type") == "CTRL", f"Expected CTRL, got {data}"


# --- Ctrl combo tests (from test_modifier_combos.py) ---

def _ctrl_plus_letter(serial, letter, expected_code):
    driver.clear_logs()
    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    keyboard.tap_key(letter, serial)
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


def test_ctrl_a():
    """Ctrl+A: one-shot Ctrl, tap 'a', verify ctrl flag."""
    serial = _setup()
    _ctrl_plus_letter(serial, "a", 97)
    _assert_pid_alive(serial)


def test_ctrl_c():
    """Ctrl+C: verify ctrl flag on key_event."""
    serial = _setup()
    _ctrl_plus_letter(serial, "c", 99)
    _assert_pid_alive(serial)


def test_ctrl_v():
    """Ctrl+V: verify ctrl flag on key_event."""
    serial = _setup()
    _ctrl_plus_letter(serial, "v", 118)
    _assert_pid_alive(serial)


def test_alt_tab_smoke():
    """Alt+Tab smoke: verify IME survives the key combo."""
    serial = _setup()
    driver.clear_logs()

    keyboard.tap_key_by_code(KEYCODE_ALT_LEFT, serial)
    try:
        keyboard.tap_key_by_code(KEYCODE_TAB, serial)
    except KeyError:
        import pytest
        pytest.skip("Tab key not present in current layout")

    try:
        entry = driver.wait_for(
            category="DevKey/TXT",
            event="key_event",
            match={"code": KEYCODE_TAB, "alt": True},
            timeout_ms=5000,
        )
        data = entry.get("data", {})
        assert data.get("alt") is True, (
            f"Alt+Tab: expected alt=True, got {data}"
        )
    except driver.DriverTimeout:
        pass  # Tab may be swallowed by OS — crash check is sufficient

    _assert_pid_alive(serial)


# --- Extended tests (from test_modifier_extended.py) ---

def test_ctrl_z_undo():
    """Ctrl+Z: one-shot Ctrl, tap 'z', verify ctrl flag."""
    serial = _setup()
    driver.clear_logs()

    keyboard.tap_key_by_code(KEYCODE_CTRL_LEFT, serial)
    keyboard.tap_key("z", serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="key_event",
        match={"code": 122, "ctrl": True},
        timeout_ms=5000,
    )
    data = entry.get("data", {})
    assert data.get("ctrl") is True, (
        f"Ctrl+Z: expected ctrl=True, got {data}"
    )
