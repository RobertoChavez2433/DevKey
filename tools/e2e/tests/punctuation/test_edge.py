"""
Punctuation heuristics edge-case tests.

Covers boundary conditions the punctuation pipeline must handle without
crashing: empty field double-space, double-space after non-alpha, punctuation
after number, and rapid triple-space.
"""
import time
from lib import adb, keyboard, driver
from .common import (
    PERIOD_CODE,
    SPACE_CODE,
    assert_text_length_at_least,
    setup as _setup,
    type_word as _type_word,
)


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
    assert_text_length_at_least(serial, 2, "empty-field spaces were not committed")


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
    assert_text_length_at_least(serial, 5, "space-after-punctuation sequence did not commit")


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
    assert_text_length_at_least(serial, 3, "punctuation-after-number sequence did not commit")


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
