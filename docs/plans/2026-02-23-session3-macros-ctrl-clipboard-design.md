# Session 3 Design: Macros, Ctrl Mode, Clipboard & Symbols

**Date**: 2026-02-23
**Status**: Approved
**Scope**: Session 3 of DevKey implementation plan
**Prerequisites**: Session 2 complete (Compose keyboard rendering, typing, modifiers)

---

## 1. Overview

Session 3 implements four features that make DevKey a power-user keyboard:

1. **Macro System** — Record, save, and replay keyboard macro sequences
2. **Ctrl-Held Shortcut Mode** — Visual shortcut reference when Ctrl is held
3. **Clipboard Manager** — History, search, pin, paste
4. **Symbols Layer** — Alternate keyboard layout for symbols/numbers

## 2. Architecture: Centralized KeyboardMode

A single sealed class controls the keyboard's display state:

```kotlin
sealed class KeyboardMode {
    object Normal : KeyboardMode()           // QWERTY + suggestions
    object MacroChips : KeyboardMode()       // QWERTY + macro chips replace suggestion bar
    object MacroGrid : KeyboardMode()        // QWERTY + grid panel above keyboard
    object MacroRecording : KeyboardMode()   // Recording UI replaces toolbar + suggestion bar
    object Clipboard : KeyboardMode()        // QWERTY + clipboard panel above keyboard
    object Symbols : KeyboardMode()          // Symbols layout replaces QWERTY keys
}
```

**Rules**:
- State lives as `mutableStateOf<KeyboardMode>` in `DevKeyKeyboard`
- Only one mode active at a time — switching auto-closes the previous
- Toggle behavior: tap button while in that mode → returns to `Normal`
- Ctrl Mode is **independent** — driven by `ModifierStateManager.ctrlState == HELD`, not by KeyboardMode

**Toolbar button mapping**:
| Button | Tap | Long-press |
|--------|-----|------------|
| ⚡ Macros | Toggle `Normal` ↔ `MacroChips` | Toggle `Normal` ↔ `MacroGrid` |
| 📋 Clipboard | Toggle `Normal` ↔ `Clipboard` | — |
| 123 Symbols | Toggle `Normal` ↔ `Symbols` | — |
| 🎤 Voice | No-op (Session 4) | — |
| ··· Overflow | No-op (Session 5) | — |

## 3. Macro Engine

### 3.1 MacroStep Data Model

```kotlin
data class MacroStep(
    val key: String,              // e.g., "c", "a", "f"
    val keyCode: Int,             // e.g., 99
    val modifiers: List<String>   // e.g., ["ctrl"], ["ctrl", "shift"]
)
```

Logical combos, not raw keycodes. Modifier-only presses are buffered but not emitted — a step is created only when a non-modifier key is pressed with the current modifier state.

### 3.2 MacroEngine.kt (`feature/macro/`)

Stateless recording and replay:

- `isRecording: Boolean` — observable state
- `startRecording()` — initializes capture buffer
- `captureKey(key, keyCode, modifiers)` — appends `MacroStep` to buffer
- `stopRecording(): List<MacroStep>` — returns captured steps, resets
- `cancelRecording()` — clears buffer, resets
- `replay(steps, bridge)` — sends each combo to IME via `KeyboardActionBridge`

Integration: `KeyboardActionBridge.onKey()` checks `MacroEngine.isRecording` — if true, captures the key+modifiers AND sends to IME (keys still produce input during recording).

### 3.3 MacroRepository.kt (`feature/macro/`)

Room-backed CRUD wrapping `MacroDao`:

- `getAllMacros(): Flow<List<MacroEntity>>` — sorted by `usageCount` DESC
- `saveMacro(name, steps)` — serializes steps to JSON, inserts entity
- `deleteMacro(id)`, `updateMacroName(id, name)`
- `incrementUsage(id)` — called on each replay

### 3.4 MacroSerializer.kt (`feature/macro/`)

JSON serialization using `JSONArray`/`JSONObject`:

```json
[
  {"modifiers": ["ctrl"], "key": "a", "keyCode": 97},
  {"modifiers": ["ctrl"], "key": "c", "keyCode": 99}
]
```

