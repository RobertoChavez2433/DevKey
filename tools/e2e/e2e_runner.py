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
import pathlib
import sys
import time
import traceback

# WHY: Windows console default encoding is cp1252 which cannot encode Unicode
#      glyphs used in long-press expectations (e.g. ⌫ U+232B, ≠, α) or in test
#      names. Without this, any test whose label contains a non-ASCII char
#      crashes the whole runner loop via UnicodeEncodeError in print().
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
# WHY: pytest.skip() raises _pytest.outcomes.Skipped which subclasses
#      BaseException (verified on pytest 9.0.2 during tailor 2026-04-09),
#      NOT Exception. Without an explicit handler the whole runner loop
#      crashes on the first test that hits a skip branch.
# FROM SPEC: Phase 3 gate depends on graceful-skip handling in
#            test_voice.py, test_visual_diff.py, test_long_press.py,
#            test_clipboard.py, test_macros.py, test_command_mode.py,
#            test_plugins.py, test_modifier_combos.py.
# IMPORTANT: Import is lazy-guarded so the harness keeps running even on
#            a host where pytest is missing — prints a deprecation warning
#            instead of crashing.
try:
    from _pytest.outcomes import Skipped as _PytestSkipped
except ImportError:  # pragma: no cover — exercised only when pytest is absent
    _PytestSkipped = None
from typing import List, Tuple


def discover_tests(
    test_filter: str = None,
    feature: str = None,
) -> List[Tuple[str, callable]]:
    """
    Discover all test functions in the tests/ directory.

    Test functions must start with 'test_' and be in modules starting with 'test_'.
    Discovers flat files (tests/test_*.py) first, then one-level subdirectories
    (tests/*/test_*.py).

    Args:
        test_filter: Run a specific module or test (e.g. 'test_smoke' or
                     'test_smoke.test_keyboard_visible'). Mutually exclusive
                     with *feature*.
        feature:     Restrict discovery to tests/<feature>/test_*.py.

    Returns list of (fully_qualified_name, function) tuples.
    """
    tests_dir = pathlib.Path(__file__).parent / "tests"
    tests = []

    # Build ordered list of (import_path, prefix, module_short_name, Path).
    # prefix is "" for flat files, "subdir" for subdirectory files.
    candidates: list[tuple[str, str, str, pathlib.Path]] = []

    if feature:
        # Only look inside the requested feature subdirectory.
        for p in sorted(tests_dir.glob(f"{feature}/test_*.py")):
            subdir = p.parent.name
            module_name = p.stem
            import_path = f"tests.{subdir}.{module_name}"
            candidates.append((import_path, subdir, module_name, p))
    else:
        # 1. Flat files: tests/test_*.py
        for p in sorted(tests_dir.glob("test_*.py")):
            module_name = p.stem
            import_path = f"tests.{module_name}"
            candidates.append((import_path, "", module_name, p))

        # 2. Subdirectory files: tests/*/test_*.py
        for p in sorted(tests_dir.glob("*/test_*.py")):
            subdir = p.parent.name
            module_name = p.stem
            import_path = f"tests.{subdir}.{module_name}"
            candidates.append((import_path, subdir, module_name, p))

    for import_path, prefix, module_name, _ in candidates:
        # --test filter: supports "test_smoke" (flat only), or
        # "prediction.test_smoke" (subdirectory), or
        # "test_smoke.test_func" / "prediction.test_smoke.test_func".
        if test_filter:
            parts = test_filter.split(".")
            if prefix:
                # Subdirectory module: match "subdir.module" or "subdir.module.func"
                if parts[0] == prefix:
                    if len(parts) < 2 or parts[1] != module_name:
                        continue
                else:
                    continue
            else:
                # Flat module: match "module" or "module.func"
                if parts[0] != module_name:
                    continue

        try:
            module = importlib.import_module(import_path)
        except Exception as e:
            print(f"  ERROR importing {import_path}: {e}")
            continue

        for name, func in inspect.getmembers(module, inspect.isfunction):
            if not name.startswith("test_"):
                continue

            # Function-level filter
            if test_filter:
                parts = test_filter.split(".")
                func_part = None
                if prefix and len(parts) == 3:
                    func_part = parts[2]
                elif not prefix and len(parts) == 2:
                    func_part = parts[1]
                if func_part and name != func_part:
                    continue

            # Qualified name includes subdirectory prefix for disambiguation.
            if prefix:
                qualified_name = f"{prefix}.{module_name}.{name}"
            else:
                qualified_name = f"{module_name}.{name}"
            tests.append((qualified_name, func))

    return tests


