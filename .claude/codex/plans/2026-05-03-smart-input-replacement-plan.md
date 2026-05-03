# DevKey Smart Input Replacement Remaining Plan

Created: 2026-05-03
Updated: 2026-05-03

This file tracks only remaining implementation work. Completed audit, boundary,
logging, AnySoftKeyboard English dictionary import, production removal of
DevKey's trie/autocorrect engine, candidate-strip donor routing, temporary
donor-boundary next-word routing, local verification, and S21 verification items
were removed.

## Direction

DevKey keeps its keyboard surface: physical-key layout, modifier keys, toolbar,
macros, clipboard, command mode, privacy protections, and voice entry point.

The smart-text system is replaced. Do not grow DevKey's in-house autocorrect,
prediction, auto-capitalization, grammar, punctuation, or dictionary engine.
Current in-house code may exist only behind `SmartTextEngine` until the donor
adapter lands, then it should be deleted or retired.

## Donor And Dictionary Decision

Primary donor engine: AnySoftKeyboard.

Dictionary to add: AnySoftKeyboard's language-pack dictionary stack, starting
with the English dictionary pack path.

Implementation meaning:

- Treat AnySoftKeyboard's dictionary/language-pack model as the dictionary
  source of truth for the donor adapter.
- Keep DevKey's custom dictionary and learned-word privacy rules, but route
  ranking and correction behavior through the donor adapter.

Why this dictionary:

- AnySoftKeyboard is Apache-2.0 and has no-internet keyboard positioning.
- AnySoftKeyboard publishes language packs with dictionaries and keyboards.
- Its language-pack process supports AOSP/Lineage-style `XX_wordlist.combined.gz`
  inputs and generated raw `.dict` resources.
- This matches DevKey's privacy and plugin/dictionary direction better than
  continuing the in-house trie/fuzzy dictionary.

Rejected for now:

- DevKey `TrieDictionary` as the final smart-text dictionary.
- HeliBoard/OpenBoard-derived GPL dictionaries or engine code unless the whole
  project explicitly accepts GPL impact.
- FlorisBoard as the first dictionary source; keep it as a fallback donor only
  if the AnySoftKeyboard spike fails.

Sources checked:

- AnySoftKeyboard repo: https://github.com/AnySoftKeyboard/AnySoftKeyboard
- AnySoftKeyboard language packs: https://anysoftkeyboard.github.io/languages/
- AnySoftKeyboard language-pack template/process:
  https://github.com/AnySoftKeyboard/LanguagePack
- Example F-Droid language pack showing AOSP-based dictionary packaging:
  https://f-droid.org/en/packages/com.anysoftkeyboard.languagepack.french/

## Remaining To-Do List

### 1. AnySoftKeyboard Source Spike

- [ ] Measure APK size, memory, and startup cost for the selected import path.
- [ ] Record whether source vendoring or a packaged ASK language-pack module is
  needed after the thin dictionary adapter is exercised in release-like typing.

### 2. Donor SmartText Adapter

- [ ] Move capitalization decisions to the donor adapter.
- [ ] Move punctuation transforms to the donor adapter.
- [ ] Move learned-word ranking to the donor adapter.
- [ ] Preserve DevKey ownership of rendering, modifiers, toolbar modes, command
  mode, macros, clipboard, voice flow, and privacy rules.
- [ ] Keep credential-sensitive fields, command mode, URLs, emails, code-like
  tokens, and mixed alphanumeric tokens excluded from correction.

### 3. Retire Remaining Current Smart-Text Code

- [ ] Delete or quarantine remaining legacy `Suggest`, `SuggestionPipeline`,
  `NextWordSuggester`, and dictionary-ref classes once lifecycle dependencies
  are confirmed unused outside the donor adapter.
- [ ] Leave compatibility shims only where needed for staged deletion, with clear
  delete-by markers.

### 4. Product-Quality Tests

- [ ] Add committed-behavior tests for auto-capitalization after sentence endings.
- [ ] Add committed-behavior tests for double-space period and punctuation-space
  swapping.
- [ ] Add committed-behavior tests for parentheses and quote handling if the donor
  adapter claims those transforms.
- [ ] Add tests for learned words and custom dictionary behavior through the donor
  adapter.
- [ ] Add backspace-revert tests against donor-applied corrections.
- [ ] Keep privacy tests proving no typed text, credentials, transcripts, clipboard
  contents, or dictionary words are logged.

### 5. Voice Release Gate

- [ ] Benchmark current Whisper Tiny latency on S21 using the existing latency logs.
- [ ] Define a hard stop-to-committed-text latency target before calling voice
  release quality.
- [ ] Evaluate model preload/warmup where memory allows.
- [ ] Evaluate faster local runtimes or smaller/streaming models if the target is
  missed.
- [ ] If offline voice misses target, explicitly choose one release posture:
  keep offline voice labeled delayed, disable release voice, or add optional
  system/cloud recognition behind a privacy warning.
