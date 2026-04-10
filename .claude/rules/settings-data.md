---
paths:
  - "**/SettingsRepository.kt"
  - "**/data/db/**"
  - "**/data/export/**"
  - "**/feature/**/Repository.kt"
  - "**/ui/settings/**"
---

# Settings And Data Rules

## Settings Ownership

`SettingsRepository` is the settings source of truth.

- Add new settings there first.
- Do not add direct `SharedPreferences` reads in feature or UI code.
- Keep defaults safe and usable without extra user action.

## Database And Export Safety

- Room schema changes must be deliberate; this project still carries destructive
  migration risk.
- Do not rename columns, tables, or entities casually.
- Export/import changes must consider clipboard, macros, learned words, and
  command app data explicitly.
- Never log sensitive stored content during migrations, export, or import.

## Layering

- UI settings screens render and dispatch actions; they do not own persistence.
- Repositories own storage-facing behavior and should not leak storage details
  into Compose code.
