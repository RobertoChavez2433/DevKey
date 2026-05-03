"""Autocorrect smoke tests with verified controlled-host output."""

from .common import (
    assert_controlled_text_equals,
    assert_no_autocorrect_event,
    setup_autocorrect,
    set_autocorrect_level,
    tap_word_with_space,
    wait_autocorrect_applied,
    wait_next_word_suggestions,
)
from lib import driver


def test_autocorrect_aggressive_applies():
    """Aggressive autocorrect must commit the corrected fixture word."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    tap_word_with_space("teh", serial)

    wait_autocorrect_applied()
    assert_controlled_text_equals(serial, "the ", "aggressive autocorrect")


def test_autocorrect_off_no_correction():
    """Off mode must leave the fixture misspelling uncorrected."""
    serial = setup_autocorrect()
    set_autocorrect_level("off")
    driver.clear_logs()

    tap_word_with_space("teh", serial)

    wait_next_word_suggestions(timeout_ms=3000)
    assert_no_autocorrect_event()
    assert_controlled_text_equals(serial, "teh ", "autocorrect off")


def test_autocorrect_privacy_no_words_logged():
    """The autocorrect event remains structural while the host proves output."""
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    tap_word_with_space("teh", serial)

    data = wait_autocorrect_applied()
    assert_controlled_text_equals(serial, "the ", "autocorrect privacy")
    allowed_keys = {"action", "level"}
    extra = set(data.keys()) - allowed_keys
    assert not extra, (
        f"autocorrect_applied payload contained unexpected keys: {extra}. "
        f"PRIVACY: payload must contain only action and level."
    )
