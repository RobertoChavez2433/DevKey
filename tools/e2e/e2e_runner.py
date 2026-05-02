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
import json
import os
import sys
from datetime import datetime, timezone

# WHY: Windows console default encoding is cp1252 which cannot encode Unicode
#      glyphs used in long-press expectations (e.g. ⌫ U+232B, ≠, α) or in test
#      names. Without this, any test whose label contains a non-ASCII char
#      crashes the whole runner loop via UnicodeEncodeError in print().
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from lib.discovery import LOCKED_FULL_SUITE_COUNT, discover_tests, load_failed_names
from lib.driver_setup import require_driver_with_forwarding, warm_debug_pipeline
from lib.executor import run_tests
from lib.inventory import generate_inventory_payload
from lib.preflight import run_preflight
from lib.results import build_result_payload, write_inventory, write_results
from lib.run_lock import RunLockError, device_run_lock

DEFAULT_TEST_TIMEOUT_SECONDS = float(os.environ.get("DEVKEY_E2E_TEST_TIMEOUT_SECONDS", "90"))


def build_parser() -> argparse.ArgumentParser:
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
    filter_group.add_argument(
        "--rerun-failed",
        help="Read a prior JSON results file and rerun only failed/error tests"
    )
    parser.add_argument(
        "--device", "-d",
        help="ADB device serial (overrides DEVKEY_DEVICE_SERIAL env var)"
    )
    parser.add_argument(
        "--suite",
        choices=("all", "features", "legacy-flat"),
        default="all",
        help="Discovery suite to run when --test/--feature are not used"
    )
    parser.add_argument(
        "--list", "-l",
        action="store_true",
        help="List all discovered tests without running them"
    )
    parser.add_argument(
        "--preflight",
        action="store_true",
        help="Run preflight checks and exit"
    )
    parser.add_argument(
        "--no-preflight",
        action="store_true",
        help="Skip the default preflight before running tests"
    )
    parser.add_argument(
        "--timeout-multiplier",
        type=float,
        default=1.0,
        help="Multiply driver wait_for timeouts for slow devices"
    )
    parser.add_argument(
        "--test-timeout-seconds",
        type=float,
        default=DEFAULT_TEST_TIMEOUT_SECONDS,
        help=(
            "Hard wall-clock timeout per test before the runner terminates the "
            "test child and records TestTimeout (default: "
            f"{DEFAULT_TEST_TIMEOUT_SECONDS:g}; set 0 to disable)"
        ),
    )
    parser.add_argument(
        "--results-file",
        help="Path for JSON results output (default: .claude/test-results/e2e-results-<timestamp>.json)"
    )
    parser.add_argument(
        "--dump-inventory",
        action="store_true",
        help="Generate canonical key/button inventory JSON for every layout and layer, then exit"
    )
    parser.add_argument(
        "--inventory-file",
        help="Path for inventory JSON output (default: .claude/test-results/key-inventory-<timestamp>.json)"
    )
    parser.add_argument(
        "--allow-count-drift",
        action="store_true",
        help=f"Allow --suite all discovery to differ from locked count {LOCKED_FULL_SUITE_COUNT}"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Show full tracebacks on errors"
    )
    return parser


def configure_environment(args: argparse.Namespace) -> str | None:
    if args.device:
        os.environ["DEVKEY_DEVICE_SERIAL"] = args.device

    if args.verbose:
        os.environ["DEVKEY_E2E_VERBOSE"] = "1"

    os.environ["DEVKEY_TIMEOUT_MULTIPLIER"] = str(args.timeout_multiplier)

    # Add the e2e directory to path so imports work
    e2e_dir = os.path.dirname(os.path.abspath(__file__))
    if e2e_dir not in sys.path:
        sys.path.insert(0, e2e_dir)

    return os.environ.get("DEVKEY_DEVICE_SERIAL")


def run_preflight_command(serial: str | None) -> int:
    with device_run_lock(serial, "preflight"):
        preflight = run_preflight(serial)
    print(json.dumps(preflight, indent=2, sort_keys=True))
    return 0 if preflight.get("ok") else 1


