"""Test discovery and rerun selection for the DevKey E2E runner."""
import importlib
import inspect
import json
from typing import Any, Callable, Iterable, List, Optional, Tuple

from .paths import TESTS_DIR

LOCKED_FULL_SUITE_COUNT = 181
DiscoveredTest = Tuple[str, Callable[..., Any]]
Candidate = Tuple[str, str, str]


class DiscoveryError(RuntimeError):
    pass


def select_tests(
    *,
    test_filter: Optional[str],
    feature: Optional[str],
    suite: str,
    rerun_failed: Optional[str],
    allow_count_drift: bool,
) -> List[DiscoveredTest]:
    tests = discover_tests(test_filter=test_filter, feature=feature, suite=suite)
    if rerun_failed:
        tests = _filter_failed_tests(tests, rerun_failed)

    if not tests:
        raise DiscoveryError(_no_tests_message(test_filter, feature, rerun_failed))

    if _has_locked_count_drift(test_filter, feature, suite, rerun_failed, allow_count_drift, tests):
        raise DiscoveryError(
            f"Discovered {len(tests)} tests, expected locked full-suite count "
            f"{LOCKED_FULL_SUITE_COUNT}. Use --allow-count-drift if this change is intentional."
        )

    return tests


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
    import_errors: List[str] = []
    tests: List[DiscoveredTest] = []

    for candidate in _iter_candidates(feature, suite):
        module_tests, import_error = _discover_module_tests(candidate, test_filter)
        tests.extend(module_tests)
        if import_error:
            import_errors.append(import_error)

    if import_errors:
        raise RuntimeError(
            "E2E test discovery failed because one or more test modules did "
            "not import: " + "; ".join(import_errors)
        )

    return tests


def _iter_candidates(feature: Optional[str], suite: str) -> Iterable[Candidate]:
    if feature:
        for path in sorted(TESTS_DIR.glob(f"{feature}/test_*.py")):
            subdir = path.parent.name
            module_name = path.stem
            yield f"tests.{subdir}.{module_name}", subdir, module_name
        return

    if suite in ("all", "legacy-flat"):
        for path in sorted(TESTS_DIR.glob("test_*.py")):
            module_name = path.stem
            yield f"tests.{module_name}", "", module_name

    if suite in ("all", "features"):
        for path in sorted(TESTS_DIR.glob("*/test_*.py")):
            subdir = path.parent.name
            module_name = path.stem
            yield f"tests.{subdir}.{module_name}", subdir, module_name


def _discover_module_tests(
    candidate: Candidate,
    test_filter: Optional[str],
) -> Tuple[List[DiscoveredTest], Optional[str]]:
    import_path, prefix, module_name = candidate
    if test_filter and not _module_matches_filter(test_filter, prefix, module_name):
        return [], None

    try:
        module = importlib.import_module(import_path)
    except Exception as exc:
        print(f"  ERROR importing {import_path}: {exc}")
        return [], f"{import_path}: {exc}"

    tests = [
        (_qualified_name(prefix, module_name, name), func)
        for name, func in inspect.getmembers(module, inspect.isfunction)
        if _is_selected_test_function(name, prefix, test_filter)
    ]
    return tests, None


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


def _filter_failed_tests(tests: List[DiscoveredTest], results_file: str) -> List[DiscoveredTest]:
    failed_names = set(load_failed_names(results_file))
    return [(name, func) for name, func in tests if name in failed_names]


def _no_tests_message(
    test_filter: Optional[str],
    feature: Optional[str],
    rerun_failed: Optional[str],
) -> str:
    lines = ["No tests found."]
    if test_filter:
        lines.append(f"  Filter '{test_filter}' matched nothing.")
    if feature:
        lines.append(f"  Feature '{feature}' matched nothing.")
    if rerun_failed:
        lines.append(f"  No failed/error tests found in '{rerun_failed}'.")
    return "\n".join(lines)


def _has_locked_count_drift(
    test_filter: Optional[str],
    feature: Optional[str],
    suite: str,
    rerun_failed: Optional[str],
    allow_count_drift: bool,
    tests: List[DiscoveredTest],
) -> bool:
    return (
        not test_filter
        and not feature
        and not rerun_failed
        and suite == "all"
        and len(tests) != LOCKED_FULL_SUITE_COUNT
        and not allow_count_drift
    )


def _is_selected_test_function(name: str, prefix: str, test_filter: Optional[str]) -> bool:
    if not name.startswith("test_"):
        return False
    return not test_filter or _function_matches_filter(test_filter, prefix, name)


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
