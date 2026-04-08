# DevKey v1.0 Pre-Release — Phase 1 (Feature Completion) Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** Complete all v1.0 feature-completion work (voice asset bundle, SwiftKey reference capture, plugin security gate, next-word prediction wiring, long-press popup data fill, layout fidelity tuning, voice end-to-end verification, existing-feature regression) so that Phase 2 (regression infrastructure) can begin.

**Spec:** `.claude/specs/2026-04-08-pre-release-vision-spec.md` (§6 Phase 1, items 1.1–1.8)
**Tailor:** `.claude/tailor/2026-04-08-pre-release-phase1/`

**Architecture:** No architectural churn. Edits are surgical: PluginManager gains a debug-build early-return, Suggest gains one new public method, LatinIME.setNextSuggestions is rewritten to call it, KeyData gets an optional `longPressCodes` field for multi-char popups, layout builders fill in long-press data, DevKeyTheme tokens are re-tuned against SwiftKey reference screenshots. Compose UI wiring, IME lifecycle, and the JNI bridge are untouched.

**Tech Stack:** Kotlin + C++ NDK, Jetpack Compose, Room, TF Lite, Gradle KTS

**Blast Radius:** 6 source files modified (`PluginManager.kt`, `Suggest.kt`, `LatinIME.kt`, `KeyData.kt`, `QwertyLayout.kt`, `SymbolsLayout.kt`, `DevKeyTheme.kt` — 7 total, all contained), 2 asset files added (`whisper-tiny.en.tflite`, `filters_vocab_en.bin`), 0 direct dependents requiring cascade edits (backward-compatible data-class field add, new public method has no existing callers), test artifacts added under `.claude/test-flows/swiftkey-reference/`. No JNI, no build.gradle, no AndroidManifest changes.

---

## Ground-Truth Discrepancies (from tailor — resolved here)

All three discrepancies in `.claude/tailor/2026-04-08-pre-release-phase1/ground-truth.md` §Discrepancies are resolved as follows:

1. **Bigram gate `wordComposer.size() == 1`** (Suggest.kt:208) — resolved by adding a new public method `Suggest.getNextWordSuggestions(prevWord)` that runs the bigram path with `size() == 0` allowed via a fresh empty `WordComposer()`. Existing `getSuggestions()` is untouched; zero risk to current callers. See Phase 4.
2. **COMPACT mode long-press scope vs spec §4.2** ("Same long-press popup on every key") — Deferred to user escalation — see Phase 5 gate. The existing `KeyData.kt:22` design comment says COMPACT has no long-press on letter keys; spec §4.2 conflicts. Resolution must be selected by the user before Phase 5 begins.
3. **No signature-verification exemplar for GH #4** — resolved by option **(b)** per spec §2.4: debug-build gate inside PluginManager, using the same `ApplicationInfo.FLAG_DEBUGGABLE` pattern as `KeyMapGenerator.isDebugBuild(context)` (KeyMapGenerator.kt:38). Full signature verification is deferred to v1.1. See Phase 3.

**Open item for implementer** (manual / out-of-band):
- Phase 1 sub-phase 1.1 (whisper model sourcing) has a human-gated provenance + license step. The plan provides the directory, `.gitattributes`, and `MODEL_PROVENANCE.md` scaffold, but the user (or a human operator) must fetch the two binary asset files and record SHA256s. Do NOT commit speculative binaries.

---

## Spec §9 Open Questions

The following spec §9 open questions must be resolved by the user before Phase 1 closes. Defaults below apply only where noted; Q1 and Q2 remain blocking.

- **Q1 — Distribution channel** (Play Store vs F-Droid vs both): OPEN. User must decide before release builds are signed.
- **Q2 — Min API**: OPEN. Currently API 26 in `build.gradle.kts`; user must confirm or raise.
- **Q3 — Visual-diff tolerance**: PARTIAL DEFAULT — SSIM > 0.95 per row region (recorded in `.claude/test-flows/swiftkey-reference/README.md`). Pixel-exact reserved for hit-box structural parity. User may override.
- **Q4 — Multilingual voice bundle**: DEFAULT — English-only (`whisper-tiny.en.tflite`). Multilingual model is out of v1.0 scope. Recorded as assumption; user may override, in which case spec §5.2 needs amendment.

---

## Phase Ordering Rationale

Spec §6 lists Phase-1 sub-items in the order 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8. This plan groups them by dependency and by the phase-ordering rules (data/backend before UI, native last, dead-code last):

| Plan Phase | Spec item | Why this slot |
|---|---|---|
| 1 | 1.1 Voice assets | Prerequisite gate — unblocks 1.6 verification. Pure file drop, no code. |
| 2 | 1.2 SwiftKey reference capture | Prerequisite for 1.3 (references ARE the visual-diff oracle). Test-artifacts only, no code. |
| 3 | 1.8 Plugin security debug-gate | Small, isolated backend change. Do early so all subsequent phases build & test under the release-safe config. |
| 4 | 1.5 Next-word prediction wiring | IME backend (`Suggest` + `LatinIME`) before any UI-layer work. |
| 5 | 1.4 Long-press popup data fill | Layout data fill (no UI logic change). |
| 6 | 1.3 Layout fidelity pass | Theme token tuning. Consumes the SwiftKey references from Phase 2. |
| 7 | 1.6 Voice end-to-end verification | Uses assets from Phase 1. Light instrumentation + manual verification checklist. |
| 8 | 1.7 Existing feature regression pass | Final smoke across clipboard / macros / command / plugin surfaces (1.7). |

There are no C++ / JNI changes in Phase 1.

---

## Phase 1: Voice Model Asset Bundle (spec 1.1, §5.2)

Create the `app/src/main/assets/` directory and scaffold provenance docs so a human operator can source the two Whisper asset files. No Kotlin changes — `VoiceInputEngine.kt:98` already opens `whisper-tiny.en.tflite` via the asset manager, and `VoiceInputEngine.kt:113-117` already handles missing-asset exceptions gracefully.

### Sub-phase 1.1: Create assets directory with provenance scaffold

**Files:**
- `app/src/main/assets/.gitkeep` (create)
- `app/src/main/assets/MODEL_PROVENANCE.md` (create)
- `.gitattributes` (modify — add LFS pattern if repo uses LFS, otherwise create new)

**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Create `app/src/main/assets/` directory by adding a placeholder `.gitkeep` file.
   ```
   # file: app/src/main/assets/.gitkeep
   (empty file — tracks the directory in git)
   ```

