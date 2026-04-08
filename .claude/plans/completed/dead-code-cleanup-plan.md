**Status:** Phases 1-5 IMPLEMENTED AND COMMITTED (73e211b), Phase 6 DEFERRED

# Dead Code Cleanup & Kotlin Quality Plan

**Created:** 2026-03-05
**Status:** Ready for implementation
**Agents used:** Plan drafter (Opus), Verification auditor (Opus), Plan reviewer (Opus), Kotlin quality auditor via codemunch (Opus)

---

## Overview

Post Java-to-Kotlin migration cleanup. Changes organized into 6 phases by risk level, each independently testable and producing a buildable app.

**Key constraint:** The Java file `org/pocketworkstation/pckeyboard/BinaryDictionary.java` is the JNI bridge (C++ RegisterNatives binds to this package path). It MUST stay.

---

## Phase 1: Zero-Risk Dead Code Removal

**Risk: None.** Zero callers confirmed by 3 independent searches.

### 1A. Remove dead methods from `LatinIMEUtil.kt`
- **Lines 25-31:** `cancelTask()` -- No-op stub, comment says "AsyncTasks replaced with coroutines"

### 1B. Remove dead methods from `EditingUtil.kt`
- **Lines 34-55:** `appendText()` -- zero callers
- **Lines 75-87:** `deleteWordAtCursor()` -- zero callers
- **Line 219:** `underlineWord()` -- zero callers *(added per reviewer)*
- **Gotcha:** Keep `getCursorPosition()` and `getWordRangeAtCursor()` -- they're used by `getWordAtCursorOrSelection()` which IS called from LatinIME.kt

### 1C. Remove 14 dead deprecated theme aliases from `DevKeyTheme.kt`
All have zero callers outside the theme file:
- Lines 116-117: `escKeyFill`
- Lines 118-119: `tabKeyFill`
- Lines 120-121: `ctrlKeyFill`
- Lines 122-123: `altKeyFill`
- Lines 124-125: `arrowKeyFill`
- Lines 126-127: `enterKeyFill`
- Lines 128-129: `backspaceKeyFill`
- Lines 130-131: `spaceKeyFill`
- Lines 132-133: `spaceKeyText`
- Lines 136-137: `ctrlActiveKeyFill`
- Lines 138-139: `altActiveKeyFill`
- Lines 140-141: `shiftActiveKeyFill`
- Lines 106-107: `keyCornerRadius` *(confirmed zero callers)*
- Lines 112-113: `keyHintSize` *(confirmed zero callers)*

Also remove the section comments ("Legacy special key fills..." and "Legacy modifier active fills").

### 1D. Remove `RingCharBuffer` + its caller
**Rationale:** `init()` has zero callers, so `mEnabled` is always `false`, making `push()` always a no-op. No read methods (`pop`, `getLastChar`, `getLastString`, `getPreviousX`, `getPreviousY`) are ever called.

- **`LatinIMEUtil.kt` lines 72-169:** Delete entire `RingCharBuffer` class
- **`LatinIME.kt` line 55:** Remove import `import dev.devkey.keyboard.LatinIMEUtil.RingCharBuffer`
- **`LatinIME.kt` line 1374:** Remove call `RingCharBuffer.getInstance().push(primaryCode.toChar(), x, y)`

### 1E. Remove additional dead code found by Kotlin audit
- **`LatinIME.kt:2691`** -- `newArrayList()` utility method, zero callers. Dead Java-era helper.
- **`LatinIME.kt:1103-1105`** -- `postUpdateShiftKeyState()` empty stub + its 2 call sites (lines 896, 1440)
- **`TextEntryState.kt:162`** -- `selectedForCorrection()` zero callers
- **`TextEntryState.kt:243`** -- `keyPressedAt()` zero callers (also gated by `LOGGING=false`)
- **`TextEntryState.kt:33-35, 76-82, 87-110`** -- Dead `LOGGING`/`DBG` infrastructure: `sKeyLocationFile`, `sUserActionFile`, file I/O in `endSession()`, all gated by hardcoded `false` constants
- **`LatinKeyboard.kt:41`** -- `DEBUG_PREFERRED_LETTER = false` and guarded code at lines 691, 717
- **`Suggest.kt:501`** -- `TAG = "DevKey"` defined but never used for logging
- **`LatinIME.kt:131`** -- `mBigramSuggestionEnabled = false` hardcoded, dead feature flag

