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
- [x] Release device docs normalized to S21 and `Pixel_7_API_36`; Windows
  automation remains emulator-only by repo rule.
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
- [x] S21 `--dump-inventory`
- [x] S21 focused smoke under verified-state harness:
  - `test_smoke.test_tap_letter_produces_logcat`
  - evidence included `DevKeyPress` logcat assertion after tap

## Remaining Implementation Work

- [ ] Add toolbar/dynamic button action inventory, then tie it to release
  validation.
- [ ] Rerun and repair all feature scopes under the verified-state harness.
  Earlier E2E results are audit data only; they are not release-green because
  they were produced before action/evidence ordering was enforced.
- [ ] Validate toolbar buttons, dynamic panels, mode switches, modifiers, and
  settings workflows against the generated inventory.
- [ ] Resolve plugin loading security for v1.0:
  - either gate plugin loading behind debug-only behavior
  - or add signature/provenance verification before release
- [ ] Validate custom dictionary/non-word behavior:
  - programming words and user-added non-words must not be aggressively
    autocorrected after learning
  - autocorrect must stay conservative by default
- [ ] Validate dictionary resilience:
  - dictionary `close()`/cleanup idempotency
  - corrupt binary dictionary handling does not crash the IME
  - native/JNI dictionary package path remains aligned
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
- [ ] Run feature scopes: voice, input, modifiers, modes,
  prediction/autocorrect, punctuation, clipboard, macros, command_mode,
  visual/long-press.
- [ ] Run dictionary-specific validation:
  - current AOSP-derived dictionaries load
  - synthetic prefixes and misspellings produce expected suggestions/corrections
  - next-word/bigram path works through the legacy dictionary stack
  - learned/custom words suppress unwanted autocorrect
- [ ] Run `--rerun-failed` once after fixes.
- [ ] Run full E2E suite on the primary S21 release target.
- [ ] Reproduce final failures or final full suite on `Pixel_7_API_36` only if
  the S21 release gate needs a reproducibility fallback. Current validation
  direction is S21-only.
- [ ] Confirm logs/artifacts contain no typed text, transcripts, clipboard
  contents, or credential-adjacent input.
- [ ] Confirm generated result JSON contains verification evidence for every
  passing test.

## Current Blockers

- Prior S21 feature-scope results are not trusted as release-green until rerun
  under the verified-state harness.
- Voice S21 scope before the verified-state harness produced 15 passes, 2
  emulator-only skips, and 2 transient errors that passed on `--rerun-failed`.
  Treat this as diagnostic only, not final release validation.

## Commit Trail

- `04c2681 fix(voice): harden release voice flow`
- `c37ddf0 test(e2e): add release preflight results`
- `0e7e4bd docs(e2e): normalize release validation gates`
- `2f6e628 refactor(core): route settings access through repository`
- `5cd6817 test(e2e): add canonical key inventory`
- `aade9a9 test(e2e): enforce inventory coverage`
