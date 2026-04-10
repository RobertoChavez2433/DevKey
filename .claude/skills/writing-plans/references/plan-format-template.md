# Plan Format Template

## Header

```markdown
# <Feature Name> Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** <one sentence>
**Spec:** `.claude/specs/YYYY-MM-DD-<name>-spec.md`
**Tailor:** `.claude/tailor/YYYY-MM-DD-<spec-slug>/`
**Architecture:** <2-3 sentences>
**Tech Stack:** Kotlin, Compose, Room, JNI, Gradle
**Blast Radius:** <summary>

## Phase Ranges

| Phase | Name | Start | End |
| --- | --- | --- | --- |
| 1 | <phase name> | <line> | <line> |
```

## Phase Body

```markdown
## Phase N: <Phase Name>

<one sentence describing why this phase exists>

### Sub-phase N.1: <Sub-phase Name>

**Files:** `path/to/file.kt` (modify), `path/to/new-file.kt` (create)
**Agent:** `general-purpose`

**Steps:**

1. <concrete action>
2. <concrete action>

**Verification:** `./gradlew assembleDebug`
```

## Rules

- Fill `Phase Ranges` after the plan body is assembled so line numbers match.
- Use real file paths and symbols from tailor output.
- Keep steps executable without guesswork.
- Use `./gradlew assembleDebug` as the default local verification step.
- Do not include `./gradlew connectedAndroidTest` or ad-hoc manual ADB loops.
