# Agent Routing Table

This file is for `plan-writer-agent` subagents. When dispatching a subagent, paste this file inline in the prompt so the agent can assign the correct agent to each sub-phase without reading extra files.

Use the `**Agent:**` field in each sub-phase to assign the right agent based on the files being modified.

---

## Routing Table

| File Pattern | Agent |
|-------------|-------|
| `**/LatinIME.kt`, `**/KeyEventSender.kt`, `**/Modifier*`, `**/Suggest*`, `**/WordComposer*`, `**/ChordeTracker*` | `ime-core-agent` |
| `**/ui/keyboard/**`, `**/ui/theme/**`, `**/ComposeKeyboard*`, `**/DevKeyKeyboard*`, `**/KeyView*`, `**/QwertyLayout*` | `compose-ui-agent` |
| `**/pckeyboard/**`, `**/jni/**`, `**/native-lib.cpp` | `general-purpose` (sonnet) — JNI bridge is locked; plans here are review-only unless user explicitly authorizes |
| `**/build.gradle*`, `**/proguard*`, `gradle/libs.versions.toml` | `general-purpose` (sonnet) |
| `app/src/test/**`, `app/src/androidTest/**` | `general-purpose` (sonnet) |
| All files post-phase | `code-review-agent` + `security-agent` (parallel) |
| `.claude/**` config | `general-purpose` (sonnet) |

---

## Notes

- When a sub-phase touches files matching multiple patterns, use the most specific match (e.g., a file in `ui/keyboard/` that also happens to be named `Modifier*.kt` routes to `compose-ui-agent`).
- `general-purpose` agents use sonnet model unless otherwise specified.
- Review agents (`code-review-agent`, `security-agent`, `completeness-review-agent`) are dispatched in Phase 5 of the writing-plans skill, not in plan steps. Do not include review dispatch steps in plan sub-phases.
- JNI files (`**/pckeyboard/**`, `**/jni/**`, `**/native-lib.cpp`) are locked — the package path `org.pocketworkstation.pckeyboard` must never change, as it is hardcoded in `RegisterNatives` calls.
