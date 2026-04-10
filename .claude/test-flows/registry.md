# Test Flow Registry

All keyboard test flows available for automated ADB-based testing. The test orchestrator reads this file to determine which flows to run and in what order.

## Wave Computation

Flows are organized into waves based on dependency chains. The orchestrator computes waves via topological sort on the `deps` field:

```
Wave 0: [ime-setup]
Wave 1: [typing, modifier-states, mode-switching]
Wave 2: [modifier-combos, caps-lock, layout-modes]
Wave 3: [rapid-stress]
```

## Git Diff Auto-Selection

When `/test` runs with no arguments:

1. `git diff main...HEAD --name-only` identifies changed files
2. File paths are mapped to features (see feature-path-map below)
3. All flows matching those features are selected
4. Transitive dependencies are pulled in automatically

### Feature-Path Map

| Feature | Path patterns |
|---------|--------------|
| core | `app/src/main/java/dev/devkey/keyboard/core/**` |
| ui-keyboard | `app/src/main/java/dev/devkey/keyboard/ui/keyboard/**` |
| ui-theme | `app/src/main/java/dev/devkey/keyboard/ui/theme/**` |
| ime | `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` |
| macro | `app/src/main/java/dev/devkey/keyboard/feature/macro/**` |
| command | `app/src/main/java/dev/devkey/keyboard/feature/command/**` |
| voice | `app/src/main/java/dev/devkey/keyboard/feature/voice/**` |
| data | `app/src/main/java/dev/devkey/keyboard/data/**` |
| settings | `app/src/main/java/dev/devkey/keyboard/settings/**` |
| build | `app/build.gradle.kts`, `build.gradle.kts`, `gradle/**` |

---

## ime-setup

