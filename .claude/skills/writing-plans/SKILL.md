---
name: writing-plans
description: "Reads tailor output and writes implementation plans for DevKey. Main agent writes directly for small/medium plans, splits across plan-writer subagents for large ones. Runs 3-sweep adversarial reviews with fix loops."
user-invocable: true
---

# Writing-Plans Skill

Announce at start: "I'm using the writing-plans skill to create an implementation plan."

This skill consumes tailor output and produces a complete, reviewed implementation plan for the DevKey codebase. Plans must be executable by the `/implement` skill without ambiguity. Do not skip phases or reorder them.

---

## Spec as Source of Truth

The spec is the source of truth. The plan must faithfully implement the spec. Reviewers verify the plan, not the spec. If the plan conflicts with the spec, the plan is wrong. If the spec seems wrong, escalate to the user — do not silently deviate.

---

## Phase 1 — Accept

1. Read the spec from `.claude/specs/`. If a path is given as an argument, use it.
2. Search `.claude/tailor/` for a directory whose slug matches the spec (by spec slug in the directory name).
3. **Hard gate:** If no matching tailor directory exists, output:
   ```
   No tailor output found for this spec.
   Run `/tailor <spec-path>` first, then re-run /writing-plans.
   ```
   STOP. Do not proceed.
4. Record the tailor directory path for all subsequent phases.

---

## Phase 2 — Load Tailor Output

Read tailor output files in this order:

1. `manifest.md` — confirms research completeness, lists patterns and discrepancies
2. `ground-truth.md` — verified constants, key codes, package paths; note any discrepancies
3. `dependency-graph.md` — import relationships and module boundaries
4. `blast-radius.md` — affected files, test count, cleanup targets
5. `patterns/<pattern-name>.md` — all pattern files listed in manifest
6. `source-excerpts/by-file.md` and `source-excerpts/by-concern.md`

If `manifest.md` records open questions, resolve them before writing (use Grep/Read as needed). If ground-truth discrepancies are listed, flag them prominently in the plan header and note required clarifications.

---

## Phase 3 — Writer Strategy

Estimate the plan's line count from spec complexity:

- **Under ~2000 lines** → main agent writes the plan directly. Do not dispatch subagents.
- **Over ~2000 lines** → split by phase. Dispatch `plan-writer-agent` subagents (one per major phase) via the Agent tool. Each receives:
  - `TAILOR_DIR` — path to tailor directory
  - `OUTPUT_PATH` — path to write its fragment (`.claude/plans/parts/<plan-name>-writer-N.md`)
  - `PHASE_ASSIGNMENT` — which phases to write (e.g., "Phase 1 and Phase 2")
  - The plan format template (inline in the dispatch prompt — paste from `.claude/skills/writing-plans/references/plan-format-template.md`)
  - The agent routing table (inline — paste from `.claude/skills/writing-plans/references/agent-routing-table.md`)

After all subagents complete, assemble fragments into the final plan file at `.claude/plans/YYYY-MM-DD-<feature-name>.md`. Verify continuity between fragments (no duplicate headings, consistent phase numbering).

---

## Phase 4 — Write the Plan

Follow the Plan Format Reference below exactly. Every sub-phase step must be:
- Actionable (agent can execute without interpretation)
- Complete (includes the full code to write, not a description of it)
- Annotated where logic is not self-evident (see Code Annotations)
- Verified against tailor `ground-truth.md` for all string literals and constants

### Plan Format Reference

#### Plan Header

```markdown
# [Feature Name] Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** [One sentence]
**Spec:** `.claude/specs/YYYY-MM-DD-<name>-spec.md`
**Tailor:** `.claude/tailor/YYYY-MM-DD-<spec-slug>/`

**Architecture:** [2-3 sentences]
**Tech Stack:** Kotlin + C++ NDK, Jetpack Compose, Room, TF Lite, Gradle KTS
**Blast Radius:** [N direct, N dependent, N tests, N cleanup]
```

#### Phase Structure

```markdown
## Phase N: [Phase Name]

[One sentence describing what this phase accomplishes and why it comes before the next.]

### Sub-phase N.1: [Sub-phase Name]

**Files:** `path/to/file.kt` (modify), `path/to/new-file.kt` (create)
**Agent:** [agent name from routing table]

**Steps:**

1. [Step description]
   ```kotlin
   // WHY: [Business reason]
   // FROM SPEC: [Spec requirement reference]
   actual code here
   ```

2. [Step description]
   ...

**Verification:** `./gradlew assembleDebug`
```

The verification step (`./gradlew assembleDebug`) appears only on the LAST sub-phase of each phase — not on every sub-phase. See Test Run Rules.

#### Code Annotations (required where logic is not self-evident)

```kotlin
// WHY: [Business reason for this code]
// NOTE: [Pattern choice, references existing convention]
// IMPORTANT: [Non-obvious behavior or gotchas]
// FROM SPEC: [References specific spec requirement by section/number]
```

---

## Phase Ordering Rules

Apply these rules when deciding the order of phases in the plan:

1. **Data layer first** — Room entities, DAOs, SharedPreferences migration before any IME or UI consumers
2. **IME backend before UI** — `LatinIME.kt`, modifier state, key dispatch before Compose UI that depends on it
3. **Native/JNI last** — if any C++ changes are needed, they go in the final phase; JNI changes block everything else if broken
4. **Dead code removal in final phase** — never remove dead code in early phases; it may be referenced by later steps
5. **Every phase ends with build verification** — the last sub-phase of each phase includes `./gradlew assembleDebug`

---

## Agent Routing Table

Use this table to assign the `**Agent:**` field in each sub-phase:

