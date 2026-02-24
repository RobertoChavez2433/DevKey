# Session 3 Implementation Plan: Macros, Ctrl Mode, Clipboard & Symbols

**Design Doc**: `docs/plans/2026-02-23-session3-macros-ctrl-clipboard-design.md`
**Status**: Ready for implementation
**Base path**: `app/src/main/java/dev/devkey/keyboard/`

---

## Phase 1: KeyboardMode & Theme Additions

**Goal**: Define the centralized mode state and add new theme colors/dimensions for Session 3 features.

**Files to create**:

### 1.1 `ui/keyboard/KeyboardMode.kt`
- Sealed class `KeyboardMode`:
  - `object Normal : KeyboardMode()`
  - `object MacroChips : KeyboardMode()`
  - `object MacroGrid : KeyboardMode()`
  - `object MacroRecording : KeyboardMode()`
  - `object Clipboard : KeyboardMode()`
  - `object Symbols : KeyboardMode()`

**Files to modify**:

### 1.2 `ui/theme/DevKeyTheme.kt`
Add the following new constants (do NOT change any existing constants):

**Colors**:
- `ctrlModeBannerBg = Color(0x882A2A5A)` — semi-transparent blue/purple for Ctrl Mode banner
- `ctrlModeShortcutBg = Color(0xFF3A3A6A)` — blue/purple tint for shortcut keys in Ctrl Mode
- `ctrlModeRedBg = Color(0xFF5A2A2A)` — red tint for Copy/Paste keys in Ctrl Mode
- `ctrlModeDimmed = Color(0x4DE0E0E0)` — 30% opacity text for non-shortcut keys
- `ctrlModeFullDim = Color(0x1AE0E0E0)` — 10% opacity text for number row
- `macroRecordingRed = Color(0xFFE53935)` — red for recording indicator
- `macroAmber = Color(0xFFFFB300)` — amber for macro key combo labels in grid
- `chipBg = Color(0xFF333333)` — macro chip background
- `chipBorder = Color(0xFF555555)` — macro chip border
- `dashedBorder = Color(0xFF666666)` — dashed border for "+ Add" / "+ Record"
- `clipboardPanelBg = Color(0xFF222222)` — clipboard panel background
- `searchBarBg = Color(0xFF2A2A2A)` — search bar background
- `pinIcon = Color(0xFFFFB300)` — amber pin icon color
- `timestampText = Color(0xFF888888)` — grey timestamp text

**Dimensions**:
- `ctrlModeBannerHeight = 24.dp`
- `macroGridCellHeight = 56.dp`
- `macroGridMaxHeight = 168.dp` — 3 rows of cells
- `clipboardPanelMaxHeight = 200.dp`
- `clipboardEntryHeight = 48.dp`
- `macroChipHeight = 32.dp`
- `recordingBarHeight = 76.dp` — toolbar + suggestion bar combined

**Typography**:
- `ctrlModeShortcutLabelSize = 10.sp`
- `macroChipTextSize = 12.sp`
- `clipboardPreviewTextSize = 13.sp`
- `clipboardTimestampSize = 11.sp`

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 2: Macro Engine & Data Layer

**Goal**: Core macro logic — recording, replay, serialization, and Room repository.

**Files to create**:

### 2.1 `feature/macro/MacroStep.kt`
- Data class:
  ```kotlin
  data class MacroStep(
      val key: String,              // e.g., "c", "a"
      val keyCode: Int,             // e.g., 99
      val modifiers: List<String>   // e.g., ["ctrl"], ["ctrl", "shift"]
  )
  ```

### 2.2 `feature/macro/MacroSerializer.kt`
- `object MacroSerializer`:
  - `fun serialize(steps: List<MacroStep>): String` — converts to JSON string using `JSONArray`/`JSONObject`
  - `fun deserialize(json: String): List<MacroStep>` — parses JSON back to list
  - JSON format: `[{"key": "a", "keyCode": 97, "modifiers": ["ctrl"]}]`
  - Handle empty list → `"[]"`, invalid JSON → return empty list (graceful fallback)

