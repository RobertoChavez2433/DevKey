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


def tap(x: int, y: int, serial: Optional[str] = None) -> None:
    """Tap at (x, y) screen coordinates via ADB shell input."""
    cmd = _adb_cmd(["shell", "input", "tap", str(x), str(y)], serial)
    subprocess.run(cmd, check=True, capture_output=True)


def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300,
          serial: Optional[str] = None) -> None:
    """Swipe from (x1,y1) to (x2,y2) over given duration."""
    cmd = _adb_cmd([
        "shell", "input", "swipe",
        str(x1), str(y1), str(x2), str(y2), str(duration_ms)
    ], serial)
    subprocess.run(cmd, check=True, capture_output=True)


def input_text(text: str, serial: Optional[str] = None) -> None:
    """Type text via ADB shell input text."""
    cmd = _adb_cmd(["shell", "input", "text", text], serial)
    subprocess.run(cmd, check=True, capture_output=True)


def clear_logcat(serial: Optional[str] = None, retries: int = 3) -> None:
    """
    Clear the logcat buffer.

    `adb logcat -c` is flaky on emulators under load — it occasionally exits
    with status 4294967295 (-1) when the buffer is being written to. Retry
    a few times before giving up.
    """
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
    return [line for line in lines if tag in line]


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
        return match.group(1).strip()
    return ""


def get_focused_window(serial: Optional[str] = None) -> str:
    """Get the currently focused window/activity name."""
    cmd = _adb_cmd(["shell", "dumpsys", "window", "windows"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    match = re.search(r"mCurrentFocus=Window\{.+?\s+(.+?)\}", result.stdout)
    if match:
        return match.group(1)
    return ""


def is_keyboard_visible(serial: Optional[str] = None) -> bool:
    """Check if the soft keyboard is currently visible."""
    cmd = _adb_cmd(["shell", "dumpsys", "input_method"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return "mInputShown=true" in result.stdout


TEST_HOST_ACTIVITY = (
    "dev.devkey.keyboard/dev.devkey.keyboard.debug.TestHostActivity"
)


def ensure_keyboard_visible(serial: Optional[str] = None) -> None:
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
        driver_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
        # WHY: 127.0.0.1 on host is not reachable from inside the emulator.
        # Android emulator uses 10.0.2.2 to reach host loopback.
        ime_url = driver_url.replace("127.0.0.1", "10.0.2.2").replace("localhost", "10.0.2.2")
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


def assert_logcat_contains(tag: str, pattern: str, timeout: float = 2.0,
                           serial: Optional[str] = None) -> bool:
    """
    Assert that logcat contains a line matching the given pattern for the tag.

    Returns True if found, raises AssertionError if not.
    """
    lines = capture_logcat(tag, timeout, serial)
    for line in lines:
        if re.search(pattern, line):
            return True
    raise AssertionError(
        f"Expected logcat tag '{tag}' to contain pattern '{pattern}', "
        f"but got {len(lines)} lines: {lines[:5]}"
    )
