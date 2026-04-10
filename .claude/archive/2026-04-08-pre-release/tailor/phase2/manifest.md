# Tailor Manifest — v1.0 Pre-Release Phase 2 (Regression Infrastructure)

**Spec**: `.claude/specs/pre-release-spec.md` (§6 Phase 2)
**Slug**: `pre-release-phase2`
**Tailor date**: 2026-04-08
**Scope**: Phase 2 ONLY — items 2.1 through 2.5. Phases 1 (complete), 3, 4, 5 are out of scope.
**Repo key**: `local/Hackers_Keyboard_Fork-e04f25e5`

## Phase 2 scope (from spec §6)

| Item | What |
|---|---|
| 2.1 | HTTP debug server integration — `DevKeyLogger.enableServer(url)` wired into `/test` harness |
| 2.2 | Test driver server — external process receiving HTTP-forwarded logs, coordinates waves, replaces sleep-based sync |
| 2.3 | New test flows — voice round-trip, next-word prediction, long-press coverage, SwiftKey visual diff |
| 2.4 | Existing tier stabilization — FULL + COMPACT + COMPACT_DEV to 100% green |
| 2.5 | Coverage verification — every Phase 1 feature has at least one automated flow |

## Files analyzed

**Runtime logging surface (client side of HTTP debug server)**
- `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`
- `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt`
- `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` (lines 395-423 for broadcast receiver pattern; 1880-1960 for `setNextSuggestions`; 817 for `shouldShowVoiceButton` stub)

**Voice feature (instrumentation already landed in Session 42)**
- `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt`
- `.claude/test-flows/phase1-voice-verification.md` (manual checklist — Phase 2.3 voice flow replaces this with automation)

**Existing test infrastructure (prior art)**
- `tools/debug-server/server.js` (existing Node HTTP log collector — candidate to extend for 2.2)
- `tools/e2e/` (existing Python ADB E2E harness — alternate prior art for driver server)
- `.claude/skills/test/SKILL.md` (Claude-agent-based test orchestrator — flagged for redesign per `.claude/memory/test-skill-redesign.md`)
- `.claude/agents/test-orchestrator-agent.md`
- `.claude/agents/test-wave-agent.md`
- `.claude/skills/test/references/ime-testing-patterns.md`
- `.claude/test-flows/registry.md` (existing 8 flows)

**Ground-truth sources**
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt` (KeyCodes object — `KEYCODE_VOICE = -102`)
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/LayoutMode.kt`
- `app/src/main/java/dev/devkey/keyboard/core/KeyboardModeManager.kt`

## Patterns discovered (Phase 3)

| Pattern | File | Relevance |
|---|---|---|
| [HTTP log-forwarding](patterns/http-log-forwarding.md) | DevKeyLogger.kt | 2.1 — the exact client-side API to wire |
| [Debug broadcast receiver](patterns/debug-broadcast-receiver.md) | LatinIME.kt + KeyMapGenerator.kt | 2.1 — canonical pattern for triggering debug behavior from ADB |
| [DevKey log categories](patterns/devkey-log-categories.md) | DevKeyLogger.kt | 2.2 — the exact tag strings the driver server filters on |
| [Voice state instrumentation](patterns/voice-state-instrumentation.md) | VoiceInputEngine.kt | 2.3 — state-transition events already emitted for voice round-trip flow |
| [ADB + logcat test runner](patterns/python-adb-e2e.md) | tools/e2e/ | 2.2, 2.4 — existing Python harness prior art |
| [Claude wave-agent test orchestrator](patterns/claude-wave-agent-test.md) | .claude/skills/test/ | 2.4 — what the driver server must replace or stabilize |
| [Key coordinate calibration cascade](patterns/key-coordinate-calibration.md) | KeyMapGenerator.kt + LatinIME.kt:417 | 2.3, 2.4 — broadcast → cache → scan fallback |

## Ground-truth discrepancies

**NONE** from the spec text itself against source.

**One open architectural question** (not a discrepancy, but the plan author must resolve):

> Spec §2.2 requires `DevKeyLogger.enableServer(url)` to be "enabled during test runs," but in the current source this method is **never called from production code** — it's a dormant API. No runtime config path exists for the test harness to push a URL into the IME process. The plan must invent a delivery mechanism.

