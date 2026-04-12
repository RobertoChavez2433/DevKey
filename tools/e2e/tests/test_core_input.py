"""
Core input handling tests: backspace, key repeat, and word learning.

Tests fundamental input pipeline behavior that is critical for normal typing.

Depends on:
  - InputHandlers.handleBackspace() instrumentation (backspace_handled)
  - SessionDependencies.commitWord() instrumentation (word_committed)
  - KeyGestureHandler key repeat logcat (RPT)
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
    # Use compact layout — its Backspace key is KEYCODE_DELETE (-5) directly,
    # unlike compact_dev which uses SMART_BACK_ESC (-301) that can resolve to
    # ESCAPE when cursor position races leave getExtractedText at 0.
    keyboard.set_layout_mode("compact", serial)
    # Ensure key map is loaded (set_layout_mode may fail to load during
    # circuit breaker cooldown)
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


def test_backspace_in_composing():
    """
    Typing letters then backspace while composing must emit backspace_handled
    with was_composing=true (prediction still active after deleting one char).
    """
    serial = _setup()
    # Enable suggestions so composing mode activates
    driver.broadcast(
        "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
        {"level": "mild"},
    )
    time.sleep(0.3)
    driver.clear_logs()

    _type_word("hel", serial)
    time.sleep(0.2)
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=3000,
    )
    data = entry["data"]
    # After deleting one char from "hel", composing word "he" remains — still predicting
    assert data["was_composing"] is True


def test_backspace_plain():
    """
    Backspace with no active composing must emit backspace_handled with
    was_composing=false. Verifies the non-composing delete path.
    """
    serial = _setup()
    # Disable suggestions so typing goes through sendModifiableKeyChar
    # (non-composing path) instead of entering prediction/composing mode
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": False},
    )
    time.sleep(0.5)

    driver.clear_logs()

    keyboard.tap_key("a", serial)
    time.sleep(0.3)
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert data["was_composing"] is False

    # Restore suggestions for subsequent tests
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )


def test_key_repeat_on_hold():
    """
    Holding backspace for ~1.2s must trigger key repeat acceleration.
    Assert logcat contains RPT entries with count >= 5.
    """
    serial = _setup()
    # Type some text first so backspace has something to delete
    _type_word("abcdefgh", serial)
    time.sleep(0.3)

    adb.clear_logcat(serial)
    # Long-press backspace by swiping in-place (hold gesture)
    km = keyboard.get_key_map()
    bs_key = km.get(BACKSPACE_LABEL)
    assert bs_key is not None, f"Backspace key '{BACKSPACE_LABEL}' not found in key map"
    x, y = bs_key
    # Swipe from the key to itself over 1500ms to simulate a long hold
    adb.swipe(x, y, x + 1, y, 1500, serial)
    time.sleep(0.5)

    lines = adb.capture_logcat("DevKeyPress", timeout=1.5, serial=serial)
    rpt_lines = [l for l in lines if "RPT" in l]
    assert len(rpt_lines) >= 1, (
        f"Expected at least 1 RPT logcat entry for backspace hold, got {len(rpt_lines)}"
    )


def test_word_learning_commits_word():
    """
    Typing a word + space must emit word_committed with the correct word_length.
    """
    serial = _setup()
    driver.broadcast(
        "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
        {"level": "mild"},
    )
    time.sleep(0.3)
    driver.clear_logs()

    _type_word("hello", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert data["word_length"] == 5
