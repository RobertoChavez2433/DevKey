# Long-Press Expectations

Per-mode expectation files for the `long-press-coverage` test flow
(`tools/e2e/tests/test_long_press.py`). Each file is a JSON list of entries:

```json
{"label": "<key label>", "lp_codes": [<int>, ...]}
```

Where:

- `label` is the key's `primaryLabel` as emitted by `KeyMapGenerator` / matched in
  the driver's key map.
- `lp_codes` is the list of acceptable long-press codes for that key. A key with
  a single-char `longPressCode` becomes a one-element list; a key with
  `longPressCodes = listOf(...)` enumerates all accented/popup codes. Any one
  of the listed codes satisfies the assertion — the test only asserts that the
  fired `lp_code` is a member of this set, matching the actual per-mode
  `QwertyLayout.kt` / `SymbolsLayout.kt` definitions.

Keys with no long-press are omitted entirely. If a new key gains a long-press
in source, add it here in the same pass — this file is authored by hand because
jcodemunch's Kotlin resolver cannot statically enumerate Compose object-literal
`KeyData(...)` calls.

## Files

| File | Mode | Source |
|------|------|--------|
| `full.json` | `LayoutMode.FULL` | `QwertyLayout.buildFullLayout()` |
| `compact.json` | `LayoutMode.COMPACT` | `QwertyLayout.buildCompactLayout()` |
| `compact_dev.json` | `LayoutMode.COMPACT_DEV` | `QwertyLayout.buildCompactDevLayout()` |
| `symbols.json` | Symbols layer | `SymbolsLayout.buildLayout()` |

## Regeneration

When `QwertyLayout.kt` or `SymbolsLayout.kt` changes, re-read the source and
hand-update the affected expectation files. This is the long-press data capture
step of Phase 1 sub-phase 1.5 / Phase 4 sub-phase 4.3.