- `serialize(steps: List<MacroStep>): String`
- `deserialize(json: String): List<MacroStep>`

### 3.5 Default Macros

Pre-populated on first launch via `RoomDatabase.Callback.onCreate`:

| Name | Combo |
|------|-------|
| Copy | Ctrl+C |
| Paste | Ctrl+V |
| Cut | Ctrl+X |
| Undo | Ctrl+Z |
| Select All | Ctrl+A |
| Save | Ctrl+S |
| Find | Ctrl+F |

## 4. Macro UI

### 4.1 MacroChipStrip.kt (`ui/macro/`)

When `KeyboardMode.MacroChips` — replaces suggestion bar (40dp height):

- Horizontally scrollable `LazyRow` of chips
- Each chip: `⚡ Name` with key combo in grey (e.g., `Ctrl+C`)
- Sorted by `usageCount` DESC
- Tap chip → `MacroEngine.replay()` + increment usage + return to `Normal`
- Last item: "+ Add" chip with dashed border → `MacroRecording` mode
- Collapse arrow on left → back to `Normal`

### 4.2 MacroGridPanel.kt (`ui/macro/`)

When `KeyboardMode.MacroGrid` — inserts between suggestion bar and keyboard:

- 4-column `LazyVerticalGrid`, max height ~160dp
- Each cell: amber key combo label (bold) + macro name below (small)
- "+ Record" cell with dashed border at end
- Tap cell → replay + close grid
- Long-press cell → dialog: Edit name / Delete / Cancel

### 4.3 MacroRecordingBar.kt (`ui/macro/`)

When `KeyboardMode.MacroRecording` — replaces toolbar + suggestion bar (~76dp):

- Left: Red pulsing dot + "Recording..." text
- Center: Live preview strip of captured steps (e.g., `Ctrl+A → Ctrl+C`), horizontally scrollable
- Right: "Cancel" button (→ `Normal`, discards) + "Stop" button
- On Stop: `AlertDialog` with name text field + Save/Cancel
- On Save: `MacroRepository.saveMacro()` → `Normal`

## 5. Ctrl-Held Shortcut Mode

### 5.1 CtrlShortcutMap.kt (`ui/keyboard/`)

Static map of known Ctrl shortcuts:

```kotlin
object CtrlShortcutMap {
    val shortcuts: Map<String, ShortcutInfo> = mapOf(
        "a" to ShortcutInfo("Sel All"),
        "c" to ShortcutInfo("Copy", HighlightType.RED),
        "v" to ShortcutInfo("Paste", HighlightType.RED),
        "x" to ShortcutInfo("Cut"),
        "z" to ShortcutInfo("Undo"),
        "s" to ShortcutInfo("Save"),
        "f" to ShortcutInfo("Find"),
        "n" to ShortcutInfo("New"),
        "o" to ShortcutInfo("Open"),
        "p" to ShortcutInfo("Print"),
        "q" to ShortcutInfo("Quit"),
        "w" to ShortcutInfo("Close"),
        "r" to ShortcutInfo("Redo"),
        "t" to ShortcutInfo("Tab"),
        "y" to ShortcutInfo("Redo"),
        "d" to ShortcutInfo("Dup"),
        "h" to ShortcutInfo("Replace"),
        "l" to ShortcutInfo("GoTo"),
    )
}

data class ShortcutInfo(val label: String, val highlight: HighlightType = HighlightType.NORMAL)
enum class HighlightType { NORMAL, RED }
```

### 5.2 KeyView Modifications

When `ctrlState == HELD` and `key.type == LETTER`:
- **Shortcut found**: Blue/purple tinted background, shortcut label below letter (10sp). RED highlight → red-tinted background for Copy/Paste.
- **No shortcut**: Dim key to 30% opacity
- **Number row**: Always fully dimmed (10% opacity)
- **Modifier keys + arrows**: Stay normal (no dimming)

### 5.3 CtrlModeBanner

When `ctrlState == HELD`, 24dp banner below suggestion bar:
- Text: "CTRL MODE — tap a key for shortcut"
- Semi-transparent blue/purple background
- Animated slide-in from top

### 5.4 Behavior Rules

