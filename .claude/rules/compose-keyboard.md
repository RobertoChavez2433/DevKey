---
paths:
  - "**/ui/keyboard/**"
  - "**/ui/theme/**"
---

# Compose Keyboard UI Rules

## Theme Ownership

- Theme tokens live in `ui/theme/DevKeyTheme.kt`.
- Do not define one-off colors, typography, or Material overrides in
  `ui/keyboard/**`.
- Keep the existing teal monochrome system as the single keyboard theme source.

## Compose-Local Keys

- Keys that change Compose-only UI state must be filtered by
  `isComposeLocalCode()` before the bridge.
- Do not forward Compose-local mode or overlay keys into `LatinIME`.
- Adding a new locally handled key means updating that filter first.

## Layout Boundaries

- `LayoutMode` controls physical arrangement; `KeyboardMode` controls the active
  character layer. Do not conflate them.
- Layout changes should rebuild layout structure. Character-layer changes should
  swap maps without pretending the layout changed.
- Preserve weight-based row sizing in `QwertyLayout`; do not hardcode per-row
  `dp` heights.

## Bridge And Debug Boundaries

- Keep the bridge chain intact:
  `KeyboardActionBridge -> KeyboardActionListener -> LatinIME.onKey()`.
- Do not bypass the bridge or reimplement bridge behavior in composables.
- `KeyMapGenerator` stays in `debug/` and out of production paths.
