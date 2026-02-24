# Session 2 Design: Keyboard Layout & Rendering

**Date**: 2026-02-23
**Status**: Approved
**Parent Design**: `docs/plans/2026-02-23-devkey-design.md`
**Implementation Plan**: `.claude/plans/devkey-implementation-plan.md` (Session 2)

---

## 1. Architecture Overview

Clean break from legacy Canvas rendering. Compose UI replaces the old view pipeline entirely.

```
LatinIME.java (kept as IME service)
  └── onCreateInputView() returns ComposeView
        └── DevKeyKeyboard composable (root)
              ├── ToolbarRow (4 buttons + overflow, all no-op shells)
              ├── SuggestionBar (3 static slots)
              └── KeyboardView
                    ├── KeyRow × 5 (number, qwerty, home, z-row, bottom)
                    │     └── KeyView × N (per key)
                    └── KeyPreviewPopup (floating above pressed key)
```

**Data flow**:
- `QwertyLayout` — Kotlin object defining all rows/keys as data classes
- `ModifierStateManager` — `StateFlow<ModifierState>` tracks Shift/Ctrl/Alt
- Key press → `KeyboardActionBridge` → calls `LatinIME.onKey()` (existing text insertion logic)
- Modifier changes → `ModifierStateManager` updates flow → Compose recomposes affected keys

**Key principle**: The Compose UI is purely a view layer. All text insertion, dictionary lookup, and InputConnection work continues through LatinIME's existing machinery.

## 2. Key Data Model

```kotlin
enum class KeyType {
    LETTER, NUMBER, MODIFIER, ACTION, ARROW, SPECIAL, SPACEBAR
}

data class KeyData(
    val primaryLabel: String,
    val primaryCode: Int,
    val longPressLabel: String?,
    val longPressCode: Int?,
    val type: KeyType,
    val weight: Float = 1.0f,
    val isRepeatable: Boolean = false
)

data class KeyRowData(
    val keys: List<KeyData>,
    val rowHeight: Dp = 48.dp
)

data class KeyboardLayoutData(
    val rows: List<KeyRowData>
)
```

**Layout** defined as a Kotlin object (`QwertyLayout`):

| Row | Keys | Notes |
|-----|------|-------|
| Number | Esc, 1-9, Tab | Esc longpress=`` ` ``, Tab longpress=`0` |
| QWERTY | Q-P | Longpress: `! @ # $ % ^ & * ( )` |
| Home | A-L | Longpress: `` ~ | \ { } [ ] " ' `` |
| Z-row | Shift, Z-M, Backspace | Longpress: `; : / ? < > _` |
| Bottom | Shift, Ctrl, Alt, Space, ←↑↓→, Enter | Modifiers + arrows |

**Key sizing**: Weight-based flex. Each key gets a weight value (letter=1.0, Space=5.0, Shift=1.5). Row fills available width using `Modifier.weight()`. Height is a fixed dp per row.

**Shift transforms**: When Shift is active, letter keys show uppercase labels. ModifierStateManager state drives recomposition of affected key labels only.

## 3. Touch Handling

**Tap**: Send `primaryCode` via `KeyboardActionBridge.onKey()`. Key darkens briefly (100ms animation).

**Long-press**: After 300ms hold (configurable), send `longPressCode`. Haptic feedback buzz. For repeatable keys (Backspace, arrows), auto-repeat starts after the long-press threshold at ~50ms intervals.

**Key preview popup**: On press, a floating composable appears ~60dp above the key showing the magnified label. Disappears on release. Implemented as a `Popup` composable anchored to the pressed key's position.

## 4. Modifier State Machine

```
ModifierState per modifier (Shift, Ctrl, Alt):
  OFF ──tap──→ ONE_SHOT ──next key──→ OFF
   ↑                ↓ (double-tap)
   └────────── LOCKED ──tap──→ OFF

  Any state ──hold──→ HELD (active while finger down) ──release──→ OFF
```

| State | Behavior | Visual |
|-------|----------|--------|
| OFF | Modifier inactive | Default key color |
| ONE_SHOT | Active for next keypress only, then reverts to OFF | Highlighted background |
| LOCKED | Stays active until tapped again (caps lock, ctrl lock) | Highlighted + lock indicator |
| HELD | Active while finger is physically down (multitouch) | Highlighted background |

**Multitouch**: User holds Ctrl with one finger, taps C with another → sends Ctrl+C. `ModifierStateManager` tracks pointer IDs per modifier key. Compose's `pointerInput` modifier handles this.

**ModifierStateManager** exposes:
- `val shiftState: StateFlow<ModifierKeyState>`
- `val ctrlState: StateFlow<ModifierKeyState>`
- `val altState: StateFlow<ModifierKeyState>`
- `fun isShiftActive(): Boolean` (true if ONE_SHOT, LOCKED, or HELD)
- `fun onModifierDown(modifier, pointerId)`
- `fun onModifierUp(modifier, pointerId)`
- `fun onModifierTap(modifier)`
- `fun consumeOneShot()` — called after a non-modifier key press

## 5. Theme & Visual Design

**Clean Modern theme**:

| Element | Value |
|---------|-------|
| Keyboard background | `#1B1B1B` |
| Key fill | `#2A2A2A`, 6dp corner radius |
| Key text | `#E0E0E0`, 18sp |
| Long-press hint | `#666666`, 9sp, top-right corner |
| Key gap | 4dp horizontal, 4dp vertical |
| Pressed key | `#3A3A3A` (slightly lighter) |

**Special key colors**:

