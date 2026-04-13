# Pattern: E2E Subdirectory Migration

## Current State

- `tools/e2e/tests/` contains 20 flat `test_*.py` files
- `e2e_runner.py` uses `os.listdir("tests/")` — no recursion
- Filter: `--test module_name` only

## Target State

- Feature subdirectories: `tests/prediction/`, `tests/autocorrect/`, etc.
- Each subdirectory has `__init__.py`, `test_smoke.py`, `test_stress.py`, `test_edge.py`
- Runner discovers `tests/**/test_*.py` recursively
- New `--feature` flag filters by subdirectory name

## Migration Strategy

1. **Update runner first** — add `os.walk()` or `pathlib.glob("**/test_*.py")`
   discovery alongside existing flat discovery. Both must work.

2. **Add `--feature` flag** — filters to `tests/<feature>/` subdirectory.

3. **Create subdirectories** with new stress/edge tests. Do NOT move existing
   flat files yet — they remain as-is for backward compatibility.

4. **Migrate existing tests** into subdirectories as `test_smoke.py` files
   only after the runner update is verified.

5. **Verify** all 20 original tests still pass after runner update (spec
   requirement).

## Runner Discovery Pseudocode

```python
def discover_tests(test_filter=None, feature=None):
    tests_dir = Path(__file__).parent / "tests"
    patterns = ["test_*.py"]  # flat
    if feature:
        patterns = [f"{feature}/test_*.py"]
    else:
        patterns = ["test_*.py", "*/test_*.py"]  # flat + one level deep
    for pattern in patterns:
        for path in sorted(tests_dir.glob(pattern)):
            ...
```
