# .claude Index

This index describes the live Claude workflow surface for DevKey. Treat plans,
tailor output, logs, and test artifacts as support material, not default
context.

## Stable References

| Path | Purpose |
|------|---------|
| `specs/pre-release-spec.md` | Active umbrella pre-release spec |
| `docs/reference/key-coordinates.md` | Durable coordinate fallback reference |

## Live Skills

| Skill | Purpose |
|-------|---------|
| `/brainstorming` | Lock intent and write an approved spec for larger or ambiguous work |
| `/tailor` | Build a CodeMunch-backed context package for an approved spec |
| `/writing-plans` | Turn approved spec + tailor output into an executable plan |
| `/implement` | Execute an approved plan with generic workers and scoped reviewers |
| `/systematic-debugging` | Run evidence-first debugging without skipping to fixes |
| `/test` | Run the existing ADB/HTTP-driver test harness and capture artifacts |
| `/audit-config` | Audit the live `.claude` surface for drift and stale references |
| `/resume-session` | Load hot status only |
| `/end-session` | Compress session state and handoff notes |

## Live Agents

| Agent | Purpose |
|-------|---------|
| `code-review-agent` | Scoped read-only correctness and maintainability review |
| `security-agent` | Scoped read-only security review |
| `completeness-review-agent` | Spec/plan/implementation drift review |
| `debug-research-agent` | Read-only tracing support for deep debugging |
| `plan-writer-agent` | Writes plan fragments for unusually large plans |

## Path-Scoped Rules

| Rule | Covers |
|------|--------|
| `rules/build-config.md` | Gradle, SDK, dependency, and release-config rules |
| `rules/compose-keyboard.md` | Compose keyboard UI, theme tokens, bridge boundaries |
| `rules/ime-lifecycle.md` | IME startup, lifecycle, and service-scope behavior |
| `rules/jni-bridge.md` | Locked JNI package/class and native bridge safety |
| `rules/modifier-state.md` | Modifier state transitions and mode-reset behavior |
| `rules/settings-data.md` | Settings, Room, repositories, and export/import safety |
| `rules/testing-infra.md` | Debug server, E2E harness, and test artifact conventions |

## Hot State

| File | Purpose |
|------|---------|
| `autoload/_state.md` | Current phase, blockers, recent sessions |
| `memory/MEMORY.md` | Durable project truths and high-value gotchas |
| `state/PROJECT-STATE.json` | Structured project metadata when explicitly needed |
| `state/FEATURE-MATRIX.json` | Structured feature status when explicitly needed |

## Artifact Stores

Load these only when the task needs them:

- `plans/`
- `tailor/`
- `logs/`
- `test-flows/`
- `test-results/`
- `archive/`
- `outputs/`
