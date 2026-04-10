# Agent Routing Table

Use this table when writing plans. It describes which execution style and rule
set each touched surface should use during `/implement`.

## Routing

| File Pattern | Execution Owner |
|-------------|-----------------|
| `app/src/main/java/dev/devkey/keyboard/ui/**` | `general-purpose` worker with `rules/compose-keyboard.md` |
| `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`, `app/src/main/java/dev/devkey/keyboard/core/**` | `general-purpose` worker with `rules/ime-lifecycle.md` and matching adjacent rules |
| `**/ModifierStateManager.kt`, `**/ChordeTracker.kt`, `**/KeyEventSender.kt` | `general-purpose` worker with `rules/modifier-state.md` |
| `app/src/main/java/dev/devkey/keyboard/data/**`, `app/src/main/java/dev/devkey/keyboard/feature/**/Repository.kt` | `general-purpose` worker with `rules/settings-data.md` |
| `app/src/main/java/org/pocketworkstation/pckeyboard/**`, `app/src/main/cpp/**` | `general-purpose` worker with `rules/jni-bridge.md` |
| `tools/e2e/**`, `tools/debug-server/**`, `.claude/test-flows/**` | `general-purpose` worker with `rules/testing-infra.md` |
| `**/build.gradle*`, `gradle/libs.versions.toml`, `**/proguard*` | `general-purpose` worker with `rules/build-config.md` |
| Final review gate | `code-review-agent`, `security-agent`, `completeness-review-agent` |

## Notes

- Default to fewer, wider phases instead of one phase per file.
- Use rule loading, not specialist subagents, to provide domain context.
- JNI-bound files stay review-heavy and must preserve the locked package/class
  path.
