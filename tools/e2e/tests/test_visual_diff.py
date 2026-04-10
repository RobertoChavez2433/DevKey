"""
SwiftKey visual parity tests.

FROM SPEC: §6 Phase 2 item 2.3 — "SwiftKey visual diff (screenshot comparison
           against captured SwiftKey reference screenshots)"
FROM SPEC: §4.4.1 — reference files live in .claude/test-flows/swiftkey-reference/
                     (local-only, .gitignored).

Strategy:
  - Switch to the layout mode whose reference exists
  - Screenshot via driver.logcat_dump? No — direct adb exec-out
  - SSIM against reference
  - SKIP (not FAIL) when reference is missing, since capture is a manual prereq

CURRENT REFERENCE INVENTORY (as of Session 42 — verify at runtime):
  - compact-dark.png   → COMPACT mode, dark theme
  - All other modes/themes: NOT YET CAPTURED → tests for them skip
"""
import os
import subprocess
from lib import adb, keyboard, driver, diff

REFERENCE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "..", ".claude", "test-flows", "swiftkey-reference"
)


def _capture_screenshot(out_path: str, serial: str) -> None:
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += ["exec-out", "screencap", "-p"]
    with open(out_path, "wb") as f:
        subprocess.run(cmd, check=True, stdout=f)


def _crop_to_reference_aspect(actual_path: str, reference_path: str) -> None:
    """
    Crop the captured screenshot to the keyboard region by matching the
    reference's aspect ratio. Overwrites `actual_path` in place.

    WHY: full-screen captures include the host activity chrome (status bar,
         title, EditText, navigation bar) which has nothing to do with the
         keyboard we're measuring parity against. Comparing full screens
         drops SSIM into the 0.3-0.5 range because 60% of the pixels are
         off-topic. The SwiftKey reference `*-cropped.png` files are already
         keyboard-only; we crop the actual capture to the same aspect ratio
         (anchored to the bottom of the screen where the keyboard lives)
         before running the structural-similarity comparison.
    """
    from PIL import Image

    img = Image.open(actual_path).convert("RGB")
    ref = Image.open(reference_path).convert("RGB")
    aw, ah = img.size
    rw, rh = ref.size
    ref_aspect = rw / rh  # width / height
    target_h = int(round(aw / ref_aspect))
    # Guard against degenerate crops — e.g. landscape captures where the
    # computed target is taller than the image itself.
    if target_h <= 0 or target_h > ah:
        return
    crop_box = (0, ah - target_h, aw, ah)
    img.crop(crop_box).save(actual_path)


def _force_toolbar_off(serial: str) -> None:
    """
    Disable the SwiftKey-parity-breaking toolbar row for visual diff runs.

    The SwiftKey reference screenshots were captured with no toolbar, so
    leaving DevKey's toolbar pref ON shifts the keyboard body up and makes
    the crop mismatch the reference. Write the pref directly via adb shell.
    """
    prefs_path = (
        "/data/data/dev.devkey.keyboard/shared_prefs/"
        "dev.devkey.keyboard_preferences.xml"
    )
    # Best-effort: flip the pref via run-as so it takes effect on next onStartInput.
    # If the file doesn't exist yet, broadcast the ENABLE_DEBUG_SERVER receiver first
    # to ensure prefs are created. We ignore failures — the test will still run.
    for cmd in [
        ["adb", "-s", serial, "shell", "am", "broadcast", "-a",
         "dev.devkey.keyboard.SET_BOOL_PREF", "--es", "key", "devkey_show_toolbar",
         "--ez", "value", "false"],
    ]:
        try:
            subprocess.run(cmd, check=False, capture_output=True,
                           encoding="utf-8", errors="replace")
        except Exception:
            pass


def _visual_diff_test(mode: str, reference_name: str):
    serial = adb.get_device_serial()
    driver.require_driver()

    _force_toolbar_off(serial)

    reference_path = os.path.join(REFERENCE_DIR, reference_name)
    if not os.path.exists(reference_path):
        import pytest
        pytest.skip(
            f"SwiftKey reference not captured yet: {reference_name}. "
            f"See spec §4.4.1 for capture checklist."
        )

    keyboard.set_layout_mode(mode, serial)
    # Ensure keyboard is visible.
    if not adb.is_keyboard_visible(serial):
        subprocess.run(
            ["adb", "-s", serial, "shell", "am", "start", "-a",
             "android.intent.action.INSERT", "-t",
             "vnd.android.cursor.dir/contact"],
            check=True, capture_output=True,
        )

    actual_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "artifacts", f"{mode}-{os.path.splitext(reference_name)[0]}.png"
    )
    _capture_screenshot(actual_path, serial)
    # Prefer the `*-cropped.png` variant as the diff oracle when it exists —
    # matches the keyboard-only crop of the SwiftKey reference capture. If the
    # only reference is the full-screen variant, we skip the crop step and
    # compare whole screens (lower SSIM, but still a signal).
    base, ext = os.path.splitext(reference_name)
    cropped_reference = os.path.join(REFERENCE_DIR, f"{base}-cropped{ext}")
    if os.path.exists(cropped_reference):
        reference_path = cropped_reference
        _crop_to_reference_aspect(actual_path, reference_path)

    passed, score = diff.assert_ssim(actual_path, reference_path)
    assert passed, (
        f"Visual diff FAIL for {mode} vs {reference_name}: "
        f"SSIM={score:.4f} < threshold. "
        f"Actual: {actual_path}  Reference: {reference_path}"
    )


def test_visual_compact_dark():
    _visual_diff_test("compact", "compact-dark.png")


def test_visual_full_dark():
    _visual_diff_test("full", "full-dark.png")


def test_visual_compact_dev_dark():
    _visual_diff_test("compact_dev", "compact-dev-dark.png")
