---
name: QwertyLayout home row refactor (Phase 5.2)
description: buildHomeRow() was shared between FULL and COMPACT_DEV but Phase 5.2 required per-layout home rows; it was split into buildFullHomeRow() with the shared helper removed.
type: project
---

The original `QwertyLayout.kt` had a shared `buildHomeRow()` function used by both FULL and COMPACT_DEV. After Phase 5.2 the two layouts needed different home row long-press assignments (FULL keeps symbol long-press on s/d/f/g/h/j/k/l; COMPACT_DEV gets hacker shift-symbols per the dev template).

**Resolution:** `buildHomeRow()` was replaced with `buildFullHomeRow()` for the FULL layout. COMPACT_DEV and COMPACT now define their home rows inline in their respective builder functions.

**Why:** FULL and COMPACT_DEV home row long-press symbols diverge: FULL keeps `s→|`, `d→\`, `f→{`, etc. (existing), while COMPACT_DEV uses `s→\``, `d→-`, `f→_`, `g→=`, `h→+`, `j→{`, `k→}`, `l→|` (hacker template). A single shared builder cannot express both.

**How to apply:** If adding a fourth layout mode that shares the FULL home row, use `buildFullHomeRow()`. Do not re-introduce a shared `buildHomeRow()` without a mode parameter.
