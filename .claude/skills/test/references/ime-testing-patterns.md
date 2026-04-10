# IME Testing Patterns Reference

Guide for testing an Input Method Editor via ADB and the shared DevKey harness.
Unlike a normal app, an IME renders as a system overlay and exposes more of its
state through logs and driver endpoints than through resource IDs.

## IME Vs Normal App Testing

| Aspect | Normal App | IME |
|--------|------------|-----|
| Launch | `am start` activity | `ime set` + open a text field |
| Interaction | resource IDs / accessibility | coordinate taps, driver checks, logs |
| Verification | view hierarchy | logs, driver state, screenshots |
| Lifecycle | tied to visible activity | persists across apps and installs |

## Key Principles

- Treat logs and driver state as first-class verification signals.
- Reuse harness helpers for coordinates, waits, and navigation.
- Confirm visual changes with screenshots only when the flow needs them.
- Never log or assert against real typed content.

## Known Gotchas

- `./gradlew connectedAndroidTest` disrupts installed IME state.
- `adb install -r` does not reliably restart the IME process.
- Tab and other focus-moving keys can alter the text field context while
  testing.
- Compose visual state can fail separately from internal mode state, so compare
  logs and screenshots when a mode switch looks suspicious.
