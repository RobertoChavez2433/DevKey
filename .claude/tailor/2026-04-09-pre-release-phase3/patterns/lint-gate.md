# Pattern — Lint Gate (§3.7)

## Summary
Spec §3.7 requires `./gradlew lint` "clean" before the Phase 3 gate closes.
The project's current lint configuration disables abort-on-error, so "clean"
must be defined operationally, not by build exit code alone.

## Current configuration
```kotlin
// app/build.gradle.kts:71-74
lint {
    checkReleaseBuilds = true
    abortOnError = false
}
```
- `./gradlew lint` returns exit code 0 even when lint finds errors.
- Lint results land at `app/build/reports/lint-results-debug.xml`
  (and `lint-results-release.xml` when the release variant runs).
- "Warnings" are numerous in a typical Android+Compose project. The gate
  definition therefore needs a severity cut-off.

## How we (should) do it

Phase 3 interpretation of "lint clean" — choose one of two options and record
the choice in the Phase 3 plan's "Design decisions locked by plan author"
block.

### Option A — strict flip, recommended
1. Change `abortOnError = false` → `abortOnError = true` in
   `app/build.gradle.kts` for the duration of the gate run (and commit as
   part of the Phase 3 lint-clean commit).
2. Run `./gradlew lint`. If it exits 0, the gate is green.
3. If it exits non-zero, fix the reported errors or baseline them via
   `./gradlew updateLintBaseline` **only** after filing a follow-up issue.
   Do not baseline silently.

### Option B — XML parse, no config change
1. Run `./gradlew lint` (exits 0 regardless).
2. Parse `app/build/reports/lint-results-debug.xml` and assert zero entries
   of `<issue severity="Error">` (warnings tolerated).
3. Commit the XML under `.claude/test-results/<date>/lint-results-debug.xml`
   as the gate-green audit trail.

Option A is cleaner — flipping `abortOnError` makes the signal build-native
and survives CI wiring. Option B keeps the Phase 3 footprint zero on
`app/build.gradle.kts`.

## Exemplar — canonical command
```bash
./gradlew lint --no-daemon                 # run under Phase 3 gate
cat app/build/reports/lint-results-debug.xml | grep -c 'severity="Error"'
# Expected: 0
```

## Reusable operations

| Operation | Command |
|---|---|
| Run lint for debug variant | `./gradlew lintDebug` |
| Run lint for all variants | `./gradlew lint` |
| Generate HTML report (readable) | `./gradlew lint` (report at `lint-results-debug.html`) |
| Update baseline (do NOT silently) | `./gradlew updateLintBaseline` |

## Anti-patterns
- **Do not** update `lint-baseline.xml` as a way to dodge red errors.
  Baseline updates must be tied to a follow-up issue per `CLAUDE.md` defect
  routing.
- **Do not** add new `@Suppress("UNCHECKED_CAST")`-style suppressions in
  Phase 3 to hide lint warnings. Phase 4 is where code-quality passes live.
- **Do not** gate Phase 3.7 on `./gradlew lint` exit code alone while
  `abortOnError = false` stays in place. That would make the gate a no-op.
