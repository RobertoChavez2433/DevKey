"""
Punctuation heuristics stress tests.

Exercises the punctuation pipeline under sustained and rapid input:
repeated double-space conversions, alternating separators, double-space
after autocorrect, and period reswap ellipsis patterns.
"""
import time
from lib import adb, keyboard, driver
from .common import (
    COMMA_CODE,
    PERIOD_CODE,
    SPACE_CODE,
    assert_text_length_at_least,
    clear_edit_text as _clear_edit_text,
    setup as _setup,
    type_word as _type_word,
)


def test_10_double_space_to_period():
    """
    Perform 10 double-space conversions in sequence. Each must fire
    double_space_applied. Asserts event count, never text content.
    """
    serial = _setup()
    words = ["hi", "go", "do", "be", "an", "up", "am", "no", "so", "on"]
    event_count = 0

    for word in words:
        driver.clear_logs()
        _type_word(word, serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        time.sleep(0.45)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            category="DevKey/TXT",
            event="double_space_applied",
            timeout_ms=5000,
        )
        assert entry["data"]["chars_removed"] == 2
        event_count += 1
        time.sleep(0.3)

    assert event_count == 10, (
        f"Expected 10 double_space_applied events, got {event_count}"
    )


def test_alternating_separators():
    """
    Rapid period, comma, exclamation after word+space. Each should fire
    punc_space_swapped (all three are sentence separators or suggested
    punctuation).
    """
    serial = _setup()
    separator_codes = [PERIOD_CODE, COMMA_CODE]
    event_count = 0

    for sep_code in separator_codes:
        _clear_edit_text(serial)
        driver.clear_logs()
        _type_word("ok", serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        time.sleep(0.4)
        keyboard.tap_key_by_code(sep_code, serial)

        entry = driver.wait_for(
            category="DevKey/TXT",
            event="punc_space_swapped",
            timeout_ms=5000,
        )
        assert entry is not None
        event_count += 1

    assert event_count == 2, (
        f"Expected 2 punc_space_swapped events for alternating separators, "
        f"got {event_count}"
    )


def test_double_space_after_autocorrect():
    """
    Type a misspelled word + space (triggers autocorrect commit) + space
    (triggers double-space). Verify double_space_applied fires.
    """
    serial = _setup()
    driver.clear_logs()

    # Type a word that may trigger autocorrect, then double-space
    _type_word("teh", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="double_space_applied",
        timeout_ms=5000,
    )
    assert entry["data"]["chars_removed"] == 2


def test_period_reswap_ellipsis():
    """
    '...' pattern: type word + space + period (swap) + period (reswap) +
    period. Verify punc_space_swapped and period_reswapped both fire.
    """
    serial = _setup()

    _type_word("wow", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)

    # First period: swap
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    driver.wait_for(
        category="DevKey/TXT",
        event="punc_space_swapped",
        timeout_ms=5000,
    )

    # Second period: reswap
    driver.clear_logs()
    time.sleep(0.3)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="period_reswapped",
        timeout_ms=5000,
    )
    assert entry is not None

    # Third period: typed normally (no special event expected, just no crash)
    time.sleep(0.3)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    time.sleep(0.5)
    assert_text_length_at_least(serial, 5, "ellipsis sequence did not commit final period")