def run_tests(tests: List[Tuple[str, callable]]) -> Tuple[int, int, int, int]:
    """
    Run all discovered tests and print results.

    Returns (passed, failed, errors, skipped) counts.
    """
    passed = 0
    failed = 0
    errors = 0
    skipped = 0

    total = len(tests)
    print(f"\nRunning {total} test(s)...\n")
    print("=" * 70)

    # Pre-test focus guard — lazy-import to avoid pulling the lib path before
    # test discovery patches sys.path. WHY: several tests (set_layout_mode,
    # symbols toggle, ABC return) cause the IME to temporarily hide. Without
    # a refocus hook between tests, later tests tap into nothing and produce
    # zero events. See lib/adb.py::ensure_keyboard_visible.
    try:
        from lib import adb as _adb_for_focus  # type: ignore
        _focus_serial = _adb_for_focus.get_device_serial()
    except Exception:
        _adb_for_focus = None
        _focus_serial = None

    for i, (name, func) in enumerate(tests, 1):
        print(f"  [{i}/{total}] {name} ... ", end="", flush=True)
        start = time.time()

        try:
            if _adb_for_focus is not None:
                try:
                    _adb_for_focus.ensure_keyboard_visible(_focus_serial)
                except Exception:
                    # Never fail a test because the refocus helper raised —
                    # tests that need a focused keyboard will still assert on it.
                    pass
            func()
            elapsed = time.time() - start
            print(f"PASS ({elapsed:.1f}s)")
            passed += 1
        except AssertionError as e:
            elapsed = time.time() - start
            print(f"FAIL ({elapsed:.1f}s)")
            print(f"         {e}")
            failed += 1
        except BaseException as e:
            # WHY: BaseException catches pytest.Skipped (which inherits from
            #      BaseException, NOT Exception) AND generic Exception errors
            #      in a single branch. We discriminate on type immediately
            #      below so skipped tests are not counted as errors.
            # IMPORTANT: Re-raise KeyboardInterrupt and SystemExit — those
            #            must not be swallowed by the harness.
            if isinstance(e, (KeyboardInterrupt, SystemExit)):
                raise
            elapsed = time.time() - start
            if _PytestSkipped is not None and isinstance(e, _PytestSkipped):
                # pytest.skip("reason") — reason is accessible via e.msg on
                # pytest 7+, or the first arg on older versions.
                reason = getattr(e, "msg", None) or (e.args[0] if e.args else "")
                print(f"SKIP ({elapsed:.1f}s)")
                if reason:
                    print(f"         {reason}")
                skipped += 1
            else:
                print(f"ERROR ({elapsed:.1f}s)")
                print(f"         {type(e).__name__}: {e}")
                if os.environ.get("DEVKEY_E2E_VERBOSE"):
                    traceback.print_exc()
                errors += 1

    print("=" * 70)
    print(f"\nResults: {passed} passed, {failed} failed, {errors} errors, "
          f"{skipped} skipped (out of {total} tests)")

    return passed, failed, errors, skipped


def main():
    parser = argparse.ArgumentParser(
        description="DevKey E2E Test Runner"
    )
    filter_group = parser.add_mutually_exclusive_group()
    filter_group.add_argument(
        "--test", "-t",
        help="Run specific test module or test (e.g., test_smoke or test_smoke.test_keyboard_visible)"
    )
    filter_group.add_argument(
        "--feature", "-f",
        help="Restrict discovery to tests/<feature>/test_*.py subdirectory"
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
    tests = discover_tests(test_filter=args.test, feature=args.feature)

    if not tests:
        print("No tests found.")
        if args.test:
            print(f"  Filter '{args.test}' matched nothing.")
        if args.feature:
            print(f"  Feature '{args.feature}' matched nothing.")
        sys.exit(1)

    if args.list:
        print(f"Discovered {len(tests)} test(s):")
        for name, _ in tests:
            print(f"  - {name}")
        sys.exit(0)

    # F7: fail-fast on missing driver + auto-enable HTTP forwarding.
    # Gated behind the list-early-exit above so `--list` works without a running driver
    # (matches the plan's own verification command `e2e_runner.py --list`).
    from lib import driver
    driver.require_driver()
    # WHY: The host-side driver URL (e.g. http://127.0.0.1:3950) is NOT reachable
    #      from inside the emulator. Android emulator uses 10.0.2.2 to reach the
    #      host's loopback. Translate automatically so callers don't need two vars.
    _driver_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    _ime_url = _driver_url.replace("127.0.0.1", "10.0.2.2").replace("localhost", "10.0.2.2")
    driver.broadcast(
        "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
        {"url": _ime_url},
    )
    # Warmup: wait for the debug_server_enabled event, then verify with a
    # round-trip pref broadcast. Retries absorb post-install connection lag.
    for attempt in range(5):
        try:
            driver.clear_logs()
            driver.broadcast(
                "dev.devkey.keyboard.SET_BOOL_PREF",
                {"key": "devkey_show_toolbar", "value": True},
            )
            driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
            break  # pipeline warm
        except Exception:
            if attempt < 4:
                time.sleep(2.0)
                # Re-send enable in case the first one was dropped
                driver.broadcast(
                    "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
                    {"url": _ime_url},
                )
    driver.clear_logs()

    # Run tests
    passed, failed, errors, skipped = run_tests(tests)

    # WHY: Skipped tests are NOT failures. Spec §3.5 visual-diff gate
    #      explicitly relies on SKIPs for uncaptured SwiftKey references
    #      (spec §4.4.1 "reference always wins over template"), and spec
    #      §3.2 voice gate allows SKIP on emulator without audio injection.
    # Exit with non-zero if any failures or errors — skipped tests count as green.
    sys.exit(1 if (failed + errors) > 0 else 0)


if __name__ == "__main__":
    main()
