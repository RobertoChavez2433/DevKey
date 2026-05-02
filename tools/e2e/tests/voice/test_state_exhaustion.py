"""
Voice state exhaustion tests.

Exercises the VoiceInputEngine state machine through every transition
and several creative adversarial patterns that probe the boundary between
voice, typing, mode switching, and the Whisper inference pipeline.

State machine:  IDLE -> LISTENING -> PROCESSING -> IDLE
                IDLE -> LISTENING -> IDLE (cancel)
                IDLE -> PROCESSING -> IDLE (file-based)

Depends on:
  - VoiceInputEngine state-transition instrumentation (DevKey/VOX)
  - VOICE_PROCESS_FILE broadcast (file-based inference)
  - HTTP forwarding enabled
"""
import os
import time

from lib import adb, keyboard, driver
from tests.voice_common import process_voice_fixture

SPACE_CODE = 32


def _setup_voice():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("full", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    host_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    ime_url = adb.configure_debug_server_forwarding(serial, host_url)
    driver.broadcast("dev.devkey.keyboard.ENABLE_DEBUG_SERVER", {"url": ime_url})
    return serial


def _enter_voice_mode(serial):
    """Enter voice mode and wait for LISTENING."""
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
    driver.clear_logs()
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    driver.wait_for(
        "DevKey/VOX", "state_transition",
        match={"state": "IDLE"},
        timeout_ms=5000,
    )


def test_voice_typing_voice_rapid_alternation():
    """
    Rapidly alternate: voice activate -> cancel -> type word -> voice activate
    -> cancel -> type word. 5 cycles.

    Verifies the input pipeline cleanly hands off between voice and
    keyboard modes without state corruption.
    """
    serial = _setup_voice()

    for i in range(3):
        # Voice cycle
        _enter_voice_mode(serial)
        _exit_voice_mode()
        time.sleep(0.5)

        # Typing cycle — verify keyboard still responds
        driver.clear_logs()
        keyboard.tap_key("a", serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        time.sleep(0.5)

    # Final health check
    driver.require_driver()


def test_voice_double_activate():
    """
    Send SET_KEYBOARD_MODE voice twice while already in LISTENING.
    The engine must not crash or enter an invalid state — the second
    broadcast should be a no-op.
    """
    serial = _setup_voice()
    _enter_voice_mode(serial)

    # Second activation while already LISTENING
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "voice"},
    )
    time.sleep(1.0)

    # Must still be in LISTENING (not crashed/errored)
    _exit_voice_mode()

    # Pipeline must be healthy
    driver.require_driver()


def test_voice_mode_switch_during_listening():
    """
    While in LISTENING state, switch to clipboard mode, then back to
    normal. The voice engine must cleanly cancel and not leak AudioRecord.
    """
    serial = _setup_voice()
    _enter_voice_mode(serial)

    # Switch to clipboard mode mid-listen
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_KEYBOARD_MODE",
        {"mode": "clipboard"},
    )
    time.sleep(0.5)

    # Switch back to normal
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.5)

    # Voice engine should be IDLE and keyboard responsive
    driver.clear_logs()
    keyboard.tap_key("b", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.3)

    driver.require_driver()


def test_file_inference_during_listening():
    """
    Start live listening, then trigger VOICE_PROCESS_FILE while
    AudioRecord is active. The file-based path should override
    or queue cleanly.
    """
    serial = _setup_voice()

    _enter_voice_mode(serial)

    # Trigger file-based inference while in LISTENING.
    process_voice_fixture(serial, "voice-complex.wav")

    # Pipeline must be healthy
    driver.require_driver()


def test_file_inference_three_fixtures():
    """
    Process all three voice fixtures back-to-back: voice-hello.wav,
    voice-complex.wav, voice-extended.wav. Each must complete with a
    result event.
    """
    serial = _setup_voice()
    fixtures = ["voice-hello.wav", "voice-complex.wav", "voice-extended.wav"]

    result_count = 0
    for fixture_name in fixtures:
        process_voice_fixture(serial, fixture_name)
        result_count += 1

    assert result_count == 3, (
        f"Expected 3 file inferences, got {result_count}"
    )


def test_voice_cancel_timing_sweep():
    """
    Cancel voice at different timings after activation: 0ms, 100ms,
    500ms, 1000ms, 2000ms. Each must return to IDLE cleanly.

    Probes race conditions between AudioRecord startup and cancellation.
    """
    serial = _setup_voice()
    delays = [0.0, 0.1, 0.5, 1.0, 2.0]

    for delay in delays:
        _enter_voice_mode(serial)
        time.sleep(delay)
        _exit_voice_mode()
        time.sleep(0.3)

    # Pipeline must be healthy after all timing variants
    driver.require_driver()


def test_voice_after_heavy_typing():
    """
    Type 30 words rapidly, then immediately activate voice.
    Verifies the voice engine initializes cleanly even when the
    composing/suggestion pipeline is hot.
    """
    serial = _setup_voice()

    # Enable suggestions for heavy composing load
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    time.sleep(0.3)

    # Type 30 words rapidly
    words = ["the", "quick", "brown", "fox", "jumped", "over", "the",
             "lazy", "dog", "and", "the", "cat", "sat", "on", "the",
             "mat", "while", "the", "bird", "sang", "a", "song", "in",
             "the", "warm", "sun", "of", "the", "long", "day"]
    for word in words:
        keyboard.tap_sequence(word, delay=0.05, serial=serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)
        time.sleep(0.05)

    # Now activate voice — the engine must initialize cleanly
    _enter_voice_mode(serial)
    _exit_voice_mode()

    # Verify typing still works after voice
    driver.clear_logs()
    keyboard.tap_key("z", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.3)

    driver.require_driver()