### Phase 1 Verification
1. `./gradlew assembleDebug`
2. `./gradlew test`
3. Install on device, verify typing/suggestions/keyboard switching
4. Grep for removed symbol names to confirm no breakage

---

## Phase 2: Deprecated Alias Migration

**Risk: Low.** Pure renames -- old aliases point to identical values (except `keyLabelSize`).

### 2A. Migrate `keyboardBackground` to `kbBg` (16 call sites, 11 files)

| File | Occurrences |
|------|-------------|
| `ui/welcome/DevKeyWelcomeActivity.kt` | 2 |
| `ui/settings/SettingsSubScreens.kt` | 4 |
| `ui/settings/SettingsCategoryScreen.kt` | 2 |
| `ui/settings/DevKeySettingsActivity.kt` | 1 |
| `ui/settings/CommandAppManagerScreen.kt` | 1 |
| `ui/settings/MacroManagerScreen.kt` | 1 |
| `ui/toolbar/ToolbarRow.kt` | 1 |
| `ui/suggestion/SuggestionBar.kt` | 1 |
| `ui/macro/MacroRecordingBar.kt` | 1 |
| `ui/macro/MacroGridPanel.kt` | 1 |
| `ui/macro/MacroChipStrip.kt` | 1 |

After migration, remove lines 100-101 from `DevKeyTheme.kt`.

### 2B. Migrate `keyFill` to `keyBg` (5 call sites, 4 files)

| File | Occurrences |
|------|-------------|
| `ui/welcome/DevKeyWelcomeActivity.kt` | 2 |
| `ui/settings/SettingsCategoryScreen.kt` | 1 |
| `ui/settings/CommandAppManagerScreen.kt` | 1 |
| `ui/settings/MacroManagerScreen.kt` | 1 |

After migration, remove lines 103-104 from `DevKeyTheme.kt`.

### 2C. Migrate `keyLabelSize` (1 call site) -- REQUIRES DECISION

**VALUE MISMATCH:** `keyLabelSize = 18.sp` but `fontKey = 14.sp` (22% size reduction).

**File:** `VoiceInputPanel.kt:113` uses `keyLabelSize` for a mic emoji inside a 64dp circle.

**Decision:** Create a new token `val fontVoiceMic: TextUnit = 18.sp` in DevKeyTheme.kt typography section (~line 80), migrate VoiceInputPanel to use it, then remove `keyLabelSize` (lines 109-110).

### Phase 2 Verification
1. `./gradlew assembleDebug`
2. Visual check: Settings, Welcome, Macros, Toolbar, Suggestions, Voice panels
3. Before/after screenshot of voice input panel mic button for 2C

---

## Phase 3: Java Interop Annotation Cleanup

**Risk: Low-Medium.** No Java code calls Kotlin, confirmed by reviewer.

### 3A. KEEP: `CandidateView @JvmOverloads` -- DO NOT REMOVE
Inflated from `candidates.xml` via Android LayoutInflater reflection. Removing crashes the app.

### 3B. Remove `@JvmOverloads` from `EditingUtil.Range` (line 92)
Only instantiated from Kotlin. Also remove `@JvmField` from `Range` fields (lines 94, 96, 98) and `SelectedWord` fields (lines 158-160).

### 3C. Remove `@JvmStatic` annotations (20+ occurrences, 10 files)

