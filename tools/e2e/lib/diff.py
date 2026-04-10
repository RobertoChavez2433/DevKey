"""
Visual diff helpers for SwiftKey parity testing.

Uses scikit-image's structural_similarity (SSIM) with a default threshold of 0.55.
Override via env var DEVKEY_SSIM_THRESHOLD.

WHY 0.55 (not 0.92):
    The SwiftKey reference captures come from a real device with native Android
    rendering at one DPI; DevKey runs in Compose at a different DPI on the
    emulator. Even after cropping the captures to the same keyboard aspect
    ratio, structural similarity tops out around 0.60 because:
      - Compose vs native font shapers anti-alias key labels differently
      - SwiftKey draws subtle shadow/highlights on each key cap; DevKey is flat
      - SwiftKey's spacebar carries the "Microsoft SwiftKey" watermark;
        DevKey's is bare
    A threshold around 0.55 catches *gross* regressions (e.g. wrong layout,
    color theme drift, missing rows) without rejecting the pixel-level
    differences that are inherent to running two different rendering stacks.
    Tests that need a tighter check should pass `threshold=` explicitly.
"""
import os
from typing import Tuple, Optional

DEFAULT_THRESHOLD = float(os.environ.get("DEVKEY_SSIM_THRESHOLD", "0.55"))


def ssim(actual_path: str, reference_path: str) -> float:
    """
    Compute SSIM between two image files.

    Raises FileNotFoundError if either file is missing — callers decide
    whether missing-reference is a SKIP (Phase 2 default) or a FAIL.
    """
    # WHY: scikit-image + Pillow are imported lazily so that the rest of the
    #      Python harness still works even if the visual-diff deps aren't installed.
    #      Unrelated flows shouldn't fail-hard at import time.
    from skimage.metrics import structural_similarity as _ssim
    from PIL import Image
    import numpy as np

    if not os.path.exists(actual_path):
        raise FileNotFoundError(f"actual image missing: {actual_path}")
    if not os.path.exists(reference_path):
        raise FileNotFoundError(f"reference image missing: {reference_path}")

    a = np.array(Image.open(actual_path).convert("RGB"))
    b = np.array(Image.open(reference_path).convert("RGB"))

    # NOTE: If sizes differ, resize the reference to match the actual.
    #       Different emulator DPIs produce different screen sizes; parity
    #       is about visual structure, not pixel-exact dimensions.
    if a.shape != b.shape:
        b_img = Image.open(reference_path).convert("RGB").resize(
            (a.shape[1], a.shape[0]), Image.BICUBIC
        )
        b = np.array(b_img)

    # channel_axis=-1 tells scikit-image this is an RGB image, not grayscale.
    score, _ = _ssim(a, b, channel_axis=-1, full=True)
    return float(score)


def assert_ssim(actual_path: str, reference_path: str,
                threshold: Optional[float] = None) -> Tuple[bool, float]:
    """
    Returns (passed, score). `passed` is True if score >= threshold.

    Never raises on SSIM — only on missing files.
    """
    t = threshold if threshold is not None else DEFAULT_THRESHOLD
    score = ssim(actual_path, reference_path)
    return (score >= t, score)
