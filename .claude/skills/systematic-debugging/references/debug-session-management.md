# Debug Session Management

## Server Setup (Deep Mode Only)

```bash
# Start debug server (port 3947)
node tools/debug-server/server.js

# ADB reverse port forwarding (device → host)
adb reverse tcp:3947 tcp:3947

# Verify server is reachable from device
curl http://localhost:3947/health
```

## App Launch / Reset

```bash
# Full reset: build, install, force-stop, re-activate IME
./gradlew installDebug && \
  adb shell am force-stop dev.devkey.keyboard && \
  adb shell ime set dev.devkey.keyboard/.LatinIME
```

Force-stopping before re-activating ensures the IME is freshly initialized. This matters when debugging lifecycle or state issues.

---

## Session Lifecycle

```
Start session
    |
    v
Document symptom (Phase 1 triage + GH Issues query)
    |
    v
Form hypothesis (Phase 2)
    |
    v
Add DevKeyLogger.hypothesis() markers (Phase 3)
    |
    v
Build + Install + Reproduce (Phase 4)
    |
    v
Analyze logs (Phase 5)
    |
    v
[Denied?] -----> Form new hypothesis (Phase 2)
    |
[Confirmed?]
    |
    v
Name root cause (Phase 6)
    |
    v
Design fix (Phase 7) → Implement (Phase 8)
    |
    v
Verify fix (Phase 9)
    |
    v
Cleanup: remove hypothesis() markers
    |
    v
File GH Issue if new pattern (Phase 10)
    |
    v
Save session file → Close
```

---

## Cleanup Gate

**Mandatory before session close.** Run this and confirm zero output:

```bash
Grep "hypothesis(" app/src/
```

If any hits remain:
1. Open each file at the indicated line.
2. Remove the `DevKeyLogger.hypothesis()` call.
3. Re-run the grep until it returns zero hits.
4. Then close the session.

Do not commit hypothesis markers to the main branch. They are investigation artifacts, not production logging.

---

## Session Files

Save to: `.claude/debug-sessions/YYYY-MM-DD-{slug}.md`

Naming: Use a short slug that identifies the bug area, e.g.:
- `2026-04-08-modifier-shift-focus-loss.md`
- `2026-04-08-suggestion-jni-crash-api26.md`

**Retention**: 30 days. Sessions older than 30 days may be deleted. GH Issues are the permanent record — link the session file in the issue body when filing.

---

## Research Agent Management (Deep Mode)

When entering Deep mode, spawn the `debug-research-agent` in the background to parallelize codebase research while you instrument and reproduce the bug.

How to launch:
- Use the `run_in_background` parameter when invoking the agent.
- Provide the symptom description, area, and hypothesis H001 as the prompt.
- The agent will search the codebase, query GH Issues for known patterns, and produce a research report.
- Continue with Phases 2-4 in the main thread while the agent runs.
- When the agent completes, incorporate its findings into your Phase 5 log analysis.

The agent is read-only on code (no Edit/Write). Its Bash access is limited to `gh issue list` queries only.

---

## Reproduction Interview Questions

When a user reports a bug, ask these questions before Phase 1 triage to get a complete symptom picture:

1. **What app were you typing in?** (package name or app name — different input types behave differently)
2. **What keyboard mode were you in?** (QWERTY / Symbols / Fn layer / Command mode)
3. **What key or key sequence did you press?** (describe key by label, e.g. "Shift then A" not the resulting character)
4. **What did you expect to happen?**
5. **What actually happened?**
6. **Is it reproducible every time, or intermittent?**
7. **Does it happen in all apps or only specific ones?**
8. **Were you using any modifier keys held down?** (Shift, Ctrl, Alt)
9. **What Android version / device?**
10. **Does it happen on a fresh install (cleared app data)?**

Answers to questions 1-3 are required before instrumentation. Questions 4-10 add confidence and may redirect the area classification.
