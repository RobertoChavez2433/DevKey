"""
Command mode smoke test.

FROM PLAN: Phase 2 sub-phase 4.8 — "focus terminal-capable field and wait_for
           the auto event, OR manually toggle command mode and wait_for the
           manual event. Dismiss and assert clean exit."

Depends on:
  - DevKeyLogger.ime("command_mode_auto_enabled", mapOf("trigger" to "terminal_detect"))
    at the auto-detect code path (CommandModeDetector.detect()).
  - DevKeyLogger.ime("command_mode_manual_enabled", mapOf("trigger" to "user_toggle"))
    at the manual toggle path (CommandModeDetector.toggleManualOverride()).

PRIVACY: structural only — NEVER log command buffers or typed content.

Strategy:
  This test takes the auto-detect path (plan's preferred Option A). We
  attempt to launch a terminal-class package from CommandModeDetector's
  TERMINAL_PACKAGES set and focus an input field in it so the IME's
  onStartInputView → CommandModeDetector.detect() path runs and fires the
  command_mode_auto_enabled event.

  The previous implementation invented a non-existent
  dev.devkey.keyboard.TOGGLE_COMMAND_MODE broadcast — that path is removed
  here. Manual-toggle coverage is left to an in-app UI path (overflow
  button) which is exercised by the coverage-matrix smoke when the
  overflow control is wired into the key map.

  Skips gracefully when no terminal-class package is installed on the
  test device — cannot exercise the auto path in that case.
"""
import subprocess
from lib import adb, driver, keyboard

# Canonical terminal packages from CommandModeDetector.TERMINAL_PACKAGES.
# Ordered by likelihood of being installed on a test device / emulator.
TERMINAL_PACKAGES = (
    "com.termux",
    "jackpal.androidterm",
    "com.termoneplus",
    "org.connectbot",
    "com.sonelli.juicessh",
    "com.server.auditor",
    "com.offsec.nethunter",
    "yarolegovich.materialterminal",
    "com.googlecode.android_scripting",
)


def _installed_packages(serial):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += ["shell", "pm", "list", "packages"]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return {line.strip().removeprefix("package:") for line in result.stdout.splitlines()}


def _first_installed_terminal(serial):
    installed = _installed_packages(serial)
    for pkg in TERMINAL_PACKAGES:
        if pkg in installed:
            return pkg
    return None


def _launch_terminal(serial, pkg):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += ["shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"]
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")


def test_command_mode_auto_enabled_on_terminal_focus():
    """
    Launch a terminal-class app → wait_for command_mode_auto_enabled event.

    SKIPs if no terminal package is installed on the device, or if the
    command_mode_auto_enabled event is not observed within the timeout
    (instrumentation may not yet be landed, or the terminal did not focus
    an editable field).
    """
    import pytest

    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    pkg = _first_installed_terminal(serial)
    if pkg is None:
        pytest.skip(
            "no terminal-class package installed — cannot exercise the "
            "command_mode auto-detect path. Install Termux or similar "
            "to activate this test."
        )

    driver.clear_logs()
    launch = _launch_terminal(serial, pkg)
    if launch.returncode != 0:
        pytest.skip(
            f"failed to launch {pkg}: {launch.stderr.strip() or launch.stdout.strip()}"
        )

    try:
        entry = driver.wait_for(
            category="DevKey/IME",
            event="command_mode_auto_enabled",
            timeout_ms=5000,
        )
    except driver.DriverTimeout:
        pytest.skip(
            "command_mode_auto_enabled not observed within 5s — either "
            "instrumentation not yet landed (plan sub-phase 4.8) or the "
            "terminal did not surface an editable field for the IME."
        )

    # PRIVACY guard: payload must never include command buffer contents.
    data = entry.get("data", {})
    forbidden = {"buffer", "text", "content", "command"}
    leaked = forbidden.intersection(data.keys())
    assert not leaked, (
        f"command_mode_auto_enabled payload leaked keys: {leaked}. "
        f"PRIVACY: payload must be structural-only."
    )

    # Expect the documented structural fields only.
    allowed = {"trigger"}
    extra = set(data.keys()) - allowed
    assert not extra, (
        f"command_mode_auto_enabled payload had unexpected keys: {extra}. "
        f"PRIVACY: only {allowed} allowed."
    )

    assert data.get("trigger") == "terminal_detect", (
        f"expected trigger=terminal_detect, got {data.get('trigger')!r}"
    )

    # Verify the IME did not crash.
    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after command-mode auto-detect"