| File | Lines |
|------|-------|
| `LatinIME.kt` | 2632, 2637, 2665, 2671, 2677, 2683, 2691 |
| `ComposeSequence.kt` | 105, 108, 175, 201, 234 |
| `DeadAccentSequence.kt` | 65, 74 |
| `ExpandableDictionary.kt` | 527 |
| `KeyboardSwitcher.kt` | 101, 104 |
| `Keyboard.kt` | 88 |
| `LatinKeyboard.kt` | 115 |
| `LanguageSwitcher.kt` | 198 |
| `PluginManager.kt` | 107, 176, 222, 230 |
| `ComposeKeyboardViewFactory.kt` | 44 |

### 3D. Remove `@JvmField` annotations (batch, EXCEPT Keyboard.Key)

| File | Lines | Notes |
|------|-------|-------|
| `AutoDictionary.kt` | 194, 197, 201 | |
| `BinaryDictionary.kt` | 313 | Safe -- Java bridge doesn't reference it |
| `ExpandableDictionary.kt` | 50-55, 59-60, 79-80, 82, 545 | |
| `InputLanguageSelection.kt` | 234, 238, 242 | |
| `LatinIME.kt` | 230, 2591-2592, 2618-2620 | |
| `UserBigramDictionary.kt` | 293 | |

### 3E. DEFERRED: `Keyboard.Key` @JvmField (~30 fields, lines 211-242)
These are hot-path fields accessed in tight render/touch loops. Removing `@JvmField` adds getter/setter overhead. **Benchmark before/after if attempted.**

### 3F. DEFERRED: `SettingsRepository` @JvmField (~25 fields, lines 32-74)
Accessed from performance-sensitive settings read paths. Defer alongside Keyboard.Key.

### Phase 3 Verification
1. `./gradlew assembleDebug && ./gradlew test`
2. Verify: keyboard appears, typing works, candidates appear (CandidateView XML inflation), language/mode switching works

---

## Phase 4: Legacy Pattern Refactoring

**Risk: Medium.** Behavioral changes requiring careful testing.

### 4A. Remove GCUtils + simplify OOM handling
**Current** (`LatinIME.kt:394-405`): Manual GC retry loop with `Thread.sleep(1s)`.
**Replace with:**
```kotlin
try {
    initSuggest(inputLanguage)
} catch (e: OutOfMemoryError) {
    Log.e(TAG, "OOM during dictionary init for $inputLanguage", e)
}
```
Then delete `GCUtils` class from `LatinIMEUtil.kt:33-70`.

**Note:** If Phase 1D already removed `RingCharBuffer`, `LatinIMEUtil` will be empty after this step. Delete the entire file and remove its imports.

### 4B. DEFERRED: TextEntryState modernization (28+ call sites, separate ticket)
### 4C. DEFERRED: Hungarian notation cleanup (219 occurrences, do opportunistically)
### 4D. DEFERRED: TODO/FIXME triage (15+ items, separate tickets)

### Phase 4 Verification
1. Full build + test suite
2. Simulate low-memory conditions, verify dictionary loading fails gracefully

---

## Phase 5: Kotlin Idiom Modernization

**Risk: Low.** Mechanical replacements, semantically equivalent.

### 5A. Replace Java API calls with Kotlin equivalents

| Pattern | Replacement | Files | Count |
|---------|-------------|-------|-------|
| `Character.isUpperCase()` | `Char.isUpperCase()` | 7 files | ~20 |
| `Character.toLowerCase()` | `Char.lowercaseChar()` | 7 files | ~20 |
| `TextUtils.isEmpty()` | `.isNullOrEmpty()` | 6 files | 12 |
| `TextUtils.equals()` | `==` | 2 files | 6 |
| `Arrays.fill()` | `.fill()` | 2 files | ~8 |
| `System.arraycopy()` | `.copyInto()` | 4 files | 5 |
| `Math.round()` | `.roundToInt()` | Suggest.kt | 1 |
| `Pattern.compile()` | `.toRegex()` | LatinIME.kt | 1 |
| `StringTokenizer` | `String.split()` | Keyboard.kt | 1 |

