"""Logcat helpers for DevKey E2E tests."""
import re
import subprocess
import time
from typing import List, Optional

from . import verify
from .adb_device import _adb_cmd


def clear_logcat(serial: Optional[str] = None, retries: int = 3) -> None:
    """
    Clear the logcat buffer.

    `adb logcat -c` can be flaky under load. Retry a few times before giving up.
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
    filtered = [line for line in lines if tag in line]
    if filtered:
        verify.record_evidence("adb.capture_logcat", {"tag": tag, "line_count": len(filtered)})
    return filtered


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
