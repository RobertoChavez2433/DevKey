---
name: brainstorming
description: "Use for large, ambiguous, or cross-cutting work that needs scope, success criteria, and constraints locked before planning."
user-invocable: true
disable-model-invocation: true
---

# Brainstorming

Turn an idea into an approved spec at
`.claude/specs/YYYY-MM-DD-<slug>-spec.md`.

## When To Use

Use this skill when the work is:

- large or cross-cutting
- ambiguous or under-specified
- behavior-changing enough that success criteria need to be locked first
- risky enough that implementation should not start from assumptions

Skip it for small, clear, implementation-ready requests.

## Hard Gate

If brainstorming is warranted, do not plan or implement until the spec is
written and approved by the user.

## Workflow

1. Explore the current repo surface that the idea will touch.
2. Ask focused questions to lock:
   - user goal
   - success criteria
   - constraints
   - non-goals
3. Present 2-3 approaches with a recommendation.
4. Present the proposed design in compact sections.
5. Revise until the user approves.
6. Write the approved spec to `.claude/specs/YYYY-MM-DD-<slug>-spec.md`.

## Spec Expectations

The spec should be decision-complete enough for `/tailor` and `/writing-plans`.
Include:

- user-visible goal
- scope and non-goals
- architecture or flow summary
- acceptance criteria
- constraints and risks
- touched files or subsystems when already known

## Output

After saving the spec, point to the saved file and recommend the next step:

- `/tailor` if implementation context still needs to be mapped
- `/writing-plans` if the spec already names the implementation surface clearly
