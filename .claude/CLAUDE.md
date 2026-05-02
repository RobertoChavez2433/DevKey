# DevKey

Android IME fork of Hacker's Keyboard with Kotlin/Compose modernization,
power-user key handling, and on-device-only input features.

**Privacy is non-negotiable. Do not log typed text, weaken JNI safety, or turn
test/debug helpers into production behavior.**

## Startup Context

- Use `.claude/autoload/_state.md` for current phase, blockers, and next tasks.
- Use `.claude/memory/MEMORY.md` only for durable project patterns and gotchas.
- Load only the `.claude/rules/` files that match the files being edited.
- Do not preload broad historical artifacts from `.claude/plans/`, `.claude/tailor/`,
  `.claude/test-results/`, or `.claude/archive/` unless the task needs them.

## Working Rules

- For small, clear work, act directly. Use the full skill pipeline only for
  medium or larger changes.
- Keep `./gradlew assembleDebug` green. Use `./gradlew lint` when the touched
  surface warrants it.
- Do not use `./gradlew connectedAndroidTest` as a normal verification step; it
  disrupts the installed IME state.
- GitHub Issues are the durable defect record. Do not create local defect files.
- Keep `.claude` live files lean. Plans, logs, tailor output, and test artifacts
  are support material, not always-on context.

## Git Commits

- Treat commit history as the durable narrative layer for committed decisions.
- Active state files track in-progress work; git history preserves lasting
  intent.
- Follow Conventional Commits: `<type>(<scope>): <subject>`.
- Valid types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `chore`,
  `ci`, `build`.
- Scopes are enforced; see `scripts/git/valid-scopes.txt`.
- Scope is required for `feat`, `fix`, `refactor`, and `perf` commits.
- Subject line: imperative mood, no period, and 72 characters or fewer.
- Body is required for `feat`, `fix`, `refactor`, and `perf`.
- Body is required for any scoped `test`, `docs`, `chore`, `ci`, or `build`
  commit.
- Mechanical-only lightweight commits may stay unscoped and terse only when no
  meaningful product, architecture, process, or test-strategy decision was made.
- Body explains why: problem or need, decision or approach, tradeoff, and
  evidence.
- `Reason:` trailer is required for narrative commits and must parse as a real
  git trailer.
- Optional trailers: `Decision:`, `Tradeoff:`, `Evidence:`, `Follow-up:`,
  `Refs:`.
- Wrap body text at 72 characters.

## Architecture

- Keep the project split intact: `core/`, `data/`, `feature/`, `ui/`, `debug/`.
- `LatinIME.kt` is the Android boundary. New feature logic should not
  accumulate there unless the IME lifecycle requires it.
- `SettingsRepository` is the single source of truth for settings. Do not add
  new direct `SharedPreferences` reads elsewhere.
- Keep UI files thin: rendering and interaction belong in `ui/**`; persistence
  and business logic belong outside the Compose surface.
- Preserve the key dispatch chain: Compose/UI -> bridge -> `LatinIME` ->
  `KeyEventSender`.
- The JNI-bound dictionary path is locked to
  `org.pocketworkstation.pckeyboard.BinaryDictionary`.

## Build, Test, And Debug

- Build and install commands live in Gradle plus the existing ADB/E2E harnesses.
- On Windows, always use the Android emulator for verification; never use physical Android devices.
- `adb install -r` does not reliably restart the IME process. Re-establish IME
  state intentionally after installs.
- The shared E2E harness lives under `tools/e2e/`; the HTTP debug/log server
  lives under `tools/debug-server/`.
- Always start the debug server on port 3950 (`PORT=3950`), not the default 3948.
- Prefer deterministic scripts and existing harness helpers over ad-hoc ADB
  command sequences.
- Debug logs may record structural state, key codes, and mode transitions, but
  never typed content or credential-adjacent values.

## Path-Scoped Rules

- `rules/build-config.md`
- `rules/compose-keyboard.md`
- `rules/ime-lifecycle.md`
- `rules/jni-bridge.md`
- `rules/modifier-state.md`
- `rules/settings-data.md`
- `rules/testing-infra.md`

## On-Demand References

- `.claude/docs/INDEX.md`
- `.claude/docs/reference/`
- `.claude/skills/`
- `.claude/plans/`
- `.claude/tailor/`
- `.claude/test-flows/`
- `.claude/test-results/`
- `.claude/archive/`
