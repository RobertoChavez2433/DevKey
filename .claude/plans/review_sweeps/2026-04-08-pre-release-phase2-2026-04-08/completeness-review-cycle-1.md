# Completeness Review — pre-release-phase2 — Cycle 1

**REJECT**

**Requirements reviewed:** 25
**Findings:** 11 (7 critical, 4 minor)

## Findings

### Finding 1 — DRIFT (Voice round-trip does not verify committed text)
**Severity:** critical
**Location:** Phase 4.1, `test_voice_round_trip_committed_text`
**Spec reference:** §5.4 — "verify expected text committed in the target EditText"; §2.2 item 3 — "verify committed text"
**Description:** Plan asserts only on `processing_complete.result_length > 0` and IDLE state. Never reads back EditText contents. Privacy contract does not preclude reading the field and asserting `len(text) > 0`.
**Fix:** Add EditText readback via `adb.get_text_field_content(serial)` (already in `lib/adb.py`) and assert non-empty.

### Finding 2 — MISSING (Long-press coverage is a smoke subset, not "every key in every mode")
**Severity:** critical
**Location:** Phase 4.3, `COMPACT_LONG_PRESS_KEYS` (7 keys only)
**Spec reference:** §2.2 item 3 — "Long-press popup coverage (every key in every mode → verify popup content matches SwiftKey reference)"
**Description:** Plan covers 7 hard-coded COMPACT keys + one FULL key. Spec mandates EVERY key in EVERY mode (Qwerty, Compact, Full, Symbols, Fn, Phone). COMPACT_DEV digit long-press unverified. Plan never verifies popup content against SwiftKey reference.
**Fix:** Programmatically enumerate `QwertyLayout.buildFullLayout/buildCompactLayout/buildCompactDevLayout` + `SymbolsLayout` to generate per-key assertions; add popup screenshot diff with SKIP-on-missing-reference.

### Finding 3 — DRIFT (Next-word flow asserts on log, not on candidate strip UI)
**Severity:** critical
**Location:** Phase 4.2, all three tests in `test_next_word.py`
**Spec reference:** §2.2 — "verify candidate strip populated with next-word suggestions"; §4.3 — "candidate strip auto-populates"
**Description:** Plan instruments `setNextSuggestions` with a log event. Spec demands verification the candidate strip is actually populated.
**Fix:** Either (a) add a second log event emitted by `CandidateView` after render, or (b) UI dump via `uiautomator` to verify strip contents.

### Finding 4 — MISSING (Tier 2.4 stabilization not delivered — only enabled)
**Severity:** critical
**Location:** Phase 5
**Spec reference:** §6 Phase 2 item 2.4 — "FULL + COMPACT + COMPACT_DEV to 100% green on emulator"
**Description:** Plan adds infrastructure (SET_LAYOUT_MODE broadcast, helper, one round-trip test) + coverage matrix. Never executes existing flows on COMPACT/COMPACT_DEV, identifies failures, or stabilizes them. Sub-phase 5.1 even has TO-DO.
**Fix:** Add a dedicated sub-phase that runs all existing test modules against all three modes, iterates on failures, exit criterion = 100% green.

### Finding 5 — MISSING (Spec §2.5 — every Phase 1 feature needs automated coverage)
**Severity:** critical
**Location:** Phase 5.4 `coverage-matrix.md` "Manual smoke only" section
**Spec reference:** §6 Phase 2 item 2.5 — "every feature in Phase 1 has at least one automated test flow"
**Description:** Matrix exempts clipboard, macro system, command mode, plugin system as "manual only". Spec §2.5 is explicit that every Phase 1 feature needs automated coverage. `modifier-combos` listed as "registry.md only, add module in stabilization" — another gap.
**Fix:** Add minimal smoke test modules for clipboard/macros/command/plugin + a test module for modifier-combos.

### Finding 6 — DRIFT (Sleeps remain in the harness)
**Severity:** critical
**Location:** Phase 3.6 `set_layout_mode` (`time.sleep(0.8)`); Phase 3.1 `load_key_map` (`time.sleep(0.3)`)
**Spec reference:** §2.2 — "Replaces any remaining polling/sleep-based synchronization"
**Description:** Plan introduces two new sleeps. Spec is unambiguous: any remaining sleep-based sync is replaced as part of Phase 2.
**Fix:** Add `DevKeyLogger.ime("layout_mode_recomposed", ...)` from the preference observer and `DevKeyLogger.ime("keymap_dump_complete", ...)` from KeyMapGenerator. Replace both sleeps with `driver.wait_for`.

### Finding 7 — DRIFT (HTTP server not auto-enabled every test run)
**Severity:** critical
**Location:** Phase 1.1 + Phase 3 test runner
**Spec reference:** §6 Phase 2 item 2.1 — "enabled for every test run"
**Description:** Plan delivers the receiver + manual README command. No test-runner code path auto-issues the broadcast.
**Fix:** Add a session-start fixture in `e2e_runner.py` (or `lib/driver.py`) that calls `driver.broadcast("dev.devkey.keyboard.ENABLE_DEBUG_SERVER", {"url": DRIVER_URL})` before running tests.

### Finding 8 — VERIFICATION MISSING (Phases 2, 3, 4 do not end with `./gradlew assembleDebug`)
**Severity:** critical
**Location:** Phase 2.4 (`node --check`), Phase 3.6 (`py_compile`), Phase 4.5 (`--list`)
**Spec reference:** `/writing-plans` skill rule — every phase's terminal sub-phase verifies with gradle build
**Description:** Plan author's deliberate substitution. Rule says gradle runs at every phase end even if IME side untouched.
**Fix:** Add `./gradlew assembleDebug` as verification to Phases 2, 3, 4.

### Finding 9 — DRIFT (borderline — next-word bigram skip)
**Severity:** minor
**Location:** Phase 4.2 `test_next_word_bigram_hit_for_common_word` — skips if bigram path doesn't fire for "the"
**Spec reference:** §2.2 — "Flaky tests must be stabilized, not suppressed"
**Fix:** Seed a guaranteed bigram fixture or FAIL with clear remediation rather than SKIP.

### Finding 10 — DRIFT (Visual-diff threshold 0.92 vs spec §9 example 0.95)
**Severity:** minor
**Location:** Header decision 3 + Phase 3.4
**Fix:** Document the deviation reasoning or use 0.95.

### Finding 11 — MINOR (Reference-capture gaps framing)
**Severity:** minor
**Location:** Phase 5.4 coverage matrix
**Fix:** State explicitly that reference capture is Phase 1 backlog, not Phase 2 scope.

## Return Summary

**Verdict:** REJECT

**Most critical:** Finding 2 — long-press covers only 7 keys when spec demands every key in every mode + popup content match against SwiftKey reference.

**Other blockers:** voice flow no EditText readback (F1), next-word asserts on log not strip (F3), tier stabilization not delivered (F4), coverage matrix exempts 4 features (F5), sleeps remain (F6), HTTP not auto-enabled (F7), Phases 2-4 missing gradle verification (F8).
