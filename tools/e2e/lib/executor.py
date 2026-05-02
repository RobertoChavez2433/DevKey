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
    adb_module, driver_module, verify_module, focus_serial = _load_child_helpers()
    start = time.time()
    verification_state: Dict[str, Any] = {"actions": [], "evidence": []}

    try:
        _prepare_test_process(adb_module, driver_module, verify_module, focus_serial, name)
        func()
        if verify_module is not None:
            verification_state = verify_module.assert_verified()
        result = _pass_result(name, retry_status, start, verify_module, verification_state)
        _end_verification(verify_module)
        return result
    except AssertionError as exc:
        verification_state = _end_verification(verify_module)
        return _failure_result(name, retry_status, start, verify_module, verification_state, exc)
    except BaseException as exc:
        if isinstance(exc, (KeyboardInterrupt, SystemExit)):
            raise
        verification_state = _end_verification(verify_module)
        if _is_pytest_skip(exc):
            return _skip_result(name, retry_status, start, verify_module, verification_state, exc)
        if os.environ.get("DEVKEY_E2E_VERBOSE"):
            traceback.print_exc()
        return _error_result(name, retry_status, start, verify_module, verification_state, exc)


def _load_child_helpers() -> Tuple[Any, Any, Any, Optional[str]]:
    try:
        from lib import adb, driver, verify  # type: ignore

        return adb, driver, verify, adb.get_device_serial()
    except Exception:
        return None, None, None, None


def _prepare_test_process(
    adb_module: Any,
    driver_module: Any,
    verify_module: Any,
    focus_serial: Optional[str],
    name: str,
) -> None:
    if adb_module is not None:
        adb_module.reset_test_host_state(focus_serial)
        adb_module.clear_logcat(focus_serial)
    if driver_module is not None:
        driver_module.clear_logs()
    if verify_module is not None:
        verify_module.begin(name)


def _end_verification(verify_module: Any) -> Dict[str, Any]:
    if verify_module is None:
        return {"actions": [], "evidence": []}
    return verify_module.end()


def _verification_summary(verify_module: Any, state: Dict[str, Any]) -> Dict[str, Any]:
    if verify_module is None:
        return {}
    return verify_module.summarize(state)


def _pass_result(
    name: str,
    retry_status: str,
    start: float,
    verify_module: Any,
    verification_state: Dict[str, Any],
) -> Dict[str, Any]:
    return {
        "name": name,
        "status": "PASS",
        "duration_seconds": _elapsed_seconds(start),
        "retry_status": retry_status,
        "verification": _verification_summary(verify_module, verification_state),
    }


def _failure_result(
    name: str,
    retry_status: str,
    start: float,
    verify_module: Any,
    verification_state: Dict[str, Any],
    exc: BaseException,
) -> Dict[str, Any]:
    error_type, message, category = serialize_error(exc)
    return {
        "name": name,
        "status": "FAIL",
        "duration_seconds": _elapsed_seconds(start),
        "error_type": error_type,
        "error": message,
        "failure_category": category,
        "retry_status": retry_status,
        "verification": _verification_summary(verify_module, verification_state),
    }


def _skip_result(
    name: str,
    retry_status: str,
    start: float,
    verify_module: Any,
    verification_state: Dict[str, Any],
    exc: BaseException,
) -> Dict[str, Any]:
    reason = getattr(exc, "msg", None) or (exc.args[0] if exc.args else "")
    return {
        "name": name,
        "status": "SKIP",
        "duration_seconds": _elapsed_seconds(start),
        "reason": reason,
        "failure_category": "skipped",
        "retry_status": retry_status,
        "verification": _verification_summary(verify_module, verification_state),
    }


def _error_result(
    name: str,
    retry_status: str,
    start: float,
    verify_module: Any,
    verification_state: Dict[str, Any],
    exc: BaseException,
) -> Dict[str, Any]:
    error_type, message, category = serialize_error(exc)
    return {
        "name": name,
        "status": "ERROR",
        "duration_seconds": _elapsed_seconds(start),
        "error_type": error_type,
        "error": message,
        "failure_category": category,
        "retry_status": retry_status,
        "verification": _verification_summary(verify_module, verification_state),
    }


def _is_pytest_skip(exc: BaseException) -> bool:
    return _PytestSkipped is not None and isinstance(exc, _PytestSkipped)


def _elapsed_seconds(start: float) -> float:
    return round(time.time() - start, 3)


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
