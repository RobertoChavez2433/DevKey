---
paths:
  - "**/ModifierStateManager.kt"
  - "**/ChordeTracker.kt"
  - "**/KeyEventSender.kt"
  - "**/LatinIME.kt"
  - "**/ui/keyboard/KeyView.kt"
---

# Modifier State Rules

## Single Source Of Truth

Modifier behavior is shared across `ModifierStateManager`, `ChordeTracker`,
`LatinIME`, and the Compose key surface. Do not introduce a third state owner or
duplicate modifier state in UI-local structures.

## Transition Rules

- Preserve the distinction between one-shot, latched, and locked states.
- Touch flow matters: press, release, and tap handlers must agree on the same
  state transition model.
- Changes to `KeyView` modifier gestures must stay aligned with
  `ModifierStateManager` semantics.
- Any reset path that changes keyboard mode must clear modifier state
  intentionally when that behavior is required.

## Safety Rules

- Modifier logic must remain explicit and testable. Avoid hidden state changes
  in unrelated UI callbacks.
- Do not synthesize modifier behavior with raw keycode literals. Use symbolic
  constants and the existing state helpers.
- When changing modifier behavior, verify adjacent flows: double tap, one-shot
  use, mode reset, and focus changes.
