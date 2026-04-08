---
name: tailor
description: "Runs jcodemunch research on a spec, discovers architectural patterns, verifies ground truth, and outputs a structured tailor directory. Prerequisite for writing-plans."
user-invocable: true
---

# Tailor Skill

Announce at start: "I'm using the tailor skill to map the DevKey codebase for this spec."

This skill performs deep codebase research against a spec, discovers the architectural patterns that apply, verifies ground truth constants, and writes a structured tailor directory that feeds `/writing-plans`. Do not skip steps or reorder phases.

---

## Phase 1 — Accept Spec

1. Read the spec from `.claude/specs/`. If a path is provided as an argument, use it. Otherwise, list `.claude/specs/` and ask the user which spec to tailor.
2. If an adversarial review exists at `.claude/adversarial_reviews/<spec-slug>*.md`, read it now and note any open concerns — these may add research targets.
3. Derive `<spec-slug>` from the spec filename (strip date prefix and `-spec.md` suffix).
4. Create output directory: `.claude/tailor/YYYY-MM-DD-<spec-slug>/` where the date is today's date.
5. Extract from the spec:
   - All files listed under "Files to Modify" and "Files to Create"
   - All symbol names, class names, function names mentioned
   - All feature requirements (numbered list, used later in Phase 5 ground-truth check)

---

## Phase 2 — jcodemunch Research Sequence (PRESCRIBED, 9 mandatory steps)

Run ALL steps in the order below. Every step is MANDATORY unless marked optional. Use repo key `local/Hackers_Keyboard_Fork-<hash>` — confirm the exact key by calling `mcp__jcodemunch__list_repos` first.

**Security exclusions — SKIP these files in ALL jcodemunch calls:**
- `**/local.properties`
- `**/keystore*`, `**/*.jks`
- `**/signing*.properties`
- `**/google-services.json`
- Any file matching `*secret*` or `*credential*`

### Step 1 — Index
Call `mcp__jcodemunch__index_folder` on `C:\Users\rseba\Projects\Hackers_Keyboard_Fork` with:
- `incremental: true`
- `use_ai_summaries: true`

### Step 2 — File Outlines
Call `mcp__jcodemunch__get_file_outline` on EVERY file listed in the spec's "Files to Modify/Create" section. Skip credential-bearing files per the exclusions above.

### Step 3 — Dependency Graph
Call `mcp__jcodemunch__get_dependency_graph` for all key files identified in Step 2. Capture both direct and transitive dependencies.

### Step 4 — Blast Radius
Call `mcp__jcodemunch__get_blast_radius` for all symbols being changed. Record the count: direct dependents, transitive dependents, affected tests, cleanup targets.

### Step 5 — Find Importers
Call `mcp__jcodemunch__find_importers` for all symbols being changed. Cross-reference with blast radius to identify any importers not caught by Step 4.

### Step 6 — Class Hierarchy
Call `mcp__jcodemunch__get_class_hierarchy` for all classes involved (both classes being modified and classes they extend/implement).

### Step 7 — Dead Code
Call `mcp__jcodemunch__find_dead_code` across the affected module to identify cleanup targets. Record results for Phase 6 output.

### Step 8 — Symbol Search
Call `mcp__jcodemunch__search_symbols` for every key symbol mentioned in the spec (class names, function names, constant names, annotation names).

### Step 9 — Symbol Source
Call `mcp__jcodemunch__get_symbol_source` for each relevant symbol found in Steps 2–8. Skip symbols from credential-bearing files.

### Optional Steps
- `mcp__jcodemunch__get_ranked_context` — use when you need prioritized context across many files
- `mcp__jcodemunch__get_context_bundle` — use when you need a multi-file bundle for a specific concern

### NOT Used
- `mcp__jcodemunch__index_repo` — GitHub-fetch, freezes
- `mcp__jcodemunch__get_repo_outline` — GitHub-fetch, freezes

---

