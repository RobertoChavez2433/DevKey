# Code Review — Cycle 1
**Verdict:** REJECT

## HIGH findings
1. **Suggest.getNextWordSuggestions missing per-call state reset.** The new method resets only `mBigramPriorities` + `mBigramSuggestions` (and clears `mSuggestions`). It does not reset `mIsFirstCharCapitalized`, `mIsAllUpperCase`, `mLowerOriginalWord`, `mOriginalWord`, `mNextLettersFrequencies`. `addWord()` at Suggest.kt:385 + 425-431 consults these and will emit wrong-case and wrong-ordered bigram candidates if a prior `getSuggestions` call left them set. Fix:
   ```kotlin
   mIsFirstCharCapitalized = false
   mIsAllUpperCase = false
   mLowerOriginalWord = ""
   mOriginalWord = null
   mNextLettersFrequencies.fill(0)
   collectGarbage(mSuggestions, mPrefMaxSuggestions)  // not bare clear() — leaks StringBuilders from mStringPool
   ```
2. **Ambiguous insertion snippet in Phase 4.1 step 1.** Plan shows `val bigramSuggestions` context but a literal implementer could duplicate the public accessor. Reword as "Insert after Suggest.kt:54 — add ONLY the new `mEmptyComposer` val; do NOT re-declare existing fields."

## MEDIUM findings
3. **mSuggestions.clear() rationale is factually wrong.** `addWord` writes to `mBigramSuggestions` only when dataType == BIGRAM; during the next-word cascade nothing writes `mSuggestions`. Remove the clear() + comment, OR keep and fix rationale + use `collectGarbage`.
4. **"Zero-char composer disables currentChar filter" comment is wrong.** The `currentChar` filter at Suggest.kt:236-255 lives in `getSuggestions`, not `addWord`. `getNextWordSuggestions` bypasses it by structural absence. Rewrite the comment.
5. **BinaryDictionary.getBigrams JNI tolerance for zero-length composer not verified.** Must be verified before Phase 4 ships. Add a prereq step to Phase 4.1: read `BinaryDictionary.kt:186-230` and `getBigramsNative` (line 129) to confirm size==0 is accepted. If not, use a synthetic one-char composer with filter.

## LOW findings
6. KeyData design comment is at line 22, not 27. Update plan ref.

## Approved
- Phase 3 PluginManager gate (logic-sound)
- Phase 4.2 LatinIME.setNextSuggestions rewrite (all call sites, signatures, null safety verified)
- Phase 5.1 KeyData field add (source-compat verified)
- Phase 7.1 VoiceInputEngine line refs correct