### 2.3 `feature/macro/MacroEngine.kt`
- Class `MacroEngine`:
  - `val isRecording: Boolean` — read-only property backed by private mutable
  - Private `recordingBuffer: MutableList<MacroStep>`
  - `fun startRecording()`: clears buffer, sets isRecording = true
  - `fun captureKey(key: String, keyCode: Int, modifiers: List<String>)`: if isRecording, appends MacroStep to buffer. Ignore if modifiers-only (key is empty).
  - `fun stopRecording(): List<MacroStep>`: copies buffer, clears, sets isRecording = false, returns steps
  - `fun cancelRecording()`: clears buffer, sets isRecording = false
  - `fun replay(steps: List<MacroStep>, bridge: KeyboardActionBridge, modifierState: ModifierStateManager)`:
    - For each step: temporarily set modifier state, call `bridge.onKey(step.keyCode, modifierState)`, then reset modifiers
    - Alternative simpler approach: send key events directly via the bridge's underlying listener, constructing appropriate meta state flags from the modifiers list

### 2.4 `feature/macro/MacroRepository.kt`
- Class `MacroRepository(private val dao: MacroDao)`:
  - `fun getAllMacros(): Flow<List<MacroEntity>>` — delegates to `dao.getAllMacros()`
  - `suspend fun saveMacro(name: String, steps: List<MacroStep>)`: serializes via `MacroSerializer`, creates `MacroEntity` with `System.currentTimeMillis()`, inserts
  - `suspend fun deleteMacro(id: Long)`: delegates to dao
  - `suspend fun updateMacroName(id: Long, name: String)`: delegates to dao
  - `suspend fun incrementUsage(id: Long)`: delegates to `dao.incrementUsageCount(id)`

### 2.5 `feature/macro/DefaultMacros.kt`
- `object DefaultMacros`:
  - `fun getDefaults(): List<MacroEntity>` — returns 7 default macros:
    - Copy: `[{"key":"c","keyCode":99,"modifiers":["ctrl"]}]`
    - Paste: `[{"key":"v","keyCode":118,"modifiers":["ctrl"]}]`
    - Cut: `[{"key":"x","keyCode":120,"modifiers":["ctrl"]}]`
    - Undo: `[{"key":"z","keyCode":122,"modifiers":["ctrl"]}]`
    - Select All: `[{"key":"a","keyCode":97,"modifiers":["ctrl"]}]`
    - Save: `[{"key":"s","keyCode":115,"modifiers":["ctrl"]}]`
    - Find: `[{"key":"f","keyCode":102,"modifiers":["ctrl"]}]`
  - Each with `usageCount = 0`, `createdAt = System.currentTimeMillis()`

**Files to modify**:

### 2.6 `data/db/DevKeyDatabase.kt`
- Add `RoomDatabase.Callback` in the builder chain:
  - In `onCreate(db)`: launch coroutine to insert default macros via `DefaultMacros.getDefaults()`
  - This only runs on first database creation (not on subsequent app launches)

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 3: Clipboard Manager & Data Layer

**Goal**: System clipboard listener, Room repository, and paste logic.

**Files to create**:

### 3.1 `feature/clipboard/ClipboardRepository.kt`
- Class `ClipboardRepository(private val dao: ClipboardHistoryDao)`:
  - `fun getAll(): Flow<List<ClipboardHistoryEntity>>` — delegates to `dao.getAllEntries()`
  - `fun search(query: String): Flow<List<ClipboardHistoryEntity>>` — uses `dao.getAllEntries()` then filters in Kotlin with `content.contains(query, ignoreCase = true)`. (Simple approach; Room LIKE query can be added later if performance matters.)
  - `suspend fun addEntry(content: String)`: creates entity, inserts, then enforces max limit (50) by calling `dao.getCount()` and `dao.deleteOldest()` if over limit
  - `suspend fun pin(id: Long)` / `suspend fun unpin(id: Long)`: gets entity, updates `isPinned`, calls `dao.update()`
  - `suspend fun delete(id: Long)`: delegates to dao
  - `suspend fun clearAll()`: delegates to `dao.deleteAll()`
  - `companion object { const val MAX_ENTRIES = 50 }`

