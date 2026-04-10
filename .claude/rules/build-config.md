---
paths:
  - "**/build.gradle*"
  - "**/proguard*"
---

# Build Configuration Rules

## Dependency And SDK Ownership

- Build files stay in Kotlin DSL and dependency versions live in
  `gradle/libs.versions.toml`.
- Do not hardcode version strings in `build.gradle.kts` files when the catalog
  can own them.
- Keep `minSdk = 26`, `targetSdk = 34`, and `compileSdk = 34` unless the change
  is deliberate and tested.
- Keep KSP aligned with the Kotlin version exactly.

## R8 And JNI Safety

- ProGuard/R8 must preserve
  `org.pocketworkstation.pckeyboard.BinaryDictionary`.
- Keep rule required:

```proguard
-keep class org.pocketworkstation.pckeyboard.BinaryDictionary { *; }
```

## Test Install Reality

- `./gradlew connectedAndroidTest` is not a normal verification path for this
  repo. It can uninstall or disrupt the debug IME.
- After any instrumented test run, reinstall and explicitly re-enable the IME.

## Diagnostic Logging

- Existing debug logging is intentional infrastructure, not cleanup noise.
- Do not remove it or gate it behind `BuildConfig.DEBUG`.
- Logs must stay privacy-safe: structural state, timings, and key metadata are
  allowed; typed content is not.

## Known Tooling Limit

- Compose/Espresso UI tests are unreliable on API 36 with the current
  dependency stack.
- Prefer physical devices or API 34 for Compose test work until the deps move.
