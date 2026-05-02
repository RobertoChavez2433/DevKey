"""Low-level ADB device commands for DevKey E2E tests."""
import os
import re
import subprocess
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