### 3.2 `feature/clipboard/DevKeyClipboardManager.kt`
- Class `DevKeyClipboardManager(private val context: Context, private val repository: ClipboardRepository)`:
  - Private `systemClipboardManager: android.content.ClipboardManager` obtained from context
  - Private `listener: OnPrimaryClipChangedListener` that reads clip text and calls `repository.addEntry(text)` in a coroutine
  - `fun startListening()`: registers the listener
  - `fun stopListening()`: unregisters the listener
  - `fun pasteEntry(content: String, bridge: KeyboardActionBridge)`: calls `bridge.onText(content)`
  - Uses `CoroutineScope(Dispatchers.IO + SupervisorJob())` for database operations
  - Guard against null/empty clips

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 4: Ctrl Shortcut Map & Symbols Layout

**Goal**: Static data definitions for Ctrl Mode overlay and the symbols keyboard layout. No UI yet.

**Files to create**:

### 4.1 `ui/keyboard/CtrlShortcutMap.kt`
- `data class ShortcutInfo(val label: String, val highlight: HighlightType = HighlightType.NORMAL)`
- `enum class HighlightType { NORMAL, RED }`
- `object CtrlShortcutMap`:
  - `val shortcuts: Map<String, ShortcutInfo>` — maps lowercase letter to shortcut info:
    - `"a"` → `ShortcutInfo("Sel All")`
    - `"c"` → `ShortcutInfo("Copy", HighlightType.RED)`
    - `"v"` → `ShortcutInfo("Paste", HighlightType.RED)`
    - `"x"` → `ShortcutInfo("Cut")`
    - `"z"` → `ShortcutInfo("Undo")`
    - `"s"` → `ShortcutInfo("Save")`
    - `"f"` → `ShortcutInfo("Find")`
    - `"n"` → `ShortcutInfo("New")`
    - `"o"` → `ShortcutInfo("Open")`
    - `"p"` → `ShortcutInfo("Print")`
    - `"q"` → `ShortcutInfo("Quit")`
    - `"w"` → `ShortcutInfo("Close")`
    - `"r"` → `ShortcutInfo("Redo")`
    - `"t"` → `ShortcutInfo("Tab")`
    - `"y"` → `ShortcutInfo("Redo")`
    - `"d"` → `ShortcutInfo("Dup")`
    - `"h"` → `ShortcutInfo("Replace")`
    - `"l"` → `ShortcutInfo("GoTo")`
  - `fun getShortcut(key: String): ShortcutInfo?` — looks up by lowercase

### 4.2 `ui/keyboard/SymbolsLayout.kt`
- Kotlin object that builds a `KeyboardLayoutData` for the symbols layer
- **Row 1** (10 keys): `1` through `0` (all `KeyType.NUMBER`, weight 1.0)
- **Row 2** (10 keys): `@`, `#`, `$`, `_`, `&`, `-`, `+`, `(`, `)`, `/` (all `KeyType.SPECIAL`, weight 1.0). Long-press variants: `$`→`€`, `(`→`<`, `)`→`>`, `/`→`\`
- **Row 3** (8 keys): `*`, `"`, `'`, `:`, `;`, `!`, `?`, `,` (all `KeyType.SPECIAL`, weight 1.0). Long-press variants: `"`→`"`, `'`→`'`, `!`→`¡`, `?`→`¿`
- **Row 4** (10 keys): `~`, `` ` ``, `|`, `•`, `√`, `π`, `÷`, `×`, `¶`, `∆` (all `KeyType.SPECIAL`, weight 1.0). Long-press: `~`→`€`, `` ` ``→`£`, `|`→`¥`, `•`→`¢`, `√`→`₩`
- **Row 5** (4 keys): `ABC` button (weight 1.5, `KeyType.ACTION`, code = custom `KEYCODE_ALPHA` = -200), `,` (weight 1.0), Space (weight 5.0, `KeyType.SPACEBAR`), `.` (weight 1.0), Enter (weight 1.5, `KeyType.ACTION`)
- Use Unicode char codes for special symbols

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 5: Macro UI Components

**Goal**: All three macro UI surfaces — chip strip, grid panel, and recording bar.

**Files to create**:

