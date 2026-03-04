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


def clear_logcat(serial: Optional[str] = None) -> None:
    """Clear the logcat buffer."""
    cmd = _adb_cmd(["logcat", "-c"], serial)
    subprocess.run(cmd, check=True, capture_output=True)


def capture_logcat(tag: str, timeout: float = 2.0,
                   serial: Optional[str] = None) -> List[str]:
    """
    Capture logcat lines for a specific tag.

    Waits for `timeout` seconds, then dumps logcat filtered by tag.
    Returns list of matching log lines.
    """
    time.sleep(timeout)
    cmd = _adb_cmd(["logcat", "-d", "-s", f"{tag}:*"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True)
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
    result = subprocess.run(cmd, capture_output=True, text=True)
    # Look for "mText=" in the InputConnection section
    match = re.search(r"mText=(.+?)(?:\n|$)", result.stdout)
    if match:
        return match.group(1).strip()
    return ""


def get_focused_window(serial: Optional[str] = None) -> str:
    """Get the currently focused window/activity name."""
    cmd = _adb_cmd(["shell", "dumpsys", "window", "windows"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True)
    match = re.search(r"mCurrentFocus=Window\{.+?\s+(.+?)\}", result.stdout)
    if match:
        return match.group(1)
    return ""


def is_keyboard_visible(serial: Optional[str] = None) -> bool:
    """Check if the soft keyboard is currently visible."""
    cmd = _adb_cmd(["shell", "dumpsys", "input_method"], serial)
    result = subprocess.run(cmd, capture_output=True, text=True)
    return "mInputShown=true" in result.stdout


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
