"""
Clipboard panel smoke test.

FROM PLAN: Phase 2 sub-phase 4.6 — "tap clipboard toolbar button → wait_for
           DevKey/UI panel_opened{panel=clipboard} → dismiss → assert no crash."

Depends on:
  - A DevKeyLogger.ui("panel_opened", mapOf("panel" to "clipboard")) emit site
    in the clipboard panel's onShow / initial composition. PRIVACY: structural
    only — NEVER log clipboard contents.

Skips gracefully when the clipboard button is not in the current key map (some
layouts may not surface it at the top level).
"""
from lib import adb, keyboard, driver

# Clipboard toolbar button keycode. Resolved at runtime via the key map by
# looking up the "Clipboard" label or by a documented keycode once wired in.
CLIPBOARD_LABEL_CANDIDATES = ["Clipboard", "clipboard", "\U0001F4CB"]  # 📋


def _find_clipboard_key():
    km = keyboard.get_key_map()
    for candidate in CLIPBOARD_LABEL_CANDIDATES:
        if candidate in km:
            return candidate, km[candidate]
    return None, None


def test_clipboard_panel_opens():
    """Tap the clipboard toolbar button → panel_opened event fires → dismiss cleanly."""
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)

    label, coords = _find_clipboard_key()
    if coords is None:
        import pytest
        pytest.skip(
            "clipboard toolbar key not in current key map — "
            "layout may not expose clipboard at top level"
        )

    driver.clear_logs()
    driver.tap(coords[0], coords[1])
    entry = driver.wait_for(
        category="DevKey/UI",
        event="panel_opened",
        match={"panel": "clipboard"},
        timeout_ms=2000,
    )
    # PRIVACY: panel_opened payload must never contain clipboard contents.
    data = entry.get("data", {})
    assert set(data.keys()).issubset({"panel"}), (
        f"panel_opened payload had unexpected keys: {set(data.keys()) - {'panel'}}. "
        f"PRIVACY: must be structural-only (panel name)."
    )

    # Dismiss via back key — harness asserts no crash by verifying the IME
    # process is still alive afterwards.
    import subprocess
    back_cmd = ["adb"]
    if serial:
        back_cmd += ["-s", serial]
    back_cmd += ["shell", "input", "keyevent", "KEYCODE_BACK"]
    subprocess.run(back_cmd, check=True, capture_output=True)

    pidof = ["adb"]
    if serial:
        pidof += ["-s", serial]
    pidof += ["shell", "pidof", "dev.devkey.keyboard"]
    result = subprocess.run(pidof, capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.stdout.strip(), "IME process died after dismissing clipboard panel"
