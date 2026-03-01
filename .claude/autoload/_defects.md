# Active Defects

Max 7 active. Oldest rotates to `.claude/logs/defects-archive.md`.

## Active Patterns

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

### [IME] 2026-02-24: Legacy LatinIME assumes LatinKeyboardView exists — NPE with Compose
**Pattern**: LatinIME.java has ~30 calls to `mKeyboardSwitcher.getInputView()` expecting a `LatinKeyboardView`. With Compose keyboard, this is always null.
**Prevention**: Every `getInputView()` call must be null-guarded.
**Ref**: @LatinIME.java (30+ call sites)

### [DB] 2026-02-24: DevKeyDatabase uses destructive migration — wipes user data on schema change
**Pattern**: `DevKeyDatabase.buildDatabase()` calls `fallbackToDestructiveMigration()`. Any schema change triggers full database wipe.
**Prevention**: Before changing any Room entity, write a proper `Migration(oldVersion, newVersion)`.
**Ref**: @DevKeyDatabase.kt

### [BUILD] 2026-02-23: /implement agents need broad Bash/Edit/Write permissions
**Pattern**: Background agents dispatched by /implement cannot prompt for tool permissions.
**Prevention**: Before running /implement, add Edit, Write, and Bash(*) to `.claude/settings.local.json` permissions.
**Ref**: @.claude/settings.local.json
