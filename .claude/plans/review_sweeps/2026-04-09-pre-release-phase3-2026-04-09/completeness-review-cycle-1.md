# Completeness Review: Phase 3 Regression Gate Plan — 2026-04-09 (Cycle 1)

**Plan:** `.claude/plans/2026-04-09-pre-release-phase3.md`
**Spec:** `.claude/specs/pre-release-spec.md`
**Reviewer:** completeness-review-agent (cycle 1)

## Summary
Requirements reviewed: 8 sub-gates (§3.1–§3.8) plus upstream §2.1, §2.2, §2.3, §2.4, §3.6, §4.4.1, §5.
Findings: 9 (2 CRITICAL, 3 HIGH, 3 MEDIUM, 1 LOW)

## Findings

### Finding 1 — DRIFT — §3.4 long-press "all modes AND themes" [HIGH]
**Spec:** §6 Phase 3.4 — "Long-press coverage flow green for all modes **and themes**"
**Plan:** sub-phase 3.2
**Description:** Spec says "all modes and themes" (plural). Plan only addresses modes (full/compact/compact_dev/symbols via expectation JSONs). Never iterates themes, never cites §4.4.1 light-theme deferral as an inheriting waiver.
**Remediation:** Add an explicit paragraph in sub-phase 3.2 citing §4.4.1 and asserting §3.4 runs dark-theme only until light theme is ratified post-v1.0.

### Finding 2 — DRIFT — §3.8 binary gate replaced by 4-bucket routing [HIGH]
**Spec:** §6 Phase 3.8 — "if any red, return to Phase 1"
**Plan:** header design decisions, sub-phases 2.2–2.4, 6.2
**Description:** Spec §3.8 is a binary gate (red → Phase 1). Plan introduces 4-bucket triage — Bucket 1 (stabilization-in-place), Bucket 2 (Phase 2 bounce), Bucket 3 (Phase 1 bounce), Bucket 4 (user escalation). Buckets 1, 2, and 4 are outcomes the spec does not sanction.
**Remediation:** Either have the user ratify the 4-bucket model as a spec refinement, OR collapse routing to "return to Phase 1" and treat Bucket 1 stabilization-in-place as explicitly authorized by spec §2.2 "flaky tests must be stabilized, not suppressed".

