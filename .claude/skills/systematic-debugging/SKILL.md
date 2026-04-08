---
name: systematic-debugging
description: 10-phase systematic debugging framework for DevKey (Android IME, Kotlin). Prevents rationalization, enforces evidence-based root cause analysis, and ensures mandatory cleanup before close.
modes:
  quick: Single hypothesis, adb logcat filter, no debug server required.
  deep: Full debug server + research agent, multiple hypotheses, instrumented builds.
---

# Systematic Debugging Skill

## Mode Selection

| Signal | Mode |
|--------|------|
| Single clear symptom, known area | Quick |
| Intermittent / hard-to-reproduce | Deep |
| Multiple symptoms or cascading failures | Deep |
| User says "I already tried X" | Deep |
| Time budget > 30 min | Deep |

**Quick mode**: Use `adb logcat -s "DevKey/H001"` for filtered output. One hypothesis. No debug server needed.

**Deep mode**: Start debug server (`node tools/debug-server/server.js`), spawn `debug-research-agent` in background (see `references/debug-session-management.md`), use numbered hypothesis IDs.

---

## Anti-Rationalization Rules

These rules exist because the most common debugging failure is jumping to a fix that "makes sense" before evidence confirms it.

1. **No fix without evidence.** If you cannot point to a specific log line, stack trace, or test failure that confirms the root cause, you do not have evidence. Stop and instrument more.
2. **One hypothesis at a time.** Form it explicitly, instrument for it, run, evaluate. Do not hedge by implementing two fixes simultaneously.
3. **Falsify, don't confirm.** Design each reproduction run to be able to disprove your hypothesis, not just support it.
4. **Log before you look.** Add `DevKeyLogger.hypothesis()` markers before running. Do not try to read existing logs to infer what happened without them.

---

## The 10 Phases

### Phase 1 — Triage & Reproduce

**Goal**: Understand the symptom precisely and check if this pattern is already known.

Steps:
1. Ask the reproduction interview questions (see `references/debug-session-management.md`).
2. Query GitHub Issues for known patterns:
   ```bash
   gh issue list --repo RobertoChavez2433/DevKey --label defect --state open --limit 30
   # Narrow by area if known:
   gh issue list --repo RobertoChavez2433/DevKey --label "area:<area>" --state open --json number,title,body,labels --limit 20
   ```
3. Scan titles/bodies for matching symptoms. If found: link the open issue, use its root cause as your starting hypothesis, skip to Phase 3.
4. Attempt manual reproduction. Record exact steps, app context, keyboard mode, key sequence.
5. Classify area: `ime-lifecycle` | `compose-ui` | `modifier-state` | `native-jni` | `build-test` | `text-input` | `voice-dictation`

**Exit**: Reproducible symptom confirmed or reproduction steps documented even if intermittent.

---

### Phase 2 — Hypothesis Formation

**Goal**: Produce exactly one falsifiable hypothesis before touching any code.

Format:
```
H001: [Component] [does/does not] [behavior] when [condition].
Evidence needed: [what log/test output would confirm or deny this]
```

Example:
```
H001: ModifierStateManager does not clear Shift state when input focus changes apps.
Evidence needed: DevKeyLogger.modifier() log showing shift_active=true after onFinishInput.
```

Write the hypothesis to the session file before proceeding.

---

### Phase 3 — Instrumentation

**Goal**: Add `DevKeyLogger.hypothesis()` markers at the exact points that would confirm or deny H001.

Rules:
- Tag every marker with the hypothesis ID: `DevKeyLogger.hypothesis("H001", Category.MODIFIER_STATE, "shift state at focus change", mapOf("shift_active" to shiftActive))`
- NEVER log text content being typed — IME sees ALL keystrokes including passwords. Log key codes, not characters.
- Never log tokens, auth strings, or user credentials.
- Add markers at entry and exit of suspected code paths, not just at the symptom.
- Quick mode: one logcat tag. Deep mode: markers flush to debug server.

See `references/log-investigation-and-instrumentation.md` for DevKeyLogger API and tagging conventions.

---

### Phase 4 — Reproduction Run

**Goal**: Build and run with instrumentation active.

```bash
# Install and force-stop to reset IME state
./gradlew installDebug && adb shell am force-stop dev.devkey.keyboard && adb shell ime set dev.devkey.keyboard/.LatinIME

# Quick mode: filter by hypothesis tag
adb logcat -s "DevKey/H001"

# Deep mode: tail the debug server
curl http://localhost:3947/logs?last=50&hypothesis=H001
```

Reproduce the exact steps documented in Phase 1. Capture full log output.

---

### Phase 5 — Log Analysis

**Goal**: Read what actually happened vs. what the hypothesis predicted.

Steps:
1. Pull hypothesis-tagged logs: `curl http://localhost:3947/logs?hypothesis=H001`
2. Identify the first point where actual behavior diverges from expected behavior.
3. Ask: does this confirm, deny, or partially support H001?