## Phase 3 — Pattern Discovery

For each pattern below, determine if it is **relevant** to this spec. If relevant, produce:
- "How we do it" — 2-3 sentences describing the DevKey convention
- 1-2 exemplars with full source from `mcp__jcodemunch__get_symbol_source`
- A reusable methods table (method name | signature | purpose)
- Required imports list

### DevKey Patterns to Evaluate

**IME Lifecycle Pattern**
- Classes: `InputMethodService`, `LatinIME`, `ComposeKeyboardViewFactory`
- Check: `onCreate` init order, view sharing strategy, service binding

**Modifier State Pattern**
- Classes: `ModifierStateManager`, `ChordeTracker`
- Check: dual-system design, multitouch state machine transitions, latch vs lock behavior

**Key Dispatch Pattern**
- Chain: `KeyView` → `KeyboardActionBridge` → `LatinIME.onKey` → `KeyEventSender`
- Check: how keycode flows from touch to output, where intercepts happen

**Compose Keyboard Layout Pattern**
- Classes: `DevKeyKeyboard`, `KeyView`, `LayoutMode`, `QwertyLayout`
- Check: how rows/keys are composed, how layout mode switches, theming integration

**Theme Token Pattern**
- Entry point: `DevKeyTheme.kt` only
- Rule: no raw colors or spacing values anywhere — all must reference tokens
- Check: token naming conventions, MaterialTheme extensions used

**Native JNI Pattern**
- Files: `native-lib.cpp`, `BinaryDictionary.java`/`.kt`
- Rule: package path locked to `org.pocketworkstation.pckeyboard` — never change
- Check: `RegisterNatives` call signatures, how Kotlin calls into C++

**Coroutine Pattern**
- Scope: `serviceScope` on the IME service — never `GlobalScope`
- Check: how coroutines are launched, how they are cancelled on service destroy

**Room DAO Pattern**
- Entities: learned data, clipboard, macros, command apps
- Check: DAO interface conventions, database version, migration strategy

**Settings Pattern**
- Options: `SharedPreferences` vs `DataStore`
- Check: which is currently used, how settings are read in IME, migration notes if any

---

## Phase 4 — Ground Truth Verification

Cross-reference every constant, key code, class name, and file path mentioned in the spec against DevKey's sources of truth. Any mismatch between spec and source of truth must be flagged as a discrepancy in the output.

| Category | Source of Truth |
|----------|----------------|
| KeyCode values | `KeyData.kt` (`KeyCodes` object) |
| Layout modes | `LayoutMode.kt` + `QwertyLayout.kt` |
| Modifier states | `ModifierStateManager.kt` |
| Theme tokens | `DevKeyTheme.kt` |
| Testing keys | `.claude/test-flows/registry.md` + widget `Key(...)` constants |
| JNI class paths | C++ `native-lib.cpp` `RegisterNatives` calls — MUST match Kotlin package |
| Intent actions | `AndroidManifest.xml` |
| Room schema | `DevKeyDatabase.kt` + entity classes |
| Gradle versions | `gradle/libs.versions.toml` |
| Build flavors | `app/build.gradle.kts` |
| ProGuard rules | `app/proguard-rules.pro` |
| Calibration coordinates | `.claude/logs/key-coordinates.md` (if `.claude/test-flows/calibration.json` is present, use it too — it is device-specific and may not exist) |

For each source-of-truth category relevant to the spec:
1. Read the source file using `mcp__jcodemunch__get_symbol_source` or `Read`
2. Extract the current values
3. Compare against spec claims
4. Record verified values and any discrepancies in `ground-truth.md`

---

## Phase 5 — Research Agent Gap-Fill

If jcodemunch research left unanswered questions after Phases 2-4:

- Dispatch read-only subagents via the Agent tool
- Model: opus only (never haiku)
- Maximum 3 subagents
- One question per subagent — focused scope
- Subagents may only use Read, Glob, Grep tools
- Skip this phase entirely if all questions were resolved by jcodemunch

