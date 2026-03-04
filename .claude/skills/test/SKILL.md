---
name: test
description: "Build, install, and run automated keyboard test flows on an ADB-connected Android device/emulator using ADB input, logcat verification, and Claude vision. Supports tier flags, per-feature targeting, and structured output."
user-invocable: true
---

# /test Skill

Automated ADB-based testing for DevKey keyboard. Builds the APK, installs on a connected device/emulator, sets up the IME, and runs keyboard test flows with logcat + visual verification. Produces structured output with screenshots, logcat, and per-flow reports.

## Iron Laws

> 1. **ORCHESTRATOR STAYS THIN.** Dispatches wave agents via Task tool. Never processes raw screenshots or logcat dumps.
> 2. **LOGCAT AFTER EVERY INTERACTION.** Wave agents check `adb logcat` after every ADB action — catches IME errors and state drift.
> 3. **WRITE TO DISK INCREMENTALLY.** Wave agents write flow reports and file defects after each flow. Nothing held only in memory.

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

## Flag Resolution Logic

1. Parse all flags
2. Expand tier flags
3. Expand feature flags
4. Union all expanded flows (deduplicate)
5. Resolve transitive deps recursively
6. Deduplicate again
7. Topological sort

## How It Works

### Phase 1: Pre-Flight
1. ADB check, device info, build check (rebuild if APK >1hr old), install, set up IME
2. Create run directory with screenshots/, logs/, flows/ subdirs
3. Cleanup: delete oldest run if >5 exist
4. Write run-summary.md header

### Phase 2: Wave Computation
Topological sort of dep graph into wave groups

### Phase 3: Wave Dispatch
For each wave (sequential): check SKIPs, dispatch test-wave-agent via Task tool, parse results

### Phase 4: Finalize
Compile run-summary.md, capture full session logcat, report to user

## IME-Specific Setup

Unlike a normal app, the keyboard requires special setup after each APK install:

```bash
# CRITICAL: force-stop ensures fresh code loads (adb install -r does NOT restart IME process)
adb shell am force-stop dev.devkey.keyboard
adb shell ime enable dev.devkey.keyboard/.LatinIME
adb shell ime set dev.devkey.keyboard/.LatinIME
```

The keyboard is tested by opening a text input context (e.g., Contacts app) and tapping keys at known coordinates. Verification is via logcat tags (`DevKeyPress`, `DevKeyMode`) and screenshot vision.

## Key Coordinate System

Key positions are determined by `KeyMapGenerator` output via the `DevKeyMap` logcat tag on debug builds. All calculated coordinates need a **Y offset** applied for the specific device/emulator. On `emulator-5554` (1080x2400, 2.625 density), the offset is **-153px**.

## Device Workarounds (Baked In)

These are passed to every wave agent automatically by the orchestrator:

| Issue | Workaround |
|-------|------------|
| Android 15+ screencap broken | `adb exec-out screencap -p > local.png` (not `/sdcard/`) |
| Git Bash `/sdcard/` mangling | `MSYS_NO_PATHCONV=1` prefix on all ADB commands with `/sdcard/` |
| `adb install -r` doesn't restart IME | Always `am force-stop` + `ime set` after install |

## Model Selection

| Agent | Model |
|-------|-------|
| Top-level orchestrator | Opus (inherited) |
| Wave agents | Haiku |

## Reference Documents

| Document | Location |
|----------|----------|
| Flow Registry | `.claude/test-flows/registry.md` |
| ADB Commands | `.claude/skills/test/references/adb-commands.md` |
| IME Testing Patterns | `.claude/skills/test/references/ime-testing-patterns.md` |
| Output Format | `.claude/skills/test/references/output-format.md` |
| Wave Agent | `.claude/agents/test-wave-agent.md` |
