# Tailor

Source workflow: `.claude/skills/tailor/SKILL.md`.

Use for `/tailor <spec>` or `tailor <spec>` after a spec is approved and the
implementation surface needs grounded context.

## Codex Wrapper

1. Read the approved spec and derive the spec slug.
2. Resolve or refresh the CodeMunch index for this repo.
3. Map files, symbols, dependencies, blast radius, and verified constants.
4. Write a focused package under `.claude/tailor/YYYY-MM-DD-<spec-slug>/`.
5. Report key findings and open questions.

Do not include credentials, signing material, local secrets, guessed values, or
large irrelevant source bodies.
