# Security Review Cycle 2: Phase 2 Regression Infrastructure — 2026-04-08

**APPROVE**

## Summary

- Cycle 1 findings re-verified: MEDIUM addressed, LOW-1 addressed, LOW-2 confirmed deferred
- New sub-phases audited: 1.5, 1.6, 1.7, 1.8, 4.6, 4.7, 4.8, 4.9, 4.10
- New findings cycle 2: CRITICAL 0, HIGH 0, MEDIUM 0, LOW 0
- Privacy invariant: **preserved**

## Cycle-1 finding dispositions

### [MEDIUM] /adb/logcat DevKeyPress keystroke exfiltration — RESOLVED via README documentation

Phase 2.4 README (plan lines 876-881) adds explicit Privacy contract section documenting:
- HTTP log endpoint accepts only structural data
- DevKeyPress KEY/LONG lines carry live keystroke stream on debug builds
- Dev-only threat model with clear operational guidance (don't run on untrusted workstations, stop between sessions)

Matches cycle-1 recommendation Option 3. Acceptable.

### [LOW-1] URL scrubbing — RESOLVED, code is correct

Phase 1.1 step 2 implements recommended fix verbatim. Verified: `java.net.URI` separates userInfo from host/port, so reading scheme+host+port excludes credentials, query strings, and fragments. Correct against credential-embedded URLs.

Minor residual cosmetic nit: opaque URIs yield `"http://null:-1"` — ugly but non-leaky. Fine for dev path.

### [LOW-2] enableServer defense-in-depth — CONFIRMED DEFERRED

Plan header out-of-scope section explicitly defers this as a follow-up issue. Threat model is dev-only; loopback binding + RECEIVER_NOT_EXPORTED + isDebugBuild controls are sufficient for Phase 2.

## New sub-phase audit

### Phase 1.5 — KeyPressLogger long_press_fired forwarding

Payload: `label`, `code`, `lp_code`. Gray area — label IS the key character. However: (a) existing `KeyPressLogger` already writes the same content to logcat tag `DevKeyPress` which is in the accepted threat model, (b) `sendToServer` only fires when `serverUrl != null` which requires the ENABLE_DEBUG_SERVER broadcast on a debug build, (c) loopback + RECEIVER_NOT_EXPORTED + isDebugBuild all apply. Net-new HTTP surface for already-accepted content. Not a new finding.

**Optional (non-blocking)**: add a sentence to README privacy contract explicitly noting HTTP forwarding carries `long_press_fired` events.

### Phase 1.6-1.8 — Structural instrumentation only

- 1.6 candidate_strip_rendered: `suggestion_count` only. Clean.
- 1.7 keymap_dump_complete: `mode` + `key_count`. Clean.
- 1.8 layout_mode_recomposed: `mode` only. Clean.

### Phases 4.6-4.10 — Feature-panel smoke emit sites

- 4.6 clipboard panel_opened: panel identifier only. No clipboard content.
- 4.7 macros panel_opened: panel identifier only. No macro content.
- 4.8 command_mode_* events: trigger enum only. No command buffer, no target package.
- 4.9 plugin_scan_complete: count only. No plugin metadata.
- 4.10 modifier-combos: no new emit sites; reuses existing DevKey/TXT events.

Explicit privacy note at plan lines 2032-2033 enforces structural-only across all 4.6-4.10.

## Final disposition

**APPROVE.** All cycle-1 findings correctly addressed. New sub-phases carry either structural-only payloads or (single case of 1.5) content already covered by accepted threat model. No blocking findings.

## Optional follow-up

1. README privacy contract could mention long_press_fired events explicitly (non-blocking documentation hardening).
2. Cycle-1 LOW-2 follow-up issue for DevKeyLogger.enableServer defense-in-depth guard (file when Phase 2 ships).
