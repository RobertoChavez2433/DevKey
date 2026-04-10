# Dependency Graph — Phase 2

**Methodology note**: jcodemunch's Kotlin resolver returns no-signal for `find_importers` / `get_blast_radius` / `get_dependency_graph` / `find_dead_code` on this repo (see `.claude/memory/feedback_jcodemunch_kotlin_resolver_broken.md`). All dependencies below are derived from `search_text` hits verified against the file contents.

## Direct dependencies of Phase 2 changes

### 2.1 — `DevKeyLogger.enableServer(url)` wire-up

**Changes**: Add a caller for `DevKeyLogger.enableServer(url)` somewhere in `LatinIME.onCreate()` (or a new debug-only broadcast receiver).

**Imports pulled in**:
- `DevKeyLogger` (already imported wherever instrumentation exists)
- `KeyMapGenerator.isDebugBuild(context)` — reused to gate the new code path (same pattern as DUMP_KEY_MAP)

**Files that must be edited or created**:
| File | Direction | Why |
|---|---|---|
| `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` | EDIT | Register new broadcast receiver next to existing `keyMapDumpReceiver` (lines 404-422). Scope: add ~15 lines. |
| `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt` | NO EDIT | API already exists — `enableServer(url)` / `disableServer()` are ready to call. |
| `app/src/main/AndroidManifest.xml` | NO EDIT | Broadcast is `RECEIVER_NOT_EXPORTED` per DUMP_KEY_MAP convention — no manifest entry needed. |

**Callers of `DevKeyLogger.enableServer`**: currently ZERO. Single-producer pattern — one new call site in LatinIME is sufficient.

**Transitive**: none. The HTTP sending machinery (`sendToServer` coroutine, `HttpURLConnection`, JSON serialization) is entirely self-contained in `DevKeyLogger.kt:91-119`.

### 2.2 — Test driver server

**Changes**: Extend `tools/debug-server/server.js` OR create `tools/test-driver/` (plan decision).

**Imports pulled in** (either case):
- `node:http` (already used)
- `node:child_process` (NEW — to spawn `adb` commands)
- `node:fs` (NEW — to read flow registry + write reports)
- Optionally `ws` or similar (if the plan wants a WebSocket stream to a test client)

**New endpoints needed** (candidate list — plan refines):
- `GET /wait?category=X&message=Y&match=<jsonpath>&timeout=5000` — block until matching log arrives, or timeout
- `POST /wave/start` — start a wave, return wave id
- `POST /wave/gate` — check if a wave's exit conditions are met
- `GET /wave/status` — current state of all waves
- `POST /adb/tap` — driver-side ADB (replaces per-client ADB calls from the test runner)

**Files that must be created**:
| File | Type | Purpose |
|---|---|---|
| `tools/debug-server/server.js` OR `tools/test-driver/server.js` | EDIT/CREATE | HTTP driver server (Node) |
| `tools/debug-server/wave-gate.js` OR equiv | CREATE | Wave gating logic |
| `tools/debug-server/adb-exec.js` OR equiv | CREATE | ADB command dispatch with serial pinning |
| `tools/debug-server/README.md` | CREATE/UPDATE | Docs for running the driver + CLI reference |
| `tools/debug-server/package.json` | CREATE | `npm` manifest (still dep-free, but needed to lock Node version) |

**Callers**: the test runner (spec item 2.3 flows) — driven from the plan author's chosen orchestrator (Claude `/test` skill, `tools/e2e/e2e_runner.py`, or a new Node-based runner).

**Transitive to existing code**:
- `tools/debug-server/server.js` (if extended) — already live, no downstream Kotlin consumers
- `tools/e2e/lib/adb.py` — if the plan reuses the Python harness, the driver server's `POST /adb/tap` endpoint can replace `adb.py:tap()` entirely, or both can coexist
- **`.claude/skills/test/`** — any restructure of `/test` that removes wave agents in favor of the driver server touches SKILL.md, test-orchestrator-agent.md, test-wave-agent.md

### 2.3 — New test flows

**Changes**: Add 4 new flow definitions.

**Files created**:
| File | Purpose |
|---|---|
| `.claude/test-flows/voice-round-trip.md` OR registry append | Tap voice → inject audio → assert `DevKey/VOX processing_complete` → assert committed text |
| `.claude/test-flows/next-word-prediction.md` OR registry append | Type word + space → assert `DevKey/TXT next_word_suggestions result_count≥1` → screenshot candidate strip |
| `.claude/test-flows/long-press-coverage.md` OR registry append | Iterate every key in every mode → long-press → assert `DevKeyPress LONG lpCode=<expected>` + screenshot popup |
| `.claude/test-flows/swiftkey-visual-diff.md` OR registry append | Screenshot each mode → compare against `.claude/test-flows/swiftkey-reference/<mode>-<theme>.png` → SSIM ≥ threshold |
| `.claude/test-flows/registry.md` | EDIT | Add entries referencing the 4 new flows (keeps existing format) |

