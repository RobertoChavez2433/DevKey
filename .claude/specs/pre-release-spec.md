# DevKey v1.0 Pre-Release Vision & Scope

**Date**: 2026-04-08
**Session**: 39
**Status**: APPROVED — ready for `/writing-plans`
**Inputs**: `.claude/archive/2026-04-08-pre-release/research/architecture-audit.md`, GitHub issue #9
**Brainstorming Q&A**: Session 39 (this file is the validated output)

---

## 1. Vision

DevKey v1.0 is a release-ready Android keyboard that combines Hacker's Keyboard power-user features with SwiftKey-grade UX polish, plus a working on-device voice feature. Release is gated on four things **in strict sequential order**:

1. Features complete
2. Layout fidelity verified
3. Full regression suite green (with HTTP debug + driver server in the loop)
4. Entire codebase under the 400-line architecture rule

No phase starts before the previous one is fully green. The 400-line refactor is explicitly the **last** gate before release, not post-release hardening.

---

## 2. Release Criteria

All four criteria must be satisfied to ship.

### 2.1 Feature Completeness

- [ ] Voice input end-to-end: record → Whisper TF Lite transcribe → commit to `InputConnection`
- [ ] SwiftKey layout parity: **visual + structural + behavioral** (see §4)
- [ ] Every key has its correct long-press popup (Qwerty, Compact, Full, Symbols, Fn, Phone modes)
- [ ] Predictive next-word after space works (bigram wiring completed)
- [ ] All existing features verified functional (no regressions):
  - Clipboard panel (`ClipboardPanel.kt`)
  - Macro system (`MacroManagerScreen.kt`)
  - Command mode (`CommandAppManagerScreen.kt`)
  - Plugin system (`PluginManager.kt`) — see §2.4 security note
- [ ] All three keyboard modes work: Qwerty / Compact / Full (plus Fn + Symbols variants)

### 2.2 Regression Bar (Tier B + HTTP debug/driver server)

- [ ] `/test` all three tiers green on emulator: FULL, COMPACT, COMPACT_DEV
- [ ] **HTTP debug server integrated into the test loop** — `DevKeyLogger.enableServer(url)` is enabled during test runs so logcat + structured log forwarding feeds the external test harness. Required for reliable state observation and failure diagnosis.
- [ ] **Test driver server** running alongside the emulator — receives HTTP-forwarded logs, coordinates test waves, gates wave progression on observed state rather than sleeps. Replaces any remaining polling/sleep-based synchronization in the test harness.
- [ ] New test flows authored for v1.0 features:
  - Voice round-trip (tap mic → speak → verify committed text)
  - Predictive next-word (type word → space → verify candidate strip populated with next-word suggestions)
  - Long-press popup coverage (every key in every mode → verify popup content matches SwiftKey reference)
  - SwiftKey visual diff (screenshot comparison against captured SwiftKey reference screenshots)
- [ ] No red tests at release. Flaky tests must be stabilized, not suppressed.

### 2.3 Architecture Rule (the final gate)

- [ ] `find app/src/main/java -name "*.kt" -exec wc -l {} \; | awk '$1 > 400'` returns empty
- [ ] No `@Composable` function has cyclomatic complexity > 15
- [ ] No function has nesting depth > 4
- [ ] GH issue #9 closed (umbrella refactor issue)
- [ ] Post-refactor regression bar (§2.2) re-run and still green

### 2.4 Quality Gates

- [ ] Clean `./gradlew lint`
- [ ] No open defects labeled `priority:critical` or `priority:high` except #9 itself (which closes as part of §2.3)
- [ ] **Plugin system security**: GH #4 (plugins load untrusted packages without signature verification) must be resolved. Since plugins are in-scope for v1.0, this cannot be deferred. Either (a) add signature verification, or (b) gate plugin loading behind a debug-only flag for v1.0.

---

## 3. Non-Goals (v1.0)

Explicit scope fence — these are **NOT in v1.0**. Moving items across this fence requires an updated spec.

