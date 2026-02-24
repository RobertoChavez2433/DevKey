# Keyboard Layout Fix — Implementation Plan

**Design**: `docs/plans/2026-02-24-keyboard-layout-fix-design.md`

## Phase 1: Remove SuggestionBar

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`

1. Remove the `SuggestionBar` composable call from `KeyboardMode.Normal` (lines ~179-185)
2. Remove the `SuggestionBar` composable call from `KeyboardMode.MacroGrid` (lines ~198-203)
3. Remove the `SuggestionBar` composable call from `KeyboardMode.Clipboard` (lines ~233-238)
4. Remove unused state variables: `isSuggestionCollapsed`, `currentWord`, `predictions`, `suggestionStrings`
5. Remove unused imports: `SuggestionBar`, `PredictionResult`, `PredictionEngine`, `AutocorrectEngine`, `DictionaryProvider`, `LearningEngine`, `InputMode` (if only used for predictions)
6. Remove the `LaunchedEffect` and `SideEffect` blocks for prediction tracking
7. Remove the word-tracking logic in `onKeyAction` (the `if (code == ' '.code ...)` block that calls `learningEngine.onWordCommitted`)

**Note**: Keep `commandModeDetector` and `inputMode` — they're used by `ToolbarRow` for CMD badge.

## Phase 2: Remove Duplicate Shift from Bottom Row

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/QwertyLayout.kt`

1. In `buildLayout()` `bottomRow`: remove the first `KeyData` (Shift, weight 1.2f) from the keys list
2. In `buildCompactLayout()` `bottomRow`: remove the first `KeyData` (Shift, weight 1.2f) from the keys list
3. Bottom row becomes: Ctrl (1.2), Alt (1.2), Space (5.0), Left (1.0), Up (1.0), Down (1.0), Right (1.0), Enter (1.5) — 8 keys

## Phase 3: Dynamic Height for KeyboardView

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyboardView.kt`

1. Import `LocalConfiguration` from `androidx.compose.ui.platform`
2. Read `screenHeightDp` from `LocalConfiguration.current.screenHeightDp`
3. Calculate key area height: `(screenHeightDp * 0.40).dp - DevKeyTheme.toolbarHeight`
4. Set the Column modifier to `Modifier.fillMaxWidth().height(keyAreaHeight).background(...).padding(...)`
5. Keep `verticalArrangement = Arrangement.spacedBy(4.dp)`

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyRow.kt`

1. Change `KeyRow` to accept a `modifier: Modifier = Modifier` parameter
2. Apply this modifier to the `Row` (so the parent can pass `Modifier.weight(1f)`)
3. Remove `Modifier.height(row.rowHeight)` from the Row

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyboardView.kt` (continued)

4. Pass `modifier = Modifier.weight(1f)` to each `KeyRow` call

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt`

5. Remove `rowHeight` field from `KeyRowData` (or keep with a deprecated annotation — removing is cleaner since nothing else uses per-row height)

## Phase 4: Cleanup

**File**: `app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt`

1. Remove `suggestionBarHeight` (no longer used)
2. Remove `rowHeight` (no longer used — dynamic now)
3. Keep `suggestionTextSize`, `suggestionText` color only if `SuggestionBar.kt` is kept as a file (it is — just not rendered). Actually, leave them — the file still exists, just isn't called.

## Verification

1. Build: `./gradlew assembleDebug` — must pass
2. Tests: `./gradlew test` — must pass (28 tests)
3. Visual: Install on emulator, verify keys are taller, bottom row fits, no label clipping
