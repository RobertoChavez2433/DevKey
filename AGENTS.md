# Codex Bootstrap

`.claude/` is the single maintained AI-agent context system for DevKey.

`.codex/` is a Windows junction alias to `.claude/codex`. Keep Codex-facing
workflow docs once in `.claude/codex`; do not build an independent Codex
knowledge base.

## Load Order

1. Read `.codex/AGENTS.md`.
2. Read `.codex/Context Summary.md`.
3. Read `.codex/PLAN.md` and the smallest relevant file in `.codex/plans/`.
4. Read `.codex/CLAUDE_CONTEXT_BRIDGE.md` before loading deeper `.claude/`
   material.

## DevKey Guardrails

- Privacy is non-negotiable: never log typed text, credentials, or
  credential-adjacent input.
- On Windows, verify with the Android emulator only. Do not use physical
  Android devices.
- Default build verification is `./gradlew assembleDebug`; add `./gradlew lint`
  or `./gradlew detekt` when the touched surface warrants it.
- Do not use `./gradlew connectedAndroidTest` as a normal verification step.
- Start the debug/log server on port `3950`.
- Use DevKey rules under `.claude/rules/*`; Field Guide's Flutter/Supabase
  rules are structural precedent only and do not apply to this repo.

## Planning

- Save Codex-authored plans to `.codex/plans/` using
  `YYYY-MM-DD-<topic>-plan.md`.
- Reference shared `.claude/plans/`, `.claude/specs/`, and `.claude/tailor/`
  work instead of duplicating it.
- Treat `.claude/` as the deep reference library, not default startup context.

## Git Commits

- Treat commit history as the durable narrative layer for committed decisions.
- Follow Conventional Commits: `<type>(<scope>): <subject>`.
- `feat`, `fix`, `refactor`, and `perf` commits require a scope, narrative
  body, and real `Reason:` trailer.
- Scoped `test`, `docs`, `chore`, `ci`, and `build` commits also require a
  narrative body and `Reason:` trailer.
- Unscoped lightweight commits are only for mechanical changes with no durable
  decision.
- Valid scopes live in `scripts/git/valid-scopes.txt`.
- The repo enforces this through `.githooks/commit-msg` and
  `scripts/git/commit-msg`.