Three options documented in `patterns/http-log-forwarding.md#delivery-mechanisms`:
- **A.** Broadcast intent (mirrors `dev.devkey.keyboard.DUMP_KEY_MAP` — debug-only receiver reads extras, calls `enableServer`)
- **B.** `SharedPreference` written via `adb shell settings put` + a one-shot observer in `LatinIME.onCreate`
- **C.** System property (`adb shell setprop`) read at IME boot

The existing `DUMP_KEY_MAP` broadcast is the closest prior art — option A is the lowest-friction extension.

## Open questions for plan author (beyond the three §9 spec questions)

1. **Delivery mechanism for `enableServer(url)`** — see ground-truth section above. Plan must pick A/B/C.
2. **Which existing test runner does the driver server extend/replace?**
   - `/test` skill (Claude wave agents) is flagged for redesign per `.claude/memory/test-skill-redesign.md` (too expensive, too slow, too unreliable).
   - `tools/e2e/` Python harness is closer to the spec intent (deterministic, logcat-asserting, sleep-free potential) but unexercised lately.
   - Options: (a) replace Claude wave agents with the driver server and keep `/test` skill as a thin CLI wrapper; (b) extend `tools/e2e/` into the driver server and retire `/test`; (c) keep both and share the HTTP log backend. Plan must decide.
3. **Visual-diff library + tolerance** — spec §9 Q3 flags this. Tolerance default = SSIM ≥ 0.95 is a common Phase 2 starting point; the plan must pick a dependency (Python `scikit-image.metrics.structural_similarity`, `Pillow.ImageChops`, or shell out to ImageMagick `compare -metric SSIM`).
4. **Wave-gating replacement for sleeps** — spec §2.2 says "gates wave progression on observed state rather than sleeps." Plan must specify the observable signals: which log categories, which "wave-ready" conditions, and the timeout policy when a signal never arrives.
5. **Driver-server lifetime and ownership** — does the driver server run in CI too, or only locally? Is it a long-lived daemon, or spawned per test run? Does it persist state between runs?
6. **COMPACT mode coordinate calibration** — registry currently says "COMPACT and COMPACT_DEV testing requires changing the layout preference, which is not yet automated." Plan must specify how 2.4 automates the preference change (likely `adb shell settings put` or `am broadcast` to update `KEY_LAYOUT_MODE` = `"compact"`/`"compact_dev"`/`"full"`).

## Phase 2 research checklist

- [x] Step 1 — `index_folder` (not `index_repo`) — 2 files changed, incremental
- [x] Step 2 — File outlines (jcodemunch outline endpoint returned internal errors on DevKeyLogger.kt and VoiceInputEngine.kt; direct `Read` substituted)
- [x] Step 3 — Dependency graph (substituted `search_text` per Kotlin-resolver feedback memory)
- [x] Step 4 — Blast radius (substituted `search_text` for each changed symbol)
- [x] Step 5 — Find importers (substituted `search_text`)
- [x] Step 6 — Class hierarchy (not applicable — no classes extended/implemented within Phase 2 scope)
- [x] Step 7 — Dead code (not applicable — Phase 2 is additive infrastructure, not cleanup)
- [x] Step 8 — Symbol search (`DevKeyLogger`, `VoiceInputEngine`, `setNextSuggestions`, `KEYCODE_VOICE`, `DUMP_KEY_MAP`, `longPressCode`, `toggleMode`, `shouldShowVoiceButton`)
- [x] Step 9 — Symbol source (captured in `source-excerpts/by-file.md`)
- [ ] Phase 5 gap-fill agents — **NOT DISPATCHED.** All questions resolvable from source + prior art; no open research unknowns beyond the plan-author decisions listed above.

## Methodology notes

- **jcodemunch Kotlin-resolver workaround**: per `.claude/memory/feedback_jcodemunch_kotlin_resolver_broken.md`, `find_importers` / `get_blast_radius` / `find_dead_code` / `get_dependency_graph` return no-signal on Kotlin files. Used `search_text` + direct `Read` for every dependency and importer question in this tailor.
- **`get_file_outline` internal errors**: two calls returned `{"error":"Internal error processing get_file_outline"}` during this session (DevKeyLogger.kt, VoiceInputEngine.kt). Fell back to direct `Read`. Not a blocker — the files are short enough to read in full.
