# Phase 2.5 Coverage Matrix

Maps every Phase 1 feature (from `.claude/specs/2026-04-08-pre-release-vision-spec.md` §2.1)
to its backing automated test flow(s). Per spec §6 Phase 2 item 2.5 every Phase 1 feature
must have at least one automated test flow — Phase 2 delivers that bar.

Updated: 2026-04-08 (Phase 2)

## Automated coverage

| Phase 1 feature | Spec ref | Automated flow(s) | Test module |
|---|---|---|---|
| Voice input E2E | §2.1, §5 | `voice-round-trip` | `tools/e2e/tests/test_voice.py` |
| SwiftKey visual parity (dark, COMPACT) | §2.1, §4 | `swiftkey-visual-diff` | `tools/e2e/tests/test_visual_diff.py` |
| SwiftKey visual parity (other modes) | §2.1, §4 | `swiftkey-visual-diff` (SKIP until reference captured) | `tools/e2e/tests/test_visual_diff.py` |
| Long-press popups (every key, every mode) | §2.1, §4.2 | `long-press-coverage` | `tools/e2e/tests/test_long_press.py` |
| Predictive next-word after space | §2.1, §4.3 | `next-word-prediction` | `tools/e2e/tests/test_next_word.py` |
| Keyboard mode switching (Normal ↔ Symbols) | §2.1 | `mode-switching` | `tools/e2e/tests/test_modes.py` |
| Layout mode switching (FULL / COMPACT / COMPACT_DEV) | §2.1 | `layout-modes` + `test_layout_mode_round_trip` | `tools/e2e/tests/test_modes.py` |
| Modifier states (Shift, Ctrl, Alt one-shot) | §2.1 | `modifier-states` | `tools/e2e/tests/test_modifiers.py` |
| Caps Lock | §2.1 | `caps-lock` | `tools/e2e/tests/test_modifiers.py` |
| Modifier combos (Ctrl+C/V/A/Z, Alt+Tab) | §2.1 | `modifier-combos` | `tools/e2e/tests/test_modifier_combos.py` (Phase 2 sub-phase 4.10) |
| Clipboard panel | §2.1, §3 | `clipboard-smoke` | `tools/e2e/tests/test_clipboard.py` (Phase 2 sub-phase 4.6) |
| Macro system | §2.1, §3 | `macros-smoke` | `tools/e2e/tests/test_macros.py` (Phase 2 sub-phase 4.7) |
| Command mode | §2.1, §3 | `command-mode-smoke` | `tools/e2e/tests/test_command_mode.py` (Phase 2 sub-phase 4.8) |
| Plugin system | §2.1, §2.4 | `plugin-smoke` | `tools/e2e/tests/test_plugins.py` (Phase 2 sub-phase 4.9) |
| Basic typing | §2.1 | `typing` | `tools/e2e/tests/test_smoke.py` |
| Rapid input stress | §2.1 | `rapid-stress` | `tools/e2e/tests/test_rapid.py` |

## Supplementary manual smoke (belt and suspenders)

Phase 1 manual-checklist files continue to exist as additional verification signal
alongside the automated flows above. They are NOT the primary coverage source for
any feature — every Phase 1 feature has an automated flow as required by spec §6
Phase 2 item 2.5. Manual checklists are re-run as part of the Phase 3 manual smoke
gate (§3.6) on top of automated coverage.

| Checklist | Phase 1 coverage |
|---|---|
| `.claude/test-flows/phase1-voice-verification.md` | Voice (supplements test_voice.py) |
| `.claude/test-flows/phase1-regression-smoke.md` | Clipboard/macros/command/plugin (supplements test_clipboard.py, test_macros.py, test_command_mode.py, test_plugins.py) |

## Phase 3 gate — release blocker mapping

Per spec §6 Phase 3, release cannot proceed until every row in the "Automated coverage"
table above is green AND the supplementary manual checklists have a signed-off run.

| Phase 3 gate item | Mechanism |
|---|---|
| §3.1 — Full `/test` run all three tiers green | All rows in "Automated coverage" pass on FULL, COMPACT, COMPACT_DEV |
| §3.2 — Voice round-trip flow green | `voice-round-trip` |
| §3.3 — Next-word prediction flow green | `next-word-prediction` |
| §3.4 — Long-press coverage flow green | `long-press-coverage` |
| §3.5 — SwiftKey visual diff within tolerance | `swiftkey-visual-diff` (or manual review until all references captured) |
| §3.6 — Manual smoke on emulator | Phase 1 checklists re-run |
| §3.7 — Lint clean | `./gradlew lint` clean on CI |

## Known reference capture gaps (blockers for full Phase 3.5 green)

> **Reference capture is Phase 1 backlog (spec §6 Phase 1.2), NOT Phase 2 scope.
> Visual-diff tests SKIP gracefully until references are captured.**


From spec §4.4.1:
- Qwerty / Full / Symbols / Fn / Phone modes — dark theme references NOT captured
- Every long-press popup from SwiftKey — NOT captured
- Light theme — DEFERRED post-v1.0 per user decision

**Until captured**, the `swiftkey-visual-diff` flow SKIPs (rather than FAILs) for
missing references. This is intentional — see `test_visual_diff.py`.
