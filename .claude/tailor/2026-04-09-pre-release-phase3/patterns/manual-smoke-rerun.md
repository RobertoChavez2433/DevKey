# Pattern — Manual Smoke Re-run (§3.6)

## Summary
Per `coverage-matrix.md` §"Supplementary manual smoke", the two Phase 1
checklists remain authoritative for final verification alongside the
automated flows. Phase 3.6 re-runs them on the Phase 3 release-candidate
build after the automated gate is green (§3.1-§3.5).

## How we do it
A human operator installs the RC debug APK, enables DevKey as the active
IME, and walks each checklist top-to-bottom on a physical device and/or
emulator. Each checklist box is ticked or annotated with a defect link
(routed per §3.8). A run is "green" when every box is ticked and zero
unrouted defects remain.

## Exemplar 1 — `phase1-regression-smoke.md` (52 lines, clipboard/macros/command/plugins)
Covers the four in-scope "polish-only" features from spec §3:
- Clipboard panel (open, copy, paste, dismiss)
- Macro system (create, trigger, delete)
- Command mode (auto-detect in terminal, manual toggle)
- Plugin system (enable, invoke, disable)

## Exemplar 2 — `phase1-voice-verification.md` (31 lines, voice E2E manual)
Covers what `test_voice.py` cannot fully automate:
- Voice model present + loaded on IME start
- RECORD_AUDIO permission prompt on first tap
- Microphone button visibility in a non-password field
- Transcription commit path
- Permission-denied path (error + recovery)

**Blocker impact:** until voice model files land (blocker B3),
`phase1-voice-verification.md` section 1 cannot be ticked. Phase 3.6 cannot
complete without B3 being resolved or §3.2/§3.6 voice sections being
formally demoted by a spec amendment.

## Reusable operations

| Operation | Where |
|---|---|
| Install RC build | `./gradlew installDebug` |
| Reset IME state | `adb shell am force-stop dev.devkey.keyboard && adb shell ime set dev.devkey.keyboard/.LatinIME` |
| Capture manual screenshot | `adb exec-out screencap -p > out.png` |
| File a defect found during smoke | `gh issue create ... --label "defect,category:<CAT>,area:<AREA>,priority:<P>"` |
| Record the smoke run | Commit the updated checklist (checked boxes) with the run commit SHA |

## Anti-patterns
- **Do not** skip sections of a checklist because the automated flow already
  covered them. Spec §6 is explicit: automated + manual for Phase 3.6.
- **Do not** silently "pass" a checklist item that failed. File the defect
  and bounce per §3.8.
- **Do not** use a debug build with HTTP forwarding still enabled for the
  user-facing smoke — a polished smoke run should match the build a user
  would receive. (Broadcast `ENABLE_DEBUG_SERVER` with empty URL to
  `disableServer()` before the smoke run, or restart the emulator.)
