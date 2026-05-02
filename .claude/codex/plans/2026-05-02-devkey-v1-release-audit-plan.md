# DevKey v1.0 Release Audit Status

Created: 2026-05-02

## Completed In This Audit

- [x] Voice release gate hardened in code:
  - runtime permission request path for toolbar mic and comma long-press voice
  - silence auto-stop with capture stop, inference, `AudioRecord` release, and
    normal IME commit path
  - voice blocked in password and credential-sensitive fields
  - debug file-processing transcript preview removed from logs
  - debug-only `VOICE_PROCESS_FILE` excluded from release receiver behavior
- [x] Settings access routed through `SettingsRepository` for the touched
  startup, lifecycle, language, settings, and preference observer paths.
- [x] E2E preflight added for device, IME, debug server, key map, audio
  permission, voice assets, and reset strategy.
- [x] E2E JSON result output added with duration, device metadata, app version,
  failure category, retry status, and privacy-safe artifact metadata.
- [x] `--rerun-failed` added for prior JSON result files.
- [x] Flat/subdirectory E2E ambiguity reduced with explicit suites and locked
  full-suite discovery count.
- [x] Debug/log server port normalized to `3950`.
- [x] Release device docs normalized to S21 and `Pixel_7_API_36`; current
  validation direction is S21-only, with the emulator retained only as a
  documented reproducibility fallback.
- [x] Canonical key/button inventory added for full, compact, compact_dev, and
  symbols layers.
- [x] Inventory coverage validation added so `--dump-inventory` fails on
  incomplete visible-key metadata.
- [x] Long-press action inventory added from the configured expectation files;
  `--dump-inventory` now fails if a configured long-press action is not tied to
  a visible key in the matching layout/layer.
- [x] E2E verification state added:
  - tests now track actions separately from evidence
  - tests fail if they complete with no evidence
  - tests fail if meaningful actions occur after the last evidence point
  - S21 tap smoke was rerun with evidence recorded in JSON
- [x] S21 screenshot review caught an invalid visual/test-host state:
  - the host EditText contained stale accumulated synthetic text
  - prior preflight did not prove a clean field before visual validation
  - this invalidates any broad "visual pass" claim from earlier runs
- [x] Clean visual-state gate added and verified on S21:
  - shared reset clears `TestHostActivity` before each test
  - debug-only host state logs text length and focus state, never text content
  - preflight fails if the field is non-empty, unfocused, blank, or low contrast
  - S21 clean screenshot reviewed after the stale-text failure

## Verified Locally

- [x] `python -m py_compile tools/e2e/e2e_runner.py tools/e2e/lib/adb.py tools/e2e/lib/keyboard.py tools/e2e/lib/verify.py`
- [x] `python tools/e2e/e2e_runner.py --list`
  - locked full-suite count: 178
- [x] `./gradlew test assembleDebug lint detekt`
- [x] `./gradlew assembleDebug` after debug `TestHostActivity` state logging
- [x] Installed debug APK on S21: `adb -s RFCNC0Y975L install -r ...`
- [x] S21 `--preflight`
  - serial: `RFCNC0Y975L`
  - model: `SM-G996U`
  - DevKey default IME: yes
  - key map: 104 normal entries, 86 symbols entries
  - voice assets: present
- [x] S21 clean visual preflight after the screenshot failure:
  - clean field length: `0`
  - screenshot: `.claude/test-results/visual-baseline-20260502T150040Z.png`
  - keyboard crop contrast: `228`
  - keyboard crop stddev: `50.301`
- [x] S21 input feature rerun under verified-state harness:
  - result: 16 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T150911Z.json`
  - all passing tests had verification evidence
  - repeated-`a` composing stress was replaced with varied-key state checks
- [x] S21 autocorrect feature rerun under verified-state harness:
  - result: 11 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T151348Z.json`
  - all passing tests had verification evidence
