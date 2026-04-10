# Security Review: Phase 3 Regression Gate Plan — 2026-04-09 (Cycle 2)

**Plan:** `.claude/plans/2026-04-09-pre-release-phase3.md`
**Reviewer:** security-agent (cycle 2)
**Scope:** Verification of cycle-1 fixer edits only.

## Summary
- Cycle-1 findings re-verified: 1 MEDIUM, 2 LOW, 6 INFO
- New cycle-2 findings: 1 INFO
- All blocking cycle-1 findings addressed.

## Re-verification of cycle-1 findings

### [cycle-1 MEDIUM] `.claude/test-results/` gitignore gap — RESOLVED (banner approach)
- Sub-phase 1.3 creates README.md with explicit banner: *"This directory is committed to the repo. Do NOT write raw logcat, DEVKEY_E2E_VERBOSE tracebacks, or any unscrubbed operator traces here."*
- Banner is placed inside the directory so any operator touching it will see it. Correctly collapses both the MEDIUM (false rationale trap) and cycle-1 LOW (DEVKEY_E2E_VERBOSE traceback leakage) into one fix. Operational enforcement routed to sub-phase 6.3 step 3+4.

### [cycle-1 LOW] DEVKEY_E2E_VERBOSE traceback leakage — RESOLVED
- Subsumed by the 1.3 banner.

### [cycle-1 INFO] Android Lint Security carve-out — RESOLVED (minor gap)
- Sub-phase 4.2 step 3 adds the exception with enumerated IDs. The open-ended "etc." catches the gap noted below.

### [cycle-1 INFO] Label hygiene — RESOLVED
- sub-phase 3.1 step 4 label now matches CLAUDE.md canonical values.

### [cycle-1 INFO] PRIVACY caution at sub-phase 5.1 step 3 — RESOLVED
- Bold standalone `**PRIVACY:**` line immediately preceding the `gh issue create` example; sub-phase 5.2 inherits by reference.

## New cycle-2 findings

### [INFO] Lint Security enumeration missing `UnsafeDynamicallyLoadedCode`
- **Plan ref:** sub-phase 4.2 step 3 enumeration
- **Description:** The enumerated Security check IDs cover standard exported-component and crypto/WebView cases but omit `UnsafeDynamicallyLoadedCode` — which flags `DexClassLoader` / `PathClassLoader` loads, the exact surface `PluginManager.kt` exercises. Given plugin loading is one of DevKey's highest-risk surfaces, worth naming explicitly.
- **Recommendation:** Extend the parenthetical to include `UnsafeDynamicallyLoadedCode` and optionally `GetSignatures`/`PackageManagerGetSignatures`. Non-blocking — "etc." and Bucket 4 escalation still catch an attentive operator.

## Regression check on fixer edits
- **Sub-phase 1.3** — single-quoted heredoc, no interpolation. Clean.
- **Sub-phase 6.0** — `gh issue list` / `gh issue view` use string-literal argv, no shell eval, fixed paths, hardcoded issue number. No credential dump. Clean.
- No new `git add -A`, no new `adb logcat -d` dumps, no new interpolation points. Cycle-1 invariants preserved.

## Clean concerns (unchanged from cycle 1)
- Supply-chain (pytest), lint flip, driver handshake loopback, driver log capture, manual smoke PII, heredoc discipline — all clean.

## Verdict: APPROVE

All cycle-1 blocking and non-blocking findings are addressed. The banner approach at 1.3 is an acceptable substitute for a full gitignore. The one new finding (`UnsafeDynamicallyLoadedCode` enumeration gap) is INFO-only and non-blocking.