| Key | Background | Text Color |
|-----|-----------|------------|
| Esc | `#3A2020` | Red |
| Tab | `#203040` | Blue |
| Ctrl | `#2A2A3A` | Purple |
| Alt | `#2A3A2A` | Green |
| Arrows | `#2A2A3A` | Blue |
| Shift (active) | Highlighted variant | White |
| Space | `#2A2A2A` | `#666666` "DevKey" label |
| Enter | `#2A3A2A` | Green |
| Backspace | `#3A2020` | Red |

**Modifier active states**: ONE_SHOT or LOCKED brightens background (e.g., Ctrl `#2A2A3A` → `#4A4A6A`). LOCKED adds a small dot indicator below the label.

**Suggestion bar**: `#1B1B1B` background, 3 slots divided by `#333333` 1px lines, text `#CCCCCC`, 40dp height. Collapse arrow on left.

**Toolbar**: `#1B1B1B` background, icon buttons `#888888`, 36dp height. 1px `#333333` border between toolbar and suggestion bar.

All colors defined in a `DevKeyTheme` object for easy future theming.

## 6. Suggestion Bar & Toolbar Shells

**Suggestion Bar** — static shell, wired to predictions in Session 4:
- Collapse arrow on left — tap hides/shows
- 3 equal-width slots with dividers
- Tap suggestion → inserts text via KeyboardActionBridge
- Hardcoded suggestions ("the", "I", "and")
- `PredictionProvider` interface defined, returns static list for now

**Toolbar** — 5 buttons, all no-op shells:

| Button | Session 2 behavior | Wired in |
|--------|-------------------|----------|
| 📋 Clipboard | Toast "Coming soon" | Session 3 |
| 🎤 Voice | Toast "Coming soon" | Session 4 |
| 123 Symbols | Toast "Coming soon" | Session 3 |
| ⚡ Macros | Toast "Coming soon" | Session 3 |
| ··· Overflow | Toast "Coming soon" | Session 5 |

Each button gets a `ToolbarAction` callback parameter for easy future wiring.

## 7. IME Integration (KeyboardActionBridge)

```kotlin
class KeyboardActionBridge(private val listener: OnKeyboardActionListener) {
    fun onKeyPress(code: Int, modifiers: ModifierState)
    fun onKeyRelease(code: Int)
    fun onText(text: CharSequence)
}
```

**Connection flow**:
1. LatinIME creates ComposeView in `onCreateInputView()`
2. LatinIME passes itself (implements `OnKeyboardActionListener`) to Compose tree
3. `KeyView` tap → `bridge.onKeyPress(code, modifiers)`
4. Bridge translates to `listener.onKey(code, intArrayOf(code))`
5. LatinIME handles InputConnection, text insertion, dictionary lookup

**Modifier handling**: For Ctrl+C, bridge sends `KeyEvent` with `META_CTRL_ON` flag directly to InputConnection, bypassing normal character insertion.

**Unchanged in LatinIME**: `onKey()`, `onText()`, `commitTyped()`, `sendKeyEvent()` all stay as-is.

## 8. Files

**New files**:

| File | Purpose |
|------|---------|
| `ui/keyboard/KeyData.kt` | Data classes, KeyType enum |
| `ui/keyboard/QwertyLayout.kt` | Full 5-row layout definition |
| `ui/keyboard/KeyView.kt` | Single key composable |
| `ui/keyboard/KeyRow.kt` | Row composable |
| `ui/keyboard/KeyboardView.kt` | Full keyboard composable |
| `ui/keyboard/KeyPreviewPopup.kt` | Floating key label on press |
| `ui/keyboard/DevKeyKeyboard.kt` | Root composable |
| `ui/suggestion/SuggestionBar.kt` | 3-slot static suggestion bar |
| `ui/toolbar/ToolbarRow.kt` | 5 icon buttons |
| `ui/theme/DevKeyTheme.kt` | Colors, dimensions, typography |
| `core/ModifierStateManager.kt` | StateFlow-based modifier state machine |
| `core/KeyboardActionBridge.kt` | Compose → LatinIME bridge |

**Modified files**:

| File | Change |
|------|--------|
| `LatinIME.java` | `onCreateInputView()` returns ComposeView |
| `KeyboardSwitcher.kt` | Skip legacy view inflation when Compose active |

## 9. Definition of Done

- [ ] Full keyboard renders matching layout #8 (5 rows, correct keys)
- [ ] All long-press hints visible on all keys
- [ ] Typing works: letters, numbers, Esc, Tab, Backspace, Enter, arrows
- [ ] Key preview popup shows on press
- [ ] Shift one-shot + caps lock works
- [ ] Ctrl and Alt one-shot + held (multitouch) works
- [ ] Suggestion bar renders with 3 static suggestions, tappable
- [ ] Toolbar renders with 5 buttons (all no-op)
- [ ] Clean Modern theme applied (all colors match design doc)
- [ ] Project builds with `./gradlew assembleDebug`

## 10. Out of Scope

Deferred to Sessions 3-5: symbols layer (123), clipboard manager, voice input, macros, Ctrl-held overlay, AI predictions, settings, export/import, compact mode.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Migration strategy | Clean break | No XML parsing, pure Kotlin layouts, simpler long-term |
| Key sizing | Weight-based flex | Responsive, adapts to screen sizes automatically |
| IME wiring | ComposeView in LatinIME | Minimal surgery on the monolith, reuse all text insertion logic |
| Long-press behavior | Direct character insert | One gesture, fast, no popup picker needed (English-only) |
| Key preview | Yes, floating popup | Confirms which key was hit |
| Language scope | English only | Personal use, simplifies everything |
