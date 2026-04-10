# Code Review: Phase 3 Pre-Release Regression Gate Plan — 2026-04-09 (Cycle 2)

**Plan:** `.claude/plans/2026-04-09-pre-release-phase3.md` (1390 lines)
**Reviewer:** code-review-agent (cycle 2)
**Scope:** changes only — cycle-1-approved content not re-reviewed.

## Summary
Findings: CRITICAL 0, HIGH 0, MEDIUM 1, LOW 3

## Cycle-1 MEDIUM carry-over status
- **Skip-reason fallback** — UNCHANGED. Acceptable as-is per cycle-1 recommendation.
- **`_pytest.outcomes.Skipped` internal import** — UNCHANGED. Existing comment block documents lazy-import rationale; ImportError fallback is sufficient.
- **Placeholder-replacement heredocs** — ADDRESSED. The sentence "Replace every `<placeholder>` token in this file with the actual value before staging for commit." appears in all 13 targeted sub-phases. Coverage complete.

## New-content findings

### [MEDIUM] Sub-phase 6.0 §2.4(b) scope broader than the plan claims
- **Plan ref:** sub-phase 6.0 step 1, rule line
- **Description:** The `gh issue list --label "defect,priority:critical"` syntax is correct, but the rule "any open issue except #9 blocks PROCEED_TO_PHASE_4" is stricter than spec §2.4(b) and may produce false-positive bounces on out-of-scope defects (e.g., an unrelated Phase 4-scope defect).
- **Recommendation:** Tighten step 1's rule to scope to Phase-3-area labels (`area:ime-lifecycle`, `area:text-input`, `area:voice-dictation`, `area:compose-ui`, `area:modifier-state`, `area:build-test`). Non-blocking because sub-phase 6.2 routes BOUNCE outcomes to the user anyway.

### [LOW] Sub-phase 6.0 §2.4(c) audit does not capture closing rationale
- **Description:** `gh issue view 4 --json state,title,labels` does not include commit links or closing comments. If #4 is closed via a PR merge without a comment, the audit trail captures only `{"state":"CLOSED"}`.
- **Recommendation:** Add `comments` and `closedAt` to the `--json` field list.

### [LOW] Sub-phase 1.3 README privacy policy is advisory, not enforced
- **Description:** No pre-commit hook or CI check enforces the "structured captures only" rule. Enforcement relies solely on sub-phase 6.3 step 4 (`git diff --staged` review).
- **Recommendation:** Already cross-referenced in 1.3. Acceptable.

### [LOW] Step-0 pattern in sub-phases 3.2 and 5.1
- **Description:** Step 0 is framed as scope/rationale context rather than an action step. Unambiguous in runbook conventions.
- **Verdict:** No finding; pattern is clear.

## Focused-area verdicts (cycle-2 scope)
1. **Sub-phase 1.3 structure** — VERIFIED CORRECT. README only, no `.gitignore` edit, defers enforcement to sub-phase 6.3 step 4. Single-quoted heredoc, no expansion risk.
2. **Sub-phase 6.0 command syntax** — VERIFIED CORRECT. `gh issue list` / `gh issue view` commands are valid; redirections use `>`/`>>` in correct order; issue #4 is a hardcoded literal.
3. **Sub-phase 3.1 step 4 label** — VERIFIED. `"defect,category:IME,area:text-input,priority:high"`.
4. **Step-0 pattern clarity** — VERIFIED.
5. **Heredoc discipline for new sub-phases** — VERIFIED. Sub-phase 1.3 single-quoted; sub-phase 6.0 uses `>`/`>>` redirection, not heredoc; sub-phase 3.4 step 5a is prose.
6. **Heading integrity** — VERIFIED. No duplicate headings, no broken nesting. Sub-phase 1.3 inserted cleanly between 1.2 and Phase 2; sub-phase 6.0 inserted cleanly before 6.1.
7. **Line-reference drift** — Ground-truth anchors at plan lines 119-138 remain valid against the current external source files.

## Regression scan
- No broken markdown, no duplicate headings, no invalidated line references, no scope creep. Expected-modified set in sub-phase 6.3 still matches tailor blast-radius.

## Verdict: APPROVE

The one MEDIUM finding (sub-phase 6.0 scope over-broadness) is non-blocking — sub-phase 6.2 already routes any BOUNCE outcome to the user. Recommend tightening the §2.4(b) rule to the Phase 3 area set before execution, but this does not block implementation kickoff.
