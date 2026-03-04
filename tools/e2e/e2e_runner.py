#!/usr/bin/env python3
"""
DevKey E2E Test Runner

Discovers and runs all E2E tests in the tests/ directory.
Tests use ADB to interact with the keyboard on a connected device/emulator
and verify behavior via logcat assertions.

Usage:
    python e2e_runner.py                         # Run all tests
    python e2e_runner.py --test test_smoke        # Run specific test module
    python e2e_runner.py --test test_modes.test_symbols_mode_switch  # Run specific test
    python e2e_runner.py --device SERIAL          # Target specific device

Environment Variables:
    DEVKEY_DEVICE_SERIAL  - ADB device serial (alternative to --device flag)
"""

import argparse
import importlib
import inspect
import os
import sys
import time
import traceback
from typing import List, Tuple


def discover_tests(test_filter: str = None) -> List[Tuple[str, callable]]:
    """
    Discover all test functions in the tests/ directory.

    Test functions must start with 'test_' and be in modules starting with 'test_'.
    Returns list of (fully_qualified_name, function) tuples.
    """
    tests_dir = os.path.join(os.path.dirname(__file__), "tests")
    tests = []

    for filename in sorted(os.listdir(tests_dir)):
        if not filename.startswith("test_") or not filename.endswith(".py"):
            continue

        module_name = filename[:-3]  # strip .py

        # If filter specifies a module, skip non-matching
        if test_filter:
            filter_parts = test_filter.split(".")
            if filter_parts[0] != module_name:
                continue

        try:
            module = importlib.import_module(f"tests.{module_name}")
        except Exception as e:
            print(f"  ERROR importing tests.{module_name}: {e}")
            continue

        for name, func in inspect.getmembers(module, inspect.isfunction):
            if not name.startswith("test_"):
                continue

            # If filter specifies a function, skip non-matching
            if test_filter and "." in test_filter:
                filter_func = test_filter.split(".", 1)[1]
                if name != filter_func:
                    continue

            qualified_name = f"{module_name}.{name}"
            tests.append((qualified_name, func))

    return tests


def run_tests(tests: List[Tuple[str, callable]]) -> Tuple[int, int, int]:
    """
    Run all discovered tests and print results.

    Returns (passed, failed, errors) counts.
    """
    passed = 0
    failed = 0
    errors = 0

    total = len(tests)
    print(f"\nRunning {total} test(s)...\n")
    print("=" * 70)

    for i, (name, func) in enumerate(tests, 1):
        print(f"  [{i}/{total}] {name} ... ", end="", flush=True)
        start = time.time()

        try:
            func()
            elapsed = time.time() - start
            print(f"PASS ({elapsed:.1f}s)")
            passed += 1
        except AssertionError as e:
            elapsed = time.time() - start
            print(f"FAIL ({elapsed:.1f}s)")
            print(f"         {e}")
            failed += 1
        except Exception as e:
            elapsed = time.time() - start
            print(f"ERROR ({elapsed:.1f}s)")
            print(f"         {type(e).__name__}: {e}")
            if os.environ.get("DEVKEY_E2E_VERBOSE"):
                traceback.print_exc()
            errors += 1

    print("=" * 70)
    print(f"\nResults: {passed} passed, {failed} failed, {errors} errors "
          f"(out of {total} tests)")

    return passed, failed, errors


def main():
    parser = argparse.ArgumentParser(
        description="DevKey E2E Test Runner"
    )
    parser.add_argument(
        "--test", "-t",
        help="Run specific test module or test (e.g., test_smoke or test_smoke.test_keyboard_visible)"
    )
    parser.add_argument(
        "--device", "-d",
        help="ADB device serial (overrides DEVKEY_DEVICE_SERIAL env var)"
    )
    parser.add_argument(
        "--list", "-l",
        action="store_true",
        help="List all discovered tests without running them"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Show full tracebacks on errors"
    )
    args = parser.parse_args()

    # Set device serial
    if args.device:
        os.environ["DEVKEY_DEVICE_SERIAL"] = args.device

    if args.verbose:
        os.environ["DEVKEY_E2E_VERBOSE"] = "1"

    # Add the e2e directory to path so imports work
    e2e_dir = os.path.dirname(os.path.abspath(__file__))
    if e2e_dir not in sys.path:
        sys.path.insert(0, e2e_dir)

    # Discover tests
    tests = discover_tests(args.test)

    if not tests:
        print("No tests found.")
        if args.test:
            print(f"  Filter '{args.test}' matched nothing.")
        sys.exit(1)

    if args.list:
        print(f"Discovered {len(tests)} test(s):")
        for name, _ in tests:
            print(f"  - {name}")
        sys.exit(0)

    # Run tests
    passed, failed, errors = run_tests(tests)

    # Exit with non-zero if any failures
    sys.exit(1 if (failed + errors) > 0 else 0)


if __name__ == "__main__":
    main()
