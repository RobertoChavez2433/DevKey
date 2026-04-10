# Code Review Cycle 2: Phase 2 Pre-Release Plan — 2026-04-08

**APPROVE** — all cycle-1 blockers and majors resolved; new sub-phases internally consistent; privacy contract preserved.

## Cycle-1 finding verification

| # | Severity | Status |
|---|---|---|
| B1 | BLOCKER | FIXED — voice cancel re-taps voice key; cites DevKeyKeyboard.kt:156-158 |
| M1 | MAJOR | FIXED — false keyMapDumpReceiver leak claim removed; only new receiver unregistered |
| M2 | MAJOR | FIXED — three-source next-word instrumentation (bigram_hit/bigram_miss/no_prev_word) |
| M3 | MAJOR | FIXED — server.js:25 logs.slice replaced with logs.splice to preserve array identity |
| m1 | MINOR | FIXED — screencap ships spawn+fs.createWriteStream from day one |
| m2 | MINOR | FIXED — driver.logcat_dump no longer clears logcat before dumping |
| m3 | MINOR | FIXED — entry["data"] (single-level access) |
| m4 | MINOR | FIXED via new Phase 1.5 (KeyPressLogger→DevKeyLogger.text forwarding) |
| m5 | MINOR | FIXED — test_layout_mode_round_trip uses correct ordering |
| m6 | MINOR | FIXED — set_layout_mode docstring warns ~3s block |
| m7 | LOW | NOT FIXED — Phase 5.5 still uses gradle, justified in plan notes |
| m8 | LOW | NOT FIXED — optional reflective static assertion |
| m9 | LOW | NOT FIXED — parameter shadowing in driver._post |

## New sub-phase verification (1.5, 1.6, 1.7, 1.8, 3.7, 4.6-4.10, 5.2)

All new sub-phases are grounded in actual code paths verified against LatinIME.kt, KeyPressLogger.kt, CandidateView.kt, KeyMapGenerator.kt, DevKeyKeyboard.kt, server.js. Privacy contract preserved across all new emit sites.

## Privacy contract audit (new emit sites)

| Emit site | Payload keys | Verdict |
|---|---|---|
| debug_server_enabled | url (scrubbed) | SAFE |
| layout_mode_set | mode | SAFE |
| next_word_suggestions | prev_word_length, result_count, source | SAFE |
| long_press_fired | label, code, lp_code | SAFE (same threat model as /adb/logcat DevKeyPress) |
| candidate_strip_rendered | suggestion_count | SAFE |
| keymap_dump_complete | mode, key_count | SAFE |
| layout_mode_recomposed | mode | SAFE |
| panel_opened, command_mode_*, plugin_scan_complete | panel, trigger, plugin_count | SAFE per privacy note |

## Phase verification gates

All phases end with `./gradlew assembleDebug`:
- Phase 1: line 374
- Phase 2: line 883 (node --check + assembleDebug)
- Phase 3: line 1390 (py_compile + assembleDebug)
- Phase 4: line 2034 (e2e_runner --list + assembleDebug)
- Phase 5: line 2269

## Remaining non-blocking observations

1. Path label in sub-phase 1.5 header says `debug/KeyPressLogger.kt` but actual location is `core/KeyPressLogger.kt`. Grep-based instruction mitigates.
2. Unnecessary safe-call `mSuggestions?.size ?: 0` in 1.6 (field is non-null).
3. m7/m8/m9 accepted as LOW.
4. 4.6-4.10 emit-site exact locations deferred to implementing agent by design.
5. Case mismatch risk: `layoutMode.name.lowercase()` assumes uppercase enum — low risk.

## Verdict

**APPROVE.** All BLOCKER and MAJOR findings resolved. New sub-phases grounded in verified source. Privacy contract preserved. Phase verification gates present.
