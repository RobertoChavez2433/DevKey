"""Shared punctuation E2E setup and privacy-safe observations."""
import time

from lib import adb, driver, keyboard

SPACE_CODE = 32
ENTER_CODE = 10
PERIOD_CODE = 46
COMMA_CODE = 44
EXCLAMATION_CODE = 33


def setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("compact", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    wait_for_dictionary(serial)
    clear_edit_text(serial)
    set_bool_pref("show_suggestions", False)
    set_bool_pref("show_suggestions", True)
    set_autocorrect_level("aggressive")
    warmup_layout(serial)
    clear_edit_text(serial)
    return serial


def type_word(word, serial):
    for i, ch in enumerate(word):
        keyboard.tap_key(ch, serial)
        time.sleep(0.35 if i == 0 else 0.15)


def clear_edit_text(serial):
    adb.clear_test_host_text(serial)
    time.sleep(0.1)


def assert_text_length_at_least(serial, minimum, context):
    state = adb.query_test_host_state(serial, timeout_ms=2000)
    length = int(state.get("text_length", -1))
    assert length >= minimum, f"{context}; length={length}, expected>={minimum}"
    return state


def wait_for_dictionary(serial):
    deadline = time.time() + 2.0
    while time.time() < deadline:
        lines = adb.capture_logcat("DevKey/DictMgr", timeout=0.2, serial=serial)
        if any("TrieDictionary loaded" in line for line in lines):
            return
        time.sleep(0.2)


def set_bool_pref(key, value):
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": key, "value": value},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
    except Exception:
        pass


def set_autocorrect_level(level):
    for _ in range(3):
        try:
            driver.clear_logs()
            driver.broadcast(
                "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
                {"level": level},
            )
            driver.wait_for("DevKey/IME", "autocorrect_level_set", timeout_ms=2000)
            return
        except Exception:
            time.sleep(1.0)


def warmup_layout(serial):
    time.sleep(0.3)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.3)
    keyboard.tap_key("Backspace", serial)
    time.sleep(0.3)