| File Pattern | Agent |
|-------------|-------|
| `**/LatinIME.kt`, `**/KeyEventSender.kt`, `**/Modifier*`, `**/Suggest*`, `**/WordComposer*`, `**/ChordeTracker*` | `ime-core-agent` |
| `**/ui/keyboard/**`, `**/ui/theme/**`, `**/ComposeKeyboard*`, `**/DevKeyKeyboard*`, `**/KeyView*`, `**/QwertyLayout*` | `compose-ui-agent` |
| `**/pckeyboard/**`, `**/jni/**`, `**/native-lib.cpp` | `general-purpose` (sonnet) — JNI bridge is locked; plans here are review-only unless user explicitly authorizes |
| `**/build.gradle*`, `**/proguard*`, `gradle/libs.versions.toml` | `general-purpose` (sonnet) |
| `app/src/test/**`, `app/src/androidTest/**` | `general-purpose` (sonnet) |
| All files post-phase | `code-review-agent` + `security-agent` (parallel) |
| `.claude/**` config | `general-purpose` (sonnet) |

---

## Test Run Rules

| Allowed in sub-phase steps | NOT allowed in sub-phase steps |
|---------------------------|-------------------------------|
| `./gradlew assembleDebug` (build verification) | `./gradlew test` (full unit suite — CI only) |
| `./gradlew lint` (static analysis) | `./gradlew connectedAndroidTest` (uninstalls app — see defect) |
| `./gradlew --offline compileDebugKotlin` (quick type check) | Manual ADB install/test loops |

The last step of the **final sub-phase in each phase** may include `./gradlew assembleDebug` as a verification gate. The full test suite runs in CI after push — do not include it in plan steps.

---

## Ground Truth Verification

Before finalizing the plan, cross-reference every string literal, constant, class name, and file path in plan code against tailor's `ground-truth.md`. Any value not in `ground-truth.md` must be verified by reading the source file directly. Plans with assumed names cause runtime failures.

---

## Phase 5 — Review Loop

1. Create review directory: `.claude/plans/review_sweeps/<plan-name>-<date>/`
2. In ONE message, dispatch 3 reviewers in parallel (all opus):
   - `code-review-agent` — reviews code correctness, patterns, Kotlin conventions
   - `security-agent` — reviews for security issues, credential handling, input validation
   - `completeness-review-agent` — reviews plan against spec for drift, gaps, missing requirements
3. Save each report as `{type}-review-cycle-N.md` in the review directory (N = cycle number, starting at 1)
4. Evaluate findings:
   - If all 3 reviewers APPROVE → proceed to Phase 6
   - If any reviewer REJECT → consolidate all findings, dispatch `plan-fixer-agent` with the consolidated list, wait for fixes, then re-run ALL 3 reviewers (increment N)
5. **Maximum 3 cycles.** If cycle 3 still has REJECT findings, escalate to the user with a summary of unresolved issues. Do not proceed to Phase 6 without user input.

**Rules:**
- Never dispatch reviewers sequentially — always parallel in ONE message
- After any fix, always re-run ALL 3 sweeps — never partial re-review
- Never dispatch `plan-fixer-agent` for APPROVE findings (no unnecessary churn)

---

## Phase 6 — Present Summary

Present a summary in this format:

```
## Plan Complete: <feature-name>

**Plan file:** `.claude/plans/YYYY-MM-DD-<feature-name>.md`
**Spec:** `.claude/specs/YYYY-MM-DD-<name>-spec.md`
**Tailor:** `.claude/tailor/YYYY-MM-DD-<spec-slug>/`

**Plan stats:**
- Phases: N
- Sub-phases: N
- Agents assigned: [list]
- Review cycles: N

**Ground-truth discrepancies resolved:** N (list if any)
**Open items for implementer:** [list or "None"]

Run /implement when ready.
```

---

## Hard Gates

```
Do NOT write the plan (Phase 4) until tailor output is loaded (Phases 1-2) and writer strategy is determined (Phase 3).
Do NOT dispatch reviewers (Phase 5) until the plan file exists at .claude/plans/.
```

---

## Save Locations

- Plans: `.claude/plans/YYYY-MM-DD-<feature-name>.md`
- Writer fragments (large plans): `.claude/plans/parts/<plan-name>-writer-N.md`
- Review sweeps: `.claude/plans/review_sweeps/<plan-name>-<date>/`

---

## Anti-Patterns

| Anti-Pattern | Why It's Wrong | Do This Instead |
|--------------|----------------|-----------------|
| Writing plans without tailor output | Missing codebase context leads to bad plans | Run `/tailor` first |
| Dispatching subagents for small plans | Unnecessary overhead, loses context | Main agent writes plans under ~2000 lines directly |
| Vague steps ("add validation") | Implementing agent has to think and guess | Write complete code with annotations |
| Missing file paths | Agent guesses wrong location | Exact `path/to/file.kt:line` in every step |
| Including `./gradlew test` in plans | Tests run in CI only | Use `./gradlew assembleDebug` as local gate |
| Including `connectedAndroidTest` in plans | Uninstalls app, requires IME re-enable | CI only; if manual test needed, document recovery |
| Assumed names in plan code | Runtime failures every time | Cross-reference tailor `ground-truth.md` |
| Sequential adversarial review | Wastes time | Dispatch all 3 reviewers in parallel in ONE message |
| Partial re-review after fixes | Misses regressions | Always run ALL 3 sweeps each cycle |
| Modifying `BinaryDictionary.java` package | Breaks C++ JNI `RegisterNatives` | Package path `org.pocketworkstation.pckeyboard` is locked forever |
