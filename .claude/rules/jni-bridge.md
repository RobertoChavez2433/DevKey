---
paths:
  - "**/pckeyboard/**"
---

# JNI Bridge Rules

## Two BinaryDictionary Files — Only One Is JNI-Bound

There are **two** `BinaryDictionary` files in this project. They have different
roles and must not be confused:

### 1. JNI Bridge (Java) — DO NOT RENAME OR MODIFY
`app/src/main/java/org/pocketworkstation/pckeyboard/BinaryDictionary.java`

This is the **only** file wired to the C++ native code. The C++ layer
(`app/src/main/cpp/org_pocketworkstation_pckeyboard_BinaryDictionary.cpp`)
registers its native methods against the class path
`"org/pocketworkstation/pckeyboard/BinaryDictionary"` via `JNI_OnLoad` /
`RegisterNatives`.

**Renaming or moving this file will silently break dictionary lookup at
runtime.** The JNI call will fail to resolve the class and all autocorrect
and suggestion features will stop working.

Constraints:
- The file stays at:
  `app/src/main/java/org/pocketworkstation/pckeyboard/BinaryDictionary.java`
- The package declaration must remain `org.pocketworkstation.pckeyboard`.
- The class name must remain `BinaryDictionary`.
- The file stays Java (NOT Kotlin) — the JNI signatures were generated for Java
  bytecode. Converting to Kotlin changes name mangling and breaks the native
  bridge.

### 2. Implementation Class (Kotlin) — Delegates to Java Bridge
`app/src/main/java/dev/devkey/keyboard/BinaryDictionary.kt`

This is the **active implementation** used by `Suggest.kt` and the rest of
the `dev.devkey.keyboard.*` package. It is **not** JNI-bound. Instead it
delegates every native call through the Java bridge:

```kotlin
import org.pocketworkstation.pckeyboard.BinaryDictionary as JniBridge
// ...
private fun openNative(...): Long = JniBridge.openNative(...)
```

The Kotlin class also triggers loading of the Java bridge class via:
```kotlin
Class.forName("org.pocketworkstation.pckeyboard.BinaryDictionary")
```

This Kotlin file is the normal Kotlin class that may be edited following
standard project conventions, as long as it continues to delegate native calls
to the Java bridge and does not attempt to declare its own `external fun`
methods.

## How We Verified

- `app/src/main/cpp/org_pocketworkstation_pckeyboard_BinaryDictionary.cpp:162`:
  `const char* const kClassPathName = "org/pocketworkstation/pckeyboard/BinaryDictionary";`
- `app/src/main/cpp/org_pocketworkstation_pckeyboard_BinaryDictionary.cpp:170`:
  `jint JNI_OnLoad(JavaVM* vm, void* reserved)` calls `registerNatives(env)` with
  the above class path — binding to the **Java** class only.
- `app/src/main/java/dev/devkey/keyboard/BinaryDictionary.kt:21`:
  `import org.pocketworkstation.pckeyboard.BinaryDictionary as JniBridge` —
  the Kotlin class has no `external fun` declarations of its own.

## C++ Package Path

The C++ side (`app/src/main/cpp/`) references the Java class by its
fully-qualified name as a string literal. If you ever need to search for where
it is referenced in native code, look for:
```
org/pocketworkstation/pckeyboard/BinaryDictionary
```
(slash-separated, JNI style).

Do NOT perform project-wide renames or refactors that touch `pckeyboard/`
without verifying the C++ string literals are updated in sync.

## asciiToKeyCode Table

The `asciiToKeyCode` table is an `IntArray(128)` inside `LatinIME.kt` (or a
companion file). It maps ASCII character codes (0–127) to Android `KEYCODE_*`
integer values.

This table is critical for:
- Ctrl+letter synthesis (e.g., Ctrl+C → `KEYCODE_C`)
- Key event forwarding via `InputConnection.sendKeyEvent()`
- All terminal-mode key handling

Rules:
- Do NOT remove entries.
- Do NOT guess keycode values — verify against `android.view.KeyEvent` constants.
- Index 0–31 are control codes; index 32–127 are printable ASCII.

## pckeyboard Package Is Legacy

Everything under `org.pocketworkstation.pckeyboard` is legacy code from the
Hacker's Keyboard fork. The only active file is `BinaryDictionary.java`.

- Do not add new Kotlin or Java files to this package.
- Do not refactor classes inside this package unless the sole goal is the JNI
  bridge maintenance.
- New features belong in `dev.devkey.keyboard.*`.
