# Pattern: Theme Token Usage

**Relevant to**: Phase 1.3

## How we do it

All visual constants (colors, dimensions, typography, animation) live in a single Kotlin `object DevKeyTheme` at `app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt`. Compose call sites reference tokens directly: `DevKeyTheme.keyBg`, `DevKeyTheme.keyText`, `DevKeyTheme.keyGap`, `DevKeyTheme.fontKey`. This is a **plain Kotlin object-of-constants**, NOT a `MaterialTheme` wrapper. No `CompositionLocal`, no light/dark variants via theme switching — the tokens ARE the dark palette (the file has no separate light palette yet).

## Exemplar 1 — Token declarations (surface + text)

`app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt:17-53`

```kotlin
object DevKeyTheme {

    // ══════════════════════════════════════════
    // Surface tokens
    // ══════════════════════════════════════════
    val kbBg = Color(0xFF1B1B1F)
    val keyBg = Color(0xFF2C2C31)
    val keyBgSpecial = Color(0xFF232327)
    val keyPressed = Color(0xFF3A3A40)

    // ... modifier tokens ...

    // ══════════════════════════════════════════
    // Text tokens
    // ══════════════════════════════════════════
    val keyText = Color(0xFFE8E8EC)
    val keyTextSpecial = Color(0xFFA0A0A8)
    val keyHint = Color(0xFF5A5A62)
```

## Exemplar 2 — Spacing / row weight / typography tokens

`DevKeyTheme.kt:55-81`

```kotlin
// Spacing tokens
val keyGap: Dp = 4.dp
val keyRadius: Dp = 6.dp
val kbPadH: Dp = 4.dp
val kbPadV: Dp = 5.dp

// Row height weight tokens (used as ratios, NOT fixed dp)
/** Full mode total: 34+38+38+38+38+30 = 216 */
const val rowNumberWeight = 34f
const val rowLetterWeight = 38f
const val rowSpaceWeight = 38f
const val rowUtilityWeight = 30f
/** Compact mode total: 42+42+42+42 = 168 */
const val rowCompactWeight = 42f

// Typography tokens
val fontKey: TextUnit = 14.sp
val fontKeySpecial: TextUnit = 11.sp
val fontKeyHint: TextUnit = 9.sp
val fontKeyUtility: TextUnit = 10.sp
val fontVoiceMic: TextUnit = 18.sp
```

## Reusable token categories

| Category | Examples | Phase 1.3 tuning |
|---|---|---|
| Surface colors | `kbBg`, `keyBg`, `keyBgSpecial`, `keyPressed` | Match SwiftKey background palette |
| Modifier colors | `modBgShift`, `modTextShift`, `modBgAction`, `modTextAction`, ... (teal monochrome) | Retune if SwiftKey uses a different modifier highlight |
| Text colors | `keyText`, `keyTextSpecial`, `keyHint` | Match SwiftKey |
| Spacing | `keyGap`, `keyRadius`, `kbPadH`, `kbPadV` | Likely tweak for exact sizing |
| Row weights | `rowNumberWeight`, `rowLetterWeight`, `rowSpaceWeight`, `rowUtilityWeight`, `rowCompactWeight` | Ratio tuning for row proportions |
| Typography | `fontKey`, `fontKeySpecial`, `fontKeyHint`, `fontKeyUtility` | Font size match |
| Animation | `pressDownMs`, `pressUpMs`, `pressScale`, `pressEase` | Press feedback match |
| Voice | `voicePanelBg`, `voiceMicActive`, `voiceMicInactive`, `voiceWaveform`, `voiceMicSize`, `voiceStatusSize` | Not in 1.3 scope |
| Clipboard / Macros / Command / Settings | `clipboardPanelBg`, `macroRecordingRed`, `cmdBadgeBg`, `settingsCategoryColor` | Not in 1.3 scope |

## Required imports

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.CubicBezierEasing
```

## Phase 1.3 implications

1. **All visual tweaks stay in DevKeyTheme.kt** — do NOT inline raw `Color(0xFF...)` or hard-coded `dp` values in KeyView / DevKeyKeyboard / layout builders. Adding new tokens is strictly preferred over inlining.
2. The file is 172 lines — well under the 400-line rule. Plenty of room to add new tokens.
3. No light-theme path exists yet. If SwiftKey parity requires a light theme variant, Phase 1.3 must either (a) add a `lightPalette` / `darkPalette` split with a runtime switch, OR (b) ship dark-only for v1.0 (spec §3 non-goals allows this — "Material You theming is sufficient for v1.0"). **Plan author decision.**
4. Row weights are `Float` **ratios**, not fixed `dp`. To increase a row's proportional height, increase its weight. Total weight sums are documented in the code comments.
