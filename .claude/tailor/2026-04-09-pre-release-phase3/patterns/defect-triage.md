# Pattern — Defect Triage (§3.8 Decision Gate Routing)

## Summary
Spec §3.8: *"if any red, return to Phase 1. Do not proceed to Phase 4 with
known regressions."* In practice, the routing is more nuanced: not every red
is a Phase 1 bounce. This pattern classifies each failure by ownership so
Phase 3 does not accidentally scope-creep.

## How we do it

Every red discovered in the Phase 3 gate is classified into one of four
buckets. The bucket determines where the fix lands and whether the Phase 3
gate commit can re-run or must block on an upstream phase.

### Bucket 1 — Stabilization-in-place (fix in Phase 3)
Small harness/tooling defects that do not touch production code. The fix
lands in `tools/e2e/...` or `.claude/test-flows/...` as part of Phase 3's
commit stream. Re-run the affected tier immediately after the fix.

**Examples:**
- Harness blockers B1 (`pytest.skip` handling) and B2 (missing `pytest` dep).
- `time.sleep(...)` left behind in `tests/test_modes.py`.
- Timeout too tight in one `driver.wait_for` call on a slow emulator.
- Reference PNG path typo.

### Bucket 2 — Phase 2 bounce (test-infrastructure)
Reds rooted in Phase 2 deliverables that are too large to fix in-place —
a missing instrumentation emit site, a broken driver-server endpoint, a
new test module that needs assertion rework. The fix re-opens Phase 2's
plan and lands there; Phase 3 blocks until Phase 2 re-closes.

**Examples:**
- `command_mode_auto_enabled` instrumentation never landed (blocker B5).
- New `/wait` matcher edge case that requires a driver-server change.
- Missing structural emit site that a new test expected.

### Bucket 3 — Phase 1 bounce (app defect)
Reds rooted in feature behavior. File a GitHub issue per `CLAUDE.md`
convention, bounce to Phase 1, and block Phase 3 until the fix lands and
a new RC build is produced.

**Examples:**
- Voice model sourcing not done (blocker B3).
- SwiftKey reference captures incomplete (blocker B4) — this is Phase 1.2.
- Clipboard panel crashes on dismiss.
- Next-word prediction fires but suggestion count is zero for a common bigram.

### Bucket 4 — Spec amendment required
Red that cannot be fixed without a scope change: a feature is genuinely
out of reach in the current release timeline, or a gate item turns out to
be impossible as spec'd. Requires a written spec amendment + user sign-off
before Phase 3 can retry.

**Examples:**
- Voice model licensing falls through; §5 voice feature demoted to v1.1.
- SwiftKey visual-diff tolerance needs to drop below the Phase 2 0.92
  floor because emulator AA variance is higher than measured.

## Exemplar — defect-filing one-liner (Bucket 3)
```bash
gh issue create \
  --repo RobertoChavez2433/DevKey \
  --label "defect,category:voice,area:ime,priority:high" \
  --title "voice commit path drops last 200ms on short utterances" \
  --body "Discovered during Phase 3 §3.2 voice round-trip gate. See .claude/test-results/2026-04-09/..."
```

## Reusable operations

| Operation | Where |
|---|---|
| File a defect | `gh issue create ...` with labels from `.claude/CLAUDE.md` Session block |
| List open defects | `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open` |
| Record a Phase 3 gate re-run | Append to `tier-stabilization-status.md` with commit SHA + mode |
| Commit a stabilization-in-place fix | Normal commit on main (plan tracks it) |
| Bounce to Phase 1 | Update `.claude/plans/2026-04-08-pre-release-phase1.md` with the re-open block |

## Anti-patterns
- **Do not** silently patch app code under a Phase 3 commit. Every app
  change routes to Phase 1 per §3.8.
- **Do not** mix buckets in a single commit. A stabilization-in-place patch
  and an app fix live on different commits to keep the audit trail clean.
- **Do not** re-bucket a defect after the fact to avoid a Phase-1 bounce
  cost — that's how features ship broken.
- **Do not** claim "stabilization-in-place" for a harness change that
  rewrites assertion intent. If the test's meaning changes, it's a Phase 2
  bounce.