| Non-goal | Rationale |
|---|---|
| **Swipe-to-type (Flow gesture)** | 2-3 week feature on its own; headline v1.1 candidate |
| **Multi-language voice recognition** | English only (whisper-tiny.en). Other languages post-release |
| **Custom themes / theme marketplace / user-uploaded themes** | Material You theming is sufficient for v1.0 |
| **Cursor-swipe on spacebar** | Deferred with Flow |
| **Delete-swipe / emoji swipe gestures** | Deferred; long-press popups cover emoji access |

In-scope but polish-only (no new features, just verify they work and don't regress): clipboard panel, macro system, command mode, plugin system.

---

## 4. SwiftKey Parity Scope

Target: **full parity — visual + structural + behavioral.**

### 4.1 Visual parity
- Exact key sizes, spacing, corner radii, row heights, candidate strip styling
- Font weights and sizes matching SwiftKey
- Colors matching SwiftKey's Material-aligned palette (~~light + dark~~ **dark only for v1.0** — see §4.4.1 light-theme deferral)
- Shape of modifier keys (shift, backspace, enter, space) visually matching
- **Critical correction (2026-04-08, from `compact-dark.png`):** SwiftKey COMPACT dark renders ALL utility/modifier keys (shift, backspace, 123, enter, emoji, mic, comma, period) with the **same fill color** as letter keys — there is no distinct "special" background. DevKey's existing `keyBgSpecial` token giving modifier keys a darker shade does NOT match the reference and is retuned in Phase 6.
- **Ground truth**: captured SwiftKey reference screenshots (see §4.4)

### 4.2 Structural parity
- Same key arrangement (letters, punctuation, symbols, emoji access)
- Same long-press popup content on every key
- Same candidate strip layout (number of slots, visual emphasis of top pick)
- Same action-key behavior (period long-press, comma/emoji swap, etc.)
- Same symbol page layout

### 4.3 Behavioral parity (tightly scoped)
Only one behavioral parity feature for v1.0: **predictive next-word after space**. After a word is committed followed by a space, the candidate strip auto-populates with the most likely next words based on the bigram dictionary. The existing `ExpandableDictionary` bigram path is the foundation; the UX wiring in `LatinIME.onKey` / `setNextSuggestions` / `CandidateView` is what's missing.

Other behavioral aspects (auto-correct aggressiveness, suggestion timing, long-press thresholds) should be tuned to feel similar during manual verification but are not under formal test.

### 4.4 Ground truth strategy
- Capture SwiftKey reference screenshots on the same emulator image used for DevKey testing, at the same DPI/screen size, for every mode (Qwerty / Compact / Full / Symbols / Fn / Phone) in both light and dark theme
- Store under `.claude/test-flows/swiftkey-reference/` — these are the visual diff oracle for §2.2 screenshot comparison tests
- Capture every long-press popup from SwiftKey as well for §2.2 long-press coverage tests

#### 4.4.1 Captured references (2026-04-08)

| File | Mode | Theme | Status | Findings |
|---|---|---|---|---|
| `.claude/test-flows/swiftkey-reference/compact-dark.png` | COMPACT | dark | Captured (local-only, PNG not committed per §8) | [`.claude/test-flows/swiftkey-reference/compact-dark-findings.md`](../test-flows/swiftkey-reference/compact-dark-findings.md) |

**Light theme deferral (user decision, 2026-04-08):** Per the primary user, light theme is not used. §4.1 light-palette parity is DEFERRED to post-v1.0. Phase 6 theme retuning tunes dark-only from the captured reference; light palette is left as-is pending a future light-mode capture. This is a scoped variance from §4.1 — the light palette still exists as scaffolding but is not reference-matched for v1.0.

**Remaining capture gaps (tracked for post-v1.0):**
- Qwerty / Full / Symbols / Fn / Phone modes — dark
- All modes — light (deferred per above)
- Long-press popups for every key (spec §2.2 test scope)

**Observed long-press reference data (from `compact-dark.png`)** supersedes the Phase 5.2 digit-default templates for COMPACT mode. Per the Phase 1 plan rule "reference always wins over template", `QwertyLayout.buildCompactLayout()` letter-row long-press is retuned to match the SwiftKey-observed shift-symbol set (`q→% w→^ e→~ r→| t→[ y→] u→< i→> o→{ p→}` etc. — see findings doc).

---

## 5. Voice Feature Implementation

### 5.1 Current state
- `VoiceInputEngine.kt` (307 lines, under 400-rule) is code-complete: TF Lite `Interpreter`, audio capture via `AudioRecord` at 16 kHz mono PCM16, graceful fallback when model files absent, `VoiceState` state machine, coroutine-scoped lifecycle
- `VoiceInputSettingsScreen.kt` exists
- **Blocker**: `app/src/main/assets/` directory does not exist. Neither `whisper-tiny.en.tflite` nor `filters_vocab_en.bin` is present

### 5.2 Sourcing plan (prerequisite work)
- [ ] Source `whisper-tiny.en.tflite` — options: HuggingFace Whisper → TF Lite conversion, or pre-converted mirror from a reputable source. Verify SHA256 against upstream.
- [ ] Source / generate `filters_vocab_en.bin` — 80-bin mel filterbank + vocab in the format `VoiceInputEngine` expects. Verify compatibility with the sourced `.tflite`.
- [ ] Create `app/src/main/assets/` directory and commit both files
- [ ] Verify APK size impact (~40 MB expected). If unacceptable, fall back to Q4 option B (on-demand download)
- [ ] License audit: confirm the model files' license is compatible with the DevKey distribution license

### 5.3 Integration verification
- [ ] Voice button visible when `shouldShowVoiceButton(attribute)` returns true
- [ ] Tap → recording state → audio captured for N seconds or until silence detected
- [ ] Whisper interpreter produces transcription
- [ ] Transcribed text committed via `InputConnection.commitText`
- [ ] State machine returns to `IDLE` after commit or error
- [ ] Permission flow: first tap requests `RECORD_AUDIO`, graceful deny handling
- [ ] Error paths verified: model missing → disabled button; permission denied → toast; audio error → `ERROR` state → recovery

### 5.4 Voice test flow
New `/test` flow: tap mic → inject a known audio sample via `adb shell media` or emulator audio input → verify expected text committed in the target EditText. Uses HTTP debug server to observe `VoiceInputEngine` state transitions for deterministic assertions.

---

## 6. Phased Roadmap

**Strict ordering**. Each phase must be fully green before the next begins.

### Phase 1 — Feature Completion
1.1 **Voice model sourcing** (§5.2)
1.2 **SwiftKey reference capture** (§4.4) — screenshots for every mode + long-press popups, both themes
1.3 **Layout fidelity pass** — Qwerty / Compact / Full / Symbols / Fn / Phone in both themes, matched against reference screenshots. Iterate key sizes, colors, spacing, fonts until visual diff is within tolerance.
1.4 **Long-press popup completeness** — every key in every mode has correct popup content matching SwiftKey reference
1.5 **Predictive next-word wiring** — complete bigram → candidate strip path through `setNextSuggestions` / `CandidateView`
1.6 **Voice end-to-end verification** — §5.3 checklist
1.7 **Existing feature regression pass** — clipboard, macros, command mode, plugins. Manual smoke + existing `/test` tiers
1.8 **GH #4 resolution** — either plugin signature verification or debug-flag gating

### Phase 2 — Regression Infrastructure
2.1 **HTTP debug server integration** — `DevKeyLogger.enableServer(url)` wired into `/test` harness; enabled for every test run
2.2 **Test driver server** — external process receiving HTTP-forwarded logs, coordinating waves, replacing sleep-based synchronization
2.3 **New test flows authored**: voice round-trip, next-word prediction, long-press coverage, SwiftKey visual diff
2.4 **Existing tier stabilization** — FULL + COMPACT + COMPACT_DEV to 100% green on emulator
2.5 **Coverage verification** — every feature in Phase 1 has at least one automated test flow

### Phase 3 — Regression Gate
3.1 Full `/test` run on all three tiers — must be green
3.2 Voice round-trip flow green
3.3 Next-word prediction flow green
3.4 Long-press coverage flow green for all modes and themes
3.5 SwiftKey visual diff within tolerance
3.6 Manual smoke on emulator across all features
3.7 Lint clean
3.8 **Decision gate**: if any red, return to Phase 1. Do not proceed to Phase 4 with known regressions.

### Phase 4 — Architecture Refactor (the final gate)
Per GH #9 and `.claude/archive/2026-04-08-pre-release/research/architecture-audit.md`. Execute in the ordering from the audit report:

- 4.A **Compliance quick wins** — KeyEventSender, CandidateView (verify use first, delete if unused), Suggest, KeyboardSwitcher
- 4.B **Medium complexity** — ExpandableDictionary, Keyboard, LatinKeyboard, ComposeSequence (move data to resource file)
- 4.C **LatinIME decomposition** — 8-collaborator extraction (dedicated `/writing-plans` effort): SwipeActionHandler → FeedbackManager → DictionaryWriter → PunctuationHeuristics → PreferenceObserver → SuggestionCoordinator → InputDispatcher → ShiftStateMachine → ImeLifecycleController
- 4.D **Compose complexity hotspots** — split `KeyView` into `KeyBackground` / `KeyGlyph` / `KeyPopupPreview` / `KeyPressDetector`; split `DevKeyKeyboard` into `KeyboardGrid` / `ComposeSequenceHost` / `KeyboardScaffold`

After Phase 4: **re-run Phase 3 gate in full**. Any regression = stop, fix, re-run.

### Phase 5 — Release
5.1 Final verification: all four release criteria (§2) satisfied
5.2 Version bump, changelog
5.3 Build release APK (ProGuard/R8)
5.4 Release to chosen distribution channels (Play Store / F-Droid — decide during Phase 4)

---

## 7. Explicit Sequencing Rule

**Features first. Tests second. Refactor last. Release after all three.**

This ordering is deliberate and non-negotiable. Attempting to refactor while features are incomplete risks breaking half-built code. Attempting to release before refactor violates the firm 400-line rule. Running features + refactor in parallel creates merge hell on LatinIME (which has the highest churn in the project).

The user's own words from Session 39 Q1:
> "Implement the features first and ensure they're fully functioning, the layout is correct, all keys have their respected long press keys, all keyboard functions work, and then finally the last and final refactor of everything under our code quality standards"

---

## 8. Risk Register

| Risk | Mitigation |
|---|---|
| Whisper model sourcing blocked by license or format issues | Early spike in Phase 1.1 before committing to bundle strategy; fallback is on-demand download (Q4 option B) |
| SwiftKey visual parity is legally ambiguous | Parity is for feel and usability, not a pixel-perfect clone of copyrighted design assets; capture reference screenshots for internal comparison only, do not redistribute them |
| Refactor phase introduces regressions not caught by Phase 3 tests | Phase 4 ends with a mandatory Phase 3 re-run; gate release on second-pass green |
| LatinIME refactor is too large for one `/writing-plans` cycle | Audit report outlines 8-collaborator extraction order; each extraction is its own `/writing-plans` + `/implement` cycle |
| Plugin system (GH #4) security fix scope creeps Phase 1 | Gate plugins behind a debug flag as the minimal fix; full signature verification is v1.1 |
| HTTP debug + driver server integration introduces test flakiness | Staged rollout: integrate for one test tier first, stabilize, then roll out |

---

## 9. Open Questions (resolve during Phase 1)

- Which distribution channel first — Play Store, F-Droid, or both simultaneously?
- Target minimum device: is API 26 (current min) still right, or raise to API 28/29 to simplify Compose lifecycle?
- Screenshot visual diff tolerance: pixel-exact, or similarity threshold (e.g., SSIM > 0.95)?
- Voice model: bundle `whisper-tiny.en` only, or also include the standard `whisper-tiny` (multilingual) for opportunistic use? (If yes, APK grows by another ~40 MB — probably no for v1.0.)

---

## 10. References

- Audit report: `.claude/archive/2026-04-08-pre-release/research/architecture-audit.md`
- GH issue #9: https://github.com/RobertoChavez2433/DevKey/issues/9 (umbrella refactor)
- GH issue #8: https://github.com/RobertoChavez2433/DevKey/issues/8 (LatinIME god class — superseded by #9 Phase C)
- GH issue #4: https://github.com/RobertoChavez2433/DevKey/issues/4 (plugin security — must resolve in Phase 1.8)
- `VoiceInputEngine.kt`: `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt`
- `DevKeyLogger.kt`: `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt` (HTTP debug client)
- `/test` skill: `.claude/skills/test/`
- Key coordinates: `.claude/docs/reference/key-coordinates.md`
- Rules: `.claude/rules/ime-lifecycle.md`, `compose-keyboard.md`, `jni-bridge.md`, `build-config.md`

---

## 11. Next Step

Run `/writing-plans` against this spec to produce a phased implementation plan. The plan should split each of the five phases into concrete, dependency-ordered tasks dispatchable via `/implement`.

---

## 12. Session 49 Audit (2026-04-11) — Current State vs. Spec

Full codebase audit performed against all spec criteria. Wave 8 DI seam refactor
completed in Session 48 (ImeState, InputConnectionProvider, CandidateViewHost
extracted; all collaborators decoupled from LatinIME).

### 12.1 §2.1 Feature Completeness — Status

| # | Criterion | Status | Finding |
|---|---|---|---|
| 1 | Voice input end-to-end | **PARTIAL** | Assets present, wiring complete. `WhisperProcessor.computeMelSpectrogram()` is a **stub** (returns zeros). Binary header parsing reads 2 of 4 fields (off-by-one). Model runs but produces garbage. |
| 2 | SwiftKey layout parity | **PARTIAL** | COMPACT long-press retuned. `keyBgSpecial` token still mismatches SwiftKey (modifier keys darker than letter keys). 10/12 reference screenshots missing. Light theme deferred per §4.4.1. |
| 3 | Long-press popup correctness | **DONE** | All letter/symbol keys have `longPressCode`/`longPressCodes`. Expectation JSONs complete for compact, compact_dev, full, symbols. |
| 4 | Predictive next-word | **PARTIAL** | `setNextSuggestions()` exists and queries bigrams. Non-composing space path doesn't trigger it. Compose `SuggestionBar` and legacy `CandidateView` are separate pipelines — not in sync. |
| 5 | Existing features functional | **DONE** | Clipboard, macros, command mode, plugin manager all complete. Plugins release-gated per §2.4. |
| 6 | All keyboard modes | **DONE** | COMPACT, COMPACT_DEV, FULL, Symbols all present in Compose. Fn and symbols-shift exist in legacy XML (`kbd_full_fn.xml`, `kbd_compact_fn.xml`, `kbd_symbols_shift.xml`) and are wired through `KeyboardSwitcher.setFn()` / `toggleShift()`. |

### 12.2 §2.2 Regression Bar — Status

| # | Criterion | Status | Finding |
|---|---|---|---|
| 1 | Three test tiers green | **NOT DONE** | Latest run: 6 pass / 10 fail / 14 error / 6 skip. Stabilization DEFERRED. |
| 2 | HTTP debug server in test loop | **DONE** | `DevKeyLogger.enableServer`, circuit breaker, auto-broadcast in `e2e_runner.py`. |
| 3 | Test driver server | **DONE** | `tools/debug-server/server.js` with wave gate, ADB proxy, long-poll. |
| 4 | New v1.0 test flows | **PARTIAL** | All 4 flows defined + Python test modules exist. All 4 are failing: voice key not in map, driver timeouts, SSIM=0.44 (threshold 0.92). |
| 5 | No red tests | **NOT DONE** | 10 failures + 14 errors in latest run. |

### 12.3 §2.3 Architecture Rule — Status

| # | Criterion | Status | Finding |
|---|---|---|---|
| 1 | No .kt file > 400 lines | **NOT DONE** | `LatinIME.kt` at **623 lines** — sole violator. All other files under 400. |
| 2 | No @Composable cyclo > 15 | **PARTIAL** | KeyView split done (197 lines). No automated enforcement. |
| 3 | No function nesting > 4 | **PARTIAL** | LatinIME has depth > 4 in several methods. No automated enforcement. |
| 4 | GH #9 closed | **NOT DONE** | Still open. |

### 12.4 §2.4 Quality Gates — Status

| # | Criterion | Status | Finding |
|---|---|---|---|
| 1 | Clean lint | **NOT DONE** | 747 errors: 45 locale files use wrong XML namespace (`apk/res/dev.devkey.keyboard` instead of removing it); `POST_NOTIFICATIONS` permission missing in manifest. 568 warnings. |
| 2 | GH #4 plugin security | **DONE** | `BuildConfig.DEBUG` compile-time gate at two call sites. |

### 12.5 §5 Voice — Status

| # | Criterion | Status | Finding |
|---|---|---|---|
| 1 | Model assets | **DONE** | `whisper-tiny.en.tflite` (41.5 MB) + `filters_vocab_en.bin` (586 KB) present. |
| 2 | VoiceInputEngine | **DONE** | 197 lines, code-complete. |
| 3 | WhisperProcessor | **NOT DONE** | `computeMelSpectrogram()` stub + header parsing bug. |
| 4 | Voice button wired | **DONE** | ToolbarRow mic button, VoiceInputPanel, wired in DevKeyKeyboard. |
| 5 | RECORD_AUDIO permission | **DONE** | Manifest + PermissionActivity. |

### 12.6 §4.D Compose Hotspots — Status

| Spec Component | Status | Actual File |
|---|---|---|
| `KeyBackground` | DONE | `ui/keyboard/KeyBackground.kt` (81 lines) |
| `KeyGlyph` | DONE | `ui/keyboard/KeyGlyph.kt` (145 lines) |
| `KeyPopupPreview` | DONE | `ui/keyboard/KeyPopupPreview.kt` (83 lines) |
| `KeyPressDetector` | NAME MISMATCH | `ui/keyboard/KeyGestureHandler.kt` (140 lines) |
| `KeyboardGrid` | NAME MISMATCH | `ui/keyboard/KeyboardRenderLayer.kt` |
| `ComposeSequenceHost` | N/A | Compose key processing is IME-level (`core/ComposeKeyProcessor.kt`) |
| `KeyboardScaffold` | NAME MISMATCH | `ui/keyboard/DevKeyKeyboard.kt` (168 lines) |

### 12.7 Phase 4 Architecture — Wave 8 DI Seam Refactor — COMPLETE

Completed in Sessions 48–49:
- `ImeState` extracted (168 lines) — plain data holder, no Android framework deps
- `InputConnectionProvider` interface (15 lines) — narrow IC access
- `CandidateViewHost` interface (13 lines) — candidate view + service bridge
- `ComposeKeyProcessor` (18 lines) — compose/dead-key processing
- `OptionsMenuHandler` (96 lines) — options dialog extracted from LatinIME
- All 12 collaborators take `ImeState` + interfaces + lambdas, not `LatinIME`
- `ImeInitializer` is the sole documented exception (one-shot factory needing IMS)
- 0 collaborators in `core/` import `LatinIME` (verified by grep)
- Business logic removed from ImeState: `processMultiKey` → `ComposeKeyProcessor`,
  `saveWordInHistory` → `SuggestionCoordinator`, `isCursorTouchingWord` /
  `sameAsTextBeforeCursor` → `EditingUtil`

### 12.8 Remaining Blockers — Prioritized

1. **LatinIME.kt under 400 lines** — extract CandidateViewManager, onKeyUp body,
   Phase 2-4 onCreate wiring, updateKeyboardOptions, dump body, companion
   wrappers (~206 lines removable → ~417, needs ~20 more from wrapper collapse)
2. **WhisperProcessor functional** — implement mel spectrogram + fix header parsing
3. **Lint clean** — fix 45 locale XML namespaces + POST_NOTIFICATIONS permission
4. **Next-word prediction** — fix non-composing space path + unify dual pipeline
5. **Test suite green** — depends on above fixes
6. **GH #9 closed** — after architecture gate passes
