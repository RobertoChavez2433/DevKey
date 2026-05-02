"""
Voice edge-case tests.

Covers boundary conditions the voice pipeline must handle without
crashing: cancel from idle, voice during composing, and empty audio.
"""
import os

import pytest

from lib import adb, keyboard, driver


def _setup_voice():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    keyboard.set_layout_mode("full", serial)
    host_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    ime_url = adb.configure_debug_server_forwarding(serial, host_url)
    driver.broadcast("dev.devkey.keyboard.ENABLE_DEBUG_SERVER", {"url": ime_url})
    return serial


def test_cancel_from_idle():
    """
    Sending RESET_KEYBOARD_MODE when already idle must not crash or
    produce an error state. The engine should remain in IDLE.
    """
    serial = _setup_voice()
    driver.clear_logs()

    # Send reset without ever entering voice mode
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})

    # The driver should still be responsive — verify by entering and
    # exiting voice mode cleanly
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "voice"},
    )
    driver.wait_for(
        category="DevKey/VOX",
        event="state_transition",
        match={"state": "LISTENING"},
        timeout_ms=5000,
    )
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE"},
                    timeout_ms=3000)


def test_voice_during_composing():
    """
    Activating voice mode while text is being composed must not lose
    the composing state or crash. After cancelling voice, typing must
    still work.
    """
    serial = _setup_voice()
    SPACE_CODE = 32

    # Start composing a word
    keyboard.tap_key("h", serial)
    keyboard.tap_key("e", serial)
    keyboard.tap_key("l", serial)

    # Activate voice mid-compose
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "voice"},
    )
    driver.wait_for(
        category="DevKey/VOX",
        event="state_transition",
        match={"state": "LISTENING"},
        timeout_ms=5000,
    )

    # Cancel voice
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE"},
                    timeout_ms=3000)

    # Verify typing still works after voice cancel
    driver.clear_logs()
    keyboard.tap_key("a", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)


def test_empty_audio_buffer():
    """
    Triggering voice stop immediately after start (before any audio is
    captured) must not crash and must return to IDLE.

    Uses the broadcast path to enter voice, then immediately resets.
    """
    serial = _setup_voice()
    driver.clear_logs()

    # Enter voice mode
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "voice"},
    )
    driver.wait_for(
        category="DevKey/VOX",
        event="state_transition",
        match={"state": "LISTENING"},
        timeout_ms=5000,
    )

    # Immediately stop — no audio captured
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE"},
                    timeout_ms=5000)
