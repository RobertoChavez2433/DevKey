# Audit Config

Source workflow: `.claude/skills/audit-config/SKILL.md`.

Use for `/audit-config` or `audit config`.

## Codex Wrapper

Audit the live `.claude` workflow surface against the repo:

- `.claude/CLAUDE.md`
- `.claude/docs/INDEX.md`
- `.claude/rules/**`
- `.claude/skills/**`
- `.claude/agents/**`
- `.claude/memory/MEMORY.md`
- `.claude/autoload/_state.md`
- `.claude/codex/**`
- root `AGENT.md` and `AGENTS.md`

Report broken references, stale workflow references, contradictions,
agent/rule/doc mismatches, and recommended fixes. The report belongs under
`.claude/outputs/audit-report-YYYY-MM-DD.md`.

Do not mutate source code from this workflow.
