"""
Voice round-trip tests.

FROM SPEC: §6 Phase 2 item 2.3 — "Voice round-trip (tap mic → speak → verify committed text)"

Strategy:
  - Trigger voice mode via SET_KEYBOARD_MODE broadcast (mic key removed from
    space row per SwiftKey parity — voice accessible via toolbar only).
  - Wait for VoiceInputEngine state transitions.
  - Cancel via RESET_KEYBOARD_MODE.

Depends on:
  - VoiceInputEngine state-transition instrumentation (landed Session 42)
  - ENABLE_DEBUG_SERVER broadcast (Phase 2 sub-phase 1.1)
  - driver server running on $DEVKEY_DRIVER_URL
"""
from lib import adb, keyboard, driver
from tests.voice_common import process_voice_fixture


def _setup_voice():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    # Ensure FULL mode so we have maximum key surface and predictable state.
    keyboard.set_layout_mode("full", serial)
    # Re-ensure debug server connection — earlier tests may have caused
    # an IME restart that dropped the URL.
    import os
    host_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    ime_url = adb.configure_debug_server_forwarding(serial, host_url)
    driver.broadcast("dev.devkey.keyboard.ENABLE_DEBUG_SERVER", {"url": ime_url})
    return serial


def _enter_voice_mode(serial):
    """Enter voice mode via SET_KEYBOARD_MODE broadcast and wait for LISTENING."""
    driver.clear_logs()
    # Mic is a long-press on the comma key, not a standalone button.
    # Use the broadcast path for reliable activation.
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
    """Activate voice mode → VoiceInputEngine transitions IDLE → LISTENING."""
    serial = _setup_voice()
    _enter_voice_mode(serial)
    # If we got here, LISTENING was reached. Clean up.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})


def test_voice_cancel_returns_to_idle():
    """After activating voice and cancelling, state returns to IDLE."""
    serial = _setup_voice()
    _enter_voice_mode(serial)
    # Reset to Normal cancels voice → IDLE.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE"},
                    timeout_ms=3000)


def test_voice_round_trip_committed_text():
    """
    S21 round-trip: activate capture, inject fixture through Whisper, commit text.
    """
    serial = _setup_voice()
    _enter_voice_mode(serial)

    process_voice_fixture(
        serial,
        "voice-hello.wav",
        expect_commit=True,
        clear_logs=True,
    )


def test_voice_file_based_inference():
    """
    File-based Whisper round-trip: push a WAV file, trigger VOICE_PROCESS_FILE
    broadcast, verify inference completes with non-empty transcription.

    Uses the debug-only VOICE_PROCESS_FILE broadcast to bypass AudioRecord
    entirely — deterministic and emulator-friendly.
    """
    serial = _setup_voice()

    process_voice_fixture(serial, "voice-complex.wav")
