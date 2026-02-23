---
name: end-session
description: End session with auto-archiving
user-invocable: true
---

# End Session

Complete session with proper handoff and auto-archiving.

**CRITICAL**: NO git commands anywhere in this skill. All analysis comes from conversation context.

## Actions

### 1. Gather Summary (From Conversation Context)
Review the current conversation and collect:
- Main focus of session
- Completed tasks
- Decisions made
- Next priorities
- Defects discovered (mistakes, anti-patterns, bugs found)

Do NOT run git commands. Use only what you observed during the session.

### 2. Update _state.md
**File**: `.claude/autoload/_state.md`

Update the full state file:
1. Increment session number in header
2. Update "Current Phase" and "Status" sections
3. Write "What Was Done This Session" with numbered items
4. Write "What Needs to Happen Next Session" with top 1-3 priorities
5. Update "Blockers" section (add new, remove resolved)
6. Add compressed session entry to "Recent Sessions":
```markdown
### Session N (YYYY-MM-DD)
**Work**: Brief 1-line summary
**Decisions**: Key decisions made
**Next**: Top 1-3 priorities
```
7. Update "Active Plans" section if plans were created or completed

If >5 sessions exist in "Recent Sessions", run rotation:
1. Take oldest session entry
2. Append to `.claude/logs/state-archive.md` under appropriate month header
3. Remove from _state.md

### 3. Update Defects
**File**: `.claude/autoload/_defects.md`

For defects discovered during this session:
1. Add new defect at the **top** of "Active Patterns" section
2. Use standardized format:
```markdown
### [CATEGORY] YYYY-MM-DD: Brief Title
**Pattern**: What to avoid (1 line)
**Prevention**: How to avoid (1-2 lines)
**Ref**: @path/to/file (optional)
```
3. If >7 defects, move oldest to `.claude/logs/defects-archive.md`

### Defect Categories
| Category | Use For |
|----------|---------|
| [ANDROID] | Activity lifecycle, ViewModel, permissions, config changes |
| [IME] | InputMethodService, key dispatch, composition, text fields |
| [PREDICTION] | Dictionary, autocorrect, command prediction, TF Lite |
| [UI] | Compose, themes, animations, layout |
| [NATIVE] | C++ NDK, JNI bridge, memory |
| [VOICE] | Speech recognition, audio, transcription |
| [BUILD] | Gradle, dependencies, ProGuard, signing |

### 4. Update JSON State Files

**PROJECT-STATE.json** (`state/PROJECT-STATE.json`):
- Update `metadata.session_notes` with brief session summary
- Update `metadata.last_updated` timestamp
- Update `active_blockers` if blockers were resolved or discovered
- Update `current_phase` if phase status changed
- Do NOT duplicate session narrative here (that belongs in _state.md)

**FEATURE-MATRIX.json** (`state/FEATURE-MATRIX.json`) — only if features were added/changed:
- Add new features to the `features` array
- Update `status` if feature status changed
- Update `summary` counts

### 5. Display Summary
Present:
- Session summary (what was accomplished)
- Features touched
- Defects logged (if any)
- Next priorities
- Reminder: Run `/resume-session` to start next session

---

## Rules
- **NO git commands** — not `git status`, not `git diff`, not `git add`, not `git commit`
- All analysis from conversation context only
- Zero user input required
- Defects go to single `.claude/autoload/_defects.md` (not per-feature files)
- Max 7 active defects — oldest rotates to archive
