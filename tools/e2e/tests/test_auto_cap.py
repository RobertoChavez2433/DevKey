"""
Auto-capitalization tests.

Tests that Shift is automatically engaged after sentence separators and on
fresh field focus.

Depends on:
  - ModifierHandler.updateShiftKeyState() instrumentation (auto_cap_applied)
  - SET_BOOL_PREF broadcast for auto_cap toggle
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
PERIOD_CODE = 46


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
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        keyboard.load_key_map(serial)
    _clear_edit_text(serial)
    # Ensure auto-cap is enabled via toggle false→true so
    # onSharedPreferenceChanged fires and mAutoCapActive is set.
    for attempt in range(3):
        try:
            driver.clear_logs()
            driver.broadcast(
                "dev.devkey.keyboard.SET_BOOL_PREF",
                {"key": "auto_cap", "value": False},
            )
            driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
            driver.clear_logs()
            driver.broadcast(
                "dev.devkey.keyboard.SET_BOOL_PREF",
                {"key": "auto_cap", "value": True},
            )
            driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
            break
        except Exception:
            time.sleep(2.0)
    time.sleep(0.3)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
    # Warmup: type a space and delete it to settle the Compose layout
    # (first key press triggers candidate strip recomposition).
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.3)
    keyboard.tap_key_by_code(-301, serial)  # backspace
    time.sleep(0.3)
    _clear_edit_text(serial)
    # Reload key map after the keyboard has settled
    keyboard.load_key_map(serial)
    return serial


def _type_word(word, serial):
    """Tap each character with inter-key delay for reliable composing."""
    for i, ch in enumerate(word):
        keyboard.tap_key(ch, serial)
        # First character triggers candidate strip recomposition on the
        # emulator — allow extra settle time so subsequent taps aren't
        # mis-dispatched to wrong keys during Compose re-layout.
        time.sleep(0.35 if i == 0 else 0.15)


def test_auto_cap_after_period():
    """
    Typing 'hi. ' must trigger auto-capitalization — Shift should engage
    after a period + space (sentence boundary).
    """
    serial = _setup()
    driver.clear_logs()

    _type_word("hi", serial)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    # Period triggers word commit + suggestion recomposition;
    # allow settle time before the space tap.
    time.sleep(0.35)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/IME",
        event="auto_cap_applied",
        timeout_ms=5000,
    )
    assert entry is not None
    data = entry["data"]
    assert "new_shift_state" in data


def test_auto_cap_after_second_sentence():
    """
    Type two sentences separated by '. ' and verify auto-cap fires for
    each sentence boundary. This confirms auto-cap works repeatedly within
    the same input session, not just once.
    """
    serial = _setup()
    _clear_edit_text(serial)
    driver.clear_logs()

    # First sentence: "hi. " triggers auto-cap
    _type_word("hi", serial)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    time.sleep(0.35)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry1 = driver.wait_for(
        category="DevKey/IME",
        event="auto_cap_applied",
        timeout_ms=5000,
    )
    assert entry1 is not None

    # Type second sentence content (lowercase to consume shift)
    _type_word("ok", serial)
    keyboard.tap_key_by_code(PERIOD_CODE, serial)
    time.sleep(0.35)

    driver.clear_logs()
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    # Second sentence boundary also triggers auto-cap
    entry2 = driver.wait_for(
        category="DevKey/IME",
        event="auto_cap_applied",
        timeout_ms=5000,
    )
    assert entry2 is not None
