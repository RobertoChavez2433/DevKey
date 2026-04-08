# Tailor Manifest — DevKey Pre-Release Phase 1

**Spec**: `.claude/specs/2026-04-08-pre-release-vision-spec.md`
**Scope**: Phase 1 only (§6 Phase 1 in spec) — Feature Completion
**Slug**: `2026-04-08-pre-release-phase1`
**Tailor date**: 2026-04-08
**Repo key**: `local/Hackers_Keyboard_Fork-e04f25e5`
**Git HEAD at tailor**: `2391b5a8966659c100af817542b0a935e884ebe3`

---

## Scope Fence

This tailor covers ONLY Phase 1 of the umbrella vision spec:

| Phase 1 Step | Spec § | Covered here |
|---|---|---|
| 1.1 Voice model sourcing | §5.2 | Yes |
| 1.2 SwiftKey reference capture | §4.4 | Yes |
| 1.3 Layout fidelity pass | §4.1 | Yes |
| 1.4 Long-press popup completeness | §4.2 | Yes |
| 1.5 Predictive next-word wiring | §4.3 | Yes |
| 1.6 Voice E2E verification | §5.3 | Yes |
| 1.7 Existing feature regression | §2.1 | Yes (scope only) |
| 1.8 GH #4 plugin security | §2.4 | Yes |

Phases 2–5 (test infra / regression gate / architecture refactor / release) are **out of scope** for this tailor. Each gets its own tailor + plan cycle.

---

## Files Analyzed (Phase 1 scope)

### Voice (§5)
- `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt`
- `app/src/main/java/dev/devkey/keyboard/feature/voice/WhisperProcessor.kt`
- `app/src/main/java/dev/devkey/keyboard/feature/voice/SilenceDetector.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/voice/VoiceInputPanel.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/voice/PermissionActivity.kt`

### Prediction / next-word (§4.3)
- `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` (setNextSuggestions, handleSeparator, updateSuggestions)
- `app/src/main/java/dev/devkey/keyboard/Suggest.kt`
- `app/src/main/java/dev/devkey/keyboard/CandidateView.kt`
- `app/src/main/java/dev/devkey/keyboard/ExpandableDictionary.kt`
- `app/src/main/java/dev/devkey/keyboard/UserBigramDictionary.kt`
- `app/src/main/java/dev/devkey/keyboard/Dictionary.kt`
- `app/src/main/java/dev/devkey/keyboard/feature/prediction/PredictionEngine.kt`
- `app/src/main/java/dev/devkey/keyboard/feature/prediction/DictionaryProvider.kt`

### Layout fidelity + long-press (§4.1, §4.2)
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyView.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/QwertyLayout.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/SymbolsLayout.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/ComposeKeyboardViewFactory.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/theme/DevKeyTheme.kt`
- `app/src/main/java/dev/devkey/keyboard/core/KeyboardActionBridge.kt`
- `app/src/main/java/dev/devkey/keyboard/core/KeyboardActionListener.kt`

### Plugin security (§2.4 GH #4)
- `app/src/main/java/dev/devkey/keyboard/PluginManager.kt`
- `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt` (exemplar for debug-build check)

### Regression infra (used in Phase 1 verification)
- `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`

### Build / config
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`

---

## Phase 2 Completion (jcodemunch 9 steps)

| # | Step | Status | Notes |
|---|---|---|---|
| 1 | index_folder | ✅ | Incremental, 1 file changed, 2676 symbols, skipped local.properties/keystore/signing per security rules |
| 2 | get_file_outline | ✅ | All Phase 1 files outlined in batches |
| 3 | get_dependency_graph | ⚠️ KOTLIN LIMITATION | Returns empty edges for every Kotlin file — jcodemunch's Kotlin import resolver is broken for this repo. Fell back to `search_text` for call-site analysis. |
| 4 | get_blast_radius | ⚠️ KOTLIN LIMITATION | All symbols return `direct_dependents_count = 0`. Same root cause. |
| 5 | find_importers | ⚠️ KOTLIN LIMITATION | All files return `importer_count = 0`. Same root cause. |
| 6 | get_class_hierarchy | ✅ | Confirmed LatinIME extends InputMethodService + implements KeyboardActionListener / SharedPreferences.OnSharedPreferenceChangeListener / WordPromotionDelegate; ExpandableDictionary extends Dictionary. |
| 7 | find_dead_code | ⚠️ KOTLIN LIMITATION | Reports 362 Kotlin/resource files as "dead" — obviously false (IME works). Dead-code signal is unreliable for Kotlin on this MCP build. |
| 8 | search_symbols | ✅ | Used extensively for symbol discovery. |
| 9 | get_symbol_source | ✅ | Used for critical symbols: `setNextSuggestions`, `handleSeparator`, `getSuggestions`, `initialize` (voice), `getSoftKeyboardDictionaries`, `getLegacyDictionaries`, `enableServer`. |

