"""Autocorrect edge tests with explicit no-correction/output evidence."""

from .common import (
    assert_controlled_text_equals,
    assert_no_autocorrect_event,
    setup_autocorrect,
    set_autocorrect_level,
    tap_sequence_with_space,
    tap_word_with_space,
    wait_next_word_suggestions,
)
from lib import driver, keyboard


def test_single_char_words():
    """Single-letter words should not produce autocorrect replacements."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    expected = ""

    for ch in ["i", "a"]:
        driver.clear_logs()
        keyboard.tap_key(ch, serial)
        keyboard.tap_key_by_code(32, serial)

        wait_next_word_suggestions(timeout_ms=3000)
        assert_no_autocorrect_event()
        expected += f"{ch} "
        assert_controlled_text_equals(serial, expected, "single-char autocorrect edge")


def test_numbers_mixed():
    """Mixed alpha/numeric tokens should commit unchanged and not autocorrect."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    tap_sequence_with_space("abc1", serial, delay=0.08)

    wait_next_word_suggestions(timeout_ms=3000)
    assert_no_autocorrect_event()
    assert_controlled_text_equals(serial, "abc1 ", "mixed alpha numeric autocorrect edge")


def test_autocorrect_off():
    """Off mode should preserve a known misspelling and emit no correction."""
    serial = setup_autocorrect()
    set_autocorrect_level("off")
    driver.clear_logs()

    tap_word_with_space("teh", serial, delay=0.10)

    wait_next_word_suggestions(timeout_ms=3000)
    assert_no_autocorrect_event()
    assert_controlled_text_equals(serial, "teh ", "edge autocorrect off")


def test_word_at_boundary():
    """Long word boundaries should commit unchanged without autocorrect events."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    text = "a" * 40
    tap_sequence_with_space(text, serial, delay=0.03)

    wait_next_word_suggestions(timeout_ms=5000)
    assert_no_autocorrect_event()
    assert_controlled_text_equals(serial, f"{text} ", "long-word autocorrect edge")
