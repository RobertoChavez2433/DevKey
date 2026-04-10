---
name: audit-config
description: "Audit the live .claude workflow surface against the repo and report drift, stale references, and contradictory guidance."
user-invocable: true
disable-model-invocation: true
---

# Audit Config

Audit the live `.claude` workflow surface against the current repo. This skill
checks the files that actually shape Claude behavior and ignores historical
artifacts unless they are referenced by a live file.

## Scope

Audit these by default:

- `.claude/CLAUDE.md`
- `.claude/docs/INDEX.md`
- `.claude/rules/**`
- `.claude/skills/**`
- `.claude/agents/**`
- `.claude/memory/MEMORY.md`
- `.claude/autoload/_state.md`

Do not pre-audit historical outputs under:

- `.claude/logs/`
- `.claude/plans/review_sweeps/`
- `.claude/test-results/`
- `.claude/outputs/`

## Checks

1. Validate file references and path references from the live surface.
2. Flag stale references to removed agents, skills, files, or workflows.
3. Check for contradictory guidance across `CLAUDE.md`, memory, skills, and
   agents.
4. Verify read-only agents do not advertise write behavior.
5. Verify live docs match the current skills, agents, and rules.

## Workflow

1. Enumerate the live-surface files.
2. Grep for path references, agent names, skill names, and obvious stale terms.
3. Confirm referenced files still exist.
4. Confirm the current live agent set and rule set match the docs.
5. Write a report to `.claude/outputs/audit-report-YYYY-MM-DD.md`.

## Report Format

Use these sections:

- Summary
- Broken references
- Stale workflow references
- Contradictions
- Agent/rule/doc mismatches
- Recommended fixes

## Rules

- Do not mutate source code.
- Do not flag archived artifact content unless a live file points at it.
- Prefer concrete file references and exact stale strings over vague warnings.
- The report is the only file this skill writes.
