# Keyboard Layout Fix Design

**Date**: 2026-02-24
**Problem**: Keys render too small, bottom row labels overflow/clip, rows are vertically compressed.

## Root Cause

1. Fixed 48dp row height is too short for a 5-row power keyboard on portrait phones.
2. SuggestionBar consumes ~40dp of vertical space for a feature the user doesn't use.
3. Bottom row has 9 keys (including a duplicate Shift) crammed into one row.

## Changes

### 1. Remove SuggestionBar

Delete all `SuggestionBar` usages from `DevKeyKeyboard.kt`. The prediction/suggestion feature is unused. This frees ~40dp of vertical space for keys.

Also remove `SuggestionBar` from the Clipboard and MacroGrid modes where it's shown above the panel.

### 2. Remove Duplicate Shift from Bottom Row

The Z-row already has a Shift key. The bottom row's second Shift is redundant. Remove it from both `QwertyLayout.layout` and `QwertyLayout.compactLayout`.

Bottom row becomes 8 keys: Ctrl, Alt, Space, 4 arrows, Enter.

### 3. Dynamic Keyboard Height (Percentage of Screen)

Replace fixed `rowHeight = 48.dp` with dynamic height calculation:

- Total keyboard height = `screenHeightDp * 0.40` (40% of screen)
- Subtract toolbar height (36dp) from the total
- Key rows use `Modifier.weight(1f)` to evenly divide the remaining space

Implementation:
- `KeyboardView` Column gets `Modifier.height(calculatedKeyAreaHeight)`
- `KeyRow` switches from `Modifier.height(row.rowHeight)` to `Modifier.weight(1f)`
- Height is read from `LocalConfiguration.current.screenHeightDp`
- The `rowHeight` field in `KeyRowData` becomes unused and can be removed

## Files Modified

| File | Change |
|------|--------|
| `DevKeyKeyboard.kt` | Remove all SuggestionBar calls |
| `QwertyLayout.kt` | Remove duplicate Shift from bottom row (both layouts) |
| `KeyboardView.kt` | Add dynamic height calculation, use weight-based rows |
| `KeyRow.kt` | Accept optional `Modifier` param or use weight from parent |
| `KeyData.kt` | Remove `rowHeight` default from `KeyRowData` |
| `DevKeyTheme.kt` | Remove `suggestionBarHeight` (unused after change) |
