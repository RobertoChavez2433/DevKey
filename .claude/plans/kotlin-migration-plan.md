# Kotlin Migration Implementation Plan

**Design doc:** `docs/plans/2026-03-02-kotlin-migration-design.md`
**Status:** Ready for implementation (revised after 2-wave adversarial review)
**Approach:** Bottom-Up Incremental (9 phases: 0-7, with Phase 7 split into 4 sub-phases)
**Review:** 2-wave Opus adversarial review (42 issues found, all addressed below)

---

## Phase 0: Constant & Cross-Reference Migration

**Goal:** Move all keycode constants out of `LatinKeyboardView` into `KeyCodes` before any file deletions. Zero behavior change — pure refactor.

**Why this phase exists:** Wave 1 found that 5 surviving files (ComposeSequence.java, LatinIME.java, Keyboard.kt, LatinKeyboard.java, KeyboardSwitcher.kt) reference ~45 `LatinKeyboardView.KEYCODE_*` constants. Deleting `LatinKeyboardView` in Phase 1 without first migrating these references breaks compilation across multiple phases. (Issues #1, #2, #11, #12, #28)

### Step 0.1: Copy keycode constants to KeyCodes object
- Add all ~45 `LatinKeyboardView.KEYCODE_*` constants to the existing `KeyCodes` object in `KeyData.kt`
- Include: `KEYCODE_CTRL_LEFT`, `KEYCODE_ALT_LEFT`, `KEYCODE_META_LEFT`, `KEYCODE_FN`, `KEYCODE_COMPOSE`, `KEYCODE_OPTIONS`, `KEYCODE_NEXT_LANGUAGE`, `KEYCODE_PREV_LANGUAGE`, `KEYCODE_VOICE`, `KEYCODE_ESCAPE`, `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT/CENTER`, `KEYCODE_FKEY_F1`–`KEYCODE_FKEY_F12`, `KEYCODE_NUM_LOCK`, `KEYCODE_PAGE_UP/DOWN`, `KEYCODE_HOME`, `KEYCODE_END`, `KEYCODE_INSERT`, `KEYCODE_FORWARD_DEL`, `KEYCODE_SCROLL_LOCK`, `KEYCODE_BREAK`, `KEYCODE_SYSRQ`
- Also move `LatinIME.ASCII_ENTER`, `ASCII_SPACE`, `ASCII_PERIOD` into `KeyCodes` (Issue #11)
- Verify values match existing `KeyCodes` entries — no duplicates or conflicts
- **Test:** `./gradlew test && ./gradlew assembleDebug` — zero behavior change

### Step 0.2: Update all referencing files
- **LatinIME.java** (~83 occurrences): Update `onKey()` switch cases, `sendSpecialKey()`, `ESC_SEQUENCES`, `CTRL_SEQUENCES`, `ASCII_ENTER/SPACE/PERIOD` refs
- **ComposeSequence.java** (28 occurrences, lines 46-74): Update `UP`, `DOWN`, `LEFT`, `RIGHT`, etc. char aliases
- **DeadAccentSequence.java**: Inherits from ComposeSequence — verify no direct refs
- **Keyboard.kt** (3 occurrences, lines 921-923): Update `KEYCODE_CTRL_LEFT`, `KEYCODE_ALT_LEFT`, `KEYCODE_META_LEFT`
- **LatinKeyboard.java** (3 occurrences): Update `KEYCODE_F1`, `KEYCODE_VOICE`, `KEYCODE_OPTIONS`
- **KeyboardSwitcher.kt**: Update any direct `LatinKeyboardView.KEYCODE_*` refs
- **Test:** `./gradlew test && ./gradlew assembleDebug` — all refs now point to `KeyCodes`

### Step 0.3: Commit Phase 0
- Commit: "Phase 0: Migrate all keycode constants to KeyCodes object (pre-deletion refactor)"

---

## Phase 1: Dead Code Purge

**Goal:** Delete ~4,070 lines of legacy/superseded code. Extract OnKeyboardActionListener. Create KeyboardSwitcher compatibility shim.

### Step 1.1: Extract OnKeyboardActionListener
- Create `app/src/main/java/dev/devkey/keyboard/core/KeyboardActionListener.kt`
- Define Kotlin interface with: `onKey()`, `onPress()`, `onRelease()`, `onText()`, `onCancel()`, `swipeLeft/Right/Up/Down()`
- Provide default (no-op) implementations in the interface body — **do NOT use `@JvmDefault`** (removed in Kotlin 2.0; with jvmTarget 17, Kotlin default methods automatically compile to Java default interface methods) (Issue #5)
- Update `KeyboardActionBridge.kt` and `ComposeKeyboardViewFactory.kt` to use new interface
- Update `LatinIME.java` to implement new Kotlin interface instead of `LatinKeyboardBaseView.OnKeyboardActionListener`
- **Test:** `./gradlew test && ./gradlew assembleDebug`

### Step 1.2: Create KeyboardSwitcher compatibility shim
**Why:** After deleting `LatinKeyboardView`, `KeyboardSwitcher.mInputView` becomes null. But `setKeyboardMode()` early-returns if `mInputView` is null (line 276), breaking `isAlphabetMode()`. `setShiftState()` delegates to `mInputView?.setShiftState()`, becoming a no-op. `getShiftState()` (called by LatinIME for auto-capitalization) breaks. (Issues #3, #4, #16, #29, #33)

Before deleting LatinKeyboardView:
- Add `mShiftState: Int` field to `KeyboardSwitcher`, default `Keyboard.SHIFT_OFF`
- Rewrite `setShiftState()` to update `mShiftState` directly (not delegate to mInputView)
- Rewrite `getShiftState()` to return `mShiftState` (add this method if it doesn't exist — currently LatinIME calls `getInputView()?.getShiftState()`)
- Refactor `setKeyboardMode()` to NOT early-return on null `mInputView` — track `mMode`, `mCurrentId`, `mIsSymbols` independently
- Make `setCtrlIndicator()`, `setAltIndicator()`, `setMetaIndicator()` no-op safely (Compose `ModifierStateManager` handles visual indicators)
- Make `onAutoCompletionStateChanged()` no-op or forward to a callback
- Null-guard `ComposeSequence.executeToString()` line 209-210 (`getInputView()` may return null) — use `getShiftState()` from KeyboardSwitcher instead (Issue #34)
- Remove `changeLatinKeyboardView()`, `recreateInputView()`, `THEMES` array, `mInputView` field, `getInputView()`
- Categorize every `getInputView()` call site in LatinIME (30+ occurrences) as: (a) dead code to delete, (b) replace with shim method, (c) replace with Compose-equivalent (Issue #4)
- **Test:** Build + emulator — verify auto-capitalization, mode switching, shift state still work

### Step 1.3: Delete legacy View classes
- Delete `LatinKeyboardBaseView.java` (1,806 lines)
- Delete `LatinKeyboardView.java` (656 lines)
- Delete `PointerTracker.java` (647 lines)
- Delete `SwipeTracker.java` (157 lines)
- Delete `KeyDetector.java` (116 lines)
- Delete `ProximityKeyDetector.java` (87 lines)
- Delete `MiniKeyboardKeyDetector.java` (60 lines)
- Delete all `res/layout/input_*.xml` layout files (17 files), `keyboard_popup.xml`, `null_layout.xml` (Issue #18)
- Delete related `res/values/styles.xml` and `res/values/attrs.xml` entries used only by legacy views (Issue #18)
- **Test:** Build + emulator typing test

### Step 1.4: Delete superseded preference screens
- Delete `PrefScreenActions.java` (46 lines)
- Delete `PrefScreenFeedback.java` (52 lines)
- Delete `PrefScreenView.java` (60 lines)
- Delete `SeekBarPreference.java` (177 lines)
- Delete `SeekBarPreferenceString.java` (59 lines)
- Delete `AutoSummaryListPreference.java` (54 lines)
- Delete `AutoSummaryEditTextPreference.java` (28 lines)
- Delete `VibratePreference.java` (15 lines)
- Remove `<activity>` declarations for `PrefScreenActions`, `PrefScreenView`, `PrefScreenFeedback` from `AndroidManifest.xml` (Issue #17 — they ARE declared at lines 61, 67, 72)
- Remove keep rules for all 8 deleted preference classes from `proguard-rules.pro` (lines 17-26) (Issue #7)
- **Verify**: Confirm `DevKeySettingsActivity` fully replaces ALL settings accessible via `prefs.xml` (Issue #35). If not, either convert custom widget references in `prefs.xml` to standard AndroidX equivalents, or keep widget classes until DevKeySettingsActivity coverage is complete
- Remove referenced XML preference files (`prefs_actions.xml`, `prefs_feedback.xml`, `prefs_view.xml`) if unused
- If `prefs.xml` is fully superseded by Compose settings, delete it too (Issue #8). Otherwise update its custom widget class references
- **Test:** Build + verify settings screens still work

### Step 1.5: Remove dead code in LatinIME.java
- Delete `mToken` field, `MyInputMethodImpl` inner class, and `onCreateInputMethodInterface()` override (Issue #24 — confirmed `mToken` is write-only dead code, base `InputMethodImpl` is sufficient)
- Delete `mWordToSuggestions` field (never populated)
- Delete `switchToKeyboardView()` method
- Delete `measureCps()` and CPS fields
- Delete `VoiceRecognitionTrigger` references AND verify/remove the `voiceime` library dependency from `build.gradle.kts` (Issue #9)
- Remove HK branding from comments, log tags, constants
- **Test:** Build + emulator

### Step 1.6: Analyze Keyboard.kt + LatinKeyboard.java survivability
**Why:** After deleting the legacy view layer, `Keyboard.kt` (1004 lines, AOSP XML parser) and `LatinKeyboard.java` (~890 lines) may be effectively dead code since the Compose keyboard uses `QwertyLayout.kt`/`SymbolsLayout.kt` instead. (Issue #41)

- Trace all remaining callers of `KeyboardSwitcher.getKeyboard()` and `LatinKeyboard` constructors
- Determine if `Keyboard.kt`'s XML parsing is still needed or if it can be replaced/deleted
- Document findings — if dead, move deletion to this phase; if still needed (e.g., for layout config), keep and convert in Phase 6
- **Test:** Build

### Step 1.7: Commit Phase 1
- Commit: "Phase 1: Delete legacy Views, old prefs, dead code; add KeyboardSwitcher shim (~4,070 lines)"

---

## Phase 2: Simple Leaves

**Goal:** Convert 3 small Java files to Kotlin. ~530 lines.

### Step 2.1: ModifierKeyState.java → Kotlin
- Convert to Kotlin sealed class or enum-based state machine (47 lines)
- Keep in same package, same API (used by LatinIME)
- Remove HK references from comments
- **Test:** Existing tests pass

### Step 2.2: WordComposer.java → Kotlin
- Convert to Kotlin class (210 lines)
- Make `getTypedWord()` return `CharSequence` (same as Java)
- Use Kotlin collections where appropriate
- **Test:** Build

### Step 2.3: EditingUtil.java → Kotlin
- Convert static methods to top-level functions or extension functions on `InputConnection` (275 lines)
- `Range` and `SelectedWord` become data classes
- **Test:** Build

### Step 2.4: Commit Phase 2
- Commit: "Phase 2: Convert simple leaf files to Kotlin (~530 lines)"

**Note:** KeyDetector, MiniKeyboardKeyDetector, ProximityKeyDetector, SwipeTracker were deleted in Phase 1 — they are NOT converted. (Issue #10 — removed contradiction)

---

## Phase 3: Dictionary Stack

**Goal:** Convert dictionary subsystem to Kotlin with coroutines. ~2,350 lines.

### Coroutine Scope Strategy (Issue #13, #30, #32)

**ServiceScope pattern:** Each dictionary class owns a `CoroutineScope(SupervisorJob() + Dispatchers.Main)`:
- Created in the dictionary constructor
- Cancelled in the dictionary's `close()`/cleanup method
- LatinIME's `onDestroy()` calls `close()` on all dictionary instances (replacing existing `cancelTask()` calls)

**InputConnection thread contract (Issue #30):**
- ALL `InputConnection` calls MUST happen on `Dispatchers.Main` (the main thread)
- Dictionary I/O uses `withContext(Dispatchers.IO)` for the actual I/O operation only
- Results/callbacks always return to `Dispatchers.Main` before touching InputConnection
- Rule: **Never call `getCurrentInputConnection()` from a non-Main dispatcher**

**Mixed async model acknowledgment (Issue #20):**
- LatinIME uses `Handler`/`Message` for `MSG_UPDATE_SUGGESTIONS`, `MSG_UPDATE_SHIFT_STATE` until Phase 7
- Dictionary coroutines must post results back to main thread via `Dispatchers.Main` to match existing Handler-based expectations
- This coexistence is safe because both land on the main thread

### Step 3.1: Dictionary.java → Kotlin
- Convert abstract class (121 lines)
- `WordCallback` interface becomes Kotlin functional interface
- `DataType` enum stays as-is
- **Test:** Build

### Step 3.2: ExpandableDictionary.java → Kotlin
- Convert class (692 lines)
- Replace `LoadDictionaryTask extends AsyncTask` with `suspend fun loadDictionary()` in class-owned `CoroutineScope`
- Trie data structure (`Node`, `NodeArray`, `NextWord`) become Kotlin data classes
- `BASE_CHARS[]` table stays as companion object array
- **Test:** Build

### Step 3.3: UserDictionary.java → Kotlin
- Convert class (146 lines)
- ContentObserver stays (Android API)
- Background thread for `addWord()` → coroutine `withContext(Dispatchers.IO)`
- **Test:** Build

### Step 3.4: AutoDictionary.java → Kotlin
- Convert class (260 lines)
- Replace `DatabaseHelper extends SQLiteOpenHelper` with Kotlin class
- Replace `UpdateDbTask extends AsyncTask` with coroutine
- Break circular dependency: replace `LatinIME mIme` reference with an interface (`WordPromotionDelegate`)
- **Test:** Build

### Step 3.5: UserBigramDictionary.java → Kotlin
- Convert class (402 lines)
- Same AsyncTask→coroutine pattern as AutoDictionary
- Same circular dependency fix
- **Test:** Build

### Step 3.6: BinaryDictionary.java (dev.devkey) → Kotlin
- Convert class (309 lines)
- JNI delegation to `org.pocketworkstation.pckeyboard.BinaryDictionary` preserved
- `Class.forName()` call preserved — works identically in Kotlin (JVM class names are the same) (Issue #19)
- `native` method wrappers become `external` in Kotlin
- Verify reflection-based method invocations still resolve correct signatures after conversion
- **Test:** Build + dictionary lookup works on emulator

### Step 3.7: Suggest.java → Kotlin
- Convert class (539 lines)
- `WordCallback` implementation stays
- Suggestion ranking algorithm preserved exactly
- **Test:** Build + suggestion strip shows suggestions on emulator

### Step 3.8: Commit Phase 3
- Commit: "Phase 3: Convert dictionary stack to Kotlin with coroutines (~2,350 lines)"

---

## Phase 4: Text & Composition

**Goal:** Convert text processing and compose key files. ~1,830 lines.

### Step 4.1: TextEntryState.java → Kotlin
- Convert from all-static to instance class (283 lines)
- States become Kotlin enum
- Inject as dependency instead of static access
- **Test:** Build

### Step 4.2: ComposeSequence.java → Kotlin
- Convert class (1,137 lines)
- `ComposeSequencing` interface → Kotlin interface
- Static compose map → companion object `Map<String, String>`
- Update LatinIME to implement Kotlin `ComposeSequencing` (since LatinIME is still Java in this phase, ensure the Kotlin interface methods have Java-compatible signatures)
- **Note:** The 28 `LatinKeyboardView.KEYCODE_*` references were already migrated to `KeyCodes` in Phase 0 — verify they resolve correctly (Issue #27)
- `executeToString()` shift check was already null-guarded in Phase 1 Step 1.2 — convert to use `KeyboardSwitcher.getShiftState()` directly
- **Test:** Build

### Step 4.3: DeadAccentSequence.java → Kotlin
- Convert class (114 lines)
- Extends Kotlin ComposeSequence
- **Test:** Build

### Step 4.4: LatinIMEUtil.java → Kotlin
- Convert class (165 lines)
- `GCUtils` stays as utility
- `RingCharBuffer` becomes Kotlin with ArrayDeque
- `cancelTask()` → no-op or coroutine cancellation helper (AsyncTasks already replaced in Phase 3)
- **Test:** Build

### Step 4.5: Commit Phase 4
- Commit: "Phase 4: Convert text processing to Kotlin (~1,830 lines)"

---

## Phase 5: Support Files

**Goal:** Convert remaining support files. ~2,800 lines.

**Note:** This was previously Phase 6, moved before Settings Unification so that Java files reading `sKeyboardSettings` (CandidateView, LanguageSwitcher, Keyboard.kt) are converted to Kotlin FIRST, avoiding throwaway migration work when updating callers. (Issue #40)

### Step 5.1: LanguageSwitcher.java → Kotlin
- Convert class (229 lines)
- Break circular dependency with LatinIME (use interface)
- **Test:** Build + language switching works

### Step 5.2: PluginManager.java → Kotlin
- Convert class (292 lines)
- BroadcastReceiver stays (Android API)
- Plugin scanning → coroutine (use same ServiceScope pattern from Phase 3)
- **Test:** Build

### Step 5.3: CandidateView.java → Compose
- Replace Canvas-based View (495 lines) with Compose suggestion strip
- Break circular dependency: CandidateView no longer holds `LatinIME mService` — use callbacks/lambdas for `pickSuggestionManually()`, `onAutoCompletionStateChanged()`, `addWordToDictionary()` (Issue #6)
- Clean up `R.layout.candidates` XML and `res/values/attrs.xml` declarations for CandidateView
- **Test:** Build + suggestion strip renders + tapping suggestions works

### Step 5.4: LatinKeyboard.java → Kotlin (if not deleted in Phase 1)
- If Phase 1 Step 1.6 determined LatinKeyboard is dead code → skip (already deleted)
- Otherwise: convert class (~890 lines)
- `LatinKey` inner class stays or merges with Compose `KeyData`
- `SlidingLocaleDrawable` → Compose equivalent if needed
- **Test:** Build

### Step 5.5: Main.java → Kotlin/Compose
- Convert setup wizard Activity (102 lines)
- Already partially handled by DevKeySettingsActivity
- **Test:** Build + app launches correctly

### Step 5.6: NotificationReceiver.java → Kotlin
- Convert BroadcastReceiver (40 lines)
- **Test:** Build

### Step 5.7: LatinIMEBackupAgent.java → Kotlin
- Convert BackupAgentHelper (33 lines)
- **Test:** Build

### Step 5.8: InputLanguageSelection.java → Kotlin/Compose
- Convert deprecated PreferenceActivity (384 lines) to Compose screen
- Integrate into existing settings navigation
- Update `InputLanguageSelection` declaration in `AndroidManifest.xml` (may become embedded in settings nav) (Issue #17)
- **Test:** Build + language selection works

### Step 5.9: Commit Phase 5
- Commit: "Phase 5: Convert support files to Kotlin/Compose (~2,800 lines)"

---

## Phase 6: Settings Unification

**Goal:** Fold GlobalKeyboardSettings into SettingsRepository. ~274 lines replaced.

**Note:** This was previously Phase 5. Moved after support file conversion so callers are already Kotlin, avoiding throwaway Java updates. (Issue #40)

### Step 6.1: Audit all GlobalKeyboardSettings field accesses
- Map every caller of `LatinIME.sKeyboardSettings.*` across all files
- **Known callers** (Issue #22 — 73 occurrences across 9 files):
  - `LatinIME.java` (39 occurrences) — not yet converted, update in Java
  - `Keyboard.kt` (9 occurrences, including constructor-time reads at lines 195, 325, 343, 347, 418, 615) (Issue #37)
  - `KeyboardSwitcher.kt` (4 occurrences)
  - `CandidateView` (1 occurrence — converted to Compose in Phase 5, may already be updated)
  - `LanguageSwitcher` (1 occurrence — converted in Phase 5)
  - `LatinKeyboardBaseView.java` (10 occurrences — DELETED in Phase 1)
  - `LatinKeyboardView.java` (2 occurrences — DELETED in Phase 1)
  - `PointerTracker.java` (4 occurrences — DELETED in Phase 1)
- Group by field to understand read patterns

### Step 6.2: Add StateFlow properties to SettingsRepository
- For each GlobalKeyboardSettings field, add a `StateFlow` property to `SettingsRepository`
- Use same SharedPreferences keys (compile-time constants)
- Implement `FLAG_PREF_*` reload signals as `SharedFlow<Unit>` events

### Step 6.3: Update all callers
- Replace `LatinIME.sKeyboardSettings.fieldName` with `settingsRepository.fieldName.value`
- Inject `SettingsRepository` where needed (constructor injection)
- **Keyboard.kt** needs special attention: constructor-time static accesses (lines 195, 325, 343, 347, 418, 615) must be changed to injected parameters (Issue #37)
- In Compose: use `collectAsState()`

### Step 6.4: Delete GlobalKeyboardSettings.java
- Remove the class and `LatinIME.sKeyboardSettings` static field
- Remove `initPrefs()` and `sharedPreferenceChanged()` in LatinIME

### Step 6.5: Commit Phase 6
- Commit: "Phase 6: Unify settings into SettingsRepository"

---

## Phase 7a: Extract KeyEventSender

**Goal:** Extract key synthesis from LatinIME into a testable standalone class. LatinIME stays Java.

**Note:** Phase 7 was split into 4 sub-phases with individual commits for rollback safety. (Issue #14)

### Step 7a.1: Extract KeyEventSender.kt
- Create `app/src/main/java/dev/devkey/keyboard/core/KeyEventSender.kt`
- Move from LatinIME: `sendModifiableKeyChar()`, `sendModifiedKeyDownUp()`, `sendSpecialKey()`, `sendTab()`, `sendEscape()`, `sendKeyDown()`, `sendKeyUp()`, `sendModifierKeysDown()`, `sendModifierKeysUp()`, `handleModifierKeysUp()`, `getMetaState()`, `isShiftMod()`, `isConnectbot()`, `asciiToKeyCode[]`, `ESC_SEQUENCES`, `CTRL_SEQUENCES`
- Constructor takes: `inputConnectionProvider: () -> InputConnection?`, `modifierState: ModifierStateManager`, `settings: SettingsRepository`
- **Hidden dependency (Issue #15):** `isShiftMod()` calls `mShiftKeyState.isChording()` (Java `ModifierKeyState`). Since Step 7b enhances `ModifierStateManager` with CHORDING AFTER this step, use a `shiftStateProvider: () -> Boolean` lambda that LatinIME provides. This wraps the existing `isShiftMod()` logic initially and is simplified in 7b.
- Leave thin delegation wrappers in LatinIME.java that forward to KeyEventSender

### Step 7a.2: Write comprehensive unit tests
- Test each escape sequence maps to correct VT code (Issue #38)
- Test sign convention (negative keycodes in ESC_SEQUENCES/CTRL_SEQUENCES)
- Test ConnectBot tab/Ctrl-sequence hack mapping
- Test `sendModifiableKeyChar()` with and without active modifiers
- Test `sendModifiedKeyDownUp()` produces correct `KeyEvent` sequences
- Test `getMetaState()` returns correct flags for all modifier combinations
- **Test:** Unit tests + build + Ctrl+V works on emulator

### Step 7a.3: Commit Phase 7a
- Commit: "Phase 7a: Extract KeyEventSender with comprehensive tests"

---

## Phase 7b: Enhance ModifierStateManager

**Goal:** Unify modifier state. Delete `ModifierKeyState`.

### Step 7b.1: Enhance ModifierStateManager with chording
- Add `CHORDING` state
- Add `onOtherKeyPressed()` → transitions HELD→CHORDING
- Add `getMetaState(): Int` for KeyEvent construction
- Delete `ModifierKeyState.kt` (merged into ModifierStateManager)
- Update `KeyView.kt` and `KeyboardActionBridge.kt` to use enhanced state machine
- Simplify `KeyEventSender`'s `shiftStateProvider` lambda to use `ModifierStateManager.isChording()` directly

### Step 7b.2: Update tests
- `ModifierStateManagerTest` updated with CHORDING state tests
- Verify `KeyEventSenderTest` still passes
- **Test:** Full test suite + build

### Step 7b.3: Commit Phase 7b
- Commit: "Phase 7b: Unify modifier state machine with CHORDING support"

---

## Phase 7c: Convert LatinIME to Kotlin

**Goal:** Convert the core IME to Kotlin. ~3,627 lines.

### Step 7c.1: Convert LatinIME.java → LatinIME.kt
- Run Android Studio's J2K converter as starting point
- Fix all null-safety issues (Kotlin compiler catches them)
- Replace `Handler`/`Message` with coroutine-based delayed execution (use `ServiceScope` pattern from Phase 3)
- Replace `new Thread(() -> commandModeDetector.detectSync(...))` with `launch(Dispatchers.IO)` — ensure result callbacks land on `Dispatchers.Main` (Issue #30)
- Replace bare boolean flags (`mModCtrl` etc.) with `ModifierStateManager` references (already enhanced in 7b)
- Replace `sInstance` static with proper lifecycle-aware pattern — enumerate all `getInstance()` callers first (Issue #39): `ComposeSequence`, `KeyboardSwitcher`, LatinIME itself
- Remove `SessionDependencies` — pass dependencies directly to Compose via `ComposeKeyboardViewFactory.create()`
- Remove all `getInputView()` null-check patterns (legacy views deleted in Phase 1)
- Fix `isConnectbot()` null-check-after-dereference bug
- **Compose/InputConnection safety (Issue #31):** Ensure no Compose-side InputConnection reads happen during `beginBatchEdit()`/`endBatchEdit()` blocks. Cache smart backspace resolution at start of key handling.
- **Test:** Build + full emulator regression test

### Step 7c.2: Commit Phase 7c
- Commit: "Phase 7c: Convert LatinIME to Kotlin"

---

## Phase 7d: Final Cleanup

**Goal:** Simplify bridge, remove stale code, lint pass.

### Step 7d.1: Simplify KeyboardActionBridge
- Bridge now calls `KeyEventSender` directly instead of routing through `OnKeyboardActionListener` → `LatinIME.onKey()` for key synthesis
- `LatinIME` still handles: prediction, suggestion, composing text, InputConnection lifecycle
- **Test:** Build + typing test

### Step 7d.2: Remove remaining HK branding
- Scan all Kotlin files for "Hacker", "pckeyboard", "HK" references
- Update log tags, comments, package references
- Do NOT touch `org.pocketworkstation.pckeyboard.BinaryDictionary` (JNI constraint)
- **Test:** Build

### Step 7d.3: Final cleanup
- Remove `KeyboardActionBridge` if no longer needed (LatinIME is now Kotlin)
- Remove `ComposeSequencing` interface if `LatinIME` calls `ComposeSequence` directly
- Remove stale `@JvmStatic`, `@JvmField` annotations from Kotlin-only code
- Run `./gradlew lint` and fix warnings
- **Test:** Full test suite + emulator

### Step 7d.4: Commit Phase 7d
- Commit: "Phase 7d: Simplify bridge, remove branding, final cleanup"

---

## Post-Migration Verification

1. `./gradlew test` — all tests pass (target: 300+ tests)
2. `./gradlew assembleDebug` — clean build
3. Emulator testing:
   - Type in Messages, Chrome, Notes
   - Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+A in multiple apps
   - All 3 keyboard modes: Compact, Compact Dev, Full
   - Shift: tap (one-shot), double-tap (locked), hold+type (chord)
   - Symbol keyboard and back
   - Language switching (if multiple locales configured)
   - ConnectBot/Termux: verify VT escape sequences work (Issue #38)
   - Auto-capitalization: verify sentence-start caps still triggers
4. Verify only `org/pocketworkstation/pckeyboard/BinaryDictionary.java` remains as Java
5. Grep for "Hacker" / "pckeyboard" / "HK" — only in JNI bridge and credits doc

## File Count After Migration

- **Java files remaining:** 1 (JNI bridge)
- **Java files deleted:** ~24 (8 pref screens + 7 legacy views + 9 converted)
- **Java files converted to Kotlin:** ~16
- **Net lines removed:** ~4,000+ (deletions)
- **New Kotlin files:** KeyEventSender.kt, KeyboardActionListener.kt, KeyEventSenderTest.kt

## Rollback Strategy

Each phase/sub-phase is committed separately. Rolling back requires reverting phases in **reverse order** (later phases depend on earlier ones). Each phase MUST leave the codebase in a compilable, testable state. If mid-phase work breaks the build, use `git stash` rather than committing broken state. (Issue #25)

## Adversarial Review Tracking

All issues from the 2-wave adversarial review are addressed in this plan:
- Issues #1, #2, #11, #12, #28 → Phase 0 (constant migration)
- Issues #3, #4, #16, #29, #33, #34 → Phase 1 Step 1.2 (KeyboardSwitcher shim)
- Issue #5 → Phase 1 Step 1.1 (no @JvmDefault)
- Issues #6, #21 → Phase 5 Step 5.3 (CandidateView conversion before settings unification)
- Issues #7, #8, #35 → Phase 1 Step 1.4 (ProGuard + prefs.xml cleanup)
- Issue #9 → Phase 1 Step 1.5 (VoiceRecognitionTrigger dependency cleanup)
- Issue #10 → Phase 2 note (removed contradiction)
- Issues #13, #30, #32 → Phase 3 preamble (CoroutineScope strategy)
- Issue #14 → Phase 7 split into 7a/7b/7c/7d
- Issue #15 → Phase 7a Step 7a.1 (shiftStateProvider lambda)
- Issues #17, #18 → Phase 1 Steps 1.3-1.4 (manifest + resource cleanup)
- Issue #20 → Phase 3 preamble (mixed async model)
- Issues #22, #37 → Phase 6 Step 6.1 (caller enumeration)
- Issue #23, #41 → Phase 1 Step 1.6 (dead code analysis)
- Issue #24 → Phase 1 Step 1.5 (confirmed dead code)
- Issue #25 → Rollback Strategy section
- Issue #26 → Phase 1 Step 1.2 testing
- Issue #27 → Phase 4 Step 4.2 note
- Issue #31 → Phase 7c Step 7c.1 (Compose/InputConnection safety)
- Issue #36 → Phase 1 Step 1.2 (dual rendering path analysis)
- Issue #38 → Phase 7a Step 7a.2 (escape sequence tests)
- Issue #39 → Phase 7c Step 7c.1 (singleton callers)
- Issue #40 → Phases 5/6 swapped