Outcomes:
- **Confirmed**: Proceed to Phase 6.
- **Denied**: Return to Phase 2. Form H002 based on what you actually saw.
- **Partial**: Add targeted instrumentation around the divergence point, re-run Phase 4.
- **No data / markers not reached**: The code path was not executed. Re-examine Phase 1 reproduction steps.

Maximum 3 instrumentation iterations per hypothesis before escalating to Deep mode or declaring hypothesis exhausted.

---

### Phase 6 — Root Cause Identification

**Goal**: Name the exact defect: file, function, line range, and mechanism.

Root cause statement format:
```
ROOT CAUSE: In <file>:<function> (line ~N), <mechanism> causes <symptom> when <condition>.
Confirmed by: <log line or test output>
```

Do not proceed to Phase 7 without a complete root cause statement. "Probably in the modifier code somewhere" is not a root cause.

---

### Phase 7 — Fix Proposal

**Goal**: Design the fix before writing it.

Write out:
1. What change will be made (file, function, mechanism).
2. Why this change addresses the root cause (not just the symptom).
3. What could go wrong / side effects to watch for.
4. What test (unit or manual) will confirm the fix.

Review the fix proposal against the root cause statement. If the fix does not directly address the mechanism named in Phase 6, stop and revise.

---

### Phase 8 — Fix Implementation

**Goal**: Implement the fix exactly as proposed in Phase 7.

- Use `/implement` skill for non-trivial changes.
- Keep the fix minimal — resist the urge to refactor adjacent code while you're there.
- Run unit tests after implementation: `./gradlew test`

---

### Phase 9 — Verification

**Goal**: Confirm the fix resolves the original symptom without regression.

Steps:
1. Build and install: `./gradlew installDebug && adb shell am force-stop dev.devkey.keyboard && adb shell ime set dev.devkey.keyboard/.LatinIME`
2. Reproduce the original steps from Phase 1.
3. Confirm symptom is gone.
4. Check for regressions in adjacent paths (see `references/codebase-tracing-paths.md`).
5. Run full test suite: `./gradlew test`

If symptom persists: return to Phase 2 with a new hypothesis informed by what you now know.

---

### Phase 10 — Defect Log & Cleanup

**Goal**: Record the pattern and remove all instrumentation.

**Cleanup gate** (mandatory — do not skip):
```bash
# Must return zero hits before session closes
Grep "hypothesis(" app/src/
```

If any hits remain, remove those `DevKeyLogger.hypothesis()` calls before closing the session.

**File a GH Issue if this is a new pattern** (not already tracked):
```bash
gh issue create --repo RobertoChavez2433/DevKey \
  --title "[CATEGORY] YYYY-MM-DD: Brief Title" \
  --label "defect,category:<CAT>,area:<AREA>,priority:<P>" \
  --body "$(cat <<'EOF'
**Pattern**: <what to avoid — 1 line>
**Prevention**: <how to avoid — 1-2 lines>
**Ref**: file:line
EOF
)"
```

See `references/github-issues-integration.md` for label taxonomy and category/area mapping.

**Save session file** to `.claude/debug-sessions/YYYY-MM-DD-{slug}.md` (30-day retention).

---

## User Signal Handlers

| User says | Action |
|-----------|--------|
| "It's still broken" | Return to Phase 5. Re-read logs. Do NOT jump to a new fix without re-running the analysis. |
| "Try something else" | Return to Phase 2. Form a new hypothesis. Document why H001 was rejected. |
| "Just make it work" | Acknowledge. Explain the next single concrete step. Do not skip phases. |
| "I think it's in [X]" | Treat as a hypothesis. Write it as H00N and instrument for it. Do not accept it as confirmed. |

---

## Stop Conditions

Stop and report current state when:
- **Budget exhausted**: 3+ hypotheses tested without root cause — escalate to async investigation or file an issue.
- **Hypothesis exhausted**: All plausible hypotheses denied — document what was ruled out, file an issue for future investigation.
- **Reproduction lost**: Symptom stops occurring — document last known reproduction steps, note any system changes.

Stop condition report format:
```
STOPPED: <condition>
Hypotheses tested: H001 (<result>), H002 (<result>), ...
Evidence collected: <what is now known>
Next step: <recommended action>
```

---

## Root Cause Report Format

Saved to `.claude/debug-sessions/YYYY-MM-DD-{slug}.md`:

```markdown
# Debug Session: {slug}
Date: YYYY-MM-DD
Area: {area}

## Symptom
<user-reported behavior>

## Reproduction Steps
<exact steps>

## Hypotheses
- H001: <statement> — DENIED (reason)
- H002: <statement> — CONFIRMED

## Root Cause
In <file>:<function> (line ~N), <mechanism> causes <symptom> when <condition>.
Confirmed by: <log excerpt>

## Fix
<what was changed and why>

## Verification
<how fix was confirmed>

## GH Issue
<link or "existing issue #N">
```

---

## Session Retention

Debug sessions in `.claude/debug-sessions/` are retained for 30 days. Sessions older than 30 days may be deleted. GH Issues are the permanent record.
