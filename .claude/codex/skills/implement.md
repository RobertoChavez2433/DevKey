# Implement

Source workflow: `.claude/skills/implement/SKILL.md`.

Use for `/implement <plan>` or `implement <plan>` when an approved plan with
`Phase Ranges` exists.

## Codex Wrapper

1. Read the plan header and `Phase Ranges`.
2. Execute one phase at a time, loading only that phase slice plus matching
   `.claude/rules/*` files.
3. Keep edits scoped to the phase and the plan's file ownership.
4. Run the verification requested by the phase, with `./gradlew assembleDebug`
   as the default build gate.
5. Run scoped review checks before presenting implementation as complete.

When running under Codex, follow the active Codex system instructions for
delegation and editing. Do not commit, push, or rewrite history unless the user
explicitly asks.