### 5.1 `ui/macro/MacroChipStrip.kt`
- `@Composable fun MacroChipStrip(macros: List<MacroEntity>, onMacroClick: (MacroEntity) -> Unit, onAddClick: () -> Unit, onCollapse: () -> Unit)`
- Height: `DevKeyTheme.suggestionBarHeight` (40dp) to match the suggestion bar it replaces
- Collapse arrow on left (same as SuggestionBar: `◀`, tappable, calls `onCollapse`)
- `LazyRow` with macro chips:
  - Each chip: `Row` with `⚡` icon + macro name text, background `chipBg`, rounded 16dp, padding 8dp horizontal / 4dp vertical
  - Below name: key combo in grey (parse from `keySequence` JSON to display "Ctrl+C" etc.)
  - Tap → `onMacroClick(macro)`
- Last item: "+ Add" chip with dashed border (`dashedBorder` color), tap → `onAddClick()`
- Sorted by `usageCount` descending (already sorted from DAO)

### 5.2 `ui/macro/MacroGridPanel.kt`
- `@Composable fun MacroGridPanel(macros: List<MacroEntity>, onMacroClick: (MacroEntity) -> Unit, onRecordClick: () -> Unit, onEditMacro: (MacroEntity) -> Unit, onDeleteMacro: (MacroEntity) -> Unit)`
- Max height: `macroGridMaxHeight` (168dp), scrollable
- `LazyVerticalGrid` with 4 columns
- Each cell: `Column` centered, height `macroGridCellHeight` (56dp)
  - Top: key combo in amber bold text (`macroAmber`, 12sp)
  - Bottom: macro name in grey text (11sp)
  - Background: `chipBg`, rounded 8dp, margin 4dp
  - Tap → `onMacroClick(macro)`
  - Long-press → show `AlertDialog` with "Edit name" / "Delete" / "Cancel" options
    - Edit → second dialog with text field, save calls `onEditMacro(updatedMacro)`
    - Delete → `onDeleteMacro(macro)`
- Last cell: "+ Record" with dashed border, tap → `onRecordClick()`

### 5.3 `ui/macro/MacroRecordingBar.kt`
- `@Composable fun MacroRecordingBar(capturedSteps: List<MacroStep>, onCancel: () -> Unit, onStop: () -> Unit)`
- Height: `recordingBarHeight` (76dp)
- Background: `keyboardBackground` with subtle red-tinted top border (2dp, `macroRecordingRed`)
- Layout: `Row(verticalAlignment = CenterVertically)`
  - Left section: Red pulsing circle (8dp, `macroRecordingRed`, infinite `animateFloat` 0.3f → 1.0f) + "Recording..." text in red
  - Center section: `LazyRow` showing captured steps as text chips (e.g., "Ctrl+A", "Ctrl+C") with `→` arrows between them. Horizontally scrollable, auto-scrolls to end as new steps are captured.
  - Right section: "Cancel" text button (grey text) + "Stop" text button (red text, `macroRecordingRed`)
- On Stop, show naming dialog:
  - `AlertDialog`: title "Name this macro", `OutlinedTextField` for name, "Save" + "Cancel" buttons
  - Save button disabled if name is empty
  - On Save: calls `onStop()` (parent handles save logic)

### 5.4 `ui/macro/MacroKeyComboText.kt`
- `@Composable fun MacroKeyComboText(steps: List<MacroStep>, textColor: Color, textSize: TextUnit)`
- Utility composable that renders a macro's steps as readable text
- Format: "Ctrl+A → Ctrl+C" (modifiers capitalized, joined with "+", steps joined with " → ")
- `fun formatSteps(steps: List<MacroStep>): String` — static helper version
- Also: `fun formatFromJson(json: String): String` — parses JSON then formats

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 6: Clipboard & Ctrl Mode UI Components

**Goal**: Clipboard panel, Ctrl Mode banner, and KeyView modifications for Ctrl Mode overlay.

**Files to create**:

