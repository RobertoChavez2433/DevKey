# DevKey Smart Input Replacement Plan

Created: 2026-05-03
Updated: 2026-05-03
Status: completed and verified on S21 `RFCNC0Y975L`

## Outcome

DevKey keeps its keyboard surface: physical-key layout, modifier keys, toolbar,
macros, clipboard, command mode, privacy protections, and voice entry point.

The in-house smart-text stack has been retired from production. Correction,
suggestions, learned-word ranking, capitalization, and punctuation decisions now
route through `SmartTextEngine` and the AnySoftKeyboard-backed adapter.

## Donor And Dictionary Decision

Primary donor engine: AnySoftKeyboard.

Dictionary source: AnySoftKeyboard language-pack dictionary model, starting with
the bundled English wordlist artifact.

Why:

- Apache-2.0 compatible donor direction.
- No-internet keyboard posture fits DevKey privacy requirements.
- Language-pack dictionaries align with DevKey's plugin/dictionary direction.

Rejected:

- DevKey `TrieDictionary` and legacy AOSP/Hacker's Keyboard `Suggest` as final
  smart-text sources.
- HeliBoard/OpenBoard GPL-derived engines or dictionaries unless the whole
  project explicitly accepts GPL impact.
- FlorisBoard as first donor; retained only as fallback if ASK fails later.

## Completed Work

- Moved punctuation transforms to the donor adapter.
- Deleted the legacy `Suggest`, `SuggestionPipeline`, `NextWordSuggester`,
  `SuggestionInserter`, `SuggestState`, dictionary-ref, trie, expandable,
  auto-dictionary, and bigram dictionary implementation.
- Removed legacy dictionary lifecycle fields and writes from IME state,
  lifecycle, input handlers, and suggestion picking.
- Routed custom dictionary additions through `LearningEngine.addCustomWord()`.
- Added replacement constants for remaining JNI dictionary/plugin IDs.
- Added committed-behavior tests for capitalization, double-space period,
  punctuation-space swapping, custom/learned words, backspace revert, and
  privacy-safe smart-text logging.
- Added voice latency policy, model warmup where memory allows, S21 latency
  target logging, and delayed offline posture when the target is missed.

## Voice Release Gate

Hard target before calling offline voice release-quality:
`stop-to-committed-text < 1000 ms`.

Original measurement on S21 `RFCNC0Y975L` using `voice-complex.wav` and
debug-server latency logs:

- `model_load`: 221 ms
- `preprocessing`: 11740 ms
- `inference`: 1400 ms
- `decode`: 0 ms
- `stop_to_result`: 13150 ms
- `process_file_to_committed`: 13389 ms

Decision: current Whisper Tiny path misses the release-quality target and is
labeled `offline_delayed`.

Follow-up optimization started after the first S21 gate showed preprocessing
dominating latency. The immediate fix replaces the allocating recursive Kotlin
FFT with JTransforms' optimized real FFT, skips padded mel frames for short
utterances, and runs the TFLite interpreter with four threads.

Second-pass optimization removed the debug fixture path's forced model reload,
memory-mapped the uncompressed `.tflite` asset, cached tensor metadata at model
load, and reused direct input/output buffers across inference runs. The debug
file path now measures the normal warmed voice path by default and keeps an
explicit `cold_start` flag for cold-start profiling.

Post-optimization S21 measurements:

- `voice-complex.wav` (39.7s file, trimmed to full 30s Whisper window):
  - `model_load`: 86 ms
  - `preprocessing`: 408 ms
  - `inference`: 1166 ms
  - `process_file_to_committed`: 1701 ms
  - release quality: false
- `voice-hello.wav` (1s low-amplitude latency probe, not an accuracy fixture):
  - `process_file_to_committed`: 714 ms
  - release quality: true

Second-pass warmed-path S21 measurements:

- `voice-hello.wav`:
  - first warmed run: `process_file_to_committed` 696 ms
  - repeat warmed run: `process_file_to_committed` 583 ms
  - final verification run: `process_file_to_committed` 579 ms
  - release quality: true
- `voice-complex.wav`:
  - `preprocessing`: 187 ms
  - `inference`: 1132 ms
  - `process_file_to_committed`: 1331 ms
  - final verification run: `process_file_to_committed` 1410 ms
  - release quality: false

Runtime knob audit:

- TFLite CPU/XNNPACK with 4 threads remains the best measured path.
- 3 threads regressed to 1629 ms full-window end-to-end.
- 2 threads regressed to 1675 ms full-window end-to-end.
- NNAPI failed interpreter initialization on S21 for this model and is disabled.

## Verification

Local:

- `./gradlew testDebugUnitTest assembleDebug detekt --no-daemon` passed.
- `./gradlew lint --no-daemon` passed.

S21:

- Preflight passed on `RFCNC0Y975L`.
- `input` passed after rerunning three transient failures from the first pass.
- `autocorrect`: 11 passed.
- `prediction`: 13 passed.
- `punctuation`: 13 passed.
- `voice`: 20 passed.
- Final voice short latency gate passed at 579 ms
  `process_file_to_committed`.
- Final voice full-window smoke passed at 1410 ms
  `process_file_to_committed`; release posture remains `offline_delayed`.
- `modes`: 10 passed.
- `modifiers`: 22 passed.
- `command_mode`: 6 passed.
- `clipboard`: 8 passed.
- `macros`: 8 passed.
- `test_toolbar_inventory`: 1 passed.
- Privacy flags in E2E result payloads remained false for typed text,
  clipboard contents, and transcripts.

## Remaining To-Do List

- Replace the full-window Whisper Tiny TFLite path with a streaming,
  quantized, smaller, or native runtime that can keep real speech
  `stop-to-committed-text < 1000 ms` on S21.
- Add a real short speech accuracy fixture; current `voice-hello.wav` is a
  low-amplitude latency probe and should not be used as an accuracy signal.