- **feature**: core
- **deps**: []
- **precondition**: APK is installed, IME is enabled and set, a text field has focus.
- **steps**:
  1. Verify IME process is running: `pidof dev.devkey.keyboard`
  2. **Reset keyboard to Normal mode**: Force-stop and re-set IME (`am force-stop dev.devkey.keyboard && ime set dev.devkey.keyboard/.LatinIME`). This guarantees Normal/QWERTY mode regardless of previous keyboard state. Then re-open the text field and tap to bring up keyboard.
  3. Take screenshot to confirm keyboard is visible in Normal mode
  4. Load key coordinates via 3-tier calibration cascade (run by **orchestrator**, not the wave agent):
     - 4a. **Broadcast (fast path)**: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` then read `DevKeyMap` logcat
     - 4b. **Cache**: Read `.claude/test-flows/calibration.json` if it exists (device-specific runtime data)
     - 4c. **Y-scan probe (slow path)**: Binary search for keyboard top Y by tapping at candidate Y positions and checking logcat. Saves results to `calibration.json` for future runs.
  5. Tap the 'h' key at coordinates from the resolved coordinate table and verify `DevKeyPress` logcat entry with `code=104` appears
  6. If all calibration tiers fail, use pre-calibrated coordinates from key-coordinates.md as final fallback
- **verify**: Keyboard is visible in screenshot in Normal mode. At least one key tap produces a `DevKeyPress` logcat entry. Key coordinates are resolved and passed to all wave agents. Calibration source is logged (broadcast/cache/scan/fallback).
- **timeout**: 30s
- **notes**: This flow is a dependency for all other flows. It establishes that the keyboard is functional and coordinates are calibrated. The orchestrator runs calibration (step 4) and passes resolved coordinates to ALL wave agents — agents never parse DevKeyMap themselves. Force-stop in step 2 guarantees a clean Normal mode start.

---

## typing

- **feature**: ui-keyboard
- **deps**: [ime-setup]
- **precondition**: Keyboard is visible and key coordinates are loaded.
- **steps**:
  1. Clear logcat
  2. Tap keys: h, e, l, l, o (each at their QWERTY row coordinates)
  3. Wait 200ms between each tap
  4. Read `DevKeyPress` logcat
  5. Verify 5 tap events with codes: 104, 101, 108, 108, 111
  6. Take screenshot showing "hello" in text field
- **verify**: All 5 `DevKeyPress` entries present with correct codes. Screenshot shows "hello" in text field.
- **timeout**: 15s
- **notes**: This is the most basic functional test. If this fails, all other flows are unreliable.

---

## modifier-states

- **feature**: core
- **deps**: [ime-setup]
- **precondition**: Keyboard is visible in Normal mode. All modifiers are OFF.
- **steps**:
  1. **Shift one-shot**: Clear logcat → tap Shift → verify `ModifierTransition SHIFT: OFF -> ONE_SHOT` → tap 'a' → verify logcat `shift=true, code=97` → verify next tap has `shift=false`
  2. **Ctrl one-shot**: Clear logcat → tap Ctrl → verify `ModifierTransition CTRL: OFF -> ONE_SHOT` → tap 'a' → verify `ctrl=true`
  3. **Alt one-shot**: Clear logcat → tap Alt → verify `ModifierTransition ALT: OFF -> ONE_SHOT` → tap 'a' → verify `alt=true`
- **verify**: All 3 modifier one-shot cycles complete correctly in logcat. Each modifier is consumed after one key press.
- **timeout**: 20s
- **notes**: Tests only one-shot behavior. Caps Lock and held/chording are tested in separate flows.

---

## mode-switching

- **feature**: ui-keyboard
- **deps**: [ime-setup]
- **precondition**: Keyboard is visible in Normal mode.
- **steps**:
  1. **Normal → Symbols**: Clear logcat → tap 123 key → verify `toggleMode: Normal -> Symbols` → take screenshot → verify Symbols layout visible
  2. **Symbols → Normal**: Clear logcat → tap ABC key → verify logcat `setMode: Normal` → take screenshot → verify QWERTY layout visible
  3. **Round-trip**: Repeat steps 1-2 → verify final state is Normal mode
- **verify**: Logcat shows correct mode transitions. Screenshots confirm visual layout changes (QWERTY ↔ Symbols). Round-trip returns to Normal.
- **timeout**: 20s
- **notes**: This tests both the logcat state transition AND the Compose recomposition. If logcat passes but screenshot fails, it's the known decor lifecycle issue.

---

## caps-lock

- **feature**: core
- **deps**: [modifier-states]
- **precondition**: Keyboard is visible, Shift is OFF.
- **steps**:
  1. Clear logcat
  2. Tap Shift → verify `SHIFT: OFF -> ONE_SHOT`
  3. Tap Shift again within 400ms → verify `SHIFT: ONE_SHOT -> LOCKED`
  4. Tap 'a', 'b', 'c' → verify all show `shift=true`
  5. Tap Shift → verify `SHIFT: LOCKED -> OFF`
  6. Tap 'a' → verify `shift=false`
- **verify**: Double-tap activates LOCKED. All subsequent letters are shifted until explicit unlock. Single tap from LOCKED returns to OFF.
- **timeout**: 15s
- **notes**: The double-tap must happen within 400ms. The `stateBeforeDown` tracking in ModifierStateManager is critical for this to work through the KeyView event flow.

---

## modifier-combos

- **feature**: core
- **deps**: [modifier-states]
- **precondition**: Keyboard is visible, all modifiers OFF.
- **steps**:
  1. **Ctrl+C**: Clear logcat → tap Ctrl → tap 'c' → verify `ctrl=true, code=99`
  2. **Ctrl+V**: Clear logcat → tap Ctrl → tap 'v' → verify `ctrl=true, code=118`
  3. **Ctrl+A**: Clear logcat → tap Ctrl → tap 'a' → verify `ctrl=true, code=97`
  4. **Ctrl+Z**: Clear logcat → tap Ctrl → tap 'z' → verify `ctrl=true, code=122`
  5. **Alt+Tab** (FULL mode only): Clear logcat → tap Alt → tap Tab → verify `alt=true` in logcat
- **verify**: All modifier+key combos produce correct logcat entries with modifier flags set.
- **timeout**: 20s
- **notes**: These combos are critical for power users (terminal, remote desktop). The `KeyEventSender` synthesizes proper Android KeyEvent with META_CTRL_ON flags.

---

## layout-modes

- **feature**: ui-keyboard
- **deps**: [ime-setup]
- **precondition**: Keyboard is visible. Device settings allow layout mode switching.
- **steps**:
  1. **FULL mode**: Verify 6 rows visible in screenshot. Tap number '5' in row 0 → verify code=53. Tap Esc in utility row → verify code=27.
  2. **Number row**: Tap each number key 1-0 → verify codes 49-57, 48.
  3. **Arrow keys** (FULL mode): Tap Left → verify code. Tap Down, Up, Right → verify codes.
  4. **Mode switching via broadcast**: Switch to COMPACT via
     `adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact`,
     reload key map, verify at least 10 letter keys resolve. Repeat for COMPACT_DEV and FULL.
- **verify**: All rows render correctly. Number row and utility row keys produce correct keycodes.
- **timeout**: 25s
- **notes**: COMPACT and COMPACT_DEV mode switching is now automated via the
  `dev.devkey.keyboard.SET_LAYOUT_MODE` debug broadcast (added Phase 2 sub-phase 1.2).
  The Python harness's `keyboard.set_layout_mode(mode, serial)` helper issues the
  broadcast and re-loads the key map. See `test_modes.py::test_layout_mode_round_trip`
  for the smoke test that guards this path.

---

## rapid-stress

- **feature**: ui-keyboard
- **deps**: [typing, modifier-states]
- **precondition**: Keyboard is visible in Normal mode, all modifiers OFF.
- **steps**:
  1. **Rapid typing**: Tap 10 letter keys with 50ms delay between each → verify at least 8 `DevKeyPress` entries (allows 2 dropped)
  2. **Rapid mode toggle**: Toggle 123/ABC 5 times with 200ms delay → verify no crashes (`pidof` still returns PID) and at least 5 `DevKeyMode` entries
  3. **Rapid Shift taps**: Tap Shift 6 times with 150ms delay → verify `ModifierTransition` entries show correct cycle (OFF→ONE_SHOT→LOCKED→OFF repeated)
  4. **Shift + rapid letters**: Tap Shift → tap 'a','b','c' with 50ms delay → verify first letter has `shift=true`
- **verify**: No crashes. No ANRs in logcat. Key events are not permanently lost (allow transient drops under stress).
- **timeout**: 20s
- **notes**: This catches race conditions in the modifier state machine and Compose recomposition. Performance regression if >10% key drops.

---

## voice-round-trip

- **feature**: voice
- **deps**: [ime-setup]
- **precondition**: Keyboard visible, voice button shows (non-password text field), driver server running, ENABLE_DEBUG_SERVER broadcast sent, Whisper model present.
- **steps**:
  1. Clear driver logs
  2. Tap voice key (code=-102)
  3. Wait for DevKey/VOX state_transition{state=LISTENING} via `driver.wait_for`
  4. Inject audio sample (emulator only — SKIP on physical devices)
  5. Wait for DevKey/VOX state_transition{state=PROCESSING}
  6. Wait for DevKey/VOX processing_complete
  7. Assert result_length > 0
  8. Wait for DevKey/VOX state_transition{state=IDLE, reason=inference_complete}
- **verify**: All wait_for calls succeed, processing_complete has result_length > 0.
- **timeout**: 35s
- **notes**: Privacy: NEVER assert on transcript content. result_length only. Test SKIPs gracefully when voice is disabled or audio injection is unavailable.

---

## next-word-prediction

- **feature**: ime
- **deps**: [ime-setup, typing]
- **precondition**: Keyboard visible in Normal mode, HTTP forwarding enabled.
- **steps**:
  1. Clear driver logs
  2. Type "the"
  3. Tap space (code=32)
  4. Wait for DevKey/TXT next_word_suggestions
  5. Assert source ∈ {bigram_hit, bigram_miss, no_prev_word}
  6. Assert payload contains only prev_word_length, result_count, source (privacy guard)
- **verify**: Event arrives within 3s, payload is structural-only.
- **timeout**: 15s
- **notes**: SKIP the bigram-hit sub-test if the default dict lacks bigrams for "the" — it's not a code defect.

---

## long-press-coverage

- **feature**: ui-keyboard
- **deps**: [ime-setup]
- **precondition**: Keyboard visible, layout mode switchable, long-press expectation JSON files committed under `.claude/test-flows/long-press-expectations/<mode>.json`, `KeyPressLogger.logLongPress` instrumented with `DevKeyLogger.text("long_press_fired", ...)` per Phase 2 sub-phase 1.5.
- **steps**:
  1. For each mode in [full, compact, compact_dev, symbols]:
     - Switch to mode via SET_LAYOUT_MODE broadcast + wait_for layout_mode_recomposed
     - Load key map for the mode
     - Read `long-press-expectations/<mode>.json` — list of `{label, code, lp_code, popup_codes?}` entries derived from QwertyLayout.buildXxxLayout() / SymbolsLayout
     - For each expected entry: clear logs → swipe-hold 500ms on key → `driver.wait_for("DevKey/TXT", "long_press_fired", match={"label": label})` with 2s timeout → assert `lp_code` matches expected
     - For entries with `popup_codes` (multi-char popups): screencap popup region → SSIM against `swiftkey-reference/popups/<mode>/<label>.png` if present, SKIP otherwise
- **verify**: Every key listed in the expectation JSONs produces a matching `long_press_fired` event via HTTP forwarding (no legacy DevKeyPress scraping). Popup content matches SwiftKey reference where a reference exists.
- **timeout**: 240s total (iteration across 4 modes × all keys with long-press data)
- **notes**: Expectation JSONs authored during sub-phase 4.3 from live `QwertyLayout.kt` / `SymbolsLayout.kt` source by the implementing agent. Flow routes exclusively through `DevKeyLogger.text` + driver `wait_for` — no sleeps, no legacy logcat scrape.

---

## swiftkey-visual-diff

- **feature**: ui-theme
- **deps**: [ime-setup]
- **precondition**: Reference screenshots exist in .claude/test-flows/swiftkey-reference/, scikit-image installed.
- **steps**:
  1. For each (mode, reference) pair:
     - SKIP if reference file missing
     - Switch to mode via SET_LAYOUT_MODE broadcast
     - Ensure keyboard visible
     - Capture screenshot via adb exec-out screencap
     - Compute SSIM against reference
     - Assert SSIM >= DEVKEY_SSIM_THRESHOLD (default 0.92)
- **verify**: Captured screenshots score above threshold.
- **timeout**: 30s per mode
- **notes**: Phase 2 references (Session 42): compact-dark only. Other modes SKIP until captured per spec §4.4.1. Adjust threshold via DEVKEY_SSIM_THRESHOLD env var.

---

## Unit Test Tiers

For reference, these are the existing test tiers (not run by this skill, but context for understanding coverage):

| Tier | Runner | Count | Command |
|------|--------|-------|---------|
| JVM Unit Tests | JUnit 4 + Robolectric | ~361 | `./gradlew test` |
| Compose UI Tests | ComposeTestRule | ~14 | `./gradlew connectedAndroidTest` (blocked on API 36) |
| ADB E2E (Python) | Custom harness | ~14 | `python tools/e2e/e2e_runner.py` |
| ADB E2E (this skill) | Claude agents | flows above | `/test` |
