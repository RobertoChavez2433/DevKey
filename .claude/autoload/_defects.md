# Active Defects

Max 7 active. Oldest rotates to `.claude/logs/defects-archive.md`.

## Active Patterns

### [IME] 2026-03-02: Dual modifier state machines cause potential double-toggle
**Pattern**: Three parallel modifier tracking systems: Compose `ModifierStateManager` (visual), Java `ModifierKeyState` (chording), and `mModCtrl/Alt/Meta/Fn` booleans (key synthesis). Changes in one don't propagate to others.
**Prevention**: Kotlin migration Phase 7 unifies all three into a single `ModifierStateManager` with CHORDING state. Until then, avoid modifying any modifier logic independently.
**Ref**: @ModifierStateManager.kt, @ModifierKeyState.java, @LatinIME.java:onPress/onRelease

### [IME] 2026-03-02: Dual settings systems with no compile-time link
**Pattern**: `GlobalKeyboardSettings` (Java) and `SettingsRepository` (Kotlin) both listen to the same `SharedPreferences` with separate listeners. Pref key strings are duplicated as literals with no compile-time link — renaming one silently breaks the other.
**Prevention**: Kotlin migration Phase 5 unifies into single `SettingsRepository`. Until then, never rename a pref key without checking both systems.
**Ref**: @GlobalKeyboardSettings.java, @SettingsRepository.kt, @DevKeyKeyboard.kt:rememberPreference

### [IME] 2026-03-02: Thread leak in onStartInputView — bare Thread() per field focus
**Pattern**: `LatinIME.onStartInputView()` spawns `new Thread(() -> commandModeDetector.detectSync(pkg)).start()` on every input field focus. No thread pool, cancellation, or lifecycle tracking. Rapid field-switching creates unbounded thread accumulation.
**Prevention**: Replace with `launch(Dispatchers.IO)` in coroutine scope during Kotlin migration Phase 7. Add cancellation on `onFinishInputView()`.
**Ref**: @LatinIME.java:841-847

### [ANDROID] 2026-03-01: KeyCodes use ASCII values, not Android KeyEvent constants
**Pattern**: The LatinIME backend's `onKey()` switch operates on ASCII codepoints (Tab=9, Enter=10), not Android `KeyEvent.KEYCODE_*` constants (Tab=61). Using the wrong value silently sends the wrong character (e.g., 61 sends `=` instead of tab).
**Prevention**: Always verify keycode values against LatinIME.onKey() switch cases and keycodes.xml. The `KeyCodes` object in KeyData.kt is the single source of truth for Compose keyboard keycodes.
**Ref**: @KeyData.kt:KeyCodes, @LatinIME.java:onKey

### [ANDROID] 2026-03-01: PendingIntent requires FLAG_IMMUTABLE on API 31+
**Pattern**: `PendingIntent.getBroadcast(..., 0)` with flags=0 crashes on API 31+ (targetSdk 34). Similarly, `registerReceiver()` without RECEIVER_NOT_EXPORTED crashes on API 34+.
**Prevention**: Always pass `PendingIntent.FLAG_IMMUTABLE` (or FLAG_MUTABLE if needed). Always pass `Context.RECEIVER_NOT_EXPORTED` for internal receivers.
**Ref**: @LatinIME.java:showNotification

### [IME] 2026-02-24: API 36 requires ViewTreeLifecycleOwner on IME window decor view
**Pattern**: On API 36+, `InputMethodService.setInputView()` walks up to the window's decor view and requires a `ViewTreeLifecycleOwner`. Setting it only on the ComposeView child causes `IllegalStateException`.
**Prevention**: In `onCreateInputView()`, call `installLifecycleOwner()` on decor view BEFORE returning the ComposeView. Both decor and ComposeView must share the same lifecycle owner instance.
**Ref**: @ComposeKeyboardViewFactory.kt, @LatinIME.java:onCreateInputView

### [DB] 2026-02-24: DevKeyDatabase uses destructive migration — wipes user data on schema change
**Pattern**: `DevKeyDatabase.buildDatabase()` calls `fallbackToDestructiveMigration()`. Any schema change triggers full database wipe.
**Prevention**: Before changing any Room entity, write a proper `Migration(oldVersion, newVersion)`.
**Ref**: @DevKeyDatabase.kt
