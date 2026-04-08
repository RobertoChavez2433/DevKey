# Phase 1.6 — Voice E2E Verification Checklist

**Spec**: `.claude/specs/2026-04-08-pre-release-vision-spec.md` §5.3
**Plan**: `.claude/plans/2026-04-08-pre-release-phase1.md` Phase 7

## Preconditions

- [ ] `app/src/main/assets/whisper-tiny.en.tflite` present and SHA256 recorded in `MODEL_PROVENANCE.md`
- [ ] `app/src/main/assets/filters_vocab_en.bin` present and SHA256 recorded
- [ ] `./gradlew installDebug` succeeds
- [ ] IME is selected and active on the target device

## Checklist (spec §5.3 items)

- [ ] Voice button visible when `shouldShowVoiceButton(attribute)` returns true (normal text field; not password, not URL field)
- [ ] Tap voice button → keyboard enters `KeyboardMode.Voice` → `VoiceState` transitions IDLE → LISTENING (check logcat for `DevKey/Voice state_transition LISTENING`)
- [ ] Audio captured for up to N seconds or until silence detected
- [ ] Whisper interpreter produces a transcription — check logcat for `DevKey/Voice processing_complete` with `{ result_length: N, duration_ms: M }` only. Transcript content MUST NOT appear in the log payload.
- [ ] Voice button is NOT visible on `TYPE_TEXT_VARIATION_PASSWORD` / `VISIBLE_PASSWORD` / `WEB_PASSWORD` fields. If it IS visible, file a defect (`shouldShowVoiceButton` at LatinIME.kt:817 currently returns true unconditionally — pre-existing defect, not introduced by this plan).
- [ ] Transcribed text is committed via `InputConnection.commitText` (visible in the target EditText)
- [ ] State machine returns to `IDLE` after commit
- [ ] State machine returns to `IDLE` after error (disconnect mic, tap voice, observe)
- [ ] Audio-error recovery: force an `AudioRecord` failure (e.g., revoke mic at OS level mid-record or simulate init failure), verify `DevKey/Voice state_transition ERROR` appears in logcat, then tap voice again and verify a fresh `state_transition LISTENING` is reachable (recovery from ERROR works — spec §5.3)
- [ ] First-time tap on voice button requests `RECORD_AUDIO` permission via `PermissionActivity`
- [ ] Permission denial → graceful handling (toast, button disabled, no crash)
- [ ] Model-missing path: temporarily rename `whisper-tiny.en.tflite`, reinstall, verify `VoiceInputEngine.initialize()` catches the exception, `modelLoaded = false` logged, voice button surfaces the limited state (or is hidden depending on `shouldShowVoiceButton`)
- [ ] After verification, restore asset file and reinstall

## Exit criteria

All checkboxes must be ticked for Phase 7 to be considered complete. Failures are filed as defects per `.claude/docs/defects.md` conventions.
