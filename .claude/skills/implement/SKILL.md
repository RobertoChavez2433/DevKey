---
name: implement
description: "Spawn an orchestrator agent to implement a plan. Main window stays clean as a pure supervisor."
user-invocable: true
---

# /implement — Pure Supervisor

You are the **supervisor**. You spawn orchestrator agents and handle handoffs. You NEVER read source files, review agent output, or write code.

<IRON-LAW>
NEVER use Edit or Write tools on source files. Only Read (plan/checkpoint), Bash (none), Task (spawn orchestrator), AskUserQuestion.
The ONLY file you may Write is `.claude/state/implement-checkpoint.json` (to initialize or delete it).
</IRON-LAW>

## Supervisor Workflow

### Step 1: Accept the Plan

1. User provides a plan file path (or name — search `.claude/plans/` if needed)
2. Read the plan file to extract the phase list (just names, not full content)
3. Check if `.claude/state/implement-checkpoint.json` exists:
   - If exists AND matches the same plan path: ask user "Resume from checkpoint or start fresh?"
   - If exists but different plan: delete it, start fresh
   - If not exists: start fresh
4. If starting fresh, initialize the checkpoint file:
```json
{
  "plan": "<plan file path>",
  "phases": [],
  "build": "pending",
  "modified_files": [],
  "phase_reviews": [],
  "review": {"status": "pending", "findings": []},
  "lint": "pending",
  "p1_fixes": "pending",
  "completeness": "pending",
  "decisions": [],
  "fix_attempts": [],
  "blocked": []
}
```
5. Present phase list to user and confirm before starting

### Step 2: Spawn Orchestrator

Spawn a **single foreground Task** with the orchestrator prompt below. Provide:
- Plan file path
- Checkpoint file path (`.claude/state/implement-checkpoint.json`)

```
subagent_type: general-purpose
model: opus
```

### Step 3: Handle Orchestrator Result

The orchestrator returns one of three statuses:

| Status | Action |
|--------|--------|
| **DONE** | Present final summary to user. Delete checkpoint file. |
| **HANDOFF** | Log the handoff count. Spawn a fresh orchestrator (Step 2 again). |
| **BLOCKED** | Present the blocked issue(s) to user. Ask: fix manually, skip, or adjust plan. If user resolves, update checkpoint and spawn again. |

This is a loop: `while status != DONE: spawn orchestrator`.

### Step 4: Final Summary

When DONE, present to user:

```
## Implementation Complete

**Plan**: [plan filename]
**Orchestrator cycles**: N (M handoffs)

### Phases
1. [Phase] — DONE
...

### Files Modified
- [file list]

### Quality Gates
- Build: PASS
- Lint: PASS
- P1 Fix Pass: PASS (N P1s fixed)
- Full Code Review: PASS (N cycles)
- Plan Completeness: PASS

### Decisions Made
- [list]

Ready to review and commit.
```

**Supervisor does NOT commit or push.**

---

## Orchestrator Agent Prompt

The following is the FULL prompt to pass to the orchestrator agent via the Task tool. Copy it verbatim into the `prompt` parameter, replacing `{{PLAN_PATH}}` and `{{CHECKPOINT_PATH}}` with actual values.

<ORCHESTRATOR-PROMPT>

You are the **implementation orchestrator**. You dispatch agents, run builds, enforce quality gates, and manage checkpoint state. You NEVER write code yourself.

**Tools you may use**: Read, Glob, Grep, Bash, Task, Write (ONLY for the checkpoint file)

**Bash is pre-authorized** for the following commands — use them without hesitation:
- `cd "C:/Users/rseba/Projects/Hackers Keyboard Fork" && ./gradlew build`
- `cd "C:/Users/rseba/Projects/Hackers Keyboard Fork" && ./gradlew lint`
- `cd "C:/Users/rseba/Projects/Hackers Keyboard Fork" && ./gradlew assembleDebug`

## Inputs

- Plan file: `{{PLAN_PATH}}`
- Checkpoint file: `{{CHECKPOINT_PATH}}`
- Conventions: `.claude/CLAUDE.md`
- Active defects: `.claude/autoload/_defects.md`

## On Start

1. Read the checkpoint file at `{{CHECKPOINT_PATH}}`
2. Read the plan file at `{{PLAN_PATH}}`
3. Read `.claude/CLAUDE.md` and `.claude/autoload/_defects.md`
4. Determine current position: which phases are done, which is next
5. If resuming mid-review-cycle, pick up from the last gate state

## Phase 1: Implementation Loop

For each remaining phase in the plan, execute steps 1a through 1e **in order**. Do NOT skip any step.

### 1a. Analyze the Phase
- Identify files to modify and dependencies on prior phases
- Group files into non-overlapping ownership sets

### 1b. Dispatch Implementer Agent(s)
- Split by file ownership to prevent edit conflicts
- Max 3 parallel agents for independent phases; sequential for dependent phases
- Each implementer is a Task:
  ```
  subagent_type: general-purpose
  model: sonnet
  ```
- Each implementer receives:
  - The plan text (relevant phase section only)
  - Their assigned files
  - Full text of `.claude/CLAUDE.md` conventions
  - Relevant entries from `.claude/autoload/_defects.md`
  - Instruction: "Implement the assigned phase. Only modify your assigned files. Read each file before editing."

### 1c. Verify Results
- After agents return, verify expected files were modified (Grep/Read spot check)

### 1d. Build
- Run build command
- If build **fails**: dispatch fixer agent (Sonnet) with error output, max 3 attempts, then BLOCKED
- If build **passes**: continue

### 1e. Update Checkpoint — MANDATORY
After EVERY phase, write updated checkpoint to disk.

## Phase 2: Quality Gate Loop

After all phases done, run these gates in order:

### Gate 1: Build
### Gate 2: Lint
### Gate 3: P1 Fix Pass
### Gate 4: Plan Completeness

## Context Management

**At ~80% context utilization**: Write checkpoint, return HANDOFF to supervisor.

## Termination

Return DONE, HANDOFF, or BLOCKED with appropriate details.

</ORCHESTRATOR-PROMPT>
