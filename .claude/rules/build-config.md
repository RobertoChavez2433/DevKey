---
paths:
  - "**/build.gradle*"
  - "**/proguard*"
---

# Build Configuration Rules

## Gradle Kotlin DSL + Version Catalogs

Both build files use Kotlin DSL (`.gradle.kts`):
- `build.gradle.kts` — root project
- `app/build.gradle.kts` — app module

Dependency versions are managed via the version catalog
(`gradle/libs.versions.toml`). Do NOT hardcode version strings directly in
`build.gradle.kts` files. Always add new dependencies via the catalog first,
then reference them as `libs.*` in the build file.

## SDK Versions

| Setting | Value |
|---------|-------|
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` | 34 (Android 14) |
| `compileSdk` | 34 |

Do NOT lower `minSdk` below 26 — the codebase uses APIs not available on
older versions. Do NOT raise `targetSdk` without testing behavior changes
(notification permissions, intent restrictions, etc. change per API level).

## Key Dependency Versions

| Component | Version |
|-----------|---------|
| Kotlin | 2.0.21 |
| AGP | 8.5.2 |
| Gradle | 8.7 |
| KSP | 2.0.21-1.0.27 |
| Compose BOM | 2024.06.00 |
| Room | 2.6.1 |
| TF Lite | 2.16.1 |

KSP version must match the Kotlin version exactly (major.minor.patch-ksp-version).
Mismatches cause annotation processing failures.

## ProGuard Must Not Break JNI

The ProGuard/R8 configuration must keep `BinaryDictionary` and all its JNI
method signatures intact.

Required keep rule (must be present in proguard rules):
```proguard
-keep class org.pocketworkstation.pckeyboard.BinaryDictionary { *; }
```

Without this rule, R8 will rename or remove native method declarations,
silently breaking dictionary lookup in release builds while debug builds work
fine.

## connectedAndroidTest Uninstalls the App

Running instrumented tests (`./gradlew connectedAndroidTest`) removes the debug
APK from the device as part of test teardown.

After running instrumented tests, always:
```bash
./gradlew installDebug
adb shell ime enable dev.devkey.keyboard/.LatinIME
adb shell ime set dev.devkey.keyboard/.LatinIME
```

Do NOT assume the keyboard is still installed and active after a test run.

## Debug Logging Is Intentional

`KeyPressLogger` and `Log.d` calls throughout the codebase are intentional
debugging infrastructure added in Session 19. Do NOT:
- Remove them
- Gate them behind `BuildConfig.DEBUG`
- Treat them as lint warnings to be cleaned up

They are permanent diagnostic tools until explicitly decided otherwise.

## Espresso + API 36 Incompatibility

Compose BOM `2024.06.00` Espresso dependencies use `InputManager.getInstance()`
reflection that fails on Android 16 (API 36) emulators. Compose UI tests:
- Fail on API 36 emulator
- Pass on physical device and API 34 emulator

Do not upgrade to API 36 emulators for CI until Espresso deps are upgraded.
