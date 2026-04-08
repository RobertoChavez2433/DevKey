# Agent Memory — Compose UI Agent

## Patterns Discovered
- Phase 5.4: Multi-char popup gesture uses `awaitEachGesture` + `awaitFirstDown` + `awaitPointerEvent(PointerEventPass.Main)` loop. This gives full pointer-move access that `detectTapGestures` does not provide. The long-press coroutine sets `popupCodes` state; the event loop reads it to compute `popupActiveIndex` from finger X vs popup left edge (composable-local coords, centered over key).
- Popup is placed inside the `Box` content (not outside), using `Popup(alignment = TopStart, offset = IntOffset(...))` anchored relative to the Box. The y-offset is negative (upward) based on estimated popup height + gap.
- Candidate index tracking formula: `((fingerX - popupLeftEdgePx) / cellWidthPx).toInt().coerceIn(0, codes.size-1)` where `popupLeftEdgePx = (keySize.width - totalPopupWidthPx) / 2f`.
- Phase 6 tokenization: `0.dp` structural values (e.g. collapsed container height) must also be tokens — `DevKeyTheme.collapsedHeight = 0.dp` was added for `SuggestionBar`. Never leave even zero values as literals.

## Gotchas & Quirks
- `buildHomeRow()` in QwertyLayout was shared between FULL and COMPACT_DEV but was split in Phase 5.2 because the two layouts need different home row long-press symbols. Now FULL uses `buildFullHomeRow()`; COMPACT_DEV defines its home row inline. See [project_qwerty_layout_structure.md](project_qwerty_layout_structure.md).
- Phase 6: `keyBgSpecial = keyBg` (aliased, not a distinct color). SwiftKey COMPACT dark shows all key types at the same fill. Teal modifier tokens (modBgShift, modBgEnter, etc.) remain unchanged — they are intentional teal flavor, not the COMPACT dark surface.
- `KeyboardView.kt:46` has `(screenHeightDp * heightPercent).dp` — this is a runtime computed Dp from LocalConfiguration, NOT a hardcoded literal. Not a token violation.

## Architectural Decisions
- Phase 5 option A: COMPACT letter keys carry the same long-press content as FULL (digit long-press + accented vowel popups), overriding the KeyData.kt:22 design comment. User-escalated decision.
- Phase 6: COMPACT long-press retuned to SwiftKey shift-symbol set. Vowels (a, e, i, o, u) keep their accent longPressCodes (richer than SwiftKey single-char). `y` loses ý/ÿ popup, gets `]`. Z-row corrected from cropped image re-read: z→_, x→$, c→", v→', b→:, n→;, m→/ (v and b were previously wrong placeholder values 4/5).

## Frequently Referenced Files
| File | Notes |
|------|-------|
| DevKeyKeyboard.kt | Main Compose keyboard container |
| KeyView.kt | Individual key rendering + touch |
| KeyboardActionBridge.kt | Compose → LatinIME bridge |
| ComposeKeyboardViewFactory.kt | Lifecycle-sharing factory |
| QwertyLayout.kt | All three layout builders; buildSpaceRow() shared; buildFullHomeRow() for FULL only; COMPACT retuned Phase 6 |
| SymbolsLayout.kt | Phase 5.3 complete: all template long-press populated; =key absent from layout (skipped); all chars used \u-escape form |
| DevKeyTheme.kt | Phase 6 full tokenization: all dp/sp/Color literals in ui/ moved here. 180+ named tokens. Dark palette retuned. keyBgSpecial=keyBg. collapsedHeight=0.dp added. |
