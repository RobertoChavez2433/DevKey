"""JSON result and inventory artifact helpers for DevKey E2E runs."""
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from .paths import DEFAULT_RESULTS_DIR
from .preflight import device_metadata


def build_result_payload(
    started_at: datetime,
    ended_at: datetime,
    *,
    serial: Optional[str],
    suite: str,
    feature: Optional[str],
    test_filter: Optional[str],
    rerun_source: Optional[str],
    timeout_multiplier: float,
    test_timeout_seconds: float,
    preflight: Optional[Dict[str, Any]],
    passed: int,
    failed: int,
    errors: int,
    skipped: int,
    test_results: List[Dict[str, Any]],
) -> Dict[str, Any]:
    return {
        "schema_version": 1,
        "started_at": started_at.isoformat(),
        "ended_at": ended_at.isoformat(),
        "duration_seconds": round((ended_at - started_at).total_seconds(), 3),
        "device": device_metadata(serial),
        "suite": suite,
        "feature": feature,
        "test_filter": test_filter,
        "rerun_source": rerun_source,
        "timeout_multiplier": timeout_multiplier,
        "test_timeout_seconds": test_timeout_seconds,
        "preflight": preflight,
        "total": len(test_results),
        "passed": passed,
        "failed": failed,
        "errors": errors,
        "skipped": skipped,
        "tests": test_results,
        "failures": [result for result in test_results if result.get("status") in ("FAIL", "ERROR")],
        "artifacts": [],
        "privacy": {
            "typed_text_logged": False,
            "transcripts_logged": False,
            "clipboard_logged": False,
        },
    }


def write_results(payload: Dict[str, Any], explicit_path: Optional[str] = None) -> Path:
    return _write_json(payload, "e2e-results", explicit_path)


def write_inventory(payload: Dict[str, Any], explicit_path: Optional[str] = None) -> Path:
    return _write_json(payload, "key-inventory", explicit_path)


def _write_json(payload: Dict[str, Any], prefix: str, explicit_path: Optional[str]) -> Path:
    if explicit_path:
        path = Path(explicit_path)
    else:
        DEFAULT_RESULTS_DIR.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        path = DEFAULT_RESULTS_DIR / f"{prefix}-{stamp}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    return path
