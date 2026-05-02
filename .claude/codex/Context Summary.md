# Context Summary

## Project Snapshot

- App: DevKey, an Android IME fork of Hacker's Keyboard.
- Stack: Kotlin, Compose, Room, Gradle, Python ADB E2E harness, and a small C++
  JNI dictionary bridge.
- Shape: `core/`, `data/`, `feature/`, `ui/`, `debug/`,
  `tools/e2e/`, and `tools/debug-server/`.
- Core constraint: input privacy is non-negotiable. Do not log typed text or
  credential-adjacent input.

## Core Entry Points

- `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` owns the Android IME
  lifecycle boundary.
- `app/src/main/java/dev/devkey/keyboard/data/repository/SettingsRepository.kt`
  is the settings source of truth.
- `app/src/main/java/dev/devkey/keyboard/core/KeyEventSender.kt` participates
  in the final key dispatch path.
- `app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt` and sibling
  theme files own UI tokens.
- `app/src/main/java/org/pocketworkstation/pckeyboard/BinaryDictionary.java`
  and `app/src/main/cpp/org_pocketworkstation_pckeyboard_BinaryDictionary.cpp`
  must keep the JNI package/class path aligned.

## Current Active Context

- Latest session handoff source: `.claude/autoload/_state.md`.
- Durable project patterns: `.claude/memory/MEMORY.md`.
- Directory map: `.claude/docs/INDEX.md`.
- Current state from the live handoff: v1.0 pre-release stress-test QA is in
  E2E stabilization after stress-test implementation and a test-quality audit.
- Current next work from the live handoff: stabilize the remaining emulator E2E
  failures, then handle accumulated local commits.

## Verification Defaults

- Default build: `./gradlew assembleDebug`.
- Add `./gradlew lint` or `./gradlew detekt` when the touched surface warrants
  static checks.
- Use `./gradlew test` only when the change needs unit-test coverage.
- Avoid routine `./gradlew connectedAndroidTest`; it disrupts installed IME
  state.
- On Windows, use the Android emulator only.
- E2E harness: `tools/e2e/e2e_runner.py`.
- Debug/log server: `tools/debug-server/`, port `3950`.

## Use This Directory To Stay Lean

- `.codex/PLAN.md` indexes active planning without loading historical plans.
- `.codex/CLAUDE_CONTEXT_BRIDGE.md` maps the exact `.claude/` files to open
  for session handoff, feature context, agents, skills, and rules.
- `.codex/skills/*.md` are Codex-facing wrappers around the maintained
  `.claude/skills/*/SKILL.md` workflows.
