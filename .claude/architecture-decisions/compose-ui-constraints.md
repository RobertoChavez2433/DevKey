# Compose UI Constraints

Architecture constraints for the Jetpack Compose keyboard UI layer.

## Lifecycle Sharing

Compose inside an `InputMethodService` has a non-standard lifecycle. The
`WindowRecomposer` resolves its lifecycle from the window's decor/root view,
not from the `ComposeView` itself.

**Constraint**: Always pass the decor view to `ComposeKeyboardViewFactory.create()`
so a single lifecycle owner is shared. Two separate lifecycle owners (one on
decor, one on ComposeView) cause recomposition to stop firing after initial
composition, even when state changes occur.

This is the permanent fix established in Session 27. Do not refactor it.

## Theme Token Locations

All design tokens are defined in one place:
- File: `app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt`
- Package: `dev.devkey.keyboard.ui.theme`

Constraints:
- Do NOT define colors, typography, or spacing in `ui/keyboard/` files.
- Do NOT scatter `MaterialTheme.colorScheme` overrides across composables.
- New tokens go into `DevKeyTheme.kt` only.
- The teal monochrome design token system is canonical; do not add a parallel
  theme.

## Bridge Filtering (isComposeLocalCode)

The bridge chain (`KeyboardActionBridge → KeyboardActionListener → LatinIME`)
receives all key events by default. Keys handled entirely within Compose
(mode switches, overlays) must NOT be forwarded to the bridge.

`isComposeLocalCode()` in `DevKeyKeyboard.kt` is the filter. It must include:
- `KeyCodes.SYMBOLS` (= -2, mode change)
- `KeyCodes.EMOJI` (emoji overlay)
- `KeyCodes.KEYCODE_ALPHA` (alpha mode toggle)
- Any future key that triggers a Compose-only state change

Failure to filter causes `LatinIME.changeKeyboardMode()` to fire in parallel
with Compose state, resulting in desynced UI.

## Layout Architecture

Two orthogonal enums control keyboard display:

| Enum | Controls | Values |
|------|----------|--------|
| `LayoutMode` | Physical key arrangement | COMPACT, COMPACT_DEV, FULL |
| `KeyboardMode` | Character layer | Normal, Symbols, … |

`LayoutMode` changes require view re-inflation; `KeyboardMode` changes only
swap the key character map. Never use one enum where the other is intended.

`QwertyLayout.getLayout(mode: LayoutMode)` is the single source of layout
definitions. Do not inline layout data into composables.

## Row Height Weights

Row heights are weight ratios within the user-configurable total height
percentage. Do NOT hardcode `dp` values for rows. The weight system allows
different row sizes (utility row, number row, letter rows) to scale
proportionally with the user's preference.

## KeyMapGenerator

`KeyMapGenerator.kt` is in `dev.devkey.keyboard.debug` — a debug-only package.
It exists solely to dump key map coordinates to logcat on demand:
```
adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP
```

Do NOT reference it from production composables or production code paths.
Do NOT move it to `ui/keyboard/`.

## State Management Pattern

Compose keyboard state follows the project-wide pattern:
- State: `StateFlow` in ViewModel or manager class
- Observation: `collectAsState()` in composables
- No direct mutable state in composable functions for business logic
- `remember { mutableStateOf(...) }` only for transient UI-local state
  (e.g., pressed appearance during a single touch event)
