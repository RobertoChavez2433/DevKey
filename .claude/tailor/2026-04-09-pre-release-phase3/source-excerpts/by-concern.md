# Source Excerpts — by Concern

Mapped to the Phase 3 gate sub-items.

---

## §3.1 — Full harness × 3 layouts

### Runner entry point (`tools/e2e/e2e_runner.py:167-176`)
```python
from lib import driver
driver.require_driver()
_driver_url = os.environ.get("DEVKEY_DRIVER_URL", "http://10.0.2.2:3947")
driver.broadcast("dev.devkey.keyboard.ENABLE_DEBUG_SERVER", {"url": _driver_url})
```

### Layout switching contract (`tools/e2e/lib/keyboard.py:159-202`)
```python
def set_layout_mode(mode: str, serial: Optional[str] = None) -> None:
    if mode not in ("full", "compact", "compact_dev"):
        raise ValueError(f"invalid layout mode: {mode}")
    # broadcast SET_LAYOUT_MODE
    # wait_for layout_mode_recomposed
    # reload key map
```

---

## §3.2 — Voice round-trip

### Full round-trip test (`tools/e2e/tests/test_voice.py:62-108`)
```python
def test_voice_round_trip_committed_text():
    serial, voice_key = _setup_voice_button()
    driver.clear_logs()
    keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
    try:
        driver.wait_for("DevKey/VOX", "state_transition",
                        match={"state": "LISTENING"}, timeout_ms=3000)
    except driver.DriverTimeout:
        import pytest
        pytest.skip("LISTENING state never reached (permission or model missing)")
    # ... audio inject, processing, commit assertion
```

**Blocker:** B1 (skip handling) + B3 (model absent).

---

## §3.3 — Next-word prediction

### Canonical test (`tools/e2e/tests/test_next_word.py:24-59`)
```python
def test_next_word_fires_after_space():
    serial = _setup()
    driver.clear_logs()
    for ch in "the":
        keyboard.tap_key(ch, serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    entry = driver.wait_for("DevKey/TXT", "next_word_suggestions", timeout_ms=3000)
    assert entry["data"]["source"] in {"bigram_hit", "bigram_miss", "no_prev_word"}
    strip = driver.wait_for("DevKey/UI", "candidate_strip_rendered", timeout_ms=3000)
    if entry["data"]["source"] == "bigram_hit":
        suggestion_count = int(strip["data"].get("suggestion_count", 0))
        assert suggestion_count >= 1
```

---

## §3.4 — Long-press coverage

### Per-mode driver (`tools/e2e/tests/test_long_press.py:51-98`)
```python
def _assert_long_press_for_mode(mode: str):
    serial = _setup(mode)
    expectations = _load_expectations(mode)
    for entry in expectations:
        label = entry["label"]
        expected_codes = entry.get("lp_codes", [])
        if not expected_codes:
            continue
        driver.clear_logs()
        _long_press(label, serial)
        fired = driver.wait_for(
            category="DevKey/TXT",
            event="long_press_fired",
            match={"label": label},
            timeout_ms=2000,
        )
        lp_code = int(fired["data"].get("lp_code", 0))
        assert lp_code in expected_codes
```

### Expectation schema (verified on all 4 mode files)
```json
[{"label": "q", "lp_codes": [37]}, {"label": "w", "lp_codes": [94]}, ...]
```

---

## §3.5 — SwiftKey visual diff

### Per-mode test (`tools/e2e/tests/test_visual_diff.py:39-73`)
```python
def _visual_diff_test(mode: str, reference_name: str):
    serial = adb.get_device_serial()
    driver.require_driver()
    reference_path = os.path.join(REFERENCE_DIR, reference_name)
    if not os.path.exists(reference_path):
        import pytest
        pytest.skip(...)
    keyboard.set_layout_mode(mode, serial)
    # ... capture actual, compute SSIM, assert >= threshold (default 0.92)
```

### Reference inventory (on disk)
```
.claude/test-flows/swiftkey-reference/
  compact-dark.png              ✓
  compact-dark-cropped.png      ✓ (auxiliary)
  compact-dark-findings.md
  README.md
  (full-dark.png, compact-dev-dark.png — ABSENT, B4)
```

---

## §3.6 — Manual smoke

Both checklist files live under `.claude/test-flows/`:
- `phase1-regression-smoke.md` — 52 lines, clipboard/macros/command/plugins
- `phase1-voice-verification.md` — 31 lines, voice model + permission path

---

## §3.7 — Lint clean

### Build config (`app/build.gradle.kts:71-74`)
```kotlin
lint {
    checkReleaseBuilds = true
    abortOnError = false   // < semantic trap — see patterns/lint-gate.md
}
```

---

## §3.8 — Decision gate

No source file owns §3.8. It is the human operator's judgement, recorded in:
- `.claude/test-flows/tier-stabilization-status.md` (per-tier green SHAs)
- A Phase 3 gate commit that updates the above and closes the gate.
- Any defects filed via `gh issue create` per `patterns/defect-triage.md`.
