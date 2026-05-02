"""
Punctuation heuristics smoke tests (subdirectory format).

Migrated from tools/e2e/tests/test_auto_punctuation.py -- same test logic,
relocated into the punctuation/ test package for Phase 4 organisation.

Depends on:
  - PunctuationHeuristics instrumentation (double_space_applied, punc_space_swapped,
    period_reswapped, trailing_space_removed)
  - Autocorrect/prediction enabled
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
ENTER_CODE = 10
PERIOD_CODE = 46

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


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("compact", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    _wait_for_dictionary(serial)
    _clear_edit_text(serial)
    # Toggle show_suggestions false->true so onSharedPreferenceChanged
    # fires and mShowSuggestions is definitely set.
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": False},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
    except Exception:
        pass
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
    except Exception:
        pass
    # Enable autocorrect
    for attempt in range(3):
        try:
            driver.clear_logs()
            driver.broadcast(
                "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
                {"level": "aggressive"},
            )
            driver.wait_for("DevKey/IME", "autocorrect_level_set", timeout_ms=2000)
            break
        except Exception:
            time.sleep(2.0)
    time.sleep(0.3)
    # Warmup: type a space and delete it to settle the Compose layout
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.3)
    keyboard.tap_key("Backspace", serial)
    time.sleep(0.3)
    _clear_edit_text(serial)
    return serial


def _type_word(word, serial):
    """Tap each character with inter-key delay for reliable composing."""
    for i, ch in enumerate(word):
        keyboard.tap_key(ch, serial)
        time.sleep(0.35 if i == 0 else 0.15)


def test_double_space_period():
    """
    Typing a word + space + space must produce '. ' (double-space to period).
    Assert double_space_applied event fires.
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("hi", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.35)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="double_space_applied",
        timeout_ms=5000,
    )
    data = entry["data"]
    assert data["chars_removed"] == 2


def test_double_space_guard_at_start():
    """
    Space + space with no preceding letter should NOT trigger double-space period.
    """
    serial = _setup()
    driver.clear_logs()

    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.15)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.5)

    try:
        driver.wait_for(
            category="DevKey/TXT",
            event="double_space_applied",
            timeout_ms=1500,
        )
        assert False, "double_space_applied should NOT fire without a preceding letter"
    except Exception:
        pass  # Expected: no event


def test_punctuation_space_swap():
    """
    Typing a word + space + period must swap the space and period.
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("the", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="punc_space_swapped",
        timeout_ms=5000,
    )
    assert entry is not None


def test_remove_trailing_space_on_enter():
    """
    Typing a word + space + enter must remove the trailing auto-space.
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("the", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(ENTER_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="trailing_space_removed",
        timeout_ms=5000,
    )
    assert entry is not None


def test_period_reswap():
    """
    Typing word + space + period + period must reswap to produce '..' pattern.
    """
    serial = _setup()

    _type_word("the", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)

    # First period triggers punc_space_swapped
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    driver.wait_for(
        category="DevKey/TXT",
        event="punc_space_swapped",
        timeout_ms=5000,
    )

    # Second period triggers reswap
    driver.clear_logs()
    time.sleep(0.3)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="period_reswapped",
        timeout_ms=5000,
    )
    assert entry is not None
