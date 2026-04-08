# Pattern: Bigram / Next-Word Suggestion Path

**Relevant to**: Phase 1.5

## How we do it

DevKey inherits a three-dictionary bigram stack from Hacker's Keyboard: `UserBigramDictionary` (SQLite) → `ContactsDictionary` → `mMainDict` (native `BinaryDictionary`). Each dictionary overrides `Dictionary.getBigrams(composer, previousWord, callback, nextLettersFrequencies)`, pushing candidates through `Suggest` acting as the `Dictionary.WordCallback`. The candidate list is consumed by `CandidateView.setSuggestions(...)` via `LatinIME.setSuggestions(...)`. **The read path is complete and correct** — what's missing is an entry point callable with zero typed characters (current gate: `wordComposer.size() == 1`).

## Exemplar 1 — Existing bigram collection inside `Suggest.getSuggestions`

`app/src/main/java/dev/devkey/keyboard/Suggest.kt:208-250` (excerpt)

```kotlin
if (wordComposer.size() == 1 && (mCorrectionMode == CORRECTION_FULL_BIGRAM ||
            mCorrectionMode == CORRECTION_BASIC)
) {
    // At first character typed, search only the bigrams
    mBigramPriorities.fill(0)
    collectGarbage(mBigramSuggestions, PREF_MAX_BIGRAMS)

    if (!mutablePrevWordForBigram.isNullOrEmpty()) {
        val lowerPrevWord: CharSequence = mutablePrevWordForBigram.toString().lowercase()
        if (mMainDict.isValidWord(lowerPrevWord)) {
            mutablePrevWordForBigram = lowerPrevWord
        }
        if (mUserBigramDictionary != null) {
            mUserBigramDictionary!!.getBigrams(
                wordComposer, mutablePrevWordForBigram!!, this,
                mNextLettersFrequencies
            )
        }
        if (mContactsDictionary != null) {
            mContactsDictionary!!.getBigrams(
                wordComposer, mutablePrevWordForBigram!!, this,
                mNextLettersFrequencies
            )
        }
        mMainDict.getBigrams(
            wordComposer, mutablePrevWordForBigram!!, this,
            mNextLettersFrequencies
        )
        // ... filters by currentChar, populates mSuggestions
    }
}
```

## Exemplar 2 — Current `setNextSuggestions` stub

`app/src/main/java/dev/devkey/keyboard/LatinIME.kt:1895-1897`

```kotlin
private fun setNextSuggestions() {
    setSuggestions(mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
}
```

**This is the target of Phase 1.5**. After a word commit (`pickDefaultSuggestion` at line 1839, `pickSuggestionManually` at line 1809), this method runs and populates the candidate strip with **punctuation marks**, not next-word predictions. The fix: call the bigram path with `wordComposer.size() == 0` instead of the punctuation list.

## Reusable methods

| Method | Signature | Purpose |
|---|---|---|
| `Dictionary.getBigrams` | `open fun getBigrams(composer: WordComposer, previousWord: CharSequence, callback: WordCallback, nextLettersFrequencies: IntArray?)` | Base contract — each dict implementation iterates bigram pairs for `previousWord` and callbacks into `Suggest.addWord`. |
| `Suggest.addWord` (as WordCallback) | `override fun addWord(word: CharArray, wordOffset: Int, wordLength: Int, freq: Int, dicTypeId: Int, dataType: Dictionary.DataType): Boolean` | Stores suggestion + priority, manages `mBigramSuggestions` pool. |
| `LatinIME.setSuggestions` (private) | `private fun setSuggestions(stringList: List<CharSequence>?, completions: Boolean, typedWordValid: Boolean, haveMinimalSuggestion: Boolean)` | Pushes to `mCandidateView.setSuggestions(...)`. |
| `WordComposer` | Tracks typed characters. For zero-char next-word path, pass `wordComposer` with `size() == 0` (empty). |

## Suggested Phase 1.5 shape

```kotlin
// In Suggest.kt — new public method
fun getNextWordSuggestions(prevWord: CharSequence?): List<CharSequence> {
    if (prevWord.isNullOrEmpty()) return emptyList()
    mBigramPriorities.fill(0)
    collectGarbage(mBigramSuggestions, PREF_MAX_BIGRAMS)
    mSuggestions.clear()

    val lowerPrev = prevWord.toString().lowercase()
    val emptyComposer = WordComposer()  // size() == 0
    mUserBigramDictionary?.getBigrams(emptyComposer, lowerPrev, this, null)
    mContactsDictionary?.getBigrams(emptyComposer, lowerPrev, this, null)
    mMainDict.getBigrams(emptyComposer, lowerPrev, this, null)

    // mBigramSuggestions is populated by addWord callback — copy to mSuggestions
    mSuggestions.addAll(mBigramSuggestions.take(mPrefMaxSuggestions))
    return mSuggestions
}

// In LatinIME.kt — modify setNextSuggestions
private fun setNextSuggestions() {
    val lastWord = getLastCommittedWord()  // new helper — reads from InputConnection or mWordHistory
    if (lastWord != null && mSuggest != null && isPredictionOn()) {
        val nextWords = mSuggest!!.getNextWordSuggestions(lastWord)
        if (nextWords.isNotEmpty()) {
            setSuggestions(nextWords, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
            return
        }
    }
    // Fallback to punctuation
    setSuggestions(mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
}
```

## Required imports (new)

```kotlin
// In Suggest.kt, already imported: WordComposer, Dictionary, etc. — no new imports
// In LatinIME.kt — no new imports
```

## Caveats

1. `WordComposer` constructor availability — verify it's parameterless or has a default ctor. If not, expose `Suggest.mEmptyComposer` as a lazy field.
2. `mBigramSuggestions` is an internal field of `Suggest`. The suggested shape above accesses it inside Suggest itself, not from LatinIME — keeps encapsulation.
3. `isPredictionOn()` already gates punctuation; same gate protects next-word.
4. `getLastCommittedWord()` does not exist today. `mWordHistory` (line 218) exists but stores `WordAlternatives`. Alternative: use `currentInputConnection.getTextBeforeCursor(32, 0)` and extract the last word via regex. This is the typical IME pattern.
