# Test Output Format Reference

Documentation format templates for test wave agents and the top-level orchestrator. All test runs produce a self-contained results directory with screenshots, logs, flow reports, and a summary.

## Directory Layout

```
.claude/test-results/
  YYYY-MM-DD_HHmm_{descriptor}/     # Per-run directory
    run-summary.md                   # Overall results table
    screenshots/                     # All screenshots
      {flow}-{step:02d}-{desc}.png   # Step screenshots
      {flow}-final.png               # Final verification screenshot
    logs/                            # Logcat captures
      {flow}-keypress.log            # DevKeyPress tag output
      {flow}-mode.log                # DevKeyMode tag output
      {flow}-warnings.log            # Per-flow warnings/errors
      full-session.log               # Complete session logcat
    flows/                           # Per-flow detailed reports
      {flow}.md                      # Step-by-step with screenshot refs
```

## Descriptor Naming Convention

The descriptor suffix is derived from the test invocation flags:

| Invocation | Descriptor |
|------------|-----------|
| `/test --smoke` | `_smoke` |
| `/test --modifiers --modes` | `_modifiers-modes` |
| `/test --full` | `_full` |
| `/test typing` | `_typing` |
| `/test caps-lock modifier-combos` | `_caps-lock_modifier-combos` |
| `/test` (auto-select) | `_auto` |

Rules:
- Feature flags are joined with `-` (e.g., `modifiers-modes`)
- Named flows use `_` as separator (e.g., `caps-lock_modifier-combos`)
- Auto-select uses `_auto`

## Run Summary Format (`run-summary.md`)

```markdown
# Test Run: YYYY-MM-DD_HHmm_{descriptor}

**Date**: YYYY-MM-DD HH:mm
**Branch**: {git branch name}
**Device**: {model} (Android {version}, {resolution})
**Layout Mode**: {FULL/COMPACT/COMPACT_DEV}
**Tier**: {smoke | feature | full | custom}
**Invocation**: /test {flags}
**Duration**: {total time}

## Results

| # | Flow | Status | Duration | Defects | Notes |
|---|------|--------|----------|---------|-------|
| 1 | ime-setup | PASS | 12s | 0 | -- |
| 2 | typing | PASS | 45s | 0 | -- |
| 3 | caps-lock | FAIL | 30s | 1 | Double-tap timing inconsistent |

## Summary
- **Total**: {N} | **Pass**: {P} | **Fail**: {F} | **Skip**: {S}
- **Defects filed**: {count} -> .claude/autoload/_defects.md
- **Screenshots**: {count} -> screenshots/
- **Logs**: {count} -> logs/

## Defects Filed

| # | Flow | Feature | Description |
|---|------|---------|-------------|
| 1 | caps-lock | modifiers | Double-tap timing inconsistent |

## Wave Execution

| Wave | Flows | Status | Duration |
|------|-------|--------|----------|
| 0 | ime-setup | 1/1 PASS | 12s |
| 1 | typing, modifier-states | 2/2 PASS | 60s |
| 2 | caps-lock, mode-switching | 1/2 PASS | 75s |
```

## Flow Report Format (`flows/{flow}.md`)

Each flow gets a detailed step-by-step report. Wave agents write these during execution.

```markdown
# Flow: {flow-name}

**Status**: PASS | FAIL | SKIP
**Duration**: {seconds}s
**Steps**: {completed}/{total}
**Feature**: {feature-name}
**Wave**: {wave-number}

## Steps

### Step 1: {step description}
- **Action**: {what was done}
- **Key/Element**: {key label, coordinate, or logcat tag used}
- **Result**: SUCCESS | FAIL | SKIP
- **Screenshot**: ../screenshots/{flow}-01-{desc}.png
- **Logcat**: Clean (0 warnings) | {N} warnings (non-critical) | ERROR: {error text}

### Step 2: {step description}
- **Action**: {what was done}
- **Key/Element**: {key label, coordinate, or logcat tag used}
- **Result**: SUCCESS
- **Screenshot**: ../screenshots/{flow}-02-{desc}.png
- **Logcat**: Clean

## Logcat Assertions
- [PASS] DevKeyPress: tap code=104 label=h shift=false
- [PASS] DevKeyPress: ModifierTransition SHIFT: OFF -> ONE_SHOT
- [FAIL] Expected DevKeyMode: toggleMode: Normal -> Symbols -- not found in logcat

## Logcat Summary
- **Total warnings**: {count}
- **IME errors**: {count}
- **Key events captured**: {count}
- **Critical**: {any critical log lines, or "None"}

## Notes
{observations, timing issues, workarounds applied, or context for future runs}
```

## Screenshot Naming Convention

Format: `{flow}-{step:02d}-{short-description}.png`

Examples:
- `typing-01-hello.png`
- `caps-lock-01-shift-tapped.png`
- `caps-lock-02-double-tap-locked.png`
- `caps-lock-03-letters-typed.png`
- `caps-lock-final.png`
- `mode-switching-01-symbols.png`
- `mode-switching-02-back-to-normal.png`
- `mode-switching-final.png`

Rules:
- Step numbers are zero-padded to 2 digits (01, 02, ... 99)
- Short description is lowercase, hyphen-separated, max 30 chars
- Final verification screenshot uses `-final.png` suffix
- Pre-flow state screenshots use `-00-initial.png`
- All captured via: `MSYS_NO_PATHCONV=1 adb exec-out screencap -p > ./path/file.png`

## Logcat File Naming Convention

Format: `{flow}-{tag}.log`

Examples:
- `typing-keypress.log` — DevKeyPress tag output
- `typing-mode.log` — DevKeyMode tag output
- `typing-warnings.log` — All warnings/errors
- `full-session.log` — Complete session, captured at end of run

Content format for per-flow logcat:
```
=== Logcat for flow: {flow-name} ===
=== Captured: YYYY-MM-DD HH:mm:ss ===
=== Tag: {DevKeyPress | DevKeyMode | *:W} ===

{raw logcat output}
```

## Defect Filing Format

When a flow fails, the wave agent files a defect to `.claude/autoload/_defects.md`:

```markdown
### [IME] {YYYY-MM-DD}: {flow-name} flow failure (auto-test)
**Status**: OPEN
**Source**: Automated test run {run-directory-name}
**Symptom**: {failure description from the flow report}
**Step**: Step {N} -- {step description}
**Logcat**: {relevant error lines, max 5 lines}
**Screenshot**: .claude/test-results/{run-dir}/screenshots/{flow}-{step}-{desc}.png
**Suggested cause**: {assessment based on logs + screenshots + flow context}
```

Rules:
- Check for existing duplicate defects before filing
- Max 7 active defects — archive oldest to `defects-archive.md` if full
- Defects filed immediately on failure (not batched)

## Retention Policy

- **Keep last 5 runs** — the orchestrator deletes the oldest run directory when a 6th run starts
- **Screenshots are gitignored** — `.claude/test-results/` content is not committed
- **run-summary.md is human-readable** — designed for quick scanning in any text editor
- **Flow reports reference screenshots with relative paths** — `../screenshots/` prefix

## Chat Summary Format

After a test run completes, the orchestrator reports to the user in this format:

```
Test Run: X/Y PASS | Z FAIL | W SKIP ({duration})

Failures:
  - [{feature}] {flow-name}: {one-line failure description}
  - [{feature}] {flow-name}: {one-line failure description}

Skipped (upstream dependency failed):
  - {flow-name} (depends on {failed-flow})

Report: .claude/test-results/{run-dir}/run-summary.md
Screenshots: .claude/test-results/{run-dir}/screenshots/ ({count} files)
Defects filed: N new
```
