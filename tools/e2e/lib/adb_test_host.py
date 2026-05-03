"""Controlled TestHostActivity state helpers for DevKey E2E tests."""
import re
import subprocess
import time
from typing import Any, Dict, Optional

from . import verify
from .adb_device import (
    _adb_cmd,
    configure_debug_server_forwarding,
    is_keyboard_visible,
)
from .adb_logcat import capture_logcat

TEST_HOST_ACTIVITY = (
    "dev.devkey.keyboard/dev.devkey.keyboard.debug.TestHostActivity"
)
DEBUG_CLEAR_EDIT_TEXT_ACTION = "dev.devkey.keyboard.debug.CLEAR_EDIT_TEXT"
DEBUG_DUMP_TEST_HOST_STATE_ACTION = "dev.devkey.keyboard.debug.DUMP_TEST_HOST_STATE"
RESET_KEYBOARD_MODE_ACTION = "dev.devkey.keyboard.RESET_KEYBOARD_MODE"


def get_text_field_content(serial: Optional[str] = None) -> str:
    """
    Read the text content of the currently focused text field.

    Uses dumpsys input_method to get the current text from the IME.
    Returns the extracted text or empty string if not found.
    """
    text = _read_focused_text(serial)
    if text is not None:
        verify.record_evidence("adb.text_field_content", {"length": len(text)})
        return text
    return ""


def get_text_field_length(serial: Optional[str] = None, strict: bool = False) -> int:
    """
    Return the focused text field length without exposing the text itself.

    The release harness may validate state after typing synthetic strings, but
    result JSON and assertion messages must stay privacy-safe. This helper
    returns and records only a length.
    """
    text = _read_focused_text(serial)
    if text is None:
        if strict:
            raise AssertionError("Focused text field length is unavailable")
        return 0
    length = len(text)
    verify.record_evidence("adb.text_field_length", {"length": length})
    return length


def assert_text_field_empty(serial: Optional[str] = None, context: str = "test_host") -> int:
    """Fail if the focused EditText contains stale content. Reports length only."""
    try:
        state = query_test_host_state(serial)
    except AssertionError:
        length = get_text_field_length(serial, strict=True)
    else:
        length = int(state.get("text_length", -1))
        focused = state.get("focused")
        if focused is False:
            raise AssertionError(f"{context} EditText is not focused")
    if length != 0:
        raise AssertionError(f"{context} text field is not empty; length={length}")
    verify.record_evidence("adb.text_field_empty", {"context": context, "length": 0})
    return length


def reset_keyboard_mode(serial: Optional[str] = None) -> None:
    """Return the debug IME to its normal keyboard mode."""
    verify.record_action("adb.reset_keyboard_mode", requires_verification=False)
    subprocess.run(
        _adb_cmd(["shell", "am", "broadcast", "-a", RESET_KEYBOARD_MODE_ACTION], serial),
        capture_output=True,
    )
    time.sleep(0.2)


def clear_test_host_text(serial: Optional[str] = None, timeout: float = 2.0) -> Dict[str, Any]:
    """
    Clear the debug TestHostActivity EditText and prove it is empty.

    This intentionally records only text length, never content.
    """
    verify.record_action("adb.clear_test_host_text", requires_verification=False)
    deadline = time.time() + timeout
    last_state: Optional[Dict[str, Any]] = None
    while time.time() < deadline:
        request_id = _new_request_id("clear")
        _broadcast_with_request_id(DEBUG_CLEAR_EDIT_TEXT_ACTION, request_id, serial)
        remaining_ms = max(250, int((deadline - time.time()) * 1000))
        try:
            state = _wait_for_test_host_event(
                "test_host_text_cleared",
                request_id,
                serial,
                timeout_ms=min(remaining_ms, 1000),
            )
            last_state = state
        except AssertionError:
            state = None
        if state and state.get("text_length") == 0 and state.get("focused") is not False:
            verify.record_evidence(
                "adb.clear_test_host_text",
                {"length": 0, "focused": state.get("focused")},
            )
            return {
                "ok": True,
                "length": 0,
                "focused": state.get("focused"),
            }
    length_part = "unknown"
    if last_state is not None:
        length_part = str(last_state.get("text_length", "unknown"))
    raise AssertionError(f"TestHostActivity EditText did not clear; length={length_part}")


