# DevKey

Open-source Android keyboard: Hacker's Keyboard power-user features + modern SwiftKey-inspired UX.

## Skills

| Skill | Purpose |
|-------|---------|
| `/brainstorming` | Collaborative design → spec |
| `/writing-plans` | Spec → dependency graph → implementation plan |
| `/implement` | Orchestrate plan execution (dispatch, review, verify) |
| `/systematic-debugging` | 10-phase root cause analysis |
| `/test` | ADB automated keyboard testing |
| `/audit-config` | `.claude/` health check |
| `/tailor` | Tailor a spec for implementation planning |
| `/resume-session` | Load HOT context on session start |
| `/end-session` | Session handoff with auto-archiving |

### When to Use What

| Situation | Use |
|-----------|-----|
| New feature or behavior change | `/brainstorming` → `/tailor` → `/writing-plans` → `/implement` |
| Bug or unexpected behavior | `/systematic-debugging` |
| Need tailored plan from a spec | `/tailor` then `/writing-plans` |
| Writing or refining a plan | `/writing-plans` |
| `.claude/` config drift or health | `/audit-config` |
| Starting a session | `/resume-session` |
| Ending a session | `/end-session` |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin (migrated from Java) + C++ NDK |
| UI | Jetpack Compose |
| AI/ML | TensorFlow Lite (on-device) |
| Build | Gradle (Kotlin DSL) |
| Min API | 26 (Android 8.0) |
| Target API | 34 (Android 14) |
| Base fork | github.com/klausw/hackerskeyboard |

## Domain Rules

Auto-loaded by Claude when matching files are opened. Details in `.claude/rules/`.

| Rule File | Paths Trigger | Covers |
|-----------|--------------|--------|
| `rules/ime-lifecycle.md` | `**/LatinIME.kt`, `**/InputMethodService*` | IME lifecycle, onCreate() init order, serviceScope |
| `rules/compose-keyboard.md` | `**/ui/keyboard/**`, `**/ui/theme/**` | Compose keyboard layout, theme tokens, bridge pattern |
| `rules/jni-bridge.md` | `**/pckeyboard/**` | JNI bridge — never rename the Java class |
| `rules/build-config.md` | `**/build.gradle*`, `**/proguard*` | Gradle DSL, version catalogs, ProGuard |

## Quick Reference Commands

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew installDebug     # Install on connected device/emulator
./gradlew lint             # Run lint checks
```

> Note: `./gradlew connectedAndroidTest` uninstalls the APK. Must reinstall + `ime enable` + `ime set` after.

## Common Mistakes

1. **JNI bridge rename**: `org.pocketworkstation.pckeyboard.BinaryDictionary` is hardcoded in C++ — never rename.
2. **Init order**: `sKeyboardSettings` MUST be initialized BEFORE `KeyboardSwitcher.init()` in `LatinIME.onCreate()`.
3. **IME process survives `adb install -r`**: Always `am force-stop` + `ime set` after install for fresh code.
4. **Compose lifecycle in IME**: `WindowRecomposer` resolves from the decor/root view's lifecycle. Share ONE lifecycle owner between decor and ComposeView via `ComposeKeyboardViewFactory.create()`.

## Context Efficiency

- **Parallel Tasks**: Dispatch independent sub-tasks concurrently; don't serialize what can overlap.
- **Cap Explore at 3**: When researching unknown areas, limit initial file reads to 3 before forming a hypothesis.
- **Summarize results**: After any research pass, summarize findings in 3-5 bullets before proceeding.

## Directory Reference

All directories under `.claude/`:

| Directory | Purpose |
|-----------|---------|
| `agents/` | Specialist agent definitions (11 agents) |
| `architecture-decisions/` | ADR log — architectural decisions with rationale |
| `autoload/` | Hot state loaded every session (`_state.md`) |
| `docs/` | Navigation index and reference documentation |
| `hooks/` | Claude Code hook scripts |
| `logs/` | Cold storage archives (state, test logs, key coordinates) |
| `memory/` | Key learnings and patterns (`MEMORY.md`) |
| `plans/` | Active implementation plans |
| `plans/completed/` | Completed implementation plans |
| `rules/` | Domain rules auto-loaded by file path pattern |
| `skills/` | Skill definitions (9 skills) |
| `specs/` | Feature specs (output of `/brainstorming`) |
| `state/` | JSON state files (`PROJECT-STATE.json`, `FEATURE-MATRIX.json`) |
| `test-flows/` | ADB test flow definitions and calibration data |
| `test-results/` | Test run output and reports |
| `agent-memory/` | Agent-specific memory and scratchpads |
| `adversarial_reviews/` | Adversarial review outputs from plan review cycles |
| `code-reviews/` | Code review reports from `code-review-agent` |
| `dependency_graphs/` | Dependency graph outputs from `/writing-plans` |
| `outputs/` | General agent outputs and artifacts |
| `research/` | Research notes and reference documents |

## Conventions

### Kotlin/Android
- **DI**: Manual — constructor injection
- **UI**: Jetpack Compose with Material You
- **State**: StateFlow + collectAsState()
- **Async**: Kotlin Coroutines (structured concurrency, no GlobalScope)
- **Database**: Room for learned data, clipboard, macros, command apps

### IME-Specific
- InputMethodService as core entry point
- C++ NDK for dictionary lookup (liblatinime)
- TF Lite for on-device voice recognition (Whisper)
- Modifier keys: true multitouch state machine
- Command mode: auto-detect terminal apps + manual toggle

### Build
- Gradle Kotlin DSL with version catalogs
- ProGuard/R8 for release builds
- Target both Play Store and F-Droid

## Session

- `/resume-session` — Load HOT context only
- `/end-session` — Save state with auto-archiving
- State: `.claude/autoload/_state.md` (max 5 sessions)
- Defects: GitHub Issues on `RobertoChavez2433/DevKey` — filed via `gh issue create ... --label "defect,category:<CAT>,area:<AREA>,priority:<P>"`
- Archives: `.claude/logs/state-archive.md`
- All open defects: `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open`
