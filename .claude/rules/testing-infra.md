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

- Prefer the S21 release target (`RFCNC0Y975L`) for on-device E2E verification.
  Use the Android emulator only when the S21 is unavailable or when a
  reproducibility fallback is explicitly needed.
- Prefer the existing Python and Node helpers over ad-hoc shell workflows.
- Keep coordinate lookup, driver communication, and artifact writing in the
  harness libraries when possible.
- Do not duplicate protocol or navigation logic across multiple scripts.
- Physical devices use `adb reverse` for the host debug server. Emulators reach
  the host loopback through `10.0.2.2`; the harness translates automatically.

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

## E2E Release Gates

- Start the debug server on port `3950`.
- Run `python tools/e2e/e2e_runner.py --preflight` before feature or full-suite
  E2E gates.
- Use `--rerun-failed <results-json>` once after fixes to distinguish flaky
  failures from real regressions.
- Keep E2E result JSON under `.claude/test-results/` and keep artifacts
  structural: no typed text, voice transcripts, clipboard contents, credentials,
  or credential-adjacent input.
- Treat the locked `--suite all` discovery count as intentional. If it drifts,
  update the runner count and docs in the same change.

## Testing Philosophy

- Test real behavior, not mock presence.
- Do not add test-only methods or lifecycle hooks to production classes.
- Mock only after understanding the real dependency chain and required side effects.
- Prefer real production seams over large mock stacks.
