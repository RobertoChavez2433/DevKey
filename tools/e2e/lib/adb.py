"""
ADB helper functions for DevKey E2E tests.

Wraps common ADB shell commands for tapping, logcat capture,
and UI state inspection.
"""

import os
import re
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from . import verify


def get_device_serial() -> Optional[str]:
    """Get device serial from env var or None for default device."""
    return os.environ.get("DEVKEY_DEVICE_SERIAL")


def _adb_cmd(args: List[str], serial: Optional[str] = None) -> List[str]:
    """Build ADB command with optional device serial."""
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return cmd


def is_emulator(serial: Optional[str] = None) -> bool:
    """Return true when the selected ADB target is an Android emulator."""
    if serial and serial.startswith("emulator-"):
        return True
    result = subprocess.run(
        _adb_cmd(["shell", "getprop", "ro.kernel.qemu"], serial),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return result.stdout.strip() == "1"


def configure_debug_server_forwarding(
    serial: Optional[str] = None,
    driver_url: Optional[str] = None,
) -> str:
    """
    Return the URL the IME should use for the host debug server.

    Emulators reach the host loopback through 10.0.2.2. Physical devices need
    adb reverse for loopback URLs so device localhost forwards to the host.
    """
    url = driver_url or os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    if is_emulator(serial):
        return url.replace("127.0.0.1", "10.0.2.2").replace("localhost", "10.0.2.2")

    match = re.match(r"^(https?://)(127\.0\.0\.1|localhost):(\d+)(/.*)?$", url)
    if match:
        port = match.group(3)
        subprocess.run(
            _adb_cmd(["reverse", f"tcp:{port}", f"tcp:{port}"], serial),
            check=True,
            capture_output=True,
        )
        verify.record_action("adb.reverse", {"port": port}, requires_verification=False)
        return f"{match.group(1)}127.0.0.1:{port}{match.group(4) or ''}"
    return url


def tap(x: int, y: int, serial: Optional[str] = None) -> None:
    """Tap at (x, y) screen coordinates via ADB shell input."""
    verify.record_action("adb.tap", {"x": x, "y": y})
    cmd = _adb_cmd(["shell", "input", "tap", str(x), str(y)], serial)
    subprocess.run(cmd, check=True, capture_output=True)


def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300,
          serial: Optional[str] = None) -> None:
    """Swipe from (x1,y1) to (x2,y2) over given duration."""
    verify.record_action("adb.swipe", {"x1": x1, "y1": y1, "x2": x2, "y2": y2, "duration_ms": duration_ms})
    cmd = _adb_cmd([
        "shell", "input", "swipe",
        str(x1), str(y1), str(x2), str(y2), str(duration_ms)
    ], serial)
    subprocess.run(cmd, check=True, capture_output=True)


def input_text(text: str, serial: Optional[str] = None) -> None:
    """Type text via ADB shell input text."""
    verify.record_action("adb.input_text", {"length": len(text)})
    cmd = _adb_cmd(["shell", "input", "text", text], serial)
    subprocess.run(cmd, check=True, capture_output=True)


def clear_logcat(serial: Optional[str] = None, retries: int = 3) -> None:
    """
    Clear the logcat buffer.

    `adb logcat -c` is flaky on emulators under load — it occasionally exits
    with status 4294967295 (-1) when the buffer is being written to. Retry
    a few times before giving up.
    """
    verify.record_action("adb.clear_logcat", requires_verification=False)
    cmd = _adb_cmd(["logcat", "-c"], serial)
    last_err: Optional[Exception] = None
    for attempt in range(retries):
        result = subprocess.run(cmd, capture_output=True)
        if result.returncode == 0:
            return
        last_err = subprocess.CalledProcessError(result.returncode, cmd, result.stdout, result.stderr)
        time.sleep(0.2 * (attempt + 1))
    if last_err is not None:
        raise last_err


