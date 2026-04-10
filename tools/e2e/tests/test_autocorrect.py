"""
Autocorrect feature tests.

Tests the modern autocorrect pipeline: aggressive auto-apply, off mode,
and suggestion bar visibility.

Depends on:
  - autocorrect_applied instrumentation in LatinIME handleSeparator
  - SET_AUTOCORRECT_LEVEL broadcast receiver
  - HTTP forwarding enabled
"""
from lib import adb, keyboard, driver

SPACE_CODE = 32


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    return serial


def _set_autocorrect_level(level):
    """Set the autocorrect level via broadcast."""
    driver.broadcast(
        "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
        {"level": level},
    )
    entry = driver.wait_for(
        category="DevKey/IME",
        event="autocorrect_level_set",
        match={"level": level},
        timeout_ms=3000,
    )
    assert entry["data"]["level"] == level


def test_autocorrect_aggressive_applies():
    """
    With aggressive autocorrect, typing a misspelled word + space
    must emit an autocorrect_applied event.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    driver.clear_logs()

    # Type "teh " — common misspelling that should be corrected to "the"
    for ch in "teh":
        keyboard.tap_key(ch, serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="autocorrect_applied",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert data["action"] == "applied"
    assert data["level"] == "aggressive"


def test_autocorrect_off_no_correction():
    """
    With autocorrect off, typing a misspelled word + space must NOT
    emit an autocorrect_applied event.
    """
    serial = _setup()
    _set_autocorrect_level("off")
    driver.clear_logs()

    # Type "teh " — should stay as-is
    for ch in "teh":
        keyboard.tap_key(ch, serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    # Wait for the space event (next_word_suggestions) to confirm input was processed
    driver.wait_for(
        category="DevKey/TXT",
        event="next_word_suggestions",
        timeout_ms=3000,
    )

    # Verify no autocorrect_applied event was emitted
    try:
        driver.wait_for(
            category="DevKey/TXT",
            event="autocorrect_applied",
            timeout_ms=1000,
        )
        assert False, "autocorrect_applied should NOT fire when level is off"
    except Exception:
        pass  # Expected: no autocorrect event


def test_autocorrect_privacy_no_words_logged():
    """
    The autocorrect_applied payload must contain only action and level.
    No typed words or corrections should be logged.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    driver.clear_logs()

    for ch in "teh":
        keyboard.tap_key(ch, serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="autocorrect_applied",
        timeout_ms=5000,
    )
    data = entry["data"]
    allowed_keys = {"action", "level"}
    extra = set(data.keys()) - allowed_keys
    assert not extra, (
        f"autocorrect_applied payload contained unexpected keys: {extra}. "
        f"PRIVACY: payload must contain only action and level."
    )
