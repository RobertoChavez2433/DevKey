# Session State

**Last Updated**: 2026-04-08 | **Session**: 41

## Current Phase
- **Phase**: v1.0 Pre-Release Execution — Phase 1 (Features)
- **Status**: PLAN COMPLETE for Phase 1. Session 41 ran `/writing-plans` against the Session 40 tailor. Plan at `.claude/plans/2026-04-08-pre-release-phase1.md` passed all 3 reviewers (code/security/completeness) in cycle 2 after 20 surgical fixes from cycle 1. Ready for `/implement` in Session 42, subject to user escalation decisions on open items (COMPACT scope, §9 Q1/Q2/Q4, Whisper sourcing).

## HOT CONTEXT - Resume Here

### Where Session 41 Left Off

User asked for `/writing-plans` against the latest tailor (`.claude/tailor/2026-04-08-pre-release-phase1/`). Skill executed in full: Phase 1 Accept, Phase 2 Load Tailor, Phase 3 Writer Strategy (main-agent direct — plan under 2000 lines), Phase 4 Write the Plan, Phase 5 Review Loop (cycle 1 REJECT → plan-fixer-agent → cycle 2 APPROVE).

**Deliverables**:
- `.claude/plans/2026-04-08-pre-release-phase1.md` — 8-phase implementation plan for spec §6 Phase 1 items 1.1–1.8
- `.claude/plans/review_sweeps/2026-04-08-pre-release-phase1-2026-04-08/` — 6 review reports (3 agents × 2 cycles)

### Plan Structure (8 Phases, 14 Sub-phases)

| Phase | Spec item | Key work |
|---|---|---|
| 1 | 1.1 Voice assets | assets/ dir + MODEL_PROVENANCE.md + LFS .gitattributes (human sources binaries) |
| 2 | 1.2 SwiftKey refs | test-flows/swiftkey-reference/ + README + active `.gitignore` (fail-closed) |
| 3 | 1.8 Plugin security | Sub-phase 3.0 enables `buildFeatures.buildConfig = true`; 3.1 uses compile-time `BuildConfig.DEBUG` gate on `PluginManager.getPluginDictionaries`/`getDictionary` + private helpers |
| 4 | 1.5 Next-word | `Suggest.getNextWordSuggestions(prevWord)` with state reset (mIsFirstCharCapitalized/mIsAllUpperCase/mLowerOriginalWord/mOriginalWord/mNextLettersFrequencies) + `collectGarbage` + shared `mEmptyComposer`; `LatinIME.setNextSuggestions` rewrite + `getLastCommittedWordBeforeCursor` helper |
| 5 | 1.4 Long-press | KeyData `longPressCodes: List<Int>?` field, QwertyLayout + SymbolsLayout data fill, KeyView multi-char popup (no escape hatch). **Hard user-escalation gate for COMPACT letter-key scope.** |
| 6 | 1.3 Layout fidelity | DevKeyTheme token retune against SwiftKey refs; BOTH light + dark palette required |
| 7 | 1.6 Voice E2E | DevKeyLogger.voice instrumentation with load-bearing privacy policy (no transcript/audio in payloads); Phase 7.2 manual checklist |
| 8 | 1.7 Regression smoke | Manual checklist for clipboard/macros/command/plugins |

### OPEN ITEMS (block `/implement`)

1. **COMPACT letter-key long-press scope** — Phase 5 has a hard escalation gate. User must choose:
   - **(A)** Follow spec §4.2 literally (populate COMPACT letter-row long-press, override `KeyData.kt:22` design comment)
   - **(B)** Spec amendment adding COMPACT scope exemption to §3 Non-Goals or scoping §4.2 to "full parity modes"
2. **Spec §9 Q1** — Distribution channel (Play Store / F-Droid / both) — OPEN
3. **Spec §9 Q2** — Min API target (26 vs 28/29) — OPEN
4. **Spec §9 Q4** — Multilingual voice bundle — default English-only unless overridden
5. **Whisper model sourcing** (human operator, out-of-band) — pick a canonical source, verify SHA256, license audit
6. **SwiftKey reference capture** (human operator) — screenshots for all modes × 2 themes + long-press popups
7. **BinaryDictionary.getBigrams tolerance for size()==0 WordComposer** — Phase 4.1 Step 0 prerequisite verification required before editing Suggest.kt

### Review Cycle Summary

