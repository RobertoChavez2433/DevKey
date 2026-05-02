"""
Modifier state exhaustion tests.

Enumerates modifier state transitions across all combinations of
Shift, Ctrl, Alt, and Meta. Tests major transition paths via key
events to verify the state machine does not crash or leak state.

Depends on:
  - FULL layout mode (all modifier keys present)
  - modifier_transition instrumentation
  - HTTP forwarding enabled
"""
import itertools
import subprocess
import time

from lib import adb, keyboard, driver

KEYCODE_SHIFT = -1
KEYCODE_CTRL_LEFT = -113
KEYCODE_ALT_LEFT = -57
KEYCODE_META_LEFT = -117
TEST_LETTER = "b"

MODIFIER_KEYS = {
    "shift": KEYCODE_SHIFT,
    "ctrl": KEYCODE_CTRL_LEFT,
    "alt": KEYCODE_ALT_LEFT,
    "meta": KEYCODE_META_LEFT,
}

MODIFIER_EVENT_TYPES = {
    "shift": "SHIFT",
    "ctrl": "CTRL",
    "alt": "ALT",
    "meta": "META",
}


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
    assert result.stdout.strip(), "IME process died during state exhaustion test"


def _reset_modifiers(serial):
    """
    Reset all modifier state by tapping each modifier key twice (to
    cycle through one-shot and back to off) and then sending a reset
    broadcast.
    """
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
    time.sleep(0.3)


def _wait_modifier_transition(name):
    driver.wait_for(
        "DevKey/MOD",
        "modifier_transition",
        match={"type": MODIFIER_EVENT_TYPES[name]},
        timeout_ms=3000,
    )


def _verify_modified_key_or_host_state(serial, active_names, context):
    match = {}
    for name in active_names:
        if name in {"ctrl", "alt", "meta"}:
            match[name] = True
            break
    if match:
        driver.wait_for(
            "DevKey/TXT",
            "key_event",
            match=match,
            timeout_ms=3000,
        )
        return

    state = adb.query_test_host_state(serial, timeout_ms=2000)
    assert state.get("focused") is not False, f"{context}: TestHostActivity lost focus"
    assert adb.is_keyboard_visible(serial), f"{context}: keyboard is not visible"


def test_single_modifier_engage_and_consume():
    """
    For each modifier (Shift, Ctrl, Alt, Meta): engage one-shot, then
    type a letter to consume it. Verify IME survives each cycle.
    """
    serial = _setup()

    for name, code in MODIFIER_KEYS.items():
        _reset_modifiers(serial)
        driver.clear_logs()

        try:
            keyboard.tap_key_by_code(code, serial)
        except KeyError:
            continue  # Meta key may not be present in all layouts
        _wait_modifier_transition(name)
        time.sleep(0.15)

        keyboard.tap_key(TEST_LETTER, serial)
        _verify_modified_key_or_host_state(serial, [name], f"{name} consume")

        _assert_pid_alive(serial)


def test_pairwise_modifier_combinations():
    """
    For each pair of modifiers: engage both one-shot, then type a letter.
    Covers all 6 pairwise combinations: Shift+Ctrl, Shift+Alt, Shift+Meta,
    Ctrl+Alt, Ctrl+Meta, Alt+Meta.
    """
    serial = _setup()
    pairs = list(itertools.combinations(MODIFIER_KEYS.items(), 2))

    for (name1, code1), (name2, code2) in pairs:
        _reset_modifiers(serial)
        driver.clear_logs()

        try:
            keyboard.tap_key_by_code(code1, serial)
            _wait_modifier_transition(name1)
            time.sleep(0.1)
            keyboard.tap_key_by_code(code2, serial)
            _wait_modifier_transition(name2)
            time.sleep(0.1)
        except KeyError:
            continue

        keyboard.tap_key(TEST_LETTER, serial)
        _verify_modified_key_or_host_state(serial, [name1, name2], f"{name1}+{name2}")

        _assert_pid_alive(serial)


def test_all_four_modifiers_at_once():
    """
    Engage all four modifiers (Shift, Ctrl, Alt, Meta) then type a
    letter. Verify IME survives the maximally-modified key event.
    """
    serial = _setup()
    _reset_modifiers(serial)
    driver.clear_logs()

    for name, code in MODIFIER_KEYS.items():
        try:
            keyboard.tap_key_by_code(code, serial)
        except KeyError:
            pass
        else:
            _wait_modifier_transition(name)
        time.sleep(0.1)

    keyboard.tap_key(TEST_LETTER, serial)
    _verify_modified_key_or_host_state(serial, list(MODIFIER_KEYS.keys()), "all modifiers")

    _assert_pid_alive(serial)


def test_modifier_engage_disengage_cycle():
    """
    For each modifier: engage (tap), disengage (tap again to toggle off),
    repeat 3 times. Verify the state machine handles rapid toggling.
    """
    serial = _setup()

    for name, code in MODIFIER_KEYS.items():
        _reset_modifiers(serial)
        driver.clear_logs()

        for _ in range(3):
            try:
                keyboard.tap_key_by_code(code, serial)
                _wait_modifier_transition(name)
                time.sleep(0.1)
                keyboard.tap_key_by_code(code, serial)
                _wait_modifier_transition(name)
                time.sleep(0.1)
            except KeyError:
                break

        _assert_pid_alive(serial)


def test_sequential_modifier_transitions():
    """
    Walk through a sequence of transitions: engage Ctrl, type letter
    (consume), engage Alt, type letter (consume), engage Shift, type
    letter (consume), engage Meta, type letter (consume). Then reverse.
    Verify no modifier state leaks between transitions.
    """
    serial = _setup()
    _reset_modifiers(serial)
    driver.clear_logs()

    sequence = list(MODIFIER_KEYS.items())
    # Forward pass
    for name, code in sequence:
        try:
            keyboard.tap_key_by_code(code, serial)
        except KeyError:
            continue
        _wait_modifier_transition(name)
        time.sleep(0.15)
        keyboard.tap_key(TEST_LETTER, serial)
        _verify_modified_key_or_host_state(serial, [name], f"forward {name}")

    # Reverse pass
    for name, code in reversed(sequence):
        try:
            keyboard.tap_key_by_code(code, serial)
        except KeyError:
            continue
        _wait_modifier_transition(name)
        time.sleep(0.15)
        keyboard.tap_key(TEST_LETTER, serial)
        _verify_modified_key_or_host_state(serial, [name], f"reverse {name}")

    _assert_pid_alive(serial)
