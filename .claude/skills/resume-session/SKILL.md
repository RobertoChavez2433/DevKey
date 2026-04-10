---
name: resume-session
description: Fast session resume - load state, display summary, ready to work
user-invocable: true
disable-model-invocation: true
---

# Resume Session

Fast context load. Read the hot state, summarize it, and stop.

## Actions

1. Read only:
   - `.claude/memory/MEMORY.md`
   - `.claude/autoload/_state.md`
2. Display:
   - current phase
   - current status
   - last-session summary
   - next tasks
   - blockers if present
3. Return control to the user without preloading more files.

## Do Not Preload

- `.claude/logs/`
- `.claude/plans/`
- `.claude/tailor/`
- `.claude/test-results/`
- rules, docs, or structured state JSON unless the user asks for that surface

## Rules

- No git commands.
- No extra questions.
- The first user message after the summary determines the next surface to load.
