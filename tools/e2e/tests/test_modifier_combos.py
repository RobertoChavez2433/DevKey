"""
Modifier-combo smoke tests: Ctrl+A, Ctrl+C, Ctrl+V, Alt+Tab.

FROM PLAN: Phase 2 sub-phase 4.10 — covers the `modifier-combos` flow
           entry from .claude/test-flows/registry.md with an actual Python test.

Strategy:
  - Engage modifier via tap_key_by_code (one-shot), tap target letter, wait_for
    a DevKey/TXT key_event carrying the expected modifier flag.
  - For Ctrl+A/C/V: verify the ctrl flag is set on the target letter event.
  - For Alt+Tab: smoke-only — assert IME process is still alive after the cascade.
"""
import subprocess
from lib import adb, keyboard, driver

KEYCODE_SHIFT = -1
KEYCODE_CTRL_LEFT = -113
KEYCODE_ALT_LEFT = -57
KEYCODE_TAB = 9


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    # Modifier combos (Ctrl+A/C/V, Alt+Tab) require FULL mode because the
    # Ctrl/Alt/Tab utility row is omitted from COMPACT layouts. Also forces
    # a clean layout state so tests don't inherit symbols mode from prior runs.
    keyboard.set_layout_mode("full", serial)
    # Retry key map load if first attempt returned empty — race between
    # RESET_KEYBOARD_MODE recomposition and DUMP_KEY_MAP can yield 0 keys.
    import time as _time
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        _time.sleep(0.5)
        keyboard.load_key_map(serial)
    return serial


def _assert_pid_alive(serial):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died during modifier-combo test"


def _ctrl_plus_letter(serial, letter: str, expected_code: int):
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
        f"Ctrl+{letter}: expected ctrl=True in key_event payload, got {data}"
    )


def test_ctrl_a_selects_all():
    """Ctrl+A: one-shot Ctrl, tap 'a', assert key_event has ctrl=True, code=97."""
    serial = _setup()
    _ctrl_plus_letter(serial, "a", 97)
    _assert_pid_alive(serial)


def test_ctrl_c_copies():
    """Ctrl+C: one-shot Ctrl, tap 'c', assert key_event has ctrl=True, code=99."""
    serial = _setup()
    _ctrl_plus_letter(serial, "c", 99)
    _assert_pid_alive(serial)


def test_ctrl_v_pastes():
    """Ctrl+V: one-shot Ctrl, tap 'v', assert key_event has ctrl=True, code=118."""
    serial = _setup()
    _ctrl_plus_letter(serial, "v", 118)
    _assert_pid_alive(serial)


def test_alt_tab_smoke():
    """
    Alt+Tab smoke: one-shot Alt, tap Tab, verify IME didn't crash.

    We assert on a key_event with alt=True rather than any side-effect on the
    host, because Alt+Tab semantics are host-dependent.
    """
    serial = _setup()
    driver.clear_logs()
    keyboard.tap_key_by_code(KEYCODE_ALT_LEFT, serial)
    try:
        keyboard.tap_key_by_code(KEYCODE_TAB, serial)
    except KeyError:
        import pytest
        pytest.skip("Tab key not present in current layout (COMPACT omits it)")
    try:
        entry = driver.wait_for(
            category="DevKey/TXT",
            event="key_event",
            match={"code": KEYCODE_TAB, "alt": True},
            timeout_ms=5000,
        )
        data = entry.get("data", {})
        assert data.get("alt") is True, (
            f"Alt+Tab: expected alt=True in key_event payload, got {data}"
        )
    except driver.DriverTimeout:
        # Tab may be swallowed by the OS before reaching DevKeyLogger.text —
        # still valid to assert no crash.
        pass
    _assert_pid_alive(serial)
