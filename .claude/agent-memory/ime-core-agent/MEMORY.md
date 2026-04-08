# Agent Memory — IME Core Agent

## Patterns Discovered

## Gotchas & Quirks
- [BinaryDictionary.getBigrams — empty WordComposer fix (Phase 4.1)](gotcha_binarydict_bigrams_size_zero.md) — IOOBE patched in BinaryDictionary.kt + dictionary.cpp; sentinel removed from Suggest.mEmptyComposer; main dict bigrams now work for next-word prediction

## Architectural Decisions

## Frequently Referenced Files
| File | Notes |
|------|-------|
| LatinIME.kt | ~2500 lines, main IME entry point |
| KeyEventSender.kt | ~330 lines, key synthesis |
| ModifierStateManager.kt | Shift/Ctrl/Alt/Meta unified state |
