# Source Excerpts — By Concern

Same source material as `by-file.md`, re-indexed by Phase 2 spec requirement. Cross-references only — full excerpts are in `by-file.md`.

## Concern: 2.1 HTTP debug server integration

**Goal**: `DevKeyLogger.enableServer(url)` is enabled during test runs.

**Source anchors**:
- `DevKeyLogger.kt:33-44` — `serverUrl`, `enableServer`, `disableServer` (the dormant API)
- `DevKeyLogger.kt:91-119` — `sendToServer` machinery (already works end-to-end)
- `LatinIME.kt:403-422` — `keyMapDumpReceiver` (the pattern to mirror for a new `ENABLE_DEBUG_SERVER` receiver)
- `KeyMapGenerator.kt:39-41` — `isDebugBuild(context)` (the gate to reuse)
- `tools/debug-server/server.js:17-34` — `POST /log` endpoint that receives the forwarded events

**Instrumentation surfaces that WILL be exercised** once enableServer is live:
- `DevKeyLogger.voice(...)` — all 16 sites in `VoiceInputEngine.kt` (Session 42)
- `DevKeyLogger.hypothesis(...)` — ad-hoc debug markers
- Any new Phase 2 emitters added for 2.3 flows

**Instrumentation surfaces that WILL NOT be exercised** (legacy direct-`Log.d`):
- `KeyPressLogger.kt:10, 16` (`DevKeyPress` tag)
- `KeyboardModeManager.kt:36` (`DevKeyMode` tag)
- `KeyMapGenerator.kt:27` (`DevKeyMap` tag)
- See `patterns/devkey-log-categories.md#Driver-server dual-source strategy`

## Concern: 2.2 Test driver server

**Goal**: External process receives HTTP logs + coordinates waves + replaces sleeps.

**Existing prior art**:
- `tools/debug-server/server.js` (full file, 85 lines) — HTTP receiver + query + buffer. Starting point for extension.
- `tools/e2e/e2e_runner.py` — Python test discovery + run loop. Alternate extension point.
- `tools/e2e/lib/adb.py:57-120` — `capture_logcat`, `assert_logcat_contains` (the sleep-based assertion pattern that Phase 2 must replace with server-side wait)

