---
paths:
  - "**/LatinIME.kt"
  - "**/InputMethodService*"
---

# IME Lifecycle Rules

## Startup Order

- In `LatinIME.onCreate()`, initialize `sKeyboardSettings` before
  `KeyboardSwitcher.init()`.
- Do not reorder those two steps. The switcher reads settings during init.

## Service Lifetime

- Use `serviceScope` for background work owned by the IME service.
- Do not use `GlobalScope`.
- Cancel service-owned work in `onDestroy()`.

## Compose Lifecycle Sharing

- `ComposeKeyboardViewFactory` must share the decor/root lifecycle with the
  Compose view.
- Do not create an independent Compose lifecycle inside the IME window.

## Install And Restart Reality

- `adb install -r` does not reliably restart the IME process or refresh state.
- Re-establish IME state intentionally after installs.
- Do not rely on `am force-stop` as the default reset path on Samsung devices;
  it can deregister the IME.

## Keycode Discipline

- `asciiToKeyCode` is the source of truth for ASCII-driven key synthesis.
- Do not invent keycode values.
- Treat negative keycodes as special actions and verify them before changing
  mode-handling paths.

## Debug Logging

- Existing IME debug logging is intentional.
- Do not remove it or gate it behind `BuildConfig.DEBUG`.
- Keep it privacy-safe.
