# Security Review: Phase 3 Regression Gate Plan — 2026-04-09 (Cycle 1)

**Plan:** `.claude/plans/2026-04-09-pre-release-phase3.md`
**Reviewer:** security-agent (cycle 1)

## Summary
- Domains audited: 9 security concerns from request
- Findings: CRITICAL 0, HIGH 0, MEDIUM 1, LOW 2, INFO 6
- OWASP items triggered: M9 (Insecure Data Storage)

## Findings

### [MEDIUM] `.claude/test-results/` is NOT gitignored despite plan assertion
- **Plan ref:** rationale comment at end of Phase 3 ("which is gitignored in the project")
- **Source verification:** `.gitignore` contains only `settings.local.json`, `screenshots/`, `test-flows/calibration.json` under `.claude/`. `test-results/` is NOT present.
- **Description:** Plan's rationale is factually wrong. Sub-phase 6.3 deliberately commits the directory, which is internally consistent, but the misleading rationale could induce a future operator to dump raw logcat here assuming local-only scratch space. `adb logcat -d` on a debug build can contain keystroke-level data.
- **Recommendation:** Either (1) add `.claude/test-results/` to `.gitignore` and change sub-phase 6.3 to `git add -f` only the specific intended artifacts, OR (2) remove the false rationale comment and add an explicit banner: "do not dump raw logcat here — this directory is committed to the repo." Option 1 preferred.

### [LOW] VERBOSE traceback path could leak repr'd state into committed logs
- **Plan ref:** sub-phase 1.1 step 2 (`if os.environ.get("DEVKEY_E2E_VERBOSE")`)
- **Description:** Subsumed by the MEDIUM above — if `.claude/test-results/` stays in the repo, operators setting DEVKEY_E2E_VERBOSE during triage will commit tracebacks with local variable repr'd state.
- **Recommendation:** Add a note to sub-phase 2.2 step 3 that DEVKEY_E2E_VERBOSE is for local-only triage, not for runs whose logs land in the repo.

### [LOW] Debug-build prerequisite for ENABLE_DEBUG_SERVER not explicit in runbook
- **Plan ref:** sub-phase 2.1 step 5
- **Source verification:** `LatinIME.kt:410` gates the receiver behind `isDebugBuild()`. `LatinIME.kt:448-451` scrubs the URL. `LatinIME.kt:457-461` registers with `RECEIVER_NOT_EXPORTED`. All invariants hold.
- **Description:** Plan verifies the package is installed but not that it is the debug variant. An accidentally-installed release build silently drops the broadcast.
- **Recommendation:** Hoist a `./gradlew installDebug` call or `dumpsys package` variant check into sub-phase 2.1 step 2.

### [INFO] pytest supply-chain posture acceptable
- pytest is first-party PyPA; floor `>=7.0` is reasonable (needed for `Skipped.msg` attribute stability); lower risk than existing `scikit-image` / `Pillow`. No concern.

### [INFO] Lint flip does not mask any other behavior
- Source verification: `app/build.gradle.kts:71-74` contains only `checkReleaseBuilds` + `abortOnError`. No disabled checks, no baseline, no severity overrides. Flip surfaces errors rather than hiding them.
- **Recommendation:** Add to sub-phase 4.2 step 3 triage rules: "If the lint check id belongs to Android Lint's Security category (HardcodedDebugMode, TrustAllX509TrustManager, ExportedReceiver, etc.), escalate to Bucket 4 — do NOT surgically `@Suppress` at the call site."

### [INFO] Label discipline mostly correct with one exception
- sub-phase 3.1 step 4 hardcodes `category:prediction,area:ime` — neither value is in CLAUDE.md's canonical lists (valid categories: NATIVE/ANDROID/BUILD/IME; valid areas: ime-lifecycle, compose-ui, modifier-state, native-jni, build-test, text-input, voice-dictation).
- **Recommendation:** Fix to `category:IME,area:text-input,priority:high`.

### [INFO] Driver log capture — no raw logcat dumps requested
- Plan captures via structured `/logs?category=...` only. Phase 2 source-level privacy guards apply. No PII dump at plan level.

### [INFO] Manual smoke — no request to paste literal content
- Plan does not ask operators to paste clipboard/macro/voice content into artifacts.
- **Recommendation:** Add a one-line caution at top of sub-phase 5.1: "describe failure modes in abstract terms — do not paste literal clipboard content, macro bodies, or voice transcripts."

### [INFO] Heredoc discipline correct
- All `cat <<'EOF'` forms are single-quoted; no user-controlled values flow in; no `${VAR}` / `$(cmd)` interpolation.

## Clean concerns
- #2 Supply-chain (pytest) — clean
- #3 Lint flip side-effects — clean (verified in source)
- #5 Driver handshake loopback — clean (server.js:242 verified)
- #6 Driver log capture — clean
- #7 Manual smoke PII — clean
- #9 Heredoc discipline — clean

## Source verification performed
- `tools/debug-server/server.js:242` — loopback `127.0.0.1` binding confirmed
- `LatinIME.kt:410-461` — isDebugBuild gate + RECEIVER_NOT_EXPORTED + URL scrub confirmed
- `app/build.gradle.kts:71-74` — no hidden lint suppressions
- `.gitignore` — `.claude/test-results/` absent (drives MEDIUM finding)
- `tools/e2e/e2e_runner.py:80-113` — current handler confirmed as patch target

## Verdict: APPROVE

The plan is safe to execute with the non-blocking caveats above. No new attack surface, no loopback exposure, no raw logcat capture, no `git add -A` violations. Source verification confirms the invariants the plan relies on. The MEDIUM .gitignore finding should be fixed in the same stabilization commit stream for hygiene.
