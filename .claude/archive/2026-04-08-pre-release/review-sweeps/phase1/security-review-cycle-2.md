# Security Review — Cycle 2
**Verdict:** APPROVE

## Cycle-1 findings verification
1. **BuildConfig.DEBUG plugin gate** — FIXED. Sub-phase 3.0 enables `buildFeatures.buildConfig = true`; 3.1 uses `private fun arePluginsEnabled(): Boolean = BuildConfig.DEBUG`. Compile-time gate — re-signed debuggable APK cannot reopen GH #4.
2. **SwiftKey `.gitignore` active exclusions** — FIXED. Ships with `*.png`/`*.jpg`/`*.jpeg`/`*.webp` active + `!README.md` unignore. Fails-closed per spec §8.
3. **DevKeyLogger.voice privacy policy callout** — FIXED. Load-bearing block in Phase 7.1 names that `DevKeyLogger.sendToServer` lacks BuildConfig.DEBUG gate; forbids transcript text, audio buffers, raw bytes, InputConnection text content.
4. **processing_complete event** — FIXED. Phase 7.2 checklist now specifies `result_length` + `duration_ms` only; transcript content forbidden.
5. **Password-field voice button verification** — FIXED. Phase 7.2 checklist adds TYPE_TEXT_VARIATION_PASSWORD / VISIBLE_PASSWORD / WEB_PASSWORD assertion referencing pre-existing `shouldShowVoiceButton` defect at LatinIME.kt:817.
6. **PluginManager helpers private** — FIXED. Sub-phase 3.1 Step 6 marks `getSoftKeyboardDictionaries`/`getLegacyDictionaries` as `private fun`.

## New-content audit
- `buildFeatures.buildConfig = true` is compile-time only; no runtime surface
- Private helpers strictly reduce attack surface
- Privacy policy callout is a tightening
- No new Intents, permissions, IPC, ClassLoader, JNI surface, or network endpoints

## OWASP impact
- M2 (Supply Chain) — STRENGTHENED (GH #4 closed at compile-time)
- M6 (Privacy Controls) — STRENGTHENED (voice log policy)
- M9 (Data Storage) — STRENGTHENED (SwiftKey references fail-closed)

## Observations (non-blocking)
- `shouldShowVoiceButton` pre-existing defect correctly routed to manual verification + defect filing, not expanded into Phase 1 scope
- `DevKeyLogger.sendToServer` BuildConfig.DEBUG gate is an out-of-scope v1.1 hardening target; compensated here by the explicit privacy policy

All cycle-1 findings closed. No new concerns. Plan ready for `/implement`.
