# Defects Archive

Archived/resolved defect patterns from `_defects.md` rotation.

---

## Archived 2026-02-24

### [BUILD] 2026-02-23: Compose compiler plugin requires Kotlin 2.0+
**Pattern**: `org.jetbrains.kotlin.plugin.compose` plugin ID was introduced in Kotlin 2.0. Using it with Kotlin 1.9.x causes "plugin not found" errors.
**Prevention**: Use Kotlin 2.0+ when using the compose-compiler plugin. With Kotlin 1.x, use the older `composeOptions { kotlinCompilerExtensionVersion = "..." }` approach instead.
**Ref**: @build.gradle.kts, @gradle/libs.versions.toml
**Archived reason**: Project is on Kotlin 2.0.21, won't recur.
