---
name: writing-plans
description: "Read approved tailor output and write an implementation plan with machine-readable phase ranges for /implement."
user-invocable: true
disable-model-invocation: true
---

# Writing Plans

Use approved tailor output to write an implementation plan. The plan must carry
a machine-readable `Phase Ranges` block so `/implement` can execute by phase
without loading full plan bodies into orchestrator context.

## Prerequisite

`/tailor` must already exist for the spec. If the matching tailor directory is
missing, stop and tell the user to run `/tailor <spec-path>` first.

## Workflow

1. Read the approved spec.
2. Load only the tailor output needed for the plan.
3. Decide whether the main agent writes directly or whether
   `plan-writer-agent` is warranted for a large plan.
4. Write the plan using the shared template and routing table references.
5. Run these reviewers every cycle:
   - `code-review-agent`
   - `security-agent`
   - `completeness-review-agent`
6. If findings appear, fix the plan directly and rerun all three sweeps.
7. Stop after 3 review cycles if unresolved findings remain and escalate.

## Plan Requirements

- Include a complete header plus `Phase Ranges`.
- Use real file paths, symbols, and names from tailor output.
- Keep each step concrete enough that implementers do not need to guess.
- Use `./gradlew assembleDebug` as the default local verification step.
- Do not put `./gradlew connectedAndroidTest` or manual ADB loops in plan steps.

## Save Locations

- Plans: `.claude/plans/YYYY-MM-DD-<feature-name>.md`
- Writer fragments: `.claude/plans/parts/<plan-name>-writer-N.md`
- Review sweeps: `.claude/plans/review_sweeps/<plan-name>-<date>/`

## Hard Gates

Do not write the final plan until:

1. tailor output is loaded
2. the header is complete
3. `Phase Ranges` are populated
4. the review loop is complete or explicitly escalated