**Cycle 1 REJECT** (all 3 reviewers):
- Code (2 HIGH, 3 MEDIUM, 1 LOW): Suggest state-reset missing, ambiguous insert-point, wrong comments, no BinaryDictionary JNI verification, wrong line ref
- Security (3 MEDIUM, 2 LOW): Prefer BuildConfig.DEBUG over FLAG_DEBUGGABLE, `.gitignore` `*.png` not active, no voice privacy policy, helpers not private, password-field check missing
- Completeness (4 CRITICAL, 4 MINOR): Fn/Phone missing, COMPACT scope baked in, 5.4 escape hatch, 6.1 light-theme deferral, §9 Q1/Q2/Q4 not tracked, APK escalation missing, action-key coverage partial, intermediate verification wording

**Fixer applied 20 surgical edits.**

**Cycle 2 APPROVE** (all 3 reviewers): all cycle-1 findings closed, no new concerns.

### EXACTLY WHAT SESSION 42 MUST DO

**Before running `/implement`**:
1. Resolve COMPACT scope question (option A or B)
2. Record §9 Q1/Q2 decisions; confirm Q4 default
3. Ensure Whisper assets + SwiftKey refs are staged OR accept that Phase 1 / Phase 2 of the plan will be partial
4. Run `/implement` on `.claude/plans/2026-04-08-pre-release-phase1.md`

### Critical Tailor Findings (for plan author)

1. **`LatinIME.setNextSuggestions` is a 3-line stub** (line 1895-1897) — only populates punctuation via `mSuggestPuncList`. The bigram read path in `Suggest.getSuggestions` + `Dictionary.getBigrams` is fully functional but gated on `wordComposer.size() == 1`. Phase 1.5 needs a NEW zero-char entry point (recommend: add `Suggest.getNextWordSuggestions(prevWord)`).
2. **`VoiceInputEngine` is fully wired** into `DevKeyKeyboard.kt:81-249`. Phase 1.1 is PURE asset sourcing — zero code changes. Phase 1.6 E2E verification is ready as soon as assets land.
3. **`KeyData.longPressCode/longPressLabel` already exist and dispatch end-to-end via `KeyView.kt:213-215`.** Phase 1.4 is mostly a data-fill task in `QwertyLayout.buildCompactLayout` / `buildCompactDevLayout` / `SymbolsLayout.buildLayout`. **Scope conflict**: spec §4.2 wants "every key" but `KeyData.kt:27` comment says COMPACT intentionally has no letter long-press. Plan author decision.
4. **`PluginManager` loads unsigned external APK resources** via `getResourcesForApplication` — GH #4 is real. Minimal fix: gate `getPluginDictionaries` and `getDictionary` behind `ApplicationInfo.FLAG_DEBUGGABLE`. Exemplar: `KeyMapGenerator.isDebugBuild()` at KeyMapGenerator.kt:38. Consumers already handle null.
5. **TF Lite 2.16.1** already wired in `gradle/libs.versions.toml` + `app/build.gradle.kts:110-111`. No dep work.
6. **`app/src/main/assets/` dir missing** (verified via `ls`). Phase 1.1 must create it.
7. **Possible `KeyData` extension**: `longPressCodes: List<Int>?` for multi-char SwiftKey popups (backward-compatible default).

### Methodology Limitation Discovered

**jcodemunch Kotlin import resolution is broken for this repo**. `get_dependency_graph`, `get_blast_radius`, `find_importers`, and `find_dead_code` all return no-signal output for Kotlin files (every file reports `importer_count = 0`, 362 files flagged as "dead"). Steps 3/4/5/7 of the prescribed tailor sequence required fallback to `search_text` for call-site analysis. Documented in tailor manifest. **Do NOT trust jcodemunch dead-code / blast-radius output on this repo until the Kotlin resolver is fixed upstream.**

### EXACTLY WHAT SESSION 41 MUST DO

**Run `/writing-plans` against the tailor directory**:

- Tailor path: `.claude/tailor/2026-04-08-pre-release-phase1/`
- Scope: Phase 1 ONLY — do not expand to phases 2-5
- Open questions to resolve in plan:
  1. Next-word entry point: new `Suggest.getNextWordSuggestions` method (recommended) vs direct `Dictionary.getBigrams` from LatinIME
  2. Whisper model canonical source (with SHA256 capture)
  3. COMPACT mode long-press scope (follow SwiftKey vs preserve current design)
  4. Plugin gate: `BuildConfig.DEBUG` constant vs `ApplicationInfo.FLAG_DEBUGGABLE` runtime flag