def query_test_host_state(serial: Optional[str] = None, timeout_ms: int = 1000) -> Dict[str, Any]:
    """Ask the debug TestHostActivity for structural state only."""
    request_id = _new_request_id("state")
    verify.record_action("adb.query_test_host_state", requires_verification=False)
    _broadcast_with_request_id(DEBUG_DUMP_TEST_HOST_STATE_ACTION, request_id, serial)
    state = _wait_for_test_host_event(
        "test_host_state",
        request_id,
        serial,
        timeout_ms=timeout_ms,
    )
    verify.record_evidence("adb.query_test_host_state", {
        "text_length": state.get("text_length"),
        "focused": state.get("focused"),
    })
    return state


def reset_test_host_state(serial: Optional[str] = None) -> Dict[str, Any]:
    """
    Establish the clean state every E2E test is allowed to inherit.

    This is stricter than merely showing the keyboard: stale host text,
    symbols/voice/macro mode, or a hidden IME means later tests can pass or fail
    for the wrong reason.
    """
    focus_status = ensure_keyboard_visible(serial)
    reset_keyboard_mode(serial)
    clear_status = clear_test_host_text(serial)
    assert_text_field_empty(serial, context="test_host_reset")
    visible = is_keyboard_visible(serial)
    if visible:
        verify.record_evidence("adb.reset_test_host_state", {
            "visible": True,
            "text_length": clear_status["length"],
        })
    return {
        "visible": visible,
        "text_length": clear_status["length"],
        "focus": focus_status,
    }


def ensure_keyboard_visible(serial: Optional[str] = None) -> dict:
    """
    Ensure the soft keyboard is visible and focused on a controlled EditText.

    Idempotent and cheap to call before every test. The underlying `am start`
    is a no-op if the activity is already top.
    """
    grant_result = subprocess.run(
        _adb_cmd(
            ["shell", "pm", "grant", "dev.devkey.keyboard",
             "android.permission.RECORD_AUDIO"],
            serial,
        ),
        capture_output=True,
    )
    subprocess.run(
        _adb_cmd(
            ["shell", "am", "start", "-n", TEST_HOST_ACTIVITY],
            serial,
        ),
        capture_output=True,
    )
    visible = _wait_until_keyboard_visible(serial)

    if visible:
        _enable_debug_server(serial)
        _reset_circuit_breaker(serial)
        verify.record_evidence("adb.ensure_keyboard_visible", {
            "record_audio": _permission_granted("android.permission.RECORD_AUDIO", serial)
                or grant_result.returncode == 0,
        })
    return {
        "visible": visible,
        "permissions": {
            "RECORD_AUDIO": _permission_granted("android.permission.RECORD_AUDIO", serial)
                or grant_result.returncode == 0,
        },
        "key_map_size": 0,
    }


