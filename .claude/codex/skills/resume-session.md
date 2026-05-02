# Resume Session

Source workflow: `.claude/skills/resume-session/SKILL.md`.

Use for `/resume-session` or `resume session`.

## Codex Wrapper

1. Read only `.claude/memory/MEMORY.md` and `.claude/autoload/_state.md`.
2. Summarize current phase, status, last-session summary, next tasks, and
   blockers.
3. Stop and wait for the user's next task.

Do not run git commands or preload plans, logs, tailor output, test results,
rules, docs, or structured state JSON unless the user asks.
