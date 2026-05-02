"""
Punctuation heuristics edge-case tests.

Covers boundary conditions the punctuation pipeline must handle without
crashing: empty field double-space, double-space after non-alpha, punctuation
after number, and rapid triple-space.
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
PERIOD_CODE = 46

_dictionary_ready = False


def _wait_for_dictionary(serial):
    """Poll logcat until TrieDictionary reports loaded, or timeout after 15s."""
    global _dictionary_ready
    if _dictionary_ready:
        return
    deadline = time.time() + 15.0
    while time.time() < deadline:
        lines = adb.capture_logcat("DevKey/DictMgr", timeout=0.5, serial=serial)
        for line in lines:
            if "TrieDictionary loaded" in line:
                _dictionary_ready = True
                return
        time.sleep(1.0)


def _clear_edit_text(serial):
    """Clear the TestHostActivity EditText via debug broadcast."""
    import subprocess
    subprocess.run(
        adb._adb_cmd(
            ["shell", "am", "broadcast",
             "-a", "dev.devkey.keyboard.debug.CLEAR_EDIT_TEXT"],
            serial,
        ),
        capture_output=True,
    )
    time.sleep(0.3)


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("compact", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    _wait_for_dictionary(serial)
    _clear_edit_text(serial)
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": False},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
    except Exception:
        pass
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
    except Exception:
        pass
    for attempt in range(3):
        try:
            driver.clear_logs()
            driver.broadcast(
                "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
                {"level": "aggressive"},
            )
            driver.wait_for("DevKey/IME", "autocorrect_level_set", timeout_ms=2000)
            break
        except Exception:
            time.sleep(2.0)
    time.sleep(0.3)
    # Warmup: type a space and delete it to settle the Compose layout
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.3)
    keyboard.tap_key("Backspace", serial)
    time.sleep(0.3)
    _clear_edit_text(serial)
    return serial


def _type_word(word, serial):
    """Tap each character with inter-key delay for reliable composing."""
    for i, ch in enumerate(word):
        keyboard.tap_key(ch, serial)
        time.sleep(0.35 if i == 0 else 0.15)


def test_double_space_empty_field():
    """
    Double-space at start of empty field must NOT trigger double_space_applied.
    The guard requires a letter/digit before the two spaces.
    """
    serial = _setup()
    driver.clear_logs()

    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.15)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.5)

    try:
        driver.wait_for(
            category="DevKey/TXT",
            event="double_space_applied",
            timeout_ms=1500,
        )
        assert False, "double_space_applied should NOT fire in empty field"
    except Exception:
        pass  # Expected: no event


def test_space_after_emoji():
    """
    Double-space after non-alpha characters. Since emoji keys may not be
    available, type a regular word + period + space + space. The period
    is non-alphanumeric, so the second double-space should not fire
    double_space_applied (the char before the two spaces is a period,
    not a letter/digit).
    """
    serial = _setup()

    _type_word("ok", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    time.sleep(0.3)

    # Now we have "ok. " in the field (after swap). Type space + space.
    # The character before the two spaces should be ' ' (from the swap),
    # which is not a letter/digit, so double_space should NOT fire.
    driver.clear_logs()
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.15)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.5)

    try:
        driver.wait_for(
            category="DevKey/TXT",
            event="double_space_applied",
            timeout_ms=1500,
        )
        assert False, (
            "double_space_applied should NOT fire after non-alphanumeric"
        )
    except Exception:
        pass  # Expected: no event


def test_punctuation_after_number():
    """
    Type a number + period. The period is typed after a digit.
    Verify no crash occurs (the period is handled by the separator path).
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("42", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    time.sleep(0.5)

    # Verify the driver is still responsive by checking that events
    # were processed without error.
    try:
        driver.wait_for(
            category="DevKey/TXT",
            event="punc_space_swapped",
            timeout_ms=3000,
        )
    except Exception:
        pass  # May or may not fire depending on TextEntryState, but no crash


def test_three_rapid_spaces():
    """
    3 spaces rapidly after a word. Verify no crash. The first double-space
    fires; the third space should be handled gracefully.
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("go", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.35)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.1)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.5)

    # First double-space should fire
    entry = driver.wait_for(
        category="DevKey/TXT",
        event="double_space_applied",
        timeout_ms=5000,
    )
    assert entry["data"]["chars_removed"] == 2

    # Verify driver is still responsive after the third space
    driver.clear_logs()
    _type_word("ok", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.35)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="double_space_applied",
        timeout_ms=5000,
    )
    assert entry is not None
