# Plan Index

## Active Codex Plans In `.codex/plans/`

- `2026-05-02-devkey-v1-release-audit-plan.md`: current v1.0 release audit
  status, completed commits, remaining implementation work, and validation
  blockers.
- `2026-05-03-smart-input-replacement-plan.md`: remaining smart-text donor
  replacement work, including the AnySoftKeyboard language-pack dictionary
  import and donor adapter milestones.

Save new Codex-authored plans here using:

- `YYYY-MM-DD-<topic>-plan.md`

Reference upstream `.claude/plans/`, `.claude/specs/`, and `.claude/tailor/`
artifacts instead of copying them unless a Codex-specific addendum is needed.

## Active Upstream Plans In `.claude/plans/`

- `2026-04-13-stress-test-suite.md`: stress-test implementation plan named by
  the current handoff as the main pre-release QA reference.
- `2026-04-13-e2e-optimization-spec.md`: current E2E optimization/spec support
  material.
- `2026-04-09-pre-release-phase3.md`: earlier pre-release phase plan that may
  still explain verification gates and phase context.
- `e2e-audit-todo.md`: E2E audit follow-up queue.

## Related Spec And Tailor Context

- `.claude/specs/2026-04-13-stress-test-spec.md`
- `.claude/specs/pre-release-spec.md`
- `.claude/tailor/2026-04-13-stress-test-suite/`
- `.claude/tailor/2026-04-09-pre-release-phase3/`

Load these only when the current task requires their detail.

## Codex Planning Policy

- Keep `.claude/` canonical.
- Store new Codex-authored plans under `.codex/plans/`.
- Use `.codex/PLAN.md` as the active index.
- Keep `.claude/plans/completed/`, `.claude/archive/`, and dated test results
  out of startup context unless the task depends on historical rationale.
- Verify plan paths before relying on generated or stale names from older
  session state.
