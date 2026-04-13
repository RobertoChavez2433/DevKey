# Pattern: Private Method Testing Strategy

## Problem

The spec names 5 `private` methods as direct test targets. Kotlin `private`
methods cannot be called from test classes without reflection hacks.

## Options

### A. Promote to `internal` + `@VisibleForTesting` (Recommended)
```kotlin
@VisibleForTesting
internal fun editDistance(a: String, b: String): Int { ... }
```
- Pro: Clean, idiomatic, compiler-enforced boundary.
- Con: Minor API surface expansion within the module.

### B. Test via public entry points only
- `editDistance` -> tested indirectly via `getFuzzyMatches` thresholds.
- `computeMelSpectrogram` -> tested indirectly via `processAudio` output shape.
- `nextShiftState` -> tested indirectly via `onPress`/`onRelease` state cycles.
- Pro: Zero production changes.
- Con: Harder to isolate failures, more complex test setup.

### C. Extract to companion or utility (over-engineering)
- Not recommended for internal algorithms.

## Recommendation

Use Option A for `editDistance` (pure function, easy to test directly).
Use Option B for the other 4 (better tested through their public orchestrators).
