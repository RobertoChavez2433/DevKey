"""Privacy-safe exact text assertions for the controlled E2E host."""
import base64
import subprocess
from typing import Any, Dict, Optional

from . import verify
from .adb_device import _adb_cmd
from .adb_test_host import _coerce_bool, _new_request_id, _wait_for_test_host_event

DEBUG_ASSERT_EDIT_TEXT_ACTION = "dev.devkey.keyboard.debug.ASSERT_EDIT_TEXT"


def assert_test_host_text_equals(
    expected: str,
    serial: Optional[str] = None,
    context: str = "test_host",
    timeout_ms: int = 1000,
) -> Dict[str, Any]:
    """
    Ask the debug TestHostActivity to compare its EditText content locally.

    The expected value is a controlled E2E fixture, but it still must not be
    recorded in result JSON, debug logs, or assertion messages. The app logs
    only match status, focus, and lengths.
    """
    request_id = _new_request_id("assert")
    verify.record_action(
        "adb.assert_test_host_text_equals",
        {
            "context": context,
            "expected_length": len(expected),
            "comparison": "exact_text_equality",
        },
    )
    state = _request_test_host_text_assertion(expected, request_id, serial, timeout_ms)
    ok = _coerce_bool(state.get("ok"))
    actual_length = int(state.get("actual_length", state.get("text_length", -1)))
    expected_length = int(state.get("expected_length", len(expected)))
    if state.get("focused") is False:
        raise AssertionError(f"{context}: TestHostActivity EditText is not focused")
    if ok is not True:
        raise AssertionError(
            f"{context}: controlled TestHost text mismatch; "
            f"expected_length={expected_length}, actual_length={actual_length}"
            f"{_format_mismatch_detail(state)}"
        )
    verify.record_evidence(
        "adb.assert_test_host_text_equals",
        {
            "context": context,
            "comparison": "exact_text_equality",
            "exact_match": True,
            "expected_length": expected_length,
            "actual_length": actual_length,
            "focused": state.get("focused"),
        },
    )
    _assert_same_length_mismatch_is_rejected(
        expected=expected,
        serial=serial,
        context=context,
        timeout_ms=timeout_ms,
    )
    return {
        "ok": True,
        "expected_length": expected_length,
        "actual_length": actual_length,
        "focused": state.get("focused"),
    }


def _format_mismatch_detail(state: Dict[str, Any]) -> str:
    first_diff = state.get("first_diff_index")
    expected_kind = state.get("expected_char_kind")
    actual_kind = state.get("actual_char_kind")
    if first_diff is None and expected_kind is None and actual_kind is None:
        return ""
    return (
        f", first_diff_index={first_diff}, "
        f"expected_char_kind={expected_kind}, actual_char_kind={actual_kind}"
    )


def _assert_same_length_mismatch_is_rejected(
    expected: str,
    serial: Optional[str],
    context: str,
    timeout_ms: int,
) -> None:
    if not expected:
        return
    mismatch = _same_length_mismatch(expected)
    request_id = _new_request_id("reject")
    verify.record_action(
        "adb.assert_test_host_text_rejects_mismatch",
        {
            "context": context,
            "expected_length": len(expected),
            "comparison": "same_length_negative_control",
        },
    )
    state = _request_test_host_text_assertion(mismatch, request_id, serial, timeout_ms)
    ok = _coerce_bool(state.get("ok"))
    actual_length = int(state.get("actual_length", state.get("text_length", -1)))
    mismatch_length = int(state.get("expected_length", len(mismatch)))
    if ok is True:
        raise AssertionError(
            f"{context}: TestHost exact-text assertion accepted a same-length mismatch; "
            f"expected_length={mismatch_length}, actual_length={actual_length}"
        )
    if mismatch_length != len(expected):
        raise AssertionError(
            f"{context}: TestHost mismatch control length drifted; "
            f"expected_length={len(expected)}, control_length={mismatch_length}"
        )
    verify.record_evidence(
        "adb.assert_test_host_text_rejects_mismatch",
        {
            "context": context,
            "comparison": "same_length_negative_control",
            "same_length_rejected": True,
            "expected_length": mismatch_length,
            "actual_length": actual_length,
            "focused": state.get("focused"),
        },
    )


def _request_test_host_text_assertion(
    expected: str,
    request_id: str,
    serial: Optional[str],
    timeout_ms: int,
) -> Dict[str, Any]:
    encoded = base64.b64encode(expected.encode("utf-8")).decode("ascii")
    subprocess.run(
        _adb_cmd(
            [
                "shell", "am", "broadcast",
                "-a", DEBUG_ASSERT_EDIT_TEXT_ACTION,
                "--es", "request_id", request_id,
                "--es", "expected_text_base64", encoded,
            ],
            serial,
        ),
        capture_output=True,
    )
    return _wait_for_test_host_event(
        "test_host_text_asserted",
        request_id,
        serial,
        timeout_ms=timeout_ms,
    )


def _same_length_mismatch(value: str) -> str:
    return "".join("~" if char != "~" else "!" for char in value)