- Only activates on `HELD` state (physical hold via multitouch pointer tracking)
- `ONE_SHOT` and `LOCKED` Ctrl: NO visual transformation, modifier-only behavior
- Transformation appears instantly on hold, reverts instantly on release
- Keys still send `Ctrl+[key]` combos normally (already handled by bridge)

## 6. Clipboard Manager

### 6.1 ClipboardManager.kt (`feature/clipboard/`)

- Registers `OnPrimaryClipChangedListener` on system `ClipboardManager`
- On clip change → saves to Room via `ClipboardHistoryDao`
- Max entries: 50 (configurable later in settings). Excess deletes oldest non-pinned.
- Registered in `LatinIME.onCreate()`, unregistered in `onDestroy()`
- `pasteEntry(entry, bridge)` — sends text to InputConnection via `bridge.onText()`

### 6.2 ClipboardRepository.kt (`feature/clipboard/`)

- `getAll(): Flow<List<ClipboardHistoryEntity>>` — newest first
- `search(query): Flow<List<ClipboardHistoryEntity>>` — content LIKE filter
- `pin(id)` / `unpin(id)`, `delete(id)`, `clearAll()`

### 6.3 ClipboardPanel.kt (`ui/clipboard/`)

When `KeyboardMode.Clipboard` — inserts between suggestion bar and keyboard:

- Max height: ~200dp, scrollable
- **Search bar** at top (text field + magnifying glass icon)
- **Vertical list** of entries:
  - Content preview (truncated ~60 chars, single line) + relative timestamp ("2m ago")
  - Tap → paste + close panel
  - Long-press → context menu: Pin/Unpin, Delete
  - Pinned entries: pin icon, always at top
- **Clear All** button at bottom with confirmation dialog
- Empty state: "No clipboard history" message

### 6.4 Encryption

Deferred to Session 5. The `encryptedContent` column exists in `ClipboardHistoryEntity` but is unused. Content stored in plain `content` field for now.

## 7. Symbols Layer

### 7.1 SymbolsLayout.kt (`ui/keyboard/`)

A second `KeyboardLayoutData`, rendered by the existing `KeyboardView`:

| Row | Keys |
|-----|------|
| 1 | `1 2 3 4 5 6 7 8 9 0` |
| 2 | `@ # $ _ & - + ( ) /` |
| 3 | `* " ' : ; ! ? ,` with long-press variants |
| 4 | `~ \` \| • √ π ÷ × ¶ ∆` with long-press `€ £ ¥ ¢ ₩` |
| 5 | `ABC` (return to QWERTY), `,`, Space, `.`, Enter |

### 7.2 Behavior

- Tap `123` in toolbar → `KeyboardMode.Symbols`, keyboard swaps to `SymbolsLayout.layout`
- Tap `ABC` in symbols bottom row OR tap `123` again → `KeyboardMode.Normal`
- Long-press on keys → variant popup (same mechanism as QWERTY)
- No Shift/Ctrl/Alt/arrows in symbols layout — bottom row is simplified
- Suggestion bar hidden in symbols mode

## 8. Integration Wiring

### DevKeyKeyboard.kt Structure

```
Column {
    // Toolbar (hidden during recording)
    if (mode !is MacroRecording) ToolbarRow(...)

    // Dynamic middle area (mode-dependent)
    when (mode) {
        Normal       → SuggestionBar(...)
        MacroChips   → MacroChipStrip(macros, ...)
        MacroGrid    → SuggestionBar(...) + MacroGridPanel(macros, ...)
        MacroRec     → MacroRecordingBar(engine, ...)
        Clipboard    → SuggestionBar(...) + ClipboardPanel(entries, ...)
        Symbols      → (nothing)
    }

    // Ctrl Mode banner (independent)
    if (ctrlState == HELD) CtrlModeBanner()

    // Keyboard (layout switches for symbols)
    KeyboardView(
        layout = if (mode is Symbols) SymbolsLayout else QwertyLayout,
        ctrlHeld = ctrlState == HELD,
        isRecording = macroEngine.isRecording,
        ...
    )
}
```

### Data Flow