**Gaps** (what's missing — plan must add):
- No `GET /wait?...` endpoint on the debug server
- No ADB dispatch from the debug server
- No wave-gating logic
- No flow registry parser on the server side
- No visual-diff machinery
- No audio injection for voice flow

## Concern: 2.3 New test flows

### Voice round-trip
**Source anchors**:
- `KeyData.kt:148` — `const val KEYCODE_VOICE = -102`
- `LatinIME.kt:1302` — `KeyCodes.KEYCODE_VOICE -> { ... }` handler
- `LatinIME.kt:817` — `shouldShowVoiceButton` stub (returns `true` unconditionally — Phase 2 flow must handle this gracefully, since voice button appears on password fields incorrectly)
- `VoiceInputEngine.kt` — 16 `DevKeyLogger.voice` emit sites (see `ground-truth.md#VoiceInputEngine state transitions`)
- `.claude/test-flows/phase1-voice-verification.md` — existing manual checklist, source material for the automated flow

### Next-word prediction
**Source anchors**:
- `LatinIME.kt:1911-1930` — `getLastCommittedWordBeforeCursor` (returns the bigram lookup key)
- `LatinIME.kt:1933-1960` — `setNextSuggestions` (the method Phase 2.3 instruments)
- `Suggest.kt:376` — `getNextWordSuggestions` comment referencing `LatinIME.setNextSuggestions`
- `LatinIME.kt:905, 960, 1687, 1809, 1839, 1889, 2539` — callers of `setNextSuggestions`

**Instrumentation add** (tailor recommendation):
```kotlin
// LatinIME.kt line ~1953, before `return`:
DevKeyLogger.text(
    "next_word_suggestions",
    mapOf("prev_word_length" to prevWord.length, "result_count" to nextWords.size)
)
// And in the fallback branch ~line 1960:
DevKeyLogger.text("next_word_suggestions", mapOf("prev_word_length" to 0, "result_count" to 0))
```

### Long-press coverage
**Source anchors**:
- `KeyData.kt:37-58` — `longPressCode`, `longPressCodes` fields
- `KeyView.kt:193-323` — long-press pointer input + popup rendering
- `KeyPressLogger.kt:16` — `logLongPress` emit (`DevKeyPress LONG label=X code=N lpCode=M`)
- `QwertyLayout.kt`, `SymbolsLayout.kt` — the oracle: every layout data class carries its expected popup content

**Coverage enumeration** (plan author must build):
1. Walk `QwertyLayout.buildFullLayout()`, `buildCompactLayout()`, `buildCompactDevLayout()` → collect every KeyData
2. For each KeyData with `longPressCode != null` OR `longPressCodes != null`, emit an assertion: tap-and-hold → expect `DevKeyPress LONG lpCode=<expected>` in logcat
3. For multi-char popups, also screenshot the popup and compare against `.claude/test-flows/swiftkey-reference/<mode>-<theme>-popups/<key>.png`

### SwiftKey visual diff
**Source anchors** (nothing in app/ yet — this is a new test):
- `.claude/test-flows/swiftkey-reference/compact-dark.png` — the single captured reference (Session 42)
- `.claude/test-flows/swiftkey-reference/compact-dark-findings.md` — findings doc
- `.claude/test-flows/swiftkey-reference/README.md`, `.gitignore` — scaffold

**Missing references** (spec §4.4.1):
- Qwerty / Full / Symbols / Fn / Phone — dark
- All light modes (deferred per user)
- All long-press popups

**Plan author decision**: visual diff flow must handle the case where the reference image is missing (SKIP with warning, not FAIL).

## Concern: 2.4 Tier stabilization

**Source anchors**:
- `LatinIME.kt:403-422` — DUMP_KEY_MAP broadcast pattern (to mirror for `SET_LAYOUT_MODE`)
- `SettingsRepository.KEY_LAYOUT_MODE` — preference key, values `"full"` / `"compact"` / `"compact_dev"`
- `KeyMapGenerator.kt` — both precise and calculated coordinate paths
- `.claude/logs/key-coordinates.md` — current FULL-only coordinate reference
- `.claude/test-flows/registry.md:146-157` — `layout-modes` flow with "COMPACT/COMPACT_DEV not yet automated" note
- `tools/e2e/lib/keyboard.py:45` — broken regex (Phase 2.4 bug fix)

**Layout mode preference switching**: currently has no programmatic path from a test. Plan options in `patterns/debug-broadcast-receiver.md#Phase 2 new receivers`.

## Concern: 2.5 Coverage verification

**Goal**: Every Phase 1 feature has ≥1 automated flow.

**Phase 1 features** (from spec §2.1 + §6 Phase 1 items):
1. Voice input end-to-end
2. SwiftKey layout parity (visual + structural + behavioral)
3. Long-press popups (every key, every mode)
4. Predictive next-word after space
5. Clipboard panel
6. Macro system
7. Command mode
8. Plugin system
9. Qwerty / Compact / Full mode switching
10. Fn + Symbols variants

**Current flow coverage** (from `.claude/test-flows/registry.md`):
| Feature | Covered by |
|---|---|
| Basic typing | `typing` |
| Modifier states | `modifier-states`, `caps-lock`, `modifier-combos` |
| Mode switching | `mode-switching` |
| Layout modes (FULL only) | `layout-modes` |
| Rapid input | `rapid-stress` |
| Voice | **GAP** — added by 2.3 |
| Next-word prediction | **GAP** — added by 2.3 |
| Long-press coverage | **GAP** — added by 2.3 |
| SwiftKey visual parity | **GAP** — added by 2.3 |
| Clipboard / macro / command / plugin | **GAP** — manual smoke only, not automated |
| COMPACT / COMPACT_DEV modes | **GAP** — spec 2.4 |
| Fn / Symbols variants | **GAP** — partial via `mode-switching`, not comprehensive |

**Plan decision**: Which gaps become new automated flows vs. accepted manual smoke? The spec §2.1 existing-features clause accepts "manual smoke + existing `/test` tiers" for clipboard/macro/command/plugin, so those may stay manual. Every other gap should get at least one flow in Phase 2.3.
