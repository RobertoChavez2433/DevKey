---
paths:
  - "**/LatinIME.kt"
  - "**/InputMethodService*"
---

# IME Lifecycle Rules

## Init Order in LatinIME.onCreate()

`sKeyboardSettings` MUST be initialized BEFORE `KeyboardSwitcher.init()`. The
switcher reads settings during its own init. Reversing the order causes a crash
at startup (Session 25 crash fix).

Correct sequence:
1. Initialize `sKeyboardSettings` (reads SharedPreferences)
2. Call `KeyboardSwitcher.init(this, this)`
3. Set up `serviceScope` coroutines
4. Create `ComposeKeyboardViewFactory`

Never reorder steps 1 and 2.

## serviceScope Coroutines

- Use `serviceScope` (CoroutineScope tied to the service lifecycle) for all
  background work inside `LatinIME`.
- Do NOT use `GlobalScope` — it outlives the service.
- Replace any `Handler`/`Message` patterns with `serviceScope.launch { delay(ms) }`.
- Cancel `serviceScope` in `onDestroy()`.

## ComposeKeyboardViewFactory Lifecycle Sharing

In `InputMethodService`, Compose's `WindowRecomposer` resolves from the
**decor/root view's lifecycle**, NOT the ComposeView's. If the decor view
carries a DESTROYED lifecycle, recomposition silently never runs after initial
composition — state changes do not trigger UI updates.

Fix: pass the decor view to `ComposeKeyboardViewFactory.create(ctx, listener,
decorView)` so ONE lifecycle owner is shared between the decor and the
ComposeView. Do NOT create the ComposeView with an independent lifecycle.

## adb install Does NOT Restart the IME

`adb install -r` replaces the APK but the keyboard process survives. Running
the old code continues until the process is explicitly killed.

After every install, always run:
```bash
adb shell am force-stop dev.devkey.keyboard
adb shell ime set dev.devkey.keyboard/.LatinIME
```

Never assume an `installDebug` Gradle task is sufficient for testing code
changes in a live IME session.

## KeyCodes Use ASCII Values

`KeyCodes` constants map to Android `KEYCODE_*` values via the `asciiToKeyCode`
table — an `IntArray(128)` indexed by ASCII character code. This table is
critical for Ctrl+letter synthesis (e.g., Ctrl+C, Ctrl+V) and key event
forwarding.

- Do NOT guess or invent keycode values; always look up the `asciiToKeyCode`
  table.
- `KeyCodes.SYMBOLS = -2` is an alias for `Keyboard.KEYCODE_MODE_CHANGE`.
- Negative keycodes are special actions (mode changes, etc.); positive keycodes
  are Android `KEYCODE_*` values.

## Debug Logging

`KeyPressLogger` and `Log.d` calls were added deliberately in Session 19 for
ongoing debugging. Do NOT remove them or gate them behind `BuildConfig.DEBUG`.
They are intentional and permanent until explicitly reviewed.
