"""Autocorrect stress tests that prove corrected output reaches the host."""

from .common import (
    assert_controlled_text_equals,
    setup_autocorrect,
    set_autocorrect_level,
    tap_sequence_with_space,
    tap_space,
    tap_word_with_space,
    wait_autocorrect_applied,
    wait_next_word_suggestions,
)
from lib import driver, keyboard

BACKSPACE_LABEL = "Backspace"


def test_aggressive_10_words():
    """Ten repeated misspellings must all land as corrected host text."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    expected = ""

    for _ in range(10):
        driver.clear_logs()
        tap_word_with_space("teh", serial, delay=0.08)

        wait_autocorrect_applied(timeout_ms=5000)
        expected += "the "
        assert_controlled_text_equals(serial, expected, "aggressive repeated autocorrect")


def test_type_and_backspace_revert():
    """Backspace after a corrected word must revert and keep input responsive."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    tap_word_with_space("teh", serial, delay=0.08)
    wait_autocorrect_applied(timeout_ms=8000)
    assert_controlled_text_equals(serial, "the ", "autocorrect before backspace")

    keyboard.tap_key(BACKSPACE_LABEL, serial)
    assert_controlled_text_equals(serial, "teh", "backspace reverts autocorrect")

    tap_space(serial)
    wait_next_word_suggestions(timeout_ms=5000)
    assert_controlled_text_equals(serial, "teh ", "space after autocorrect revert")

    driver.clear_logs()
    tap_word_with_space("teh", serial, delay=0.08)
    wait_autocorrect_applied(timeout_ms=8000)
    assert_controlled_text_equals(serial, "teh the ", "autocorrect after backspace revert")


def test_mixed_paragraph():
    """A mixed controlled paragraph must preserve every corrected token."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    words = ["teh", "of", "teh", "and", "teh"]
    expected = ""

    for word in words:
        driver.clear_logs()
        tap_sequence_with_space(word, serial, delay=0.05)
        if word == "teh":
            wait_autocorrect_applied(timeout_ms=5000)
            expected += "the "
        else:
            wait_next_word_suggestions(timeout_ms=5000)
            expected += f"{word} "
        assert_controlled_text_equals(serial, expected, "mixed autocorrect paragraph")


def test_autocorrect_then_suggestion():
    """Next-word suggestions must fire after an actual autocorrect commit."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    tap_word_with_space("teh", serial, delay=0.08)

    wait_autocorrect_applied(timeout_ms=5000)
    data = wait_next_word_suggestions(timeout_ms=5000)
    assert_controlled_text_equals(serial, "the ", "autocorrect then suggestion")
    valid_sources = {
        "bigram_hit",
        "bigram_miss",
        "no_prev_word",
        "space_only",
        "picked_default",
    }
    assert data["source"] in valid_sources, (
        f"Unexpected source after autocorrect: {data['source']}"
    )
