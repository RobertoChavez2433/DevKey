"""
Input E2E smoke tests — migrated from test_core_input.py.

Covers the four fundamental input pipeline behaviors:
  1. Backspace while composing
  2. Backspace with no composing (plain delete)
  3. Key repeat on long-press hold
  4. Word learning / commit via space

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


def test_backspace_in_composing():
    """
    Typing letters then backspace while composing must emit backspace_handled
    with was_composing=true (prediction still active after deleting one char).
    """
    serial = _setup()
    driver.broadcast(
        "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
        {"level": "mild"},
    )
    time.sleep(0.3)
    driver.clear_logs()

    _type_word("hel", serial)
    time.sleep(0.3)
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert data["was_composing"] is True


def test_backspace_plain():
    """
    Backspace with no active composing must emit backspace_handled with
    was_composing=false. Verifies the non-composing delete path.
    """
    serial = _setup()
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
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=3000)


def test_key_repeat_on_hold():
    """
    Holding backspace for ~1.2s must trigger key repeat acceleration.
    Assert logcat contains RPT entries with count >= 1.
    """
    serial = _setup()
    _type_word("abcdefgh", serial)
    time.sleep(0.3)

    adb.clear_logcat(serial)
    km = keyboard.get_key_map()
    bs_key = km.get(BACKSPACE_LABEL)
    assert bs_key is not None, f"Backspace key '{BACKSPACE_LABEL}' not found in key map"
    x, y = bs_key
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
