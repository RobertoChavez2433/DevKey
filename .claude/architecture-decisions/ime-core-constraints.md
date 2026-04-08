# IME Core Constraints

Architecture constraints for `LatinIME.kt` and the `InputMethodService` layer.
Violating these rules causes crashes, silent failures, or broken IME behavior.

## Hard Rules for LatinIME

1. `LatinIME` is the sole `InputMethodService`. Do not create a second service
   subclass.
2. All public entry points (`onStartInput`, `onFinishInput`, `onKey`,
   `onCreateInputView`) must complete on the main thread. Never dispatch them to
   a background coroutine without re-posting results back to main.
3. `LatinIME` is ~2,500 lines. New functionality must be extracted into focused
   helper classes (e.g., `KeyEventSender`, `ModifierStateManager`) rather than
   added inline.
4. `SettingsRepository` is the single source of truth for all settings.
   `GlobalKeyboardSettings` was fully folded into it in Session 24. Do NOT
   reintroduce `GlobalKeyboardSettings` or any parallel settings holder.

## Init Ordering

`onCreate()` sequence is order-dependent:

```
1. sKeyboardSettings initialized   ← MUST be first
2. KeyboardSwitcher.init()          ← reads settings during init
3. serviceScope created
4. ComposeKeyboardViewFactory setup
5. Room database access (async)
```

Swapping steps 1 and 2 causes an NPE crash during keyboard switcher init.
This was the root cause of the Session 25 crash.

## Coroutine Patterns

- Use `serviceScope` (bound to service lifecycle) for all async work.
- `serviceScope` must be cancelled in `onDestroy()`.
- `GlobalScope` is forbidden — it survives service destruction and leaks.
- Replace any remaining `Handler(Looper.getMainLooper()).postDelayed` with
  `serviceScope.launch(Dispatchers.Main) { delay(ms); ... }`.
- Database operations: `serviceScope.launch(Dispatchers.IO) { ... }`.
- UI state updates: always `withContext(Dispatchers.Main)` before touching
  StateFlow.

## InputConnection Rules

- Always call `currentInputConnection` on the main thread; cache it only for
  the duration of a single input event handler.
- `sendKeyEvent()` is used by `KeyEventSender` for Ctrl+letter, function keys,
  and modifier combos. Do NOT call it for regular printable character input —
  use `commitText()` instead.
- Check `currentInputConnection != null` before every use; the connection can
  go null between events when the app switches focus.
- Never hold a reference to `InputConnection` across `onFinishInput()`.

## Lifecycle Sharing (ComposeKeyboardViewFactory)

The `WindowRecomposer` in Compose resolves its lifecycle from the decor/root
view, not from the `ComposeView` itself. The decor view in an IME can carry a
DESTROYED lifecycle while the ComposeView has RESUMED.

Resolution: `ComposeKeyboardViewFactory.create(ctx, listener, decorView)`
passes the decor view explicitly so one lifecycle owner is shared. This is
permanent architecture — do not refactor it away.