### 6.1 `ui/clipboard/ClipboardPanel.kt`
- `@Composable fun ClipboardPanel(entries: List<ClipboardHistoryEntity>, onPaste: (ClipboardHistoryEntity) -> Unit, onPin: (Long) -> Unit, onUnpin: (Long) -> Unit, onDelete: (Long) -> Unit, onClearAll: () -> Unit)`
- Max height: `clipboardPanelMaxHeight` (200dp), background `clipboardPanelBg`
- **Search bar** at top: `OutlinedTextField` with leading magnifying glass icon, background `searchBarBg`, rounded 8dp. Filters entries locally on text change.
- **Pinned entries section**: if any pinned entries, show them first with a pin icon (🔒 or 📌) in amber. Sorted by timestamp DESC within pinned.
- **Entry list**: `LazyColumn` of clipboard entries
  - Each entry: `Row` with content preview (truncated to 60 chars, single line, `clipboardPreviewTextSize`) + relative timestamp on right (`timestampText` color, `clipboardTimestampSize`)
  - Tap → `onPaste(entry)`
  - Long-press → `DropdownMenu` with Pin/Unpin + Delete options
  - Pinned entries show pin icon before content
- **Clear All** button at bottom: text button "Clear All", grey text. On tap → `AlertDialog` confirmation "Clear all clipboard history? Pinned items will also be removed." → Yes/Cancel.
- **Empty state**: If `entries` is empty, show centered text "No clipboard history" in grey

### 6.2 `ui/keyboard/CtrlModeBanner.kt`
- `@Composable fun CtrlModeBanner()`
- Height: `ctrlModeBannerHeight` (24dp)
- Background: `ctrlModeBannerBg` (semi-transparent blue/purple)
- Centered text: "CTRL MODE — tap a key for shortcut" in `keyText` color, 11sp
- Animated slide-in: `AnimatedVisibility(enter = slideInVertically(initialOffsetY = { -it }), exit = slideOutVertically(targetOffsetY = { -it }))`
  - Note: the `AnimatedVisibility` wrapper goes in `DevKeyKeyboard.kt` where the banner is placed, not inside the banner composable itself. The banner itself is a simple Row with text.

**Files to modify**:

### 6.3 `ui/keyboard/KeyView.kt`
Add Ctrl-held visual transformation. The composable receives a new parameter: `ctrlHeld: Boolean = false`.

When `ctrlHeld` is `true` AND the key is a `LETTER` type:
- Look up `CtrlShortcutMap.getShortcut(key.primaryLabel.lowercase())`
- **Shortcut found (NORMAL highlight)**:
  - Background tints to `ctrlModeShortcutBg` instead of normal `keyFill`
  - Letter label stays centered, keep same size
  - Below the letter: shortcut label in `ctrlModeShortcutLabelSize` (10sp), white text
  - Long-press hint hidden
- **Shortcut found (RED highlight)**:
  - Same as above but background is `ctrlModeRedBg`
- **No shortcut found**:
  - Key text alpha = 0.3f (`ctrlModeDimmed`)
  - Background unchanged (`keyFill`)
  - Long-press hint hidden

When `ctrlHeld` is `true` AND the key is a `NUMBER` type:
- Key text alpha = 0.1f (`ctrlModeFullDim`)
- Background unchanged

When `ctrlHeld` is `true` AND key is `MODIFIER`, `ARROW`, `ACTION`, or `SPACEBAR`:
- No change — keep normal appearance

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 7: Root Composable Wiring

**Goal**: Wire all Session 3 features into `DevKeyKeyboard.kt` and connect toolbar buttons.

**Files to modify**:

### 7.1 `ui/keyboard/DevKeyKeyboard.kt` — Major rewrite
This is the root composable. Replace the current simple implementation with full mode-aware wiring.

New parameters and state:
- `val context = LocalContext.current`
- `val database = remember { DevKeyDatabase.getInstance(context) }`
- `val macroRepo = remember { MacroRepository(database.macroDao()) }`
- `val clipboardRepo = remember { ClipboardRepository(database.clipboardHistoryDao()) }`
- `val macroEngine = remember { MacroEngine() }`
- `var keyboardMode by remember { mutableStateOf<KeyboardMode>(KeyboardMode.Normal) }`
- `val ctrlState by modifierState.ctrlState.collectAsState()`
- `val macros by macroRepo.getAllMacros().collectAsState(initial = emptyList())`
- `val clipboardEntries by clipboardRepo.getAll().collectAsState(initial = emptyList())`
- `val coroutineScope = rememberCoroutineScope()`
- `var macroNameDialogVisible by remember { mutableStateOf(false) }`
- `var pendingMacroSteps by remember { mutableStateOf<List<MacroStep>>(emptyList()) }`

