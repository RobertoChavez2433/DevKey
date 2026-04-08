# Completeness Review — Cycle 1
**Verdict:** REJECT

## CRITICAL findings
1. **Phase 5 omits Fn and Phone layouts.** Spec §2.1 enumerates "Qwerty, Compact, Full, Symbols, **Fn, Phone modes**" as in-scope for long-press coverage. Plan addresses only QwertyLayout (FULL + COMPACT_DEV + COMPACT) and SymbolsLayout. MUST add sub-phases for FnLayout and PhoneLayout.
2. **COMPACT letter-key long-press scope reduction is baked into code steps.** Plan flags it as Discrepancy #2 but `/implement` will execute the deviating `buildCompactLayout` scope comment by default. Decision must block execution via plan-header escalation gate, not a runtime comment. Fix: add a hard gate at the top of Phase 5: "DO NOT BEGIN Phase 5 until the user decides COMPACT scope (include letter-row long-press to match spec §4.2, OR confirmed exclusion)."
3. **Phase 5.4 allows shipping multi-char popups as data-only with UX deferred.** Spec §4.2 "same long-press popup content on every key" requires the user to actually see `à á â ã ä å æ`. Plan's "escape hatch" language must be removed OR Phase 5.4 must require full multi-char Compose Popup implementation. Remove the fallback.
4. **Phase 6.1 defers light theme as "preferred".** Spec §4.1 requires light + dark palette. Non-goals §3 does NOT list light-theme deferral. Remove the dark-only fallback OR escalate via spec amendment before Phase 6.

## MINOR findings
5. **Spec §9 open questions Q1/Q2/Q4 not tracked.** Spec says "resolve during Phase 1." Only Q3 is partially addressed in Phase 2 README. Add a tracking sub-phase (or plan-header item) listing Q1 distribution channel, Q2 min API, Q4 multilingual voice — with placeholders for user decisions before Phase 1 closes.
6. **Sub-phase 1.1 lacks APK-size-failure escalation branch.** Spec §5.2 says "If [APK size] unacceptable, fall back to Q4 option B (on-demand download)." Plan's provenance checklist has a checkbox but no decision branch. Add a note: "If APK size check fails, STOP and escalate — switching to on-demand download is a spec amendment."
7. **Action-key behavior (comma/emoji swap, period long-press) only partial.** Spec §4.2 lists "period long-press, comma/emoji swap, etc." Plan mentions period in 5.2 but comma/emoji swap is not explicitly addressed. Add a verification step in Phase 5 for these action-key behaviors.
8. **Intermediate sub-phases defer verification.** 4.1, 5.1, 5.2, 7.1 all say "deferred." DevKey convention allows this but prefers per-sub-phase build checks. Either add intermediate `./gradlew assembleDebug` OR tighten the "deferred" wording to "intentionally batched — verified at end of phase." (Minor.)

## Clean
- Spec §6 phase reordering has sound dependency rationale; no violations
- Plugin gate Phase 3 fully covered, matches spec §2.4(b)
- Next-word wiring Phase 4 fully covers spec §4.3
- Phase 7.2 voice checklist covers spec §5.3
- No forbidden commands (no `./gradlew test`, no `connectedAndroidTest`)
- No forward pull of Phase 2-5 umbrella work except Phase 7.1 DevKeyLogger instrumentation (small, defended as prep)
