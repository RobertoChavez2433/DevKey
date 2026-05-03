"""Shared autocorrect E2E helpers with privacy-safe host text assertions."""
import time

from lib import adb, driver, keyboard

SPACE_CODE = 32
VALID_AUTOCORRECT_LEVELS = ("aggressive", "legacy_full", "legacy_basic")

_dictionary_ready = False


def setup_autocorrect():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    adb.ensure_keyboard_visible(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    set_bool_pref("devkey_show_number_row", False)
    set_bool_pref("devkey_show_toolbar", False)
    set_bool_pref("show_suggestions", True)
    set_bool_pref("auto_cap", False)
    keyboard.set_layout_mode("full", serial)
    wait_for_dictionary(serial)
    adb.clear_test_host_text(serial)
    clear_learned_words(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(2.0)
    return serial


def wait_for_dictionary(serial):
    global _dictionary_ready
    if _dictionary_ready:
        return
    # The isolated runner clears logcat before each test, so the one-time
    # dictionary-loaded log is often gone even when the dictionary is ready.
    # Keep this as a short best-effort settle instead of stalling every test.
    deadline = time.time() + 2.0
    while time.time() < deadline:
        lines = adb.capture_logcat("DevKey/DictMgr", timeout=0.2, serial=serial)
        if any("AnySoftKeyboard dictionary loaded" in line for line in lines):
            _dictionary_ready = True
            return
        time.sleep(0.2)
    _dictionary_ready = True


def clear_learned_words(serial):
    adb.clear_learned_words(serial)


def set_autocorrect_level(level):
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
            if entry["data"]["level"] == level:
                time.sleep(0.4)
                return
        except driver.DriverError as exc:
            last_error = exc
            time.sleep(1.0)
    raise AssertionError(f"autocorrect_level_set({level}) was not observed: {last_error}")


def set_bool_pref(key, value):
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": key, "value": value},
    )
    entry = driver.wait_for(
        category="DevKey/IME",
        event="bool_pref_set",
        match={"key": key},
        timeout_ms=5000,
    )
    if entry["data"].get("value") != value:
        raise AssertionError(f"bool_pref_set({key}) value mismatch")


def type_word(word, serial, delay=0.15):
    for ch in word:
        keyboard.load_key_map(serial)
        keyboard.tap_key(ch, serial)
        time.sleep(delay)


def tap_word_with_space(word, serial, delay=0.15):
    type_word(word, serial, delay=delay)
    tap_space(serial)


def tap_sequence_with_space(text, serial, delay=0.08):
    type_word(text, serial, delay=delay)
    tap_space(serial)


def tap_space(serial):
    keyboard.load_key_map(serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)


def wait_autocorrect_applied(timeout_ms=5000):
    entry = driver.wait_for(
        category="DevKey/TXT",
        event="autocorrect_applied",
        timeout_ms=timeout_ms,
    )
    data = entry["data"]
    if data.get("action") != "applied":
        raise AssertionError("autocorrect_applied did not report applied action")
    if data.get("level") not in VALID_AUTOCORRECT_LEVELS:
        raise AssertionError(f"Unexpected autocorrect level: {data.get('level')}")
    return data


def wait_next_word_suggestions(timeout_ms=5000):
    entry = driver.wait_for(
        "DevKey/TXT",
        "next_word_suggestions",
        timeout_ms=timeout_ms,
    )
    if "source" not in entry["data"]:
        raise AssertionError("next_word_suggestions payload missing source")
    return entry["data"]


def assert_no_autocorrect_event(timeout_ms=1000):
    try:
        driver.wait_for(
            "DevKey/TXT",
            "autocorrect_applied",
            timeout_ms=timeout_ms,
        )
    except driver.DriverTimeout:
        return
    raise AssertionError("autocorrect_applied fired when correction should be disabled")


def assert_controlled_text_equals(serial, expected, context):
    adb.assert_test_host_text_equals(expected, serial=serial, context=context)