Toggle helper:
```kotlin
fun toggleMode(target: KeyboardMode) {
    keyboardMode = if (keyboardMode == target) KeyboardMode.Normal else target
}
```

Layout structure:
```
Column(Modifier.fillMaxWidth()) {
    // 1. Toolbar (hidden during recording)
    if (keyboardMode !is KeyboardMode.MacroRecording) {
        ToolbarRow(
            onClipboard = { toggleMode(KeyboardMode.Clipboard) },
            onVoice = { /* Session 4 — show toast */ },
            onSymbols = { toggleMode(KeyboardMode.Symbols) },
            onMacros = { toggleMode(KeyboardMode.MacroChips) },
            onMacrosLongPress = { toggleMode(KeyboardMode.MacroGrid) },
            onOverflow = { /* Session 5 — show toast */ }
        )
    }

    // 2. Dynamic middle area
    when (keyboardMode) {
        KeyboardMode.Normal -> SuggestionBar(...)
        KeyboardMode.MacroChips -> MacroChipStrip(
            macros = macros,
            onMacroClick = { macro ->
                val steps = MacroSerializer.deserialize(macro.keySequence)
                macroEngine.replay(steps, bridge, modifierState)
                coroutineScope.launch { macroRepo.incrementUsage(macro.id) }
                keyboardMode = KeyboardMode.Normal
            },
            onAddClick = {
                macroEngine.startRecording()
                keyboardMode = KeyboardMode.MacroRecording
            },
            onCollapse = { keyboardMode = KeyboardMode.Normal }
        )
        KeyboardMode.MacroGrid -> {
            SuggestionBar(...)  // Suggestion bar stays visible above grid
            MacroGridPanel(
                macros = macros,
                onMacroClick = { /* same as chip click */ },
                onRecordClick = {
                    macroEngine.startRecording()
                    keyboardMode = KeyboardMode.MacroRecording
                },
                onEditMacro = { macro -> coroutineScope.launch { macroRepo.updateMacroName(macro.id, macro.name) } },
                onDeleteMacro = { macro -> coroutineScope.launch { macroRepo.deleteMacro(macro.id) } }
            )
        }
        KeyboardMode.MacroRecording -> MacroRecordingBar(
            capturedSteps = macroEngine.getCapturedSteps(),
            onCancel = {
                macroEngine.cancelRecording()
                keyboardMode = KeyboardMode.Normal
            },
            onStop = {
                pendingMacroSteps = macroEngine.stopRecording()
                macroNameDialogVisible = true
            }
        )
        KeyboardMode.Clipboard -> {
            SuggestionBar(...)
            ClipboardPanel(
                entries = clipboardEntries,
                onPaste = { entry ->
                    bridge.onText(entry.content)
                    keyboardMode = KeyboardMode.Normal
                },
                onPin = { id -> coroutineScope.launch { clipboardRepo.pin(id) } },
                onUnpin = { id -> coroutineScope.launch { clipboardRepo.unpin(id) } },
                onDelete = { id -> coroutineScope.launch { clipboardRepo.delete(id) } },
                onClearAll = { coroutineScope.launch { clipboardRepo.clearAll() } }
            )
        }
        KeyboardMode.Symbols -> { /* no suggestion bar */ }
    }

    // 3. Ctrl Mode banner (independent of KeyboardMode)
    AnimatedVisibility(visible = ctrlState == ModifierKeyState.HELD) {
        CtrlModeBanner()
    }

    // 4. Keyboard
    KeyboardView(
        layout = if (keyboardMode is KeyboardMode.Symbols) SymbolsLayout.layout else QwertyLayout.layout,
        modifierState = modifierState,
        ctrlHeld = ctrlState == ModifierKeyState.HELD,
        onKeyAction = { code ->
            // Capture for macro recording
            if (macroEngine.isRecording) {
                val modifiers = buildList {
                    if (modifierState.isCtrlActive()) add("ctrl")
                    if (modifierState.isAltActive()) add("alt")
                    if (modifierState.isShiftActive()) add("shift")
                }
                val key = if (code in 32..126) code.toChar().toString() else ""
                if (key.isNotEmpty()) {
                    macroEngine.captureKey(key, code, modifiers)
                }
            }
            // Handle ABC key from symbols layout
            if (code == SymbolsLayout.KEYCODE_ALPHA) {
                keyboardMode = KeyboardMode.Normal
            } else {
                bridge.onKey(code, modifierState)
            }
        },
        onKeyPress = { code -> bridge.onKeyPress(code) },
        onKeyRelease = { code -> bridge.onKeyRelease(code) }
    )
}

// Macro naming dialog
if (macroNameDialogVisible) {
    MacroNameDialog(
        onSave = { name ->
            coroutineScope.launch {
                macroRepo.saveMacro(name, pendingMacroSteps)
            }
            macroNameDialogVisible = false
            keyboardMode = KeyboardMode.Normal
        },
        onCancel = {
            macroNameDialogVisible = false
            keyboardMode = KeyboardMode.Normal
        }
    )
}
```

