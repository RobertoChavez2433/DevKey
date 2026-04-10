---
name: end-session
description: End session with auto-archiving
user-invocable: true
disable-model-invocation: true
---

# End Session

Complete the session handoff without pulling in broad repo history.

## Actions

1. Summarize the session from conversation context only.
2. Update `.claude/autoload/_state.md` with:
   - current phase/status
   - what was done this session
   - top next actions
   - blockers
   - one compact recent-session entry
3. If needed, update `state/PROJECT-STATE.json` with a short status refresh.
4. If `Recent Sessions` grows beyond five entries, move the oldest one into
   `.claude/logs/state-archive.md`.
5. Present a concise handoff summary back to the user.

## Rules

- No git commands.
- Do not create or close GitHub issues automatically from this skill.
- Use conversation facts, not reconstructed history.
- Keep `_state.md` compressed enough for `/resume-session`.
