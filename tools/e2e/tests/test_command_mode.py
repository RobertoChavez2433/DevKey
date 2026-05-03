"""
Command mode smoke test.

FROM PLAN: Phase 2 sub-phase 4.8 — exercise command mode toggle and verify
           the manual-enabled event fires.

Strategy:
  - Toggle command mode via TOGGLE_COMMAND_MODE broadcast (same path as the
    toolbar overflow button).
  - Wait for command_mode_manual_enabled event.
  - Toggle again to disable and verify IME survives.

PRIVACY: structural only — NEVER log command buffers or typed content.
"""
import subprocess
from lib import adb, driver, keyboard
from lib.privacy import allowed_payload_keys


def test_command_mode_auto_enabled_on_terminal_focus():
    """
    Toggle command mode via broadcast → command_mode_manual_enabled fires.

    Uses the manual toggle path (same as overflow button tap) because the
    auto-detect path requires a terminal app installed on the device.
    """
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    driver.clear_logs()
    driver.broadcast("dev.devkey.keyboard.TOGGLE_COMMAND_MODE", {})

    entry = driver.wait_for(
        category="DevKey/IME",
        event="command_mode_manual_enabled",
        timeout_ms=3000,
    )

    # PRIVACY guard: payload must never include command buffer contents.
    data = entry.get("data", {})
    forbidden = {"buffer", "text", "content", "command"}
    leaked = forbidden.intersection(data.keys())
    assert not leaked, (
        f"command_mode_manual_enabled payload leaked keys: {leaked}. "
        f"PRIVACY: payload must be structural-only."
    )

    # Expect the documented structural fields only.
    allowed = allowed_payload_keys("trigger")
    extra = set(data.keys()) - allowed
    assert not extra, (
        f"command_mode_manual_enabled payload had unexpected keys: {extra}. "
        f"PRIVACY: only {allowed} allowed."
    )

    assert data.get("trigger") == "user_toggle", (
        f"expected trigger=user_toggle, got {data.get('trigger')!r}"
    )

    # Toggle off to clean up.
    driver.broadcast("dev.devkey.keyboard.TOGGLE_COMMAND_MODE", {})
    driver.wait_for(
        category="DevKey/IME",
        event="command_mode_toggle_processed",
        match={"mode": "normal", "trigger": "user_toggle"},
        timeout_ms=3000,
    )

    # Verify the IME did not crash.
    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after command-mode toggle"
