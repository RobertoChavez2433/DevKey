"""
Voice stress tests.

Exercises the voice pipeline under repeated and concurrent activation
patterns. Many voice tests require audio injection which is not available
on emulator — tests skip gracefully when voice hardware is unavailable.
"""
import os
import time

from lib import adb, keyboard, driver
from tests.voice_common import process_voice_fixture


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


def _enter_voice_mode(serial):
    """Enter voice mode and wait for LISTENING state."""
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


def _exit_voice_mode():
    """Reset keyboard mode and wait for IDLE."""
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for(
        "DevKey/VOX", "state_transition",
        match={"state": "IDLE"},
        timeout_ms=3000,
    )


def test_start_stop_5x():
    """Start/stop voice 5 times rapidly — state machine must survive."""
    serial = _setup_voice()

    for i in range(5):
        _enter_voice_mode(serial)
        _exit_voice_mode()

    # Final state must be IDLE
    driver.clear_logs()
    # Verify driver is still responsive
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})


def test_cancel_during_processing():
    """
    Activate voice, then cancel while audio would be processing.

    Without audio injection this effectively tests cancel-from-LISTENING,
    which is still a valid stress path.
    """
    serial = _setup_voice()
    _enter_voice_mode(serial)

    # Immediately cancel — the engine may be in LISTENING or transitioning
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for(
        "DevKey/VOX", "state_transition",
        match={"state": "IDLE"},
        timeout_ms=5000,
    )


def test_file_based_back_to_back():
    """
    Push a WAV file and run file-based inference twice back-to-back.

    Skips if the voice fixture is not available.
    """
    serial = _setup_voice()

    for _ in range(2):
        process_voice_fixture(serial, "voice-complex.wav")


def test_audio_inject_rapid_cycle():
    """
    Inject a device-local voice fixture 3 times with voice mode active.
    """
    serial = _setup_voice()

    for _ in range(3):
        _enter_voice_mode(serial)
        process_voice_fixture(
            serial,
            "voice-complex.wav",
            expect_commit=True,
            idle_timeout_ms=90000,
        )


def test_start_stop_interleaved_with_typing():
    """
    Alternate between voice activation and typing — the IME must not
    get stuck in voice mode or lose key dispatch.
    """
    serial = _setup_voice()
    SPACE_CODE = 32

    for _ in range(3):
        # Voice cycle
        _enter_voice_mode(serial)
        _exit_voice_mode()

        # Typing cycle — verify keys still work
        driver.clear_logs()
        keyboard.tap_key("a", serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

    state = adb.query_test_host_state(serial, timeout_ms=2000)
    assert state["text_length"] >= 6, (
        f"Typing after voice start/stop cycles did not commit expected text; length={state['text_length']}"
    )