- [x] S21 prediction feature rerun under verified-state harness:
  - initial result exposed 2 real `next_word_suggestions` timeouts
  - rerun-failed verified both repaired tests
  - final result: 12 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T152013Z.json`
  - all passing tests had verification evidence
  - paragraph stress now isolates prediction from auto-cap geometry timing
- [x] S21 modes feature rerun under verified-state harness:
  - initial result exposed unverified cleanup actions and a stale symbols
    bottom-row coordinate for ABC
  - fixed `computeKeyBounds` to match Compose row-weight denominator behavior
  - final result: 10 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T153043Z.json`
  - all passing tests had verification evidence
- [x] S21 modifiers feature rerun under verified-state harness:
  - initial result exposed `ctrl_a_through_z` state contamination at Ctrl+G
  - fixed the stress loop to reset the controlled host before each shortcut
  - final result: 21 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T154310Z.json`
  - all passing tests had verification evidence
- [x] S21 punctuation feature rerun under verified-state harness:
  - result: 13 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T155020Z.json`
  - all passing tests had verification evidence
- [x] S21 clipboard feature rerun under verified-state harness:
  - initial result exposed nonexistent `CLIPBOARD_*` debug receivers and
    action-only tests
  - added debug-only repository-backed clipboard actions with structural logs
  - final result: 8 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T155426Z.json`
  - all passing tests had verification evidence
- [x] S21 `--dump-inventory`
  - regenerated after coordinate fix:
    `.claude/test-results/key-inventory-20260502T153201Z.json`
  - compact symbols ABC coordinate corrected to `x=66`, `y=2050`
- [x] S21 focused smoke under verified-state harness:
  - `test_smoke.test_tap_letter_produces_logcat`
  - evidence included `DevKeyPress` logcat assertion after tap
- [x] Dictionary/autocorrect unit validation:
  - targeted `testDebugUnitTest` passed for prediction, dictionary,
    autocorrect pipeline, and suggestion coordinator tests
  - ordinary learned typos no longer suppress autocorrect
  - explicit custom words still suppress unwanted autocorrect
  - empty native dictionary bigram lookup returns without a JNI crash
- [x] S21 voice scope under verified-state harness:
  - state-machine paths execute, but real round-trip remains blocked
  - `voice.test_smoke.test_voice_round_trip_committed_text` fails because
    `process_file_result` reports `committed=false`
  - `voice.test_stress.test_audio_inject_rapid_cycle` fails for the same
    non-committable Whisper output
  - this is a release blocker, not a test skip
- [x] False-positive hardening after strict S21 rerun:
  - runner no longer swallows per-test reset/log-clear failures
  - runner no longer manufactures generic logcat evidence after final taps
  - S21 modifier rerun initially exposed 9 unverified final-action tests
  - repaired modifier tests now require setting acknowledgements, mode events,
    modifier transitions, key events, or focused host state after the last
    action
  - final strict S21 modifiers result: 21 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T165433Z.json`
- [x] Generated artifact privacy guard:
  - result JSON checked for structural-only evidence
  - `.claude/test-results/` is now ignored to avoid accidental commits of
    local JSON or screenshots
- [x] Hung-test guard:
  - each E2E test now runs in an isolated child process
  - default hard timeout is 90 seconds per test
  - `--test-timeout-seconds` and `DEVKEY_E2E_TEST_TIMEOUT_SECONDS` override it
  - forced S21 timeout probe produced `TestTimeout` and JSON instead of hanging
  - normal S21 input smoke still passed under child-process isolation
- [x] jCodeMunch high-level audit refreshed on current DevKey tree:
  - indexed current DevKey snapshot: 3,996 symbols, 593 files
  - no dependency cycles detected
  - highest hotspots include `tools/e2e/e2e_runner.py::main`,
    `discover_tests`, `_execute_test_inline`, `KeyboardDependencies.
    rememberKeyboardDependencies`, `KeyView`, `InputHandlers.handleSeparator`,
    and `SuggestionPipeline.getSuggestions`
  - largest maintained DevKey files include `tools/e2e/e2e_runner.py`,
    `tools/e2e/lib/adb.py`, `KeyboardDependencies.kt`,
    `VoiceInputEngine.kt`, `tools/e2e/lib/keyboard.py`,
    `InputHandlers.kt`, and `SuggestionCoordinator.kt`
