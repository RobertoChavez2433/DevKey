"""Test discovery and rerun selection for the DevKey E2E runner."""
import importlib
import inspect
import json
from typing import Any, Callable, List, Optional, Tuple

from .paths import TESTS_DIR

LOCKED_FULL_SUITE_COUNT = 178
DiscoveredTest = Tuple[str, Callable[..., Any]]


def discover_tests(
    test_filter: Optional[str] = None,
    feature: Optional[str] = None,
    suite: str = "all",
) -> List[DiscoveredTest]:
    """
    Discover all test functions in the tests/ directory.

    Test functions must start with 'test_' and be in modules starting with 'test_'.
    Discovers flat files (tests/test_*.py) first, then one-level subdirectories
    (tests/*/test_*.py).
    """
    tests: List[DiscoveredTest] = []

    # Ordered tuples: import path, optional feature prefix, module name.
    candidates: List[Tuple[str, str, str]] = []

    if feature:
        for path in sorted(TESTS_DIR.glob(f"{feature}/test_*.py")):
            subdir = path.parent.name
            module_name = path.stem
            candidates.append((f"tests.{subdir}.{module_name}", subdir, module_name))
    else:
        if suite in ("all", "legacy-flat"):
            for path in sorted(TESTS_DIR.glob("test_*.py")):
                module_name = path.stem
                candidates.append((f"tests.{module_name}", "", module_name))

        if suite in ("all", "features"):
            for path in sorted(TESTS_DIR.glob("*/test_*.py")):
                subdir = path.parent.name
                module_name = path.stem
                candidates.append((f"tests.{subdir}.{module_name}", subdir, module_name))

    import_errors: List[str] = []
    for import_path, prefix, module_name in candidates:
        if test_filter and not _module_matches_filter(test_filter, prefix, module_name):
            continue

        try:
            module = importlib.import_module(import_path)
        except Exception as exc:
            print(f"  ERROR importing {import_path}: {exc}")
            import_errors.append(f"{import_path}: {exc}")
            continue

        for name, func in inspect.getmembers(module, inspect.isfunction):
            if not name.startswith("test_"):
                continue
            if test_filter and not _function_matches_filter(test_filter, prefix, name):
                continue
            tests.append((_qualified_name(prefix, module_name, name), func))

    if import_errors:
        raise RuntimeError(
            "E2E test discovery failed because one or more test modules did "
            "not import: " + "; ".join(import_errors)
        )

    return tests


def load_failed_names(results_file: str) -> List[str]:
    with open(results_file, "r", encoding="utf-8") as fh:
        data = json.load(fh)
    names = []
    for result in data.get("tests", []):
        if result.get("status") in ("FAIL", "ERROR"):
            name = result.get("name")
            if name:
                names.append(name)
    if not names:
        for result in data.get("failures", []):
            name = result.get("name")
            if name:
                names.append(name)
    return sorted(set(names))


def _module_matches_filter(test_filter: str, prefix: str, module_name: str) -> bool:
    parts = test_filter.split(".")
    if prefix:
        return parts[0] == prefix and (len(parts) < 2 or parts[1] == module_name)
    return parts[0] == module_name


def _function_matches_filter(test_filter: str, prefix: str, function_name: str) -> bool:
    parts = test_filter.split(".")
    function_part = None
    if prefix and len(parts) == 3:
        function_part = parts[2]
    elif not prefix and len(parts) == 2:
        function_part = parts[1]
    return function_part is None or function_name == function_part


def _qualified_name(prefix: str, module_name: str, function_name: str) -> str:
    if prefix:
        return f"{prefix}.{module_name}.{function_name}"
    return f"{module_name}.{function_name}"