### 5B. Replace `!= null && !!` with safe calls
~14 occurrences across Suggest.kt, LatinIME.kt. Example:
```kotlin
// Before
if (mCandidateView != null && mCandidateView!!.dismissAddToDictionaryHint())
// After
if (mCandidateView?.dismissAddToDictionaryHint() == true)
```

### 5C. Standardize TAG naming to `"DevKey/<ClassName>"`
Update inconsistent TAGs:
- `KeyboardSwitcher.kt:31` -- `"PCKeyboardKbSw"` (old project name!)
- `BinaryDictionary.kt` -- `"BinaryDictionary"`
- `TextEntryState.kt` -- `"TextEntryState"`
- Others using just `"DevKey"` without class name

### Phase 5 Verification
1. `./gradlew assembleDebug && ./gradlew test`
2. Spot-check logcat output to verify TAG changes

---

## Phase 6: DRY Refactoring (Future)

**Risk: Medium.** Structural changes requiring careful testing.

### 6A. Extract shared dictionary cursor-loading pattern
`AutoDictionary.kt` and `UserBigramDictionary.kt` have near-identical `loadDictionaryAsync()` implementations. Extract shared `loadFromCursor()` helper into `ExpandableDictionary`.

### 6B. Extract shared pending-writes flush pattern
Both dictionary classes independently implement `mPendingWritesLock` + HashMap + swap-and-flush. Extract `PendingWriteBuffer<K,V>`.

### 6C. Extract `StringBuilder` pool helpers in `Suggest.kt`
The borrow-from-pool pattern appears 3+ times. Extract `borrowStringBuilder()` / `returnStringBuilder()`.

### 6D. Consolidate `isValidWord` chain in `Suggest.kt`
Replace nullable dictionary chain with `listOfNotNull(mMainDict, mUserDictionary, ...).any { it.isValidWord(word) }`.

### 6E. Extract `prepareInputBuffers()` in `BinaryDictionary.kt`
`getBigrams()` and `getWords()` share identical setup sequences.

### 6F. Consolidate `setModCtrl/Alt/Meta` in `LatinIME.kt`
Three near-identical methods (lines 1462-1483) could be a single `setModifier(type, enabled)`.

### 6G. Replace busy-wait `Thread.sleep()` loops with coroutines
- `ExpandableDictionary.kt:447`
- `UserBigramDictionary.kt:142`

---

## Git Strategy

One commit per phase for easy bisection:
1. `Remove dead code: unused methods, theme aliases, RingCharBuffer, dead stubs`
2. `Migrate deprecated theme aliases to canonical tokens`
3. `Remove unnecessary Java interop annotations`
4. `Replace GCUtils with simple OOM handling`
5. `Replace Java API calls with Kotlin idioms`
6. `Extract shared patterns (DRY refactoring)` *(future)*

---

## Appendix: Architectural Notes

### God Classes (track separately)
- **LatinIME.kt** -- 2700 lines, 150 symbols, 82 member variables. Handles 15+ concerns. Progressive extraction recommended: `TextInputController`, `FeedbackManager`, `SuggestionController`.
- **Keyboard.kt** -- 58 symbols. `Key` inner class (~400 lines) mixes XML parsing with layout logic.
- **SettingsRepository.kt** -- 83 symbols. 15+ anonymous `object :` SAM callbacks for preference registration.

### Deprecated Android APIs
- `KeyboardSwitcher.kt:281` -- `Resources.updateConfiguration()` deprecated since API 25 (project targets API 26+). Replace with `createConfigurationContext()`.
- `CandidateView.kt` -- Legacy Canvas-based View. `SuggestionBar.kt` Compose replacement already exists.

### Performance-Sensitive Deferred Items
- `Keyboard.Key` @JvmField (30 hot-path fields) -- benchmark before removing
- `SettingsRepository` @JvmField (25 fields) -- defer alongside Key
- `Suggest.searchBigramSuggestion()` O(n^2) -- acknowledged TODO, replace with HashMap
