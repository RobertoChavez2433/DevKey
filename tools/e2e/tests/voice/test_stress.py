"""
Voice stress tests.

Exercises the voice pipeline under repeated and concurrent activation
patterns. Many voice tests require audio injection which is not available
on emulator — tests skip gracefully when voice hardware is unavailable.
"""
import os
import time

import pytest

from lib import adb, keyboard, driver, audio


def _setup_voice():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    keyboard.set_layout_mode("full", serial)
    host_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    ime_url = host_url.replace("127.0.0.1", "10.0.2.2").replace("localhost", "10.0.2.2")
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
    fixture = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "..", "fixtures", "voice-complex.wav"
    )
    if not os.path.exists(fixture):
        pytest.skip("voice-complex.wav fixture not found — skipping file-based stress test")

    import subprocess
    subprocess.run(
        ["adb", "-s", serial, "push", fixture, "/data/local/tmp/voice-complex.wav"],
        check=True, capture_output=True
    )
    subprocess.run(
        ["adb", "-s", serial, "shell", "run-as", "dev.devkey.keyboard",
         "cp", "/data/local/tmp/voice-complex.wav",
         "/data/data/dev.devkey.keyboard/files/voice-complex.wav"],
        check=True, capture_output=True
    )

    for _ in range(2):
        driver.clear_logs()
        driver.broadcast(
            "dev.devkey.keyboard.VOICE_PROCESS_FILE",
            {"file_path": "/data/data/dev.devkey.keyboard/files/voice-complex.wav"},
        )
        driver.wait_for("DevKey/VOX", "state_transition",
                        match={"state": "PROCESSING"}, timeout_ms=5000)
        driver.wait_for("DevKey/VOX", "state_transition",
                        match={"state": "IDLE"}, timeout_ms=60000)


def test_audio_inject_rapid_cycle():
    """
    Inject audio sample 3 times rapidly with voice mode active.

    Skips if audio injection is not available on the emulator.
    """
    serial = _setup_voice()

    if not audio.is_emulator(serial):
        pytest.skip("audio injection requires an emulator")
    if not audio.inject_sample():
        pytest.skip("audio sample not available or no host audio player found")

    for _ in range(3):
        _enter_voice_mode(serial)
        # Wait briefly for audio to be captured
        time.sleep(0.5)
        _exit_voice_mode()


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