### 7.2 `ui/toolbar/ToolbarRow.kt` — Add long-press and active state
- Add `onMacrosLongPress: () -> Unit` parameter
- Add `activeMode: KeyboardMode` parameter for visual feedback (highlight active button)
- The ⚡ button uses `combinedClickable(onClick = onMacros, onLongClick = onMacrosLongPress)` (from `foundation.combinedClickable`)
- Active button gets brighter icon color (`keyText` instead of `#888888`)
- Remove "Coming soon" toasts from Clipboard, Symbols, and Macros buttons (they now have real actions)
- Keep "Coming soon" toast for Voice and Overflow

### 7.3 `ui/keyboard/KeyboardView.kt` — Pass ctrlHeld
- Add `ctrlHeld: Boolean = false` parameter
- Pass `ctrlHeld` to each `KeyRow` → each `KeyView`

### 7.4 `ui/keyboard/KeyRow.kt` — Pass ctrlHeld
- Add `ctrlHeld: Boolean = false` parameter
- Pass to each `KeyView`

### 7.5 `ui/macro/MacroNameDialog.kt` (new file)
- `@Composable fun MacroNameDialog(onSave: (String) -> Unit, onCancel: () -> Unit)`
- `AlertDialog` with title "Name this macro"
- `OutlinedTextField` for name input
- "Save" button (enabled when name is not blank) + "Cancel" button
- Save calls `onSave(name.trim())`

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 8: IME Integration & Clipboard Listener

**Goal**: Wire clipboard listener into IME lifecycle and register the MacroEngine for recording access.

**Files to modify**:

### 8.1 `core/LatinIME.java` — Clipboard listener lifecycle
Add the following:
- New field: `private DevKeyClipboardManager clipboardManager;`
- In `onCreate()` (after `super.onCreate()`):
  ```java
  ClipboardRepository clipboardRepo = new ClipboardRepository(
      DevKeyDatabase.Companion.getInstance(this).clipboardHistoryDao()
  );
  clipboardManager = new DevKeyClipboardManager(this, clipboardRepo);
  clipboardManager.startListening();
  ```
- In `onDestroy()` (before `super.onDestroy()`):
  ```java
  if (clipboardManager != null) {
      clipboardManager.stopListening();
  }
  ```
- Import `dev.devkey.keyboard.feature.clipboard.DevKeyClipboardManager`
- Import `dev.devkey.keyboard.feature.clipboard.ClipboardRepository`
- Import `dev.devkey.keyboard.data.db.DevKeyDatabase`

Note: Since `DevKeyClipboardManager` is Kotlin, ensure it's accessible from Java. Add `@JvmStatic` or use companion object methods if needed, or construct directly since it's a regular class.

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 9: Build Verification & Cleanup

**Goal**: Final build, cleanup, and verification.

### 9.1 Full build
- Run `./gradlew assembleDebug`
- Fix any compilation errors

### 9.2 Cleanup
- Delete `.gitkeep` files from directories that now have real files: `feature/macro/`, `feature/clipboard/`, `ui/macro/`, `ui/clipboard/`
- Verify no unused imports in new files
- Verify no circular dependencies

