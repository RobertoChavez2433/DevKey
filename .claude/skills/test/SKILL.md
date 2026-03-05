---
name: test
description: "Build, install, and run automated keyboard test flows on an ADB-connected Android device/emulator using ADB input, logcat verification, and Claude vision. Supports tier flags, per-feature targeting, and structured output."
user-invocable: true
---

# /test Skill

Automated ADB-based testing for DevKey keyboard. Builds the APK, installs on a connected device/emulator, sets up the IME, and runs keyboard test flows with logcat + visual verification. Produces structured output with screenshots, logcat, and per-flow reports.

## Iron Laws

> 1. **ORCHESTRATOR STAYS THIN.** Dispatches wave agents in background. Never processes raw screenshots or logcat dumps.
> 2. **LOGCAT AFTER EVERY INTERACTION.** Wave agents check `adb logcat` after every ADB action — catches IME errors and state drift.
> 3. **DISK-FIRST OUTPUT.** Wave agents write full results to disk (flow reports, defects, screenshots). Return only a 1-line-per-flow status to orchestrator.
> 4. **READ DISK ON DEMAND ONLY.** Orchestrator never pre-reads flow reports. Only reads from disk when user asks to investigate a specific failure.

## CLI Interface

```
/test --<flag> [--<flag> ...]
/test <flow-name> [<flow-name> ...]
```

## Tier Flags

| Flag | What runs | Est. time |
|------|-----------|-----------|
| `--smoke` | 2 smoke flows (ime-setup, typing) | ~1-2 min |
| `--feature` | All 6 feature flows (modifiers, modes, caps-lock, combos, layout) | ~5-10 min |
| `--full` | All flows including rapid-stress | ~10-15 min |

## Feature Flags (run specific feature flows)

| Flag | Flows triggered |
|------|----------------|
| `--typing` | typing |
| `--modifiers` | modifier-states, caps-lock, modifier-combos |
| `--modes` | mode-switching |
| `--layout` | layout-modes |
| `--stress` | rapid-stress |

## Combining Flags

```
/test --smoke --modifiers         # Smoke flows + modifier feature flows
/test --typing --modes            # Typing + mode-switching feature flows
/test --feature --stress          # All feature flows + rapid-stress
```

## Special Flags

| Flag | Behavior |
|------|----------|
| `--all` | Alias for `--full` |
| `--list` | Print available flows, don't run |
| `--dry-run` | Parse flags, show what would run, don't execute |

## Named Flow Arguments

```
/test typing                      # Run typing flow only
/test caps-lock                   # Runs ime-setup + modifier-states + caps-lock (deps auto-resolved)
/test typing mode-switching       # Run both flows + deps
```

## Auto-Selection (no args)

```
/test                             # Auto-select from git diff
```

Maps `git diff main...HEAD` to features, selects matching flows + transitive dependencies.

## Flag Resolution Logic

When the orchestrator receives flags, it resolves them to a concrete flow list:

1. **Parse all flags** from the invocation
2. **Expand tier flags**: `--smoke` -> 2 smoke flows, `--feature` -> all 6 feature flows, etc.
3. **Expand feature flags**: `--modifiers` -> [modifier-states, caps-lock, modifier-combos], etc.
4. **Union all expanded flows** (deduplicate)
5. **Resolve transitive deps**: For each flow, add all flows listed in its `deps` field, recursively
6. **Deduplicate again** after dep resolution
7. **Topological sort**: Order flows by dependency depth (no-dep flows first)

Priority order when flags conflict: the union is always taken (flags are additive, never subtractive).

## How It Works

### Phase 1: Pre-Flight
1. **ADB check**: `adb devices -l` — verify device/emulator connected
2. **Device info**: Collect model, Android version, screen resolution for report header
3. **Build check**: If debug APK is >1 hour old or missing, rebuild via `./gradlew assembleDebug`
4. **Install**: `adb -s $SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`
5. **IME setup**: Force stop, enable IME, set IME (see IME-Specific Setup below)
6. **Create run directory**: `.claude/test-results/YYYY-MM-DD_HHmm_{descriptor}/` with `screenshots/`, `logs/`, `flows/` subdirs
7. **Cleanup**: Delete oldest run if >5 exist
8. **Write report header**: Initialize `run-summary.md` with metadata

### Phase 2: Wave Computation
1. Build dependency graph from `deps` fields
2. Topological sort into wave groups
3. Flows in the same wave have no mutual deps and can run in parallel

### Phase 3: Wave Dispatch (Background, Context-Efficient)
For each wave (sequential):
1. Check for SKIPs: if any flow depends on a FAILed flow, mark SKIP
2. **Calibrate coordinates first** (Wave 0 only): Before dispatching, the orchestrator runs the DevKeyMap broadcast and parses the coordinates. These **resolved coordinates** are passed directly to ALL wave agents as a coordinate table — agents NEVER parse DevKeyMap output or make up coordinates.
3. Dispatch `test-wave-agent` via Task tool with **`run_in_background: true`**:
   - Flow definitions (steps, key coordinates, verify)
   - **Explicit coordinate table** (key label → x,y) from the broadcast/calibration — agents use ONLY these values
   - Run directory paths (screenshots/, logs/, flows/)
   - Device info and workarounds
   - Previous wave results (1-line-per-flow status only — NOT full details)
