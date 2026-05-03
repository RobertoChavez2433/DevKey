"""
Shared helpers for S21 voice validation.

The helpers use structural state only: fixture names, result lengths, state
transitions, and TestHost text length. They never read or log transcripts.
"""

import os
import subprocess

import pytest

from lib import adb, driver


FIXTURE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..",
    "fixtures",
)
STOP_TO_COMMITTED_TARGET_MS = 2500
OFFLINE_DELAYED_POSTURE = "offline_delayed"


def push_voice_fixture(serial, fixture_name):
    fixture = os.path.join(FIXTURE_DIR, fixture_name)
    if not os.path.exists(fixture):
        pytest.skip(f"{fixture_name} fixture not found")

    subprocess.run(
        ["adb", "-s", serial, "push", fixture, f"/data/local/tmp/{fixture_name}"],
        check=True,
        capture_output=True,
    )
    subprocess.run(
        [
            "adb", "-s", serial, "shell", "run-as", "dev.devkey.keyboard",
            "cp", f"/data/local/tmp/{fixture_name}",
            f"/data/data/dev.devkey.keyboard/files/{fixture_name}",
        ],
        check=True,
        capture_output=True,
    )
    return f"/data/data/dev.devkey.keyboard/files/{fixture_name}"


def process_voice_fixture(
    serial,
    fixture_name="voice-complex.wav",
    expect_commit=False,
    expect_speech=True,
    clear_logs=True,
    processing_timeout_ms=10000,
    idle_timeout_ms=60000,
    result_timeout_ms=15000,
):
    file_path = push_voice_fixture(serial, fixture_name)
    before_length = None
    if expect_commit:
        before_length = adb.query_test_host_state(serial, timeout_ms=2000).get("text_length", 0)

    if clear_logs:
        driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.VOICE_PROCESS_FILE",
        {"file_path": file_path},
    )
    driver.wait_for(
        "DevKey/VOX",
        "state_transition",
        match={"state": "PROCESSING"},
        timeout_ms=processing_timeout_ms,
    )
    driver.wait_for(
        "DevKey/VOX",
        "state_transition",
        match={"state": "IDLE"},
        timeout_ms=idle_timeout_ms,
    )
    result = driver.wait_for(
        "DevKey/VOX",
        "process_file_result",
        timeout_ms=result_timeout_ms,
    )
    latency = driver.wait_for(
        "DevKey/VOX",
        "latency",
        match={"phase": "process_file_to_committed"},
        timeout_ms=3000,
    )
    data = result.get("data", {})
    latency_data = latency.get("data", {})
    length = data.get("length", 0)
    assert length > 0, f"Expected non-empty transcription length, got {length}"
    duration_ms = int(latency_data.get("duration_ms", -1))
    target_ms = int(latency_data.get("target_ms", -1))
    assert duration_ms >= 0, "Missing process_file_to_committed duration"
    assert target_ms == STOP_TO_COMMITTED_TARGET_MS, (
        f"Expected voice target {STOP_TO_COMMITTED_TARGET_MS}ms, got {target_ms}"
    )
    if duration_ms <= target_ms:
        assert latency_data.get("release_quality") is True
    else:
        assert latency_data.get("release_quality") is False
        assert latency_data.get("release_posture") == OFFLINE_DELAYED_POSTURE
        assert latency_data.get("runtime_next_step") == "evaluate_streaming_or_smaller_local_model"
    if expect_speech:
        assert data.get("committed") is True, (
            "Expected voice fixture to produce committable speech, but committed=false"
        )
    else:
        assert data.get("committed") is False, (
            "Expected non-speech fixture to be rejected, but committed=true"
        )
    if expect_commit:
        assert data.get("committed") is True, (
            "Expected voice result to be accepted for commit, but committed=false"
        )

    if expect_commit:
        after_length = adb.query_test_host_state(serial, timeout_ms=3000).get("text_length", 0)
        assert after_length > before_length, (
            "Expected voice result to be committed through the IME text path; "
            f"length before={before_length}, after={after_length}"
        )
    return result
