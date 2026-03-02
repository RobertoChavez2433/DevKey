# Layout Redesign Implementation Plan

**Date**: 2026-03-01
**Design Doc**: `docs/plans/2026-03-01-layout-redesign-design.md`
**Status**: Ready for implementation
**Adversarial Review**: 15 issues found across 2 review passes, all incorporated below

## Overview

Implement Layout #8 Hybrid: 6-row full layout with thin utility row, 3 keyboard modes (Compact, Compact Dev, Full), smart ⌫/Esc key, teal monochrome modifier theme, and smart app detection foundation.

## File Change Matrix

### Must Change (17 files)

| File | Path | Change |
|------|------|--------|
| `KeyData.kt` | `ui/keyboard/` | Add KeyType.UTILITY, KeyType.TOGGLE, LayoutMode enum, new KeyCodes (DELETE=-5, SYMBOLS=-201, EMOJI=-300) |
| `QwertyLayout.kt` | `ui/keyboard/` | Rewrite: 3 layout modes, new row structure, utility row. API: `getLayout(mode: LayoutMode)` |
| `SymbolsLayout.kt` | `ui/keyboard/` | Update bottom row: ABC ☺ , Space . Enter |
| `DevKeyTheme.kt` | `ui/theme/` | **NOTE: actual path is ui/theme/ not ui/keyboard/**. Replace theme with teal monochrome token system |
| `KeyView.kt` | `ui/keyboard/` | Smart ⌫/Esc logic, themed modifier colors, animation tokens. Accept `InputConnection?` parameter for smart key |
| `KeyRow.kt` | `ui/keyboard/` | Variable row height, update `Arrangement.spacedBy(2.dp)` → `DevKeyTheme.keyGap` |
| `KeyboardView.kt` | `ui/keyboard/` | 6-row rendering, per-row height tokens, update vertical `spacedBy(2.dp)` → theme token. Keep percentage height system but distribute rows by token ratios |
| `DevKeyKeyboard.kt` | `ui/keyboard/` | Mode switching, layout selection. Update `getLayout(compactMode)` → `getLayout(LayoutMode)`. Handle SYMBOLS/EMOJI keycodes. Pass InputConnection to KeyView for smart ⌫/Esc |
| `KeyboardActionBridge.kt` | `core/` | Smart ⌫/Esc decision method using InputConnection |
| `KeyBoundsCalculator.kt` | `ui/keyboard/` | Update gap 2dp→4dp, padding, support 6 rows, per-row heights |
| `KeyMapGenerator.kt` | `debug/` | **NOTE: actual path is debug/ not ui/keyboard/**. Update KEY_GAP_DP=2f→4f, ROW_GAP_DP=2f→4f, 6-row layout, new key labels |
| `SettingsSubScreens.kt` | `ui/settings/` | **ADDED per review #13**: Migrate compact mode boolean toggle → 3-option LayoutMode selector |
| `SettingsRepository.kt` | `ui/settings/` | **ADDED per review #13**: Add KEY_LAYOUT_MODE string pref, migrate KEY_COMPACT_MODE boolean |
| `QwertyLayoutFullTest.kt` | test `ui/keyboard/` | Rewrite for 6-row Full layout |
| `QwertyLayoutCompactTest.kt` | test `ui/keyboard/` | Rewrite for Compact + Compact Dev modes |
| `SymbolsLayoutTest.kt` | test `ui/keyboard/` | Update bottom row expectations |
| `KeyBoundsCalculatorTest.kt` | test `ui/keyboard/` | Update all bounds for new layout dimensions |

### Won't Change

ModifierStateManagerTest, CommandModeDetectorTest, MacroSerializerTest, SilenceDetectorTest, BackupSerializationTest, LatinIME.java, KeyboardSwitcher.kt, KeyPressLogger.kt, CtrlShortcutMap.kt

## Adversarial Review Fixes Incorporated

| # | Severity | Issue | Fix |
|---|----------|-------|-----|
| 1 | CRITICAL | DevKeyTheme.kt is at `ui/theme/` not `ui/keyboard/` | Fixed path in file matrix |
| 2 | CRITICAL | No `DELETE` constant in KeyCodes (Backspace = Keyboard.KEYCODE_DELETE = -5) | Add `DELETE = -5` to KeyCodes in Phase 1 |
| 3 | CRITICAL | `getLayout(LayoutMode)` API change breaks 6+ call sites | Phase 4 must update ALL callers: DevKeyKeyboard.kt, KeyBoundsCalculator.kt, KeyMapGenerator.kt, tests |
| 4 | HIGH | TAB keycode: KeyCodes.TAB=9 (ASCII) but plan must not confuse with KeyEvent.KEYCODE_TAB=61 | Plan already correct (TAB=9), added explicit warning |
| 5 | HIGH | Smart ⌫/Esc wiring: KeyView needs InputConnection access but is deep in Compose tree | Pass `InputConnection?` as lambda parameter from DevKeyKeyboard → KeyboardView → KeyRow → KeyView |
| 6 | HIGH | 123 key has no keycode defined | Use existing `Keyboard.KEYCODE_MODE_CHANGE = -2` for 123 key (already handled by LatinIME). Add `KEYCODE_SYMBOLS = -2` alias in KeyCodes |
| 7 | HIGH | Emoji key has no handler | Add `EMOJI = -300` keycode, handle in DevKeyKeyboard.kt to show system emoji picker via `InputMethodManager` |
| 8 | MEDIUM | 30dp utility row below 48dp accessibility minimum | Documented trade-off. Do NOT use `minimumInteractiveComponentSize()` on utility keys. Increase to 36dp if testing shows issues |
| 9 | HIGH | KeyRow.kt and KeyboardView.kt hardcode `spacedBy(2.dp)` — must use theme token | Phase 3 explicitly updates both to `DevKeyTheme.keyGap` |
| 10 | HIGH | Height system: fixed dp vs percentage-based | KEEP percentage system. Use token heights only as ratios within the user's height preference. Total row weights distribute the available height |
| 11 | LOW | Line count nitpicks | Ignored — trivial |
| 12 | HIGH | Compact mode: clarify long-press behavior | COMPACT: NO long-press symbols (clean SwiftKey). COMPACT_DEV: numbers + symbols on long-press. This is intentional — COMPACT is for non-dev users |
| 13 | HIGH | SettingsSubScreens.kt not in change list | Added to Must Change. Migrate boolean compact toggle → 3-option LayoutMode selector |

## Implementation Phases

### Phase 1: Data Model & Layout Definitions
**Files**: KeyData.kt, QwertyLayout.kt, SymbolsLayout.kt
**Est. complexity**: Medium

1. **KeyData.kt** — Changes:
   - Add `KeyType.UTILITY` for utility row keys (Ctrl/Alt/Tab/arrows)
   - Add `KeyType.TOGGLE` for 123 and emoji keys
   - Add `LayoutMode` enum: `COMPACT`, `COMPACT_DEV`, `FULL`
   - Add KeyCodes:
     - `DELETE = -5` (matches `Keyboard.KEYCODE_DELETE`)
     - `SYMBOLS = -2` (alias for `Keyboard.KEYCODE_MODE_CHANGE`, handled by LatinIME)
     - `EMOJI = -300` (new, handled in DevKeyKeyboard.kt)
     - `SMART_BACK_ESC = -301` (new, sent by smart ⌫/Esc key, resolved in DevKeyKeyboard.kt)
   - **WARNING**: `KeyCodes.TAB = 9` is ASCII HT, NOT `KeyEvent.KEYCODE_TAB = 61`. Do not change.
   - Verify existing: CTRL_LEFT=-113, ALT_LEFT=-57, ESCAPE=-111

2. **QwertyLayout.kt** — Rewrite `getLayout()`:
   - Change signature: `getLayout(compactMode: Boolean)` → `getLayout(mode: LayoutMode)`
   - **FULL mode (6 rows)**:
     - Row 0 (number): `(~) 1 2 3 4 5 6 7 8 9 0 — all weight 1.0, backtick type=SPECIAL
     - Row 1 (QWERTY): q(!) w(@) e(#) r($) t(%) y(^) u(&) i(*) o(() p()) — weight 1.0, type=LETTER
     - Row 2 (home): a(~) s(|) d(\) f({) g(}) h([) j(]) k(") l(') — weight 1.0, type=LETTER
     - Row 3 (Z): Shift/1.5/MODIFIER z(;) x(:) c(/) v(?) b(<) n(>) m(_) ⌫/1.5/ACTION (smart key) — **keep weight 1.5f to match existing layout, NOT 1.4**
     - Row 4 (space): 123/1.0/TOGGLE ☺/0.8/TOGGLE ,/0.8/LETTER Space/4.0/SPACEBAR ./0.8/LETTER Enter/1.6/ACTION
     - Row 5 (utility): Ctrl/1.5/UTILITY Alt/1.5/UTILITY Tab/1.5/UTILITY [spacer/1.0] ←/1.2/UTILITY ↓/1.2/UTILITY ↑/1.2/UTILITY →/1.2/UTILITY
   - **COMPACT mode (4 rows)** — clean SwiftKey, NO long-press on letter keys:
     - Row 0: q w e r t y u i o p (type=LETTER, no longPress)
     - Row 1: a s d f g h j k l (type=LETTER, no longPress)
     - Row 2: Shift z x c v b n m ⌫ (plain backspace, no smart Esc)
     - Row 3: 123 ☺ , Space . Enter
   - **COMPACT_DEV mode (4 rows)** — numbers + symbols on long-press:
     - Row 0: q(1) w(2) e(3) r(4) t(5) y(6) u(7) i(8) o(9) p(0)
     - Row 1: a(~) s(|) d(\) f({) g(}) h([) j(]) k(") l(')
     - Row 2: Shift z(;) x(:) c(/) v(?) b(<) n(>) m(_) ⌫/Esc (smart)
     - Row 3: 123 ☺ , Space . Enter

3. **SymbolsLayout.kt** — Update bottom row:
   - Change from: `ABC , Space . Enter`
   - Change to: `ABC ☺ , Space . Enter` (match main layout)
   - ABC key uses `KEYCODE_ALPHA = -200` (existing, unchanged)

### Phase 2: Theme System
**Files**: `ui/theme/DevKeyTheme.kt` (NOTE: correct path, not ui/keyboard/)
**Est. complexity**: Low

1. Replace all existing color/spacing constants with tokenized system
2. **Surface tokens**: kbBg=#1B1B1F, keyBg=#2C2C31, keyBgSpecial=#232327, keyPressed=#3A3A40
3. **Teal monochrome modifier tokens**:
   - modBgShift=#1A3538, modTextShift=#5EC4CC
   - modBgAction=#1A3538, modTextAction=#5EC4CC
   - modBgEnter=#1A3D40, modTextEnter=#6ED4DC
   - modBgToggle=#1E2A2C, modTextToggle=#5AABB2
   - modBgSysmod=#172628, modTextSysmod=#4A9AA0
   - modBgNav=#1A2425, modTextNav=#3D8388
4. **Text tokens**: keyText=#E8E8EC, keyTextSpecial=#A0A0A8, keyHint=#5A5A62
5. **Spacing tokens**: keyGap=4.dp, keyRadius=6.dp, kbPadH=4.dp, kbPadV=5.dp
6. **Row height tokens** (used as ratio weights, NOT fixed dp):
   - rowNumberWeight=34f, rowLetterWeight=38f, rowSpaceWeight=38f, rowUtilityWeight=30f
   - Total weight for Full: 34+38+38+38+38+30=216
   - Total weight for Compact: 42+42+42+42=168
7. **Typography tokens**: fontKey=14.sp, fontKeySpecial=11.sp, fontKeyHint=9.sp, fontKeyUtility=10.sp
8. **Animation tokens**: pressDownMs=40, pressUpMs=180, pressScale=0.92f, pressEase=CubicBezierEasing(0.2f,0f,0f,1f)
9. **IMPORTANT**: Keep existing color names as deprecated aliases during transition to avoid breaking non-layout code that references them

### Phase 3: Rendering Pipeline
**Files**: KeyView.kt, KeyRow.kt, KeyboardView.kt
**Est. complexity**: Medium

1. **KeyboardView.kt**:
   - KEEP percentage-based height system (`heightPercent` from user preference)
   - Distribute total height across rows using weight ratios from theme tokens
   - Each row height = `(totalKeyAreaHeight * rowWeight) / totalWeight`
   - Update `Arrangement.spacedBy(2.dp)` → `Arrangement.spacedBy(DevKeyTheme.keyGap)` (CRITICAL: review #9)
   - Accept `List<KeyRowData>` directly (layout already provides this)

2. **KeyRow.kt**:
   - Accept explicit `rowHeight: Dp` parameter
   - Update `Arrangement.spacedBy(2.dp)` → `Arrangement.spacedBy(DevKeyTheme.keyGap)` (CRITICAL: review #9)
   - Pass height to `Modifier.height(rowHeight)` instead of `fillMaxHeight` with weight

3. **KeyView.kt**:
   - Map KeyType to theme colors:
     - LETTER → keyBg/keyText
     - NUMBER → keyBg/keyText
     - MODIFIER → modBgShift/modTextShift
     - ACTION → modBgAction/modTextAction (for ⌫/Esc)
     - SPACEBAR → keyBg/keyText
     - SPECIAL → keyBgSpecial/keyTextSpecial
     - TOGGLE → modBgToggle/modTextToggle (for 123, emoji)
     - UTILITY → modBgSysmod/modTextSysmod (for Ctrl/Alt/Tab) or modBgNav/modTextNav (for arrows)
     - Enter specifically → modBgEnter/modTextEnter
   - Use animation tokens (pressDownMs, pressUpMs, pressScale, pressEase)
   - **Smart ⌫/Esc** (review #5 — InputConnection access):
     - Accept `smartBackEscResolver: (() -> Int)?` lambda parameter (nullable)
     - When key type is ACTION and label is "⌫": call resolver to get DELETE or ESCAPE keycode
     - Long-press: always ESCAPE
     - DevKeyKeyboard.kt provides the lambda that calls `KeyboardActionBridge.resolveSmartBackEsc()`
   - Render long-press hints in top-right corner with `keyHint` color
   - Do NOT use `Modifier.minimumInteractiveComponentSize()` on utility row keys (review #8)

### Phase 4: Mode Switching & Integration
**Files**: DevKeyKeyboard.kt, KeyboardActionBridge.kt, SettingsSubScreens.kt, SettingsRepository.kt
**Est. complexity**: Medium

1. **DevKeyKeyboard.kt**:
   - Add `layoutMode: MutableState<LayoutMode>` state, read from SettingsRepository
   - Default to `LayoutMode.FULL`
   - Update ALL calls to `QwertyLayout.getLayout()` — pass `layoutMode.value` (review #3)
   - Handle `KeyCodes.SYMBOLS (-2)`: switch to Symbols mode (existing LatinIME handler via bridge)
   - Handle `KeyCodes.EMOJI (-300)`: show system emoji picker via `InputMethodService.switchInputMethod()` or `InputMethodManager.showInputMethodPicker()` — research best API
   - Provide `smartBackEscResolver` lambda to KeyView:
     ```kotlin
     val resolver = { bridge.resolveSmartBackEsc(currentInputConnection) }
     ```

2. **KeyboardActionBridge.kt**:
   - Add `KeyCodes.DELETE` import
   - Add smart ⌫/Esc decision method:
     ```kotlin
     fun resolveSmartBackEsc(inputConnection: InputConnection?): Int {
         val extracted = inputConnection?.getExtractedText(ExtractedTextRequest(), 0)
         val cursor = extracted?.selectionStart ?: 0
         val selLength = (extracted?.selectionEnd ?: 0) - cursor
         return if (cursor > 0 || selLength > 0) KeyCodes.DELETE else KeyCodes.ESCAPE
     }
     ```
   - Default to DELETE (Backspace) when InputConnection is null

3. **SettingsSubScreens.kt** (review #13):
   - Replace boolean "Compact Mode" toggle with 3-option selector:
     - "Full" — 6 rows, number row + utility row
     - "Compact" — 4 rows, clean SwiftKey layout
     - "Compact Dev" — 4 rows, numbers/symbols on long-press
   - Use `ListPreference`-style dropdown or radio group

4. **SettingsRepository.kt** (review #13):
   - Add `KEY_LAYOUT_MODE = "layout_mode"` string preference
   - Values: "full", "compact", "compact_dev"
   - Add migration: read old `KEY_COMPACT_MODE` boolean, map `true` → "compact_dev", `false` → "full"
   - Add `getLayoutMode(): Flow<LayoutMode>` method

### Phase 5: Key Bounds & Debug System Update
**Files**: KeyBoundsCalculator.kt, KeyMapGenerator.kt
**Est. complexity**: Low-Medium

1. **KeyBoundsCalculator.kt**:
   - Update `computeKeyBounds()` to accept per-row height weights from theme tokens
   - Support 6 rows (currently assumes 5)
   - Update gap constant: `2.dp` → `DevKeyTheme.keyGap` (4.dp)
   - Update padding constants to match kbPadH/kbPadV tokens
   - Use same weight-ratio distribution as KeyboardView

2. **KeyMapGenerator.kt**:
   - Update `KEY_GAP_DP = 2f` → `4f` (review #9)
   - Update `ROW_GAP_DP = 2f` → `4f` (review #9)
   - Update for 6-row layout structure
   - Update key label normalization for new keys (☺, ⌫/Esc, `)
   - Verify `isDebugBuild()` check still works

### Phase 6: Test Suite Update
**Files**: QwertyLayoutFullTest.kt, QwertyLayoutCompactTest.kt, SymbolsLayoutTest.kt, KeyBoundsCalculatorTest.kt
**Est. complexity**: High

1. **QwertyLayoutFullTest.kt** — Rewrite for 6-row Full layout:
   - Update API call: `QwertyLayout.getLayout(LayoutMode.FULL)`
   - Test 6 rows total
   - Row 0: 11 keys (` 1-9 0), backtick has ~ long-press, dedicated 0 key, type=SPECIAL/NUMBER
   - Row 1: 10 QWERTY keys with symbol long-press
   - Row 2: 9 home row keys with symbol long-press
   - Row 3: 9 keys (Shift + 7 letters + ⌫/Esc), smart key type=ACTION
   - Row 4: 6 keys (123 ☺ , Space . Enter), verify TOGGLE type on 123/emoji
   - Row 5: 8 keys + spacer (Ctrl Alt Tab _ ← ↓ ↑ →), verify UTILITY type
   - Verify NO Esc key in number row
   - Verify NO Tab key in number row
   - Verify 0 key is dedicated (NOT a long-press on Tab)
   - Verify 123 key has `primaryCode == KeyCodes.SYMBOLS` (-2)
   - Verify emoji key has `primaryCode == KeyCodes.EMOJI` (-300)

2. **QwertyLayoutCompactTest.kt** — Rewrite for both Compact modes:
   - COMPACT: `QwertyLayout.getLayout(LayoutMode.COMPACT)` — 4 rows, NO long-press, NO utility row
   - COMPACT_DEV: `QwertyLayout.getLayout(LayoutMode.COMPACT_DEV)` — 4 rows, numbers on Q-P long-press, symbols on home/Z
   - Test LayoutMode enum has exactly 3 values
   - Test Compact mode letter keys have `longPressCode == null`
   - Test Compact Dev Q key has `longPressLabel == "1"`, P key has `longPressLabel == "0"`

3. **SymbolsLayoutTest.kt** — Update:
   - Bottom row: ABC ☺ , Space . Enter (add emoji key presence test)
   - Number row: verify 1-0 all present as dedicated keys (existing)

4. **KeyBoundsCalculatorTest.kt** — Update:
   - New layout dimensions (6 rows, gap=4dp, padding=4dp/5dp)
   - Update expected pixel coordinates for emulator spec (1080x2400)
   - Test utility row bounds (shorter weighted height)
   - Update API call to use LayoutMode.FULL

### Phase 7: Build Verification & Commit
**Est. complexity**: Low

1. Run `./gradlew assembleDebug` — verify build passes
2. Run `./gradlew test` — verify all tests pass (target: 254+ tests, 0 failures)
3. Fix any compilation or test failures
4. Single commit with all changes

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Smart ⌫/Esc cursor check returns null | Default to Backspace (DELETE) when InputConnection unavailable |
| KeyBoundsCalculator pixel math diverges from Compose | Use same gap/padding tokens as single source of truth |
| LayoutMode enum breaks existing mode switching | Keep KeyboardMode (Normal/Symbols/etc) separate from LayoutMode. They are orthogonal: LayoutMode=row structure, KeyboardMode=which keyboard shown |
| Utility row 30dp below 48dp accessibility minimum | Known trade-off. Do NOT use minimumInteractiveComponentSize(). Increase to 36dp if user testing shows issues |
| Compose recomposition on mode switch | Use `remember(layoutMode)` to cache layout data |
| Percentage height vs fixed dp confusion | KEEP percentage system. Use token weights only for row distribution ratios within the user's chosen height |
| getLayout() API change breaks callers | Phase 4 explicitly updates all 6+ call sites |
| Old compact_mode boolean pref in the wild | Migration path in SettingsRepository: true→compact_dev, false→full |

## Additional Review Notes

- **Pre-existing test bug**: `QwertyLayoutCompactTest.kt` line 98 asserts Tab keycode=61 but actual is 9. Phase 6 rewrite fixes this.
- **Toolbar 123 duplication**: New 123 key on spacebar row duplicates toolbar Symbols button. For now keep both — remove toolbar button in a follow-up cleanup.
- **Compact mode Tab access**: COMPACT mode users cannot type Tab (no utility row, no Tab key). This is intentional — COMPACT is SwiftKey-clone for non-dev users. COMPACT_DEV and FULL both have Tab.
- **Compact mode ⌫ long-press**: COMPACT mode backspace has NO long-press (null). COMPACT_DEV has smart ⌫/Esc with long-press=Escape.
- **Design doc height**: Fix heading from "212dp" to "216dp" (34+38+38+38+38+30=216).
- **Smart ⌫/Esc architecture**: KeyView sends `KeyCodes.SMART_BACK_ESC = -301` on tap. DevKeyKeyboard.kt intercepts, calls bridge.resolveSmartBackEsc(inputConnection), then forwards resolved keycode. KeyView does NOT access InputConnection directly.

## Definition of Done

- [ ] 3 layout modes render correctly (Compact, Compact Dev, Full)
- [ ] Full mode has 6 rows with correct key arrangement
- [ ] Dedicated 0 key visible in Full mode number row
- [ ] Smart ⌫/Esc sends correct keycode based on cursor position
- [ ] Teal monochrome modifier colors applied per KeyType
- [ ] All animation tokens active (40ms press, 180ms release, 0.92 scale)
- [ ] Gap/spacing uses theme tokens (not hardcoded 2.dp)
- [ ] KeyBoundsCalculator produces correct coordinates for 6-row layout
- [ ] Settings UI offers 3-way layout mode selector
- [ ] Old compact_mode boolean migrated to new layout_mode string
- [ ] All tests pass (0 failures)
- [ ] Build succeeds
- [ ] Spacebar row matches SwiftKey: 123 ☺ , Space . Enter