- Output: phased implementation plan for the 8 Phase 1 work items (1.1 through 1.8 per spec §6)

### Locked Decisions from Session 39 Brainstorming

| # | Decision | Lock status |
|---|---|---|
| 1 | Strict sequencing: Features → Tests → Refactor → Release | LOCKED — no parallel tracks |
| 2 | SwiftKey parity = full (visual + structural + behavioral) | LOCKED |
| 3 | Behavioral parity scoped to ONLY predictive next-word after space | LOCKED — no swipe-to-type, no cursor-swipe, no delete-swipe, no emoji swipe |
| 4 | Voice: bundle `whisper-tiny.en.tflite` + `filters_vocab_en.bin` in APK | LOCKED — sourcing is Phase 1.1 work |
| 5 | Regression bar B + HTTP debug server (`DevKeyLogger.enableServer`) + driver server integrated into test loop | LOCKED |
| 6 | Out of scope: swipe-to-type, multi-lang voice, custom themes | LOCKED |
| 7 | In scope (polish-only, no new features): clipboard, macros, command mode, plugins (with GH #4 security fix as Phase 1.8) | LOCKED |
| 8 | 400-line rule is the FINAL gate before release (Phase 4), not post-release | LOCKED — user's exact words |

### Preliminary Audit Findings (frozen — see audit report for detail)

**9 files violate the 400-line hard rule:**

| # | File | Lines | Worst hotspot inside |
|---|---|---:|---|
| 1 | LatinIME.kt | 2,679 | 141 methods, 12 responsibilities, 10 of top 25 project hotspots |
| 2 | ComposeSequence.kt | 1,144 | `reset` cyclo 42 + ~1000 lines of static data |
| 3 | Keyboard.kt | 1,004 | `Key.getPopupKeyboardContent` cyclo 43; `loadKeyboard` nesting 9 |
| 4 | LatinKeyboard.kt | 956 | `isInside` cyclo 38 nesting 9 |
| 5 | ExpandableDictionary.kt | 707 | `getWordsRec` cyclo 40 nesting 9 **9 params** |
| 6 | Suggest.kt | 533 | `getSuggestions` cyclo 52 |
| 7 | KeyboardSwitcher.kt | 528 | `getKeyboardId` cyclo 27 + singleton god |
| 8 | core/KeyEventSender.kt | 481 | `sendModifiableKeyChar` cyclo 55 |
| 9 | CandidateView.kt | 473 | Legacy Android View — verify still used first |

**Complexity hotspots (files under 400 but functions block release):**
- `KeyView` composable: cyclo **78**, nesting **10** (#1 project hotspot)
- `DevKeyKeyboard` composable: cyclo 42, churn **9** (#1 churn in project)

All tracked in GH #9.

### Key Testing Resumption Guide

```bash
# Install + force restart IME:
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell "am force-stop dev.devkey.keyboard"
adb -s emulator-5554 shell "ime enable dev.devkey.keyboard/.LatinIME"
adb -s emulator-5554 shell "ime set dev.devkey.keyboard/.LatinIME"
```

## Blockers

- **COMPACT long-press scope decision** — Phase 5 of the Session 41 plan has a hard user-escalation gate blocking `/implement`. User must pick (A) follow spec or (B) spec amendment.
- **Spec §9 Q1/Q2 OPEN** — distribution channel + min API target. Plan header tracks these as blocking Phase 1 close.
- **Whisper model files missing** — `app/src/main/assets/` still does not exist. Phase 1 of plan has provenance scaffold ready; human operator sources binaries.
- **SwiftKey reference screenshots** not yet captured. Phase 2 of plan has directory + README + fail-closed `.gitignore` scaffold.
- **GH #4 plugin security** — fix is in plan (Phase 3: BuildConfig.DEBUG compile-time gate). Not yet implemented.
- **BinaryDictionary.getBigrams zero-length WordComposer tolerance** — Phase 4.1 Step 0 prerequisite: must be verified before editing Suggest.kt. Escalation path documented if native requires size() >= 1.
- **9 files over 400 lines** — final release gate (Phase 4 of umbrella spec). Tracked in GH #9.
- **jcodemunch Kotlin import resolver broken** — `find_importers`, `get_blast_radius`, `find_dead_code`, `get_dependency_graph` return no-signal on Kotlin files in this repo. Fallback to `search_text` documented. Affects all future tailor runs on DevKey.
- **Uncommitted backlog**: Sessions 36/37/38/39/40/41 DevKey-side changes not yet committed.
- **Wasted space in Symbols/other keyboard modes** — addressed during Phase 6 layout fidelity work against SwiftKey reference.
- **Compose UI tests blocked on API 36** (Espresso `InputManager.getInstance` reflection incompat).

## Recent Sessions

### Session 41 (2026-04-08)
**Work**: Ran `/writing-plans` against `.claude/tailor/2026-04-08-pre-release-phase1/`. Main-agent wrote the plan directly (scoped under 2000 lines). Plan structure: 8 phases / 14 sub-phases covering spec §6 Phase 1 items 1.1–1.8 in dependency order (voice assets → SwiftKey refs → plugin gate → next-word → long-press → layout fidelity → voice E2E → regression smoke). Dispatched 3 reviewers in parallel (code-review, security, completeness) — all REJECT in cycle 1 with 20 total findings. Dispatched plan-fixer-agent with consolidated list; fixer applied all 20 edits surgically. Re-dispatched 3 reviewers in parallel — all APPROVE in cycle 2. Key plan decisions: compile-time `BuildConfig.DEBUG` plugin gate (stricter than runtime `FLAG_DEBUGGABLE`) via new Sub-phase 3.0 enabling `buildFeatures.buildConfig = true`; `Suggest.getNextWordSuggestions` with full state reset (mIsFirstCharCapitalized/mIsAllUpperCase/mLowerOriginalWord/mOriginalWord/mNextLettersFrequencies) + `collectGarbage` + shared `mEmptyComposer`; `LatinIME.getLastCommittedWordBeforeCursor` 64-char lookback with letter-or-digit-or-apostrophe word boundary; load-bearing voice-log privacy policy at Phase 7.1 (no transcript/audio in payloads); hard user-escalation gate blocking Phase 5 until COMPACT letter-key scope is decided; no escape hatches for multi-char popups (5.4) or light-theme deferral (6.1); Spec §9 Q1/Q2/Q4 tracked. Saved 6 review reports under `.claude/plans/review_sweeps/2026-04-08-pre-release-phase1-2026-04-08/`.
**Decisions**: Plan writing strategy = main-agent direct (under 2000 lines). Plugin gate = compile-time BuildConfig.DEBUG (not runtime FLAG_DEBUGGABLE). COMPACT letter-key scope → user escalation, NOT silent default. Multi-char popup UX + light theme → required, no escape hatch. Fn/Phone layouts → documented as variants inside existing QwertyLayout/SymbolsLayout builders (no separate files). BinaryDictionary JNI tolerance → prerequisite verification required before Phase 4.1 execution.
**Next**: (1) Resolve user escalation items (COMPACT scope, §9 Q1/Q2, Whisper source, SwiftKey captures), (2) run `/implement` on the plan, (3) commit Session 36-41 backlog.

### Session 40 (2026-04-08)
**Work**: Ran `/tailor` scoped to Phase 1 of the v1.0 pre-release spec (features only — voice sourcing, SwiftKey parity, long-press, next-word, plugin security). Flagged the umbrella spec covers 5 phases and got user confirmation to scope to Phase 1 only. Wrote 9-file tailor output under `.claude/tailor/2026-04-08-pre-release-phase1/`: manifest, dependency-graph, ground-truth, blast-radius, 6 patterns, 2 source-excerpt files. **Critical findings**: (1) `LatinIME.setNextSuggestions` line 1895 is a 3-line stub — bigram path exists but gated on `wordComposer.size() == 1`, needs new zero-char entry point. (2) `VoiceInputEngine` fully wired in `DevKeyKeyboard.kt:81-249` — Phase 1.1 is pure asset sourcing. (3) `KeyData.longPressCode/longPressLabel` already exist and dispatch; Phase 1.4 is data-fill only. (4) `PluginManager` loads unsigned external APKs — GH #4 confirmed real; `KeyMapGenerator.isDebugBuild()` at line 38 is the reusable exemplar. (5) TF Lite 2.16.1 already wired in gradle. **Methodology limitation discovered**: jcodemunch Kotlin import resolution broken for this repo — every Kotlin file reports 0 importers, 362 files flagged as false dead. Falling back to `search_text` for call-site analysis. Documented in tailor manifest + added to blockers. `/writing-plans` deferred to Session 41 per user instruction.
**Decisions**: Tailor scope limited to Phase 1 only — Phases 2-5 will each get their own tailor+plan cycle per spec §8. Fallback tool strategy for broken jcodemunch Kotlin resolver: `search_text` + `get_symbol_source` + direct `Read`. COMPACT mode long-press scope + next-word entry point + plugin gate mechanism + Whisper source all flagged as open questions for plan author.
**Next**: (1) Run `/writing-plans` against `.claude/tailor/2026-04-08-pre-release-phase1/`. (2) Commit Session 36-40 backlog.


### Session 39 (2026-04-08)
**Work**: Executed the Session 38 intent. Verified jcodemunch Kotlin patches live in MCP (`handleCharacter` returned 3 AST callers). Ran pre-release architecture audit against full `app/src/main/java` — 9 files violate the new firm 400-line rule (LatinIME 2679, ComposeSequence 1144, Keyboard 1004, LatinKeyboard 956, ExpandableDictionary 707, Suggest 533, KeyboardSwitcher 528, KeyEventSender 481, CandidateView 473) plus 2 Compose complexity hotspots (`KeyView` cyclo 78 nesting 10, `DevKeyKeyboard` cyclo 42 churn 9). 10 of top 25 project hotspots inside LatinIME. Wrote audit report `.claude/research/2026-04-08-pre-release-architecture-audit.md`. Filed umbrella GH issue **#9** — pre-release decomposition with Phase A/B/C/D roadmap + 8-collaborator LatinIME extraction order (SwipeActionHandler → FeedbackManager → DictionaryWriter → PunctuationHeuristics → PreferenceObserver → SuggestionCoordinator → InputDispatcher → ShiftStateMachine → ImeLifecycleController). Commented on #8 as superseded. Ran `/brainstorming` — 6 scoped Q rounds locking 6 decisions. Produced validated spec `.claude/specs/2026-04-08-pre-release-vision-spec.md` with 5 phases, strict sequencing, release criteria, non-goals, risk register.
**Decisions**: Features → Tests → 400-line refactor → Release (strict, no parallel). SwiftKey full parity. Behavioral parity = next-word only (swipe-to-type deferred). Voice bundled in APK (sourcing is prerequisite Phase 1.1 work). Regression bar B + HTTP debug server + driver server integrated into test loop. Out: swipe-to-type / multi-lang voice / custom themes. In (polish-only): clipboard / macros / command mode / plugins (with #4 security gate). 400-line rule is the FINAL release gate.
**Next**: (1) Run `/tailor` on the spec, (2) run `/writing-plans` to produce the phased implementation plan, (3) commit the DevKey-side backlog (Sessions 36-39).

### Session 38 (2026-04-08)
**Work**: Tooling rabbit hole — pivoted from the user's intended pre-release codebase audit to fix jcodemunch Kotlin support, which was producing a broken index that made the audit impossible. Rebased local jcodemunch fork onto upstream `v1.23.5` (97 commits) preserving the Dart commit. Applied 3 local patches: (1) `extractor.py:_extract_symbol` error-tolerant for `has_error` nodes with extractable names — recovers `LatinIME` (lines 87-2679, 146 methods) and `LatinKeyboard` from being silently dropped, (2) `extractor.py:_extract_call_name` Kotlin support for `simple_identifier` + `navigation_expression` — populates 4,295 Kotlin call references across 982 of 1455 symbols (67%), (3) `imports.py` dedup of auto-merge-introduced duplicate `_extract_dart_imports`. Full jcodemunch test suite: 2334/2334 passing. Rebuilt DevKey index from scratch — verified `LatinIME.handleCharacter` has 2 captured callers (`onKey`, `onText`) via call graph methodology. Documented architectural limit: import-based tools cannot see same-package Kotlin/Java refs by language design.
**Decisions**: jcodemunch fork is local-only — never push to either remote (saved to memory). Always use `index_folder` not `index_repo` (saved to memory). Backup branch `backup/feat-dart-pre-rebase-2026-04-08` left in place. Did NOT execute the user's original audit intent — handed off to Session 39 (which completed it).
**Next**: → Session 39 completed the audit + brainstorming + spec.

### Session 37 (2026-04-08)
**Work**: Plan-revision + live-skill-update session. Staleness-audited the 2026-03-17 restructure plan against current repo (4 committed plans need moving, P0 refs still broken minus one). Rescoped plan (938 → 1545 lines) to add full tailor + writing-plans pipeline adapted from Field Guide for Kotlin/Android/jcodemunch, including plan-writer/plan-fixer/completeness-review agents. Rewrote implement pipeline to drop headless mode entirely (Agent tool only) and restructure reviews: one completeness-review-agent per phase, full 3-sweep + fixer loop only at end of plan. Eliminated per-concern defect files: all defect tracking now GitHub Issues. Updated 8 live files to use `gh` CLI interim.
**Decisions**: No headless `claude --bare` anywhere. Per-phase review is completeness-only (spec intent vs phase); 3-agent adversarial sweep moved to end of plan. Defects = GH Issues, no file-based tracking. Plan serves as its own spec. `.claude/plans/completed/` already exists.
**Next**: (1) Create GH labels + file 7 existing defects as issues, (2) re-index jcodemunch with source_root, (3) run `/implement` on the restructure plan, (4) commit Sessions 27/31-37 backlog.

## Active Plans

- **v1.0 Pre-Release Execution — Phase 1** — PLAN COMPLETE + REVIEWED (Session 41). Plan file: `.claude/plans/2026-04-08-pre-release-phase1.md`. 8 phases / 14 sub-phases. Reviewers: APPROVE in cycle 2 (all 3). Session 42 runs `/implement` after resolving user escalation items (COMPACT scope, §9 Q1/Q2/Q4, Whisper source, SwiftKey captures). Reviews: `.claude/plans/review_sweeps/2026-04-08-pre-release-phase1-2026-04-08/`. This is P1.
- **v1.0 Pre-Release Execution — Phases 2-5** — SPEC APPROVED (Session 39), NOT YET TAILORED. Each phase gets its own tailor+plan cycle after Phase 1 lands. Phases: Regression infra → Regression gate → 400-line refactor → Release.
- **Pre-Release Architecture Audit & Brainstorming** — COMPLETE (Session 39). Outputs: audit report `.claude/research/2026-04-08-pre-release-architecture-audit.md`, GH umbrella issue #9, spec `.claude/specs/2026-04-08-pre-release-vision-spec.md`. Roll into Phase 1 plan via `/writing-plans` (Session 41).
- **.claude Directory Restructure** — PLAN REVISED (Session 37), NOT YET IMPLEMENTED. 6 phases, 1545 lines. P2 behind v1.0 release work.
- **E2E Testing** — IN PROGRESS (FULL mode 7/8 PASS Session 30, COMPACT/COMPACT_DEV pending). Subsumed into Phase 2 of v1.0 spec.
- **Dead Code Cleanup** — COMPLETE + COMMITTED (73e211b, Session 35).
- **Coordinate Calibration** — COMPLETE + COMMITTED (31d33f7, Sessions 31-32).
- **123 Fix + E2E Harness** — COMPLETE + COMMITTED (386a25b, Session 27).
- **Kotlin Migration** — FULLY COMPLETE + COMMITTED (22a3050, Session 25).

## Reference
- **v1.0 Pre-Release Phase 1 Plan**: `.claude/plans/2026-04-08-pre-release-phase1.md` (Session 41 — reviewed APPROVE cycle 2)
- **v1.0 Pre-Release Phase 1 Reviews**: `.claude/plans/review_sweeps/2026-04-08-pre-release-phase1-2026-04-08/` (6 reports, 2 cycles)
- **v1.0 Pre-Release Phase 1 Tailor**: `.claude/tailor/2026-04-08-pre-release-phase1/` (Session 40 — 9 files)
- **v1.0 Pre-Release Spec**: `.claude/specs/2026-04-08-pre-release-vision-spec.md` (approved Session 39)
- **Pre-Release Audit Report**: `.claude/research/2026-04-08-pre-release-architecture-audit.md` (Session 39)
- **GH Umbrella Refactor Issue**: #9 (https://github.com/RobertoChavez2433/DevKey/issues/9)
- **Restructure plan**: `.claude/plans/2026-03-17-claude-directory-restructure.md`
- **Dead code cleanup plan**: `.claude/plans/completed/dead-code-cleanup-plan.md`
- **Test skill redesign**: `.claude/memory/test-skill-redesign.md`
- **Key coordinate map**: `.claude/logs/key-coordinates.md` (UPDATED Session 31)
- **Calibration design**: `docs/plans/2026-03-04-coordinate-calibration-design.md`
- **Architecture**: `docs/ARCHITECTURE.md`
- **jcodemunch local-patch record**: `.claude/memory/project_jcodemunch_kotlin_patches.md` (Session 38)
- **DevKey index repo key**: `local/Hackers_Keyboard_Fork-e04f25e5` (rebuilt Session 38, Kotlin first-class)
