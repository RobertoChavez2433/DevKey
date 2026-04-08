---
name: audit-config
description: "Read-only health check of .claude/ against codebase — validates file paths, class refs, agent tool restrictions."
user-invocable: true
---

# /audit-config — .claude Health Check

Read-only audit of the `.claude/` directory. Validates that file references, class/symbol references, and agent security invariants are all consistent with the current codebase.

**CRITICAL**: This skill is READ-ONLY. No Write, no Edit, no Bash mutations. Only Read, Glob, Grep, and reporting.

---

## Scope

### Included
- `.claude/agents/` — all agent frontmatter and body
- `.claude/skills/` — all skill files
- `.claude/plans/` — active plans (not `completed/`)
- `.claude/autoload/` — hot-loaded state files
- `.claude/memory/` — MEMORY.md
- `.claude/state/` — JSON state files
- `.claude/rules/` — rule files

### Excluded (do not audit)
- `.claude/logs/` — archives, not live references
- `.claude/adversarial_reviews/` — historical output
- `.claude/code-reviews/` — historical output
- `.claude/test-results/` — test output
- `.claude/debug-sessions/` — debug output
- `.claude/outputs/` — generated reports

---

## Workflow

### Phase 1: Discover Files

1. Use Glob to enumerate every file in `.claude/` recursively.
2. Filter to included scope (see above). Skip excluded directories.
3. Build a working list: `agents/*.md`, `skills/*/SKILL.md`, `plans/*.md` (active), state files, memory files.
4. Print: "Discovered N files to audit."

### Phase 2: Validate File Path References

For each file in scope:
1. Extract every path-like token that looks like a file reference:
   - Quoted paths: `"path/to/file"`, `` `path/to/file` ``
   - Markdown links: `[text](path)`
   - Code blocks containing paths
   - Patterns like `.claude/`, `docs/`, `src/`, `app/`
2. For each candidate path:
   - Normalize: strip leading `/`, make relative to project root
   - Use Glob to check existence: `Glob(pattern=<path>)`
   - If no match: **FLAG** as broken reference
3. Skip clearly non-path tokens (URLs, regex patterns, format strings like `YYYY-MM-DD`)
4. Record: file, line context, broken path

### Phase 3: Validate Class/Symbol References

For agent and skill files that reference Kotlin/Java class names or Android components:
1. Extract tokens matching `PascalCase` or `fully.qualified.ClassName` patterns
2. For each candidate class name:
   - Use Grep to search `src/` and `app/` for the class definition:
     `Grep(pattern="class <Name>|interface <Name>|object <Name>", glob="**/*.kt")`
   - If no match found in source: **FLAG** as unresolved class reference
3. Focus on explicit class mentions (e.g., `InputMethodService`, `LatinIME`, specific composable names)
4. Skip generic terms that happen to be PascalCase (e.g., `Read`, `Write`, `Glob` — tool names)

### Phase 4: Validate Agent Frontmatter — Security Invariants

Read each file in `.claude/agents/`. For each agent, extract its YAML frontmatter and check:

#### Required Security Invariants

| Agent name (file) | Required `disallowedTools` | Notes |
|---|---|---|
| `security-agent` (any file matching `*security*`) | Must include `Write`, `Edit`, `Bash`, AND `NotebookEdit` (all four) | Read-only auditor — no code changes, no command execution |
| `code-review-agent` (any file matching `*code-review*`) | Must include `Write`, `Edit`, `Bash`, AND `NotebookEdit` (all four) | Read-only reviewer — no code changes, no command execution |
| `implement-orchestrator` (any file matching `*implement-orchestrator*`) | Must include `Edit` AND `NotebookEdit`; `Write` is ALLOWED (scoped to checkpoint file only) | Bash is allowed for build commands; all source edits go through dispatched agents |

For each agent checked:
- Parse the `disallowedTools:` YAML field (may be a list or inline)
- Verify all required tools are present in the disallow list
- If a required tool is NOT in the disallow list: **FLAG** as security invariant violation

#### General Frontmatter Checks (all agents)
- Has `name:` field
- Has `description:` field
- If `user-invocable: true` → also has `description:` (checked above)
- No `allowedTools:` that contradicts a security agent's `disallowedTools:` (i.e., don't both allow and disallow the same tool)

### Phase 5: Write Report

Create the output file at `.claude/outputs/audit-report-YYYY-MM-DD.md` (substitute today's date).

**Report format:**

```markdown
# .claude Audit Report — YYYY-MM-DD

**Files audited**: N
**Broken path references**: N
**Unresolved class references**: N
**Security invariant violations**: N
**Other issues**: N

---

## Security Invariants

### PASS / FAIL — security-agent
- disallowedTools includes Write: YES/NO
- disallowedTools includes Edit: YES/NO
- disallowedTools includes Bash: YES/NO
- disallowedTools includes NotebookEdit: YES/NO

### PASS / FAIL — code-review-agent
- disallowedTools includes Write: YES/NO
- disallowedTools includes Edit: YES/NO
- disallowedTools includes Bash: YES/NO
- disallowedTools includes NotebookEdit: YES/NO

### PASS / FAIL — implement-orchestrator
- disallowedTools includes Edit: YES/NO
- disallowedTools includes NotebookEdit: YES/NO
- Write is ALLOWED (scoped to checkpoint file): YES/NO
- tools includes Bash (for build commands): YES/NO

---

## Broken Path References

| File | Line Context | Broken Path |
|------|-------------|-------------|
| ... | ... | ... |

(None — all references valid.) ← if clean

---

## Unresolved Class References

| File | Class Name | Search Pattern Used |
|------|-----------|---------------------|
| ... | ... | ... |

(None flagged.) ← if clean

---

## Other Issues

| File | Issue |
|------|-------|
| ... | ... |

(None.) ← if clean

---

## Files Audited

<collapsible list of all N files checked>
```

After writing the report:
- Print the report path to the user
- Print a one-line summary: "Audit complete. N issues found." (or "Audit complete. No issues found.")
- If any security invariant violations exist: print a prominent warning

---

## Output File

`.claude/outputs/audit-report-YYYY-MM-DD.md`

Create the `outputs/` directory if it does not exist (use Write to create a `.gitkeep` first, or write the report directly — the directory must exist before writing).

---

## Rules

- **READ-ONLY** — no source file mutations, no git operations
- Skip excluded directories entirely (do not even enumerate their contents)
- Do not flag URLs, regex patterns, or format strings as broken paths
- Do not flag tool names (Read, Write, Edit, Glob, Grep, Bash, Task) as unresolved class references
- If a Glob or Grep search fails to find a file/symbol, FLAG it — do not silently skip
- Report every issue found, even if it seems minor
- The report is the only output artifact; do not create any other files
