# End Session

Source workflow: `.claude/skills/end-session/SKILL.md`.

Use for `/end-session` or `end session`.

## Codex Wrapper

1. Summarize the session from conversation facts.
2. Update `.claude/autoload/_state.md` with current phase/status, completed
   work, next actions, blockers, and one compact recent-session entry.
3. Update `.claude/state/PROJECT-STATE.json` only if a short structured status
   refresh is needed.
4. If `Recent Sessions` grows beyond five entries, move the oldest entry to
   `.claude/logs/state-archive.md`.
5. Return a concise handoff summary.

Do not run git commands. Do not create or close GitHub issues automatically.
