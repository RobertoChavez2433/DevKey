# Modifier State Constraints

Architecture constraints for the dual modifier state system.

## Dual System Overview

Two classes manage modifier state, covering different modifier types:

| Class | Modifiers | Location |
|-------|-----------|----------|
| `ModifierStateManager` | Shift, Ctrl, Alt, Meta | `core/ModifierStateManager.kt` |
| `ChordeTracker` | Symbol, Fn (chording) | `ChordeTracker.kt` |

These systems are intentionally separate. `ModifierStateManager` handles the
single-key toggle/latch/lock cycle. `ChordeTracker` handles held-key chording
for Symbol and Fn layers. Unification is deferred — do not merge them without
a deliberate design decision.

## ModifierStateManager State Machine

Each modifier (`Shift`, `Ctrl`, `Alt`, `Meta`) cycles through states:

```
OFF → ONE_SHOT (first tap) → LOCKED (second tap) → OFF (third tap)
```

Additional transitions:
- `ONE_SHOT` → `OFF` after the next non-modifier key is consumed (one-shot
  fires and resets).
- `LOCKED` → `OFF` on explicit tap when already LOCKED.
- Caps Lock is a Shift-specific behavior on top of the same state machine:
  ONE_SHOT = Caps, LOCKED = Caps Lock.

The Caps Lock fix (applied and E2E verified) confirmed the ONE_SHOT→LOCKED→OFF
cycle works correctly.

## Double-Tap stateBeforeDown Tracking

The `KeyView` touch event flow for a modifier key is always:
```
onModifierDown → onModifierUp → onModifierTap
```

The down/up cycle resets state BEFORE the tap handler runs. Without correction,
a double-tap always appears as two separate single taps because by the time
`onModifierTap` fires, the state has already been reset by `onModifierUp`.

**Fix**: `stateBeforeDown` is saved at `onModifierDown` time and used by
`onModifierTap` to detect double-tap from the pre-down state.

Do NOT remove `stateBeforeDown` tracking. The 6 double-tap unit tests in
`ModifierStateManagerTest` verify this behavior. All 6 must pass.

## Multitouch State Machine

Modifier keys participate in true multitouch — a modifier can be held while
another key is pressed. Rules:
- A held modifier (`onModifierDown` without `onModifierUp`) contributes its
  state to key synthesis while held.
- `ChordeTracker` handles the Symbol/Fn chording: Symbol or Fn held + another
  key = chorded character.
- Do NOT treat modifier keys as simple boolean toggles. The state machine must
  be traversed correctly.

## Key Synthesis

`KeyEventSender.kt` (~330 lines) handles all key synthesis:
- Ctrl+letter → `InputConnection.sendKeyEvent(KeyEvent(KEYCODE_*))`  with
  `META_CTRL_ON`
- Alt+letter, Meta+letter → same pattern with respective meta state flags
- Function keys (F1–F12) → `sendKeyEvent`
- Modifier combos: set all applicable `META_*` flags simultaneously

Do NOT call `sendKeyEvent` directly from `LatinIME` for synthesized keys —
route through `KeyEventSender`.

## ChordeTracker Constraints

- `ChordeTracker` tracks held Symbol and Fn keys.
- Chording fires the alternate character for a key while Symbol/Fn is held.
- Chording does NOT go through `ModifierStateManager` — it is a separate
  parallel mechanism.
- Do not add Shift/Ctrl/Alt/Meta chording to `ChordeTracker`; those belong in
  `ModifierStateManager`.
