---
paths:
  - "**/ui/keyboard/**"
  - "**/ui/theme/**"
---

# Compose Keyboard UI Rules

## Theme Tokens Location

All design tokens (colors, typography, shapes, spacing) live in
`app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt`.

- Do NOT define colors or typography constants inside `ui/keyboard/`.
- Do NOT scatter Material3 color overrides across composables.
- Always reference tokens via `DevKeyTheme.colors.*` or the Material3 theme
  provided by `DevKeyTheme { }`.

The teal monochrome token system is the canonical theme. Do not add a second
theme file.

## isComposeLocalCode Filter

`bridge.onKeyPress(code)` and `bridge.onKeyRelease(code)` forward ALL keycodes
to `LatinIME`. For keys handled locally by Compose (`SYMBOLS`, `EMOJI`,
`KEYCODE_ALPHA`), this causes `LatinIME.changeKeyboardMode()` to fire,
interfering with Compose state.

`isComposeLocalCode()` in `DevKeyKeyboard.kt` filters these codes before
forwarding to the bridge. Rules:
- Any keycode that triggers a Compose-side UI state change (mode, overlay) MUST
  be listed in `isComposeLocalCode()`.
- Never forward Compose-local codes to `KeyboardActionBridge`.
- Adding a new locally-handled key? Add it to `isComposeLocalCode()` first.

## LayoutMode Is Separate from KeyboardMode

`LayoutMode` enum (`COMPACT`, `COMPACT_DEV`, `FULL`) controls the physical key
layout (number of rows, key arrangement). `KeyboardMode` (`Normal`, `Symbols`,
etc.) controls the character layer active on the keys.

- Never conflate them.
- `QwertyLayout.getLayout(mode: LayoutMode)` returns the key arrangement for a
  given layout mode.
- A `LayoutMode` change requires re-inflation of the keyboard view; a
  `KeyboardMode` change only swaps the character map.

## Row Height Weight Ratios

Row heights are expressed as weight ratios within the user's percentage-based
height preference, not as fixed dp values. When adding or modifying rows:
- Preserve the relative weight ratios defined in `QwertyLayout`.
- Do NOT hardcode `dp` heights for individual rows.
- The utility row (top) and number row have distinct weights from the main QWERTY
  rows.

## KeyMapGenerator Package

`KeyMapGenerator.kt` lives in the `debug/` package
(`dev.devkey.keyboard.debug`), NOT in `ui/keyboard/`. It is a debug-only tool
for dumping key coordinates to logcat.

- Do NOT move it to `ui/keyboard/`.
- Do NOT call it from production code paths.
- Trigger via broadcast: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`

## Compose Bridge Architecture

The bridge chain is:
```
KeyboardActionBridge → KeyboardActionListener → LatinIME.onKey()
```

`KeyboardActionBridge` provides:
- Shift-uppercase transformation
- Smart backspace (word delete on long-press)
- Modifier key consumption

This bridge is permanent (evaluated and retained in Session 24). Do not bypass
it or duplicate its functionality in composables.
