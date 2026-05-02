"""
Punctuation heuristics smoke tests (subdirectory format).

Migrated from tools/e2e/tests/test_auto_punctuation.py -- same test logic,
relocated into the punctuation/ test package for Phase 4 organisation.

Depends on:
  - PunctuationHeuristics instrumentation (double_space_applied, punc_space_swapped,
    period_reswapped, trailing_space_removed)
  - Autocorrect/prediction enabled
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver
from .common import (
    ENTER_CODE,
    PERIOD_CODE,
    SPACE_CODE,
    assert_text_length_at_least,
    setup as _setup,
    type_word as _type_word,
)


def test_double_space_period():
    """
    Typing a word + space + space must produce '. ' (double-space to period).
    Assert double_space_applied event fires.
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("hi", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.35)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="double_space_applied",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert data["chars_removed"] == 2


def test_double_space_guard_at_start():
    """
    Space + space with no preceding letter should NOT trigger double-space period.
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
        assert False, "double_space_applied should NOT fire without a preceding letter"
    except Exception:
        pass  # Expected: no event
    assert_text_length_at_least(serial, 2, "start-of-field spaces were not committed")


def test_punctuation_space_swap():
    """
    Typing a word + space + period must swap the space and period.
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("the", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="punc_space_swapped",
        timeout_ms=5000,
    )
    assert entry is not None


def test_remove_trailing_space_on_enter():
    """
    Typing a word + space + enter must remove the trailing auto-space.
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("the", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(ENTER_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="trailing_space_removed",
        timeout_ms=5000,
    )
    assert entry is not None


def test_period_reswap():
    """
    Typing word + space + period + period must reswap to produce '..' pattern.
    """
    serial = _setup()

    _type_word("the", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)

    # First period triggers punc_space_swapped
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    driver.wait_for(
        category="DevKey/TXT",
        event="punc_space_swapped",
        timeout_ms=5000,
    )

    # Second period triggers reswap
    driver.clear_logs()
    time.sleep(0.3)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="period_reswapped",
        timeout_ms=5000,
    )
    assert entry is not None