def run_inventory_command(args: argparse.Namespace, serial: str | None) -> int:
    with device_run_lock(serial, "inventory"):
        require_driver_with_forwarding(serial)
        preflight = None
        if not args.no_preflight:
            preflight = run_preflight(serial)
            if not preflight.get("ok"):
                print(json.dumps(preflight, indent=2, sort_keys=True))
                print("Preflight failed; aborting inventory dump.")
                return 1
        payload = generate_inventory_payload(serial, preflight)
        inventory_path = write_inventory(payload, args.inventory_file)

    print(f"Inventory JSON: {inventory_path}")
    coverage = payload.get("coverage", {})
    if not coverage.get("ok", False):
        for failure in coverage.get("failures", [])[:20]:
            print(f"Inventory coverage failure: {failure}")
        return 1

    action_coverage = payload.get("action_inventory", {}).get("coverage", {})
    if not action_coverage.get("ok", False):
        for failure in action_coverage.get("failures", [])[:20]:
            print(f"Action inventory coverage failure: {failure}")
        return 1
    return 0


def select_tests(args: argparse.Namespace):
    tests = discover_tests(test_filter=args.test, feature=args.feature, suite=args.suite)

    if args.rerun_failed:
        failed_names = set(load_failed_names(args.rerun_failed))
        tests = [(name, func) for name, func in tests if name in failed_names]

    if not tests:
        print("No tests found.")
        if args.test:
            print(f"  Filter '{args.test}' matched nothing.")
        if args.feature:
            print(f"  Feature '{args.feature}' matched nothing.")
        if args.rerun_failed:
            print(f"  No failed/error tests found in '{args.rerun_failed}'.")
        sys.exit(1)

    if (
        not args.test
        and not args.feature
        and not args.rerun_failed
        and args.suite == "all"
        and len(tests) != LOCKED_FULL_SUITE_COUNT
        and not args.allow_count_drift
    ):
        print(
            f"Discovered {len(tests)} tests, expected locked full-suite count "
            f"{LOCKED_FULL_SUITE_COUNT}. Use --allow-count-drift if this change is intentional."
        )
        sys.exit(1)

    return tests


def print_test_list(tests) -> None:
    print(f"Discovered {len(tests)} test(s):")
    for name, _ in tests:
        print(f"  - {name}")


def run_test_command(args: argparse.Namespace, serial: str | None, tests) -> int:
    with device_run_lock(serial, "test-run"):
        warm_debug_pipeline(serial)

        preflight = None
        if not args.no_preflight:
            preflight = run_preflight(serial)
            if not preflight.get("ok"):
                print(json.dumps(preflight, indent=2, sort_keys=True))
                print("Preflight failed; aborting test run.")
                return 1

        started_at = datetime.now(timezone.utc)
        passed, failed, errors, skipped, test_results = run_tests(
            tests,
            retry_status="retry" if args.rerun_failed else "not_retried",
            test_timeout_seconds=args.test_timeout_seconds,
        )
        ended_at = datetime.now(timezone.utc)
        result_payload = build_result_payload(
            started_at,
            ended_at,
            serial=serial,
            suite=args.suite,
            feature=args.feature,
            test_filter=args.test,
            rerun_source=args.rerun_failed,
            timeout_multiplier=args.timeout_multiplier,
            test_timeout_seconds=args.test_timeout_seconds,
            preflight=preflight,
            passed=passed,
            failed=failed,
            errors=errors,
            skipped=skipped,
            test_results=test_results,
        )
        results_path = write_results(result_payload, args.results_file)

    print(f"JSON results: {results_path}")

    # Skipped tests are not failures. Voice and visual reference gaps may skip,
    # but assertions, timeouts, and child-process errors fail the run.
    return 1 if (failed + errors) > 0 else 0


def main() -> None:
    args = build_parser().parse_args()
    serial = configure_environment(args)

    try:
        if args.preflight:
            sys.exit(run_preflight_command(serial))
        if args.dump_inventory:
            sys.exit(run_inventory_command(args, serial))
    except RunLockError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)

    tests = select_tests(args)

    if args.list:
        print_test_list(tests)
        sys.exit(0)

    try:
        sys.exit(run_test_command(args, serial, tests))
    except RunLockError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
