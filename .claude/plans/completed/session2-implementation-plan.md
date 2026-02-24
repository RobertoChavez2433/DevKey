# Session 2 Implementation Plan: Keyboard Layout & Rendering

**Design Doc**: `docs/plans/2026-02-23-session2-keyboard-layout-design.md`
**Status**: Ready for implementation
**Base path**: `app/src/main/java/dev/devkey/keyboard/`

---

## Phase 1: Theme & Data Model

**Goal**: Define all theme constants and the key data model that every other phase depends on.

**Files to create**:

### 1.1 `ui/theme/DevKeyTheme.kt`
- Kotlin object with all color, dimension, and typography constants
- Colors: keyboard background `#1B1B1B`, key fill `#2A2A2A`, pressed `#3A3A3A`, text `#E0E0E0`, hint `#666666`
- Special key colors: Esc (`#3A2020`/red), Tab (`#203040`/blue), Ctrl (`#2A2A3A`/purple), Alt (`#2A3A2A`/green), Arrows (`#2A2A3A`/blue), Enter (`#2A3A2A`/green), Backspace (`#3A2020`/red), Space (`#2A2A2A`/`#666666`)
- Modifier active colors: brightened variants for ONE_SHOT/LOCKED (e.g., Ctrl `#2A2A3A` → `#4A4A6A`)
- Dimensions: key corner radius 6dp, key gap 4dp, row height 48dp, suggestion bar height 40dp, toolbar height 36dp
- Typography: key label 18sp, hint 9sp, suggestion text size

### 1.2 `ui/keyboard/KeyData.kt`
- `KeyType` enum: `LETTER`, `NUMBER`, `MODIFIER`, `ACTION`, `ARROW`, `SPECIAL`, `SPACEBAR`
- `KeyData` data class: `primaryLabel`, `primaryCode`, `longPressLabel`, `longPressCode`, `type`, `weight` (default 1.0f), `isRepeatable` (default false)
- `KeyRowData` data class: `keys: List<KeyData>`, `rowHeight: Dp` (default 48.dp)
- `KeyboardLayoutData` data class: `rows: List<KeyRowData>`

### 1.3 `ui/keyboard/QwertyLayout.kt`
- Kotlin object that builds the full `KeyboardLayoutData`
- **Number row** (11 keys): Esc (weight 1.0, longpress=backtick), 1-9 (weight 1.0 each, no longpress), Tab (weight 1.0, longpress=0)
- **QWERTY row** (10 keys): Q-P (weight 1.0 each). Longpress: Q→!, W→@, E→#, R→$, T→%, Y→^, U→&, I→*, O→(, P→)
- **Home row** (9 keys): A-L (weight 1.0 each). Longpress: A→~, S→|, D→\, F→{, G→}, H→[, J→], K→", L→'
- **Z-row** (9+2 keys): Shift (weight 1.5, MODIFIER type), Z-M (weight 1.0 each), Backspace (weight 1.5, ACTION type, isRepeatable=true). Longpress: Z→;, X→:, C→/, V→?, B→<, N→>, M→_
- **Bottom row** (9 keys): Shift (weight 1.2, MODIFIER), Ctrl (weight 1.2, MODIFIER), Alt (weight 1.2, MODIFIER), Space (weight 5.0, SPACEBAR), ← (weight 1.0, ARROW, repeatable), ↑ (weight 1.0, ARROW), ↓ (weight 1.0, ARROW), → (weight 1.0, ARROW, repeatable), Enter (weight 1.5, ACTION)
- Use `Keyboard.KEYCODE_SHIFT`, `Keyboard.KEYCODE_DELETE` for special keycodes. For Esc use 27, Tab use 9, Enter use 10, arrows use constants from KeyEvent.

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 2: Modifier State Machine & Action Bridge

**Goal**: The two core logic components that the UI will depend on.

**Files to create**:

