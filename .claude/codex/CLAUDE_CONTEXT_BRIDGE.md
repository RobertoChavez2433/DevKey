# Claude Context Bridge

This file maps the maintained `.claude/` library into targeted, low-noise loads
for Codex. `.codex/` is only the compatibility alias path into
`.claude/codex`. Open only the files needed for the current task.

## Session Handoff

- `.claude/autoload/_state.md`
- `.claude/memory/MEMORY.md`
- `.claude/state/PROJECT-STATE.json`

## Project Manual

- `.claude/CLAUDE.md`
- `.claude/docs/INDEX.md`

Treat `.claude/CLAUDE.md` as the full project manual. Do not preload it for
every small task if `.codex/AGENTS.md` and `.codex/Context Summary.md` already
provide enough direction.

## Path-Scoped Rules

Load by touched surface, not all at once:

- `.claude/rules/build-config.md`
- `.claude/rules/compose-keyboard.md`
- `.claude/rules/ime-lifecycle.md`
- `.claude/rules/jni-bridge.md`
- `.claude/rules/modifier-state.md`
- `.claude/rules/settings-data.md`
- `.claude/rules/testing-infra.md`

## Skills

Live `.claude` skill entrypoints:

- `.claude/skills/resume-session/SKILL.md`
- `.claude/skills/end-session/SKILL.md`
- `.claude/skills/brainstorming/SKILL.md`
- `.claude/skills/tailor/SKILL.md`
- `.claude/skills/writing-plans/SKILL.md`
- `.claude/skills/implement/SKILL.md`
- `.claude/skills/systematic-debugging/SKILL.md`
- `.claude/skills/test/SKILL.md`
- `.claude/skills/audit-config/SKILL.md`
- `.claude/skills/layered-commit/SKILL.md`

Codex-facing wrappers:

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

`audit-docs` is a Codex alias for DevKey's existing `audit-config` workflow.

## Agents

Use these as routing and review references:

- `.claude/agents/plan-writer-agent.md`
- `.claude/agents/code-review-agent.md`
- `.claude/agents/security-agent.md`
- `.claude/agents/completeness-review-agent.md`
- `.claude/agents/debug-research-agent.md`

## On-Demand Stores

Load only when the task needs them:

- `.claude/plans/`
- `.claude/specs/`
- `.claude/tailor/`
- `.claude/test-flows/`
- `.claude/docs/reference/`
- `.claude/test-results/`
- `.claude/outputs/`
- `.claude/archive/`

## DevKey Verification Shortcuts

- Build: `./gradlew assembleDebug`
- Lint: `./gradlew lint`
- Detekt when relevant: `./gradlew detekt`
- E2E: `python tools/e2e/e2e_runner.py`
- Debug/log server: `tools/debug-server/`, port `3950`

Do not use `./gradlew connectedAndroidTest` as routine verification on
Windows. Use the Android emulator only.

## High-Noise Areas

Avoid by default:

- `.claude/logs/*`
- `.claude/plans/completed/*`
- `.claude/test-results/*`
- `.claude/archive/*`
- `.claude/outputs/*`
