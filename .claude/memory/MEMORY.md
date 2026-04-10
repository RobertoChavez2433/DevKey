# DevKey Memory

## Project Shape

- Android IME fork of Hacker's Keyboard with Kotlin, Compose, Room, and a small
  C++ JNI bridge.
- Goal: preserve power-user keyboard behavior while modernizing UI, testing, and
  on-device-only assistive features.
- Durable architectural split: `core/`, `data/`, `feature/`, `ui/`, `debug/`.

## Durable Constraints

- `LatinIME.kt` is the Android lifecycle boundary and still the biggest risk
  surface. Prefer extractions over adding more unrelated logic there.
- `SettingsRepository` is the single source of truth for settings. Do not add
  new direct `SharedPreferences` reads elsewhere.
- `DevKeyTheme.kt` owns theme tokens. Do not scatter color, spacing, or
  typography literals through `ui/**`.
- `serviceScope` is the lifecycle-aware coroutine owner inside `LatinIME`.
  Do not reintroduce `GlobalScope` or detached background scopes.
- The JNI dictionary path is locked to
  `org.pocketworkstation.pckeyboard.BinaryDictionary`. The Java class, package,
  and native registration path must stay aligned.

## Build And Test Defaults

```bash
./gradlew assembleDebug
./gradlew lint
./gradlew installDebug
python tools/e2e/e2e_runner.py
```

- Use `./gradlew test` only when the task truly needs unit-test coverage.
- Do not use `./gradlew connectedAndroidTest` as routine verification; it
  disrupts the installed IME state.

## High-Value Gotchas

- In `LatinIME.onCreate()`, `sKeyboardSettings` must be initialized before
  `KeyboardSwitcher.init()`.
- In `InputMethodService`, Compose recomposition depends on the decor/root view
  lifecycle, not only the `ComposeView` lifecycle.
- `bridge.onKeyPress()` and `bridge.onKeyRelease()` must not forward
  Compose-local mode keys back into the legacy IME path.
- `ModifierStateManager` and `ChordeTracker` are both still important; avoid
  creating a third modifier source of truth.
- `adb install -r` does not guarantee a fresh IME process. Re-establish IME
  state intentionally after install steps.

## Testing And Debugging

- Shared E2E harness: `tools/e2e/`
- Shared debug/log server: `tools/debug-server/`
- Runtime coordinate and flow data belong to the harness and `.claude/test-flows/`,
  not to ad-hoc one-off shell snippets.
- Debug output may log structural state, key codes, flags, and mode transitions,
  but never typed text or credential-adjacent values.

## Context Hygiene

- Treat `.claude/plans/`, `.claude/tailor/`, `.claude/logs/`, and
  `.claude/test-results/` as artifact stores, not default context.
- `_state.md` is hot status. `MEMORY.md` is durable knowledge. Keep them both
  concise and stable.

## Current State
- All implementation sessions + layout redesign + Kotlin migration + code review complete
- Migration COMMITTED: 4 commits on main (migration, tests, plans, crash fix)
- **.claude/ restructure COMPLETE (Phase 1-6 of 2026-03-17 plan)**: New dirs added — `rules/`, `hooks/`, `architecture-decisions/`, `agent-memory/`, `specs/`, `adversarial_reviews/`, `code-reviews/`, `dependency_graphs/`, `outputs/`. CLAUDE.md rewritten with 9-skill table, domain rules, context efficiency, expanded directory reference.
- 3 keyboard modes: Compact (SwiftKey), Compact Dev (long-press numbers), Full (6-row + utility)
- Theme: teal monochrome design token system
- Build passing, 361 unit tests passing (355 + 6 new ModifierStateManager double-tap tests)
- Keyboard RUNNING on emulator — FULL mode E2E mostly verified (Session 28: typing, 123 toggle, modifiers, numbers all PASS)
- Caps Lock fix APPLIED + E2E verified (ONE_SHOT→LOCKED→OFF cycle)
- 123 mode switch FIXED + E2E verified (Normal→Symbols→Normal round-trip)
- Performance: 6.8ms avg key latency, 0 ANRs, 0 dropped keys
- Compose UI tests: 14 tests written, ALL fail on API 36 emulator (Espresso compat), 9/14 pass on physical device
- Needs: Commit Session 27 changes, COMPACT/COMPACT_DEV testing, arrow key completion, Compose test dep upgrade
