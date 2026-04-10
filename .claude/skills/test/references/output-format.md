# Test Output Format Reference

Reference for the shared test harness output shape. Each test run should produce
a self-contained results directory with screenshots, logs, per-flow notes, and a
summary.

## Directory Layout

```text
.claude/test-results/
  YYYY-MM-DD_HHmm_<descriptor>/
    run-summary.md
    screenshots/
    logs/
    flows/
```

## `run-summary.md`

Include:

- date/time
- device and Android version
- invocation or tier
- duration
- pass/fail/skip counts
- one line per failing flow
- artifact paths

## `flows/<flow>.md`

For each flow, record:

- status
- duration
- high-signal steps only
- relevant screenshot names
- relevant log files
- concise notes on failures or skips

## Chat Summary

Report test results back to the user in this shape:

```text
Test Run: X PASS | Y FAIL | Z SKIP

Failures:
- <flow>: <one-line failure>

Report: .claude/test-results/<run-dir>/run-summary.md
```

## Rules

- Keep summaries short enough to scan quickly.
- Put detail in flow reports, not in the chat summary.
- Do not auto-file issues from this reference; testing reports failures, it does
  not own the defect workflow.
