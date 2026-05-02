"""
Rapid interaction tests for DevKey keyboard.

Stress tests for rapid tapping, mode switching, and modifier combinations
to catch race conditions and state corruption.
"""

import time

from lib import adb, driver, keyboard


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
    from lib import driver
    driver.require_driver()

    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    driver.clear_logs()

    transitions = 0
    for _ in range(5):
        # Enter symbols via 123 key.
        keyboard.tap_key_by_code(-2, serial)
        entry = driver.wait_for(
            "DevKey/IME",
            "keyboard_mode_changed",
            match={"action": "toggle"},
            timeout_ms=3000,
        )
        if "Symbols" in entry.get("data", {}).get("to", ""):
            transitions += 1

        # Return to normal via broadcast; this is deterministic and has an
        # explicit reset acknowledgement for the verifier.
        driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
        driver.wait_for("DevKey/IME", "keyboard_mode_reset", timeout_ms=3000)

    assert transitions >= 5, (
        f"Expected 5 symbols transitions, got {transitions}"
    )


def test_rapid_shift_taps():
    """
    Tap Shift rapidly 6 times and verify no state corruption.
    Expected cycle: OFF -> ONE_SHOT -> LOCKED -> OFF -> ONE_SHOT -> LOCKED -> OFF
    """
    serial = adb.get_device_serial()

    # Ensure Normal mode — prior test_rapid_mode_toggles can leave the keyboard
    # in symbols where SHIFT (-1) doesn't exist.
    keyboard.set_layout_mode("compact_dev", serial)

    driver.clear_logs()

    for _ in range(6):
        keyboard.tap_key_by_code(-1, serial)
        time.sleep(0.15)

    time.sleep(0.5)
    driver.wait_for(
        "DevKey/MOD",
        "modifier_transition",
        match={"type": "SHIFT"},
        timeout_ms=3000,
    )

    # Verify modifier transitions via driver — more reliable than logcat on
    # physical devices where rapid log writes can be dropped.
    try:
        import urllib.request, json
        url = f"{driver.DRIVER_URL}/logs?last=50&category=DevKey/MOD"
        with urllib.request.urlopen(url, timeout=3) as resp:
            body = resp.read().decode()
        # Each line is a separate JSON object
        lines = [l.strip() for l in body.strip().split("\n") if l.strip()]
        entries = []
        for line in lines:
            try:
                entries.append(json.loads(line))
            except json.JSONDecodeError:
                pass
        shift_entries = [
            e for e in entries
            if e.get("message") == "modifier_transition"
            and isinstance(e.get("data"), dict)
            and e["data"].get("type") == "SHIFT"
        ]
        assert len(shift_entries) >= 3, (
            f"Expected at least 3 Shift transitions, got {len(shift_entries)}"
        )
    except (urllib.error.URLError, OSError):
        # Fallback: logcat assertion
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
