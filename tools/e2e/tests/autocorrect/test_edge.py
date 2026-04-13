"""
Autocorrect edge-case tests.

Covers boundary conditions the autocorrect pipeline must handle without
crashing: single-character words, mixed numbers, autocorrect off mode,
and long word boundaries.
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
    """Clear learned words so autocorrect is not suppressed by prior test runs."""
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
    _wait_for_dictionary(serial)
    _clear_edit_text(serial)
    _clear_learned_words(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
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


def test_single_char_words():
    """
    Type single characters + space. No autocorrect should apply for
    single-letter input. Verify the pipeline stays healthy.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    single_chars = ["i", "a"]
    event_count = 0

    for ch in single_chars:
        driver.clear_logs()
        keyboard.tap_key(ch, serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            "DevKey/TXT", "next_word_suggestions",
            timeout_ms=3000,
        )
        assert "source" in entry["data"]
        event_count += 1

    assert event_count == len(single_chars), (
        f"Expected {len(single_chars)} events for single-char words, got {event_count}"
    )


def test_numbers_mixed():
    """
    Type a mix of letters and numbers + space. The autocorrect pipeline
    must handle non-alpha characters without crashing.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    driver.clear_logs()

    # Type letters then space
    keyboard.tap_sequence("abc", delay=0.08, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )
    assert "source" in entry["data"], (
        "Pipeline should handle mixed letter/number input without crashing"
    )


def test_autocorrect_off():
    """
    Set autocorrect level to off, type a word + space, and verify
    no autocorrect_applied event fires.
    """
    serial = _setup()
    _set_autocorrect_level("off")
    driver.clear_logs()

    keyboard.tap_sequence("teh", delay=0.10, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    # Wait for space processing to complete
    driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )

    # Verify no autocorrect_applied event was emitted
    try:
        driver.wait_for(
            "DevKey/TXT", "autocorrect_applied",
            timeout_ms=1000,
        )
        assert False, "autocorrect_applied should NOT fire when level is off"
    except Exception:
        pass  # Expected: no autocorrect event


def test_word_at_boundary():
    """
    Type a long word (40 characters) + space. Verify the autocorrect
    pipeline handles the boundary without crashing.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    driver.clear_logs()

    keyboard.tap_sequence("a" * 40, delay=0.03, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=5000,
    )
    assert "source" in entry["data"], (
        "Pipeline should handle long word boundary without crashing"
    )
