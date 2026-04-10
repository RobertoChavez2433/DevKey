# Blast Radius — Phase 2

**Methodology**: Kotlin resolver broken (see `feedback_jcodemunch_kotlin_resolver_broken.md`). All counts below are from `search_text` over `app/src/main/java`, `tools/`, `.claude/test-flows/`, and `.claude/skills/test/`.

## 2.1 — `DevKeyLogger.enableServer(url)` wire-up

### Direct dependents of the symbol being changed (`DevKeyLogger.enableServer`)

| Location | Count | Notes |
|---|---|---|
| Call sites in production | **0** | Dormant API — nobody calls it yet |
| Call sites in tests | **0** | |
| Call sites in scripts / tooling | **0** | |

**Add 1**: one new caller in `LatinIME.onCreate()` via a new debug-only broadcast receiver.

### Transitive dependents of `DevKeyLogger` (the object, for all methods)

| Location | Count | Via |
|---|---|---|
| `app/src/main/java/**` | 17 files | Via category methods `ime()`, `ui()`, `modifier()`, `text()`, `native()`, `voice()`, `error()`, `hypothesis()` |
| `app/src/test/java/**` | 0 files | Not mocked / not asserted anywhere |
| `tools/**` | 0 | |

**Conclusion**: Adding a caller to `DevKeyLogger.enableServer` does NOT perturb any existing caller of any other `DevKeyLogger` method. All existing `DevKeyLogger.foo(...)` calls continue to route to `Log.d` regardless of whether the server is enabled, because `sendToServer` early-returns on null `serverUrl` (DevKeyLogger.kt:97: `val url = serverUrl ?: return`). Behavior without the server = unchanged.

### Affected tests

- `app/src/test/java/dev/devkey/keyboard/debug/DevKeyLoggerTest.kt` — **does not exist**. Phase 2 should add one (minimum: verify `enableServer` sets state, `disableServer` clears state, null state path is a no-op).
- No existing test currently asserts `DevKeyLogger` behavior.
- 0 existing tests will break from Phase 2.1.

### Cleanup targets
None. 2.1 is purely additive.

## 2.2 — Test driver server

### Direct dependents of `tools/debug-server/server.js`

| Caller | Path | Status |
|---|---|---|
| `/systematic-debugging` skill (deep mode) | `.claude/skills/systematic-debugging/SKILL.md` | Reads `http://localhost:3947/logs` — will continue to work if Phase 2 preserves existing endpoints |
| `DevKeyLogger.sendToServer` | `app/src/main/java/.../DevKeyLogger.kt:91` | POSTs `/log` — compatible |

**If the plan extends `server.js` instead of creating `tools/test-driver/`**: both existing callers keep working because `/log`, `/logs`, `/health`, `/categories`, `/clear` all stay. New endpoints are additive.

**If the plan creates `tools/test-driver/` as a separate process**: the debug server and the test driver run on DIFFERENT ports. DevKeyLogger needs to know both URLs OR the test driver must proxy `/log` to the debug server OR (simplest) the driver server **is** the extended debug server and the systematic-debugging skill just keeps talking to the same port. **Strong recommendation**: extend in place.

### Transitive dependents

- `.claude/skills/systematic-debugging/**` — read-only consumer of `/logs`. No breakage.
- `.claude/skills/test/**` — does NOT currently talk to the debug server. Phase 2 adds this coupling.

### Affected tests
None — the debug server has no tests today. Phase 2 may add smoke tests for the new driver endpoints (e.g., `curl http://127.0.0.1:3947/wait?category=...` with a canned log stream).

### Cleanup targets
None initially. AFTER Phase 2.4 stabilization, the plan may retire:
- `.claude/agents/test-wave-agent.md` (if the driver server replaces wave agents)
- `.claude/agents/test-orchestrator-agent.md` (if the driver server owns orchestration)
- Parts of `.claude/skills/test/SKILL.md` (wave dispatch section)

**Plan decision**: whether to retire the Claude-agent test model entirely, keep it as a fallback, or run both side-by-side.

## 2.3 — New test flows

### Files touched in `app/` for instrumentation

| File | Edit | Blast |
|---|---|---|
| `LatinIME.kt` (next-word instrumentation at line ~1953) | Add 2 `DevKeyLogger.text(...)` calls inside `setNextSuggestions()` | Zero downstream — `setNextSuggestions` has no callers that assert on log output |
| `KeyPressLogger.kt` (optional migration to DevKeyLogger) | OPTIONAL | Current callers: `KeyView.kt:248, 321` — 2 call sites, trivial |

### Files touched in `.claude/test-flows/`

- 4 new flow files OR registry appendix
- `registry.md` updated with new entries

### Affected tests
- Unit tests: NONE affected by new instrumentation (string content isn't asserted)
- Existing E2E flows: NONE broken

### Cleanup targets
- `.claude/test-flows/phase1-voice-verification.md` (manual checklist) — **becomes redundant** once `voice-round-trip` automated flow lands. Plan decision: keep manual as belt-and-suspenders, or delete.
- `.claude/test-flows/phase1-regression-smoke.md` — same question. Phase 2.5 coverage matrix should explicitly document which manual checklists are superseded by automated flows.

## 2.4 — Tier stabilization

### Files touched in `app/`

| File | Edit | Blast |
|---|---|---|
| `LatinIME.kt` (new SET_LAYOUT_MODE receiver, if chosen) | +~15 lines next to DUMP_KEY_MAP receiver | Zero downstream — a new debug-only broadcast receiver mirrors an existing pattern |

### Files touched in `.claude/`

- `.claude/docs/reference/key-coordinates.md` — add COMPACT + COMPACT_DEV rows
- `.claude/test-flows/registry.md` — update layout-modes flow notes
- `.claude/skills/test/references/ime-testing-patterns.md` — add COMPACT coordinate ranges

### Affected tests
- Existing `/test` flows: some become flakier-to-nonflaky depending on root cause of instability. Not a breakage, but may require re-running to calibrate the new coordinate tables.
- Python E2E (`tools/e2e/`): untouched unless the plan chooses the Python-extension path for the driver server.

### Cleanup targets
None.

## 2.5 — Coverage verification

### Blast
Pure documentation + a lightweight script. Zero runtime changes. Zero test changes.

### Cleanup targets
None.

## Summary — Phase 2 blast radius is small

| Dimension | Count |
|---|---|
| IME runtime files edited | 2 (`LatinIME.kt` for 2 new broadcast receivers; `KeyPressLogger.kt` optionally) |
| IME runtime files created | 0 |
| IME runtime lines of net diff | < 80 |
| Tests broken | 0 |
| Tests added | 1-2 (new `DevKeyLoggerTest.kt` minimal) |
| External tooling files edited | 1 (`tools/debug-server/server.js`) |
| External tooling files created | 3-5 (driver server modules, flow definitions) |
| Test flows added | 4 |
| Test flows retired | 0 (possibly 2 manual checklists retired in 2.5 — plan decision) |

**All IME-side changes are gated on `KeyMapGenerator.isDebugBuild(context)` — zero release-build exposure.**

**Phase 2 is tooling-heavy, IME-light.** The plan author should weight effort accordingly: ~20% in `app/`, ~80% in `tools/` + `.claude/test-flows/`.
