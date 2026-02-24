# Session 5 Implementation Plan: Settings, Export/Import, Polish & Testing

**Design Doc**: `docs/plans/2026-02-24-session5-settings-polish-design.md`
**Status**: Ready for implementation
**Base path**: `app/src/main/java/dev/devkey/keyboard/`

---

## Phase 1: SettingsRepository & kotlinx.serialization Setup

**Goal**: Create the SharedPreferences wrapper with Flow observation, add kotlinx.serialization dependency, and add new DevKey preference keys.

**Files to create**:

### 1.1 `data/repository/SettingsRepository.kt`
- Class `SettingsRepository(private val prefs: SharedPreferences)`:
  - Implements `SharedPreferences.OnSharedPreferenceChangeListener`
  - Typed getters:
    - `fun getBoolean(key: String, default: Boolean): Boolean`
    - `fun getInt(key: String, default: Int): Int`
    - `fun getFloat(key: String, default: Float): Float`
    - `fun getString(key: String, default: String): String`
  - Typed setters:
    - `fun setBoolean(key: String, value: Boolean)`
    - `fun setInt(key: String, value: Int)`
    - `fun setFloat(key: String, value: Float)`
    - `fun setString(key: String, value: String)`
  - Flow observation:
    - `fun observeBoolean(key: String, default: Boolean): Flow<Boolean>`
    - `fun observeString(key: String, default: String): Flow<String>`
    - Internal: `MutableSharedFlow<String>` emits key name on change, individual flows filter by key
  - Companion object with all preference key constants:
    ```kotlin
    // Legacy keys (match existing SharedPreferences keys)
    const val KEY_HEIGHT_PORTRAIT = "settings_height_portrait"
    const val KEY_HEIGHT_LANDSCAPE = "settings_height_landscape"
    const val KEY_KEYBOARD_MODE_PORTRAIT = "pref_keyboard_mode_portrait"
    const val KEY_KEYBOARD_MODE_LANDSCAPE = "pref_keyboard_mode_landscape"
    const val KEY_SUGGESTIONS_IN_LANDSCAPE = "suggestions_in_landscape"
    const val KEY_HINT_MODE = "pref_hint_mode"
    const val KEY_LABEL_SCALE = "pref_label_scale_v2"
    const val KEY_CANDIDATE_SCALE = "pref_candidate_scale"
    const val KEY_TOP_ROW_SCALE = "pref_top_row_scale"
    const val KEY_KEYBOARD_LAYOUT = "pref_keyboard_layout"
    const val KEY_RENDER_MODE = "pref_render_mode"
    const val KEY_AUTO_CAP = "auto_cap"
    const val KEY_CAPS_LOCK = "pref_caps_lock"
    const val KEY_SHIFT_LOCK_MODIFIERS = "pref_shift_lock_modifiers"
    const val KEY_CTRL_A_OVERRIDE = "pref_ctrl_a_override"
    const val KEY_CHORDING_CTRL = "pref_chording_ctrl_key"
    const val KEY_CHORDING_ALT = "pref_chording_alt_key"
    const val KEY_CHORDING_META = "pref_chording_meta_key"
    const val KEY_SLIDE_KEYS = "pref_slide_keys_int"
    const val KEY_SWIPE_UP = "pref_swipe_up"
    const val KEY_SWIPE_DOWN = "pref_swipe_down"
    const val KEY_SWIPE_LEFT = "pref_swipe_left"
    const val KEY_SWIPE_RIGHT = "pref_swipe_right"
    const val KEY_VOL_UP = "pref_vol_up"
    const val KEY_VOL_DOWN = "pref_vol_down"
    const val KEY_VIBRATE_ON = "vibrate_on"
    const val KEY_VIBRATE_LEN = "vibrate_len"
    const val KEY_SOUND_ON = "sound_on"
    const val KEY_CLICK_METHOD = "pref_click_method"
    const val KEY_CLICK_VOLUME = "pref_click_volume"
    const val KEY_POPUP_ON = "popup_on"
    const val KEY_QUICK_FIXES = "quick_fixes"
    const val KEY_SHOW_SUGGESTIONS = "show_suggestions"
    const val KEY_AUTO_COMPLETE = "auto_complete"
    const val KEY_SUGGESTED_PUNCTUATION = "pref_suggested_punctuation"
    const val KEY_LONG_PRESS_DURATION = "pref_long_press_duration"
    const val KEY_VOICE_MODE = "voice_mode"
    const val KEY_SETTINGS_KEY = "settings_key"

    // New DevKey keys
    const val KEY_COMPACT_MODE = "devkey_compact_mode"
    const val KEY_AUTOCORRECT_LEVEL = "devkey_autocorrect_level"
    const val KEY_MACRO_DISPLAY_MODE = "devkey_macro_display_mode"
    const val KEY_VOICE_MODEL = "devkey_voice_model"
    const val KEY_VOICE_AUTO_STOP_TIMEOUT = "devkey_voice_auto_stop_timeout"
    const val KEY_COMMAND_AUTO_DETECT = "devkey_command_auto_detect"
    ```
  - Register/unregister listener in `init`/`close()` methods