### 2.1 `core/ModifierStateManager.kt`
- `ModifierKeyState` enum: `OFF`, `ONE_SHOT`, `LOCKED`, `HELD`
- `ModifierType` enum: `SHIFT`, `CTRL`, `ALT`
- Class `ModifierStateManager`:
  - Private `MutableStateFlow<ModifierKeyState>` for each modifier, initialized to `OFF`
  - Public `val shiftState: StateFlow<ModifierKeyState>`
  - Public `val ctrlState: StateFlow<ModifierKeyState>`
  - Public `val altState: StateFlow<ModifierKeyState>`
  - `fun isShiftActive(): Boolean` — true if state != OFF
  - `fun isCtrlActive(): Boolean`
  - `fun isAltActive(): Boolean`
  - `fun onModifierTap(type: ModifierType)` — OFF→ONE_SHOT, ONE_SHOT→LOCKED (double-tap window 400ms), LOCKED→OFF
  - `fun onModifierDown(type: ModifierType, pointerId: Int)` — enters HELD state, tracks pointer ID
  - `fun onModifierUp(type: ModifierType, pointerId: Int)` — exits HELD→OFF (only if matching pointer)
  - `fun consumeOneShot()` — called after a non-modifier key press. If any modifier is ONE_SHOT, reset to OFF.
  - Private `lastTapTime` per modifier for double-tap detection

### 2.2 `core/KeyboardActionBridge.kt`
- Takes `LatinKeyboardBaseView.OnKeyboardActionListener` in constructor
- `fun onKeyPress(code: Int)` — calls `listener.onPress(code)`
- `fun onKeyRelease(code: Int)` — calls `listener.onRelease(code)`
- `fun onKey(code: Int, modifierState: ModifierStateManager)`:
  - If Ctrl or Alt is active and code is a letter: construct and send a `KeyEvent` with appropriate `META_CTRL_ON`/`META_ALT_ON` flags via the listener's `onKey()` method
  - If Shift is active and code is a letter: send uppercase code
  - Otherwise: call `listener.onKey(code, intArrayOf(code), NOT_A_TOUCH_COORDINATE, NOT_A_TOUCH_COORDINATE)`
  - After sending: call `modifierState.consumeOneShot()`
- `fun onText(text: CharSequence)` — calls `listener.onText(text)`
- `NOT_A_TOUCH_COORDINATE` = -1 (matches `LatinKeyboardBaseView` constant)

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 3: Key Composables (KeyView, KeyRow, KeyPreviewPopup)

**Goal**: The atomic UI building blocks for individual keys and rows.

**Files to create**:

### 3.1 `ui/keyboard/KeyPreviewPopup.kt`
- `@Composable fun KeyPreviewPopup(label: String, anchorBounds: IntRect, visible: Boolean)`
- Renders a `Popup` positioned ~60dp above the key center
- Shows magnified label (24sp, white text) on dark rounded rect (`#3A3A3A`, 8dp radius)
- Popup dimensions: ~48dp x 56dp
- Only shown when `visible` is true (controlled by KeyView press state)

### 3.2 `ui/keyboard/KeyView.kt`
- `@Composable fun KeyView(key: KeyData, modifierState: ModifierStateManager, onKeyAction: (Int) -> Unit, onKeyPress: (Int) -> Unit, onKeyRelease: (Int) -> Unit, modifier: Modifier)`
- Background: rounded rect with color from `DevKeyTheme` based on `key.type`. Use `key.type` to look up colors from theme.
- Center label: show `key.primaryLabel`. If key is LETTER type and shift is active, show uppercase.
- Top-right hint: show `key.longPressLabel` in 9sp `#666666` if non-null.
- Modifier active indicator: if key is MODIFIER type and its state is ONE_SHOT or LOCKED, use brightened background. LOCKED adds a small dot below label.
- Touch handling via `pointerInput`:
  - On down: set pressed state, show preview popup, call `onKeyPress(code)`, start long-press timer (300ms)
  - On up (before 300ms): fire tap → `onKeyAction(primaryCode)`, hide popup, call `onKeyRelease(code)`
  - On timeout (300ms): if `longPressCode` non-null, fire `onKeyAction(longPressCode)`, haptic feedback. If `isRepeatable`, start repeat loop (~50ms interval) calling `onKeyAction(primaryCode)`.
  - On up (after long-press): stop repeat, hide popup, call `onKeyRelease(code)`
