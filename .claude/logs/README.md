# Logs

Cold storage for archived state and defects.

## Rotation Rules
- **State archive**: Max 5 recent sessions in `_state.md`. Overflow moves to `state-archive.md`.
- **Defects archive**: Max 7 active defects in `_defects.md`. Overflow moves to `defects-archive.md`.

## Files
- `state-archive.md` — Archived session entries
- `defects-archive.md` — Archived/resolved defect patterns
