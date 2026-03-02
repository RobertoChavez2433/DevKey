# Kotlin Migration Implementation Plan

**Design doc:** `docs/plans/2026-03-02-kotlin-migration-design.md`
**Status:** Ready for implementation
**Approach:** Bottom-Up Incremental (7 phases)

## Phase 1: Dead Code Purge

**Goal:** Delete ~4,070 lines of legacy/superseded code. Extract OnKeyboardActionListener.

### Step 1.1: Extract OnKeyboardActionListener
- Create `app/src/main/java/dev/devkey/keyboard/core/KeyboardActionListener.kt`
- Define Kotlin interface with: `onKey()`, `onPress()`, `onRelease()`, `onText()`, `onCancel()`, `swipeLeft/Right/Up/Down()`
- Add `@JvmDefault` or default implementations where needed for Java compatibility during transition
- Update `KeyboardActionBridge.kt` and `ComposeKeyboardViewFactory.kt` to use new interface
- Update `LatinIME.java` to implement new Kotlin interface instead of `LatinKeyboardBaseView.OnKeyboardActionListener`
- **Test:** `./gradlew test && ./gradlew assembleDebug`

### Step 1.2: Delete legacy View classes
- Delete `LatinKeyboardBaseView.java` (1,806 lines)
- Delete `LatinKeyboardView.java` (656 lines)
- Delete `PointerTracker.java` (647 lines)
- Delete `SwipeTracker.java` (157 lines)
- Delete `KeyDetector.java` (116 lines)
- Delete `ProximityKeyDetector.java` (87 lines)
- Delete `MiniKeyboardKeyDetector.java` (60 lines)
- Update `KeyboardSwitcher.kt`: remove `mInputView`, `getInputView()`, `changeLatinKeyboardView()`, `hasDistinctMultitouch()` (hardcode true)
- Update `LatinIME.java`: remove all `getInputView()` null-check call sites, `switchToKeyboardView()`, `mKeyboardSwitcher.getInputView()` references
- Move keycode constants from `LatinKeyboardView` into `KeyCodes` object in `KeyData.kt` (verify alignment with existing `KeyCodes`)
- **Test:** Build + emulator typing test

### Step 1.3: Delete superseded preference screens
- Delete `PrefScreenActions.java` (46 lines)
- Delete `PrefScreenFeedback.java` (52 lines)
- Delete `PrefScreenView.java` (60 lines)
- Delete `SeekBarPreference.java` (177 lines)
- Delete `SeekBarPreferenceString.java` (59 lines)
- Delete `AutoSummaryListPreference.java` (54 lines)
- Delete `AutoSummaryEditTextPreference.java` (28 lines)
- Delete `VibratePreference.java` (15 lines)
- Remove from AndroidManifest.xml if declared as activities
- Remove referenced XML preference files (`prefs_actions.xml`, `prefs_feedback.xml`, `prefs_view.xml`) if unused
- **Test:** Build + verify settings screens still work in Kotlin

### Step 1.4: Remove dead code in LatinIME.java
- Delete `mToken` field and `MyInputMethodImpl` inner class
- Delete `mWordToSuggestions` field (never populated)
- Delete `switchToKeyboardView()` method
- Delete `measureCps()` and CPS fields
- Delete `VoiceRecognitionTrigger` references (voice handled differently now)
- Remove HK branding from comments, log tags, constants
- **Test:** Build + emulator

### Step 1.5: Commit Phase 1
- Commit with message: "Phase 1: Delete legacy Views, old prefs, dead code (~4,070 lines)"

---

## Phase 2: Simple Leaves

**Goal:** Convert 7 small Java files to Kotlin. ~750 lines.

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

### Step 2.4: Remaining KeyDetector files (if not deleted in Phase 1)
- Verify all KeyDetector classes were deleted in Phase 1
- If any remain (referenced outside legacy views), convert to Kotlin

### Step 2.5: Commit Phase 2
- Commit with message: "Phase 2: Convert simple leaf files to Kotlin (~750 lines)"

---

## Phase 3: Dictionary Stack

**Goal:** Convert dictionary subsystem to Kotlin with coroutines. ~2,350 lines.

### Step 3.1: Dictionary.java → Kotlin
- Convert abstract class (121 lines)
- `WordCallback` interface becomes Kotlin functional interface
- `DataType` enum stays as-is
- **Test:** Build

