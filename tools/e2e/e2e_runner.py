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
import json
import os
import pathlib
import subprocess
import sys
import time
import traceback
from datetime import datetime, timezone

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
from typing import Any, Dict, List, Optional, Tuple

LOCKED_FULL_SUITE_COUNT = 178
PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_RESULTS_DIR = PROJECT_ROOT / ".claude" / "test-results"
LONG_PRESS_EXPECTATIONS_DIR = PROJECT_ROOT / ".claude" / "test-flows" / "long-press-expectations"


def discover_tests(
    test_filter: str = None,
    feature: str = None,
    suite: str = "all",
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
        suite:       "all" runs both legacy flat tests and feature tests,
                     "legacy-flat" runs tests/test_*.py only, and "features"
                     runs tests/*/test_*.py only.

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
        if suite in ("all", "legacy-flat"):
            # 1. Flat files: tests/test_*.py
            for p in sorted(tests_dir.glob("test_*.py")):
                module_name = p.stem
                import_path = f"tests.{module_name}"
                candidates.append((import_path, "", module_name, p))

        if suite in ("all", "features"):
            # 2. Subdirectory files: tests/*/test_*.py
            for p in sorted(tests_dir.glob("*/test_*.py")):
                subdir = p.parent.name
                module_name = p.stem
                import_path = f"tests.{subdir}.{module_name}"
                candidates.append((import_path, subdir, module_name, p))

    import_errors: list[str] = []
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
            import_errors.append(f"{import_path}: {e}")
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

    if import_errors:
        raise RuntimeError(
            "E2E test discovery failed because one or more test modules did "
            "not import: " + "; ".join(import_errors)
        )

    return tests


def _run_adb(args: List[str], serial: Optional[str] = None, timeout: float = 10.0) -> subprocess.CompletedProcess:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )


def _first_nonblank(value: str) -> str:
    for line in value.splitlines():
        if line.strip():
            return line.strip()
    return ""


def _device_metadata(serial: Optional[str]) -> Dict[str, Any]:
    def prop(name: str) -> str:
        try:
            return _run_adb(["shell", "getprop", name], serial).stdout.strip()
        except Exception:
            return ""

    version_name = ""
    version_code = ""
    try:
        pkg = _run_adb(["shell", "dumpsys", "package", "dev.devkey.keyboard"], serial, timeout=15.0).stdout
        for line in pkg.splitlines():
            line = line.strip()
            if line.startswith("versionName="):
                version_name = line.split("=", 1)[1]
            elif "versionCode=" in line:
                version_code = line.split("versionCode=", 1)[1].split()[0]
    except Exception:
        pass

    return {
        "serial": serial or os.environ.get("DEVKEY_DEVICE_SERIAL") or "(default)",
        "model": prop("ro.product.model"),
        "manufacturer": prop("ro.product.manufacturer"),
        "sdk": prop("ro.build.version.sdk"),
        "release": prop("ro.build.version.release"),
        "app_version_name": version_name,
        "app_version_code": version_code,
    }


def _local_voice_assets() -> Dict[str, bool]:
    assets_dir = PROJECT_ROOT / "app" / "src" / "main" / "assets"
    return {
        "whisper-tiny.en.tflite": (assets_dir / "whisper-tiny.en.tflite").is_file(),
        "filters_vocab_en.bin": (assets_dir / "filters_vocab_en.bin").is_file(),
    }


