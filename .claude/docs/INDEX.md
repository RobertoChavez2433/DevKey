# .claude/ Documentation Index

## Skills

| Skill | File | Purpose |
|-------|------|---------|
| `/brainstorming` | `skills/brainstorming/SKILL.md` | Collaborative design → spec |
| `/writing-plans` | `skills/writing-plans/SKILL.md` | Spec → dependency graph → implementation plan |
| `/implement` | `skills/implement/SKILL.md` | Plan → autonomous execution (dispatch, review, verify) |
| `/systematic-debugging` | `skills/systematic-debugging/SKILL.md` | 10-phase root cause analysis |
| `/test` | `skills/test/SKILL.md` | ADB automated keyboard testing |
| `/audit-config` | `skills/audit-config/SKILL.md` | `.claude/` health check |
| `/tailor` | `skills/tailor/SKILL.md` | Tailor a spec for implementation planning |
| `/resume-session` | `skills/resume-session/SKILL.md` | Session start — load HOT context |
| `/end-session` | `skills/end-session/SKILL.md` | Session end — save state with auto-archiving |

## Agents

| Agent | File | Model | Tools | Scope |
|-------|------|-------|-------|-------|
| `implement-orchestrator` | `agents/implement-orchestrator.md` | opus | Read, Glob, Grep, Task, Write | Orchestrates plan execution; dispatches to specialist agents |
| `ime-core-agent` | `agents/ime-core-agent.md` | sonnet | Read, Edit, Write, Bash, Glob, Grep | IME core: LatinIME, KeyEventSender, ModifierStateManager, InputConnection |
| `compose-ui-agent` | `agents/compose-ui-agent.md` | sonnet | Read, Edit, Write, Bash, Glob, Grep | Compose UI: keyboard layouts, themes, KeyView, ActionBridge |
| `code-review-agent` | `agents/code-review-agent.md` | opus | Read, Grep, Glob, Bash | Read-only code quality reviewer; files defects via `gh issue` |
| `security-agent` | `agents/security-agent.md` | opus | Read, Grep, Glob, Bash | Read-only security auditor across 8 IME-specific domains |
| `debug-research-agent` | `agents/debug-research-agent.md` | sonnet | Read, Grep, Glob, Bash | Background research during deep debugging sessions |
| `plan-writer-agent` | `agents/plan-writer-agent.md` | opus | Read, Write, Glob, Grep | Writes plan fragments for large plans split across agents |
| `plan-fixer-agent` | `agents/plan-fixer-agent.md` | opus | Read, Edit, Grep, Glob | Addresses review findings on a plan via surgical edits |
| `completeness-review-agent` | `agents/completeness-review-agent.md` | opus | Read, Grep, Glob | Reviews a plan against its spec for drift, gaps, missing requirements |
| `test-orchestrator-agent` | `agents/test-orchestrator-agent.md` | sonnet | Bash, Read, Write, Grep, Glob, Edit | Orchestrates ADB-based automated keyboard testing; builds APK, dispatches waves |
| `test-wave-agent` | `agents/test-wave-agent.md` | sonnet | Bash, Read, Write, Edit | Executes a wave of keyboard test flows on ADB-connected device |

## Rules

Auto-loaded by Claude when matching files are opened.

| Rule File | Paths Trigger | Covers |
|-----------|--------------|--------|
| `rules/ime-lifecycle.md` | `**/LatinIME.kt`, `**/InputMethodService*` | IME lifecycle, onCreate() init order, serviceScope |
| `rules/compose-keyboard.md` | `**/ui/keyboard/**`, `**/ui/theme/**` | Compose keyboard layout, theme tokens, bridge pattern |
| `rules/jni-bridge.md` | `**/pckeyboard/**` | JNI bridge — never rename the Java class |
| `rules/build-config.md` | `**/build.gradle*`, `**/proguard*` | Gradle DSL, version catalogs, ProGuard |

## State Files

| File | Location | Purpose |
|------|----------|---------|
| `_state.md` | `autoload/_state.md` | Hot session state — auto-loaded each session (max 5 entries) |
| `PROJECT-STATE.json` | `state/PROJECT-STATE.json` | Structured project state: feature matrix, sessions, milestones |
| `FEATURE-MATRIX.json` | `state/FEATURE-MATRIX.json` | Feature completion tracking matrix |
| `implement-checkpoint.json` | `state/implement-checkpoint.json` | Checkpoint file for `/implement` plan execution progress |

## Defects

Defects live as GitHub Issues on `RobertoChavez2433/DevKey` with the `defect` label.

- All open defects: `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open`
- By area: `gh issue list --repo RobertoChavez2433/DevKey --label "area:<area>"` (see label taxonomy in `skills/systematic-debugging/references/github-issues-integration.md`)
- Historical archive: `.claude/logs/defects-archive-final.md` (snapshot at migration time — NOT updated)
