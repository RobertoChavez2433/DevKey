---
name: BinaryDictionary.getBigrams — empty WordComposer fix (Phase 4.1)
description: The IOOBE from getCodesAt(0) on an empty composer is fully patched; sentinel workaround removed. Main dict bigrams now work for next-word prediction.
type: project
---

`BinaryDictionary.getBigrams` previously called `composer.getCodesAt(0)` unconditionally (line 198), throwing `IndexOutOfBoundsException` on an empty `WordComposer`.

**Fix applied (Phase 4.1 option B):**

1. `BinaryDictionary.kt` `getBigrams`: guarded the `getCodesAt(0)` call with `if (codesSize > 0) { ... }`. When `codesSize == 0`, `mInputCodes` stays filled with `-1` and the native call proceeds safely.

2. `dictionary.cpp` `Dictionary::searchForTerminalNode` (line 536): changed `if (checkFirstCharacter(word))` to `if (mInputLength == 0 || checkFirstCharacter(word))`. When `mInputLength == 0` (next-word prediction path), all bigram candidates pass through without first-character filtering.

3. `Suggest.kt` `mEmptyComposer`: reverted from the sentinel `WordComposer().also { it.add(-1, intArrayOf(-1)) }` to a plain `WordComposer()`. All three dictionaries (main, user-bigram, contacts) now return results in `getNextWordSuggestions`.

**How to apply:** No further workarounds needed. `getNextWordSuggestions` can pass `mEmptyComposer` directly to any `Dictionary.getBigrams` call.
