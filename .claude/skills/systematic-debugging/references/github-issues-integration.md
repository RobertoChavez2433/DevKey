# GitHub Issues Integration

**UPDATED 2026-04-08**: No per-concern files. All defects are GitHub Issues on `RobertoChavez2433/DevKey`.

## Repo

`RobertoChavez2433/DevKey`

---

## Filter Queries (Phase 1 Triage)

```bash
# All open defects
gh issue list --repo RobertoChavez2433/DevKey --label defect --state open --limit 30

# By area
gh issue list --repo RobertoChavez2433/DevKey --label "area:ime-lifecycle" --state open

# By category
gh issue list --repo RobertoChavez2433/DevKey --label "category:IME" --state all

# Recent fixes (30d)
gh issue list --repo RobertoChavez2433/DevKey --label defect --state closed --search "closed:>$(date -d '-30 days' +%Y-%m-%d)"

# Full JSON for pattern scanning
gh issue list --repo RobertoChavez2433/DevKey --label "area:<area>" --state open --json number,title,body,labels --limit 20
```

---

## Label Taxonomy

- `defect` — marker label, every defect issue has this
- `category:{IME|UI|MODIFIER|NATIVE|BUILD|TEXT|VOICE|ANDROID}` — single category per issue
- `area:{ime-lifecycle|compose-ui|modifier-state|native-jni|build-test|text-input|voice-dictation}` — primary concern
- `priority:{critical|high|medium|low}`

---

## DevKeyLogger Category → GH Labels Mapping

| DevKeyLogger Method | category label | area label |
|---------------------|---------------|------------|
| `DevKeyLogger.ime()` | `category:IME` | `area:ime-lifecycle` |
| `DevKeyLogger.ui()` | `category:UI` | `area:compose-ui` |
| `DevKeyLogger.modifier()` | `category:MODIFIER` | `area:modifier-state` |
| `DevKeyLogger.native()` | `category:NATIVE` | `area:native-jni` |
| `DevKeyLogger.text()` | `category:TEXT` | `area:text-input` |
| `DevKeyLogger.voice()` | `category:VOICE` | `area:voice-dictation` |
| `DevKeyLogger.error()` | any category (use judgment) | any area |

---

## Issue Body Template

```
**Pattern**: <what to avoid — 1 line>
**Prevention**: <how to avoid — 1-2 lines>
**Ref**: <file:line references>
**Root cause session**: <link to debug-sessions/*.md if applicable>
```

---

## Defect Lifecycle

- `open` — active pattern to avoid
- `closed` with comment referencing fix commit SHA — pattern resolved
- No max count, no rotation, no archive — GitHub handles it

---

## Defect Filing Template

If the user wants a new GitHub defect recorded, capture:

- title
- category
- area
- priority
- short pattern summary
- short prevention note
- `file:line` references

Use the existing repo issue workflow for creation instead of inventing a local
defect file.
