# Testing Principles

## Defaults

- Build first with `./gradlew assembleDebug`.
- Add `./gradlew lint` or `./gradlew detekt` when the changed surface warrants
  static analysis.
- Use `./gradlew test` for meaningful unit-test coverage, not as a reflex for
  every docs-only change.
- Do not use `./gradlew connectedAndroidTest` as normal verification; it
  disrupts the installed IME state.

## Windows Device Policy

- Use the Android emulator only.
- Do not verify on physical Android devices from Windows.
- Re-establish IME state intentionally after installs because `adb install -r`
  does not guarantee a fresh IME process.

## Harness Policy

- Prefer the existing E2E harness under `tools/e2e/`.
- Prefer the existing debug/log server under `tools/debug-server/`.
- Use debug server port `3950`.
- Prefer harness waits, driver checks, and structured logs over blind sleeps.

## Privacy Policy

- Never log typed text.
- Never log credentials or credential-adjacent input.
- Debug output may include structural state, key codes, flags, mode
  transitions, and harness events.

## Test Design

- Test real IME behavior through production seams.
- Do not add test-only hooks, lifecycle APIs, or production bypasses.
- Mock lower-level boundaries only after the real dependency chain and side
  effects are understood.
- If a test is hard to write honestly, extract a real production seam instead
  of inventing a test-only escape hatch.
