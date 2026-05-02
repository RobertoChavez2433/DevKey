"""Screenshot and visual-baseline helpers for DevKey E2E tests."""
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional

from . import verify
from .adb_device import _adb_cmd
from .adb_test_host import assert_text_field_empty


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
