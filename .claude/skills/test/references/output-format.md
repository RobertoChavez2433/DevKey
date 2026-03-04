# Test Output Format Reference

Structured output format for `/test` skill runs.

## Directory Layout

```
.claude/test-results/
  YYYY-MM-DD_HHmm_{descriptor}/
    run-summary.md              # Overall run report (incrementally written)
    screenshots/                # All screenshots
      {flow}-{step:02d}-{desc}.png
      {flow}-final.png
    logs/                       # Logcat captures
      {flow}-keypress.log
      {flow}-mode.log
      {flow}-warnings.log
    flows/                      # Per-flow reports
      {flow}.md
```

## Descriptor Naming

The `{descriptor}` is derived from the invocation:
- `/test --smoke` → `smoke`
- `/test --full` → `full`
- `/test typing` → `typing`
- `/test modifier-states caps-lock` → `modifier-states_caps-lock`
- `/test` (auto) → `auto`

## run-summary.md Format

```markdown
# Test Run YYYY-MM-DD HH:mm

**Branch**: {branch}
**Trigger**: /test {args}
**Flows selected**: {comma-separated flow names}
**Device**: {model} ({resolution})
**Layout Mode**: {FULL/COMPACT/COMPACT_DEV}

## Summary: X/Y PASS | Z FAIL | W SKIP

| Flow | Status | Duration | Wave |
|------|--------|----------|------|
| {flow} | {PASS/FAIL/SKIP} | {Xs} | {N} |

## Failures

### {flow-name} (Wave N)
**Symptom**: {description}
**Screenshot**: {path}
**Logcat**: {relevant lines}
**Suggested root cause**: {assessment}

## Screenshots
- {list of all screenshot paths}
```

## flows/{flow}.md Format

```markdown
# {flow-name}: {PASS|FAIL} ({duration}s)

## Steps
| Step | Action | Expected | Actual | Status |
|------|--------|----------|--------|--------|
| 1 | {action} | {expected logcat/visual} | {actual} | {PASS/FAIL} |

## Logcat Assertions
- {N/M passed}
- {relevant logcat lines}

## Screenshots
- {list of screenshot files for this flow}

## Notes
{observations, warnings, retry attempts}
```

## Screenshot Naming Convention

- Step screenshots: `{flow}-{step:02d}-{desc}.png`
  - Example: `typing-01-hello.png`, `caps-lock-03-locked.png`
- Final screenshot: `{flow}-final.png`
- All captured via: `MSYS_NO_PATHCONV=1 adb exec-out screencap -p > ./path/file.png`

## Logcat File Naming

- `{flow}-keypress.log` — DevKeyPress tag output
- `{flow}-mode.log` — DevKeyMode tag output
- `{flow}-warnings.log` — All warnings/errors from the flow

## Defect Filing

On flow FAIL, auto-file to `.claude/autoload/_defects.md`:

```markdown
### [IME] {date}: {flow-name} flow failure (auto-test)
**Pattern**: {failure description}
**Prevention**: {suggested fix from agent assessment}
**Ref**: @{relevant source file}
```

Rules:
- Check for existing duplicate defects before filing
- Max 7 active defects — archive oldest to `defects-archive.md` if full
- Defects filed immediately on failure (not batched)

## Retention Policy

- Keep last 5 test run directories
- Orchestrator deletes oldest runs at start of each invocation
- Deletion order: by directory timestamp (oldest first)

## Chat Summary Format

```
Test Run: X/Y PASS | Z FAIL | W SKIP ({duration})

[Per failure: one-line description with feature tag]
[Per skip: one-line noting dependency]

Report: .claude/test-results/{run-dir}/run-summary.md
Screenshots: .claude/test-results/{run-dir}/screenshots/
Defects filed: N new
```
