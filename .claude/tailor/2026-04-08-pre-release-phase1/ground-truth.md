# Ground Truth — Phase 1

All values verified against source files at git HEAD `2391b5a`.

---

## Voice (§5.2)

| Claim | Source of truth | Status |
|---|---|---|
| `app/src/main/assets/` dir missing | `ls app/src/main/assets/` → "No such file or directory" | ✅ CONFIRMED |
| `whisper-tiny.en.tflite` missing | Same ls | ✅ CONFIRMED |
| `filters_vocab_en.bin` missing | Same ls | ✅ CONFIRMED |
| Model path in code | `VoiceInputEngine.kt:98` → `assetManager.open("whisper-tiny.en.tflite")` | ✅ CONFIRMED |
| Graceful degradation path | `VoiceInputEngine.kt:113-117` catches exception, sets `modelLoaded = false` | ✅ CONFIRMED |
| TF Lite dep version | `gradle/libs.versions.toml:14` → `tflite = "2.16.1"` | ✅ |
| TF Lite support version | `gradle/libs.versions.toml:15` → `tflite-support = "0.4.4"` | ✅ |
| tflite wired in build | `app/build.gradle.kts:110-111` — `implementation(libs.tflite)` + `implementation(libs.tflite.support)` | ✅ |
| VoiceInputEngine is 307 lines | `VoiceInputEngine.kt` — verified under 400-line rule | ✅ (spec §5.1 claim) |
| RECORD_AUDIO permission | `AndroidManifest.xml:6` → `<uses-permission android:name="android.permission.RECORD_AUDIO" />` | ✅ |
| Mic hardware optional | `AndroidManifest.xml:7` → `<uses-feature android:name="android.hardware.microphone" android:required="false" />` | ✅ |
| `KEYCODE_VOICE` | `KeyData.kt:137` → `const val KEYCODE_VOICE = -102` | ✅ |

---

## Bigram / next-word prediction (§4.3)

| Claim | Source of truth | Status |
|---|---|---|
| `setNextSuggestions` is stub | `LatinIME.kt:1895-1897` — body is just `setSuggestions(mSuggestPuncList, ...)` | ✅ CONFIRMED |
| 8 call sites | Grep: lines 905, 960, 1687, 1809, 1839, 1889, 1895, 2476 in LatinIME.kt | ✅ |
| `Suggest.getSuggestions(prevWordForBigram)` has bigram path | `Suggest.kt:183-315` — calls `mUserBigramDictionary!!.getBigrams(...)`, `mContactsDictionary!!.getBigrams(...)`, `mMainDict.getBigrams(...)` | ✅ |
| **Gate**: requires `wordComposer.size() == 1` | `Suggest.kt:208-210` — `if (wordComposer.size() == 1 && (mCorrectionMode == CORRECTION_FULL_BIGRAM || mCorrectionMode == CORRECTION_BASIC))` | ⚠️ **IMPORTANT** — existing method cannot serve zero-char "next word after space" case directly. Plan must add new entry point OR relax gate. |
| `UserBigramDictionary` extends `ExpandableDictionary` | `UserBigramDictionary.kt:37` — `: ExpandableDictionary(context, dicTypeId)` | ✅ |
| `Dictionary.getBigrams` is abstract/open | `Dictionary.kt:74` — `open fun getBigrams(composer, previousWord, callback, nextLettersFrequencies)` | ✅ |
| `ExpandableDictionary.getBigrams` default impl | `ExpandableDictionary.kt:427` | ✅ |
| `CandidateView.setSuggestions` signature | `CandidateView.kt:303` — `fun setSuggestions(suggestions, completions, typedWordValid, haveMinimalSuggestion)` | ✅ |
| `handleSeparator` is where space-input runs | `LatinIME.kt:1570-1624` | ✅ |
| Space handling: `doubleSpace()` on space with prediction on | `LatinIME.kt:1616` — `else if (isPredictionOn() && primaryCode == ASCII_SPACE) { doubleSpace() }` | ✅ — but does NOT trigger next-word suggestions. Plan target. |
| `ASCII_SPACE` constant | `KeyData.kt:86` — `const val ASCII_SPACE = ' '.code` (== 32) | ✅ |

---

## Keyboard layout / long-press (§4.1, §4.2)

| Claim | Source of truth | Status |
|---|---|---|
| `KeyData.longPressCode` / `longPressLabel` exist | `KeyData.kt:46-47` | ✅ |
| `LayoutMode` enum | `KeyData.kt:26-30` → `COMPACT`, `COMPACT_DEV`, `FULL` | ✅ |
| Compact mode doc says "no long-press on letter keys" | `KeyData.kt:27` | ⚠️ **Spec §4.2 requires "Same long-press popup content on every key"**. For COMPACT mode this means ADDING long-press data the current layout intentionally omits. **This is a scope expansion vs current behavior** — plan must decide: follow SwiftKey (add long-press data) or keep compact-mode clean (add long-press only in COMPACT_DEV). |
| `KeyView` dispatches long-press | `KeyView.kt:213-215` — if `key.longPressCode != null`, `onKeyAction(key.longPressCode)` | ✅ |
| `KeyPressLogger.logLongPress` exists | `KeyPressLogger.kt:16` | ✅ |
| `QwertyLayout.buildFullLayout` populates long-press for qwertyuiop | `QwertyLayout.kt:72-98` — visible pattern of `longPressLabel = "!"`, `longPressCode = '!'.code`, etc. | ✅ |
| `SymbolsLayout.buildLayout` exists | `SymbolsLayout.kt:16` | ✅ (contents need fill-in analysis; 5KB file) |
| `DevKeyTheme.kt` line count | 172 lines | ✅ under 400-rule |
| `KeyData.kt` line count | 146 lines | ✅ under 400-rule |
| DevKeyTheme has ~47 named tokens | 20 surface/mod/text colors, 4 spacing, 5 row weights, 5 typography, 4 animation, 3 active mod, 3 ctrlmode, ~12 session3/4/5 tokens | ✅ |
| Raw colors centralized | All `Color(0xFF...)` live in `DevKeyTheme.kt`; `KeyView.kt` references `DevKeyTheme.keyText`, `DevKeyTheme.keyHint` etc. | ✅ (pattern is live; plan should keep new Phase 1.3 values in DevKeyTheme) |

