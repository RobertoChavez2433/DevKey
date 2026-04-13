# Pattern: Integration Test Approach

## Problem

The spec proposes 6 integration test files (`integration/*PipelineTest.kt`) but:
- No `androidTest` source set should be used (`connectedAndroidTest` is banned)
- Integration tests need realistic wiring but not a running Android device

## Approach: Robolectric in the Unit Test Source Set

Place integration tests alongside unit tests in `app/src/test/`:
```
app/src/test/java/dev/devkey/keyboard/integration/
  PredictionPipelineTest.kt
  AutocorrectPipelineTest.kt
  VoicePipelineTest.kt
  ModifierPipelineTest.kt
  PunctuationPipelineTest.kt
```

Use Robolectric for Android framework classes (`InputConnection`, `EditorInfo`,
`Context`). The project already has Robolectric in test dependencies.

## Key Principle

Integration tests wire real production classes together but mock only the
Android boundary (InputConnection, AudioRecord). They test the pipeline, not
individual methods.

## What NOT to do

- Do not create a separate `androidTest` source set
- Do not use `connectedAndroidTest`
- Do not add test-only methods to production classes
- Do not mock internal production classes — only framework boundaries
