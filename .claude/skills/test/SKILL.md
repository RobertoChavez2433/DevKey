---
name: test
description: "Run the existing DevKey ADB and HTTP-driver test harness, write artifacts, and report failures without turning testing into an auto-fix workflow."
user-invocable: true
disable-model-invocation: true
---

# Test

Run the existing DevKey E2E and debug harnesses. This skill executes flows,
captures artifacts, and reports failures. It does not turn failures into an
automatic repair pipeline.

## Artifact Root

Write run artifacts to:

- `.claude/test-results/YYYY-MM-DD_HHmm_<descriptor>/`

## Hard Rules

1. Use the existing harness under `tools/e2e/` before inventing new test logic.
2. Use the existing debug/log server under `tools/debug-server/` when the flow
   needs driver-backed state.
3. Prefer harness waits, driver checks, and structured logs over blind sleeps.
4. Do not auto-fix failures or dispatch fixer agents from this skill.
5. Only inspect detailed artifacts inline when a failure needs investigation.

## Workflow

1. Resolve which flows or tiers to run from the user's request.
2. Read the relevant flow definitions and test references.
3. Verify device, driver, and IME state.
4. Run the harness commands that already exist for the chosen scope.
5. Save screenshots, logs, and summaries under the run directory.
6. Report pass/fail/skip results plus the artifact path.

## Preferred Sources Of Truth

- `.claude/test-flows/registry.md`
- `.claude/skills/test/references/adb-commands.md`
- `.claude/skills/test/references/ime-testing-patterns.md`
- `.claude/skills/test/references/output-format.md`
- `tools/e2e/README.md`

## Investigation Mode

If a flow fails and the user asks why:

1. read the specific run artifacts for that failure
2. summarize the failure signal
3. recommend whether the next step is `/systematic-debugging` or a direct fix
