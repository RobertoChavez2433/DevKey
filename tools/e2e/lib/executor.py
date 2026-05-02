"""
Per-test execution and watchdog support for the DevKey E2E runner.

The CLI runner should orchestrate suites, not own test reset, verification,
result shaping, child-process isolation, and timeout recovery.
"""
import importlib
import multiprocessing as mp
import os
import time
import traceback
from typing import Any, Dict, List, Optional, Tuple

try:
    from _pytest.outcomes import Skipped as _PytestSkipped
except ImportError:  # pragma: no cover - only when pytest is absent
    _PytestSkipped = None


def failure_category(error_type: str, message: str) -> str:
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


def serialize_error(e: BaseException) -> Tuple[str, str, str]:
    error_type = (
        "Skipped"
        if (_PytestSkipped is not None and isinstance(e, _PytestSkipped))
        else type(e).__name__
    )
    message = getattr(e, "msg", None) or (str(e) if str(e) else "")
    return error_type, message, failure_category(error_type, message)


def execute_test_inline(name: str, func: callable, retry_status: str) -> Dict[str, Any]:
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
        result = {
            "name": name,
            "status": "PASS",
            "duration_seconds": round(elapsed, 3),
            "retry_status": retry_status,
            "verification": _verify.summarize(verification_state) if _verify is not None else {},
        }
    except AssertionError as e:
        elapsed = time.time() - start
        if _verify is not None:
            verification_state = _verify.end()
        error_type, message, category = serialize_error(e)
        result = {
            "name": name,
            "status": "FAIL",
            "duration_seconds": round(elapsed, 3),
            "error_type": error_type,
            "error": message,
            "failure_category": category,
            "retry_status": retry_status,
            "verification": _verify.summarize(verification_state) if _verify is not None else {},
        }
    except BaseException as e:
        if isinstance(e, (KeyboardInterrupt, SystemExit)):
            raise
        elapsed = time.time() - start
        if _PytestSkipped is not None and isinstance(e, _PytestSkipped):
            if _verify is not None:
                verification_state = _verify.end()
            reason = getattr(e, "msg", None) or (e.args[0] if e.args else "")
            result = {
                "name": name,
                "status": "SKIP",
                "duration_seconds": round(elapsed, 3),
                "reason": reason,
                "failure_category": "skipped",
                "retry_status": retry_status,
                "verification": _verify.summarize(verification_state) if _verify is not None else {},
            }
        else:
            if os.environ.get("DEVKEY_E2E_VERBOSE"):
                traceback.print_exc()
            if _verify is not None:
                verification_state = _verify.end()
            error_type, message, category = serialize_error(e)
            result = {
                "name": name,
                "status": "ERROR",
                "duration_seconds": round(elapsed, 3),
                "error_type": error_type,
                "error": message,
                "failure_category": category,
                "retry_status": retry_status,
                "verification": _verify.summarize(verification_state) if _verify is not None else {},
            }
    else:
        if _verify is not None:
            _verify.end()
    return result


def _test_process_entry(
    qualified_name: str,
    module_name: str,
    function_name: str,
    retry_status: str,
    queue: Any,
) -> None:
    module = importlib.import_module(module_name)
    func = getattr(module, function_name)
    queue.put(execute_test_inline(qualified_name, func, retry_status))


def _timeout_result(name: str, retry_status: str, timeout_seconds: float) -> Dict[str, Any]:
    return {
        "name": name,
        "status": "ERROR",
        "duration_seconds": round(timeout_seconds, 3),
        "error_type": "TestTimeout",
        "error": f"test exceeded hard timeout of {timeout_seconds:g}s",
        "failure_category": "timeout",
        "retry_status": retry_status,
        "verification": {"verified": False, "evidence_count": 0, "action_count": 0, "evidence": []},
    }


def _child_exit_result(
    name: str,
    retry_status: str,
    exitcode: Optional[int],
    elapsed: float,
) -> Dict[str, Any]:
    return {
        "name": name,
        "status": "ERROR",
        "duration_seconds": round(elapsed, 3),
        "error_type": "ChildProcessError",
        "error": f"test child exited without a result; exitcode={exitcode}",
        "failure_category": "exception",
        "retry_status": retry_status,
        "verification": {"verified": False, "evidence_count": 0, "action_count": 0, "evidence": []},
    }


def _recover_after_timeout() -> None:
    try:
        from lib import adb, driver  # type: ignore

        serial = adb.get_device_serial()
        try:
            driver.clear_logs()
        except Exception:
            pass
        try:
            adb.reset_test_host_state(serial)
        except Exception:
            pass
    except Exception:
        pass


def run_test_with_timeout(
    name: str,
    func: callable,
    retry_status: str,
    timeout_seconds: float,
) -> Dict[str, Any]:
    if timeout_seconds <= 0:
        return execute_test_inline(name, func, retry_status)

    ctx = mp.get_context("spawn")
    queue = ctx.Queue(maxsize=1)
    process = ctx.Process(
        target=_test_process_entry,
        args=(name, func.__module__, func.__name__, retry_status, queue),
        daemon=True,
    )
    start = time.time()
    process.start()
    process.join(timeout_seconds)
    if process.is_alive():
        process.terminate()
        process.join(5)
        if process.is_alive():
            process.kill()
            process.join(5)
        _recover_after_timeout()
        return _timeout_result(name, retry_status, timeout_seconds)

    elapsed = time.time() - start
    try:
        return queue.get_nowait()
    except Exception:
        return _child_exit_result(name, retry_status, process.exitcode, elapsed)


def run_tests(
    tests: List[Tuple[str, callable]],
    *,
    retry_status: str,
    test_timeout_seconds: float,
) -> Tuple[int, int, int, int, List[Dict[str, Any]]]:
    """
    Run discovered tests and print result summaries.

    Returns (passed, failed, errors, skipped, per_test_results).
    """
    passed = 0
    failed = 0
    errors = 0
    skipped = 0
    results: List[Dict[str, Any]] = []

    total = len(tests)
    print(f"\nRunning {total} test(s)...")
    if test_timeout_seconds > 0:
        print(f"Hard timeout: {test_timeout_seconds:g}s per test")
    else:
        print("Hard timeout: disabled")
    print()
    print("=" * 70)

    for index, (name, func) in enumerate(tests, 1):
        print(f"  [{index}/{total}] {name} ... ", end="", flush=True)
        result = run_test_with_timeout(
            name,
            func,
            retry_status,
            test_timeout_seconds,
        )
        status = result.get("status")
        elapsed = float(result.get("duration_seconds", 0.0))
        results.append(result)

        if status == "PASS":
            print(f"PASS ({elapsed:.1f}s)")
            passed += 1
        elif status == "FAIL":
            print(f"FAIL ({elapsed:.1f}s)")
            if result.get("error"):
                print(f"         {result.get('error')}")
            failed += 1
        elif status == "SKIP":
            print(f"SKIP ({elapsed:.1f}s)")
            if result.get("reason"):
                print(f"         {result.get('reason')}")
            skipped += 1
        else:
            label = "TIMEOUT" if result.get("error_type") == "TestTimeout" else "ERROR"
            print(f"{label} ({elapsed:.1f}s)")
            print(f"         {result.get('error_type')}: {result.get('error')}")
            errors += 1

    print("=" * 70)
    print(f"\nResults: {passed} passed, {failed} failed, {errors} errors, "
          f"{skipped} skipped (out of {total} tests)")

    return passed, failed, errors, skipped, results
