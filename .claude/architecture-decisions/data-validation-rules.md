# Data Validation Rules

Shared validation rules enforced across all agents and implementation work.

## KeyCode Values

Android keycode constants come from `android.view.KeyEvent`. Rules:
- Always reference symbolic constants (`KeyEvent.KEYCODE_A`, etc.) rather than
  integer literals.
- Verify unknown keycodes against the official Android SDK documentation or
  `android.view.KeyEvent` source — do NOT guess integer values.
- Negative keycodes are DevKey-defined special actions (not Android keycodes):
  - `KeyCodes.SYMBOLS = -2` (alias: `Keyboard.KEYCODE_MODE_CHANGE`)
  - Other negative values: inspect `KeyCodes.kt` before assuming.

## ASCII to Keycode Mapping

The `asciiToKeyCode` table is an `IntArray(128)` mapping ASCII character codes
(0–127) to Android `KEYCODE_*` integers. This table is the authoritative source
for key synthesis.

Validation rules:
- Any Ctrl+letter synthesis MUST look up the letter's ASCII code in this table.
- The table covers 0–127 only; characters outside this range are not supported
  via the ASCII path.
- Index 0–31: control characters (DEL, ESC, TAB, etc.)
- Index 32–127: printable ASCII (space, digits, letters, symbols)
- Do NOT add entries for non-ASCII characters (128+); use a different mechanism.

## Build Requirements

Before submitting any implementation, verify:

| Check | Requirement |
|-------|-------------|
| `./gradlew assembleDebug` | Must succeed with 0 errors |
| `./gradlew test` | All 361 unit tests must pass |
| Min SDK | 26 — no API calls below API 26 without compat wrapper |
| No GlobalScope | Search for `GlobalScope` before committing |
| No hardcoded dp values for rows | Use weight ratios |
| BinaryDictionary untouched | Class name, package, language unchanged |

## Room Database Schema

- Database: `DevKeyDatabase`, version 2
- 4 tables: `macros`, `learned_words`, `clipboard_history`, `command_apps`
- Migration strategy: `fallbackToDestructiveMigration()` — schema changes wipe
  user data.

Validation rules:
- Any schema change (add column, add table, change type) MUST be accompanied by
  a migration that preserves data, OR the destructive migration must be
  explicitly acceptable for the change.
- Do NOT change column names or types without a migration.
- Version number must be incremented for every schema change.

## Coroutine Dispatcher Rules

| Work type | Correct dispatcher |
|-----------|-------------------|
| Database (Room) | `Dispatchers.IO` |
| CPU-bound (dictionary lookup) | `Dispatchers.Default` |
| UI state updates, StateFlow | `Dispatchers.Main` |
| Key event handling in IME | Main thread (no dispatcher needed) |

Using `Dispatchers.Main` for database or `Dispatchers.IO` for UI updates is a
validation failure.

## Settings Validation

`SettingsRepository` is the single source of truth for all user preferences.
Validation rules:
- New settings must be added to `SettingsRepository` only.
- No companion objects, singletons, or global variables for settings.
- Settings must have sensible defaults that work without any user action.
- Boolean settings: default to the safer/less-intrusive option.

## ProGuard Keep Rules

Any class with JNI native methods, reflection-based access, or serialization
(Room entities, `@Serializable`) must have a corresponding ProGuard keep rule.

Classes that currently require keep rules:
- `org.pocketworkstation.pckeyboard.BinaryDictionary` (JNI)
- All `@Entity` Room classes
- All `@Serializable` data classes used for JSON export/import