---

## Phase 6 — Write Output Directory

**Hard Gate: Do NOT write any output files until Phases 2–5 are complete.**

Write the following structure to `.claude/tailor/YYYY-MM-DD-<spec-slug>/`:

```
.claude/tailor/YYYY-MM-DD-<spec-slug>/
├── manifest.md
├── dependency-graph.md
├── ground-truth.md
├── blast-radius.md
├── patterns/
│   └── <pattern-name>.md     (one file per relevant pattern from Phase 3)
└── source-excerpts/
    ├── by-file.md
    └── by-concern.md
```

### File Contents

**manifest.md** — Overview of the tailor run:
- Spec path and slug
- Date of tailor run
- List of files analyzed
- List of patterns discovered (with link to patterns/ file)
- List of ground-truth discrepancies (if any)
- List of open questions (if any)
- Confirmation that Phase 2 all 9 steps completed

**dependency-graph.md** — Output of Steps 3-5:
- Direct dependencies of files being modified
- Transitive dependencies (summarized if large)
- Import graph for key symbols

**ground-truth.md** — Output of Phase 4:
- For each relevant category: verified value from source of truth
- Any discrepancies between spec and source of truth (flagged prominently)
- Confirmed string literals, constants, package paths

**blast-radius.md** — Output of Steps 4-5:
- Direct dependents (count + list)
- Transitive dependents (count + summary)
- Affected tests (count + list)
- Cleanup targets from dead code (count + list)

**patterns/<pattern-name>.md** — One file per relevant pattern:
- Pattern name and summary
- "How we do it" (2-3 sentences)
- Exemplar 1: symbol name, source excerpt
- Exemplar 2 (if applicable): symbol name, source excerpt
- Reusable methods table
- Required imports

**source-excerpts/by-file.md** — Symbol sources organized by file path

**source-excerpts/by-concern.md** — Symbol sources organized by feature concern (matches spec requirements)

### Security Rules for Output
- NEVER include content from `local.properties`, keystores, or signing configs
- Include method signatures and structure only — never credential values
- Never include raw user-typed text from test fixtures (IME privacy)

---

## Phase 7 — Present Summary

Present a summary in this format:

```
## Tailor Complete: <spec-slug>

**Output directory:** `.claude/tailor/YYYY-MM-DD-<spec-slug>/`

**Research completed:**
- jcodemunch steps: 9/9
- Files analyzed: N
- Patterns discovered: N (list names)
- Ground-truth discrepancies: N (list if any)
- Gap-fill agents: N / 3

**Key findings:**
[2-5 bullet points of most important discoveries]

**Open questions for plan author:**
[List any unresolved questions, or "None"]

Run /writing-plans when ready.
```

---

## Hard Gate

Do NOT write the output directory (Phase 6) until Phases 2–5 are complete. If you are interrupted or context is lost, restart from Phase 2 rather than writing partial output.

---

## Anti-Patterns

Do not do any of the following:

- **Skipping jcodemunch steps** — All 9 steps are mandatory. Do not skip any step because the answer seems obvious.
- **Using `index_repo` or `get_repo_outline`** — These are GitHub-fetch operations that freeze. Use `index_folder` instead.
- **Haiku subagents in Phase 5** — Only opus for research agents. Haiku lacks the context capacity for codebase research.
- **Patterns without exemplars** — Every pattern entry must include at least one source excerpt from `get_symbol_source`. Do not describe patterns from memory.
- **Skipping ground truth verification** — Phase 4 is not optional. Plans built on unverified constants cause runtime failures.
- **`use_ai_summaries: false`** — Always use `true` for richer indexing results.
- **Credential values in output** — Never write key values, passwords, or signing configs to any output file. Method signatures only.
- **Writing output before research is complete** — Phase 6 output written mid-research will be incomplete and mislead `/writing-plans`.
