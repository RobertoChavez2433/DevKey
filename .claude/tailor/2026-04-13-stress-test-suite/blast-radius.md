# Blast Radius — Stress Test Suite

## What This Spec Changes

This spec is **test-only** — no production code changes are required except
possible visibility promotions (`private` -> `internal`) for 5 methods.

### New Files (estimated)

| Category | Count | Location |
|----------|-------|----------|
| Unit test files | ~12 new | `app/src/test/java/dev/devkey/keyboard/` |
| Integration test files | ~6 new | `app/src/test/java/dev/devkey/keyboard/integration/` |
| E2E test files | ~30 new | `tools/e2e/tests/<feature>/` |
| Test utilities | 3 new | `app/src/test/.../testutil/` |
| E2E runner changes | 1 modified | `tools/e2e/e2e_runner.py` |

### Production Files Potentially Touched

Only if visibility changes are approved:

| File | Change | Risk |
|------|--------|------|
| `feature/prediction/TrieDictionary.kt` | `editDistance`: private -> internal | Low |
| `feature/voice/WhisperProcessor.kt` | `computeMelSpectrogram`: private -> internal | Low |
| `feature/voice/VoiceInputEngine.kt` | `runInference`, `readWavPcm`: private -> internal | Low |
| `core/ModifierHandler.kt` | `nextShiftState`: private -> internal | Low |

All other production files remain untouched.

### E2E Runner Blast Radius

Changing `e2e_runner.py` to support subdirectory discovery affects all 20
existing test files. The runner must remain backward-compatible with flat
files during the migration.

### Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Breaking existing 20 E2E tests | Medium | Runner must support both flat and subdirectory layouts |
| Flaky stress tests | Medium | Use harness `wait_for()`, no blind waits |
| Privacy leaks in assertions | High | Assert counts/lengths only, never typed content |
| Integration tests needing Android context | Medium | Use Robolectric, not connectedAndroidTest |
| Visibility changes leaking API surface | Low | Use `@VisibleForTesting` + `internal` |
