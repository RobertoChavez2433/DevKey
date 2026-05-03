"""
Per-test verification state for the DevKey E2E harness.

Tests are not allowed to pass just because no Python exception was raised.
They must record at least one explicit observation/evidence item after driving
the IME. This keeps action-only tests from becoming false positives.
"""
from contextvars import ContextVar
from typing import Any, Dict, List, Optional


_current_state: ContextVar[Optional[Dict[str, Any]]] = ContextVar(
    "devkey_e2e_verification_state",
    default=None,
)


def begin(test_name: str) -> None:
    _current_state.set({
        "test": test_name,
        "sequence": 0,
        "actions": [],
        "evidence": [],
    })


def end() -> Dict[str, Any]:
    state = _current_state.get()
    _current_state.set(None)
    if state is None:
        return {"actions": [], "evidence": []}
    return state


def _next_sequence(state: Dict[str, Any]) -> int:
    state["sequence"] += 1
    return state["sequence"]


def record_action(
    kind: str,
    detail: Optional[Dict[str, Any]] = None,
    requires_verification: bool = True,
) -> None:
    state = _current_state.get()
    if state is None:
        return
    state["actions"].append({
        "kind": kind,
        "detail": detail or {},
        "sequence": _next_sequence(state),
        "requires_verification": requires_verification,
    })


def record_evidence(kind: str, detail: Optional[Dict[str, Any]] = None) -> None:
    state = _current_state.get()
    if state is None:
        return
    state["evidence"].append({
        "kind": kind,
        "detail": detail or {},
        "sequence": _next_sequence(state),
    })


def assert_verified() -> Dict[str, Any]:
    state = _current_state.get()
    if state is None:
        return {"actions": [], "evidence": []}
    if not state["evidence"]:
        actions = ", ".join(action["kind"] for action in state["actions"][:8])
        suffix = f" Actions observed: {actions}." if actions else ""
        raise AssertionError(
            "Test completed without verification evidence. "
            "Use driver.wait_for, adb.assert_logcat_contains, key-map/visibility "
            "checks, or another explicit observation before passing."
            f"{suffix}"
        )
    last_evidence_sequence = max(evidence["sequence"] for evidence in state["evidence"])
    unresolved = [
        action for action in state["actions"]
        if action.get("requires_verification") and action["sequence"] > last_evidence_sequence
    ]
    if unresolved:
        actions = ", ".join(action["kind"] for action in unresolved[:8])
        raise AssertionError(
            "Test performed action(s) after the last verification evidence. "
            "Add an explicit observation after the final meaningful action. "
            f"Unverified actions: {actions}."
        )
    return state


def unresolved_actions() -> List[Dict[str, Any]]:
    state = _current_state.get()
    if state is None:
        return []
    last_evidence_sequence = 0
    if state["evidence"]:
        last_evidence_sequence = max(evidence["sequence"] for evidence in state["evidence"])
    return [
        action for action in state["actions"]
        if action.get("requires_verification") and action["sequence"] > last_evidence_sequence
    ]


def summarize(state: Dict[str, Any]) -> Dict[str, Any]:
    evidence: List[Dict[str, Any]] = state.get("evidence", [])
    actions: List[Dict[str, Any]] = state.get("actions", [])
    if len(evidence) <= 10:
        summarized_evidence: List[Dict[str, Any]] = evidence
    else:
        summarized_evidence = (
            evidence[:5]
            + [{
                "kind": "verify.evidence_truncated",
                "detail": {
                    "omitted_count": len(evidence) - 10,
                    "summary": "showing first 5 and last 5 evidence records",
                },
                "sequence": -1,
            }]
            + evidence[-5:]
        )
    return {
        "verified": bool(evidence),
        "evidence_count": len(evidence),
        "action_count": len(actions),
        "evidence": summarized_evidence,
    }
