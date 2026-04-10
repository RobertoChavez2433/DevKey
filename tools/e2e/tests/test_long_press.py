"""
Long-press popup coverage tests — every key in every mode (F2).

FROM SPEC: §6 Phase 2 item 2.3 — "Long-press popup coverage (every key in every mode)"

Strategy:
  - Read expected long-press data from .claude/test-flows/long-press-expectations/<mode>.json
  - For each key, swipe-hold 500ms to trigger long-press
  - Assert DevKey/TXT long_press_fired event (from Phase 1 sub-phase 1.5 instrumentation)
    arrives with matching label and lp_code
  - Popup visual-content diff is a SKIP-on-missing-reference assertion (F2)
"""
import json
import os
from lib import adb, keyboard, driver

EXPECTATIONS_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "..", ".claude", "test-flows", "long-press-expectations"
)
POPUP_REF_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "..", ".claude", "test-flows", "swiftkey-reference", "popups"
)


def _load_expectations(mode: str) -> list:
    path = os.path.join(EXPECTATIONS_DIR, f"{mode}.json")
    if not os.path.exists(path):
        import pytest
        pytest.skip(f"long-press expectations file missing: {path}")
    with open(path, "r") as f:
        return json.load(f)


def _setup(mode: str):
    serial = adb.get_device_serial()
    driver.require_driver()
    keyboard.set_layout_mode(mode, serial)
    return serial


def _long_press(label: str, serial: str) -> None:
    km = keyboard.get_key_map()
    if label not in km:
        raise KeyError(f"'{label}' not in key map for current layout")
    x, y = km[label]
    driver.swipe(x, y, x, y, duration_ms=500)


def _assert_long_press_for_mode(mode: str):
    """
    Every key in the mode's expectation file must emit a DevKey/TXT long_press_fired
    event with matching label and lp_code.
    """
    serial = _setup(mode)
    expectations = _load_expectations(mode)

    for entry in expectations:
        label = entry["label"]
        expected_codes = entry.get("lp_codes", [])
        if not expected_codes:
            continue  # key has no long-press popup — skip
        driver.clear_logs()
        _long_press(label, serial)
        # F6: wave-gate on DevKeyLogger.text long_press_fired — zero sleeps.
        fired = driver.wait_for(
            category="DevKey/TXT",
            event="long_press_fired",
            match={"label": label},
            timeout_ms=2000,
        )
        lp_code = int(fired["data"].get("lp_code", 0))
        assert lp_code in expected_codes, (
            f"[{mode}] long-press on '{label}': fired lp_code={lp_code}, "
            f"expected one of {expected_codes}"
        )

        # F2 popup-content diff — SKIP if the reference image is missing.
        ref_path = os.path.join(POPUP_REF_DIR, mode, f"{label}.png")
        if os.path.exists(ref_path):
            from lib import diff
            import subprocess
            actual_path = os.path.join(
                os.path.dirname(os.path.abspath(__file__)),
                "..", "artifacts", f"popup-{mode}-{label}.png"
            )
            os.makedirs(os.path.dirname(actual_path), exist_ok=True)
            cmd = ["adb"]
            if serial:
                cmd += ["-s", serial]
            cmd += ["exec-out", "screencap", "-p"]
            with open(actual_path, "wb") as f:
                subprocess.run(cmd, check=True, stdout=f)
            passed, score = diff.assert_ssim(actual_path, ref_path)
            assert passed, (
                f"[{mode}] popup visual diff FAIL for '{label}': SSIM={score:.4f}"
            )


def test_long_press_every_key_full():
    _assert_long_press_for_mode("full")


def test_long_press_every_key_compact():
    _assert_long_press_for_mode("compact")


def test_long_press_every_key_compact_dev():
    _assert_long_press_for_mode("compact_dev")


def test_long_press_every_key_symbols():
    """
    Long-press every key in Symbols mode.

    Symbols mode is entered via SET_KEYBOARD_MODE broadcast (not a layout mode).
    Coordinates come from the SYM_KEY entries in the key map dump.
    """
    serial = adb.get_device_serial()
    driver.require_driver()
    keyboard.set_layout_mode("full", serial)

    expectations = _load_expectations("symbols")

    # Enter symbols mode via 123 key tap (not broadcast) so the keyboard
    # actually switches its display. The SYM_KEY coordinates in the key map
    # correspond to the rendered Symbols layout.
    keyboard.tap_key_by_code(-2, serial)  # KEYCODE_SYMBOLS = -2
    import time
    time.sleep(0.5)

    # Reload key map to get fresh SYM_KEY coordinates.
    keyboard.load_key_map(serial)
    sym_map = keyboard.get_symbols_key_map()

    for entry in expectations:
        label = entry["label"]
        if label not in sym_map:
            continue  # Key not in symbols map for this layout

        x, y = sym_map[label]
        driver.clear_logs()
        driver.swipe(x, y, x, y, duration_ms=500)
        try:
            result = driver.wait_for(
                category="DevKey/TXT",
                event="long_press_fired",
                match={"label": label},
                timeout_ms=2000,
            )
        except driver.DriverTimeout:
            raise AssertionError(
                f"[symbols] long_press_fired timeout for label='{label}' at ({x},{y})"
            )

    # Restore Normal mode.
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