- [x] Field Guide runtime-isolation repo indexed for validation-system
  comparison:
  - `DriverServer` exposes explicit HTTP endpoints for find, tap, text,
    screenshot, tree, navigation, wait, injection, sync, local records,
    current route, overlays, and artifacts
  - `DriverSetup` separates driver-specific dependency overrides from app
    composition
  - `HarnessSeedData` separates deterministic data seeding from test flow code
  - Field Guide has local architecture lint concepts for max file length and
    max import count that DevKey currently lacks
- [x] First modularity iteration after the audit:
  - moved per-test execution, result shaping, skip/error serialization,
    child-process watchdog, and timeout recovery out of `e2e_runner.py`
    into `tools/e2e/lib/executor.py`
  - `e2e_runner.py` dropped from 23 to 15 symbols after extraction
  - S21 normal smoke still passed:
    `input.test_smoke.test_backspace_plain --test-timeout-seconds 60`
  - S21 forced timeout still failed loudly:
    `input.test_smoke.test_backspace_plain --test-timeout-seconds 1`
- [x] Second E2E runner modularity iteration:
  - moved discovery/filtering/locked-count logic into
    `tools/e2e/lib/discovery.py`
  - moved preflight/device metadata into `tools/e2e/lib/preflight.py`
  - moved inventory/action inventory into `tools/e2e/lib/inventory.py`
  - moved JSON result payload and artifact writing into
    `tools/e2e/lib/results.py`
  - moved debug-server forwarding/warmup into
    `tools/e2e/lib/driver_setup.py`
  - `e2e_runner.py::main` dropped from cyclomatic 59 to 6 after command
    handler extraction
  - locked full-suite discovery still reports 178 tests
- [x] S21 host-state concurrency guard:
  - added a per-device E2E run lock under `.claude/test-results/locks`
  - a second S21 preflight/test/inventory run now exits immediately with
    code 2 instead of mutating the same device state concurrently
  - this directly addresses the invalid preflight screenshot observed when
    preflight and smoke were accidentally run in parallel
- [x] Initial Field-Guide-style structure audit guardrail:
  - added `python tools/audit_structure.py`
  - excludes generated/build/cache/artifact paths from reports
  - reports line-count and import-count violations for maintained source
  - current audit scans 349 files and reports 8 line-count violations and 1
    import-count violation
- [x] Residual E2E hotspot split:
  - split `tools/e2e/lib/discovery.py::discover_tests` into candidate,
    module, and function selection helpers
  - split `tools/e2e/lib/executor.py::execute_test_inline` into child-helper
    loading, process preparation, verification summary, and result builders
  - `discover_tests` dropped to cyclomatic 6
  - `execute_test_inline` dropped to cyclomatic 7
  - current jCodeMunch hotspot report no longer includes `tools/e2e`
    runner/discovery/executor functions in the top 12
- [x] E2E ADB helper split:
  - kept `tools/e2e/lib/adb.py` as a compatibility facade for existing tests
  - moved low-level device commands into `adb_device.py`
  - moved logcat helpers into `adb_logcat.py`
  - moved controlled TestHostActivity state/reset/visibility helpers into
    `adb_test_host.py`
  - moved screenshot and visual-baseline helpers into `adb_visual.py`
  - `tools/e2e/lib/adb.py` is no longer a structure-audit line violation
  - `ensure_keyboard_visible` dropped to cyclomatic 7 in its new module
- [x] E2E keyboard helper split:
  - kept `tools/e2e/lib/keyboard.py` as a compatibility facade for existing
    tests
  - moved shared key-map cache state into `keyboard_state.py`
  - moved key-map loading/cache access into `keyboard_keymap.py`
  - moved canonical inventory validation into `keyboard_inventory.py`
  - moved layout-mode switching into `keyboard_layout.py`
  - moved tapping/sequence helpers into `keyboard_tap.py`
  - `tools/e2e/lib/keyboard.py` is no longer a structure-audit line violation
  - S21 preflight, input smoke, and layout-mode round-trip smoke passed after
    the split