### Step 3.2: ExpandableDictionary.java → Kotlin
- Convert class (692 lines)
- Replace `LoadDictionaryTask extends AsyncTask` with `suspend fun loadDictionary()` + `CoroutineScope`
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
- `Class.forName()` call preserved
- `native` method wrappers become `external` in Kotlin
- **Test:** Build + dictionary lookup works on emulator

### Step 3.7: Suggest.java → Kotlin
- Convert class (539 lines)
- `WordCallback` implementation stays
- Suggestion ranking algorithm preserved exactly
- **Test:** Build + suggestion strip shows suggestions on emulator

### Step 3.8: Commit Phase 3
- Commit with message: "Phase 3: Convert dictionary stack to Kotlin with coroutines (~2,350 lines)"

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
- Update LatinIME to implement Kotlin `ComposeSequencing`
- **Test:** Build

### Step 4.3: DeadAccentSequence.java → Kotlin
- Convert class (114 lines)
- Extends Kotlin ComposeSequence
- **Test:** Build

### Step 4.4: LatinIMEUtil.java → Kotlin
- Convert class (165 lines)
- `GCUtils` stays as utility
- `RingCharBuffer` becomes Kotlin with ArrayDeque
- `cancelTask()` → no-op or coroutine cancellation helper
- **Test:** Build

### Step 4.5: Commit Phase 4
- Commit with message: "Phase 4: Convert text processing to Kotlin (~1,830 lines)"

---

## Phase 5: Settings Unification

**Goal:** Fold GlobalKeyboardSettings into SettingsRepository. ~274 lines replaced.

### Step 5.1: Audit all GlobalKeyboardSettings field accesses
- Map every caller of `LatinIME.sKeyboardSettings.*` across all files
- Group by field to understand read patterns

### Step 5.2: Add StateFlow properties to SettingsRepository
- For each GlobalKeyboardSettings field, add a `StateFlow` property to `SettingsRepository`
- Use same SharedPreferences keys (compile-time constants)
- Implement `FLAG_PREF_*` reload signals as `SharedFlow<Unit>` events

### Step 5.3: Update all callers
- Replace `LatinIME.sKeyboardSettings.fieldName` with `settingsRepository.fieldName.value`
- Inject `SettingsRepository` where needed (constructor injection)
- In Compose: use `collectAsState()`

### Step 5.4: Delete GlobalKeyboardSettings.java
- Remove the class and `LatinIME.sKeyboardSettings` static field
- Remove `initPrefs()` and `sharedPreferenceChanged()` in LatinIME

### Step 5.5: Commit Phase 5
- Commit with message: "Phase 5: Unify settings into SettingsRepository"

---

## Phase 6: Support Files

**Goal:** Convert remaining support files. ~2,800 lines.

### Step 6.1: LanguageSwitcher.java → Kotlin
- Convert class (229 lines)
- Break circular dependency with LatinIME (use interface)
- **Test:** Build + language switching works

### Step 6.2: PluginManager.java → Kotlin
- Convert class (292 lines)
- BroadcastReceiver stays (Android API)
- Plugin scanning → coroutine
- **Test:** Build

### Step 6.3: CandidateView.java → Compose
- Replace Canvas-based View (495 lines) with Compose suggestion strip
- Break circular dependency: CandidateView no longer holds `LatinIME mService`
- Use callbacks/lambdas instead
- **Test:** Build + suggestion strip renders + tapping suggestions works

### Step 6.4: LatinKeyboard.java → Kotlin
- Convert class (~890 lines)
- `LatinKey` inner class stays or merges with Compose `KeyData`
- `SlidingLocaleDrawable` → Compose equivalent if needed
- **Test:** Build

### Step 6.5: Main.java → Kotlin/Compose
- Convert setup wizard Activity (102 lines)
- Already partially handled by DevKeySettingsActivity
- **Test:** Build + app launches correctly

### Step 6.6: NotificationReceiver.java → Kotlin
- Convert BroadcastReceiver (40 lines)
- **Test:** Build

### Step 6.7: LatinIMEBackupAgent.java → Kotlin
- Convert BackupAgentHelper (33 lines)
- **Test:** Build

### Step 6.8: InputLanguageSelection.java → Kotlin/Compose
- Convert deprecated PreferenceActivity (384 lines) to Compose screen
- Integrate into existing settings navigation
- **Test:** Build + language selection works

