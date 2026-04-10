# Claude Refactor TODO

## Live Surface

- [x] Rewrite `.claude/CLAUDE.md` into a short always-on project entrypoint
- [x] Compress `.claude/memory/MEMORY.md` to durable project truths only
- [x] Rewrite `.claude/docs/INDEX.md` around live surface vs artifact stores

## Skills

- [x] Refactor `/brainstorming` to be optional for small clear work
- [x] Refactor `/tailor` around focused CodeMunch mapping
- [x] Refactor `/writing-plans` around lean plan writing + reviewer sweeps
- [x] Refactor `/implement` to a thin orchestrator with generic workers
- [x] Refactor `/systematic-debugging` to an interactive root-cause-first flow
- [x] Refactor `/test` around the existing harness instead of wave agents
- [x] Refactor `/audit-config` into a live-surface drift audit
- [x] Tighten `/resume-session` and `/end-session`

## Agents

- [x] Keep only lean-core agents: review, security, completeness, debug research, plan writer
- [x] Remove specialist/orchestrator-heavy agents
- [x] Rewrite surviving agents to be scoped, concise, and read-only where appropriate

## Rules And References

- [x] Add worker/reviewer reference files for `/implement`
- [x] Update plan-writing reference files to remove stale specialist routing
- [x] Add path-scoped rules for modifier state, settings/data, and testing infra
- [x] Preserve existing IME lifecycle, Compose, JNI, and build rules

## Cleanup

- [x] Remove stale hook and orchestrator artifacts from the live config surface
- [x] Remove obsolete specialist-agent memory files
- [x] Run a post-refactor audit for stale references and contradictions
- [x] Review the final diff for correctness and missing follow-through