- [x] Keyboard debug/test receiver split:
  - moved debug-only key-map, mode/reset, command-mode, clipboard, and macro
    receivers out of `KeyboardDependencies.rememberKeyboardDependencies`
  - split receiver logic into focused modules:
    `DebugKeyboardReceivers.kt`, `ClipboardDebugReceiver.kt`, and
    `MacroDebugReceiver.kt`
  - `rememberKeyboardDependencies` is now low complexity in jCodeMunch:
    cyclomatic 1, 36 lines
  - `KeyboardDependencies.kt` is no longer a structure-audit line violation
  - current structure audit scans 361 files and reports 5 line-count
    violations plus 1 import-count violation
  - fresh debug APK installed on S21 `RFCNC0Y975L`
  - S21 preflight passed after install:
    `.claude/test-results/visual-baseline-20260502T174905Z.png`
  - receiver-backed S21 scopes passed after the split:
    clipboard 8/8, macros 8/8, command_mode 6/6, modes 10/10
  - the first modes rerun exposed one real false-positive risk in
    `modes.test_stress.test_symbols_type_toggle_back`; the test now proves
    final host text length after returning to normal mode
- [x] Separator input-handler split:
  - moved separator acceptance, autocorrect result handling, punctuation
    heuristics, and next-word triggering out of
    `InputHandlers.handleSeparator` into `SeparatorHandler`
  - `InputHandlers.handleSeparator` is now low complexity in jCodeMunch:
    cyclomatic 1, 3 lines
  - focused unit validation passed:
    `./gradlew testDebugUnitTest --tests dev.devkey.keyboard.core.InputHandlersTest`
  - `./gradlew assembleDebug` passed after the split
  - separator-adjacent S21 validation on `RFCNC0Y975L`:
    input 16/16 clean, prediction 12/12 clean
  - punctuation first rerun exposed verification/device-state problems:
    11/13 passed, then `--rerun-failed` passed 2/2
  - autocorrect first rerun exposed one next-word timeout:
    10/11 passed, then `--rerun-failed` passed 1/1
  - punctuation setup is now shared, uses verified host clearing, and has a
    shorter best-effort dictionary wait instead of a 15-second stale-log wait
- [x] Voice Whisper round-trip repaired and validated:
  - parsed the bundled `filters_vocab_en.bin` using the actual three-int
    filter/vocabulary header and vocabulary placement
  - aligned mel-spectrogram generation with the upstream Android Whisper
    reference's 400-point FFT path
  - debug file inference reloads the model before processing each fixture to
    avoid stale interpreter state during stress loops
  - voice E2E now distinguishes speech fixtures from the near-silent fixture
    and requires committed output for speech
  - voice tail-action tests now require final host-state or driver evidence
    after their last action

## Remaining Implementation Work

- [ ] Integrate DevKey architecture guardrails into final release gates:
  - run `python tools/audit_structure.py`
  - capture jCodeMunch hotspot/complexity report with the release checklist
  - attach explicit deferral rationale for each remaining structure violation
- [ ] Refactor production hotspots before release where they affect validation:
  - `InputHandlers.handleCharacter`
  - `SuggestionPipeline.getSuggestions`
  - `SuggestionCoordinator.onSelectionChanged`
  - `KeyView`
  - `DevKeyKeyboard`
  - `InputViewSetup.onStartInputView`
- [ ] Add toolbar/dynamic button action inventory, then tie it to release
  validation.
- [ ] Rerun the full S21 suite under the hardened child-process timeout.
  Feature-scope evidence is useful, but the release still needs one complete
  strict run after the voice fix and timeout guard.
- [ ] Validate toolbar buttons, dynamic panels, mode switches, modifiers, and
  settings workflows against the generated inventory.
- [ ] Resolve plugin loading security for v1.0:
  - either gate plugin loading behind debug-only behavior
  - or add signature/provenance verification before release
- [ ] Complete AnySoftKeyboard dictionary spike:
  - verify Apache-2.0 compatibility and source provenance
  - compare current DevKey dictionaries against an AnySoft-backed candidate on
    synthetic prefixes and misspellings
  - measure quality, latency, memory, APK size, and crash behavior
  - integrate only behind `DictionaryProvider` if clearly lower-risk and better
- [ ] Keep HeliBoard code out of v1.0 due GPL risk.
- [ ] Avoid Hunspell before release unless AnySoft fails and Hunspell is proven
  lower-risk.
