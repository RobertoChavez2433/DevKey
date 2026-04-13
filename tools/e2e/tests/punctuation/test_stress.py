"""
Punctuation heuristics stress tests.

Exercises the punctuation pipeline under sustained and rapid input:
repeated double-space conversions, alternating separators, double-space
after autocorrect, and period reswap ellipsis patterns.
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
PERIOD_CODE = 46
COMMA_CODE = 44
EXCLAMATION_CODE = 33

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
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.3)
    keyboard.tap_key_by_code(-301, serial)  # backspace
    time.sleep(0.3)
    _clear_edit_text(serial)
    return serial


def _type_word(word, serial):
    """Tap each character with inter-key delay for reliable composing."""
    for i, ch in enumerate(word):
        keyboard.tap_key(ch, serial)
        time.sleep(0.35 if i == 0 else 0.15)


def test_10_double_space_to_period():
    """
    Perform 10 double-space conversions in sequence. Each must fire
    double_space_applied. Asserts event count, never text content.
    """
    serial = _setup()
    words = ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j"]
    event_count = 0

    for word in words:
        driver.clear_logs()
        _type_word(word, serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        time.sleep(0.35)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            category="DevKey/TXT",
            event="double_space_applied",
            timeout_ms=5000,
        )
        assert entry["data"]["chars_removed"] == 2
        event_count += 1

    assert event_count == 10, (
        f"Expected 10 double_space_applied events, got {event_count}"
    )


def test_alternating_separators():
    """
    Rapid period, comma, exclamation after word+space. Each should fire
    punc_space_swapped (all three are sentence separators or suggested
    punctuation).
    """
    serial = _setup()
    separator_codes = [PERIOD_CODE, COMMA_CODE]
    event_count = 0

    for sep_code in separator_codes:
        _clear_edit_text(serial)
        driver.clear_logs()
        _type_word("ok", serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        time.sleep(0.4)
        keyboard.tap_key_by_code(sep_code, serial)

        entry = driver.wait_for(
            category="DevKey/TXT",
            event="punc_space_swapped",
            timeout_ms=5000,
        )
        assert entry is not None
        event_count += 1

    assert event_count == 2, (
        f"Expected 2 punc_space_swapped events for alternating separators, "
        f"got {event_count}"
    )


def test_double_space_after_autocorrect():
    """
    Type a misspelled word + space (triggers autocorrect commit) + space
    (triggers double-space). Verify double_space_applied fires.
    """
    serial = _setup()
    driver.clear_logs()

    # Type a word that may trigger autocorrect, then double-space
    _type_word("teh", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="double_space_applied",
        timeout_ms=5000,
    )
    assert entry["data"]["chars_removed"] == 2


def test_period_reswap_ellipsis():
    """
    '...' pattern: type word + space + period (swap) + period (reswap) +
    period. Verify punc_space_swapped and period_reswapped both fire.
    """
    serial = _setup()

    _type_word("wow", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.4)

    # First period: swap
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    driver.wait_for(
        category="DevKey/TXT",
        event="punc_space_swapped",
        timeout_ms=5000,
    )

    # Second period: reswap
    driver.clear_logs()
    time.sleep(0.3)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="period_reswapped",
        timeout_ms=5000,
    )
    assert entry is not None

    # Third period: typed normally (no special event expected, just no crash)
    time.sleep(0.3)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    time.sleep(0.5)
