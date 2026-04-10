---
paths:
  - "**/pckeyboard/**"
---

# JNI Bridge Rules

## JNI-Bound Class

- The JNI-bound class is
  `app/src/main/java/org/pocketworkstation/pckeyboard/BinaryDictionary.java`.
- Do not rename it, move it, convert it to Kotlin, or change its package.
- The C++ side binds to the literal JNI path
  `org/pocketworkstation/pckeyboard/BinaryDictionary`.

## Kotlin Wrapper Boundary

- `app/src/main/java/dev/devkey/keyboard/BinaryDictionary.kt` is the editable
  Kotlin wrapper.
- It must keep delegating native calls through the Java bridge.
- Do not replace that delegation with new `external fun` declarations in the
  Kotlin class.

## Legacy Package Discipline

- Treat `org.pocketworkstation.pckeyboard/**` as legacy bridge territory.
- Do not add new features or general refactors there.
- New work belongs under `dev.devkey.keyboard/**`.
