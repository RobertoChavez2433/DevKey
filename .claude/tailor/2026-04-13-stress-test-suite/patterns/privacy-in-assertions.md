# Pattern: Privacy-Safe Test Assertions

## Rule

From the spec constraints:
> Privacy: assertions check lengths/counts, never typed content

## Correct Assertions

```kotlin
// Unit test — check suggestion count, not content
assertThat(suggestions).hasSize(3)
assertThat(suggestions.first().second).isGreaterThan(0) // frequency

// Unit test — check state transitions, not text
assertThat(TextEntryState.getState()).isEqualTo(TextEntryState.State.IN_WORD)

// E2E — check event counts via logcat
assert_log_contains("suggestions_updated count=3")
assert_log_contains("prediction_triggered")
```

## Forbidden Assertions

```kotlin
// NEVER assert on typed content
assertThat(ic.committedText).isEqualTo("hello world")  // FORBIDDEN
assertThat(clipboardEntry.content).isEqualTo("secret")  // FORBIDDEN

// NEVER log typed text in test helpers
log("User typed: $composingWord")  // FORBIDDEN
```

## E2E Privacy Guard

E2E tests verify behavior via:
- DevKeyLogger structural events (key codes, state names, counts)
- UI element presence/absence
- Field length changes (not content)
