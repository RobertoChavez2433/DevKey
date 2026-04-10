# Pattern — Claude Wave-Agent Test Orchestrator

## Summary
The `/test` skill is DevKey's current automated-test entry point. It dispatches `test-wave-agent` subagents in background, computes dependency waves over 8 flows in `.claude/test-flows/registry.md`, and each wave agent executes ADB + logcat + screenshot vision sequentially. **This pattern is flagged as problematic** per `.claude/memory/test-skill-redesign.md` (Session 33): token-expensive, slow, unreliable documentation, agents spending most tokens thinking instead of doing. Phase 2.2's driver server is explicitly intended to replace or supplement the agent-based mechanical work.

## How we do it (current state)
- `.claude/skills/test/SKILL.md` — top-level orchestrator rules, flag resolution, wave computation
- `.claude/agents/test-orchestrator-agent.md` — subagent that handles build/install/flow-selection then dispatches wave agents
- `.claude/agents/test-wave-agent.md` — subagent that executes one wave of flows (ADB taps, logcat checks, screenshots, vision verification, writes per-flow reports)
- `.claude/test-flows/registry.md` — 8 flow definitions with `feature`, `deps`, `steps`, `verify`, `timeout`, `notes`
- Wave agents run in background via `run_in_background: true` + `TaskOutput(block=true)` (called exactly once)
- Per-wave return format: 1 line per flow (PASS/FAIL/SKIP + duration) — orchestrator never reads per-flow files unless user asks

## Existing 8 flows (from `.claude/test-flows/registry.md`)

| Flow | Feature | Deps | Purpose |
|---|---|---|---|
| `ime-setup` | core | [] | Force-stop → ime set → verify keyboard visible → run broadcast calibration |
| `typing` | ui-keyboard | [ime-setup] | Tap `hello` → verify 5 DevKeyPress entries |
| `modifier-states` | core | [ime-setup] | Shift/Ctrl/Alt one-shot cycles |
| `mode-switching` | ui-keyboard | [ime-setup] | 123/ABC round-trip |
| `caps-lock` | core | [modifier-states] | Double-tap Shift → LOCKED → unlock |
| `modifier-combos` | core | [modifier-states] | Ctrl+C/V/A/Z, Alt+Tab |
| `layout-modes` | ui-keyboard | [ime-setup] | FULL mode only (COMPACT/COMPACT_DEV "not yet automated") |
| `rapid-stress` | ui-keyboard | [typing, modifier-states] | Rapid taps + mode toggles |

## Exemplar — wave dispatch (from SKILL.md)

```
### Phase 3: Wave Dispatch (Background, Context-Efficient)
For each wave (sequential):
1. Check for SKIPs: if any flow depends on a FAILed flow, mark SKIP
2. Calibrate coordinates first (Wave 0 only)
3. Dispatch `test-wave-agent` via Task tool with `run_in_background: true`:
   - Flow definitions
   - Explicit coordinate table (key label → x,y)
   - Run directory paths
   - Device info and workarounds
   - Previous wave results (1-line-per-flow status only)
4. Batch ADB commands: `adb logcat -c && sleep 0.2 && adb shell input tap X Y && sleep 0.5 && adb logcat -d -s DevKeyPress` in ONE Bash call
5. Wait for agent using `TaskOutput(task_id, block=true)` — call exactly once
6. Agent returns 1-line-per-flow status
```

## Reusable methods table

| Call | Purpose |
|---|---|
| `Task` tool with `subagent_type="test-wave-agent"` + `run_in_background=true` | Dispatch a wave |
| `TaskOutput(task_id, block=true)` (called exactly once) | Wait for wave to complete |
| `adb -s $SERIAL logcat -c` | Clear logcat before a flow |
| `adb -s $SERIAL logcat -d -s <tag>` | Dump since last clear, filter by tag |
| `MSYS_NO_PATHCONV=1 adb -s $SERIAL exec-out screencap -p > file.png` | Screenshot (Git Bash workaround) |
| `adb shell am force-stop dev.devkey.keyboard` | Restart IME with fresh code |
| `adb shell ime enable / set dev.devkey.keyboard/.LatinIME` | Select the IME |
| `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` | Trigger coordinate dump |

## Known issues (from Session 33 memory)

1. **Wave agents do too much at once** — tap + logcat + screenshot + vision + write report + return status. Should be split.
2. **No enforcement of report writes** — orchestrator never checks that per-flow files were written
3. **Agents spend most tokens thinking** — mechanical ADB work doesn't need an LLM
4. **Cost data**: Wave 0 (ime-setup) = 91s / ~34k tokens / 24 tool calls. Wave 1 (typing + modifier + mode) = ~720s / ~53k tokens / 80 tool calls. Total ~87k tokens / 104 tool calls / ~15 min for 4 basic flows.

## Phase 2 relationship to this pattern

**Option 1 — Replace wave agents with driver server** (aggressive)
- Driver server directly handles ADB mechanical work (taps, logcat polling, wave sequencing)
- Claude LLM role shrinks to: (a) building the APK, (b) interpreting failure reports, (c) screenshot vision verification on visual-diff mismatches
- `.claude/agents/test-wave-agent.md` — retired
- `.claude/agents/test-orchestrator-agent.md` — slimmed to build/dispatch/summarize
- `.claude/skills/test/SKILL.md` — slimmed, driver-server CLI becomes the main path

**Option 2 — Driver server as supplemental back-end** (incremental)
- Wave agents still run, but delegate `sleep/logcat` pairs to `curl http://localhost:3947/wait?...` calls
- Visual diff still runs in vision agents
- No file retirement
- Pro: Lowest churn in skill definitions
- Con: Doesn't address Session 33 cost/reliability concerns

**Option 3 — Full parallel track** (safest)
- Driver server + `tools/e2e/` Python harness becomes the primary path for Phase 2 green-bar testing
- `/test` Claude-skill stays as-is for ad-hoc exploratory runs
- Claims of "full green" are backed by the Python harness, not the wave agents
- Pro: Doesn't destabilize the existing Claude-skill test path
- Con: Two test runners to maintain forever

**Recommendation**: Plan author chooses between Option 1 (aggressive cleanup during Phase 4) and Option 3 (keep both). Option 2 is a half-step that carries costs of both approaches.

## Required imports / references (for the plan)
- `.claude/skills/test/SKILL.md`
- `.claude/agents/test-orchestrator-agent.md`
- `.claude/agents/test-wave-agent.md`
- `.claude/skills/test/references/adb-commands.md`
- `.claude/skills/test/references/ime-testing-patterns.md`
- `.claude/skills/test/references/output-format.md`
- `.claude/test-flows/registry.md`
- `.claude/memory/test-skill-redesign.md`
