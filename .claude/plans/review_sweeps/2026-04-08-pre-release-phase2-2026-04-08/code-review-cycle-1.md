# Code Review: Phase 2 Pre-Release Plan — 2026-04-08

**REJECT** — one blocker (test asserts behavior that does not exist), one major plan-text inaccuracy, and several minor issues. None of the blockers affect compilation; all are runtime / test-correctness issues that would cause confusing failures during `/implement`.

## Findings

### BLOCKER

**B1. `test_voice_cancel_returns_to_idle` cancels via Backspace, but Backspace is not wired to `cancelListening`**
- File: `tools/e2e/tests/test_voice.py` (created in Phase 4.1, step 1)
- Plan location: Phase 4.1 step 1
- Plan comment claims: *"KeyboardActionBridge wires cancel to backspace-in-voice-mode"*
- Reality: `voiceInputEngine.cancelListening()` is invoked from exactly two sites — both in `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt:157` (re-tap of the voice toolbar button) and `:249` (Cancel button on `VoiceInputPanel`). There is no Backspace handler that calls it. Tapping `code=-5` while in `KeyboardMode.Voice` does nothing visible to the voice engine; the test's `wait_for` for `source=cancelListening` will time out.
- **Fix**: Cancel by re-tapping the voice key (`DevKeyKeyboard.kt:156-158`). Also verify the voice toolbar button appears in `KeyMapGenerator`'s dump — if not, the test must use a different cancel path or be removed.

### MAJOR

**M1. Plan asserts `keyMapDumpReceiver` "currently leaks" — false; it is already unregistered**
- File: `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`
- Plan location: Phase 1.1 step 3
- `LatinIME.kt:576` already calls `keyMapDumpReceiver?.let { unregisterReceiver(it) }` inside `onDestroy()`. The plan's "this fix cleans up both" comment will lead the implementing agent to insert duplicate cleanup code.
- **Fix**: Remove the `keyMapDumpReceiver` block from step 3. Only add the new `enableDebugServerReceiver` unregistration. Drop the false claim.

**M2. `setNextSuggestions` instrumentation falsifies `prev_word_length` on the fallback path**
- File: `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`
- Plan location: Phase 1.3 step 3
- The fallback `setSuggestions(mSuggestPuncList, ...)` is reached in three distinct cases: (a) `mSuggest == null || !isPredictionOn()`, (b) `prevWord == null`, (c) `prevWord != null` but `nextWords.isEmpty()`. The plan emits `prev_word_length=0` for all three. Case (c) is a real bigram-miss with a real previous word.
- **Fix**: Restructure to emit three distinct sources: `bigram_hit` (count > 0), `bigram_miss` (prevWord != null, count == 0), `no_prev_word` (prevWord == null or no prediction). Update test 4.2's allowed source set accordingly.

**M3. `wave-gate.js` `waitFor` closes over a stale `logs` reference if buffer trims during the wait**
- File: `tools/debug-server/wave-gate.js` (Phase 2.1) + `server.js` (Phase 2.3)
- `server.js:25` does `logs = logs.slice(-MAX_ENTRIES)` — reassigns the module-level variable. Long-poll `/wait` calls close over the old array; new entries flow into a different array; wait silently times out.
- **Fix**: Replace `logs = logs.slice(...)` in `server.js` with in-place `logs.splice(0, logs.length - MAX_ENTRIES)` so array identity is preserved. Document the line change explicitly in Phase 2.3.

### MINOR

**m1. `screencap` with `>` redirect inside `execFile` will not work** — `execFile` does not invoke a shell. Ship the `spawn` + `fs.createWriteStream` pattern from day one, not the broken version with a fix-later comment.

**m2. `driver.logcat_dump` clears logcat *before* dumping** — wipes the events the caller wants to read. Drop the `_post("/adb/logcat/clear")` line inside `logcat_dump`.

**m3. `test_voice_round_trip_committed_text` double-indexes entry** — `driver.wait_for` returns `result["entry"]` already, so `entry["entry"]["data"]` should be `entry["data"]`.

**m4. `test_long_press_compact_letter_keys_fire` issues no delay between swipe and logcat dump** — KeyPressLogger writes async on UI thread. Either add a bounded wait OR instrument `KeyPressLogger.logLongPress` via DevKeyLogger so the test can `wait_for`. The latter aligns with spec §2.2 no-sleep mandate.

**m5. `test_layout_mode_round_trip` inverted ordering** — calls `set_layout_mode` (which sleeps) then `wait_for` (idempotent on past events). Restructure: `clear_logs()` → broadcast via `driver.broadcast()` directly → `wait_for(layout_mode_set)` → `load_key_map()`.

**m6. `set_layout_mode` blocks ~3s silently** — docstring should warn callers.

**m7. Phase 5 verification re-runs `./gradlew assembleDebug` despite no Kotlin changes after Phase 1** — the plan's own implementation note contradicts this. Replace with `python tools/e2e/e2e_runner.py --list` or omit.

**m8. `DevKeyLoggerTest` reflective access** — add `Modifier.isStatic(field.modifiers)` assertion so a future refactor into a non-static holder fails loudly. Optional.

**m9. `_post` in `driver.py` shadows outer `body` parameter in the except branch** — rename local to `err_body`. Pure nit.

**m10. test_voice.py audio import signature** — verified, no bug. Documenting that I checked.

## Cross-cutting observations

- **Anti-pattern audit**: No `./gradlew test` and no `./gradlew connectedAndroidTest`. Clean.
- **Vague step audit**: Every sub-phase ships full code. Clean.
- **Ground-truth audit**: Most claims verified. The two wrong claims: (a) "keyMapDumpReceiver currently leaks" (M1) and (b) "Backspace cancels voice" (B1).
- **Agent routing**: Phase 1 1.1–1.3 → `ime-core-agent`, 1.4 + 2-5 → `general-purpose`. Correct.
- **Verification placement**: Phase 1 ends with `./gradlew assembleDebug` ✓. Phases 2-4 use language-specific smoke (intentional per plan notes). Phase 5 incorrectly re-adds gradle (m7).
- **Imports**: All Phase 1 imports verified present in `LatinIME.kt`.

## Required actions before APPROVE

1. Fix B1: change voice cancel to re-tap voice key
2. Fix M1: remove redundant keyMapDumpReceiver cleanup and false leak claim
3. Fix M2: three-source next-word instrumentation
4. Fix M3: in-place splice in server.js
5. Address m1-m10 inline

After these fixes the plan is sound. Infrastructure design is correct, dependency-graph between sub-phases is clean, no JNI touches, privacy contract preserved end-to-end.
