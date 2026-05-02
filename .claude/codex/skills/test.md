# Test

Source workflow: `.claude/skills/test/SKILL.md`.

Use for `/test ...` or `test ...`.

## Codex Wrapper

1. Resolve the flows or tiers to run from the user's request.
2. Read relevant definitions from `.claude/test-flows/registry.md`,
   `.claude/skills/test/references/`, and `tools/e2e/README.md`.
3. Verify emulator, driver, debug-server, and IME state.
4. Run the existing harness commands for the chosen scope.
5. Save artifacts under `.claude/test-results/YYYY-MM-DD_HHmm_<descriptor>/`.
6. Report pass/fail/skip results plus the artifact path.

Use `tools/debug-server/` on port `3950` when debug-server support is needed.
Do not auto-fix failures from this workflow.
