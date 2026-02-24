# Session 5 Design: Settings, Export/Import, Polish & Testing

**Date**: 2026-02-24
**Status**: Approved
**Scope**: Settings hub, export/import, compact mode, performance optimization, unit tests, documentation

---

## 1. Settings Hub

### Architecture

`DevKeySettingsActivity` — a Compose Activity replacing the legacy `LatinIMESettings` PreferenceActivity. Single scrollable screen with category headers.

`SettingsRepository` — wraps `SharedPreferences` with:
- Typed getters: `getInt()`, `getBoolean()`, `getString()`, `getFloat()`
- `Flow<T>` observation via `SharedPreferences.OnSharedPreferenceChangeListener`
- Constants for all preference keys (single source of truth for key strings)
- New DevKey keys added here (macro display mode, voice settings, command mode, etc.)

SharedPreferences is the single storage backend. The Room `SettingsEntity` table is dropped.

### Categories & Settings

**1. Keyboard View**
- Keyboard height portrait (slider 15-75%)
- Keyboard height landscape (slider 15-75%)
- Keyboard mode portrait (dropdown: full/compact)
- Keyboard mode landscape (dropdown: full/compact)
- Compact mode — hide number row (toggle, new)
- Suggestions in landscape (toggle)
- Hint mode (dropdown)
- Label scale (slider 50-200%)
- Candidate scale (slider 50-800%)
- Top row scale (slider 50-100%)
- Keyboard layout (dropdown)
- Render mode (dropdown)

**2. Key Behavior**
- Auto-capitalize (toggle)
- Caps lock (toggle)
- Shift lock modifiers (toggle)
- Ctrl+A override (dropdown)
- Ctrl/Alt/Meta chording keys (3 dropdowns)
- Slide keys (dropdown)

**3. Actions**
- Swipe up/down/left/right (4 dropdowns)
- Volume up/down (2 dropdowns)

**4. Feedback**
- Vibrate on keypress (toggle)
- Vibrate duration (slider 5-200ms)
- Sound on keypress (toggle)
- Click method (dropdown)
- Click volume (slider 0-100%)
- Key popup preview (toggle)

**5. Prediction & Autocorrect**
- Quick fixes (toggle)
- Show suggestions (toggle)
- Auto-complete (toggle, depends on show suggestions)
- Suggested punctuation (text input)
- Autocorrect aggressiveness (dropdown: off/mild/aggressive, new)

**6. Macros**
- Default view (dropdown: chips/grid, new)
- Manage macros — opens macro list with edit/delete/reorder (new)

**7. Voice Input**
- Voice model (dropdown: tiny/base, new)
- Auto-stop timeout (slider, new)
- Download base model button (shows download progress, new)

**8. Command Mode**
- Auto-detect terminal apps (toggle, new)
- Manage pinned apps — opens app list with add/remove (new)

**9. Backup**
- Export data (button — SAF file picker)
- Import data (button — SAF file picker — conflict resolution dialog)

**10. About**
- Version number
- "Built on Hacker's Keyboard" credit
- GitHub link

### Dropped Legacy Settings

The following legacy settings are removed (from the "Other" category):
- Recorrection
- Fullscreen override
- Force keyboard on
- Keyboard notification
- ConnectBot tab hack
- Touch position debug
- Input connection info

### Compose UI Components

- `SettingsScreen` — root composable, LazyColumn with category headers
- `SettingsCategory` — bold header text with divider
- `ToggleSetting` — label + description + Switch
- `SliderSetting` — label + value display + Slider
- `DropdownSetting` — label + current value, tap opens dialog with radio options
- `TextInputSetting` — label + current value, tap opens edit dialog
- `ButtonSetting` — label + description, tap triggers action
- `MacroManagerScreen` — separate screen for macro list management
- `CommandAppManagerScreen` — separate screen for pinned app management

---

## 2. Export/Import

### Data Classes

`DevKeyBackup` — the export container:
```
schema_version: Int (starts at 1)
exported_at: String (ISO 8601 timestamp)
app_version: String (versionName from BuildConfig)
macros: List<MacroExport> (name, keySequence, createdAt, usageCount)
learned_words: List<LearnedWordExport> (word, frequency, contextApp, isCommand)
command_apps: List<CommandAppExport> (packageName, mode)
pinned_clipboard: List<ClipboardExport> (content, timestamp)
```

### ExportManager

- Reads all Room tables (MacroDao, LearnedWordDao, CommandAppDao, ClipboardHistoryDao — pinned only)
- Serializes to `DevKeyBackup` via kotlinx.serialization
- Writes via SAF — user picks location, suggested filename: `devkey-backup-YYYY-MM-DD.json`
- Returns success/failure result

### ImportManager

- Reads JSON file via SAF file picker
- Validates `schema_version` — reject if higher than current
- Conflict resolution dialog with 3 options:
  - **Replace** — clear existing data, insert imported data
  - **Merge** — combine without duplicates (macros matched by name, words by word+contextApp, command apps by packageName)
  - **Cancel** — abort import
- Returns `ImportResult` with counts (macros imported, words imported, etc.)
- Shows summary snackbar after import

### Serialization

kotlinx.serialization (new dependency). Export data classes annotated with `@Serializable`.

### Error Handling

- Malformed JSON — show error dialog, don't crash
- Missing fields — use defaults (backward compat for future schema versions)
- Empty file — show "No data found" message

---

## 3. Compact Mode

### Behavior

