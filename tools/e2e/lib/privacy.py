"""Privacy-safe log payload helpers for DevKey E2E assertions."""

TRACE_METADATA_KEYS = {
    "devkey_trace_id",
    "devkey_event_seq",
    "devkey_uptime_ms",
    "devkey_thread",
}


def allowed_payload_keys(*keys: str) -> set[str]:
    """Return event-specific keys plus app-wide structural trace metadata."""
    return set(keys) | TRACE_METADATA_KEYS