4. **Batch ADB commands**: The orchestrator's prompt to wave agents MUST instruct them to chain multiple ADB commands in a single Bash call using `&&` or `;` to reduce per-call overhead. For example: `adb logcat -c && sleep 0.2 && adb shell input tap X Y && sleep 0.5 && adb logcat -d -s DevKeyPress` in ONE Bash call, not 5 separate calls.
5. Wait for agent using `TaskOutput(task_id, block=true)` — **call exactly once**
6. Agent returns **1-line-per-flow status** (see wave agent Return Format). Do NOT request or process anything beyond this.
7. Record pass/fail/skip per flow from the status lines. That's it — no further parsing.

### Phase 4: Finalize
1. Compile `run-summary.md` from the 1-line status results already in memory — **do NOT re-read flow reports from disk**
2. Capture full session logcat to `logs/full-session.log`
3. Report to user using the Chat Summary Format from output-format.md (pass/fail/skip counts, failure one-liners, report path)
4. **Only read `flows/{flow}.md` from disk if the user asks** to investigate a specific failure

## Data Flow

```
Orchestrator (top-level)              Wave Agent (background)
    |                                      |
    |-- create run directory               |
    |-- write run-summary.md header        |
    |-- run calibration (broadcast/cache)  |
    |-- parse coordinates into table       |
    |-- dispatch wave 0                    |
    |   (Task, run_in_background=true)     |
    |   + coordinate table                 |
    |   + batching instructions ---------->|
    |                                      |-- execute flow steps
    |                                      |-- batch ADB cmds with && in one Bash call
    |                                      |-- check logcat after every step
    |                                      |-- write flows/{flow}.md to DISK
    |                                      |-- save screenshots to DISK
    |                                      |-- save logcat to DISK
    |                                      |-- file defects on failure to DISK
    |   <-- 1-line-per-flow status ------- |  (minimal return, ~3-5 lines total)
    |   (TaskOutput, block=true, once)     |
    |                                      |
    |-- dispatch wave 1                    |
    |   + same coordinate table            |
    |   (same pattern) ------------------>|
    |   ...                                |
    |                                      |
    |-- compile run-summary.md             |
    |   (from status lines in memory,      |
    |    NOT by re-reading disk)           |
    |-- report to user (chat summary)      |
    |                                      |
    |   User asks "what failed in X?" ---> |
    |-- THEN read flows/X.md from disk     |
```

## IME-Specific Setup

Unlike a normal app, the keyboard requires special setup after each APK install:

```bash
# CRITICAL: force-stop ensures fresh code loads (adb install -r does NOT restart IME process)
adb -s $SERIAL shell am force-stop dev.devkey.keyboard
adb -s $SERIAL shell ime enable dev.devkey.keyboard/.LatinIME
adb -s $SERIAL shell ime set dev.devkey.keyboard/.LatinIME
```

The keyboard is tested by opening a text input context (e.g., Contacts app) and tapping keys at known coordinates. Verification is via logcat tags (`DevKeyPress`, `DevKeyMode`) and screenshot vision.

## Key Coordinate System

Key positions are determined at runtime via a 3-tier calibration cascade:
1. **Broadcast**: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` triggers `KeyMapGenerator.dumpToLogcat()` which logs precise coordinates via the `DevKeyMap` logcat tag (debug builds only).
2. **Cache**: `.claude/test-flows/calibration.json` stores device-specific runtime coordinates from the last successful calibration.
3. **Y-scan probe**: If broadcast and cache both fail, a binary search probes for the keyboard top Y by tapping candidate positions.

Pre-calibrated reference values are in `.claude/logs/key-coordinates.md` as a final fallback.

## Device Workarounds (Baked In)

These are passed to every wave agent automatically by the orchestrator:

| Issue | Workaround |
|-------|------------|
| Android 15+ screencap broken | `adb exec-out screencap -p > local.png` (not `/sdcard/`) |
| Git Bash `/sdcard/` mangling | `MSYS_NO_PATHCONV=1` prefix on all ADB commands with `/sdcard/` |
| `adb install -r` doesn't restart IME | Always `am force-stop` + `ime set` after install |

## Model Selection

| Agent | Model | Rationale |
|-------|-------|-----------|
| Top-level orchestrator | Opus (inherited) | Parses complex flag logic, coordinates waves |
| Wave agents | Sonnet | Reliable shell scripting, correct ADB timing, vision capable |

## Prerequisites

- ADB-connected Android device or emulator with USB debugging enabled
- `adb` available in PATH
- Android SDK / Gradle available (for APK build)
- Device has a text input context available (Contacts app or similar)

## Retention

Only the 5 most recent test run directories are kept. The orchestrator deletes older runs at the start of each invocation.

## Reference Documents

| Document | Location |
|----------|----------|
| Flow Registry | `.claude/test-flows/registry.md` |
| ADB Commands | `.claude/skills/test/references/adb-commands.md` |
| IME Testing Patterns | `.claude/skills/test/references/ime-testing-patterns.md` |
| Output Format | `.claude/skills/test/references/output-format.md` |
| Wave Agent | `.claude/agents/test-wave-agent.md` |
| Key Coordinates | `.claude/logs/key-coordinates.md` |
| Calibration Cache | `.claude/test-flows/calibration.json` (device-specific, gitignored) |
