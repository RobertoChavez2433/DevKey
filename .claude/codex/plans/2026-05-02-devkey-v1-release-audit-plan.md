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

## Verified Locally

- [x] `python -m py_compile tools/e2e/e2e_runner.py tools/e2e/lib/keyboard.py`
- [x] `python tools/e2e/e2e_runner.py --list`
  - locked full-suite count: 178
- [x] `./gradlew test assembleDebug lint detekt`

## Remaining Implementation Work

- [ ] Add toolbar/dynamic button action inventory, then tie it to release
  validation.
- [ ] Validate toolbar buttons, dynamic panels, mode switches, modifiers, and
  settings workflows against the generated inventory.
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

- [ ] Run `--preflight` on an allowed automation target.
- [ ] Run feature scopes: voice, input, modifiers, modes,
  prediction/autocorrect, punctuation, clipboard, macros, command_mode,
  visual/long-press.
- [ ] Run `--rerun-failed` once after fixes.
- [ ] Run full E2E suite on the primary release target.
- [ ] Reproduce final failures or final full suite on `Pixel_7_API_36`.
- [ ] Confirm logs/artifacts contain no typed text, transcripts, clipboard
  contents, or credential-adjacent input.

## Current Blockers

- S21 is connected as `SM_G996U`, but this Windows workspace is governed by the
  repo rule: use Android emulator only and do not verify on physical Android
  devices. S21 validation must run from an allowed environment or be performed
  manually outside this Windows automation rule.
- No emulator was attached during this audit, so emulator preflight and full
  E2E execution remain pending.

## Commit Trail

- `04c2681 fix(voice): harden release voice flow`
- `c37ddf0 test(e2e): add release preflight results`
- `0e7e4bd docs(e2e): normalize release validation gates`
- `2f6e628 refactor(core): route settings access through repository`
- `5cd6817 test(e2e): add canonical key inventory`
- `aade9a9 test(e2e): enforce inventory coverage`