**Files to modify**:

### 1.2 `gradle/libs.versions.toml`
- Add kotlinx-serialization version and dependency:
  ```toml
  [versions]
  kotlinx-serialization = "1.6.3"

  [libraries]
  kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

  [plugins]
  kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
  ```

### 1.3 `app/build.gradle.kts`
- Apply serialization plugin: `alias(libs.plugins.kotlin.serialization)`
- Add dependency: `implementation(libs.kotlinx.serialization.json)`

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 2: Export/Import

**Goal**: Build the export and import managers with JSON serialization and SAF file I/O.

**Files to create**:

### 2.1 `data/export/BackupData.kt`
- `@Serializable` data classes:
  ```kotlin
  @Serializable
  data class DevKeyBackup(
      val schemaVersion: Int = 1,
      val exportedAt: String, // ISO 8601
      val appVersion: String,
      val macros: List<MacroExport> = emptyList(),
      val learnedWords: List<LearnedWordExport> = emptyList(),
      val commandApps: List<CommandAppExport> = emptyList(),
      val pinnedClipboard: List<ClipboardExport> = emptyList()
  )

  @Serializable
  data class MacroExport(
      val name: String,
      val keySequence: String, // JSON string from MacroEntity
      val createdAt: Long,
      val usageCount: Int
  )

  @Serializable
  data class LearnedWordExport(
      val word: String,
      val frequency: Int,
      val contextApp: String? = null,
      val isCommand: Boolean = false
  )

  @Serializable
  data class CommandAppExport(
      val packageName: String,
      val mode: String
  )

  @Serializable
  data class ClipboardExport(
      val content: String,
      val timestamp: Long
  )
  ```

### 2.2 `data/export/ExportManager.kt`
- Class `ExportManager(private val database: DevKeyDatabase)`:
  - `suspend fun export(): DevKeyBackup`:
    - Read all macros from `macroDao.getAllMacrosList()` (non-Flow version needed — add to DAO if missing)
    - Read all learned words from `learnedWordDao.getAllWords()`
    - Read all command apps from `commandAppDao.getAllCommandAppsList()`
    - Read pinned clipboard from `clipboardHistoryDao.getPinnedEntriesList()`
    - Map entities to export data classes
    - Return `DevKeyBackup` with current timestamp and app version
  - `fun serialize(backup: DevKeyBackup): String`:
    - `Json { prettyPrint = true }.encodeToString(backup)`
  - `suspend fun exportToUri(context: Context, uri: Uri)`:
    - Calls `export()` then `serialize()`
    - Writes JSON string to URI via `context.contentResolver.openOutputStream(uri)`

### 2.3 `data/export/ImportManager.kt`
- Class `ImportManager(private val database: DevKeyDatabase)`:
  - `enum class ConflictStrategy { REPLACE, MERGE, CANCEL }`
  - `data class ImportResult(val macros: Int, val words: Int, val commandApps: Int, val clipboard: Int)`
  - `fun deserialize(json: String): Result<DevKeyBackup>`:
    - Try `Json.decodeFromString<DevKeyBackup>(json)`
    - Catch `SerializationException` → return `Result.failure`
    - Validate `schemaVersion <= 1` — reject future versions
    - Return `Result.success`
  - `suspend fun import(backup: DevKeyBackup, strategy: ConflictStrategy): ImportResult`:
    - If `strategy == CANCEL`, return zero counts
    - If `strategy == REPLACE`:
      - Clear all tables (`macroDao.deleteAll()`, etc.)
      - Insert all imported data
    - If `strategy == MERGE`:
      - Macros: insert if no existing macro with same name, skip duplicates
      - Learned words: if word+contextApp exists, keep higher frequency; else insert
      - Command apps: upsert (imported value wins)
      - Pinned clipboard: insert if content doesn't already exist
    - Return counts of records imported
  - `suspend fun importFromUri(context: Context, uri: Uri, strategy: ConflictStrategy): Result<ImportResult>`:
    - Read JSON string from URI via `context.contentResolver.openInputStream(uri)`
    - Call `deserialize()` then `import()`