**Instrumentation required in `app/`**:
- `LatinIME.setNextSuggestions()` at LatinIME.kt:1933 — add `DevKeyLogger.text("next_word_suggestions", mapOf("prev_word_length" to X, "result_count" to Y))` at line ~1953 (before the early return on non-empty result) and at the fallback (line ~1960)
- `KeyPressLogger.logLongPress` at KeyPressLogger.kt:16 — **optional** migration to `DevKeyLogger` for HTTP forwarding. Alternative: keep legacy ADB tag and have driver server scrape it.
- Voice flow needs **no** new instrumentation — Phase 1 Session 42 already landed all 16 DevKeyLogger.voice call sites.

### 2.4 — Tier stabilization (FULL + COMPACT + COMPACT_DEV)

**Changes**:
- Add a debug-only broadcast to change the layout-mode preference (so tests don't need UIAutomator on the settings activity)
- Fix whatever flakiness exists in the current 8 flows
- Extend the coordinate calibration cascade to handle all 3 modes (currently `.claude/docs/reference/key-coordinates.md` documents FULL only)

**Files likely touched**:
| File | Direction | Why |
|---|---|---|
| `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` | EDIT | Add `dev.devkey.keyboard.SET_LAYOUT_MODE` broadcast receiver (mirrors DUMP_KEY_MAP at line 404-422) |
| `app/src/main/java/dev/devkey/keyboard/data/repository/SettingsRepository.kt` | NO EDIT | The preference key already exists (`KEY_LAYOUT_MODE`) |
| `.claude/docs/reference/key-coordinates.md` | EDIT | Add COMPACT + COMPACT_DEV rows (currently FULL-only) |
| `.claude/test-flows/registry.md` | EDIT | Remove the "FULL mode only for now" note on layout-modes flow; add COMPACT/COMPACT_DEV variants |
| `.claude/test-flows/calibration.json` | (generated at runtime, gitignored) | Regenerated per mode |

### 2.5 — Coverage verification

**Changes**: pure documentation + a coverage matrix script (lightweight).

**Files created**:
| File | Purpose |
|---|---|
| `.claude/test-flows/coverage-matrix.md` | Maps every Phase 1 feature (voice, long-press, next-word, modes, modifiers, visual parity) to the flow(s) that cover it. |
| `tools/debug-server/coverage-check.js` OR equiv | Script that fails the test run if any Phase 1 feature has zero flows. |

## Transitive dependencies

**Bounded set**. Phase 2 is additive infrastructure — nothing in the existing IME runtime depends on any of the new files. The blast radius (see `blast-radius.md`) is tiny.

**The only existing-code dependency graph edge Phase 2 adds**:

```
LatinIME.onCreate()
  └─> new ENABLE_DEBUG_SERVER broadcast receiver
        └─> DevKeyLogger.enableServer(url)
              └─> (existing) HttpURLConnection POST /log
                    └─> (external) tools/debug-server/server.js
                          └─> (external) test driver wave gating
                                └─> (external) test runner (Claude skill OR Python)
```

Only one edge is added into the IME process; the rest is external tooling.

## Import graph for new code

### LatinIME.kt new imports
None — all needed types are already imported for the DUMP_KEY_MAP receiver (`BroadcastReceiver`, `Intent`, `IntentFilter`, `Context.RECEIVER_NOT_EXPORTED`, `KeyMapGenerator.isDebugBuild`, `DevKeyLogger`).

### Driver server imports (new)
- `http` (stdlib, already used)
- `child_process.spawn` (for `adb`)
- `fs/promises.readFile` (for flow registry)
- `url.URL` (stdlib, already used)

**Dependency-free policy**: existing `tools/debug-server/server.js` has zero npm deps. Plan should preserve this — adding `package.json` only for engines field and metadata.

## Tests that must be updated

**None broken by Phase 2 changes**. The existing JVM unit test suite (361 tests) and existing Python E2E tests (tools/e2e/tests/) do NOT exercise:
- `DevKeyLogger.enableServer` (dormant method)
- `setNextSuggestions` bigram wiring (Phase 1 — no unit test yet)
- Long-press popup content (covered only by manual smoke)
- Visual diff (does not exist)

**Tests that must be added by Phase 2**:
- At minimum 1 unit test in `app/src/test/java/dev/devkey/keyboard/debug/DevKeyLoggerTest.kt` asserting the new broadcast receiver path wires `enableServer` correctly (use Robolectric or a local `HttpURLConnection` mock).
