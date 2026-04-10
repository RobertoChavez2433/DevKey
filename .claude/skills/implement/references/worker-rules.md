# Worker Rules

Static context for generic implementation and fixer workers.

## Core Rules

- Read every target file before editing it.
- Load the matching `.claude/rules/*.md` files before writing code.
- Do not widen scope beyond the assigned phase or file set.
- Do not write TODO stubs, placeholder logic, or dead code.
- Do not weaken privacy or security checks to make a change easier.
- Do not move or rename the JNI-bound dictionary class or package.
- Do not touch `.claude/autoload/_state.md` or project state files unless the
  plan explicitly says to.

## Verification

- Run `./gradlew assembleDebug` for production-code changes unless the plan says
  otherwise.
- Use targeted verification for the touched harness or script surface when
  relevant.
- Do not use `./gradlew connectedAndroidTest` as a routine local step.

## Domain Rule Loading

Load the matching rules for the files in scope:

| File pattern | Rule files |
|-------------|------------|
| `**/LatinIME.kt`, `**/InputMethodService*` | `rules/ime-lifecycle.md` |
| `**/ui/keyboard/**`, `**/ui/theme/**` | `rules/compose-keyboard.md` |
| `**/ModifierStateManager.kt`, `**/ChordeTracker.kt`, `**/KeyEventSender.kt` | `rules/modifier-state.md` |
| `**/SettingsRepository.kt`, `**/data/**`, `**/feature/**/Repository.kt` | `rules/settings-data.md` |
| `**/pckeyboard/**`, `app/src/main/cpp/**` | `rules/jni-bridge.md` |
| `tools/e2e/**`, `tools/debug-server/**`, `.claude/test-flows/**` | `rules/testing-infra.md` |
| `**/build.gradle*`, `**/proguard*`, `gradle/libs.versions.toml` | `rules/build-config.md` |

After loading, print:

`[CONTEXT] Rules loaded: <filenames>`