---

## Plugin security (§2.4 GH #4)

| Claim | Source of truth | Status |
|---|---|---|
| `PluginManager` loads external packages | `PluginManager.kt:107-173` (`getSoftKeyboardDictionaries`) + `PluginManager.kt:175-218` (`getLegacyDictionaries`) | ✅ |
| Legacy intent | `PluginManager.kt` → `LEGACY_INTENT_DICT = "org.pocketworkstation.DICT"` | ✅ |
| Soft keyboard intent | `PluginManager.kt` → `SOFTKEYBOARD_INTENT_DICT = "com.menny.android.anysoftkeyboard.DICTIONARY"` | ✅ |
| **No signature verification** | Neither method calls `PackageManager.getPackageInfo(pkg, GET_SIGNATURES)` or checks signatures | ✅ CONFIRMED — GH #4 is real |
| **No allowlist** | `packageManager.queryBroadcastReceivers` / `queryIntentActivities` returns all matching installed apps unconditionally | ✅ CONFIRMED |
| Raw byte load | `getResourcesForApplication(appInfo)` + `res.getXml()` / `res.openRawResource(rawId)` → piped into `BinaryDictionary(streams)` native loader | ✅ |
| Debug-build gate exemplar | `KeyMapGenerator.kt:40` — `(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` | ✅ (reusable helper pattern) |
| Minimal fix per spec §2.4(b) | "gate plugin loading behind a debug-only flag for v1.0" → wrap `getPluginDictionaries` body in `if (isDebugBuild(context)) { ... } else { Log.i(TAG, "plugins disabled in release build") }` | ✅ (plan target) |

---

## Test infra integration (§2.2 — used in Phase 1 verification)

| Claim | Source of truth | Status |
|---|---|---|
| `DevKeyLogger.enableServer(url)` exists | `DevKeyLogger.kt:37-39` | ✅ |
| Simple setter | Body is `serverUrl = url` | ✅ |
| No call sites in production | Searched — only declared, not called anywhere. Test harness must invoke. | ✅ |
| `DevKeyLogger.Category` enum | `DevKeyLogger.kt:22` — `Category(val tag: String)` | ✅ |
| Voice category helper | `DevKeyLogger.voice(message, data)` exists at `DevKeyLogger.kt:63` — usable for Phase 1.6 voice state assertions | ✅ |
| `.claude/test-flows/calibration.json` | Not present (`test -f` → missing) | ⚠️ Not a blocker; tailor skill docs say optional |
| `.claude/test-flows/registry.md` | Present | ✅ |

---

## Build / manifest

| Claim | Source of truth | Status |
|---|---|---|
| Kotlin version | `libs.versions.toml:3` → `kotlin = "2.0.21"` | ✅ |
| Compose BOM | `libs.versions.toml:5` → `compose-bom = "2024.06.00"` | ✅ |
| Min API 26 claim | `build.gradle.kts` (not re-verified in this tailor) | ⚠️ unverified — verify in plan if relevant |
| LatinIME service declared | `AndroidManifest.xml:21-30` | ✅ |
| `dev.devkey.keyboard.SETTINGS` action | `AndroidManifest.xml:51` | ✅ |
| Voice permission activity | `AndroidManifest.xml:63-65` — `ui.voice.PermissionActivity` with translucent theme | ✅ |

---

## Discrepancies / Open Concerns

1. **Bigram gate `size() == 1`**: The existing `Suggest.getSuggestions` cannot be called verbatim with zero typed characters. Plan must address: extend Suggest, OR call `Dictionary.getBigrams()` directly from LatinIME. Neither is a blocker — both are straightforward — but the plan must pick one.

2. **COMPACT mode long-press scope**: KeyData.kt:27 documents COMPACT as "no long-press on letter keys" by design. Spec §4.2 wants SwiftKey parity "on every key". These conflict. Plan must choose: (a) follow SwiftKey (override the design doc comment), OR (b) keep COMPACT clean and only populate popups in COMPACT_DEV / FULL / SYMBOLS. Recommend (b) unless user explicitly says otherwise.

3. **No plugin signature fix exemplar**: Nothing in the codebase currently does signature verification. If the plan goes with signature verification instead of debug-gating (§2.4a), it's net-new code with no local pattern to follow. Debug gate (§2.4b) is strictly simpler and has the `KeyMapGenerator.isDebugBuild` exemplar.