**Files to modify**:

### 2.4 DAOs — Add list query methods (if missing)
Check each DAO and add non-Flow query methods needed by ExportManager:
- `MacroDao`: `@Query("SELECT * FROM macros") suspend fun getAllMacrosList(): List<MacroEntity>`
- `LearnedWordDao`: `@Query("SELECT * FROM learned_words") suspend fun getAllWordsList(): List<LearnedWordEntity>`
- `CommandAppDao`: `@Query("SELECT * FROM command_apps") suspend fun getAllCommandAppsList(): List<CommandAppEntity>`
- `ClipboardHistoryDao`: `@Query("SELECT * FROM clipboard_history WHERE isPinned = 1") suspend fun getPinnedEntriesList(): List<ClipboardHistoryEntity>`
- Also add `deleteAll()` methods to each DAO for REPLACE strategy:
  - `@Query("DELETE FROM macros") suspend fun deleteAll()`
  - `@Query("DELETE FROM learned_words") suspend fun deleteAll()`
  - `@Query("DELETE FROM command_apps") suspend fun deleteAll()`
  - `@Query("DELETE FROM clipboard_history") suspend fun deleteAll()`

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 3: Compose Settings UI Components

**Goal**: Build the reusable Compose UI components for the settings screen.

**Files to create**:

### 3.1 `ui/settings/SettingsComponents.kt`
Reusable composables for all setting types:

- `@Composable fun SettingsCategory(title: String)`:
  - Bold text header, small top padding, 1px divider below
  - Color: `MaterialTheme.colorScheme.primary`

- `@Composable fun ToggleSetting(title: String, description: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit)`:
  - Row: label column (title + optional description) + Switch on right
  - Title: body1, Description: caption in grey

- `@Composable fun SliderSetting(title: String, value: Float, min: Float, max: Float, step: Float? = null, displayFormat: (Float) -> String, onValueChange: (Float) -> Unit)`:
  - Column: title row with current value display, Slider below
  - Value display right-aligned in title row

- `@Composable fun DropdownSetting(title: String, currentValue: String, options: List<Pair<String, String>>, onSelected: (String) -> Unit)`:
  - Row: title + current value display
  - Tap opens `AlertDialog` with radio button list
  - `options` is List of (value, displayLabel)

- `@Composable fun TextInputSetting(title: String, currentValue: String, onValueChange: (String) -> Unit)`:
  - Row: title + current value display
  - Tap opens `AlertDialog` with `TextField`

- `@Composable fun ButtonSetting(title: String, description: String? = null, onClick: () -> Unit)`:
  - Row: label column (title + optional description), clickable
  - No control on right — whole row is tappable

All components use `DevKeyTheme` colors (dark background, light text).

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 4: Settings Screen & Activity

**Goal**: Build the main settings screen with all categories and wire it to SharedPreferences via SettingsRepository.

**Files to create**:

### 4.1 `ui/settings/SettingsScreen.kt`
- `@Composable fun SettingsScreen(settingsRepository: SettingsRepository, onNavigateToMacroManager: () -> Unit, onNavigateToCommandApps: () -> Unit, onExport: () -> Unit, onImport: () -> Unit)`:
  - `LazyColumn` with category headers and settings
  - Read all current values from `settingsRepository`
  - On change, write back to `settingsRepository`
  - For dropdown settings, define options matching the legacy XML arrays:
    - Load entry/entryValues from resources where possible, or hardcode the known values

  Categories in order:
  1. **Keyboard View**: height sliders (portrait/landscape), keyboard mode dropdowns (portrait/landscape), compact mode toggle, suggestions in landscape toggle, hint mode dropdown, label scale slider, candidate scale slider, top row scale slider, keyboard layout dropdown, render mode dropdown
  2. **Key Behavior**: auto-cap toggle, caps lock toggle, shift lock modifiers toggle, Ctrl+A override dropdown, chording Ctrl/Alt/Meta dropdowns, slide keys dropdown
  3. **Actions**: swipe up/down/left/right dropdowns, volume up/down dropdowns
  4. **Feedback**: vibrate toggle, vibrate duration slider, sound toggle, click method dropdown, click volume slider, popup preview toggle
  5. **Prediction & Autocorrect**: quick fixes toggle, show suggestions toggle, auto-complete toggle (disabled if show suggestions off), suggested punctuation text input, autocorrect level dropdown (off/mild/aggressive)
  6. **Macros**: display mode dropdown (chips/grid), "Manage Macros" button → `onNavigateToMacroManager()`
  7. **Voice Input**: voice model dropdown (tiny/base), auto-stop timeout slider, "Download Base Model" button (shows download progress or "not yet available")
  8. **Command Mode**: auto-detect toggle, "Manage Pinned Apps" button → `onNavigateToCommandApps()`
  9. **Backup**: "Export Data" button → `onExport()`, "Import Data" button → `onImport()`
  10. **About**: version text (from `BuildConfig.VERSION_NAME`), "Built on Hacker's Keyboard" text, "GitHub" button (opens URL)

### 4.2 `ui/settings/DevKeySettingsActivity.kt`
- Class `DevKeySettingsActivity : ComponentActivity()`:
  - `onCreate()`:
    - Get SharedPreferences via `PreferenceManager.getDefaultSharedPreferences(this)`
    - Create `SettingsRepository(prefs)`
    - Create `ExportManager(database)` and `ImportManager(database)`
    - Set up SAF launchers:
      - `createDocumentLauncher = registerForActivityResult(CreateDocument("application/json"))` for export
      - `openDocumentLauncher = registerForActivityResult(OpenDocument())` for import
    - `setContent { DevKeyTheme { SettingsScreen(...) } }`
  - Export flow:
    - On "Export" button: launch `createDocumentLauncher` with suggested filename `devkey-backup-YYYY-MM-DD.json`
    - On result URI: `coroutineScope.launch { exportManager.exportToUri(context, uri) }` + show snackbar "Data exported"
  - Import flow:
    - On "Import" button: launch `openDocumentLauncher` with mime type `application/json`
    - On result URI: read and deserialize, show conflict resolution dialog (Replace/Merge/Cancel)
    - On user choice: `coroutineScope.launch { importManager.import(backup, strategy) }` + show snackbar with counts

### 4.3 `ui/settings/ImportConflictDialog.kt`
- `@Composable fun ImportConflictDialog(onStrategy: (ImportManager.ConflictStrategy) -> Unit, onDismiss: () -> Unit)`:
  - AlertDialog with title "Import Data"
  - Body: "How should conflicts be handled?"
  - Three buttons: "Replace All", "Merge", "Cancel"

**Files to modify**:

### 4.4 `app/src/main/AndroidManifest.xml`
- Replace `LatinIMESettings` activity declaration with `DevKeySettingsActivity`:
  ```xml
  <activity
      android:name=".ui.settings.DevKeySettingsActivity"
      android:label="@string/english_ime_settings"
      android:exported="true">
      <intent-filter>
          <action android:name="android.intent.action.MAIN" />
      </intent-filter>
  </activity>
  ```
- Keep the intent filter that the IME framework uses to launch settings

### 4.5 `ui/theme/DevKeyTheme.kt`
Add settings-specific theme constants:
- `settingsCategoryColor = Color(0xFF82B1FF)` — blue header text for categories
- `settingsDescriptionColor = Color(0xFF888888)` — grey description text
- `settingsDividerColor = Color(0xFF333333)` — subtle divider

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 5: Macro Manager & Command App Manager Screens

**Goal**: Build the sub-screens for managing macros and command mode apps.

**Files to create**:

### 5.1 `ui/settings/MacroManagerScreen.kt`
- `@Composable fun MacroManagerScreen(macroRepository: MacroRepository, onBack: () -> Unit)`:
  - Top bar with back arrow and title "Manage Macros"
  - `LazyColumn` listing all macros:
    - Each item: macro name, key combo text (from `MacroKeyComboText`), usage count
    - Swipe to delete with confirmation dialog
    - Tap to edit name (dialog with TextField)
  - FAB or button: "+ Record" (disabled — recording requires the keyboard to be active)
  - Empty state: "No macros saved. Record macros from the keyboard toolbar."

### 5.2 `ui/settings/CommandAppManagerScreen.kt`
- `@Composable fun CommandAppManagerScreen(repository: CommandModeRepository, onBack: () -> Unit)`:
  - Top bar with back arrow and title "Command Mode Apps"
  - `LazyColumn` listing all command app overrides:
    - Each item: package name, mode indicator (COMMAND/NORMAL)
    - Swipe to remove override
  - "Add App" button/FAB:
    - Opens dialog with TextField for package name input
    - Mode dropdown: Command / Normal
    - Save button
  - Info text at top: "Apps listed here override automatic terminal detection."
  - Built-in terminal apps shown separately (read-only info section listing the auto-detected packages)

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 6: Compact Mode

**Goal**: Implement the compact keyboard layout with number row hidden and remapped long-press.

**Files to modify**:

### 6.1 `ui/keyboard/QwertyLayout.kt`
- Add `compactMode: Boolean` parameter to the layout builder function
- When `compactMode = true`:
  - Remove the number row from the returned layout
  - Remap long-press on QWERTY row (Q-P):
    - Q: long-press `1` (was `!`)
    - W: long-press `2` (was `@`)
    - E: long-press `3` (was `#`)
    - R: long-press `4` (was `$`)
    - T: long-press `5` (was `%`)
    - Y: long-press `6` (was `^`)
    - U: long-press `7` (was `&`)
    - I: long-press `8` (was `*`)
    - O: long-press `9` (was `(`)
    - P: long-press `0` (was `)`)
  - Remap long-press on Shift → Esc (keycode 111)
  - Remap long-press on Backspace → Tab (keycode 61)
- When `compactMode = false`:
  - Return the existing 5-row layout unchanged

### 6.2 `ui/keyboard/DevKeyKeyboard.kt`
- Read compact mode from SharedPreferences:
  ```kotlin
  val context = LocalContext.current
  val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
  val compactMode = remember { mutableStateOf(prefs.getBoolean("devkey_compact_mode", false)) }
  ```
- Pass `compactMode.value` to `QwertyLayout` and `KeyboardView`
- Listen for preference changes to update `compactMode` live (register listener)

### 6.3 `ui/keyboard/KeyboardView.kt`
- Pass `compactMode` through to `QwertyLayout` calls if not already using layout data directly
- No visual changes needed — the layout data drives rendering

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 7: Performance Audit & Cleanup

**Goal**: Review existing Compose code for performance issues, add lifecycle cleanup, and general code cleanup.

**Files to modify**:

### 7.1 Compose Recomposition Audit
Review and optimize these files:
- `ui/keyboard/KeyboardView.kt` — ensure individual key press state changes don't recompose entire keyboard. Use `remember` for key data, `derivedStateOf` for modifier-dependent state.
- `ui/keyboard/KeyView.kt` — ensure `pointerInput` modifier uses stable keys
- `ui/keyboard/DevKeyKeyboard.kt` — ensure `remember` wraps expensive computations, prediction list updates are debounced
- `ui/suggestion/SuggestionBar.kt` — use `key()` for suggestion items in any list/row
- `ui/settings/SettingsScreen.kt` — use `key()` in LazyColumn items

### 7.2 IME Lifecycle Cleanup
- `core/LatinIME.java` or related:
  - In `onFinishInput()` or `onWindowHidden()`: call `VoiceInputEngine.release()` to free Whisper model memory
  - Ensure no coroutines outlive the IME window visibility
  - Verify `SessionDependencies` references are cleared when IME is destroyed

### 7.3 Resource Cleanup
- `feature/voice/VoiceInputEngine.kt`: verify `AudioRecord` is always released on cancel/stop/error
- `feature/clipboard/DevKeyClipboardManager.kt`: verify clipboard listener unregistered on destroy
- `data/repository/SettingsRepository.kt`: verify SharedPreferences listener unregistered in `close()`

