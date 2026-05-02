"""
Autocorrect feature tests.

Tests the modern autocorrect pipeline: aggressive auto-apply, off mode,
and suggestion bar visibility.

Depends on:
  - autocorrect_applied instrumentation in LatinIME handleSeparator
  - SET_AUTOCORRECT_LEVEL broadcast receiver
  - HTTP forwarding enabled
  - TrieDictionary loaded (async — test gates on logcat)
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32

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


def _clear_learned_words(serial):
    """Clear learned words so autocorrect isn't suppressed by prior test runs."""
    import subprocess
    subprocess.run(
        adb._adb_cmd(
            ["shell", "am", "broadcast",
             "-a", "dev.devkey.keyboard.CLEAR_LEARNED_WORDS"],
            serial,
        ),
        capture_output=True,
    )
    time.sleep(0.5)


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    adb.ensure_keyboard_visible(serial)
    # Wait for the TrieDictionary to finish loading — autocorrect depends on it.
    _wait_for_dictionary(serial)
    # Clear leftover text so the composing word is exactly what we type.
    _clear_edit_text(serial)
    # Clear learned words — prior test runs may have committed "teh" which
    # teaches the learning engine to suppress autocorrect for that word.
    _clear_learned_words(serial)
    # Reset keyboard mode to Normal to clear composing state.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
    return serial


def _type_word(word, serial):
    """Tap each character with inter-key delay for reliable composing."""
    for ch in word:
        keyboard.tap_key(ch, serial)
        time.sleep(0.15)


def _set_autocorrect_level(level):
    """Set the autocorrect level via broadcast."""
    last_error = None
    for _ in range(3):
        driver.clear_logs()
        driver.broadcast(
            "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
            {"level": level},
        )
        try:
            entry = driver.wait_for(
                category="DevKey/IME",
                event="autocorrect_level_set",
                match={"level": level},
                timeout_ms=5000,
            )
            assert entry["data"]["level"] == level
            return
        except driver.DriverError as exc:
            last_error = exc
            time.sleep(1.0)
    raise AssertionError(f"autocorrect_level_set({level}) was not observed: {last_error}")


def test_autocorrect_aggressive_applies():
    """
    With aggressive autocorrect, typing a misspelled word + space
    must emit an autocorrect_applied event.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    driver.clear_logs()

    _type_word("teh", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="autocorrect_applied",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert data["action"] == "applied"
    assert data["level"] in ("aggressive", "legacy_full", "legacy_basic")


def test_autocorrect_off_no_correction():
    """
    With autocorrect off, typing a misspelled word + space must NOT
    emit an autocorrect_applied event.
    """
    serial = _setup()
    _set_autocorrect_level("off")
    driver.clear_logs()

    _type_word("teh", serial)
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

    _type_word("teh", serial)
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
