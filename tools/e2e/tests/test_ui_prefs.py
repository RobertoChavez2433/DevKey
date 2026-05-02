"""
UI preference toggle tests: number row and toolbar visibility.

Tests that SET_BOOL_PREF broadcast toggles affect the rendered key map.
No new Kotlin instrumentation needed — uses existing key map dump.

Depends on:
  - SET_BOOL_PREF broadcast (devkey_show_number_row, devkey_show_toolbar)
  - DUMP_KEY_MAP broadcast
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    return serial


def _reload_key_map(serial):
    """Clear cache and reload the key map from the live keyboard."""
    keyboard.clear_key_map_cache()
    keyboard.load_key_map(serial)
    return keyboard.get_key_map()


def test_number_row_toggle():
    """
    Toggling devkey_show_number_row via SET_BOOL_PREF must change the total
    number of keys in the rendered key map (compact layouts only).
    The pref change triggers recomposition, so we re-set the layout mode
    after each toggle to force a full key map rebuild.
    """
    serial = _setup()

    # Enable number row, then set compact mode (triggers recomposition with number row)
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "devkey_show_number_row", "value": True},
    )
    driver.wait_for(
        "DevKey/IME",
        "bool_pref_set",
        match={"key": "devkey_show_number_row", "value": True},
        timeout_ms=3000,
    )
    time.sleep(0.3)
    keyboard.set_layout_mode("compact", serial)
    time.sleep(0.5)
    km_with = _reload_key_map(serial)
    count_with = len(km_with)

    # Disable number row, then re-set compact mode to force rebuild
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "devkey_show_number_row", "value": False},
    )
    driver.wait_for(
        "DevKey/IME",
        "bool_pref_set",
        match={"key": "devkey_show_number_row", "value": False},
        timeout_ms=3000,
    )
    time.sleep(0.3)
    keyboard.set_layout_mode("compact", serial)
    time.sleep(0.5)
    km_without = _reload_key_map(serial)
    count_without = len(km_without)

    assert count_with > count_without, (
        f"Enabling number row should produce more keys: "
        f"with={count_with} vs without={count_without}"
    )

    # Restore default
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "devkey_show_number_row", "value": True},
    )
    driver.wait_for(
        "DevKey/IME",
        "bool_pref_set",
        match={"key": "devkey_show_number_row", "value": True},
        timeout_ms=3000,
    )


def test_toolbar_toggle():
    """
    Toggling devkey_show_toolbar via SET_BOOL_PREF must be accepted by the
    IME and emit a bool_pref_set event for each transition.
    (Toolbar is a Compose-layer UI element not included in the key map dump,
    so we verify the pref write rather than key map changes.)
    """
    serial = _setup()

    # Enable toolbar
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "devkey_show_toolbar", "value": True},
    )
    entry = driver.wait_for(
        category="DevKey/IME",
        event="bool_pref_set",
        match={"key": "devkey_show_toolbar", "value": True},
        timeout_ms=3000,
    )
    assert entry["data"]["value"] is True

    # Disable toolbar
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "devkey_show_toolbar", "value": False},
    )
    entry = driver.wait_for(
        category="DevKey/IME",
        event="bool_pref_set",
        match={"key": "devkey_show_toolbar", "value": False},
        timeout_ms=3000,
    )
    assert entry["data"]["value"] is False
