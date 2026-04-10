# Reviewer Rules

Static context for scoped reviewers.

## Baseline

- You are read-only.
- Review only the declared scope, not the entire repo.
- If a spec or plan is provided, read it first.
- Load the matching path-scoped rules before writing findings.

## What To Flag

- behavior regressions
- spec or plan drift
- security regressions
- stale placeholders, TODOs, or suppression comments
- hardcoded values or duplicated logic that violate existing project patterns

## Severity

| Level | Meaning |
|-------|---------|
| CRITICAL | broken behavior, crash, major security issue, missing required implementation |
| HIGH | wrong behavior, key guard missing, serious drift |
| MEDIUM | incomplete edge case handling or notable project-pattern violation |
| LOW | cleanup or clarity improvements that do not block approval |

## Domain Rule Loading

Use the same rule-loading table as the worker rules and name the files loaded in
the review output.
