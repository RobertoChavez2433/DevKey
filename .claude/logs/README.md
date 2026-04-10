# Logs

Cold storage for archived state and test artifacts.

> **Defects live on GitHub Issues**, not in this directory. Query with:
> `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open`

## Rotation Rules
- **State archive**: Max 5 recent sessions in `_state.md`. Overflow moves to `state-archive.md`.

## Files
- `state-archive.md` — Archived session entries (overflow from `_state.md`)
- `defects-archive-final.md` — Historical snapshot of defects at GitHub Issues migration time. NOT rotated, NOT updated. New defects go to GitHub Issues.
- `emulator-audit-wave1.md` — Wave 1 emulator audit report (8 bugs found, all fixed Sessions 16-17)
