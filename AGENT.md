# Codex Context Pointer

Use `AGENTS.md` as the root Codex/bootstrap file.

`.claude/` is the single maintained AI-agent context system for DevKey.
`.codex/` is a Windows junction alias to `.claude/codex`, not a separate
knowledge base.

## Startup Order

1. `AGENTS.md`
2. `.codex/AGENTS.md`
3. `.codex/Context Summary.md`
4. `.codex/PLAN.md` and the smallest relevant file in `.codex/plans/`
5. `.codex/CLAUDE_CONTEXT_BRIDGE.md` for targeted `.claude/` loads

When project AI documentation needs to change, update `.claude/` first. Update
this pointer only if the bridge or alias structure changes.