def capture_logcat(tag: str, timeout: float = 2.0,
                   serial: Optional[str] = None) -> List[str]:
    """
    Capture logcat lines for a specific tag.

    Waits for `timeout` seconds, then dumps logcat filtered by tag.
    Returns list of matching log lines.
    """
    time.sleep(timeout)
    cmd = _adb_cmd(["logcat", "-d", "-s", f"{tag}:*"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    lines = result.stdout.strip().split("\n")
    # Filter out the header line
    filtered = [line for line in lines if tag in line]
    if filtered:
        verify.record_evidence("adb.capture_logcat", {"tag": tag, "line_count": len(filtered)})
    return filtered


def _read_focused_text(serial: Optional[str] = None) -> Optional[str]:
    cmd = _adb_cmd(["shell", "dumpsys", "input_method"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    match = re.search(r"mText=(.*?)(?:\n|$)", result.stdout)
    if match:
        return match.group(1).rstrip("\r")
    return None


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


def get_focused_window(serial: Optional[str] = None) -> str:
    """Get the currently focused window/activity name."""
    cmd = _adb_cmd(["shell", "dumpsys", "window", "windows"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    match = re.search(r"mCurrentFocus=Window\{.+?\s+(.+?)\}", result.stdout)
    if match:
        verify.record_evidence("adb.focused_window", {"window": match.group(1)})
        return match.group(1)
    return ""


def is_keyboard_visible(serial: Optional[str] = None) -> bool:
    """Check if the soft keyboard is currently visible."""
    cmd = _adb_cmd(["shell", "dumpsys", "input_method"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    visible = "mInputShown=true" in result.stdout
    if visible:
        verify.record_evidence("adb.keyboard_visible")
    return visible


TEST_HOST_ACTIVITY = (
    "dev.devkey.keyboard/dev.devkey.keyboard.debug.TestHostActivity"
)
DEBUG_CLEAR_EDIT_TEXT_ACTION = "dev.devkey.keyboard.debug.CLEAR_EDIT_TEXT"
DEBUG_DUMP_TEST_HOST_STATE_ACTION = "dev.devkey.keyboard.debug.DUMP_TEST_HOST_STATE"
RESET_KEYBOARD_MODE_ACTION = "dev.devkey.keyboard.RESET_KEYBOARD_MODE"


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
    focused_match = re.search(r"focused=(true|false)", line)
    if not length_match:
        return None
    return {
        "text_length": int(length_match.group(1)),
        "focused": focused_match.group(1) == "true" if focused_match else None,
        "request_id": request_id,
    }


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


def capture_screenshot(out_path: os.PathLike[str] | str, serial: Optional[str] = None) -> Path:
    """Capture a device screenshot and record privacy-safe metadata."""
    path = Path(out_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as fh:
        subprocess.run(_adb_cmd(["exec-out", "screencap", "-p"], serial), check=True, stdout=fh)
    size = path.stat().st_size
    if size <= 0:
        raise AssertionError(f"Screenshot capture was empty: {path}")
    verify.record_evidence("adb.screenshot", {"path": str(path), "bytes": size})
    return path


def analyze_keyboard_visual_baseline(screenshot_path: os.PathLike[str] | str) -> Dict[str, Any]:
    """
    Check that the visible keyboard region is not blank or low-contrast.

    This machine gate complements the clean text-field check. It catches blank
    or illegible screenshots so visual validation cannot pass on an unusable
    screen state.
    """
    from PIL import Image, ImageStat

    path = Path(screenshot_path)
    img = Image.open(path).convert("L")
    width, height = img.size
    if width < 320 or height < 480:
        raise AssertionError(f"Screenshot dimensions are invalid: {width}x{height}")

    crop_top = int(height * 0.55)
    crop_bottom = int(height * 0.96)
    keyboard_crop = img.crop((0, crop_top, width, crop_bottom))
    min_luma, max_luma = keyboard_crop.getextrema()
    contrast = int(max_luma - min_luma)
    stddev = float(ImageStat.Stat(keyboard_crop).stddev[0])

    ok = contrast >= 45 and stddev >= 18.0
    metrics = {
        "ok": ok,
        "width": width,
        "height": height,
        "keyboard_crop": {
            "top": crop_top,
            "bottom": crop_bottom,
            "contrast": contrast,
            "stddev": round(stddev, 3),
        },
    }
    verify.record_evidence("adb.visual_baseline", metrics)
    if not ok:
        raise AssertionError(
            "Keyboard visual baseline is too low contrast to trust; "
            f"contrast={contrast}, stddev={stddev:.3f}, screenshot={path}"
        )
    return metrics


def capture_visual_baseline(
    serial: Optional[str] = None,
    out_path: Optional[os.PathLike[str] | str] = None,
) -> Dict[str, Any]:
    """Capture and validate a clean, privacy-safe keyboard baseline screenshot."""
    assert_text_field_empty(serial, context="visual_baseline")
    if out_path is None:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        out_path = Path(".claude") / "test-results" / f"visual-baseline-{stamp}.png"
    path = capture_screenshot(out_path, serial)
    metrics = analyze_keyboard_visual_baseline(path)
    return {
        "ok": True,
        "screenshot": str(path),
        **metrics,
    }


def _permission_granted(permission: str, serial: Optional[str] = None) -> bool:
    cmd = _adb_cmd(["shell", "dumpsys", "package", "dev.devkey.keyboard"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    pattern = rf"{re.escape(permission)}: granted=true"
    return re.search(pattern, result.stdout) is not None


def ensure_keyboard_visible(serial: Optional[str] = None) -> dict:
    """
    Ensure the soft keyboard is visible and focused on a controlled EditText.

    Phase 3 gate observation: after SET_LAYOUT_MODE broadcasts, IME mode
    switches, or backgrounding, the soft keyboard can vanish mid-run and
    subsequent tap_key calls produce zero events. Every _setup helper
    should call this so tests don't inherit a keyboardless state from a
    prior test.

    Strategy:
      1. Always launch `dev.devkey.keyboard.debug.TestHostActivity` via
         `am start` — a debug-only activity that hosts a single EditText
         configured with stateVisible soft-input mode.
      2. The activity is owned by the DevKey build so it is guaranteed
         present on any device where the IME is installed. Unlike Chrome,
         it cannot navigate away on typed input, cannot lose focus to OS
         affordances, and cannot accidentally tab to another activity.
      3. Busy-wait up to 3 seconds for the IME to mark itself visible.

    Idempotent — cheap to call before every test. The underlying `am start`
    is a no-op if the activity is already top.
    """
    # Grant RECORD_AUDIO permission — required for voice tests. This is
    # idempotent and cheap; avoids requiring manual setup on fresh emulators.
    grant_result = subprocess.run(
        _adb_cmd(
            ["shell", "pm", "grant", "dev.devkey.keyboard",
             "android.permission.RECORD_AUDIO"],
            serial,
        ),
        capture_output=True,
    )
    # Always bring the test host to the front. It is cheap when it's already
    # top and guaranteed to re-request IME visibility via SOFT_INPUT_STATE_VISIBLE.
    subprocess.run(
        _adb_cmd(
            ["shell", "am", "start", "-n", TEST_HOST_ACTIVITY],
            serial,
        ),
        capture_output=True,
    )
    # Wait for the IME to mark itself visible — up to 3s in 100ms slices.
    deadline = time.time() + 3.0
    visible = False
    while time.time() < deadline:
        if is_keyboard_visible(serial):
            visible = True
            break
        time.sleep(0.1)

    # WHY: The runner broadcasts ENABLE_DEBUG_SERVER once at startup, but if
    #      the IME service wasn't yet bound at that moment (mCurMethod=null
    #      happens after a fresh install / force-stop), the broadcast is
    #      dropped because LatinIME.onCreate hasn't yet registered the
    #      receiver. Once the IME is visible we re-broadcast every test so
    #      DevKeyLogger.serverUrl is guaranteed set for the upcoming test.
    # IMPORTANT: This is a no-op on the IME side if the URL is unchanged.
    if visible:
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
        # H002: Reset the circuit breaker between tests so a prior test's
        # HTTP failures don't suppress events for the next test.
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

    if visible:
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


def assert_logcat_contains(tag: str, pattern: str, timeout: float = 2.0,
                           serial: Optional[str] = None) -> bool:
    """
    Assert that logcat contains a line matching the given pattern for the tag.

    Returns True if found, raises AssertionError if not.
    """
    lines = capture_logcat(tag, timeout, serial)
    for line in lines:
        if re.search(pattern, line):
            verify.record_evidence("adb.assert_logcat_contains", {"tag": tag, "pattern": pattern})
            return True
    raise AssertionError(
        f"Expected logcat tag '{tag}' to contain pattern '{pattern}', "
        f"but got {len(lines)} lines: {lines[:5]}"
    )
