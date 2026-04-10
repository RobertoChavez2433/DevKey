---
name: tailor
description: "Build a focused CodeMunch-backed context package for an approved spec before writing a plan."
user-invocable: true
disable-model-invocation: true
---

# Tailor

Map the codebase against an approved spec and write a durable tailor directory
at `.claude/tailor/YYYY-MM-DD-<spec-slug>/`.

## When To Use

Use this skill when writing-plans would benefit from:

- verified file and symbol context
- dependency and blast-radius mapping
- grounded constants, paths, and names
- reusable implementation patterns

Skip it only when the spec already identifies a small, fully known
implementation surface.

## Hard Gates

Do not write tailor output until:

1. the spec has been read
2. the touched surface has been mapped
3. ground-truth values have been verified from source

## Workflow

1. Read the approved spec and derive the `<spec-slug>`.
2. Resolve or refresh the local CodeMunch index for this repo.
3. Map the touched surface using the smallest useful set of calls:
   - file outline
   - search symbols
   - dependency graph
   - blast radius
   - importer or caller lookups when needed
4. Read source-of-truth files directly for constants, paths, key codes, build
   settings, and JNI-sensitive names.
5. Write a tailor package containing:
   - `manifest.md`
   - `dependency-graph.md`
   - `ground-truth.md`
   - `blast-radius.md`
   - `patterns/`
   - `source-excerpts/`
6. Present key findings and any open questions for the plan author.

## Output Expectations

`manifest.md` should capture the scope, files analyzed, and open questions.

`ground-truth.md` should contain only verified names, constants, paths, and
discrepancies.

`patterns/` should contain only patterns that actually matter to this spec.

## Security Rules

- Do not include credentials, signing material, or local machine secrets.
- Do not copy large irrelevant source bodies into tailor output.
- Never treat guessed values as ground truth.