- Press animation: `animateColorAsState` from fill to pressed color (100ms)
- For MODIFIER keys: touch handling is different — on down call `modifierState.onModifierDown()`, on up call `modifierState.onModifierUp()`, on tap call `modifierState.onModifierTap()`. Do NOT fire onKeyAction for modifier keys.

### 3.3 `ui/keyboard/KeyRow.kt`
- `@Composable fun KeyRow(row: KeyRowData, modifierState: ModifierStateManager, onKeyAction: (Int) -> Unit, onKeyPress: (Int) -> Unit, onKeyRelease: (Int) -> Unit)`
- `Row` composable with `Modifier.fillMaxWidth().height(row.rowHeight)`
- For each key in `row.keys`: `KeyView(key, ..., modifier = Modifier.weight(key.weight))`
- Horizontal spacing: 4dp gap between keys (use `Arrangement.spacedBy(4.dp)`)

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 4: Suggestion Bar & Toolbar Shells

**Goal**: The top sections of the keyboard UI — static shells to be wired later.

**Files to create**:

### 4.1 `ui/suggestion/SuggestionBar.kt`
- `@Composable fun SuggestionBar(suggestions: List<String>, onSuggestionClick: (String) -> Unit, onCollapseToggle: () -> Unit, isCollapsed: Boolean)`
- Height: 40dp, background `#1B1B1B`
- Left: collapse arrow (`◀` / `▶` based on `isCollapsed`), tappable
- When not collapsed: 3 equal-width slots separated by 1px `#333333` vertical dividers
- Each slot: centered text in `#CCCCCC`, tappable (calls `onSuggestionClick`)
- When collapsed: bar hidden (height 0dp with animation)
- Default suggestions: listOf("the", "I", "and")

### 4.2 `ui/toolbar/ToolbarRow.kt`
- `@Composable fun ToolbarRow(onClipboard: () -> Unit, onVoice: () -> Unit, onSymbols: () -> Unit, onMacros: () -> Unit, onOverflow: () -> Unit)`
- Height: 36dp, background `#1B1B1B`
- 5 equally-spaced icon buttons: 📋, 🎤, "123", ⚡, ···
- Icons in `#888888` color
- 1px `#333333` bottom border (divider between toolbar and suggestion bar)
- All callbacks show a Toast: "Coming soon" (use `LocalContext.current`)

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 5: Root Composable & Full Keyboard Assembly

**Goal**: Assemble all pieces into the complete keyboard composable.

**Files to create**:

### 5.1 `ui/keyboard/KeyboardView.kt`
- `@Composable fun KeyboardView(layout: KeyboardLayoutData, modifierState: ModifierStateManager, onKeyAction: (Int) -> Unit, onKeyPress: (Int) -> Unit, onKeyRelease: (Int) -> Unit)`
- `Column` composable with `Modifier.fillMaxWidth().background(DevKeyTheme.keyboardBackground)`
- For each row in `layout.rows`: `KeyRow(row, ...)`
- Vertical spacing: 4dp between rows (use `Arrangement.spacedBy(4.dp)`)
- Horizontal padding: 4dp on left/right

### 5.2 `ui/keyboard/DevKeyKeyboard.kt`
- `@Composable fun DevKeyKeyboard(actionListener: LatinKeyboardBaseView.OnKeyboardActionListener)`
- Creates `remember { ModifierStateManager() }`
- Creates `remember { KeyboardActionBridge(actionListener) }`
- Layout: `Column` with:
  1. `ToolbarRow(...)` — all callbacks show toast
  2. `SuggestionBar(...)` — hardcoded suggestions, `onSuggestionClick` calls `bridge.onText()`
  3. `KeyboardView(QwertyLayout.layout, modifierState, ...)` — wires key actions through bridge
- `onKeyAction` lambda: calls `bridge.onKey(code, modifierState)`
- `onKeyPress` lambda: calls `bridge.onKeyPress(code)`
- `onKeyRelease` lambda: calls `bridge.onKeyRelease(code)`

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 6: IME Integration