### 9.3 Verify imports
- All new Compose files import from `androidx.compose.*`
- All Room operations import from `dev.devkey.keyboard.data.db.*`
- All feature classes are properly accessible from Compose UI files

**Final build gate**: `./gradlew assembleDebug` passes cleanly.

---

## Dependency Graph

```
Phase 1 (KeyboardMode + Theme Additions)
  ├── Phase 2 (Macro Engine + Data Layer)
  │     └── Phase 5 (Macro UI Components)
  │           └── Phase 7 (Root Composable Wiring)
  ├── Phase 3 (Clipboard Manager + Data Layer)
  │     └── Phase 6 (Clipboard + Ctrl Mode UI)
  │           └── Phase 7 (Root Composable Wiring)
  └── Phase 4 (Ctrl Shortcut Map + Symbols Layout)
        └── Phase 6 (Clipboard + Ctrl Mode UI)
              └── Phase 7 (Root Composable Wiring)

Phase 7 → Phase 8 (IME Integration) → Phase 9 (Build & Cleanup)
```

Phases 2, 3, and 4 can run in parallel (independent). Phases 5 and 6 can run in parallel (independent). All other phases are sequential.

---

## Files Summary

**New files (15)**:
1. `ui/keyboard/KeyboardMode.kt` — Sealed class for mode management
2. `ui/keyboard/CtrlShortcutMap.kt` — Shortcut label definitions
3. `ui/keyboard/CtrlModeBanner.kt` — Ctrl Mode banner composable
4. `ui/keyboard/SymbolsLayout.kt` — Symbols keyboard layout data
5. `feature/macro/MacroStep.kt` — Macro step data model
6. `feature/macro/MacroSerializer.kt` — JSON serialization
7. `feature/macro/MacroEngine.kt` — Recording/replay logic
8. `feature/macro/MacroRepository.kt` — Room-backed CRUD
9. `feature/macro/DefaultMacros.kt` — Pre-populated default macros
10. `feature/clipboard/ClipboardRepository.kt` — Room-backed CRUD
11. `feature/clipboard/DevKeyClipboardManager.kt` — System clipboard listener
12. `ui/macro/MacroChipStrip.kt` — Macro chips in suggestion bar
13. `ui/macro/MacroGridPanel.kt` — 4-column macro grid
14. `ui/macro/MacroRecordingBar.kt` — Recording mode UI
15. `ui/macro/MacroNameDialog.kt` — Naming dialog
16. `ui/macro/MacroKeyComboText.kt` — Key combo display utility
17. `ui/clipboard/ClipboardPanel.kt` — Clipboard history panel

**Modified files (7)**:
1. `ui/theme/DevKeyTheme.kt` — New colors, dimensions, typography
2. `data/db/DevKeyDatabase.kt` — RoomDatabase.Callback for default macros
3. `ui/keyboard/KeyView.kt` — Ctrl-held visual transformation
4. `ui/keyboard/KeyboardView.kt` — Pass ctrlHeld parameter
5. `ui/keyboard/KeyRow.kt` — Pass ctrlHeld parameter
6. `ui/keyboard/DevKeyKeyboard.kt` — Full mode-aware wiring
7. `ui/toolbar/ToolbarRow.kt` — Long-press, active state, real callbacks
8. `core/LatinIME.java` — Clipboard listener lifecycle

## Known Risks

- **Compose combinedClickable**: Long-press detection on toolbar ⚡ button requires `foundation.combinedClickable` modifier. Ensure the correct dependency is imported (`androidx.compose.foundation`).
- **Room callback threading**: `RoomDatabase.Callback.onCreate` runs on the query executor thread. Use the DAO directly or ensure proper coroutine scoping for default macro insertion.
- **System clipboard listener**: `OnPrimaryClipChangedListener` fires on the main thread. Ensure Room writes are dispatched to IO.
- **Macro replay timing**: Replaying macros by calling `bridge.onKey()` in a tight loop may be too fast for some apps. May need small delays between steps (10-50ms). Start without delays and add if needed.
- **Symbols Unicode keycodes**: Characters like `π`, `÷`, `×` need correct Unicode code points. Use `.code` on the Char to get the Int keycode.
