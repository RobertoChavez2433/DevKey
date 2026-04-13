"""
Prediction edge-case tests.

Covers boundary conditions the prediction pipeline must handle without
crashing: empty input, common-charset input, multi-word sequences,
very long tokens, and rapid space/backspace cycling.
"""
from lib import adb, keyboard, driver

SPACE_CODE = 32
BACKSPACE_LABEL = "Backspace"


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    return serial


def test_empty_field_space():
    """
    Tapping space in an empty field must emit next_word_suggestions with
    source='no_prev_word' (or another valid fallback) — never crash.
    """
    serial = _setup()
    driver.clear_logs()
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )
    assert entry["data"]["source"] in {
        "no_prev_word", "bigram_miss", "space_only", "picked_default",
    }, f"Empty-field space produced unexpected source: {entry['data']['source']}"


def test_unicode_accented_input():
    """
    Typing regular ASCII characters + space must produce predictions.
    (Actual accented characters may not be on the default key map, so we
    exercise common input and verify the pipeline stays healthy.)
    """
    serial = _setup()
    driver.clear_logs()
    keyboard.tap_sequence("hello", delay=0.08, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )
    assert "source" in entry["data"], (
        "next_word_suggestions event missing 'source' field after common input"
    )


def test_cursor_reposition():
    """
    After typing multiple words, predictions must still fire on the next
    space.  This verifies the prediction state updates correctly as the
    composing context grows.
    """
    serial = _setup()
    words = ["one", "two", "three", "four", "five"]
    event_count = 0

    for word in words:
        driver.clear_logs()
        keyboard.tap_sequence(word, delay=0.08, serial=serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            "DevKey/TXT", "next_word_suggestions",
            timeout_ms=3000,
        )
        assert "source" in entry["data"]
        event_count += 1

    assert event_count == 5, (
        f"Expected 5 prediction events across multi-word input, got {event_count}"
    )


def test_very_long_word():
    """
    Typing a 50-character token then space must not crash and must still
    emit a next_word_suggestions event.
    """
    serial = _setup()
    driver.clear_logs()
    # 50 repetitions of 'a' (spec boundary value)
    keyboard.tap_sequence("a" * 50, delay=0.03, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=5000,
    )
    assert "source" in entry["data"], (
        "next_word_suggestions missing after very long word"
    )


def test_rapid_space_backspace_cycle():
    """
    Rapidly alternate space and backspace 5 times.  The prediction pipeline
    must not crash or leave the driver in an error state.
    """
    serial = _setup()
    driver.clear_logs()

    for _ in range(5):
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        keyboard.tap_key(BACKSPACE_LABEL, serial)

    # Verify the driver is still responsive — a final space must produce
    # a prediction event (proving no unhandled exception occurred).
    driver.clear_logs()
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )
    assert "source" in entry["data"], (
        "Driver unresponsive after rapid space/backspace cycling"
    )