**Goal**: Wire the Compose keyboard into LatinIME so it actually appears when a text field is focused.

**Files to modify**:

### 6.1 `LatinIME.java` — Modify `onCreateInputView()`
- Replace current implementation:
  ```java
  @Override
  public View onCreateInputView() {
      setCandidatesViewShown(false);
      ComposeView composeView = new ComposeView(this);
      composeView.setContent(() -> {
          DevKeyKeyboardKt.DevKeyKeyboard(this);
          return Unit.INSTANCE;
      });
      return composeView;
  }
  ```
- Note: LatinIME implements `OnKeyboardActionListener`, so pass `this` as the listener
- Import `androidx.compose.ui.platform.ComposeView`
- The old `mKeyboardSwitcher.recreateInputView()` / `makeKeyboards()` / `setKeyboardMode()` calls are removed from this method
- Keep the rest of LatinIME unchanged — `onKey()`, `onText()`, `onPress()`, `onRelease()` all still work

### 6.2 `KeyboardSwitcher.kt` — Minor guard
- In `recreateInputView()` and `getInputView()`, add a null-safe guard so calling these from other LatinIME methods doesn't crash (they may still be called from `onStartInput` etc.)
- Don't change the logic, just make it safe if the old view isn't initialized

**Build gate**: `./gradlew assembleDebug` must pass. This is the critical gate — if this passes, the keyboard should render on device.

---

## Phase 7: Build Verification & Cleanup

**Goal**: Ensure everything compiles, remove .gitkeep placeholders from populated directories, verify no import errors.

### 7.1 Full build
- Run `./gradlew assembleDebug`
- Fix any compilation errors

### 7.2 Cleanup
- Delete `.gitkeep` files from directories that now have real files: `ui/keyboard/`, `ui/suggestion/`, `ui/toolbar/`, `ui/theme/`, `core/`
- Verify no unused imports or dead code in new files

### 7.3 Smoke check
- Verify the APK size is reasonable
- Verify no R8/ProGuard issues

**Final build gate**: `./gradlew assembleDebug` passes cleanly.

---

## Dependency Graph

```
Phase 1 (Theme + Data Model)
  ├── Phase 2 (Modifier State + Bridge)
  │     └── Phase 3 (KeyView, KeyRow, KeyPreview)
  │           └── Phase 5 (Root Composable Assembly)
  │                 └── Phase 6 (IME Integration)
  │                       └── Phase 7 (Build & Cleanup)
  └── Phase 4 (Suggestion Bar + Toolbar)
        └── Phase 5 (Root Composable Assembly)
```

Phases 2 and 4 can run in parallel (independent). All other phases are sequential.

---

## Files Summary

**New files (12)**:
1. `ui/theme/DevKeyTheme.kt`
2. `ui/keyboard/KeyData.kt`
3. `ui/keyboard/QwertyLayout.kt`
4. `ui/keyboard/KeyPreviewPopup.kt`
5. `ui/keyboard/KeyView.kt`
6. `ui/keyboard/KeyRow.kt`
7. `ui/keyboard/KeyboardView.kt`
8. `ui/keyboard/DevKeyKeyboard.kt`
9. `ui/suggestion/SuggestionBar.kt`
10. `ui/toolbar/ToolbarRow.kt`
11. `core/ModifierStateManager.kt`
12. `core/KeyboardActionBridge.kt`

**Modified files (2)**:
1. `LatinIME.java` — `onCreateInputView()` returns ComposeView
2. `KeyboardSwitcher.kt` — null-safe guards

## Known Risks

- **ComposeView in IME**: Compose in InputMethodService is supported but less common. May need `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` on the ComposeView.
- **Modifier key multitouch**: Compose `pointerInput` multitouch requires careful pointer ID tracking. May need to use `awaitPointerEventScope` with `PointerEventPass.Initial`.
- **Key repeat timing**: `delay()` in coroutines within `pointerInput` is the standard approach, but timing precision on different devices varies.
- **Haptic feedback**: Requires `android.permission.VIBRATE` — verify it's in the manifest (likely already there from legacy keyboard).
