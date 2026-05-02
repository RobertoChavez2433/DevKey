"""
Input E2E stress tests.

Exercises the input pipeline under sustained and rapid keystroke loads:
  1. 50-word paragraph typing
  2. Rapid backspace deletion
  3. Backspace in composing vs after commit
  4. Enter mid-composing
  5. Mixed separators and letters

Depends on:
  - InputHandlers.handleBackspace() instrumentation (backspace_handled)
  - SessionDependencies.commitWord() instrumentation (word_committed)
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
ENTER_CODE = 10
BACKSPACE_LABEL = "Backspace"

# 50 common short words (3-5 chars each)
_WORDS = [
    "the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
    "her", "was", "one", "our", "out", "day", "get", "has", "him", "his",
    "how", "its", "may", "new", "now", "old", "see", "way", "who", "did",
    "boy", "let", "say", "she", "too", "use", "dad", "mom", "run", "big",
    "end", "far", "got", "hot", "lot", "put", "red", "set", "top", "try",
]


def _clear_edit_text(serial):
    """Clear the TestHostActivity EditText and verify the field length is zero."""
    adb.clear_test_host_text(serial)


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("compact", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    _clear_edit_text(serial)
    # Ensure suggestions are enabled — a prior test may have disabled them.
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=3000)
    except Exception:
        pass
    return serial


def _type_word(word, serial):
    """Tap each character with inter-key delay for reliable composing."""
    for ch in word:
        keyboard.tap_key(ch, serial)
        time.sleep(0.15)


def _ensure_suggestions(serial):
    """Enable suggestions and mild autocorrect for composing mode."""
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=3000)
    driver.broadcast(
        "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
        {"level": "mild"},
    )
    time.sleep(0.3)


def test_50_word_paragraph():
    """
    Type 50 short words separated by spaces. Verify the driver is still
    responsive after sustained typing load.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    for word in _WORDS:
        _type_word(word, serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        time.sleep(0.05)

    state = adb.query_test_host_state(serial)
    assert state["text_length"] >= len(_WORDS) * 2
    log = driver.logcat_dump(["DevKey/TXT"])
    committed_count = log.count("word_committed")
    assert committed_count >= 45, (
        f"Expected at least 45 committed-word events, got {committed_count}"
    )


def test_rapid_backspace_delete_all():
    """
    Type 20 chars then backspace 25 times (more than typed). Verify driver
    health — the pipeline must not crash on over-deletion.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    _type_word("abcdefghijklmnopqrst", serial)
    time.sleep(0.2)

    for _ in range(25):
        keyboard.tap_key(BACKSPACE_LABEL, serial)
        time.sleep(0.05)

    state = adb.query_test_host_state(serial)
    assert state["text_length"] == 0
    log = driver.logcat_dump(["DevKey/TXT"])
    backspace_count = log.count("backspace_handled")
    assert backspace_count >= 20, (
        f"Expected at least 20 backspace events, got {backspace_count}"
    )


def test_backspace_during_composing_vs_after_commit():
    """
    Exercise both backspace paths in sequence:
      1. Backspace while composing (was_composing=True)
      2. Backspace after commit (was_composing=False)
    """
    serial = _setup()
    _ensure_suggestions(serial)

    # --- composing path ---
    driver.clear_logs()
    _type_word("hel", serial)
    time.sleep(0.3)
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=5000,
    )
    assert entry["data"]["was_composing"] is True

    # --- commit then non-composing backspace ---
    _clear_edit_text(serial)
    driver.clear_logs()
    _type_word("hi", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.5)
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=5000,
    )
    assert entry["data"]["was_composing"] is False


def test_enter_mid_composing():
    """
    Press Enter (keycode 10) while composing. The composing word should be
    committed before the newline is inserted.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    _type_word("hel", serial)
    time.sleep(0.3)
    keyboard.tap_key_by_code(ENTER_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=8000,
    )
    assert entry["data"]["word_length"] == 3


def test_mixed_separators_and_letters():
    """
    Rapidly alternate letters and separators (space, period, comma).
    Verify the pipeline does not crash under mixed-mode input.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    separator_codes = [SPACE_CODE, 46, 44]  # space, period, comma
    letters = "abcdefghij"

    for i, ch in enumerate(letters):
        keyboard.tap_key(ch, serial)
        time.sleep(0.05)
        sep_code = separator_codes[i % len(separator_codes)]
        keyboard.tap_key_by_code(sep_code, serial)
        time.sleep(0.05)

    state = adb.query_test_host_state(serial)
    assert state["text_length"] >= len(letters)
    log = driver.logcat_dump(["DevKey/TXT"])
    key_event_count = log.count("key_event")
    assert key_event_count >= len(letters), (
        f"Expected at least {len(letters)} key events, got {key_event_count}"
    )