### 7.4 General Cleanup
- Remove any remaining "Coming soon" toasts or placeholder text
- Remove `.gitkeep` files from directories that now have content
- Verify all imports are used (no dead imports in new Session 5 files)

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 8: Unit Tests

**Goal**: Write unit tests for new Session 5 code.

**Files to create**:

### 8.1 `app/src/test/java/dev/devkey/keyboard/data/export/ExportManagerTest.kt`
- Test `serialize()` produces valid JSON with schema version 1
- Test `export()` includes all tables (mock DAOs returning known data)
- Test pinned-only filter for clipboard (mock DAO returns only isPinned=true)
- Test empty database produces valid backup with empty arrays

### 8.2 `app/src/test/java/dev/devkey/keyboard/data/export/ImportManagerTest.kt`
- Test `deserialize()` with valid JSON → success
- Test `deserialize()` with malformed JSON → failure
- Test `deserialize()` with schema version 2 → failure (future version)
- Test `import()` with REPLACE strategy → clears existing, inserts new
- Test `import()` with MERGE strategy → deduplicates macros by name, words by word+contextApp, command apps by packageName
- Test `import()` with CANCEL → returns zero counts
- Test roundtrip: export → serialize → deserialize → import (REPLACE) → export again → data matches

### 8.3 `app/src/test/java/dev/devkey/keyboard/data/repository/SettingsRepositoryTest.kt`
- Test typed getters return defaults when key missing
- Test typed setters persist values
- Test `observeBoolean()` emits when preference changes
- Test `observeString()` emits when preference changes

### 8.4 `app/src/test/java/dev/devkey/keyboard/ui/keyboard/QwertyLayoutCompactTest.kt`
- Test `compactMode = true` produces 4 rows (no number row)
- Test `compactMode = true` maps Q long-press to `1`, W to `2`, ..., P to `0`
- Test `compactMode = true` maps Shift long-press to Esc keycode
- Test `compactMode = true` maps Backspace long-press to Tab keycode
- Test `compactMode = false` produces 5 rows with original long-press mapping

**Build gate**: `./gradlew test` must pass.

---

## Phase 9: Documentation & Final Cleanup

**Goal**: Update docs, write user guide, clean up plans, mark project v1 complete.

**Files to create**:

### 9.1 `docs/USER-GUIDE.md`
Brief practical guide:
- **Getting Started**: How to enable DevKey in Android Settings → System → Languages & Input → On-screen keyboard
- **Typing**: Long-press for secondary characters, hint labels visible on keys
- **Modifiers**: Shift (tap = one-shot, double-tap = caps lock), Ctrl (hold for shortcut mode), Alt
- **Macros**: Tap lightning bolt for quick access (chips or grid), record new macros, manage in Settings
- **Command Mode**: Auto-detects terminals, manual toggle via overflow menu, manages app list in Settings
- **Voice Input**: Tap mic, speak, auto-stops on silence
- **Compact Mode**: Settings → Keyboard View → Hide number row
- **Backup & Restore**: Settings → Backup → Export/Import
- **Settings**: Accessible via toolbar overflow → Settings, or Android Settings → keyboard settings

**Files to modify**:

### 9.2 `docs/ARCHITECTURE.md`
- Add `ui/settings/` section: SettingsScreen, SettingsComponents, DevKeySettingsActivity, MacroManagerScreen, CommandAppManagerScreen, ImportConflictDialog
- Add `data/export/` section: ExportManager, ImportManager, BackupData
- Add `data/repository/` section: SettingsRepository
- Note that Room `SettingsEntity`/`SettingsDao` are deprecated — SharedPreferences is the settings backend
- Update feature summary with compact mode and export/import

### 9.3 `CLAUDE.md` (project root `.claude/CLAUDE.md`)
- No structural changes needed — convention docs are stable

### 9.4 `.claude/autoload/_state.md`
- Update phase to "Session 5 Implemented — Settings, Export/Import, Polish & Testing"
- Update status: "v1 feature-complete"
- Add Session 11 entry with work summary
- Move oldest session from "Recent Sessions" if needed (keep max 5)

