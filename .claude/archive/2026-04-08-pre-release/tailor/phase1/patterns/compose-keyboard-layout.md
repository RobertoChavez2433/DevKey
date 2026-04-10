# Pattern: Compose Keyboard Layout

**Relevant to**: Phase 1.3, 1.4

## How we do it

Layouts are pure Kotlin data: `object QwertyLayout` / `object SymbolsLayout` expose a `getLayout(mode: LayoutMode): KeyboardLayoutData` function that returns a `KeyboardLayoutData(rows = listOf(KeyRowData(keys = listOf(KeyData(...)))))`. `KeyData` is an immutable data class with fields for primary label/code, long-press label/code, type, weight, repeatable. `DevKeyKeyboard` (a `@Composable`) consumes this tree and renders each `KeyRowData` as a Compose `Row`, each `KeyData` as a `KeyView`. Dispatch flows through `KeyboardActionBridge.onKey(code, modifierState)` → `KeyboardActionListener.onKey(...)` → `LatinIME.onKey(...)`.

## Exemplar 1 — Long-press data in the FULL layout letter row

`app/src/main/java/dev/devkey/keyboard/ui/keyboard/QwertyLayout.kt:72-81`

```kotlin
keys = listOf(
    KeyData("q", 'q'.code, longPressLabel = "!", longPressCode = '!'.code),
    KeyData("w", 'w'.code, longPressLabel = "@", longPressCode = '@'.code),
    KeyData("e", 'e'.code, longPressLabel = "#", longPressCode = '#'.code),
    KeyData("r", 'r'.code, longPressLabel = "$", longPressCode = '$'.code),
    KeyData("t", 't'.code, longPressLabel = "%", longPressCode = '%'.code),
    KeyData("y", 'y'.code, longPressLabel = "^", longPressCode = '^'.code),
    KeyData("u", 'u'.code, longPressLabel = "&", longPressCode = '&'.code),
    KeyData("i", 'i'.code, longPressLabel = "*", longPressCode = '*'.code),
    KeyData("o", 'o'.code, longPressLabel = "(", longPressCode = '('.code),
    KeyData("p", 'p'.code, longPressLabel = ")", longPressCode = ')'.code)
)
```

## Exemplar 2 — KeyView long-press detection & dispatch

`app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyView.kt:171, 213-215`

```kotlin
.pointerInput(key.primaryCode, key.type, key.longPressCode, key.isRepeatable) {
    // ... detectTapGestures / custom long-press handling ...
    onLongPress = {
        KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
        if (key.longPressCode != null) {
            onKeyAction(key.longPressCode)
        } else if (key.isRepeatable) {
            // ... repeat logic ...
        }
    }
}
```

## Reusable methods / data

| Symbol | Signature | Purpose |
|---|---|---|
| `KeyData` | `data class KeyData(primaryLabel: String, primaryCode: Int, longPressLabel: String? = null, longPressCode: Int? = null, type: KeyType = LETTER, weight: Float = 1.0f, isRepeatable: Boolean = false)` | The unit of keyboard data. Phase 1.4 adds missing long-press fields to existing instances. |
| `KeyRowData` | `data class KeyRowData(keys: List<KeyData>)` | One row. |
| `KeyboardLayoutData` | `data class KeyboardLayoutData(rows: List<KeyRowData>)` | Complete keyboard. |
| `LayoutMode` | `enum class LayoutMode { COMPACT, COMPACT_DEV, FULL }` | Layout selector. |
| `KeyType` | `enum class KeyType { LETTER, NUMBER, MODIFIER, ACTION, ARROW, SPECIAL, SPACEBAR, UTILITY, TOGGLE }` | Drives styling in `getKeyColors(key)`. |
| `QwertyLayout.getLayout(mode)` | `fun getLayout(mode: LayoutMode): KeyboardLayoutData` | Entry point — dispatches to `buildFullLayout`, `buildCompactLayout`, `buildCompactDevLayout`. |
| `SymbolsLayout.buildLayout` (private) | `private fun buildLayout(): KeyboardLayoutData` | 5KB file — single builder method. |

## Required imports (when editing layout builders)

```kotlin
import dev.devkey.keyboard.ui.keyboard.KeyData
import dev.devkey.keyboard.ui.keyboard.KeyRowData
import dev.devkey.keyboard.ui.keyboard.KeyboardLayoutData
import dev.devkey.keyboard.ui.keyboard.KeyType
import dev.devkey.keyboard.ui.keyboard.LayoutMode
import dev.devkey.keyboard.ui.keyboard.KeyCodes
```

## Phase 1.3 / 1.4 implications

**Phase 1.3 (layout fidelity)**: Tweak `DevKeyTheme` token values (colors, `keyGap`, `keyRadius`, `kbPadH`, `kbPadV`, row weights, typography tokens). Zero changes to layout builders unless row composition differs from SwiftKey. Visual diff test catches regressions.

**Phase 1.4 (long-press popup completeness)**:
- **Data fill**: Add `longPressLabel` / `longPressCode` to every `KeyData` in `QwertyLayout.buildCompactLayout`, `buildCompactDevLayout`, `SymbolsLayout.buildLayout`, and any rows in `buildFullLayout` that don't already have them (home row, spacebar row).
- **Scope clarification needed**: `KeyData.kt:27` doc comment says COMPACT mode has "no long-press on letter keys" by design. Spec §4.2 requires "Same long-press popup content on every key" — these conflict. Plan author decision.
- **Multi-char popup**: If SwiftKey has multi-char long-press popups (e.g. `a → à á â ã ä å æ`), current `longPressCode: Int?` supports only one. **KeyData may need a `longPressCodes: List<Int>? = null` field addition** — backward-compatible since it's a defaulted field. Plan author decision.
