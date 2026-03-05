# Test Skill Redesign Notes (Session 33)

## Why /implement Works and /test Doesn't

### /implement succeeds because:
- Orchestrator gives sub-agents **one specific, focused task** with clear deliverables (write this code, modify this file)
- Deliverables are **verifiable** — orchestrator can check if code was written
- Sub-agents do **one thing well** and return

### /test fails because:
- Wave agents asked to do **too many things at once**: execute ADB commands, read logcat, take screenshots, analyze screenshots with vision, write flow reports, write log files, AND return status
- **No enforcement** that reports get written — orchestrator never checks
- Agents spend most tokens **thinking about what to do** rather than doing it
- ADB interaction is **inherently serial and slow** — every tap needs a Bash call, sleep, logcat read
- Orchestrator also kept violating its own "stay thin" rule — reflexively fixing problems instead of delegating

## What Needs to Change

### 1. Orchestrator should execute ADB flows directly
Tapping keys, reading logcat, checking codes is mechanical work — doesn't need an LLM. A loop of "clear logcat, tap key, sleep, read logcat, verify code" can be done in one Bash script or a few direct Bash calls by the orchestrator.

### 2. Use agents ONLY for vision/screenshot analysis
The one thing an LLM agent adds over a script is analyzing screenshots. That should be the ONLY agent job — everything else is mechanical.

### 3. Orchestrator should write the reports
It has all the data (pass/fail per step, logcat output, screenshot paths). It should compile reports itself rather than hoping agents do it.

### 4. Simplify the output structure
Current format is over-engineered — multiple markdown files, separate log files, screenshot naming conventions. A single `run-summary.md` with inline results is enough.

### 5. Consider a Bash script approach
Write a test runner Bash script (or set of scripts) that:
- Handles all ADB mechanical work (tap, sleep, read logcat, verify)
- Takes screenshots at key points
- Outputs structured results (JSON or simple text)
- Then have the orchestrator optionally dispatch vision agents ONLY for screenshot verification

## Cost/Performance Data (Session 33)
- Wave 0 (ime-setup): 91s, ~34k tokens, 24 tool calls — for verifying keyboard is visible and one key tap works
- Wave 1 (typing + modifier-states + mode-switching): ~720s, ~53k tokens, 80 tool calls — for typing "hello", toggling 3 modifiers, and 2 mode switches
- Total: ~87k tokens, 104 tool calls, ~15 min for 4 basic flows
