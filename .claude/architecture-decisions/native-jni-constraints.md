# Native JNI Constraints

Architecture constraints for the C++ native dictionary library and JNI bridge.

## BinaryDictionary Is Java Forever

`org.pocketworkstation.pckeyboard.BinaryDictionary` is the sole remaining Java
file in the project. It must stay Java indefinitely.

Reasons:
1. JNI method signatures are generated from Java bytecode name mangling.
   Converting to Kotlin changes how the compiler generates method names,
   breaking the native linkage.
2. The C++ side references the class by string at runtime using JNI FindClass.
   Any package or class name change requires a matching C++ update that may
   not be trivially achievable.
3. The risk of breaking dictionary lookup silently (in release builds only,
   where ProGuard runs) is too high to justify migration.

Constraints:
- File stays at: `app/src/main/java/org/pocketworkstation/pckeyboard/BinaryDictionary.java`
- Package: `org.pocketworkstation.pckeyboard` (must match C++ string literals)
- Class: `BinaryDictionary` (must match C++ string literals)
- Language: Java (NOT Kotlin)

## C++ Package Path

The native library (`liblatinime`, in `jni/`) uses JNI-style slash-separated
paths to find the Java class:

```c
// JNI-style class path used in native code:
"org/pocketworkstation/pckeyboard/BinaryDictionary"
```

If you ever need to locate this reference in C++, search for this exact string.
Any rename of the Java class or package requires a corresponding update to this
string in the C++ source AND a rebuild of the native library.

## Native Library Build

`liblatinime.so` is the compiled native dictionary library. Build
considerations:
- Compiled via NDK through the Gradle build (`externalNativeBuild`).
- The `.so` must be rebuilt when any C++ source changes; Gradle handles this.
- The library is not distributed separately — it is bundled in the APK.
- ProGuard must keep the `BinaryDictionary` class to prevent R8 from removing
  the JNI entry points (see `build-config.md`).

## Dictionary Plugin Security

Dictionary data is loaded from device storage. Security constraints:
- Dictionary file paths must be validated before passing to native code.
- Do NOT accept dictionary file paths from untrusted app intents without
  validation.
- The native layer does minimal path checking — the Kotlin/Java layer is
  responsible for safe path construction.

## What NOT to Do

- Do NOT run a project-wide "convert Java to Kotlin" refactor that touches
  `pckeyboard/`. The migration (Sessions 21–24) was complete except for this
  file, intentionally.
- Do NOT add new classes to the `org.pocketworkstation.pckeyboard` package.
  New code belongs in `dev.devkey.keyboard.*`.
- Do NOT strip the ProGuard keep rule for `BinaryDictionary` when cleaning up
  ProGuard configs.
