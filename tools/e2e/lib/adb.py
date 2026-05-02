"""
ADB helper functions for DevKey E2E tests.

Wraps common ADB shell commands for tapping, logcat capture,
and UI state inspection.
"""

import os
import re
import subprocess
import time
from typing import List, Optional

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


def get_text_field_content(serial: Optional[str] = None) -> str:
    """
    Read the text content of the currently focused text field.

    Uses dumpsys input_method to get the current text from the IME.
    Returns the extracted text or empty string if not found.
    """
    cmd = _adb_cmd(["shell", "dumpsys", "input_method"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    # Look for "mText=" in the InputConnection section
    match = re.search(r"mText=(.+?)(?:\n|$)", result.stdout)
    if match:
        verify.record_evidence("adb.text_field_content", {"length": len(match.group(1).strip())})
        return match.group(1).strip()
    return ""


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
