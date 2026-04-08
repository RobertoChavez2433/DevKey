# Security Review — Cycle 1
**Verdict:** REJECT

## MEDIUM findings
1. **Plugin gate uses runtime FLAG_DEBUGGABLE instead of compile-time BuildConfig.DEBUG.** A re-signed APK with `android:debuggable="true"` re-opens GH #4. Fix: enable `buildFeatures.buildConfig = true` in `app/build.gradle.kts` (one-line) and use `BuildConfig.DEBUG` inside `arePluginsEnabled`. OR document the trade-off explicitly in the plan header AND add a CI gate that greps for `debuggable="true"` in the built manifest. Option (a) is strongly preferred — trivial scope.
2. **SwiftKey reference .gitignore ships with `*.png` commented OUT.** Spec §8 says "do NOT redistribute." Default state must EXCLUDE screenshots. Fix: ship `.gitignore` with active `*.png` exclusion + `!README.md` unignore.
3. **DevKeyLogger.voice instrumentation lacks explicit privacy policy.** `DevKeyLogger.sendToServer` has no BuildConfig.DEBUG gate — HTTP forwarding is runtime-enabled. Adding voice log sites without an explicit "never log transcript/audio content" comment invites future drift. Fix:
   - Add explicit policy comment at the Phase 7.1 instrumentation block: "DevKeyLogger.voice() payloads MUST NOT contain transcript text, audio buffers, or any InputConnection.getTextBeforeCursor/getTextAfterCursor content."
   - Phase 7.2 checklist: `processing_complete` event must carry only `result_length` / `duration_ms`, never the transcript.

## LOW findings
4. `PluginManager.getSoftKeyboardDictionaries` / `getLegacyDictionaries` should be marked `private` (defense in depth — no external callers).
5. Phase 7.2 checklist should add: "Voice button is NOT visible on TYPE_TEXT_VARIATION_PASSWORD / VISIBLE_PASSWORD / WEB_PASSWORD fields." (Pre-existing defect in `shouldShowVoiceButton` returning true unconditionally — not a plan bug but verification should surface it.)

## Clean
- `getLastCommittedWordBeforeCursor` gated by `isPredictionOn()` (false on password/URI/email) — privacy-clean
- Voice asset provenance + SHA256 scaffolding adequate
- Plugin `mPluginDicts.clear()` on gated path defense-in-depth sound
- No new Intents / PackageManager queries / IPC / ClassLoader / permissions introduced