def run_preflight(serial: Optional[str]) -> Dict[str, Any]:
    from lib import adb, driver, keyboard

    checks: Dict[str, Any] = {}

    boot = _run_adb(["shell", "getprop", "sys.boot_completed"], serial)
    checks["device_booted"] = {
        "ok": boot.returncode == 0 and boot.stdout.strip() == "1",
        "value": boot.stdout.strip(),
    }

    installed = _run_adb(["shell", "pm", "list", "packages", "dev.devkey.keyboard"], serial)
    checks["ime_installed"] = {
        "ok": installed.returncode == 0 and "dev.devkey.keyboard" in installed.stdout,
    }

    default_ime = _run_adb(["shell", "settings", "get", "secure", "default_input_method"], serial)
    checks["ime_default"] = {
        "ok": "dev.devkey.keyboard" in default_ime.stdout,
        "value": _first_nonblank(default_ime.stdout),
    }

    try:
        health = driver.health()
        checks["debug_server"] = {
            "ok": health.get("status") == "ok",
            "entries": health.get("entries"),
            "url": driver.DRIVER_URL,
        }
    except Exception as e:
        checks["debug_server"] = {
            "ok": False,
            "url": driver.DRIVER_URL,
            "error": str(e),
        }

    focus_status = adb.ensure_keyboard_visible(serial)
    checks["keyboard_visible"] = {"ok": bool(focus_status.get("visible")), **focus_status}
    checks["audio_permission"] = {
        "ok": bool(focus_status.get("permissions", {}).get("RECORD_AUDIO")),
        "permission": "RECORD_AUDIO",
    }

    try:
        clean_state = adb.reset_test_host_state(serial)
        checks["clean_test_state"] = {
            "ok": (
                bool(clean_state.get("visible"))
                and clean_state.get("text_length") == 0
            ),
            "text_length": clean_state.get("text_length"),
            "keyboard_visible": bool(clean_state.get("visible")),
        }
    except Exception as e:
        checks["clean_test_state"] = {
            "ok": False,
            "error": f"{type(e).__name__}: {e}",
        }

    try:
        keyboard.set_layout_mode("full", serial)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        visual_path = DEFAULT_RESULTS_DIR / f"visual-baseline-{stamp}.png"
        checks["visual_baseline"] = adb.capture_visual_baseline(serial, visual_path)
    except Exception as e:
        checks["visual_baseline"] = {
            "ok": False,
            "error": f"{type(e).__name__}: {e}",
        }

    try:
        key_map = keyboard.load_key_map(serial)
        key_map_size = len(key_map)
        checks["key_map"] = {
            "ok": key_map_size > 10,
            "size": key_map_size,
            "symbols_size": len(keyboard.get_symbols_key_map()),
        }
        checks["keyboard_visible"]["key_map_size"] = key_map_size
    except Exception as e:
        checks["key_map"] = {
            "ok": False,
            "size": 0,
            "error": f"{type(e).__name__}: {e}",
        }

    checks["voice_assets"] = {
        "ok": all(_local_voice_assets().values()),
        "assets": _local_voice_assets(),
    }
    checks["reset_strategy"] = {
        "ok": True,
        "strategy": (
            "TestHostActivity focus + text clear + normal keyboard mode + "
            "RESET_CIRCUIT_BREAKER; no force-stop"
        ),
    }

    ok = all(bool(value.get("ok")) for value in checks.values())
    return {
        "ok": ok,
        "checks": checks,
        "device": _device_metadata(serial),
    }


def _failure_category(error_type: str, message: str) -> str:
    text = f"{error_type} {message}".lower()
    if "drivertimeout" in text or "timeout" in text:
        return "timeout"
    if "keyerror" in text or "key map" in text or "not found in key map" in text:
        return "key_map"
    if "driver" in text:
        return "driver"
    if error_type == "AssertionError":
        return "assertion"
    if error_type == "Skipped":
        return "skipped"
    return "exception"


def _serialize_error(e: BaseException) -> Tuple[str, str, str]:
    error_type = "Skipped" if (_PytestSkipped is not None and isinstance(e, _PytestSkipped)) else type(e).__name__
    message = getattr(e, "msg", None) or (str(e) if str(e) else "")
    return error_type, message, _failure_category(error_type, message)


def _load_failed_names(results_file: str) -> List[str]:
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


def _write_results(payload: Dict[str, Any], explicit_path: Optional[str] = None) -> pathlib.Path:
    if explicit_path:
        path = pathlib.Path(explicit_path)
    else:
        DEFAULT_RESULTS_DIR.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        path = DEFAULT_RESULTS_DIR / f"e2e-results-{stamp}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    return path


def _write_inventory(payload: Dict[str, Any], explicit_path: Optional[str] = None) -> pathlib.Path:
    if explicit_path:
        path = pathlib.Path(explicit_path)
    else:
        DEFAULT_RESULTS_DIR.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        path = DEFAULT_RESULTS_DIR / f"key-inventory-{stamp}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    return path


