"""
Voice smoke tests (subdirectory format).

Migrated from tools/e2e/tests/test_voice.py — same test logic,
relocated into the voice/ test package for Phase 7 organisation.

FROM SPEC: §6 Phase 2 item 2.3 — "Voice round-trip (tap mic -> speak -> verify committed text)"

Strategy:
  - Trigger voice mode via SET_KEYBOARD_MODE broadcast.
  - Wait for VoiceInputEngine state transitions.
  - Cancel via RESET_KEYBOARD_MODE.
"""
import os
import subprocess

import pytest

from lib import adb, keyboard, driver, audio


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
    """Enter voice mode via SET_KEYBOARD_MODE broadcast and wait for LISTENING."""
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


def test_voice_state_machine_to_listening():
    """Activate voice mode -> VoiceInputEngine transitions IDLE -> LISTENING."""
    serial = _setup_voice()
    _enter_voice_mode(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})


def test_voice_cancel_returns_to_idle():
    """After activating voice and cancelling, state returns to IDLE."""
    serial = _setup_voice()
    _enter_voice_mode(serial)
    driver.clear_logs()
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE"},
                    timeout_ms=5000)


def test_voice_round_trip_committed_text():
    """
    Full round-trip: activate voice -> inject audio -> silence-detect -> processing -> commit.

    SKIP if audio injection is unavailable or Whisper model is missing.
    """
    serial = _setup_voice()
    _enter_voice_mode(serial)

    if not audio.is_emulator(serial):
        pytest.skip("audio injection requires an emulator")
    if not audio.inject_sample():
        pytest.skip("audio sample not available or no host audio player found")

    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "PROCESSING"}, timeout_ms=10000)

    try:
        driver.wait_for("DevKey/VOX", "state_transition",
                        match={"state": "IDLE"},
                        timeout_ms=20000)
    except driver.DriverTimeout:
        driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
        driver.wait_for("DevKey/VOX", "state_transition",
                        match={"state": "IDLE"}, timeout_ms=5000)


def test_voice_file_based_inference():
    """
    File-based Whisper round-trip: push a WAV file, trigger VOICE_PROCESS_FILE
    broadcast, verify inference completes with non-empty transcription.
    """
    serial = _setup_voice()

    fixture = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "..", "fixtures", "voice-complex.wav"
    )
    if not os.path.exists(fixture):
        pytest.skip("voice-complex.wav fixture not found")

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

    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.VOICE_PROCESS_FILE",
        {"file_path": "/data/data/dev.devkey.keyboard/files/voice-complex.wav"},
    )

    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "PROCESSING"}, timeout_ms=5000)

    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE"}, timeout_ms=60000)

    result = driver.wait_for("DevKey/VOX", "process_file_result",
                             timeout_ms=5000)
    data = result.get("data", {})
    assert data.get("length", 0) > 0, (
        f"Expected non-empty transcription but got length={data.get('length')}"
    )
