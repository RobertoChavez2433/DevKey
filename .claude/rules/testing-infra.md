---
paths:
  - "tools/debug-server/**"
  - "tools/e2e/**"
  - ".claude/test-flows/**"
  - "**/debug/DevKeyLogger.kt"
  - "**/debug/KeyMapGenerator.kt"
---

# Testing Infrastructure Rules

## Harness Ownership

The shared test harness lives in `tools/e2e/` and `tools/debug-server/`.

- On Windows, always use the Android emulator for verification; never use physical Android devices.
- Prefer the existing Python and Node helpers over ad-hoc shell workflows.
- Keep coordinate lookup, driver communication, and artifact writing in the
  harness libraries when possible.
- Do not duplicate protocol or navigation logic across multiple scripts.

## Artifact Rules

- New test run artifacts belong under `.claude/test-results/` or the harness
  artifact directories, not scattered across the repo root.
- Capture enough evidence to debug failures, but do not treat screenshots and
  logs as always-on context.

## Privacy And Debugging

- Debug helpers may log structural state, key codes, timings, and mode changes.
- Never log typed text, clipboard contents, or credential-adjacent values.
- Reuse existing debug server endpoints and harness wait helpers before adding
  new polling or sleep-heavy logic.