def _read_focused_text(serial: Optional[str] = None) -> Optional[str]:
    cmd = _adb_cmd(["shell", "dumpsys", "input_method"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    match = re.search(r"mText=(.*?)(?:\n|$)", result.stdout)
    if match:
        return match.group(1).rstrip("\r")
    return None


def _new_request_id(prefix: str) -> str:
    return f"{prefix}-{int(time.time() * 1000)}"


def _broadcast_with_request_id(action: str, request_id: str, serial: Optional[str] = None) -> None:
    subprocess.run(
        _adb_cmd(
            [
                "shell", "am", "broadcast",
                "-a", action,
                "--es", "request_id", request_id,
            ],
            serial,
        ),
        capture_output=True,
    )


def _coerce_bool(value: Any) -> Optional[bool]:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        lowered = value.lower()
        if lowered == "true":
            return True
        if lowered == "false":
            return False
    return None


def _parse_test_host_line(line: str, event: str, request_id: str) -> Optional[Dict[str, Any]]:
    if event not in line or f"request_id={request_id}" not in line:
        return None
    length_match = re.search(r"text_length=(\d+)", line)
    expected_length_match = re.search(r"expected_length=(-?\d+)", line)
    actual_length_match = re.search(r"actual_length=(\d+)", line)
    focused_match = re.search(r"focused=(true|false)", line)
    ok_match = re.search(r"ok=(true|false)", line)
    if not length_match:
        return None
    parsed: Dict[str, Any] = {
        "text_length": int(length_match.group(1)),
        "focused": focused_match.group(1) == "true" if focused_match else None,
        "request_id": request_id,
    }
    if expected_length_match:
        parsed["expected_length"] = int(expected_length_match.group(1))
    if actual_length_match:
        parsed["actual_length"] = int(actual_length_match.group(1))
    if ok_match:
        parsed["ok"] = ok_match.group(1) == "true"
    return parsed


def _wait_for_test_host_event(
    event: str,
    request_id: str,
    serial: Optional[str] = None,
    timeout_ms: int = 1000,
) -> Dict[str, Any]:
    try:
        from . import driver

        entry = driver.wait_for(
            "DevKey/IME",
            event,
            match={"request_id": request_id},
            timeout_ms=timeout_ms,
        )
        data = dict(entry.get("data", {}))
        data["text_length"] = int(data.get("text_length", -1))
        data["focused"] = _coerce_bool(data.get("focused"))
        if "actual_length" in data:
            data["actual_length"] = int(data["actual_length"])
        if "expected_length" in data:
            data["expected_length"] = int(data["expected_length"])
        if "first_diff_index" in data:
            data["first_diff_index"] = int(data["first_diff_index"])
        if "ok" in data:
            data["ok"] = _coerce_bool(data["ok"])
        return data
    except Exception:
        pass

    deadline = time.time() + (timeout_ms / 1000.0)
    while time.time() < deadline:
        lines = capture_logcat("DevKey/IME", timeout=0.1, serial=serial)
        for line in lines:
            parsed = _parse_test_host_line(line, event, request_id)
            if parsed is not None:
                verify.record_evidence("adb.test_host_event", {
                    "event": event,
                    "text_length": parsed["text_length"],
                    "focused": parsed["focused"],
                })
                return parsed
    raise AssertionError(f"Timed out waiting for {event} from TestHostActivity")


def _wait_until_keyboard_visible(serial: Optional[str]) -> bool:
    deadline = time.time() + 3.0
    while time.time() < deadline:
        if is_keyboard_visible(serial):
            return True
        time.sleep(0.1)
    return False


def _enable_debug_server(serial: Optional[str]) -> None:
    ime_url = configure_debug_server_forwarding(serial)
    verify.record_action("adb.enable_debug_server", requires_verification=False)
    subprocess.run(
        _adb_cmd(
            [
                "shell", "am", "broadcast",
                "-a", "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
                "--es", "url", ime_url,
            ],
            serial,
        ),
        capture_output=True,
    )


def _reset_circuit_breaker(serial: Optional[str]) -> None:
    subprocess.run(
        _adb_cmd(
            [
                "shell", "am", "broadcast",
                "-a", "dev.devkey.keyboard.RESET_CIRCUIT_BREAKER",
            ],
            serial,
        ),
        capture_output=True,
    )


def _permission_granted(permission: str, serial: Optional[str] = None) -> bool:
    cmd = _adb_cmd(["shell", "dumpsys", "package", "dev.devkey.keyboard"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    pattern = rf"{re.escape(permission)}: granted=true"
    return re.search(pattern, result.stdout) is not None
