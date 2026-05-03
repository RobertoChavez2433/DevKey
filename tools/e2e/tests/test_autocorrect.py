"""Legacy-flat autocorrect smoke tests with verified host output."""

from tests.autocorrect.common import (
    assert_controlled_text_equals,
    assert_no_autocorrect_event,
    setup_autocorrect,
    set_autocorrect_level,
    tap_word_with_space,
    wait_autocorrect_applied,
    wait_next_word_suggestions,
)
from lib import driver
from lib.privacy import allowed_payload_keys


def test_autocorrect_aggressive_applies():
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    tap_word_with_space("teh", serial)

    wait_autocorrect_applied()
    assert_controlled_text_equals(serial, "the ", "legacy aggressive autocorrect")


def test_autocorrect_off_no_correction():
    serial = setup_autocorrect()
    set_autocorrect_level("off")
    driver.clear_logs()

    tap_word_with_space("teh", serial)

    wait_next_word_suggestions(timeout_ms=3000)
    assert_no_autocorrect_event()
    assert_controlled_text_equals(serial, "teh ", "legacy autocorrect off")


def test_autocorrect_privacy_no_words_logged():
    serial = setup_autocorrect()
    set_autocorrect_level("aggressive")
    driver.clear_logs()

    tap_word_with_space("teh", serial)

    data = wait_autocorrect_applied()
    assert_controlled_text_equals(serial, "the ", "legacy autocorrect privacy")
    allowed_keys = allowed_payload_keys("action", "level")
    extra = set(data.keys()) - allowed_keys
    assert not extra, (
        f"autocorrect_applied payload contained unexpected keys: {extra}. "
        f"PRIVACY: payload must contain only action, level, and trace metadata."
    )
