# Phase 3 Regression Gate — Audit Trail

**This directory is committed to the repo. Do NOT write raw logcat,
DEVKEY_E2E_VERBOSE tracebacks, or any unscrubbed operator traces here.**

Only structured DevKeyLogger captures fetched via the driver
`/logs?category=...` endpoint, the per-tier run logs produced by
`e2e_runner.py`, the lint XML report, and the per-gate `gate-*.md`
summaries are permitted. See sub-phase 6.3 step 3 for the explicit
allow-list of files staged into this directory.
