# Plan Format Template

This file is for `plan-writer-agent` subagents. When dispatching a subagent, paste this file inline in the prompt so the agent can follow the format without reading extra files.

---

## Plan Header Template

```markdown
# [Feature Name] Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** [One sentence describing what this plan accomplishes]
**Spec:** `.claude/specs/YYYY-MM-DD-<name>-spec.md`
**Tailor:** `.claude/tailor/YYYY-MM-DD-<spec-slug>/`

**Architecture:** [2-3 sentences describing how this feature fits into DevKey's architecture]
**Tech Stack:** Kotlin + C++ NDK, Jetpack Compose, Room, TF Lite, Gradle KTS
**Blast Radius:** [N direct, N dependent, N tests, N cleanup]
```

The plan header appears once at the top of the assembled plan. If you are a subagent writing a fragment, do NOT include the header — the orchestrator assembles it.

---

## Phase Structure Template

```markdown
## Phase N: [Phase Name]

[One sentence: what this phase accomplishes and why it comes before the next phase.]

### Sub-phase N.1: [Sub-phase Name]

**Files:** `path/to/file.kt` (modify), `path/to/new-file.kt` (create)
**Agent:** [agent name — see routing table]

**Steps:**

1. [Verb phrase describing the action]
   ```kotlin
   // WHY: [Business reason for this code]
   // FROM SPEC: [Section or requirement reference]
   // code here
   ```

2. [Next step]
   ```kotlin
   // NOTE: [Pattern choice or convention reference]
   // code here
   ```

### Sub-phase N.2: [Sub-phase Name]

**Files:** `path/to/file.kt` (modify)
**Agent:** [agent name]

**Steps:**

1. [Step]

**Verification:** `./gradlew assembleDebug`
```

**Verification rule:** `./gradlew assembleDebug` appears ONLY on the last sub-phase of each phase. Do not add it to intermediate sub-phases.

---

## Sub-phase Step Template (detailed)

Each step in a sub-phase must be:

1. **Actionable** — the implementing agent can execute it without interpretation
2. **Complete** — includes the full code to write, not a description of what to write
3. **Annotated** — uses code annotations where logic is not self-evident

### Code Annotation Types

```kotlin
// WHY: [Business reason — why does this code exist at all?]
// NOTE: [Pattern choice — references an existing DevKey convention]
// IMPORTANT: [Non-obvious behavior, edge case, or gotcha]
// FROM SPEC: [References a specific spec requirement, section, or line]
```

Use annotations sparingly — only where the reason is not obvious from the code itself. Do not annotate every line.

### Step Format Examples

**Good step (complete, annotated):**
```
1. Add `CommandModeState` sealed class to `CommandMode.kt`:
   ```kotlin
   // WHY: Sealed class allows exhaustive when-expressions in LatinIME.kt
   // FROM SPEC: Section 3.2 — "command mode must have three distinct states"
   sealed class CommandModeState {
       object Inactive : CommandModeState()
       object Active : CommandModeState()
       data class AppOverride(val packageName: String) : CommandModeState()
   }
   ```
```

**Bad step (vague, no code):**
```
1. Add command mode state to the appropriate file.
```

---

## Verification Gate Rule

The last sub-phase of each phase ends with:

```markdown
**Verification:** `./gradlew assembleDebug`
```

This confirms the build compiles cleanly after the phase. Do NOT include:
- `./gradlew test` — full unit suite runs in CI only
- `./gradlew connectedAndroidTest` — uninstalls the app, requires IME re-enable
- Manual ADB commands

---

## Phase Ordering Rules (DevKey)

When deciding phase order, apply these rules:

1. Data layer first (Room entities, DAOs, SharedPreferences migration) before IME or UI consumers
2. IME backend (`LatinIME.kt`, modifier state) before Compose UI that depends on it
3. Native/JNI changes last — they block everything else if broken
4. Dead code removal in the final phase
5. Every phase ends with `./gradlew assembleDebug` verification (last sub-phase only)