2. Create `app/src/main/assets/MODEL_PROVENANCE.md` documenting the required files and the manual sourcing step.
   ```markdown
   # Voice Model Asset Provenance (DevKey v1.0)

   This directory must contain two files before voice input is functional:

   | File | Size (approx) | Required by |
   |---|---|---|
   | `whisper-tiny.en.tflite` | ~40 MB | `VoiceInputEngine.kt:98` — `assetManager.open("whisper-tiny.en.tflite")` |
   | `filters_vocab_en.bin` | ~1 MB | `WhisperProcessor.loadResources()` — mel filterbank + vocab |

   ## Sourcing

   Per spec §5.2 the user chooses ONE provenance path and records the SHA256
   of each file below before committing. Do NOT commit files without a
   verified SHA256.

   - [ ] `whisper-tiny.en.tflite` source URL: _____________________________
   - [ ] `whisper-tiny.en.tflite` SHA256:       _____________________________
   - [ ] `filters_vocab_en.bin` source URL:     _____________________________
   - [ ] `filters_vocab_en.bin` SHA256:         _____________________________
   - [ ] License compatible with DevKey (Apache-2.0 / MIT / BSD):  ☐ Yes  ☐ No
   - [ ] APK size impact verified under 55 MB total:               ☐ Yes  ☐ No

   ## Graceful degradation

   If these files are missing, `VoiceInputEngine.initialize()` catches the
   exception and sets `modelLoaded = false`. The keyboard still functions;
   voice button will either be hidden or will surface the error state.
   This is the intended fallback for debug / development builds without
   the assets bundled. Ship builds MUST have both files.

   ## References

   - Spec: `.claude/specs/2026-04-08-pre-release-vision-spec.md` §5
   - Code path: `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt:93-119`
   - GH issue (tracking): to be filed if blocked
   ```

