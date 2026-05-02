"""
Autocorrect stress tests.

Exercises the autocorrect pipeline under sustained typing load:
rapid word sequences, backspace revert, mixed paragraph input,
and autocorrect-then-suggestion chaining.
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
BACKSPACE_LABEL = "Backspace"

COMMON_WORDS = [
    "the", "of", "and", "to", "in", "is", "it", "for", "on", "was",
]

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


def test_aggressive_10_words():
    """
    Type 10 common words rapidly with aggressive autocorrect enabled.
    Verifies no crash occurs -- checks that a next_word_suggestions
    event fires after each space.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    event_count = 0

    for word in COMMON_WORDS:
        driver.clear_logs()
        keyboard.tap_sequence(word, delay=0.08, serial=serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            "DevKey/TXT", "next_word_suggestions",
            timeout_ms=5000,
        )
        assert "source" in entry["data"]
        event_count += 1
        time.sleep(0.2)

    assert event_count == 10, (
        f"Expected 10 next_word_suggestions events, got {event_count}"
    )


def test_type_and_backspace_revert():
    """
    Type a word + space + backspace. Verify the backspace is handled
    (next_word_suggestions fires for the space, then the pipeline
    remains responsive after backspace).
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    driver.clear_logs()

    keyboard.tap_sequence("hello", delay=0.08, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=8000,
    )
    assert "source" in entry["data"]

    # Backspace after space
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    # Verify the pipeline is still responsive by typing another word + space
    driver.clear_logs()
    keyboard.tap_sequence("world", delay=0.08, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry2 = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=8000,
    )
    assert "source" in entry2["data"], (
        "Pipeline should remain responsive after backspace revert"
    )


def test_mixed_paragraph():
    """
    Mix short and medium words rapidly with aggressive autocorrect.
    Verifies no crash and that the prediction pipeline stays alive.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    words = ["i", "am", "here", "now", "at", "the", "end", "of", "it", "all"]
    event_count = 0

    for word in words:
        driver.clear_logs()
        keyboard.tap_sequence(word, delay=0.05, serial=serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            "DevKey/TXT", "next_word_suggestions",
            timeout_ms=5000,
        )
        assert "source" in entry["data"]
        event_count += 1

    assert event_count == len(words), (
        f"Expected {len(words)} prediction events, got {event_count}"
    )


def test_autocorrect_then_suggestion():
    """
    Type a word + space and check that the prediction pipeline fires
    after the autocorrect path completes.
    """
    serial = _setup()
    _set_autocorrect_level("aggressive")
    driver.clear_logs()

    keyboard.tap_sequence("the", delay=0.08, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert "source" in data, (
        "next_word_suggestions should fire after autocorrect + space"
    )
    valid_sources = {
        "bigram_hit", "bigram_miss", "no_prev_word",
        "space_only", "picked_default",
    }
    assert data["source"] in valid_sources, (
        f"Unexpected source after autocorrect: {data['source']}"
    )
