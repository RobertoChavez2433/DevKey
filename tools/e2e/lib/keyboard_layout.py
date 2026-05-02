"""Layout-mode controls for DevKey E2E tests."""
import subprocess
import time
from typing import Optional

from . import adb, verify
from .keyboard_keymap import clear_key_map_cache, load_key_map


def set_layout_mode(mode: str, serial: Optional[str] = None) -> None:
    """
    Switch the running IME to the given layout mode via debug broadcast.

    Valid values: "full", "compact", "compact_dev".
    """
    if mode not in ("full", "compact", "compact_dev"):
        raise ValueError(f"invalid layout mode: {mode}")

    _reset_keyboard_mode_for_layout(serial, mode)
    _set_layout_mode(mode, serial)
    clear_key_map_cache()
    _wait_for_layout_recompose(mode)
    load_key_map(serial)


def _reset_keyboard_mode_for_layout(serial: Optional[str], mode: str) -> None:
    subprocess.run(
        adb._adb_cmd(
            ["shell", "am", "broadcast", "-a", "dev.devkey.keyboard.RESET_KEYBOARD_MODE"],
            serial,
        ),
        check=False,
        capture_output=True,
    )
    verify.record_action("keyboard.reset_mode_for_layout", {"mode": mode}, requires_verification=False)
    time.sleep(0.3)


def _set_layout_mode(mode: str, serial: Optional[str]) -> None:
    subprocess.run(
        adb._adb_cmd(
            [
                "shell", "am", "broadcast",
                "-a", "dev.devkey.keyboard.SET_LAYOUT_MODE",
                "--es", "mode", mode,
            ],
            serial,
        ),
        check=True,
        capture_output=True,
    )
    verify.record_action("keyboard.set_layout_mode", {"mode": mode})


def _wait_for_layout_recompose(mode: str) -> None:
    from lib import driver

    try:
        driver.wait_for(
            "DevKey/IME",
            "layout_mode_recomposed",
            match={"mode": mode},
            timeout_ms=5000,
        )
    except driver.DriverError:
        pass