### 9.5 Plan cleanup
- Move `devkey-implementation-plan.md` to `plans/completed/`
- Move `session2-implementation-plan.md` to `plans/completed/`
- Move `session3-implementation-plan.md` to `plans/completed/`
- Move `session4-implementation-plan.md` to `plans/completed/`
- Keep `session5-implementation-plan.md` active until verified

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Dependency Graph

```
Phase 1 (SettingsRepository + Serialization Setup)
  ├── Phase 2 (Export/Import)
  │     └── Phase 4 (Settings Screen — needs export/import managers)
  ├── Phase 3 (Settings UI Components)
  │     └── Phase 4 (Settings Screen — needs components)
  └── Phase 6 (Compact Mode — needs new pref key)

Phase 4 (Settings Screen & Activity)
  └── Phase 5 (Macro Manager + Command App Manager sub-screens)

Phase 6 (Compact Mode) — independent of Phases 4-5

Phase 7 (Performance Audit) — independent, can run after Phase 6

Phase 8 (Unit Tests) — depends on Phases 1, 2, 6

Phase 9 (Documentation) — depends on all above
```

Parallel opportunities:
- Phases 2 and 3 can run in parallel (both depend only on Phase 1)
- Phase 6 can run in parallel with Phases 4-5 (independent feature)
- Phase 7 can run in parallel with Phase 5 or 8

---

## Files Summary

**New files (14)**:
1. `data/repository/SettingsRepository.kt` — SharedPreferences wrapper with Flow
2. `data/export/BackupData.kt` — Serializable backup data classes
3. `data/export/ExportManager.kt` — Export all Room data to JSON
4. `data/export/ImportManager.kt` — Import JSON with conflict resolution
5. `ui/settings/SettingsComponents.kt` — Reusable Compose setting widgets
6. `ui/settings/SettingsScreen.kt` — Main settings screen
7. `ui/settings/DevKeySettingsActivity.kt` — Compose settings Activity
8. `ui/settings/ImportConflictDialog.kt` — Import conflict resolution dialog
9. `ui/settings/MacroManagerScreen.kt` — Macro edit/delete list
10. `ui/settings/CommandAppManagerScreen.kt` — Command app management
11. `docs/USER-GUIDE.md` — End-user guide
12-15. Unit test files (4): ExportManagerTest, ImportManagerTest, SettingsRepositoryTest, QwertyLayoutCompactTest

**Modified files (9)**:
1. `gradle/libs.versions.toml` — kotlinx.serialization dependency
2. `app/build.gradle.kts` — serialization plugin + dependency
3. `app/src/main/AndroidManifest.xml` — Replace LatinIMESettings with DevKeySettingsActivity
4. `ui/keyboard/QwertyLayout.kt` — Compact mode layout switching
5. `ui/keyboard/DevKeyKeyboard.kt` — Read compact mode pref
6. `ui/keyboard/KeyboardView.kt` — Pass compact mode through
7. `ui/theme/DevKeyTheme.kt` — Settings theme constants
8. `docs/ARCHITECTURE.md` — Add settings, export, repository sections
9. `.claude/autoload/_state.md` — Mark v1 complete
10. DAO files (4): Add `deleteAll()` and list query methods

**Moved files**:
- `plans/*.md` → `plans/completed/` (4 completed plans)

---

## Known Risks

- **Legacy SharedPreferences key alignment**: The Compose settings UI must use exactly the same key strings as the legacy code reads. Any mismatch means the setting won't take effect. The `SettingsRepository` constants must match the XML preference keys exactly.
- **Dropdown option values**: Legacy dropdown settings use XML-defined `entryValues` arrays. The Compose dropdowns must present the same values. Read from string resources where possible, or hardcode matching values.
- **AndroidManifest settings intent**: The IME framework launches settings via an intent filter. Replacing the activity must preserve the same intent filter so Android can find the settings screen.
- **Room DAO additions**: Adding `deleteAll()` and list query methods to existing DAOs should be safe — they're additive. No schema migration needed.
- **kotlinx.serialization + Kotlin 2.0**: The serialization compiler plugin must match the Kotlin version. Using `version.ref = "kotlin"` for the plugin version ensures alignment.
- **Compact mode layout reactivity**: Changing compact mode in settings while the keyboard is visible should update the layout immediately. The `SharedPreferences.OnSharedPreferenceChangeListener` + Compose state should handle this, but needs testing.