- [ ] If AnySoft is rejected, ship current AOSP-derived dictionaries with
  conservative autocorrect.

## Remaining Validation Gates

- [x] Rerun `--preflight` on S21 after clean-state and visual-baseline gates
  are in place.
- [x] Run feature scopes on S21 with verified evidence for input, modifiers,
  modes, prediction/autocorrect, punctuation, clipboard, macros,
  command_mode, visual, and long-press.
- [x] Resolve voice round-trip, then rerun the S21 voice scope:
  - isolated failed rerun: 1 passed, 0 failed, 0 errors
  - JSON: `.claude/test-results/e2e-results-20260502T190908Z.json`
  - full voice scope: 19 passed, 0 failed, 0 errors, 0 skipped
  - JSON: `.claude/test-results/e2e-results-20260502T191613Z.json`
- [ ] Run dictionary-specific validation:
  - current AOSP-derived dictionaries load
  - synthetic prefixes and misspellings produce expected suggestions/corrections
  - next-word/bigram path works through the legacy dictionary stack
  - learned/custom words suppress unwanted autocorrect where intended
- [ ] Run `--rerun-failed` once after fixes.
- [ ] Run full E2E suite on the primary S21 release target.
- [ ] Reproduce final failures or final full suite on `Pixel_7_API_36` only if
  the S21 release gate needs a reproducibility fallback. Current validation
  direction is S21-only.
- [ ] Confirm logs/artifacts contain no typed text, transcripts, clipboard
  contents, or credential-adjacent input.
- [ ] Confirm generated result JSON contains verification evidence for every
  passing test.
- [ ] Confirm architecture audit output is attached to the release gate:
  - current top hotspots
  - god files/classes/functions above thresholds
  - `tools/audit_structure.py` output
  - refactor deferrals with explicit release-risk rationale

## Current Blockers

- Full-suite S21 validation still needs one complete strict run after the voice
  round-trip fix and child-process timeout guard.
- AnySoftKeyboard dictionary spike is still research work; do not integrate new
  dictionary sources before provenance and behavior are proven.
- Plugin loading security still needs a v1.0 decision: debug-only gate or
  signature/provenance verification.
- The current structure audit still reports 5 files above 400 lines and 1 file
  above 35 imports. These are now visible release-gate findings, not hidden
  cleanup debt.

## Commit Trail

- `04c2681 fix(voice): harden release voice flow`
- `c37ddf0 test(e2e): add release preflight results`
- `0e7e4bd docs(e2e): normalize release validation gates`
- `2f6e628 refactor(core): route settings access through repository`
- `5cd6817 test(e2e): add canonical key inventory`
- `aade9a9 test(e2e): enforce inventory coverage`
- `addc44f fix(dictionary): guard bigram lookup without native handle`
- `e1159bf fix(prediction): preserve autocorrect for learned typos`
- `d4d5b53 test(clipboard): update history dao fake`
- `66a80c1 test(e2e): fail unverified runner state`
- `7993e1d test(e2e): verify modifier state transitions`
- `393c6d9 chore(git): ignore local test results`
- `67fe5ce test(e2e): add hard per-test timeout`
- `4664b46 test(e2e): extract per-test executor`
- `0d46ef1 docs(e2e): add structural audit backlog`
- `7521758 test(e2e): split runner command modules`
- `63ae463 test(tooling): add structure audit guardrail`
- `26e13e5 test(e2e): split discovery and executor hotspots`
- `926cd6a test(e2e): split adb helper responsibilities`
- `5aa8cb3 test(e2e): split keyboard helper responsibilities`
- `d1717ec refactor(keyboard): extract debug receiver registration`
- `c641a2c test(e2e): verify symbol mode round trip state`
- `a34f0d6 docs(e2e): update release audit backlog`
- `7d43d1a refactor(core): extract separator input handler`
- `877dfa4 test(e2e): harden separator-adjacent evidence`
- `442bd0b docs(e2e): record separator validation results`
- `6b43d68 fix(voice): decode Whisper assets for round trip`
- `cd7a397 test(e2e): require committable voice inference`
