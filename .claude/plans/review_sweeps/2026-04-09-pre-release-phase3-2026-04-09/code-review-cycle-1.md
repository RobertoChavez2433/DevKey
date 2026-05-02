# Code Review: Phase 3 Pre-Release Regression Gate Plan — 2026-04-09 (Cycle 1)

**Plan:** `.claude/plans/2026-04-09-pre-release-phase3.md`
**Tailor:** `.claude/tailor/2026-04-09-pre-release-phase3/`
**Reviewer:** code-review-agent (cycle 1)

## Summary
- Files reviewed: 1 plan (1303 lines), cross-referenced against `tools/e2e/e2e_runner.py`, `app/build.gradle.kts`, `tools/e2e/requirements.txt`, `tools/e2e/tests/test_modifier_combos.py`, `.claude/test-flows/tier-stabilization-status.md`, tailor ground-truth + blast-radius.
- Findings: CRITICAL 0, HIGH 0, MEDIUM 3, LOW 5

## Findings

### [MEDIUM] Skip-reason extraction falls back silently on truthy-but-empty reasons
- **Plan ref:** sub-phase 1.1 step 2
- **Description:** `reason = getattr(e, "msg", None) or (e.args[0] if e.args else "")` — if `pytest.skip("")` is called, the fallback triggers on a falsy msg. Observable impact only: no reason line printed. Current test sites pass non-empty strings so the Phase 3 path is safe.
- **Recommendation:** Accept as-is with a clarifying WHY comment, or simplify to `reason = getattr(e, "msg", None)`.

### [MEDIUM] `_pytest.outcomes.Skipped` is a pytest-internal API
- **Plan ref:** sub-phase 1.1 steps 1 and 2
- **Description:** `from _pytest.outcomes import Skipped` reaches into pytest's private namespace. Stable in practice across pytest 7/8/9 but the plan overstates the portability argument. Lazy-import + `ImportError` fallback mitigates any future reorganization.
- **Recommendation:** Annotate the import with a comment noting the alternative `pytest.skip.Exception` public contract. Non-blocking.

### [MEDIUM] Gate artifact heredocs keep literal `<placeholder>` values
- **Plan ref:** sub-phases 2.2 step 7 / 2.3 / 2.4 / 3.1 step 6 / 3.2 step 6 / 3.3 step 6 / 3.4 step 6 / 5.1 step 5 / 5.2 steps 1/5
- **Description:** Multiple `cat > gate-*.md <<'EOF'` blocks contain placeholder tokens like `<status>`, `<n>`, `<list>` that land on disk as literal angle-brackets because the heredoc is single-quoted. The plan's Phase 6 step 3 only calls out filling the decision artifact, not the per-gate files.
- **Recommendation:** Add an explicit "replace each `<...>` placeholder before committing" step in each sub-phase that writes a gate artifact, or convert to unquoted heredocs with shell variables populated from run-log parsing.

### [LOW] Sub-phase 1.1 step 1 line-range slightly imprecise
- **Plan ref:** "lines 19-26 as of the tailor snapshot"
- **Description:** Current imports run 19-25 with `from typing` on 26. The "after `import traceback`" anchor is correct and unambiguous.
- **Recommendation:** Tighten the prose or drop the range.

### [LOW] Sub-phase 1.1 step 2 replacement range verification
- **Plan ref:** "replace lines 73-113"
- **Description:** Current `run_tests` body spans exactly 73-113. Matches.
- **Recommendation:** None.

### [LOW] Unix shell commands used throughout — consistent with project CLAUDE.md bash mandate
- **Recommendation:** None.

### [LOW] Sub-phase 2.2 step 1 broadcast → curl race
- **Description:** Post-broadcast `curl ?last=5` could race the IME's emission. Practically <1s, but the plan could use the `/wait` long-poll endpoint instead.
- **Recommendation:** Use `curl 'http://127.0.0.1:3950/wait?category=DevKey/IME&event=layout_mode_set&timeout=2000'`.

### [LOW] Sub-phase 6.3 step 3 stages voice-verification file unconditionally
- **Description:** The `# only if modified` comment is human guidance; the literal `git add` on an unmodified file is a no-op.
- **Recommendation:** Acceptable as-is.

## Focused-area verdicts
1. **Sub-phase 1.1 patch correctness** — VERIFIED CORRECT. Python matches branches top-down so `AssertionError` is caught first. KeyboardInterrupt / SystemExit re-raise at lines 228-229. Lazy import with ImportError→None fallback and `_PytestSkipped is not None and isinstance(...)` short-circuit are correct. `run_tests` return arity 3→4 is propagated to `main()`.
2. **Exit-code semantics** — VERIFIED CORRECT. `sys.exit(1 if (failed + errors) > 0 else 0)` preserved. SKIPs count as green.
3. **Sub-phase 4.1 lint flip** — VERIFIED CORRECT against `app/build.gradle.kts:71-74`. Surgical, preserves `checkReleaseBuilds = true`, only `abortOnError` flipped.
4. **Shell snippets** — All valid Git Bash.
5. **File-path correctness** — All paths match tailor ground-truth.
6. **Scope discipline** — Expected-modify set is a strict subset of tailor blast-radius. No writes to any file in the must-not-modify set. `grep` reads of `CandidateView.kt` / `KeyPressLogger.kt` are reads, not edits.
7. **Heredoc safety** — All `cat <<'EOF'` forms use single-quoted EOF disabling expansion. No content-as-EOF collisions. MEDIUM placeholder-leakage finding captured separately.

## Verdict: APPROVE

The 3 MEDIUM findings are non-blocking quality improvements. Recommend addressing the placeholder-replacement finding before committing gate artifacts, but does not block implementation kick-off.
