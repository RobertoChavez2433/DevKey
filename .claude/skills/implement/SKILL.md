---
name: implement
description: "Execute approved implementation plans with generic workers, per-phase completeness checks, and a final scoped review gate."
user-invocable: true
disable-model-invocation: true
---

# Implement

Execute an approved plan as a thin orchestrator. The main conversation stays
small, reads only the plan metadata plus the active phase slice, and dispatches
all real implementation work through generic workers and scoped reviewers.

## Iron Laws

1. The orchestrator does not edit source files directly.
2. The orchestrator does not load the full plan body into context at once.
3. The orchestrator does not use checkpoint files or headless Claude flows.
4. The orchestrator does not commit, push, or rewrite history.
5. Workers and reviewers must receive only the scope they need.

## Allowed Tools

The main conversation may use:

- Read
- Glob
- Grep
- Task

## Inputs

Required:

- plan path

Optional but usually present:

- spec path
- tailor path

## Workflow

1. Read the plan header and `Phase Ranges`.
2. For each phase:
   - read only the phase slice being executed
   - identify files in scope
   - dispatch 1-3 generic implementation workers with:
     - the phase slice
     - files in scope
     - matching path-scoped rules
     - `.claude/skills/implement/references/worker-rules.md`
   - collect returned file lists and decisions
   - run `completeness-review-agent` on that phase before moving on
3. If phase completeness review finds issues, dispatch a generic fixer worker
   with the same worker rules and rerun completeness review.
4. After all phases are complete, run these reviewers in parallel:
   - `code-review-agent`
   - `security-agent`
   - `completeness-review-agent`
5. If the final review gate finds issues, dispatch a generic fixer worker and
   rerun the full final gate up to 3 cycles.
6. Report files changed, decisions made, verification status, and any open
   risks.

## Worker Expectations

- Generic workers own code edits and local verification.
- Generic workers must read the matching path-scoped rules before editing.
- Worker prompts must inline
  `.claude/skills/implement/references/worker-rules.md`.
- Reviewer prompts must inline
  `.claude/skills/implement/references/reviewer-rules.md`.

## Hard Gates

Do not start implementation until the plan exists and includes `Phase Ranges`.

Do not present implementation as complete until:

1. every phase is executed
2. per-phase completeness checks pass
3. the final review gate passes or is explicitly escalated
