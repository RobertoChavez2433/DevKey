# Source Excerpts — Organized by Concern

Mirrors the eight Phase 1 work items from spec §6.

---

## 1.1 — Voice model sourcing

No source code changes. The only code touchpoint is the asset path read by:

**`VoiceInputEngine.initialize()` — VoiceInputEngine.kt:98**
```kotlin
val modelFile = assetManager.open("whisper-tiny.en.tflite")
```

The work for 1.1 is:
1. Create `app/src/main/assets/` directory (currently missing — verified via `ls`)
2. Source `whisper-tiny.en.tflite`
3. Source `filters_vocab_en.bin`
4. Commit both files
5. License audit + SHA256 capture

Also required to call `processor.loadResources()` (VoiceInputEngine.kt:111):

**`WhisperProcessor.loadResources()` — WhisperProcessor.kt:52**
```kotlin
fun loadResources(): Boolean
```

—

## 1.2 — SwiftKey reference capture

No source code changes. Pure test-artifact capture under `.claude/test-flows/swiftkey-reference/`. See tailor rule — reference screenshots are for internal comparison only, not redistribution.

Test-flow registry at `.claude/test-flows/registry.md` will need new entries.

—

## 1.3 — Layout fidelity pass

**Primary target**: `app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt` (172 lines)

All tunable values are Kotlin-object constants. See `patterns/theme-token-usage.md` for the full token list.

**Secondary targets** (only if a row composition changes):
- `QwertyLayout.kt` row-weight assignments
- `KeyView.kt` (should not be touched for color tuning — consumes DevKeyTheme)

—

## 1.4 — Long-press popup completeness

**Data class** (already has the fields): `KeyData.kt:43-51`

**Builders** (fill in missing long-press fields):
- `QwertyLayout.kt` — `buildFullLayout` (qwertyuiop row already done at lines 72-81; check home row `buildHomeRow` at line 293, space row `buildSpaceRow` at line 311, and full-mode utility rows for gaps)
- `QwertyLayout.kt` — `buildCompactLayout` (line 178) — currently intentionally omits long-press per KeyData.kt:27 doc comment
- `QwertyLayout.kt` — `buildCompactDevLayout` (line 241)
- `SymbolsLayout.kt` — `buildLayout` (line 16, 5KB file)

**Dispatch** (already wired): `KeyView.kt:171, 213-215` — see `patterns/compose-keyboard-layout.md`

**Scope flag**: spec §4.2 requires "Same long-press popup content on every key" — conflicts with current COMPACT design. Plan author must choose behavior.

**Potential data-class extension**: `KeyData.longPressCodes: List<Int>?` for multi-char popups. Backward-compatible add.

—

## 1.5 — Predictive next-word wiring

**Gap**: `LatinIME.setNextSuggestions` (line 1895-1897) is a stub that only shows punctuation.

**Existing building blocks** (no changes needed):
- `Dictionary.getBigrams(composer, previousWord, callback, ...)` — abstract in `Dictionary.kt:74`
- `ExpandableDictionary.getBigrams` impl at `ExpandableDictionary.kt:427`
- `UserBigramDictionary` extends `ExpandableDictionary` (`UserBigramDictionary.kt:37`)
- `BinaryDictionary.getBigrams` native-backed impl
- `Suggest.addWord` (WordCallback) accumulates into `mBigramSuggestions`

**New work**:
1. New `Suggest.getNextWordSuggestions(prevWord: CharSequence?): List<CharSequence>` — runs bigram path with empty composer
2. Modify `LatinIME.setNextSuggestions()` to call the new method with the last committed word, fall back to punctuation list if no bigrams
3. New helper `LatinIME.getLastCommittedWord(): CharSequence?` — reads InputConnection text before cursor, extracts last word via regex
4. Optional: extract `LatinIME.mEmptyComposer` shared field instead of constructing fresh

See `patterns/bigram-suggest-path.md` for the detailed suggested shape.

—

## 1.6 — Voice end-to-end verification

**Zero new code** unless state-transition instrumentation is added.

**Existing wiring**: `DevKeyKeyboard.kt:81-249` (see `patterns/voice-engine-integration.md`)

**Optional instrumentation** for test observability: route `VoiceInputEngine` state changes through `DevKeyLogger.voice(...)` (`DevKeyLogger.kt:63`). Current code uses plain `android.util.Log.*`. See `patterns/devkey-logger-http-forwarding.md`.

**Manifest check**: `AndroidManifest.xml:6-7` — RECORD_AUDIO permission + optional mic feature already declared.

**Permission flow**: `ui/voice/PermissionActivity.kt` with translucent theme (`AndroidManifest.xml:63-65`).

—

## 1.7 — Existing feature regression pass

No source changes. Per spec §2.1: clipboard panel, macro system, command mode, plugin system must all still work.

Touched files are those in Phase 1.1/1.3/1.4/1.5/1.8. Risk surface:
- Layout fidelity (1.3) could regress key hit-boxes → clipboard / macro UI relies on KeyboardActionBridge onText → safe
- Long-press data fills (1.4) could break existing long-press behavior → covered by KeyView unit tests in `app/src/androidTest/java/dev/devkey/keyboard/ui/keyboard/`
- Plugin gate (1.8) removes plugin load in release → explicitly covered by spec §2.4

—

## 1.8 — GH #4 plugin security resolution

**Target file**: `app/src/main/java/dev/devkey/keyboard/PluginManager.kt` (entire file ~230 lines)

**Change**: Early-return inside `getPluginDictionaries(context)` and `getDictionary(context, lang)` when `!isDebugBuild(context)`. Plus new private helper `arePluginsEnabled(context)` or reuse of `KeyMapGenerator.isDebugBuild`.

**Consumers** (unchanged):
- `LatinIME.kt:379, 380, 387, 579` — init / register / unregister
- `Suggest.kt:70` — `PluginManager.getDictionary(...)` (already handles null return)
- `InputLanguageSelection.kt:111` — same pattern

**Exemplar** for the debug check: `KeyMapGenerator.kt:38-42` uses `ApplicationInfo.FLAG_DEBUGGABLE`. See `patterns/plugin-loading-security.md` for the full suggested shape.

**Alternative**: `BuildConfig.DEBUG` requires `buildFeatures.buildConfig = true` in gradle — check `app/build.gradle.kts` first.
