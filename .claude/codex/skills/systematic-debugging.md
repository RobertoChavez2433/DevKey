# Systematic Debugging

Source workflow: `.claude/skills/systematic-debugging/SKILL.md`.

Use for `/systematic-debugging`, `systematic debugging`, or
`systematic debug <issue>` when an IME issue needs reproduction,
instrumentation, async tracing, or lifecycle evidence.

## Codex Wrapper

1. Lock the symptom and exact reproduction steps.
2. State one falsifiable hypothesis.
3. Instrument only the points that can confirm or deny that hypothesis.
4. Reproduce with emulator, harness, or debug-server evidence.
5. State whether the hypothesis is confirmed, denied, or incomplete.
6. Write a concrete root-cause statement before proposing a fix.

Log structural state only. Never log typed content or credential-adjacent
values.