**If APK size check fails** (final APK > 55 MB or the two asset files exceed spec §5.2's "unacceptable" threshold), STOP and escalate. The fallback is spec §5.2 Q4 option B (on-demand model download), which is a Phase-1 architectural change requiring a spec amendment. Do not silently proceed with a bloated APK.

3. Add an LFS pattern entry for `.tflite` and `.bin` in `.gitattributes`. First check if the file exists; if not, create it.
   ```
   # file: .gitattributes
   app/src/main/assets/*.tflite filter=lfs diff=lfs merge=lfs -text
   app/src/main/assets/*.bin    filter=lfs diff=lfs merge=lfs -text
   ```

   // WHY: The two asset files are ~40 MB combined. Without LFS they bloat
   // git history and slow every clone. The LFS patterns are inert until
   // the user actually drops the binaries; adding them now means the first
   // `git add` of the binaries automatically routes through LFS.
   // NOTE: If `.gitattributes` already exists with different patterns,
   // APPEND these two lines — do not overwrite.

4. **Do NOT commit binary asset files in this plan execution.** The binaries are sourced by a human operator, committed separately, and their SHA256s are recorded in `MODEL_PROVENANCE.md`. The `/implement` run of this phase stops at the directory + scaffold.

**Verification:** `./gradlew assembleDebug`

// WHY assembleDebug here: confirms the new `assets/` directory does not
// break the build (it shouldn't — the build was tolerant of a missing
// assets dir before, and is definitely tolerant of an empty one).

---

## Phase 2: SwiftKey Reference Capture (spec 1.2, §4.4)

Create the directory structure + README for the SwiftKey reference screenshots that Phase 6 (layout fidelity) will target for visual diff. Pure test-artifact scaffolding; no source code changes. The human operator captures actual screenshots out-of-band (they are legally sensitive — internal use only per spec §8 risk register).

### Sub-phase 2.1: Create swiftkey-reference directory + README

**Files:**
- `.claude/test-flows/swiftkey-reference/README.md` (create)
- `.claude/test-flows/swiftkey-reference/.gitignore` (create)

**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Create `.claude/test-flows/swiftkey-reference/README.md`.
   ```markdown
   # SwiftKey Reference Screenshots (internal-use-only)

   **Scope**: Spec `.claude/specs/2026-04-08-pre-release-vision-spec.md` §4.4 Ground Truth.
   **Legal**: Per spec §8 Risk Register — "capture reference screenshots for
   internal comparison only, do not redistribute them." These files must NOT
   leave this repository and MUST NOT be published to any mirror, release
   artifact, or public CI log. Add to `.gitignore` if repo is published.

   ## Required captures

   All captures on the SAME emulator image / DPI / screen size that DevKey
   `/test` runs against, so pixel diffs are meaningful.

   ### Keyboard modes (×2 themes = 12 images)
   - [ ] `qwerty-light.png`
   - [ ] `qwerty-dark.png`
   - [ ] `compact-light.png`
   - [ ] `compact-dark.png`
   - [ ] `full-light.png`
   - [ ] `full-dark.png`
   - [ ] `symbols-light.png`
   - [ ] `symbols-dark.png`
   - [ ] `fn-light.png`
   - [ ] `fn-dark.png`
   - [ ] `phone-light.png`
   - [ ] `phone-dark.png`

   ### Long-press popups (1 per key per mode — captured during Phase 5)
   Stored under `long-press/<mode>/<keyname>.png`.

   ## Visual-diff tolerance

   Spec §9 Q3 is unresolved: pixel-exact vs similarity (SSIM > 0.95).
   Default: SSIM > 0.95 per row region; escalate to pixel-exact only where
   structural parity is at stake (key hit-box sizes).

   ## References

   - Spec §4 SwiftKey Parity Scope
   - `.claude/test-flows/registry.md` — test flow registry (entry to be
     added in Phase 2 of the umbrella spec, not this Phase 1 plan)
   ```

2. Create `.claude/test-flows/swiftkey-reference/.gitignore` to make accidental public mirrors fail closed.
   ```gitignore
   # Internal-use-only per spec §8 risk register.
   # Screenshots of third-party UI are excluded by default. If the internal
   # team decides to version-control references for review, move them to a
   # private submodule — do not remove this exclusion.
   *.png
   *.jpg
   *.jpeg
   *.webp
   !README.md
   ```

   // WHY: Default excludes all image formats. `!README.md` ensures the
   // provenance/scope README stays tracked. Active-by-default matches
   // spec §8 risk register ('do not redistribute').

**Verification:** `./gradlew assembleDebug`

---

## Phase 3: Plugin Security Debug-Gate (spec 1.8, §2.4, GH #4)

Wrap `PluginManager.getPluginDictionaries` and `PluginManager.getDictionary` in a compile-time `BuildConfig.DEBUG` check so release builds treat plugin loading as a no-op. This is the minimal fix per spec §2.4(b); full signature verification is deferred to v1.1. Backward-compatible: `Suggest.kt:70` already handles `getDictionary` returning null.

### Sub-phase 3.0: Enable buildConfig in app/build.gradle.kts

**Files:** `app/build.gradle.kts` (modify)

**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Read `app/build.gradle.kts`.

2. In the `android { ... }` block, add (or verify) `buildFeatures { buildConfig = true }`. This generates `dev.devkey.keyboard.BuildConfig` with a compile-time `DEBUG` boolean used by sub-phase 3.1.

   // WHY: Compile-time gate is stricter than runtime FLAG_DEBUGGABLE — a
   // re-signed APK with debuggable=true cannot re-enable plugin loading,
   // closing the GH #4 attack surface more tightly.

**Verification:** `./gradlew assembleDebug`

### Sub-phase 3.1: Add debug-build gate to PluginManager

**Files:** `app/src/main/java/dev/devkey/keyboard/PluginManager.kt` (modify)

**Agent:** `ime-core-agent`

**Steps:**

1. Add `import dev.devkey.keyboard.BuildConfig` to the import block at the top of `PluginManager.kt` (currently lines 3–11). Insert alphabetically.
   ```kotlin
   import dev.devkey.keyboard.BuildConfig
   ```

2. Replace the `getPluginDictionaries(context: Context)` function body at `PluginManager.kt:220-225` with a debug-gate early-return.

   Current code (PluginManager.kt:220-225):
   ```kotlin
   fun getPluginDictionaries(context: Context) {
       mPluginDicts.clear()
       val packageManager = context.packageManager
       getSoftKeyboardDictionaries(packageManager)
       getLegacyDictionaries(packageManager)
   }
   ```

   New code:
   ```kotlin
   fun getPluginDictionaries(context: Context) {
       // WHY: GH #4 — plugins load untrusted foreign-package resources
       // into the native dictionary via BinaryDictionary without signature
       // verification. Per spec §2.4(b), disable the load path entirely in
       // release builds. Full signature verification is a v1.1 target.
       // Compile-time gate (BuildConfig.DEBUG) — re-signed APKs with
       // debuggable=true cannot re-enable plugin loading, closing the
       // GH #4 attack surface more tightly than FLAG_DEBUGGABLE would.
       if (!arePluginsEnabled()) {
           Log.i(TAG, "Plugin dictionaries disabled in release build (GH #4)")
           mPluginDicts.clear()
           return
       }
       mPluginDicts.clear()
       val packageManager = context.packageManager
       getSoftKeyboardDictionaries(packageManager)
       getLegacyDictionaries(packageManager)
   }
   ```

3. Replace the `getDictionary(context: Context, lang: String)` function at `PluginManager.kt:227-236` to short-circuit in release builds.

   Current code:
   ```kotlin
   fun getDictionary(context: Context, lang: String): BinaryDictionary? {
       var spec = mPluginDicts[lang]
       if (spec == null && lang.length >= 2) spec = mPluginDicts[lang.substring(0, 2)]
       if (spec == null) {
           return null
       }
       val dict = spec.getDict(context)
       Log.i(TAG, "Found plugin dictionary for $lang${if (dict == null) " is null" else ", size=${dict.getSize()}"}")
       return dict
   }
   ```

   New code:
   ```kotlin
   fun getDictionary(context: Context, lang: String): BinaryDictionary? {
       // WHY: GH #4 — defense in depth. Even if mPluginDicts somehow got
       // populated (e.g. debug build handed off a running process to a
       // release build — not currently possible, but cheap guard), refuse
       // to hand back any plugin dictionary in release builds.
       if (!arePluginsEnabled()) return null
       var spec = mPluginDicts[lang]
       if (spec == null && lang.length >= 2) spec = mPluginDicts[lang.substring(0, 2)]
       if (spec == null) {
           return null
       }
       val dict = spec.getDict(context)
       Log.i(TAG, "Found plugin dictionary for $lang${if (dict == null) " is null" else ", size=${dict.getSize()}"}")
       return dict
   }
   ```

4. Add the `arePluginsEnabled` private helper to the `companion object` (insert immediately after the `private val mPluginDicts = HashMap<String, DictPluginSpec>()` line at `PluginManager.kt:105`).
   ```kotlin
   /**
    * GH #4: plugin loading is disabled in release builds. Debug builds
    * retain the current behavior so local development and dictionary
    * plugin authors can keep testing.
    *
    * Compile-time gate via BuildConfig.DEBUG. A re-signed APK with
    * debuggable=true cannot flip this flag — strictly stronger than
    * the runtime ApplicationInfo.FLAG_DEBUGGABLE check used by
    * KeyMapGenerator.isDebugBuild. Sub-phase 3.0 enables
    * `buildFeatures.buildConfig = true` so this constant exists.
    */
   private fun arePluginsEnabled(): Boolean = BuildConfig.DEBUG
   ```

5. Verify no call sites of `PluginManager.getPluginDictionaries` or `PluginManager.getDictionary` need changes. Expected callers (per tailor blast-radius):
   - `LatinIME.kt:379` — `PluginManager.getPluginDictionaries(applicationContext)` (unchanged — returns early in release)
   - `Suggest.kt:70` — `PluginManager.getDictionary(context, locale.language)` (unchanged — already handles null)
   - `InputLanguageSelection.kt:111` — same pattern (unchanged — already handles null)

   No caller edits. The debug-gate is transparent to all consumers.

6. Mark `getSoftKeyboardDictionaries(packageManager: PackageManager)` and `getLegacyDictionaries(packageManager: PackageManager)` as `private fun` (defense-in-depth — no external callers exist per tailor blast-radius; private-by-default prevents future accidental bypass of the debug gate).

**Verification:** `./gradlew assembleDebug`

---

## Phase 4: Predictive Next-Word Wiring (spec 1.5, §4.3)

Add `Suggest.getNextWordSuggestions(prevWord)` running the bigram path with an empty `WordComposer`, then rewrite `LatinIME.setNextSuggestions` to call it with the last committed word (extracted from `InputConnection.getTextBeforeCursor`). Falls back to the existing punctuation list when no bigram candidates exist. Zero signature changes to existing methods; `getSuggestions(wordComposer.size() == 1)` gate is untouched.

### Sub-phase 4.1: Add `getNextWordSuggestions` to Suggest

**Files:** `app/src/main/java/dev/devkey/keyboard/Suggest.kt` (modify)

**Agent:** `ime-core-agent`

**Steps:**

0. **Prerequisite verification:** Before editing Suggest.kt, read `BinaryDictionary.kt:186-230` and the native `getBigramsNative` declaration (around line 129). Confirm that passing a `WordComposer` with `size() == 0` does not trip a native assertion. If the native layer requires `size() >= 1`, STOP and escalate — the fallback is to pass a synthetic one-char composer whose character is then filtered by the caller, which requires a JNI scope expansion (out of Phase 1).

1. Insert a NEW line immediately after Suggest.kt:54 (after the existing `val bigramSuggestions` accessor). Add ONLY the new `private val mEmptyComposer = WordComposer()` declaration + its comment. DO NOT re-declare `mBigramSuggestions` or `bigramSuggestions` — they already exist.
   ```kotlin
   // WHY: next-word prediction (spec §4.3) needs to run the bigram path
   // with zero typed characters. WordComposer() has a no-arg ctor
   // (WordComposer.kt:39) so reusing one shared empty instance is safe
   // and avoids GC pressure when the user taps space repeatedly.
   private val mEmptyComposer = WordComposer()
   ```

2. Add a new public method `getNextWordSuggestions(prevWord: CharSequence?)` to the `Suggest` class. Insert immediately before the `override fun addWord(...)` declaration at `Suggest.kt:364` so it sits next to the callback it exercises.
   ```kotlin
   /**
    * Bigram-only suggestions for the "next word after space" case.
    *
    * Runs the same three-dictionary bigram path as getSuggestions()'s
    * wordComposer.size() == 1 branch (Suggest.kt:208) but with a zero-char
    * composer so candidates are not filtered by `currentChar`. Used by
    * LatinIME.setNextSuggestions to populate the candidate strip after a
    * word is committed and space is entered.
    *
    * FROM SPEC: §4.3 "predictive next-word after space". The only
    * behavioral parity feature for v1.0.
    *
    * @param prevWord the last committed word. Null or empty returns an
    *                 empty list (no prediction possible without context).
    * @return up to mPrefMaxSuggestions bigram candidates, ordered by
    *         frequency. Empty if no bigrams found.
    */
   fun getNextWordSuggestions(prevWord: CharSequence?): List<CharSequence> {
       if (prevWord.isNullOrEmpty()) return emptyList()

       // Reset scratch state the same way getSuggestions() does for its
       // bigram branch (Suggest.kt:212-213), plus the capitalization /
       // original-word fields the addWord callback consults.
       mIsFirstCharCapitalized = false
       mIsAllUpperCase = false
       mLowerOriginalWord = ""
       mOriginalWord = null
       mNextLettersFrequencies.fill(0)
       mBigramPriorities.fill(0)
       collectGarbage(mBigramSuggestions, PREF_MAX_BIGRAMS)
       collectGarbage(mSuggestions, mPrefMaxSuggestions)

       // The lowercasing guard mirrors Suggest.kt:215-218: the bigram
       // dictionaries store lowercase keys, so the lookup key must be
       // lowercased if the main dict already knows the lowercase form.
       var lookupPrev: CharSequence = prevWord
       val lowerPrevWord = prevWord.toString().lowercase()
       if (mMainDict.isValidWord(lowerPrevWord)) {
           lookupPrev = lowerPrevWord
       }

       // Same three-dictionary cascade as the existing bigram branch.
       // The `currentChar` filter at Suggest.kt:236-255 lives inside
       // `getSuggestions` and is bypassed by design — `getNextWordSuggestions`
       // does not go through that loop. `mEmptyComposer` is passed to
       // `getBigrams` because the Dictionary API requires a non-null
       // `WordComposer`; its contents are not consulted for filtering by
       // ExpandableDictionary/BinaryDictionary implementations.
       if (mUserBigramDictionary != null) {
           mUserBigramDictionary!!.getBigrams(
               mEmptyComposer, lookupPrev, this, mNextLettersFrequencies
           )
       }
       if (mContactsDictionary != null) {
           mContactsDictionary!!.getBigrams(
               mEmptyComposer, lookupPrev, this, mNextLettersFrequencies
           )
       }
       mMainDict.getBigrams(
           mEmptyComposer, lookupPrev, this, mNextLettersFrequencies
       )

       // addWord() has populated mBigramSuggestions via the WordCallback.
       // Copy the top-N into a fresh list so the caller owns it and we
       // don't leak internal state.
       val cap = minOf(mBigramSuggestions.size, mPrefMaxSuggestions)
       val result = ArrayList<CharSequence>(cap)
       for (i in 0 until cap) {
           result.add(mBigramSuggestions[i].toString())
       }
       return result
   }
   ```

   // NOTE: `addWord` is already the `Dictionary.WordCallback` for this
   // class (Suggest.kt:364). It routes bigram hits into
   // `mBigramSuggestions` when `dataType == DataType.BIGRAM`, which is
   // what the three `getBigrams` calls above cause the dictionaries to
   // emit. No new callback logic is needed.
   //
   // IMPORTANT: Defensively reset `mSuggestions` via collectGarbage so
   // any StringBuilders return to mStringPool. Our bigram cascade only
   // populates mBigramSuggestions, but this keeps the method hermetic.

3. Verify no existing call sites of `Suggest.getSuggestions` are affected — the new method is additive. Run `Grep -pattern="Suggest.*getSuggestions"` to confirm zero callers need updates.

**Verification:** intentionally batched — see end-of-phase verification at the end of Phase 4 (sub-phase 4.2).

### Sub-phase 4.2: Rewrite `LatinIME.setNextSuggestions` to call the bigram path

**Files:** `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` (modify)

**Agent:** `ime-core-agent`

**Steps:**

1. Add a new private helper `getLastCommittedWordBeforeCursor(): CharSequence?` to `LatinIME`. Insert immediately before the existing `private fun setNextSuggestions()` at `LatinIME.kt:1895`.
   ```kotlin
   /**
    * Extracts the last completed word from the text immediately before
    * the cursor. Used by setNextSuggestions for bigram prediction.
    *
    * Returns null when:
    *   - InputConnection is unavailable
    *   - Cursor is at the start of the field
    *   - No word-character precedes the cursor (e.g. cursor is after
    *     punctuation with no preceding word)
    *
    * Reads up to 64 chars before the cursor and walks backward past
    * whitespace, then captures the longest run of word characters
    * immediately before the whitespace. This is the standard IME
    * pattern for next-word context extraction.
    *
    * FROM SPEC: §4.3 — next-word prediction needs the previously
    * committed word as the bigram lookup key.
    */
   private fun getLastCommittedWordBeforeCursor(): CharSequence? {
       val ic = currentInputConnection ?: return null
       val before = ic.getTextBeforeCursor(64, 0) ?: return null
       if (before.isEmpty()) return null

       // Walk backward past trailing whitespace (usually the space that
       // just triggered setNextSuggestions).
       var end = before.length
       while (end > 0 && before[end - 1].isWhitespace()) end--
       if (end == 0) return null

       // Walk backward over word characters to find the start of the
       // last word. "Word char" matches the bigram dictionary's notion
       // of a word: letters, digits, apostrophe (for contractions).
       var start = end
       while (start > 0) {
           val c = before[start - 1]
           if (c.isLetterOrDigit() || c == '\'') start-- else break
       }
       if (start == end) return null
       return before.subSequence(start, end)
   }
   ```

2. Replace the body of `setNextSuggestions()` at `LatinIME.kt:1895-1897`.

   Current code:
   ```kotlin
   private fun setNextSuggestions() {
       setSuggestions(mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
   }
   ```

   New code:
   ```kotlin
   private fun setNextSuggestions() {
       // FROM SPEC: §4.3 predictive next-word after space. If the bigram
       // path returns any candidates for the previously committed word,
       // show them in the candidate strip; otherwise fall back to the
       // punctuation list (the v0 behavior).
       //
       // WHY gated on isPredictionOn(): matches the gate on every other
       // suggestion-update path in this class (see updateSuggestions at
       // LatinIME.kt:1685). No prediction = no candidate strip activity.
       //
       // NOTE: fallback to mSuggestPuncList is intentional — it's the
       // same behavior users had before, so a dict with no bigram hits
       // (or a release build with plugin dictionaries disabled) is
       // functionally identical to the pre-change state.
       val suggestImpl = mSuggest
       if (suggestImpl != null && isPredictionOn()) {
           val prevWord = getLastCommittedWordBeforeCursor()
           if (prevWord != null) {
               val nextWords = suggestImpl.getNextWordSuggestions(prevWord)
               if (nextWords.isNotEmpty()) {
                   setSuggestions(
                       nextWords,
                       completions = false,
                       typedWordValid = false,
                       haveMinimalSuggestion = false
                   )
                   return
               }
           }
       }
       // Fallback: show the punctuation list (pre-Phase-1 behavior).
       setSuggestions(
           mSuggestPuncList,
           completions = false,
           typedWordValid = false,
           haveMinimalSuggestion = false
       )
   }
   ```

3. Verify all 8 call sites of `setNextSuggestions` still compile without change. Per tailor dependency-graph:
   - Line 905, 960, 1687, 1809, 1839, 1889, 2476 — all call `setNextSuggestions()` with zero args
   - Line 1895 — the declaration (the one being modified)

   No argument-list changes; all call sites are source-compatible.

**Verification:** `./gradlew assembleDebug`

---

## Phase 5: Long-Press Popup Data Fill (spec 1.4, §4.2)

> **USER ESCALATION REQUIRED BEFORE PHASE 5 BEGINS**
>
> Spec §4.2 says "Same long-press popup content on every key." Spec §2.1 enumerates Compact mode in-scope. The current KeyData.kt:22 design comment says COMPACT has "no long-press on letter keys." These conflict.
>
> **Decision required from the user:**
> - **(A)** Follow spec literally — populate long-press data on COMPACT letter keys, overriding the design comment. This is the scope-maximal interpretation.
> - **(B)** Keep COMPACT as the minimalist touch-typing mode (no letter-key long-press). This requires a spec amendment adding COMPACT to §3 Non-Goals or scoping §4.2 to "full parity modes."
>
> `/implement` MUST NOT begin Phase 5 sub-phases until the user selects (A) or (B). If (A), sub-phase 5.2 includes a `buildCompactLayout` letter-row long-press pass. If (B), the spec is amended and the scope-fence comment in `buildCompactLayout` is retained with a reference to the amendment commit.

> **Note on Fn / Phone layouts:** A repository scan (`Glob **/FnLayout*`, `Glob **/PhoneLayout*`, `Grep FnLayout|PhoneLayout` under `app/src/main/java`) confirms no separate `FnLayout.kt` or `PhoneLayout.kt` files exist. Fn and Phone are variants handled within the existing layout builders — long-press fill is therefore covered by sub-phase 5.2 (QwertyLayout) and sub-phase 5.3 (SymbolsLayout) as part of the full-mode pass. If a future audit reveals separate files, add sub-phases 5.3b (Fn) and 5.3c (Phone) following the same pattern as 5.2.

Phase 1.4 is a pure data-fill exercise. The long-press dispatch machinery already works end-to-end (`KeyView.kt:171, 213-215` reads `key.longPressCode` and dispatches via `onKeyAction`). The gap is that many `KeyData(...)` constructions in `QwertyLayout` / `SymbolsLayout` have `longPressLabel = null, longPressCode = null`. This phase adds long-press data for `LayoutMode.FULL`, `LayoutMode.COMPACT_DEV`, and the symbols layout, and optionally extends `KeyData` to carry a `longPressCodes: List<Int>?` for multi-character SwiftKey-style popups (e.g. `a → à á â ã ä å æ`).

**Scope fence**: `LayoutMode.COMPACT` keeps the existing "no long-press on letter keys" design per the `KeyData.kt:22` doc comment. This is discrepancy #2 from ground-truth; the completeness-review agent will flag it — if the user wants parity on COMPACT too, escalate before implementing. See `Ground-Truth Discrepancies` at the top of this plan.

### Sub-phase 5.1: Extend KeyData with optional `longPressCodes` list

**Files:** `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt` (modify)

**Agent:** `compose-ui-agent`

**Steps:**

1. Add an optional `longPressCodes: List<Int>? = null` field to the `KeyData` data class at `KeyData.kt:43-51`.

   Current code:
   ```kotlin
   data class KeyData(
       val primaryLabel: String,
       val primaryCode: Int,
       val longPressLabel: String? = null,
       val longPressCode: Int? = null,
       val type: KeyType = KeyType.LETTER,
       val weight: Float = 1.0f,
       val isRepeatable: Boolean = false
   )
   ```

   New code:
   ```kotlin
   data class KeyData(
       val primaryLabel: String,
       val primaryCode: Int,
       val longPressLabel: String? = null,
       val longPressCode: Int? = null,
       // WHY: SwiftKey-style multi-char long-press popups (e.g. a → à á â ä)
       // need to present multiple candidates. When non-null, KeyView
       // opens a popup offering all of these as selectable options;
       // longPressCode remains the "primary" (first) long-press for
       // backward-compat and quick-flick dispatch.
       // FROM SPEC: §4.2 "Same long-press popup content on every key"
       // (scope limited to FULL + COMPACT_DEV + symbols per discrepancy #2).
       // NOTE: Defaulted to null so every existing KeyData(...) site in
       // the codebase remains source-compatible.
       val longPressCodes: List<Int>? = null,
       val type: KeyType = KeyType.LETTER,
       val weight: Float = 1.0f,
       val isRepeatable: Boolean = false
   )
   ```

2. Verify no existing `KeyData(...)` constructor call site in the codebase breaks. All existing sites use named-argument or positional construction that does not reference the new field; since the field is defaulted and inserted AFTER the existing optional fields (`longPressCode`), positional calls that stop at `longPressCode` remain valid.

   Run `Grep -pattern="KeyData\("` across `app/src/main/` to confirm zero-argument-count issues. All positional calls that pass `type =` by name are unaffected.

**Verification:** intentionally batched — see end-of-phase verification in sub-phase 5.4.

### Sub-phase 5.2: Fill long-press data for QwertyLayout FULL + COMPACT_DEV

**Files:** `app/src/main/java/dev/devkey/keyboard/ui/keyboard/QwertyLayout.kt` (modify)

**Agent:** `compose-ui-agent`

**Steps:**

1. Read the current `QwertyLayout.kt` in full to identify all `KeyData(...)` constructions missing long-press in:
   - `buildFullLayout()` — verify the qwertyuiop row at lines 72–81 stays as-is (already has long-press). Add long-press to the home row `asdfghjkl` and the zxcvbnm row. Add long-press to action-row keys where SwiftKey provides one (e.g. period → `,`, `;`, `:`, `!`, `?`).
   - `buildCompactDevLayout()` — same treatment (letter row long-press are the hacker-friendly symbols).
   - `buildCompactLayout()` — **SKIP letter keys per discrepancy #2.** Non-letter keys (space, shift, backspace, enter, action row) may still get long-press if SwiftKey has it — verify against captured Phase 2 references if available.

2. For each row that needs long-press, update the row's `listOf(KeyData(...), ...)` block. Example template for the home row `asdfghjkl` following the SwiftKey convention for the full layout:
   ```kotlin
   keys = listOf(
       KeyData("a", 'a'.code, longPressLabel = "1", longPressCode = '1'.code),
       KeyData("s", 's'.code, longPressLabel = "2", longPressCode = '2'.code),
       KeyData("d", 'd'.code, longPressLabel = "3", longPressCode = '3'.code),
       KeyData("f", 'f'.code, longPressLabel = "4", longPressCode = '4'.code),
       KeyData("g", 'g'.code, longPressLabel = "5", longPressCode = '5'.code),
       KeyData("h", 'h'.code, longPressLabel = "6", longPressCode = '6'.code),
       KeyData("j", 'j'.code, longPressLabel = "7", longPressCode = '7'.code),
       KeyData("k", 'k'.code, longPressLabel = "8", longPressCode = '8'.code),
       KeyData("l", 'l'.code, longPressLabel = "9", longPressCode = '9'.code)
   )
   ```

   // NOTE: The digits-as-long-press convention is what HK / standard
   // Android keyboard use. If SwiftKey reference screenshots (captured
   // in Phase 2) show different long-press content, update these values
   // to match the reference. The reference ALWAYS wins over this
   // template — this template is a starting point, not a final spec.
   //
   // IMPORTANT: If the reference shows multi-char popups (e.g.
   // a → à á â ã ä å æ), use the new `longPressCodes` field added in
   // 5.1. Keep `longPressCode` set to the FIRST entry for quick-flick:
   //   KeyData("a", 'a'.code,
   //       longPressLabel = "à",
   //       longPressCode = 'à'.code,
   //       longPressCodes = listOf('à'.code, 'á'.code, 'â'.code, 'ã'.code, 'ä'.code, 'å'.code, 'æ'.code)
   //   )

3. Update the zxcvbnm row (full layout) with SwiftKey-matched long-press content — same template pattern as step 2. Consult the captured Phase 2 references.

4. Update `buildCompactDevLayout()` letter rows with hacker-friendly long-press content (e.g. `1 → !`, `2 → @`, etc., mirroring a US keyboard's shift characters). This is the COMPACT_DEV philosophy: same density as COMPACT but richer per-key access for developers.

5. Verify and populate action-row keys per spec §4.2: period long-press (`. → , ; : ! ?`), comma/emoji swap behavior (if SwiftKey swaps comma ↔ emoji button on certain IME flags, match the behavior). Read `QwertyLayout.kt` action-row construction and the existing `swapPunctuationAndSpace` / `reswapPeriodAndSpace` logic in `LatinIME.kt:1603-1615` for context.

6. In `buildCompactLayout()`, leave letter keys untouched (PENDING the Phase 5 escalation gate above — if option (A) is chosen, populate letter-row long-press here matching the FULL pattern). Add long-press ONLY to utility / action keys if the SwiftKey reference shows it. Re-confirm discrepancy #2 scope with a comment at the top of `buildCompactLayout`:
   ```kotlin
   private fun buildCompactLayout(): KeyboardLayoutData {
       // SCOPE (Plan Phase 5): Letter keys in COMPACT mode intentionally
       // omit long-press per KeyData.kt:22 design comment and the plan's
       // Ground-Truth Discrepancy #2. Spec §4.2 "same long-press on every
       // key" is scoped to FULL / COMPACT_DEV / SYMBOLS in v1.0.
       // ...
   }
   ```

**Verification:** intentionally batched — see end-of-phase verification in sub-phase 5.4.

### Sub-phase 5.3: Fill long-press data for SymbolsLayout

**Files:** `app/src/main/java/dev/devkey/keyboard/ui/keyboard/SymbolsLayout.kt` (modify)

**Agent:** `compose-ui-agent`

**Steps:**

1. Read `SymbolsLayout.kt` in full and identify every `KeyData(...)` construction.

2. For each symbol key, add long-press data based on SwiftKey reference. Common SwiftKey symbol-page long-press patterns:
   - `1 → ¹`, `2 → ²`, `3 → ³`, etc. (superscripts)
   - `$ → ¢ € £ ¥`
   - `. → … , ; : ! ?`
   - `- → _ — ~`
   - `( → [ { < ⟨`
   - `) → ] } > ⟩`
   - `" → « » „ ‟ "`

   Use `longPressCodes` (list form) where the reference shows multiple options.

3. Leave the bottom row (shift, delete, enter, space) untouched unless the reference shows symbol-page-specific long-press content.

**Verification:** `./gradlew assembleDebug`

### Sub-phase 5.4: KeyView popup rendering for `longPressCodes` list

**Files:** `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyView.kt` (modify)

**Agent:** `compose-ui-agent`

**Steps:**

> **No escape hatch.** Spec §4.2 "same long-press popup content on every key" is load-bearing. Multi-char popup UX is required. If the implementing agent finds this sub-phase is too large, STOP and escalate — the resolution is a spec amendment, not a silent scope reduction.

1. Read `KeyView.kt` in full AND search the codebase for any existing Compose `Popup` usage in the keyboard UI before starting, so the implementation reuses existing popup conventions if any. Pay particular attention to lines 160–230 (the long-press detection + dispatch block) to understand the current gesture handling.

2. Locate the `if (key.longPressCode != null)` branch at `KeyView.kt:213-215`. Extend it to handle `longPressCodes` (list form) BEFORE the single-code branch. When `longPressCodes` has ≥2 entries, open a Compose `Popup` offering all options; the user drags or taps to pick one. When `longPressCodes` is null or has ≤1 entry, fall back to the existing single-code dispatch.

   Target replacement sketch (exact form depends on KeyView current gesture pattern — read the file first):
   ```kotlin
   onLongPress = {
       KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
       val codes = key.longPressCodes
       if (codes != null && codes.size >= 2) {
           // WHY: multi-char popup matches SwiftKey UX for accented
           // letters and symbol-variant keys (spec §4.2).
           // Shows a row of selectable options; the user drags to the
           // desired one or releases on the default (first entry).
           showMultiCharPopup = true
           popupCodes = codes
       } else if (key.longPressCode != null) {
           onKeyAction(key.longPressCode)
       } else if (key.isRepeatable) {
           // ... existing repeat logic ...
       }
   }
   ```

   // IMPORTANT: The exact Compose `Popup` construction, popup state hoist,
   // and drag-to-select gesture must follow the existing `KeyView`
   // gesture/state conventions. This step is the LARGEST single code
   // change in Phase 1. Full multi-char popup UX is REQUIRED — no escape
   // hatch. If the sub-phase grows unmanageable, STOP and escalate for a
   // spec amendment. Do not silently reduce scope to first-entry-only.

3. Implement the full multi-char Compose Popup as described above. Multi-char popup UX is required by spec §4.2.

**Verification:** `./gradlew assembleDebug`

---

## Phase 6: Layout Fidelity Pass (spec 1.3, §4.1)

Retune `DevKeyTheme` token values (colors, spacing, row weights, typography) against SwiftKey reference screenshots captured in Phase 2. Pure visual tweaks — no functional code change. All values live in the single `DevKeyTheme` object; per the `theme-token-usage` pattern, raw `Color(0xFF...)` or hardcoded dp values MUST NOT be added to call sites.

**Prerequisite**: Phase 2 reference screenshots must exist in `.claude/test-flows/swiftkey-reference/`. If they do not, this phase is skipped and the implementing agent files a GH issue noting the block.

### Sub-phase 6.1: DevKeyTheme token retuning

**Files:** `app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt` (modify)

**Agent:** `compose-ui-agent`

**Steps:**

1. Verify `.claude/test-flows/swiftkey-reference/` contains at least the four base-mode captures (`qwerty-dark.png`, `compact-dark.png`, `full-dark.png`, `symbols-dark.png`). If any are missing, STOP and escalate — the visual diff target doesn't exist.

2. Read `DevKeyTheme.kt` in full. Identify each token the reference asks to change. Categories to audit:
   - Surface tokens (`kbBg`, `keyBg`, `keyBgSpecial`, `keyPressed`) — match SwiftKey background palette
   - Text tokens (`keyText`, `keyTextSpecial`, `keyHint`) — match SwiftKey text palette
   - Spacing (`keyGap`, `keyRadius`, `kbPadH`, `kbPadV`) — match SwiftKey gaps
   - Row weights (`rowNumberWeight`, `rowLetterWeight`, `rowSpaceWeight`, `rowUtilityWeight`, `rowCompactWeight`) — match row proportions
   - Typography (`fontKey`, `fontKeySpecial`, `fontKeyHint`, `fontKeyUtility`) — match SwiftKey font sizes
   - Modifier colors (`modBgShift`, `modTextShift`, `modBgAction`, `modTextAction`, etc.) — match SwiftKey modifier highlights

3. Update token values in-place. Example pattern for a color tweak:
   ```kotlin
   // BEFORE
   val keyBg = Color(0xFF2C2C31)
   // AFTER (if SwiftKey reference shows a slightly warmer gray)
   val keyBg = Color(0xFF2A2B30)
   ```

   Every changed token gets an inline comment referencing the capture:
   ```kotlin
   // TUNED to match .claude/test-flows/swiftkey-reference/qwerty-dark.png
   // SwiftKey uses a slightly warmer neutral here; delta ~2 RGB points.
   val keyBg = Color(0xFF2A2B30)
   ```

4. Do NOT inline any raw `Color(0xFF...)` into KeyView, DevKeyKeyboard, QwertyLayout, or any other file. All tuning lives in `DevKeyTheme.kt`.

5. **Both themes required.** Spec §4.1 requires light + dark palette. §3 Non-Goals does NOT list light-theme deferral. If the implementing agent finds the light-theme split is too large, STOP and escalate — spec amendment required, not a silent scope reduction. The implementation must add a parallel `lightPalette` / `darkPalette` split with a runtime switch (driven by the existing DevKey theming mechanism if present, or a new MaterialYou integration if not). The scaffolding for light-theme tokens uses the same `Color(0xFF...)` + centralization-in-DevKeyTheme convention.

6. Verify `DevKeyTheme.kt` line count stays under 400 (currently 172). Adding ~20 retuned tokens adds ~20 lines worst case — still comfortably under.

**Verification:** `./gradlew assembleDebug`

---

## Phase 7: Voice End-to-End Verification (spec 1.6, §5.3)

Zero-to-minimal code change. This phase exercises the `VoiceInputEngine` integration that is already wired in `DevKeyKeyboard.kt:81-249` against the assets dropped in Phase 1. Optionally routes `VoiceInputEngine` state transitions through `DevKeyLogger.voice(...)` for observable state assertions, which Phase 2 (regression infrastructure) consumes.

### Sub-phase 7.1: Add DevKeyLogger.voice() instrumentation to VoiceInputEngine state transitions

**Files:** `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt` (modify)

**Agent:** `ime-core-agent`

> **PRIVACY POLICY (load-bearing):** `DevKeyLogger.voice(...)` payloads routed through this phase MUST NOT contain transcript text, audio buffers, raw audio bytes, or any `InputConnection.getTextBeforeCursor`/`getTextAfterCursor` content. `DevKeyLogger.sendToServer` has no `BuildConfig.DEBUG` gate — the HTTP forwarding pipe activates whenever `enableServer(url)` is called, which means ANY log site here is treated as release-path logging from a privacy-threat-model standpoint. Log events carry state names, source markers, durations, and numeric metadata only.

**Steps:**

1. Read `VoiceInputEngine.kt` in full to locate every `_state.value = ...` assignment and every `Log.i(TAG, ...)` / `Log.w(TAG, ..., e)` call.

2. Add `import dev.devkey.keyboard.debug.DevKeyLogger` to the import block.

3. Immediately after each `_state.value = VoiceState.<NAME>` assignment, add a `DevKeyLogger.voice(...)` call mirroring the transition. Example:
   ```kotlin
   _state.value = VoiceState.LISTENING
   DevKeyLogger.voice(
       "state_transition",
       mapOf("state" to "LISTENING", "source" to "startListening")
   )
   ```

   // WHY: Phase 2 of the umbrella spec (regression infrastructure) needs
   // deterministic state assertions for voice test flows. Routing
   // transitions through DevKeyLogger.voice means Phase 2's HTTP debug
   // server sees them as structured events, replacing sleep-based
   // synchronization. This is preparation, not a Phase-2-forward pull:
   // no test-harness wiring in this phase, just instrumentation.
   //
   // NOTE: Existing Log.i/Log.w calls stay as-is. DevKeyLogger.voice is
   // additive.

4. Add `DevKeyLogger.voice("error", ...)` alongside the `Log.w(TAG, "Whisper model not available — voice input will be limited", e)` call at `VoiceInputEngine.kt:113-117` so model-missing events are observable in the test harness.

5. Do not change the state machine, audio capture, or inference paths. Only add the logger calls.

**Verification:** intentionally batched — see end-of-phase verification in sub-phase 7.2.

### Sub-phase 7.2: Manual voice E2E verification checklist

**Files:** `.claude/test-flows/phase1-voice-verification.md` (create)

**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Create `.claude/test-flows/phase1-voice-verification.md` with the checklist from spec §5.3, converted to an actionable manual verification guide.
   ```markdown
   # Phase 1.6 — Voice E2E Verification Checklist

   **Spec**: `.claude/specs/2026-04-08-pre-release-vision-spec.md` §5.3
   **Plan**: `.claude/plans/2026-04-08-pre-release-phase1.md` Phase 7

   ## Preconditions

   - [ ] `app/src/main/assets/whisper-tiny.en.tflite` present and SHA256 recorded in `MODEL_PROVENANCE.md`
   - [ ] `app/src/main/assets/filters_vocab_en.bin` present and SHA256 recorded
   - [ ] `./gradlew installDebug` succeeds
   - [ ] IME is selected and active on the target device

   ## Checklist (spec §5.3 items)

   - [ ] Voice button visible when `shouldShowVoiceButton(attribute)` returns true (normal text field; not password, not URL field)
   - [ ] Tap voice button → keyboard enters `KeyboardMode.Voice` → `VoiceState` transitions IDLE → LISTENING (check logcat for `DevKey/Voice state_transition LISTENING`)
   - [ ] Audio captured for up to N seconds or until silence detected
   - [ ] Whisper interpreter produces a transcription — check logcat for `DevKey/Voice processing_complete` with `{ result_length: N, duration_ms: M }` only. Transcript content MUST NOT appear in the log payload.
   - [ ] Voice button is NOT visible on `TYPE_TEXT_VARIATION_PASSWORD` / `VISIBLE_PASSWORD` / `WEB_PASSWORD` fields. If it IS visible, file a defect (`shouldShowVoiceButton` at LatinIME.kt:817 currently returns true unconditionally — pre-existing defect, not introduced by this plan).
   - [ ] Transcribed text is committed via `InputConnection.commitText` (visible in the target EditText)
   - [ ] State machine returns to `IDLE` after commit
   - [ ] State machine returns to `IDLE` after error (disconnect mic, tap voice, observe)
   - [ ] First-time tap on voice button requests `RECORD_AUDIO` permission via `PermissionActivity`
   - [ ] Permission denial → graceful handling (toast, button disabled, no crash)
   - [ ] Model-missing path: temporarily rename `whisper-tiny.en.tflite`, reinstall, verify `VoiceInputEngine.initialize()` catches the exception, `modelLoaded = false` logged, voice button surfaces the limited state (or is hidden depending on `shouldShowVoiceButton`)
   - [ ] After verification, restore asset file and reinstall

   ## Exit criteria

   All checkboxes must be ticked for Phase 7 to be considered complete. Failures are filed as defects per `.claude/docs/defects.md` conventions.
   ```

2. This file is for the human operator. `/implement` should NOT attempt to execute manual checks — it just creates the file and marks sub-phase 7.2 as complete.

**Verification:** `./gradlew assembleDebug`

---

## Phase 8: Existing Feature Regression Pass (spec 1.7, §2.1)

Verify clipboard panel, macro system, command mode, and plugin system still work after the Phase 3 (plugin gate) and Phase 4 (next-word) backend changes. No new code — manual smoke + existing `/test` tier runs.

### Sub-phase 8.1: Regression smoke checklist

**Files:** `.claude/test-flows/phase1-regression-smoke.md` (create)

**Agent:** `general-purpose` (sonnet)

**Steps:**

1. Create `.claude/test-flows/phase1-regression-smoke.md` enumerating the existing surfaces that must be smoke-tested after Phase 7 ships.
   ```markdown
   # Phase 1.7 — Existing Feature Regression Smoke

   **Spec**: `.claude/specs/2026-04-08-pre-release-vision-spec.md` §2.1
   **Plan**: `.claude/plans/2026-04-08-pre-release-phase1.md` Phase 8
   **When to run**: AFTER Phase 1-7 of this plan have shipped AND before
   the Phase-2 umbrella spec (regression infrastructure) begins.

   ## Surfaces in scope

   ### Clipboard panel (ClipboardPanel.kt)
   - [ ] Open panel via toolbar
   - [ ] Copy external text → entry appears
   - [ ] Tap entry → inserted at cursor
   - [ ] Pin / unpin / delete entries
   - [ ] Panel closes cleanly on backspace / back gesture

   ### Macro system (MacroManagerScreen.kt)
   - [ ] Record a macro from settings screen
   - [ ] Replay macro via long-press key
   - [ ] Edit / delete macro
   - [ ] Macro survives IME restart

   ### Command mode (CommandAppManagerScreen.kt)
   - [ ] Auto-detects terminal apps on install
   - [ ] Manual toggle in settings
   - [ ] Command palette opens / closes
   - [ ] Common commands execute

   ### Plugin system (PluginManager.kt) — post Phase 3 gate
   - [ ] Release build: `logcat` shows `"Plugin dictionaries disabled in release build (GH #4)"` at onCreate
   - [ ] Release build: no foreign-package resources are loaded (`logcat` has no `DevKey/PluginManager` load-success messages)
   - [ ] Debug build: plugin loading still works — any previously-installed dictionary plugin is discoverable
   - [ ] Both builds: `Suggest.getDictionary` null-safe path still returns valid main dict

   ### Existing /test tiers (run via /test skill)
   - [ ] FULL tier green
   - [ ] COMPACT tier green
   - [ ] COMPACT_DEV tier green
   - [ ] No new regressions vs. last green run logged in `.claude/test-results/`

   ## Exit criteria

   All checkboxes ticked AND no new defects filed. Failures → file a
   `defect` issue per `.claude/docs/defects.md`.
   ```

2. This file is for the human operator. `/implement` creates the file and marks sub-phase 8.1 complete.

**Verification:** `./gradlew assembleDebug && ./gradlew lint`

// WHY lint on the final phase: clean lint is a v1.0 quality gate per
// spec §2.4. Running lint now catches any noise introduced by Phase 1
// before Phase 2 (regression infrastructure) begins.
