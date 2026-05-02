"""
HTTP client for the DevKey driver server (tools/debug-server/server.js).

Replaces sleep-based ADB + logcat assertions with long-polling /wait calls.
"""
import json
import os
import time
from typing import Any, Dict, Optional
from urllib import request, parse, error

from . import verify

DRIVER_URL = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
TIMEOUT_MULTIPLIER = float(os.environ.get("DEVKEY_TIMEOUT_MULTIPLIER", "1.0"))


class DriverError(Exception):
    pass


class DriverTimeout(DriverError):
    pass


def _get(path: str, params: Optional[Dict[str, Any]] = None, timeout: float = 30.0) -> Dict:
    qs = ""
    if params:
        qs = "?" + parse.urlencode({k: v for k, v in params.items() if v is not None})
    url = f"{DRIVER_URL}{path}{qs}"
    try:
        with request.urlopen(url, timeout=timeout) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body) if body else {}
    except error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        if e.code == 408:
            raise DriverTimeout(body)
        raise DriverError(f"HTTP {e.code}: {body}")
    except error.URLError as e:
        raise DriverError(f"driver unreachable at {url}: {e}")


def _post(path: str, params: Optional[Dict[str, Any]] = None,
          body: Optional[Dict] = None, timeout: float = 10.0) -> Dict:
    qs = ""
    if params:
        qs = "?" + parse.urlencode({k: v for k, v in params.items() if v is not None})
    url = f"{DRIVER_URL}{path}{qs}"
    data = json.dumps(body).encode("utf-8") if body is not None else b""
    req = request.Request(url, data=data, method="POST")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            respbody = resp.read().decode("utf-8")
            return json.loads(respbody) if respbody else {}
    except error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise DriverError(f"HTTP {e.code}: {body}")
    except error.URLError as e:
        raise DriverError(f"driver unreachable at {url}: {e}")


# --- Health / lifecycle ---

def health() -> Dict:
    return _get("/health", timeout=2.0)


def require_driver() -> None:
    """Assert the driver is reachable. Call at the start of every test."""
    try:
        h = health()
        if h.get("status") != "ok":
            raise DriverError(f"driver health check returned: {h}")
    except DriverError as e:
        raise DriverError(
            f"DevKey driver server not reachable. "
            f"Start it with: node tools/debug-server/server.js\n"
            f"Underlying error: {e}"
        )


def clear_logs() -> None:
    _post("/clear")
    verify.record_action("driver.clear_logs", requires_verification=False)


# --- Wave gating ---

def wait_for(category: str, event: Optional[str] = None,
             match: Optional[Dict] = None, timeout_ms: int = 5000) -> Dict:
    """
    Long-poll the driver for a matching log entry.

    Raises DriverTimeout if no entry matches within timeout_ms.
    """
    effective_timeout_ms = max(1, int(timeout_ms * TIMEOUT_MULTIPLIER))
    params = {"category": category, "timeout": effective_timeout_ms}
    if event:
        params["event"] = event
    if match:
        params["match"] = json.dumps(match)
    # Python-side timeout is 2x server-side — catches the case where the
    # server's own timeout fires slightly late without dropping the wait.
    result = _get("/wait", params=params, timeout=(effective_timeout_ms / 1000.0) + 5.0)
    if not result.get("matched"):
        raise DriverTimeout(result.get("error", "unknown"))
    verify.record_evidence("driver.wait_for", {
        "category": category,
        "event": event,
        "match": match or {},
    })
    return result["entry"]


# --- ADB dispatch ---

def tap(x: int, y: int) -> Dict:
    verify.record_action("driver.tap", {"x": x, "y": y})
    return _post("/adb/tap", params={"x": x, "y": y})


def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300) -> Dict:
    verify.record_action("driver.swipe", {"x1": x1, "y1": y1, "x2": x2, "y2": y2, "duration_ms": duration_ms})
    return _post("/adb/swipe", params={
        "x1": x1, "y1": y1, "x2": x2, "y2": y2, "duration": duration_ms
    })


def broadcast(action: str, extras: Optional[Dict[str, str]] = None) -> Dict:
    requires_verification = action not in {
        "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
        "dev.devkey.keyboard.RESET_CIRCUIT_BREAKER",
        "dev.devkey.keyboard.RESET_KEYBOARD_MODE",
    }
    verify.record_action(
        "driver.broadcast",
        {"action": action, "extras": extras or {}},
        requires_verification=requires_verification,
    )
    return _post("/adb/broadcast", body={"action": action, "extras": extras or {}})


def logcat_dump(tags: list) -> str:
    # NOTE: Do NOT clear logcat here — that would wipe the events the caller wants to read.
    #       Callers that need a clean baseline must call `logcat_clear()` explicitly BEFORE
    #       the action that produces the events they expect to dump.
    result = _get("/adb/logcat", params={"tags": ",".join(tags)})
    stdout = result.get("stdout", "")
    if stdout.strip():
        verify.record_evidence("driver.logcat_dump", {"tags": tags, "nonempty": True})
    return stdout


def logcat_clear() -> None:
    _post("/adb/logcat/clear")
    verify.record_action("driver.logcat_clear", requires_verification=False)


# --- Wave lifecycle ---

def wave_start(wave_id: str) -> Dict:
    verify.record_action("driver.wave_start", {"wave_id": wave_id}, requires_verification=False)
    return _post("/wave/start", params={"id": wave_id})


def wave_finish(wave_id: str, status: str = "pass") -> Dict:
    verify.record_evidence("driver.wave_finish", {"wave_id": wave_id, "status": status})
    return _post("/wave/finish", params={"id": wave_id, "status": status})
