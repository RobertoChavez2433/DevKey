"""Privacy-safe learned-word helpers for DevKey E2E tests."""
import base64
import subprocess
from typing import Any, Dict, Optional

from . import driver, verify
from .adb_device import _adb_cmd
from .adb_test_host import _coerce_bool, _new_request_id

DEBUG_CLEAR_LEARNED_WORDS_ACTION = "dev.devkey.keyboard.CLEAR_LEARNED_WORDS"
DEBUG_ASSERT_LEARNED_WORD_ACTION = "dev.devkey.keyboard.debug.ASSERT_LEARNED_WORD"


def clear_learned_words(
    serial: Optional[str] = None,
    timeout_ms: int = 3000,
) -> Dict[str, Any]:
    """Clear learned words through the debug receiver and wait for ack."""
    request_id = _new_request_id("learned-clear")
    verify.record_action("adb.clear_learned_words", requires_verification=False)
    subprocess.run(
        _adb_cmd(
            [
                "shell", "am", "broadcast",
                "-a", DEBUG_CLEAR_LEARNED_WORDS_ACTION,
                "--es", "request_id", request_id,
            ],
            serial,
        ),
        capture_output=True,
    )
    entry = driver.wait_for(
        "DevKey/IME",
        "learned_words_cleared",
        match={"request_id": request_id},
        timeout_ms=timeout_ms,
    )
    verify.record_evidence("adb.clear_learned_words", {"cleared": True})
    return dict(entry.get("data", {}))


def assert_learned_word(
    word: str,
    min_frequency: int = 1,
    serial: Optional[str] = None,
    context: str = "learning",
    timeout_ms: int = 3000,
) -> Dict[str, Any]:
    """
    Assert a fixture word exists in the learned-word store without logging it.

    Only structural metadata is sent to result JSON and app logs: word length,
    frequency, and flags.
    """
    request_id = _new_request_id("learned-assert")
    verify.record_action(
        "adb.assert_learned_word",
        {
            "context": context,
            "word_length": len(word),
            "min_frequency": min_frequency,
        },
    )
    encoded = base64.b64encode(word.encode("utf-8")).decode("ascii")
    subprocess.run(
        _adb_cmd(
            [
                "shell", "am", "broadcast",
                "-a", DEBUG_ASSERT_LEARNED_WORD_ACTION,
                "--es", "request_id", request_id,
                "--es", "word_base64", encoded,
                "--ei", "min_frequency", str(min_frequency),
            ],
            serial,
        ),
        capture_output=True,
    )
    entry = driver.wait_for(
        "DevKey/IME",
        "learned_word_asserted",
        match={"request_id": request_id},
        timeout_ms=timeout_ms,
    )
    data = _normalize_assertion(dict(entry.get("data", {})))
    if data.get("ok") is not True:
        raise AssertionError(
            f"{context}: learned word assertion failed; "
            f"word_length={len(word)}, frequency={data.get('frequency', 0)}, "
            f"min_frequency={min_frequency}"
        )
    verify.record_evidence(
        "adb.assert_learned_word",
        {
            "context": context,
            "word_length": int(data.get("word_length", len(word))),
            "frequency": int(data.get("frequency", 0)),
            "min_frequency": int(data.get("min_frequency", min_frequency)),
            "is_command": data.get("is_command"),
            "is_user_added": data.get("is_user_added"),
        },
    )
    return data


def _normalize_assertion(data: Dict[str, Any]) -> Dict[str, Any]:
    for key in ("word_length", "frequency", "min_frequency"):
        if key in data:
            data[key] = int(data[key])
    for key in ("ok", "is_command", "is_user_added"):
        if key in data:
            data[key] = _coerce_bool(data[key])
    return data
