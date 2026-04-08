---
name: implement
description: "Spawn an orchestrator agent to implement a plan. Main window stays clean as a pure supervisor."
user-invocable: true
---

# /implement — Pure Supervisor

You are the **supervisor**. You spawn orchestrator agents via the Agent tool (Task) and handle handoffs. You do NOT read source files, review agent output, or write code.

<IRON-LAW>
1. **NEVER use headless mode.** No `claude --bare`, no `claude --agent`, no `--print`, no CLI invocations of Claude. Everything goes through the Agent tool (Task).
2. **Supervisor never edits source.** Allowed writes: ONLY `.claude/state/implement-checkpoint.json` (initialize or delete).
3. **Supervisor never reads plan content.** Only plan metadata (phase names, line ranges). The orchestrator and implementers read the plan themselves.
</IRON-LAW>

---

## Step 1: Accept the Plan

1. User invokes `/implement <plan-path>` — bare filename searches `.claude/plans/` if needed
2. Read the plan file **header only** — extract phase names + line ranges, `**Spec:**` path, `**Tailor:**` path (if present)
3. Checkpoint path: `.claude/state/implement-checkpoint.json`
4. Checkpoint existence check:
   - **Missing** → start fresh
   - **Exists + same plan path** → ask user "Resume from checkpoint (phases done: N) or start fresh?"
   - **Exists + different plan** → delete checkpoint, start fresh
5. Present phase list to user and confirm before starting

---

## Step 2: Initialize Checkpoint (if starting fresh)

Write `.claude/state/implement-checkpoint.json`:

```json
{
  "plan": "<absolute plan path>",
  "spec": "<absolute spec path or null>",
  "tailor": "<absolute tailor path or null>",
  "phases": {
    "1": {
      "name": "...",
      "plan_lines": "10-85",
      "status": "pending",
      "files_modified": [],
      "decisions": [],
      "completeness": { "verdict": null, "findings": [], "correction_rounds": 0 },
      "build": null,
      "fix_attempts": 0
    }
  },
  "modified_files": [],
  "decisions": [],
  "end_gate": {
    "status": "pending",
    "cycles": [],
    "low_findings": []
  },
  "blocked": []
}
```

Add one entry per phase extracted from the plan header.

---

## Step 3: Dispatch Orchestrator via Agent Tool

Spawn a single foreground Task:

```
Task tool call:
  subagent_type: implement-orchestrator
  description: "Execute implementation plan"
  prompt: |
    Plan file: {{PLAN_PATH}}
    Spec file: {{SPEC_PATH}}
    Tailor dir: {{TAILOR_PATH}}
    Checkpoint file: {{CHECKPOINT_PATH}}

    Execute the full orchestration loop defined in your agent frontmatter.
    Return DONE, HANDOFF, or BLOCKED.
```

Replace `{{PLAN_PATH}}`, `{{SPEC_PATH}}`, `{{TAILOR_PATH}}`, `{{CHECKPOINT_PATH}}` with actual absolute paths. Set `{{SPEC_PATH}}` and `{{TAILOR_PATH}}` to `null` if not present.

No headless flags. No `--print`. No `--json-schema`. No `tee` pipelines. No `stream-filter.py`. No `extract-result.py`. Just one Task call.

---

## Step 4: Handle Return Status (loop)

```
while status != DONE:
  result = Task(subagent_type=implement-orchestrator, ...)
  status = parse(result)

  if status == HANDOFF:
    log cycle count
    spawn a fresh orchestrator (Step 3 again)

  if status == BLOCKED:
    present blocked item(s) to user
    options: fix manually / skip / adjust plan
    if user resolves → update checkpoint, spawn again
    if user gives up → break

  if status == DONE:
    break
```

Each fresh orchestrator dispatch reads the checkpoint to determine where to resume.

---

## Step 5: Final Summary

Read checkpoint to compile the summary. Present to user:

```
## Implementation Complete

**Plan**: [filename]
**Orchestrator cycles**: N (M handoffs)
**Phases**: N/N complete

### Per-Phase Completeness
Phase 1 — APPROVED (0 correction rounds)
Phase 2 — APPROVED (1 correction round)
...

### End-of-Plan Quality Gate
- Build: PASS
- Lint: PASS
- Code Review: PASS (cycle 1)
- Security Review: PASS (cycle 1)
- Completeness Review: PASS (cycle 1)

### Files Modified
[deduped list from checkpoint modified_files]

### Decisions Made
[list from checkpoint decisions]

### Low Findings (logged, not fixed)
[count from checkpoint end_gate.low_findings]

Ready to review and commit.
```

**Supervisor does NOT commit or push.**

---

## Troubleshooting

| Symptom | Action |
|---------|--------|
| Orchestrator returns BLOCKED immediately | Read the `blocked` array in checkpoint. Resolve the blocker manually or skip the blocked step, then re-dispatch. |
| Orchestrator returns HANDOFF after partial progress | Normal — context limit hit. Spawn fresh orchestrator; it will resume from checkpoint. |
| Build fails repeatedly after 3 fixer attempts | Orchestrator will return BLOCKED. Present error to user; fix manually in editor, then re-dispatch. |
| Checkpoint has stale data from a different plan | Delete `.claude/state/implement-checkpoint.json` and start fresh. |
| User wants to skip a phase | Update checkpoint manually: set that phase's `status` to `"skipped"`, then re-dispatch. |
| Orchestrator cycles exceed 10 | Investigate whether the plan has circular dependencies or the build is fundamentally broken. |