### Step 6.9: Commit Phase 6
- Commit with message: "Phase 6: Convert support files to Kotlin/Compose (~2,800 lines)"

---

## Phase 7: LatinIME Itself

**Goal:** Convert the core IME to Kotlin. Extract subsystems. ~3,627 lines.

### Step 7.1: Extract KeyEventSender.kt
- Create `app/src/main/java/dev/devkey/keyboard/core/KeyEventSender.kt`
- Move from LatinIME: `sendModifiableKeyChar()`, `sendModifiedKeyDownUp()`, `sendSpecialKey()`, `sendTab()`, `sendEscape()`, `sendKeyDown()`, `sendKeyUp()`, `sendModifierKeysDown()`, `sendModifierKeysUp()`, `handleModifierKeysUp()`, `getMetaState()`, `isShiftMod()`, `isConnectbot()`, `asciiToKeyCode[]`, `ESC_SEQUENCES`, `CTRL_SEQUENCES`
- Constructor takes: `inputConnectionProvider: () -> InputConnection?`, `modifierState: ModifierStateManager`, `settings: SettingsRepository`
- Write comprehensive unit tests (see test plan in design doc)
- **Test:** Unit tests + build + Ctrl+V works on emulator

### Step 7.2: Enhance ModifierStateManager with chording
- Add `CHORDING` state
- Add `onOtherKeyPressed()` → transitions HELD→CHORDING
- Add `getMetaState(): Int` for KeyEvent construction
- Delete `ModifierKeyState.kt` (merged)
- Update `KeyView.kt` and `KeyboardActionBridge.kt` to use enhanced state machine
- **Test:** ModifierStateManagerTest updated + build

### Step 7.3: Convert LatinIME.java → LatinIME.kt
- Run Android Studio's J2K converter as starting point
- Fix all null-safety issues (Kotlin compiler catches them)
- Replace `Handler`/`Message` with coroutine-based delayed execution
- Replace `new Thread(() -> commandModeDetector.detectSync(...))` with `launch(Dispatchers.IO)`
- Replace bare boolean flags (`mModCtrl` etc.) with `ModifierStateManager` references
- Replace `sInstance` static with proper lifecycle-aware pattern
- Remove `SessionDependencies` — pass dependencies directly to Compose via `ComposeKeyboardViewFactory.create()`
- Remove all `getInputView()` null-check patterns (legacy views deleted in Phase 1)
- Fix `isConnectbot()` null-check-after-dereference bug
- **Test:** Build + full emulator regression test

### Step 7.4: Simplify KeyboardActionBridge
- Bridge now calls `KeyEventSender` directly instead of routing through `OnKeyboardActionListener` → `LatinIME.onKey()` for key synthesis
- `LatinIME` still handles: prediction, suggestion, composing text, InputConnection lifecycle
- **Test:** Build + typing test

### Step 7.5: Remove remaining HK branding
- Scan all Kotlin files for "Hacker", "pckeyboard", "HK" references
- Update log tags, comments, package references
- Do NOT touch `org.pocketworkstation.pckeyboard.BinaryDictionary` (JNI constraint)
- **Test:** Build

### Step 7.6: Final cleanup
- Remove `KeyboardActionBridge` if no longer needed (LatinIME is now Kotlin)
- Remove `ComposeSequencing` interface if `LatinIME` calls `ComposeSequence` directly
- Remove stale `@JvmStatic`, `@JvmField` annotations from Kotlin-only code
- Run `./gradlew lint` and fix warnings
- **Test:** Full test suite + emulator

### Step 7.7: Commit Phase 7
- Commit with message: "Phase 7: Convert LatinIME to Kotlin, extract KeyEventSender, unify modifiers"

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
4. Verify only `org/pocketworkstation/pckeyboard/BinaryDictionary.java` remains as Java
5. Grep for "Hacker" / "pckeyboard" / "HK" — only in JNI bridge and credits doc

## File Count After Migration

- **Java files remaining:** 1 (JNI bridge)
- **Java files deleted:** ~24 (8 pref screens + 7 legacy views + 9 converted)
- **Java files converted to Kotlin:** ~16
- **Net lines removed:** ~4,000+ (deletions)
- **New Kotlin files:** KeyEventSender.kt, KeyboardActionListener.kt, KeyEventSenderTest.kt
