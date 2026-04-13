---
name: layered-commit
description: "Classify dirty-tree files by architectural layer and commit each bucket with a conventional-commit message."
user-invocable: true
disable-model-invocation: true
---

# Layered Commit

Sort uncommitted changes into architectural-layer buckets and commit each
bucket sequentially with a `<type>: <description>` message derived from
the diff context.

## Workflow

### 1. Gather dirty files

Run both commands and merge the results into one list:

```
git diff --name-only
git ls-files --others --exclude-standard
```

If the list is empty, say "Working tree is clean" and stop.

### 2. Classify into buckets

Assign each file to the first matching bucket:

| Bucket   | Path pattern                        | Default type   |
|----------|-------------------------------------|----------------|
| core     | `app/src/main/**/core/**`           | context-driven |
| data     | `app/src/main/**/data/**`           | context-driven |
| feature  | `app/src/main/**/feature/**`        | context-driven |
| ui       | `app/src/main/**/ui/**`             | context-driven |
| debug    | `app/src/main/**/debug/**`          | context-driven |
| test     | `app/src/test/**`                   | `test:`        |
| build    | `*.gradle*`, `gradle/**`, `*.toml`  | `build:`       |
| docs     | `.claude/**`, `*.md`                | `docs:`        |

- A file that matches none of these patterns goes into a **misc** bucket
  with context-driven type.
- Drop empty buckets.

### 3. Merge related production buckets

If multiple production buckets (core, data, feature, ui, debug) contain
changes that serve the same logical change (same feature, same refactor),
merge them into a single commit. Use the diff content and file names to
judge relatedness. When in doubt, keep them separate.

### 4. Build check

Before committing any production-layer bucket (core/data/feature/ui/debug),
run `./gradlew assembleDebug` once. If it fails, warn the user clearly but
do not block — they may be committing WIP. Do not re-run the build for
test/build/docs buckets.

### 5. Commit each bucket

Process buckets in this order: production layers first, then test, then
build, then docs last.

For each bucket:

1. Read the diffs for the bucket's files (`git diff -- <file>` for tracked
   files, read the file content for untracked files).
2. Generate a one-line commit message in `<type>: <description>` format:
   - For **context-driven** buckets, pick the type from the diff:
     `feat:` (new capability), `fix:` (bug fix), `refactor:` (restructure
     without behavior change), `style:` (formatting / lint fixes).
   - For **test/build/docs** buckets, use the default type from the table.
   - The description should be lowercase, imperative, concise, and capture
     the *why* of the change — not just list file names.
3. Stage the files by explicit name: `git add <file1> <file2> ...`
4. Commit: `git commit -m "<message>"`

### 6. Summary

After all commits, print a summary table:

```
Commits created:
  <short-hash>  <type>: <description>   (N files)
  ...
```

## Rules

- Never add Co-Authored-By lines to commits.
- Never use `git checkout`.
- Never use `git add -A`, `git add .`, or any wildcard staging.
- Stage files by explicit name only.
- Respect `.gitignore` — the `git ls-files --others --exclude-standard`
  command already excludes ignored files; do not override this.
- Do not commit files that likely contain secrets (`.env`,
  `credentials.json`, etc.). Warn the user if such files appear in the
  dirty tree.
- If a commit fails (e.g. pre-commit hook), report the error and continue
  with the next bucket rather than aborting the entire run.
- Keep commit messages under 72 characters.