- Database: `DevKeyDatabase.getInstance(context)` → DAOs → Repositories → `Flow.collectAsState()`
- Macro capture: `onKeyAction` → if recording, `MacroEngine.captureKey()` + `bridge.onKey()` (both)
- Macro replay: `MacroEngine.replay()` → iterates steps → `bridge.onKey()` for each
- Clipboard: System listener → `ClipboardManager` → Room → `ClipboardPanel` via Flow
- Mode toggle: `toggle()` helper — if same mode, `Normal`; else set new mode

## 9. New Files Summary

| File | Package | Purpose |
|------|---------|---------|
| `MacroEngine.kt` | `feature.macro` | Recording/replay logic |
| `MacroRepository.kt` | `feature.macro` | Room-backed CRUD |
| `MacroSerializer.kt` | `feature.macro` | JSON serialization for steps |
| `MacroStep.kt` | `feature.macro` | Data model |
| `DefaultMacros.kt` | `feature.macro` | Pre-populated macros on first launch |
| `MacroChipStrip.kt` | `ui.macro` | Chips in suggestion bar area |
| `MacroGridPanel.kt` | `ui.macro` | 4-column grid panel |
| `MacroRecordingBar.kt` | `ui.macro` | Recording mode UI |
| `ClipboardManager.kt` | `feature.clipboard` | System clipboard listener + history |
| `ClipboardRepository.kt` | `feature.clipboard` | Room-backed CRUD |
| `ClipboardPanel.kt` | `ui.clipboard` | Clipboard history panel |
| `CtrlShortcutMap.kt` | `ui.keyboard` | Shortcut label definitions |
| `CtrlModeBanner.kt` | `ui.keyboard` | "CTRL MODE" banner |
| `SymbolsLayout.kt` | `ui.keyboard` | Symbols keyboard layout data |
| `KeyboardMode.kt` | `ui.keyboard` | Sealed class for mode management |

### Modified Files

| File | Changes |
|------|---------|
| `DevKeyKeyboard.kt` | Mode state, wiring all features, toolbar callbacks |
| `KeyView.kt` | Ctrl-held visual transformation, recording indicator |
| `ToolbarRow.kt` | Long-press callback on ⚡, active state indicators |
| `KeyboardView.kt` | Accept `ctrlHeld` and `isRecording` params |
| `KeyboardActionBridge.kt` | Macro capture hook in `onKey()` |
| `LatinIME.java` | Register/unregister ClipboardManager listener |

## 10. Testing Strategy

### Unit Tests
- **MacroEngine**: Record → stop returns correct steps. Cancel clears. isRecording transitions.
- **MacroSerializer**: Roundtrip serialize/deserialize identity. Empty list. Multi-modifier combos.
- **MacroRepository**: Insert, query (sorted), increment usage, delete. In-memory Room.
- **CtrlShortcutMap**: Expected keys present. Lookup returns correct labels. Missing keys → null.
- **ClipboardRepository**: Insert, query (newest first), pin/unpin, delete, clear all, max limit.
- **SymbolsLayout**: Row counts, key counts, valid codes, ABC key present.

### Integration Tests
- **Macro flow**: Record → name → save → chip appears → tap → replay produces correct events
- **Clipboard flow**: Copy text → entry appears → tap entry → text pasted
- **Mode switching**: Only one mode active. Toggle same button → Normal. Switch button → closes previous.
- **Ctrl Mode**: Hold Ctrl → banner + key transform → release → revert

## 11. Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| View state management | Centralized KeyboardMode sealed class | Single source of truth, prevents conflicting states |
| Ctrl Mode independence | Driven by ctrlState, not KeyboardMode | Ctrl overlay works in any mode |
| Macro capture format | Logical combos (not raw keycodes) | Cleaner JSON, easier UI display, matches existing entity schema |
| Macro access | Both chips + grid panel | Two access patterns for different workflows (quick vs. management) |
| Clipboard encryption | Deferred to Session 5 | Get clipboard working first, add security in polish phase |
| Symbols layer | Included in Session 3 | Natural fit alongside toolbar button wiring |
| Ctrl Mode activation | HELD state only (not ONE_SHOT/LOCKED) | Visual transformation only makes sense during physical hold |
| Recording behavior | Keys produce input AND get captured | User should see what they're recording in real-time |
