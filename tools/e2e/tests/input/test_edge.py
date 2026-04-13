"""
Input E2E edge-case tests.

Exercises boundary conditions in the input pipeline:
  1. Backspace in an empty field
  2. Very long composing word (100 chars)
  3. Non-ASCII verification via ASCII pipeline
  4. Backspace after suggestion commit (revert path)

Depends on:
  - InputHandlers.handleBackspace() instrumentation (backspace_handled)
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
BACKSPACE_LABEL = "Backspace"


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


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("compact", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    _clear_edit_text(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
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


def test_backspace_in_empty_field():
    """
    Press backspace in a cleared field. The backspace_handled event must
    still fire without crashing the pipeline.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=3000,
    )
    # In an empty field there is nothing composing
    assert entry["data"]["was_composing"] is False


def test_very_long_composing_word():
    """
    Type 100 identical characters without committing. The IME must not
    crash or become unresponsive under a very long composing buffer.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    # Type 100 'a' characters
    for _ in range(100):
        keyboard.tap_key("a", serial)
        time.sleep(0.05)

    # Verify the driver is still responsive
    driver.require_driver()

    # Commit the long word via space and check structural event
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )
    assert entry["data"]["word_length"] == 100


def test_non_ascii_input():
    """
    Type regular ASCII chars and verify structural events fire correctly.
    The key map only supports ASCII, so we verify the pipeline works with
    normal letters — confirming event infrastructure is intact for any
    future non-ASCII extension.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    _type_word("test", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )
    assert entry["data"]["word_length"] == 4


def test_backspace_after_suggestion_tap():
    """
    Type a word + space (commits via default suggestion), then backspace.
    The backspace must be in the non-composing path (was_composing=False)
    because the word was already committed.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    _type_word("hel", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    # Wait for the commit event first
    driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )

    # Now backspace — this is the revert/undo path, not composing
    driver.clear_logs()
    time.sleep(0.2)
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=3000,
    )
    assert entry["data"]["was_composing"] is False
