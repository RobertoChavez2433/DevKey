# Codex Compatibility Alias

`.claude/` is the single maintained AI-agent reference system for DevKey.
`.codex/` exists only as a compatibility alias to `.claude/codex`; use it to
find the right context, not to duplicate the `.claude/` knowledge base.

## Default Startup Flow

1. Read this file.
2. Read `.codex/Context Summary.md`.
3. Read `.codex/PLAN.md` and the smallest relevant file in `.codex/plans/`.
4. Use `.codex/CLAUDE_CONTEXT_BRIDGE.md` only when you need specific
   `.claude/` files.

## Operating Rules

- Keep startup context lean.
- Treat `.claude/CLAUDE.md` as the project manual, not default startup context.
- For handoff work, prefer `.claude/autoload/_state.md` first, then
  `.claude/memory/MEMORY.md` if durable patterns matter.
- For active work, prefer `.codex/PLAN.md`, `.codex/plans/`,
  `.claude/plans/`, `.claude/specs/`, and `.claude/tailor/`.
- Load only the matching `.claude/rules/` files for the surface you are
  touching.
- Use `.claude/agents/` as review and routing references, not default startup
  context.
- Do not import Field Guide's Flutter, Supabase, auth, sync, or role rules into
  DevKey. Field Guide is the layout model only.

## DevKey Non-Negotiables

- Never log typed text, credentials, or credential-adjacent input.
- Debug logs may record structural state, key codes, mode transitions, and
  harness events only.
- Keep `LatinIME.kt` as the Android lifecycle boundary; avoid growing unrelated
  feature logic there.
- Keep `SettingsRepository` as the settings source of truth.
- Preserve the key dispatch chain:
  Compose/UI -> bridge -> `LatinIME` -> `KeyEventSender`.
- Preserve the JNI dictionary package/class path:
  `org.pocketworkstation.pckeyboard.BinaryDictionary`.

## Build, Test, And Debug

- Default build: `./gradlew assembleDebug`.
- Add `./gradlew lint` or `./gradlew detekt` when the touched surface warrants
  static checks.
- Use `./gradlew test` only when unit-test coverage is relevant to the change.
- Do not use `./gradlew connectedAndroidTest` as routine verification.
- On Windows, use the Android emulator only; do not verify on physical Android
  devices.
- Use the existing E2E harness under `tools/e2e/`.
- Use the existing debug/log server under `tools/debug-server/`.
- Start the debug/log server with port `3950`.

## Live `.claude/` Surface

Use the bridge to load the smallest relevant slice of the current `.claude/`
library:

- session handoff:
  - `.claude/autoload/_state.md`
  - `.claude/memory/MEMORY.md`
  - `.claude/state/PROJECT-STATE.json`
- repo manual:
  - `.claude/CLAUDE.md`
- workflow rules:
  - `.claude/rules/**`
- shared workflows:
  - `.claude/skills/**`
- plans, specs, and tailor output:
  - `.claude/plans/**`
  - `.claude/specs/**`
  - `.claude/tailor/**`
- review personas:
  - `.claude/agents/**`
- test orchestration:
  - `.claude/test-flows/**`
  - `.claude/test-results/**`
- audit-only references:
  - `.claude/outputs/**`

## Planning

- Save new Codex-authored plans to `.codex/plans/`.
- Use `YYYY-MM-DD-<topic>-plan.md`.
- Reference existing `.claude/plans/*.md` work from `.codex/PLAN.md` instead
  of cloning it unless a Codex-specific addendum is needed.

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

## Testing Non-Negotiables

- Test real IME behavior through production seams.
- Prefer the existing emulator, ADB, debug-server, and E2E harnesses over
  one-off ADB scripts.
- Do not add test-only hooks, lifecycle APIs, or production bypasses.
- Do not log typed content, fixture text that resembles secrets, credentials,
  or credential-adjacent values.
- Re-establish IME state intentionally after installs; `adb install -r` does
  not guarantee a fresh IME process.

## Compatibility Skills

Treat these messages as workflow triggers:

- `/resume-session` or `resume session`
- `/end-session` or `end session`
- `/brainstorming` or `brainstorming` or `brainstorm <topic>`
- `/tailor <spec>` or `tailor <spec>`
- `/writing-plans <spec>` or `writing plans <spec>`
- `/implement <plan>` or `implement <plan>`
- `/systematic-debugging` or `systematic debug <issue>`
- `/test ...` or `test ...`
- `/audit-config` or `audit config`
- `/audit-docs` or `audit docs`

Workflow wrappers live in:

- `.codex/skills/resume-session.md`
- `.codex/skills/end-session.md`
- `.codex/skills/brainstorming.md`
- `.codex/skills/tailor.md`
- `.codex/skills/writing-plans.md`
- `.codex/skills/implement.md`
- `.codex/skills/systematic-debugging.md`
- `.codex/skills/test.md`
- `.codex/skills/audit-config.md`
- `.codex/skills/audit-docs.md`

These wrappers are Codex-facing, but they intentionally target the same shared
`.claude` rules, skills, specs, and handoff files.

## Avoid By Default

- `.claude/logs/*`
- `.claude/plans/completed/*`
- `.claude/test-results/*`
- `.claude/archive/*`
- `.claude/outputs/*`
