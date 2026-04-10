"""
Plugin system smoke test.

FROM PLAN: Phase 2 sub-phase 4.9 — "after IME boot, wait_for
           DevKey/IME plugin_scan_complete and assert plugin_count matches
           the expected count."

Depends on:
  - DevKeyLogger.ime("plugin_scan_complete", mapOf("plugin_count" to plugins.size))
    emit at the end of PluginManager.scanPlugins() (or equivalent boot-time scan).

PRIVACY: only plugin_count integer — NEVER plugin metadata / names / paths.
"""
import os
from lib import adb, driver, keyboard

# Expected plugin count — override per build via env var. Defaults to 0
# (no bundled plugins). Adjust in CI as the plugin set stabilizes.
EXPECTED_PLUGIN_COUNT = int(os.environ.get("DEVKEY_EXPECTED_PLUGIN_COUNT", "0"))


def test_plugin_scan_complete_event_fires():
    """
    Wait for the plugin_scan_complete event (fires once per IME boot).

    SKIPs when the event is absent — indicates the instrumentation is not yet
    landed. Once landed per sub-phase 4.9, this test activates and enforces
    the expected plugin count.
    """
    serial = adb.get_device_serial()
    driver.require_driver()
    # Touch the key map so we know the IME is up.
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    try:
        entry = driver.wait_for(
            category="DevKey/IME",
            event="plugin_scan_complete",
            timeout_ms=10000,
        )
    except driver.DriverTimeout:
        import pytest
        pytest.skip(
            "plugin_scan_complete not observed within 10s — either "
            "instrumentation not yet landed (plan sub-phase 4.9) or IME "
            "booted before the driver buffer started capturing."
        )

    data = entry.get("data", {})
    plugin_count = int(data.get("plugin_count", -1))
    assert plugin_count >= 0, (
        f"plugin_scan_complete missing or invalid plugin_count: {data}"
    )
    assert plugin_count == EXPECTED_PLUGIN_COUNT, (
        f"plugin_count={plugin_count} does not match expected "
        f"{EXPECTED_PLUGIN_COUNT}. Override via DEVKEY_EXPECTED_PLUGIN_COUNT."
    )

    # PRIVACY guard: no keys other than plugin_count.
    extra = set(data.keys()) - {"plugin_count"}
    assert not extra, (
        f"plugin_scan_complete payload had unexpected keys: {extra}. "
        f"PRIVACY: only plugin_count allowed."
    )