### Finding 3 — MISSING — §2.4(b) no-open-critical/high-defects gate absent [CRITICAL]
**Spec:** §2.4 Quality Gates — "No open defects labeled `priority:critical` or `priority:high` except #9"
**Plan:** no sub-phase; decision artifact template has no row
**Description:** Plan never runs `gh issue list --label defect --label priority:critical/high`. The decision-gate template omits this row entirely. Phase 3 can close without checking the open-defect list.
**Remediation:** Add a sub-phase (or extend 6.1) that runs the gh issue list queries and gates PROCEED_TO_PHASE_4 on the result being empty (except #9). Add a corresponding row to `decision.md`.

### Finding 4 — MISSING — §2.4(c) GH #4 plugin security resolution gate absent [CRITICAL]
**Spec:** §2.4 — "GH #4 (plugins load untrusted packages without signature verification) must be resolved"; §6 Phase 1.8
**Plan:** no verification step
**Description:** Plan does not verify GH #4 is resolved. Phase 3 closes without checking.
**Remediation:** Add a verification step in sub-phase 6.1 or 2.1 running `gh issue view 4` and requiring state=closed, OR adding an explicit annotation to `decision.md` recording how §2.4(c) was satisfied.

### Finding 5 — PARTIAL — §2.2 no-sleep-based-synchronization not enforced [MEDIUM]
**Spec:** §2.2 — "Test driver server ... Replaces any remaining polling/sleep-based synchronization"
**Plan:** sub-phase 2.1 verifies driver reachability only
**Description:** Plan's out-of-scope list acknowledges 6 sleep call sites still in `test_modes.py` and defers them. Per §2.2 this is an incomplete regression-bar condition being carried into Phase 3 without a red.
**Remediation:** Either inventory remaining sleep sites and produce a user-approved waiver list, OR escalate the 6 sleep sites to a Bucket 2 route at gate start.

### Finding 6 — DRIFT — §3.5 SSIM 0.92 inherited without spec ratification [MEDIUM]
**Spec:** §3.5 "within tolerance"; §9 Q3 (open question, cites 0.95 as example)
**Plan:** header design decision §3, sub-phase 3.4 step 5
**Description:** Spec §9 Q3 is an unresolved open question. Phase 2 plan chose 0.92; Phase 3 inherits without showing §9 Q3 was formally closed in the spec.
**Remediation:** Add a step in sub-phase 3.4 that either confirms §9 Q3 has been closed as "0.92 SSIM floor", OR escalates via Bucket 4 for user ratification before the gate is declared green. Record in `gate-3.5.md`.

### Finding 7 — DRIFT — §2.1 voice criterion vs plan's Bucket 4 escalation [HIGH]
**Spec:** §2.1 voice E2E as hard release criterion; §3.2
**Plan:** header design decision §4, sub-phase 3.3 steps 2/6
**Description:** Plan allows a Bucket 4 escalation path to amend the spec and demote §3.2 alone. But §2.1 lists voice E2E as a feature-completeness criterion; demoting §3.2 without also amending §2.1 would ship v1.0 with an unmet §2.1 item. §3.8 says "return to Phase 1" not "amend the spec".
**Remediation:** Clarify in sub-phase 3.3 that any Bucket 4 escalation MUST jointly amend §2.1 AND §3.2. Document that without model sourcing, Phase 3 cannot close green at all under the current spec — only legitimate outcomes are (a) Phase 1.1 model sourcing bounce, or (b) joint §2.1+§3.2 amendment.

### Finding 8 — PARTIAL — §3.6 "across all features" coverage incomplete [MEDIUM]
**Spec:** §3.6 — "Manual smoke on emulator across all features"; §2.1 feature list
**Plan:** Phase 5 sub-phases 5.1/5.2
**Description:** Plan runs only `phase1-regression-smoke.md` + `phase1-voice-verification.md`. Coverage-matrix.md also lists predictive next-word, long-press coverage, modifier states, caps lock, modifier combos, basic typing, rapid input — not in either manual checklist. Plan silently assumes §3.1 automation substitutes.
**Remediation:** Add an explicit citation in sub-phase 5.1 that the two checklists PLUS the automated §3.1 run collectively satisfy §2.1 per `coverage-matrix.md`, OR extend the manual checklists. Record the rationale in `gate-3.6-*.md`.

### Finding 9 — ORDERING — Phase 4 lint before Phase 5 manual smoke [LOW]
**Spec:** §6 Phase 3 lists 3.6 (manual smoke) before 3.7 (lint clean)
**Plan:** Phase 4 is lint, Phase 5 is manual smoke
**Description:** Plan reverses the spec's ordering. Spec does not explicitly mandate within-phase execution order.
**Remediation:** Either swap the order or add a one-line justification in the plan header ("lint is automated and cheap; run it before burning operator time on manual smoke").

## Requirements Coverage

| Req | Status | Notes |
|---|---|---|
| §3.1 three tiers | COVERED | Phase 2 sub-phases 2.2-2.4 |
| §3.2 voice | DRIFT | Finding 7 |
| §3.3 next-word | COVERED | sub-phase 3.1 |
| §3.4 long-press all modes AND themes | DRIFT | Finding 1 |
| §3.5 visual diff within tolerance | DRIFT | Finding 6 |
| §3.6 manual smoke across all features | PARTIAL | Finding 8 |
| §3.7 lint clean | COVERED | Phase 4 |
| §3.8 decision gate | DRIFT | Finding 2 |
| §2.1 feature completeness | DRIFT | Finding 7 |
| §2.2 no-sleep regression bar | PARTIAL | Finding 5 |
| §2.3 400-line rule | COVERED | correctly deferred to Phase 4 |
| §2.4(a) clean lint | COVERED | Phase 4 |
| §2.4(b) no open critical/high defects | MISSING | Finding 3 |
| §2.4(c) GH #4 resolution | MISSING | Finding 4 |
| §4.4.1 light-theme deferral | PARTIAL | Finding 1 dependency |
| §5.2 voice model sourcing | COVERED | correctly out-of-scope |

## Verdict: REJECT

Most critical: Findings 3 and 4 — plan never verifies spec §2.4(b) or §2.4(c) before closing Phase 3, allowing PROCEED_TO_PHASE_4 to be written while two release criteria remain unchecked.
