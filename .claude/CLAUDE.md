# DevKey

Open-source Android keyboard: Hacker's Keyboard power-user features + modern SwiftKey-inspired UX.

## Quick References

- Defects: `.claude/autoload/_defects.md` (auto-loaded)
- State: `.claude/autoload/_state.md` (auto-loaded)
- Research: `docs/research/`

## Session

- `/resume-session` — Load HOT context only
- `/end-session` — Save state with auto-archiving
- State: `.claude/autoload/_state.md` (max 5 sessions)
- Defects: `.claude/autoload/_defects.md` (max 7, auto-loaded)
- Archives: `.claude/logs/state-archive.md`, `.claude/logs/defects-archive.md`

## Skills

| Skill | Purpose |
|-------|---------|
| `/brainstorming` | Collaborative design before implementation |
| `/implement` | Orchestrate implementation (dispatch, review, verify) |
| `/resume-session` | Load HOT context on session start |
| `/end-session` | Session handoff with auto-archiving |

### When to Use What

| Situation | Use |
|-----------|-----|
| New feature or behavior change | `/brainstorming` first, then `/implement` |
| Bug or unexpected behavior | Debug directly or use agents |
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

## Directory Reference

| Directory | Purpose |
|-----------|---------|
| autoload/ | Hot state loaded every session (_state.md, _defects.md) |
| docs/ | Architecture and reference documentation |
| skills/ | Skill definitions (brainstorming, implement, session mgmt) |
| state/ | JSON state files (PROJECT-STATE.json, FEATURE-MATRIX.json) |
| logs/ | Archives (state-archive, defects-archive) — on-demand only |
| plans/ | Implementation plans (active and completed/) |
| memory/ | Key learnings and patterns (MEMORY.md) |