def _enable_debug_forwarding(driver_module: Any, serial: Optional[str] = None) -> None:
    from lib import adb

    _driver_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    _ime_url = adb.configure_debug_server_forwarding(serial, _driver_url)
    driver_module.broadcast(
        "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
        {"url": _ime_url},
    )


def generate_inventory_payload(serial: Optional[str], preflight: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    from lib import keyboard

    started_at = datetime.now(timezone.utc)
    inventory = keyboard.generate_canonical_inventory(serial)
    action_inventory = generate_action_inventory(inventory)
    ended_at = datetime.now(timezone.utc)
    inventory.update({
        "started_at": started_at.isoformat(),
        "ended_at": ended_at.isoformat(),
        "duration_seconds": round((ended_at - started_at).total_seconds(), 3),
        "device": _device_metadata(serial),
        "preflight": preflight,
        "action_inventory": action_inventory,
    })
    return inventory


def _visible_labels_for_action_mode(inventory: Dict[str, Any], mode: str) -> set:
    target_layout = "full" if mode == "symbols" else mode
    target_layer = "symbols" if mode == "symbols" else "normal"
    for layout in inventory.get("layouts", []):
        if layout.get("layout") != target_layout:
            continue
        return {
            record.get("label")
            for record in layout.get("layers", {}).get(target_layer, [])
            if record.get("label")
        }
    return set()


def generate_action_inventory(inventory: Dict[str, Any]) -> Dict[str, Any]:
    expected_modes = ("full", "compact", "compact_dev", "symbols")
    modes: Dict[str, Any] = {}
    failures = []
    total_keys = 0
    total_actions = 0

    for mode in expected_modes:
        path = LONG_PRESS_EXPECTATIONS_DIR / f"{mode}.json"
        visible_labels = _visible_labels_for_action_mode(inventory, mode)
        if not path.exists():
            failures.append(f"{mode}: long-press expectation file missing")
            modes[mode] = {
                "source": str(path.relative_to(PROJECT_ROOT)),
                "keys_with_actions": 0,
                "configured_actions": 0,
                "actions": [],
            }
            continue

        try:
            entries = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            failures.append(f"{mode}: invalid long-press expectation JSON: {exc}")
            entries = []

        actions = []
        for index, entry in enumerate(entries):
            label = entry.get("label") if isinstance(entry, dict) else None
            lp_codes = entry.get("lp_codes") if isinstance(entry, dict) else None
            record_id = f"{mode}[{index}]"

            if not isinstance(label, str) or not label:
                failures.append(f"{record_id}: label is missing")
                label = ""
            if not isinstance(lp_codes, list) or not all(isinstance(code, int) for code in lp_codes):
                failures.append(f"{record_id}: lp_codes must be integer list")
                lp_codes = []
            if label and label not in visible_labels:
                failures.append(f"{record_id}: label {label!r} is not visible in inventory")

            total_keys += 1
            total_actions += len(lp_codes)
            actions.append({
                "label": label,
                "lp_codes": lp_codes,
                "action_count": len(lp_codes),
                "visible": bool(label and label in visible_labels),
            })

        modes[mode] = {
            "source": str(path.relative_to(PROJECT_ROOT)),
            "keys_with_actions": len(actions),
            "configured_actions": sum(action["action_count"] for action in actions),
            "actions": actions,
        }

    return {
        "schema_version": 1,
        "coverage": {
            "ok": not failures,
            "tested_modes": len(expected_modes),
            "keys_with_actions": total_keys,
            "configured_actions": total_actions,
            "failures": failures,
        },
        "long_press": {
            "modes": modes,
        },
    }


def run_tests(tests: List[Tuple[str, callable]], retry_status: str = "not_retried") -> Tuple[int, int, int, int, List[Dict[str, Any]]]:
    """
    Run all discovered tests and print results.

    Returns (passed, failed, errors, skipped, per_test_results).
    """
    passed = 0
    failed = 0
    errors = 0
    skipped = 0
    results: List[Dict[str, Any]] = []

    total = len(tests)
    print(f"\nRunning {total} test(s)...\n")
    print("=" * 70)

    # Pre-test clean-state guard — lazy-import to avoid pulling the lib path before
    # test discovery patches sys.path. WHY: several tests (set_layout_mode,
    # symbols toggle, ABC return) cause the IME to temporarily hide. Without
    # a reset hook between tests, later tests can inherit stale text, the wrong
    # keyboard mode, or no focused IME and then produce false positives.
    # See lib/adb.py::reset_test_host_state.
    try:
        from lib import adb as _adb_for_focus  # type: ignore
        from lib import driver as _driver_for_logs  # type: ignore
        from lib import verify as _verify  # type: ignore
        _focus_serial = _adb_for_focus.get_device_serial()
    except Exception:
        _adb_for_focus = None
        _driver_for_logs = None
        _verify = None
        _focus_serial = None

    for i, (name, func) in enumerate(tests, 1):
        print(f"  [{i}/{total}] {name} ... ", end="", flush=True)
        start = time.time()

        verification_state: Dict[str, Any] = {"actions": [], "evidence": []}
        try:
            if _adb_for_focus is not None:
                _adb_for_focus.reset_test_host_state(_focus_serial)
                _adb_for_focus.clear_logcat(_focus_serial)
            if _driver_for_logs is not None:
                _driver_for_logs.clear_logs()
            if _verify is not None:
                _verify.begin(name)
            func()
            if _verify is not None:
                verification_state = _verify.assert_verified()
            elapsed = time.time() - start
            print(f"PASS ({elapsed:.1f}s)")
            passed += 1
            results.append({
                "name": name,
                "status": "PASS",
                "duration_seconds": round(elapsed, 3),
                "retry_status": retry_status,
                "verification": _verify.summarize(verification_state) if _verify is not None else {},
            })
        except AssertionError as e:
            elapsed = time.time() - start
            print(f"FAIL ({elapsed:.1f}s)")
            print(f"         {e}")
            if _verify is not None:
                verification_state = _verify.end()
            failed += 1
            error_type, message, category = _serialize_error(e)
            results.append({
                "name": name,
                "status": "FAIL",
                "duration_seconds": round(elapsed, 3),
                "error_type": error_type,
                "error": message,
                "failure_category": category,
                "retry_status": retry_status,
                "verification": _verify.summarize(verification_state) if _verify is not None else {},
            })
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
                if _verify is not None:
                    verification_state = _verify.end()
                # pytest.skip("reason") — reason is accessible via e.msg on
                # pytest 7+, or the first arg on older versions.
                reason = getattr(e, "msg", None) or (e.args[0] if e.args else "")
                print(f"SKIP ({elapsed:.1f}s)")
                if reason:
                    print(f"         {reason}")
                skipped += 1
                results.append({
                    "name": name,
                    "status": "SKIP",
                    "duration_seconds": round(elapsed, 3),
                    "reason": reason,
                    "failure_category": "skipped",
                    "retry_status": retry_status,
                    "verification": _verify.summarize(verification_state) if _verify is not None else {},
                })
            else:
                print(f"ERROR ({elapsed:.1f}s)")
                print(f"         {type(e).__name__}: {e}")
                if os.environ.get("DEVKEY_E2E_VERBOSE"):
                    traceback.print_exc()
                if _verify is not None:
                    verification_state = _verify.end()
                errors += 1
                error_type, message, category = _serialize_error(e)
                results.append({
                    "name": name,
                    "status": "ERROR",
                    "duration_seconds": round(elapsed, 3),
                    "error_type": error_type,
                    "error": message,
                    "failure_category": category,
                    "retry_status": retry_status,
                    "verification": _verify.summarize(verification_state) if _verify is not None else {},
                })
        else:
            if _verify is not None:
                _verify.end()

    print("=" * 70)
    print(f"\nResults: {passed} passed, {failed} failed, {errors} errors, "
          f"{skipped} skipped (out of {total} tests)")

    return passed, failed, errors, skipped, results


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
        help="Multiply driver wait_for timeouts for slow emulators"
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
    args = parser.parse_args()

    # Set device serial
    if args.device:
        os.environ["DEVKEY_DEVICE_SERIAL"] = args.device

    if args.verbose:
        os.environ["DEVKEY_E2E_VERBOSE"] = "1"

    os.environ["DEVKEY_TIMEOUT_MULTIPLIER"] = str(args.timeout_multiplier)

    # Add the e2e directory to path so imports work
    e2e_dir = os.path.dirname(os.path.abspath(__file__))
    if e2e_dir not in sys.path:
        sys.path.insert(0, e2e_dir)

    serial = os.environ.get("DEVKEY_DEVICE_SERIAL")

    if args.preflight:
        preflight = run_preflight(serial)
        print(json.dumps(preflight, indent=2, sort_keys=True))
        sys.exit(0 if preflight.get("ok") else 1)

    if args.dump_inventory:
        from lib import driver

        driver.require_driver()
        _enable_debug_forwarding(driver, serial)
        preflight = None
        if not args.no_preflight:
            preflight = run_preflight(serial)
            if not preflight.get("ok"):
                print(json.dumps(preflight, indent=2, sort_keys=True))
                print("Preflight failed; aborting inventory dump.")
                sys.exit(1)
        payload = generate_inventory_payload(serial, preflight)
        inventory_path = _write_inventory(payload, args.inventory_file)
        print(f"Inventory JSON: {inventory_path}")
        coverage = payload.get("coverage", {})
        if not coverage.get("ok", False):
            for failure in coverage.get("failures", [])[:20]:
                print(f"Inventory coverage failure: {failure}")
            sys.exit(1)
        action_coverage = payload.get("action_inventory", {}).get("coverage", {})
        if not action_coverage.get("ok", False):
            for failure in action_coverage.get("failures", [])[:20]:
                print(f"Action inventory coverage failure: {failure}")
            sys.exit(1)
        sys.exit(0)

    # Discover tests
    tests = discover_tests(test_filter=args.test, feature=args.feature, suite=args.suite)

    if args.rerun_failed:
        failed_names = set(_load_failed_names(args.rerun_failed))
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
    _enable_debug_forwarding(driver, serial)
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
                _enable_debug_forwarding(driver, serial)
    driver.clear_logs()

    preflight = None
    if not args.no_preflight:
        preflight = run_preflight(serial)
        if not preflight.get("ok"):
            print(json.dumps(preflight, indent=2, sort_keys=True))
            print("Preflight failed; aborting test run.")
            sys.exit(1)

    # Run tests
    started_at = datetime.now(timezone.utc)
    passed, failed, errors, skipped, test_results = run_tests(
        tests,
        retry_status="retry" if args.rerun_failed else "not_retried",
    )
    ended_at = datetime.now(timezone.utc)
    result_payload = {
        "schema_version": 1,
        "started_at": started_at.isoformat(),
        "ended_at": ended_at.isoformat(),
        "duration_seconds": round((ended_at - started_at).total_seconds(), 3),
        "device": _device_metadata(serial),
        "suite": args.suite,
        "feature": args.feature,
        "test_filter": args.test,
        "rerun_source": args.rerun_failed,
        "timeout_multiplier": args.timeout_multiplier,
        "preflight": preflight,
        "total": len(tests),
        "passed": passed,
        "failed": failed,
        "errors": errors,
        "skipped": skipped,
        "tests": test_results,
        "failures": [r for r in test_results if r.get("status") in ("FAIL", "ERROR")],
        "artifacts": [],
        "privacy": {
            "typed_text_logged": False,
            "transcripts_logged": False,
            "clipboard_logged": False,
        },
    }
    results_path = _write_results(result_payload, args.results_file)
    print(f"JSON results: {results_path}")

    # WHY: Skipped tests are NOT failures. Spec §3.5 visual-diff gate
    #      explicitly relies on SKIPs for uncaptured SwiftKey references
    #      (spec §4.4.1 "reference always wins over template"), and spec
    #      §3.2 voice gate allows SKIP on emulator without audio injection.
    # Exit with non-zero if any failures or errors — skipped tests count as green.
    sys.exit(1 if (failed + errors) > 0 else 0)


if __name__ == "__main__":
    main()
