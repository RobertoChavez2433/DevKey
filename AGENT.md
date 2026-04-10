# Codex Context Bridge

This file is the Codex-facing bridge for this repo.

`.claude/` is the canonical AI workflow surface for this project. Do not create
or maintain a parallel project-specific `.codex/`, `.agents/`, or duplicate
instruction tree unless the user explicitly asks for one.

## Source Of Truth Order

When building context, use this order:

1. `.claude/CLAUDE.md`
2. `.claude/autoload/_state.md`
3. `.claude/memory/MEMORY.md`
4. Matching files in `.claude/rules/`
5. `.claude/docs/INDEX.md`
6. Stable references under `.claude/docs/reference/` and `.claude/specs/`
7. Current plan or tailor artifacts only when the task needs them
8. `.claude/archive/` only for historical context

If a historical artifact conflicts with the live surface, prefer the live
surface.

## What To Read

- Always start with `.claude/CLAUDE.md`.
- Use `.claude/autoload/_state.md` for current phase, blockers, and next steps.
- Use `.claude/memory/MEMORY.md` for durable architectural constraints and
  high-value gotchas.
- Load only the path-scoped rules that match the files being edited:
  - `.claude/rules/build-config.md`
  - `.claude/rules/compose-keyboard.md`
  - `.claude/rules/ime-lifecycle.md`
  - `.claude/rules/jni-bridge.md`
  - `.claude/rules/modifier-state.md`
  - `.claude/rules/settings-data.md`
  - `.claude/rules/testing-infra.md`

## Live Workflow Surface

Use `.claude/docs/INDEX.md` as the directory map for:

- Live skills in `.claude/skills/`
- Live agents in `.claude/agents/`
- Hot state in `.claude/autoload/`, `.claude/memory/`, and `.claude/state/`
- Artifact stores in `.claude/plans/`, `.claude/tailor/`, `.claude/logs/`,
  `.claude/test-flows/`, `.claude/test-results/`, `.claude/outputs/`

## Stable References

These are durable, non-dated references and should be preferred over old dated
artifact names:

- `.claude/specs/pre-release-spec.md`
- `.claude/docs/reference/key-coordinates.md`

## Archive Policy

Historical Phase 1/2 planning material was moved under:

- `.claude/archive/2026-04-08-pre-release/`

Treat that tree as read-only history, not as active workflow input.

## Windows Verification Rule

On Windows, always use the Android emulator for verification; never use
physical Android devices.

## Maintenance Rule

When project AI documentation needs to change, update `.claude/` first.
Only update `AGENT.md` when the bridge itself or the `.claude` structure
changes.