**Fallback tools used**:
- `mcp__jcodemunch__search_text` (regex + context) for call-site mapping
- Direct `Read` of ground-truth files (DevKeyTheme.kt, KeyData.kt, AndroidManifest.xml, libs.versions.toml)
- `Bash ls` to verify `app/src/main/assets/` absence

---

## Patterns Discovered

See `patterns/` directory.

- [voice-engine-integration.md](patterns/voice-engine-integration.md) — how VoiceInputEngine is wired into DevKeyKeyboard
- [bigram-suggest-path.md](patterns/bigram-suggest-path.md) — how Suggest.getSuggestions + Dictionary.getBigrams already work
- [compose-keyboard-layout.md](patterns/compose-keyboard-layout.md) — QwertyLayout / KeyData / KeyView pattern
- [theme-token-usage.md](patterns/theme-token-usage.md) — DevKeyTheme object-of-constants pattern
- [plugin-loading-security.md](patterns/plugin-loading-security.md) — current PluginManager load path + debug-gate exemplar
- [devkey-logger-http-forwarding.md](patterns/devkey-logger-http-forwarding.md) — DevKeyLogger.enableServer pattern

---

## Ground Truth Discrepancies

See `ground-truth.md`.

Key findings:
1. ✅ **Spec claim confirmed**: `app/src/main/assets/` does NOT exist. Voice model files absent.
2. ✅ **Spec claim confirmed**: `setNextSuggestions` in `LatinIME.kt:1895-1897` is a trivial stub (shows punctuation only, no bigram wiring).
3. ⚠️ **Nuance**: `Suggest.getSuggestions(prevWordForBigram=...)` already has the full bigram read path (UserBigramDictionary, ContactsDictionary, MainDict via `getBigrams`). **However**, it gates bigram collection on `wordComposer.size() == 1` — i.e. first character already typed. For zero-char "next-word after space" prediction, the plan needs either a new entry-point on Suggest or a direct `Dictionary.getBigrams()` call from `handleSeparator`. This is not a blocker; it's a scope clarification.
4. ✅ **Spec claim confirmed**: `VoiceInputEngine` is fully integrated into `DevKeyKeyboard.kt` (lines 81, 84, 129-130, 157, 161, 241, 249). `bridge.onText(transcription)` is the commit path. Only the model asset is missing.
5. ✅ **KeyData already supports long-press**: `longPressLabel` + `longPressCode` fields exist (KeyData.kt:46-47). `KeyView` dispatches them (KeyView.kt:171, 213-215). `QwertyLayout` populates them for FULL mode letter row (lines 72-98). **Gap**: long-press data likely missing for other rows/modes and for symbol layouts. Phase 1.4 is mostly a data-fill task, not a new feature.
6. ✅ **PluginManager loads unsigned external code**: `getSoftKeyboardDictionaries` and `getLegacyDictionaries` use `queryBroadcastReceivers` / `queryIntentActivities` on unrestricted intents, then `getResourcesForApplication(appInfo)` to load raw bytes as dictionaries — no signature verification. GH #4 is real.
7. ✅ **Debug-build gate exemplar exists**: `KeyMapGenerator.isDebugBuild()` uses `applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE`. This is the minimal-fix pattern for GH #4 per spec §2.4(b).
8. ✅ **tflite 2.16.1** + **tflite-support 0.4.4** already in `gradle/libs.versions.toml` and wired into `app/build.gradle.kts`.
9. ✅ **KEYCODE_VOICE = -102** confirmed in `KeyData.kt:137`.
10. ✅ **RECORD_AUDIO permission** declared in AndroidManifest.xml:6.

---

## Open Questions for Plan Author

1. **Next-word entry point**: Add new `Suggest.getNextWordSuggestions(prevWord)` method, OR call `Dictionary.getBigrams()` directly from `LatinIME.setNextSuggestions()` / `handleSeparator()`? Recommendation in plan: new method on Suggest that runs the bigram path with `wordComposer.size() == 0` allowed.
2. **Whisper model provenance**: Spec §5.2 lists HuggingFace conversion OR pre-converted mirror. Plan must specify ONE source and capture SHA256.
3. **SwiftKey reference capture — legal constraints**: Spec §8 risk register says "do not redistribute". Plan should put references under `.claude/test-flows/swiftkey-reference/` with a README stating internal-use-only.
4. **Plugin gate mechanism**: `BuildConfig.DEBUG` constant OR `ApplicationInfo.FLAG_DEBUGGABLE` runtime flag? Existing `KeyMapGenerator.isDebugBuild` uses the runtime flag. Plan should reuse that helper.

---

## Gap-fill Agents Used

**0 / 3** — Not needed. jcodemunch search_symbols + search_text + get_symbol_source + direct Read produced sufficient signal.
