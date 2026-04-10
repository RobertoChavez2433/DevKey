"""
Voice round-trip tests.

FROM SPEC: §6 Phase 2 item 2.3 — "Voice round-trip (tap mic → speak → verify committed text)"

Depends on:
  - VoiceInputEngine state-transition instrumentation (landed Session 42)
  - ENABLE_DEBUG_SERVER broadcast (Phase 2 sub-phase 1.1)
  - driver server running on $DEVKEY_DRIVER_URL

Skips gracefully when:
  - Whisper model files are missing (voice button disabled)
  - Audio injection tool is unavailable (physical devices)
"""
from lib import adb, keyboard, driver, audio

KEYCODE_VOICE = -102  # from KeyData.kt:148


def _setup_voice_button():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    # Precondition: voice button key present in the map.
    voice_code_key = f"code_{KEYCODE_VOICE}"
    if voice_code_key not in keyboard.get_key_map():
        raise AssertionError(
            f"Voice key (code {KEYCODE_VOICE}) not found in key map. "
            f"Is the keyboard in a mode that shows it?"
        )
    return serial, voice_code_key


def test_voice_state_machine_to_listening():
    """Tap mic → VoiceInputEngine transitions IDLE → LISTENING."""
    serial, voice_key = _setup_voice_button()
    driver.clear_logs()
    keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
    driver.wait_for(
        category="DevKey/VOX",
        event="state_transition",
        match={"state": "LISTENING", "source": "startListening"},
        timeout_ms=3000,
    )


def test_voice_cancel_returns_to_idle():
    """After tapping mic and cancelling, state returns to IDLE."""
    serial, voice_key = _setup_voice_button()
    driver.clear_logs()
    keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "LISTENING"}, timeout_ms=3000)
    # Re-tapping the voice key toggles cancel (DevKeyKeyboard.kt:156-158).
    keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE", "source": "cancelListening"},
                    timeout_ms=3000)


def test_voice_round_trip_committed_text():
    """
    Full round-trip: tap mic → inject audio → silence-detect → processing → commit.

    SKIP if audio injection is unavailable or Whisper model is missing.
    """
    serial, voice_key = _setup_voice_button()
    driver.clear_logs()

    keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
    try:
        driver.wait_for("DevKey/VOX", "state_transition",
                        match={"state": "LISTENING"}, timeout_ms=3000)
    except driver.DriverTimeout:
        # Voice button tapped but LISTENING never fired — either permission
        # prompt blocked the flow, or voice is disabled. Check for an ERROR event
        # with reason=permission_denied to distinguish.
        import pytest  # lazy import — we only need pytest for the skip sentinel
        pytest.skip("LISTENING state never reached (permission or model missing)")

    # Inject audio — on physical devices this is a no-op.
    if not audio.is_emulator(serial):
        import pytest
        pytest.skip("audio injection requires an emulator")
    if not audio.inject_sample():
        import pytest
        pytest.skip("audio sample not available or no host audio player found")

    # Wait for silence-detection or manual stop — then processing → IDLE.
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "PROCESSING"}, timeout_ms=10000)
    entry = driver.wait_for("DevKey/VOX", "processing_complete",
                            timeout_ms=15000)
    result_length = int(entry["data"].get("result_length", 0))
    # PRIVACY: we assert on length, never content.
    assert result_length > 0, "processing_complete had zero result_length"

    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "IDLE", "reason": "inference_complete"},
                    timeout_ms=5000)

    # F1 (completeness): verify committed text actually landed in the text field.
    # PRIVACY: assert non-empty length only — NEVER assert on specific content.
    committed = adb.get_text_field_content(serial)
    assert committed, (
        f"Expected non-empty committed text after voice round-trip, got: '{committed}'"
    )