When "Hide number row" is enabled in settings:
- Number row (Esc, 1-9, Tab) is removed from the layout
- Numbers 1-0 become long-press on Q through P (replacing !, @, #, etc.)
- Esc becomes long-press on left Shift
- Tab becomes long-press on Backspace
- Displaced symbols (!, @, #, etc.) remain accessible via the symbols (123) layer

### Layout Switching

`QwertyLayout.kt` gets a `compactMode: Boolean` parameter:
- `false` — current 5-row layout (number row + QWERTY + home + Z + bottom)
- `true` — 4-row layout (QWERTY + home + Z + bottom) with remapped long-press

Reads compact mode preference from SharedPreferences. Change takes effect immediately via Compose recomposition.

### Long-Press Remap Table (Compact Mode)

```
Q->1  W->2  E->3  R->4  T->5  Y->6  U->7  I->8  O->9  P->0
Shift (long-press) -> Esc
Backspace (long-press) -> Tab
```

### Portrait vs Landscape

Compact mode setting is independent of keyboard mode portrait/landscape. User can have compact in portrait but full in landscape (controlled by separate keyboard mode dropdowns).

---

## 4. Performance Optimization

### Targets

- Keyboard rendering: <16ms frame time (60fps)
- Dictionary prediction: <50ms per query
- Whisper inference: <2s for 5s of audio
- Memory: <50MB RSS in normal use
- Battery: zero background drain when keyboard not visible

### Optimizations

**Compose rendering:**
- Use `remember` and `derivedStateOf` to prevent unnecessary recompositions
- Key press state changes should only recompose the pressed key, not the entire keyboard
- Use `key()` in LazyColumn for settings list stability

**Model loading:**
- Whisper model: lazy-load on first mic tap (already implemented)
- Dictionary: already loaded via JNI on IME start — no change needed

**Memory:**
- Release Whisper model when keyboard is hidden (`onFinishInput`)
- Clipboard history: enforce max entries limit (configurable, default 50)
- Learned words: no cap needed (lightweight rows)

**Battery:**
- Ensure no coroutines or listeners run when IME is not visible
- Cancel audio recording scope on `onFinishInput`
- No periodic background work — everything is event-driven

### Implementation

Code review pass across existing code, not a separate profiling phase:
1. Review Compose `@Composable` functions for unnecessary recomposition
2. Add `remember`/`derivedStateOf` where needed
3. Verify lazy model loading
4. Add resource cleanup in IME lifecycle callbacks

---

## 5. Testing

### Unit Tests (New Code Only)

**Export/Import:**
- `ExportManagerTest` — export produces valid JSON with correct schema version, all data tables included, pinned-only clipboard filter works
- `ImportManagerTest` — valid JSON imports correctly, merge deduplicates by key fields (macro name, word+contextApp, packageName), replace clears existing data, malformed JSON returns error, unknown schema version rejected
- Roundtrip test — export then import produces identical data

**SettingsRepository:**
- Typed getters return correct defaults when key missing
- `Flow` emits updates when SharedPreference changes
- New DevKey keys read/write correctly

**Compact Mode:**
- `QwertyLayout` with `compactMode=true` produces 4 rows (no number row)
- Long-press map remaps Q-P to 1-0
- Shift long-press returns Esc keycode, Backspace long-press returns Tab keycode
- `compactMode=false` produces original 5-row layout unchanged

### Not In Scope

- Compose UI rendering (requires instrumented tests / device)
- Legacy settings migration (no migration — SharedPrefs stays)
- Performance benchmarks (code review pass, not automated profiling)
- Whisper model inference (model files not bundled yet)

---

## 6. Documentation

### Updates to Existing Docs

**`docs/ARCHITECTURE.md`:**
- Add `ui/settings/` package
- Add `data/export/` package
- Add `data/repository/SettingsRepository`
- Note: Room `SettingsEntity` / `SettingsDao` deprecated
- Update feature list with compact mode

**`CLAUDE.md`:**
- Update session status (Sessions 1-5 complete)
- Note SettingsRepository as the settings access pattern

### New File

**`docs/USER-GUIDE.md`:**
Brief user guide covering:
- How to enable DevKey as your keyboard
- Typing basics (long-press hints, modifiers)
- Macros (record, replay, chips vs grid)
- Command mode (auto-detect, manual toggle)
- Voice input (tap mic, speak, auto-stop)
- Compact mode (toggle in settings)
- Backup/restore (export, import, what's included)
- Settings overview

### Cleanup

- Move completed plans to `plans/completed/`
- Update `_state.md` — mark project as v1 feature-complete

---

## Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Settings UI | Full Compose replacement | Clean break, consistent with rest of app |
| Settings storage | SharedPreferences only | Legacy code reads SharedPrefs directly, no migration needed |
| Settings navigation | Flat list with categories | Simple, everything visible, standard keyboard settings pattern |
| Legacy prefs | Keep all except "Other" category | User cherry-picked useful ones |
| Export scope | User data only (no settings) | Settings are device-specific, keeps backups small and portable |
| Export format | Single JSON file with schema version | Simple, human-readable, easy to validate |
| Serialization | kotlinx.serialization | Clean data class annotation, better than manual Gson/Moshi |
| Compact mode Esc/Tab | Long-press Shift (Esc), Backspace (Tab) | Natural positions near original row |
| Testing scope | Unit tests for new code only | Good coverage without instrumented test overhead |
| About section | Version + open-source link | Simple and sufficient |
