# Pattern — Graceful Skip (currently broken)

## Summary
Phase 2 tests SKIP (do not FAIL) when a legitimate missing prerequisite is
detected — missing reference image, absent voice model, physical device that
cannot inject audio, missing terminal package, etc. The `swiftkey-visual-diff`,
`long-press-coverage`, and `voice-round-trip` flows each declare in
`registry.md` and `coverage-matrix.md` that a missing reference is a SKIP,
not a red.

**CRITICAL: this pattern is currently broken end-to-end** because
`e2e_runner.py` cannot handle `pytest.skip()`. See `ground-truth.md` blocker
B1. Phase 3 stabilization MUST repair this before any gate can be trusted.

## How we do it (intended contract)
A test module imports `pytest` lazily and calls `pytest.skip("<reason>")`
inside the test function. The runner catches the skip, prints `SKIP (<reason>)`,
increments a skip counter, and counts the test as not-red for exit-code
purposes. Missing SwiftKey references, missing voice model, uninstalled
terminal packages — all flow through this path.

## Exemplar 1 — missing SwiftKey reference (`tests/test_visual_diff.py:44-49`)
```python
reference_path = os.path.join(REFERENCE_DIR, reference_name)
if not os.path.exists(reference_path):
    import pytest
    pytest.skip(
        f"SwiftKey reference not captured yet: {reference_name}. "
        f"See spec §4.4.1 for capture checklist."
    )
```

## Exemplar 2 — missing long-press expectation file (`tests/test_long_press.py:27-34`)
```python
def _load_expectations(mode: str) -> list:
    path = os.path.join(EXPECTATIONS_DIR, f"{mode}.json")
    if not os.path.exists(path):
        import pytest
        pytest.skip(f"long-press expectations file missing: {path}")
    with open(path, "r") as f:
        return json.load(f)
```

## Exemplar 3 — voice SKIP chain (`tests/test_voice.py:77-90`)
```python
try:
    driver.wait_for("DevKey/VOX", "state_transition",
                    match={"state": "LISTENING"}, timeout_ms=3000)
except driver.DriverTimeout:
    import pytest
    pytest.skip("LISTENING state never reached (permission or model missing)")

if not audio.is_emulator(serial):
    import pytest
    pytest.skip("audio injection requires an emulator")
if not audio.inject_sample():
    import pytest
    pytest.skip("audio sample not available or no host audio player found")
```

## The broken contract (B1 + B2)

- `pytest.Skipped` subclasses `BaseException`, NOT `Exception` (verified live
  on `pytest 9.0.2`).
- `e2e_runner.py:101` catches only `Exception`.
- `pytest` is not declared in `tools/e2e/requirements.txt`.

**Net effect:** every call to `pytest.skip(...)` in the current test tree
bubbles a `BaseException` through the runner's loop and terminates the
entire run with a traceback. This means Phase 3 cannot even *begin* the
gate run without fixing this pattern first.

## Proposed fix (stabilization-in-place)

Phase 3 plan authors should choose one of:

**Option A — support real pytest skip:**
1. Add `pytest>=7.0` to `tools/e2e/requirements.txt`.
2. Modify `e2e_runner.py:91-107` to catch `_pytest.outcomes.Skipped`
   (or `BaseException` narrowly typed) between the `AssertionError` and
   `Exception` clauses, increment a `skipped` counter, print `SKIP`, and
   **do not** increment errors.
3. Update the final summary at line 110 to include `skipped` and the exit
   code at line 182 remains `1 if (failed + errors) > 0 else 0` (skips
   count as green).

**Option B — local sentinel:**
1. Introduce `tools/e2e/lib/harness.py` with `class HarnessSkip(Exception)`.
2. Replace every `import pytest; pytest.skip(...)` with
   `from lib.harness import HarnessSkip; raise HarnessSkip(...)`.
3. Catch it in the runner between `AssertionError` and `Exception`.
4. Remove `pytest` lazy imports. Leaves requirements.txt unchanged.

Option A is preferred — it keeps the test modules portable to a real
`pytest` runner if DevKey ever migrates off the custom harness.

## Reusable operations

None until the pattern is fixed. After the fix, `driver.wait_for` timeout
paths that indicate "instrumentation not landed yet" should use the skip
sentinel, not `AssertionError`.

## Anti-patterns
- **Do not** convert existing skips to `return` (silently passing the test).
  That masks legitimate gate gaps in the §3.2 / §3.4 / §3.5 coverage.
- **Do not** convert existing skips to `assert False, "skip"`. That would
  turn a legitimately-missing reference into a red gate, blocking release
  on something spec §4.4.1 explicitly defers.
