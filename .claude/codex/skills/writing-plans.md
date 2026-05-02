# Writing Plans

Source workflow: `.claude/skills/writing-plans/SKILL.md`.

Use for `/writing-plans <spec>` or `writing plans <spec>` after matching tailor
output exists.

## Codex Wrapper

1. Read the approved spec.
2. Load only the tailor output needed for the plan.
3. Write `.claude/plans/YYYY-MM-DD-<feature-name>.md`.
4. Include a complete header and machine-readable `Phase Ranges`.
5. Run or request the documented review sweeps as the active tooling permits.
6. Fix plan findings directly and rerun review up to 3 cycles, then escalate
   unresolved findings.

Default verification in plans should start with `./gradlew assembleDebug`.
Do not put routine `./gradlew connectedAndroidTest` in plan steps.
