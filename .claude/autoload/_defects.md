# Active Defects

Max 7 active. Oldest rotates to `.claude/logs/defects-archive.md`.

## Active Patterns

### [IME] 2026-02-24: API 36 requires ViewTreeLifecycleOwner on IME window decor view
**Pattern**: On API 36+, `InputMethodService.setInputView()` walks up to the window's decor view and requires a `ViewTreeLifecycleOwner`. Setting it only on the ComposeView child causes `IllegalStateException: ViewTreeLifecycleOwner not found`.
**Prevention**: In `onCreateInputView()`, call `installLifecycleOwner()` on `getWindow().getWindow().getDecorView()` BEFORE returning the ComposeView.
**Ref**: @ComposeKeyboardViewFactory.kt, @LatinIME.java:onCreateInputView

### [IME] 2026-02-24: Legacy LatinIME assumes LatinKeyboardView exists — NPE with Compose
**Pattern**: LatinIME.java has ~30 calls to `mKeyboardSwitcher.getInputView()` expecting a `LatinKeyboardView`. With Compose keyboard, this is always null. Unguarded calls cause NPE cascade (`onStartInputView`, `updateSuggestions`, `showSuggestions`, `onRelease`, `pickSuggestion`, etc.).
**Prevention**: Every `getInputView()` call must be null-guarded. When adding new code that touches the legacy keyboard view, always check for null first.
**Ref**: @LatinIME.java (30+ call sites)

### [DB] 2026-02-24: DevKeyDatabase uses destructive migration — wipes user data on schema change
**Pattern**: `DevKeyDatabase.buildDatabase()` calls `fallbackToDestructiveMigration()`. Any change to Room entities (adding/removing columns, changing types, adding tables) triggers a full database wipe — all macros, learned words, clipboard history, and command apps are lost.
**Prevention**: Before changing any Room entity, write a proper `Migration(oldVersion, newVersion)` and add it via `.addMigrations()`. Test migration with both fresh install and upgrade scenarios.
**Ref**: @DevKeyDatabase.kt (buildDatabase method)

### [BUILD] 2026-02-23: /implement agents need broad Bash/Edit/Write permissions
**Pattern**: Background agents dispatched by the /implement orchestrator cannot prompt the user for tool permissions. They get blocked on Edit, Write, and Bash commands.
**Prevention**: Before running /implement, add `Edit`, `Write`, and `Bash(*)` patterns to `.claude/settings.local.json` permissions allow list.
**Ref**: @.claude/settings.local.json

### [UI] 2026-03-01: Main Activity is old Hacker's Keyboard setup wizard — BLOCKER
**Pattern**: The launcher activity (`.Main`) is the unmodified Hacker's Keyboard setup wizard. All text references "Hacker's Keyboard", buttons link to old Play Store/project page, UI is raw unstyled Android Views. First thing users see when opening the app.
**Prevention**: Replace with a modern Compose-based onboarding screen using DevKey branding.
**Ref**: @Main activity (legacy Java), audit: `.claude/logs/emulator-audit-wave1.md` BUG-01

### [UI] 2026-03-01: 123 toolbar button does not switch to symbols layout
**Pattern**: Tapping the "123" button on the keyboard toolbar has no effect. The keyboard stays on QWERTY. Users cannot access the symbols/numbers layout. Core keyboard function broken.
**Prevention**: Verify toolbar button callbacks are wired to `InputModeManager` or equivalent layout switching logic.
**Ref**: @DevKeyKeyboard.kt (ToolbarRow), audit: `.claude/logs/emulator-audit-wave1.md` BUG-02

### [UI] 2026-03-01: Dynamic keyboard height ignores user preference
**Pattern**: `KeyboardView.kt` hardcodes height as `screenHeightDp * 0.40`. The Settings page exposes a "Height (Portrait)" slider (currently 50%) via SharedPreferences, but the Compose keyboard doesn't read it. The two systems conflict.
**Prevention**: Read height percentage from preferences instead of hardcoding. Wire the Settings slider to the Compose keyboard's height calculation.
**Ref**: @KeyboardView.kt, @DevKeySettingsActivity, audit: `.claude/logs/emulator-audit-wave1.md` BUG-03
