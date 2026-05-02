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
    clear_logs=True,
    processing_timeout_ms=10000,
    idle_timeout_ms=60000,
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
        timeout_ms=5000,
    )
    data = result.get("data", {})
    length = data.get("length", 0)
    assert length > 0, f"Expected non-empty transcription length, got {length}"
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
