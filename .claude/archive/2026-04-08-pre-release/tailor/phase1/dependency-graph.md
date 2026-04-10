# Dependency Graph — Phase 1

**⚠️ METHODOLOGY NOTE**: jcodemunch's Kotlin import resolver returned empty edges for every file in this repo. Steps 3–5 / 7 of the prescribed sequence produced no signal. This document is reconstructed from `search_text` call-site analysis + direct reads.

---

## Call sites — `setNextSuggestions` (LatinIME.kt)

Declared at `app/src/main/java/dev/devkey/keyboard/LatinIME.kt:1895-1897`:

```kotlin
private fun setNextSuggestions() {
    setSuggestions(mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
}
```

**All 8 call sites are in LatinIME.kt**:
- Line 905 — inside onUpdateSelection-like block (guards punctuation list not already shown)
- Line 960 — `setCandidatesViewShownInternal` when view becomes visible
- Line 1687 — `updateSuggestions()` early-return when not predicting
- Line 1809 — `pickSuggestionManually` after committing accepted suggestion (not correcting branch)
- Line 1839 — `pickDefaultSuggestion` after committing auto-corrected word
- Line 1889 — `setOldSuggestions` non-predicting branch
- Line 1895 — declaration
- Line 2476 — `initSuggestPuncList` (after building punctuation list)

**Phase 1.5 target**: The call sites at 1809 and 1839 are the PRIMARY next-word hooks. After a word is committed/auto-corrected and space is typed, the user expects bigram suggestions for the next word — but currently the candidate strip just shows punctuation. Replacing `setNextSuggestions()` (or adding a new `showNextWordSuggestions(lastWord)` alongside it) at these two call sites is the minimal wiring.

---

## VoiceInputEngine — wiring

**Instantiation**: `DevKeyKeyboard.kt:81`
```kotlin
val voiceInputEngine = remember { VoiceInputEngine(context) }
```

**Lifecycle**: `DevKeyKeyboard.kt:84-86`
```kotlin
DisposableEffect(voiceInputEngine) {
    onDispose { voiceInputEngine.release() }
}
```

**State observation**: `DevKeyKeyboard.kt:129-130`
```kotlin
val voiceState by voiceInputEngine.state.collectAsState()
val voiceAmplitude by voiceInputEngine.amplitude.collectAsState()
```

**Start / cancel / stop**: `DevKeyKeyboard.kt:157, 161, 241, 249`
```kotlin
// Start
coroutineScope.launch { voiceInputEngine.startListening() }

// Stop → commit
val transcription = voiceInputEngine.stopListening()
if (transcription.isNotEmpty()) {
    bridge.onText(transcription)  // ← InputConnection path
}

// Cancel
voiceInputEngine.cancelListening()
```

**Inside VoiceInputEngine.initialize()** (`VoiceInputEngine.kt:93-119`):
```kotlin
val assetManager = context.assets
val modelFile = assetManager.open("whisper-tiny.en.tflite")  // ← missing asset
```
Graceful degradation: exception caught, `modelLoaded = false`, recording still proceeds but skips inference.

**Conclusion**: Voice wiring is complete. Phase 1.1 (asset sourcing) is the only blocker.

---

## PluginManager — load path

**Instantiation**: `LatinIME.kt:380` (`mPluginManager = PluginManager(this)`)

**Entry call**: `LatinIME.kt:379` (`PluginManager.getPluginDictionaries(applicationContext)`)

**Receiver registration**: `LatinIME.kt:387` — filters `android.intent.action.PACKAGE_ADDED` / `PACKAGE_REMOVED`

**Consumer**: `Suggest.kt:70` (`PluginManager.getDictionary(context, locale.language)`) and `InputLanguageSelection.kt:111`

**Load chain (unsigned)**:
1. `PluginManager.getPluginDictionaries(ctx)` → calls both `getSoftKeyboardDictionaries(pm)` and `getLegacyDictionaries(pm)`
2. `getSoftKeyboardDictionaries`: `pm.queryBroadcastReceivers(Intent("com.menny.android.anysoftkeyboard.DICTIONARY"), GET_META_DATA)` → for each result, `pm.getResourcesForApplication(appInfo)` → parse XML → load `DictPluginSpecSoftKeyboard`
3. `getLegacyDictionaries`: `pm.queryIntentActivities(Intent("org.pocketworkstation.DICT"), 0)` → same load flow → `DictPluginSpecLegacy`
4. At dictionary use time: `DictPluginSpecBase.getDict(ctx)` → `getStreams(res)` → `new BinaryDictionary(...)` using the raw streams from the foreign package

**No signature check, no package-pinning, no hash verification.**

---

## Long-press data flow

`QwertyLayout.buildFullLayout` / `buildCompactLayout` / `buildCompactDevLayout`
  → constructs `KeyData(primaryLabel, primaryCode, longPressLabel, longPressCode, type, ...)`
  → wrapped in `KeyRowData` → `KeyboardLayoutData`
  → consumed by `DevKeyKeyboard` → rendered by `KeyView`

Inside `KeyView.kt:171`:
```kotlin
.pointerInput(key.primaryCode, key.type, key.longPressCode, key.isRepeatable) {
```
At long-press detection (line 213–215):
```kotlin
KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
if (key.longPressCode != null) {
    onKeyAction(key.longPressCode)
}
```

`onKeyAction` flows into `KeyboardActionBridge.onKey(code, modifierState)` → `KeyboardActionListener.onKey(primaryCode, keyCodes, x, y)` → `LatinIME.onKey`.

**Long-press is wired end-to-end**. Phase 1.4 is pure data-fill in `QwertyLayout` + `SymbolsLayout`.

---

## DevKeyLogger.enableServer

`DevKeyLogger.kt:37-39`:
```kotlin
fun enableServer(url: String) {
    serverUrl = url
}
```

A simple setter. `sendToServer(category, message, data, hypothesisId)` checks `serverUrl != null` internally. No call sites in production code currently — the test harness must invoke it at test start.
