# State Archive

Older session-history entries were pruned on 2026-04-10 to keep the live
`.claude` surface lean.

### Session 51 (2026-04-11)

- Punctuation heuristics bug fixed; key coordinate offset fixed.
- 14 new E2E tests. Long press tests hardened with retry logic.
- Best run: 52/54.

### Session 50 (2026-04-11)

- Consolidated word learning to `SessionDependencies.commitWord()`.
- Fixed autocorrect suppression (only user-added words, not auto-learned).
- Fixed E2E port defaults to 3950. All 40 tests green.

### Session 49 (2026-04-11)

- Full codebase audit against pre-release-spec.md — produced comprehensive
  implementation plan covering all remaining blockers.

### Session 48 (2026-04-11)

- Completed Wave 8 DI seam refactor: all collaborators decoupled from LatinIME.
- All 40 E2E tests passing after fixing autocorrect test infrastructure.
- Delete key long-press fixed. Key press preview popup implemented.
- Voice E2E fully verified with punctuation/proper nouns.

### Session 45 (2026-04-10)

- Stabilized the Samsung E2E flow and landed three commits around logging,
  modifier reset behavior, and harness recovery.
- Confirmed the remaining failures were narrowed to FULL-mode CTRL coordinates.

### Session 44 (2026-04-09)

- Landed the Phase 2 regression-infrastructure work and kept build/lint green.

Keep this file for future session rotation only.
