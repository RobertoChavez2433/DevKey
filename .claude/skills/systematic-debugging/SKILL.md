---
name: systematic-debugging
description: "Interactive root-cause-first debugging for IME issues that need evidence, instrumentation, and a clear stop-before-fixing gate."
user-invocable: true
disable-model-invocation: true
---

# Systematic Debugging

Debug with evidence first. This skill is for issues where reproduction,
instrumentation, async behavior, or IME state transitions matter.

## Iron Law

No fixes before root cause.

## When To Use

Use this skill when:

- the bug is not obvious from a direct code read
- IME lifecycle, modifier state, async work, or driver/test behavior is involved
- the user already tried a fix and the problem remains

## Mode Choice

- `quick`: one hypothesis, direct logs, minimal instrumentation
- `deep`: background read-only research plus structured instrumentation

Choose `deep` when reproduction is flaky, multi-step, or cross-cutting.

## Workflow

1. Lock the symptom and exact reproduction steps.
2. Write one falsifiable hypothesis.
3. Instrument only the points that can confirm or deny that hypothesis.
4. Reproduce and inspect logs or driver/debug output.
5. State whether the hypothesis is confirmed, denied, or incomplete.
6. Write a concrete root-cause statement before proposing any fix.
7. If the user wants implementation, transition into a fix workflow explicitly.

## Deep Mode

In deep mode, you may dispatch `debug-research-agent` for read-only exploration.
Give it:

- `bug_summary`
- `files_in_scope` or `suspected_paths`
- optional `hypothesis`

## Rules

- Stay interactive and visible.
- One hypothesis at a time.
- Log structural state, never typed content.
- Do not silently jump from investigation into implementation.
