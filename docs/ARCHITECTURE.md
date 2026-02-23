# DevKey Architecture

## Overview

DevKey is an Android keyboard app (InputMethodService) forked from Hacker's Keyboard, progressively modernized with Kotlin, Jetpack Compose, Room, and TensorFlow Lite.

## Package Structure

```
dev.devkey.keyboard/
├── core/                    # InputMethodService entry point, key event handling
├── data/
│   ├── db/                  # Room database, DAOs, entities
│   │   ├── dao/             # Data Access Objects
│   │   └── entity/          # Room entity data classes
│   ├── export/              # JSON export/import logic
│   └── repository/          # Data access layer (abstracts DB from features)
├── feature/
│   ├── macro/               # Macro engine (record, save, replay)
│   ├── prediction/          # AI prediction engine (TF Lite)
│   ├── clipboard/           # Clipboard manager
│   ├── voice/               # Whisper voice input
│   └── command/             # Command-aware autocorrect
├── ui/
│   ├── keyboard/            # Keyboard Compose UI (keys, rows, layout)
│   ├── toolbar/             # Toolbar Compose UI
│   ├── suggestion/          # Suggestion bar + macro chips
│   ├── settings/            # Settings screens (Compose)
│   └── theme/               # Theme definitions (Clean Modern)
└── util/                    # Extensions, helpers
```

## IME Lifecycle

1. **System Registration**: `LatinIME` is declared as an InputMethodService in AndroidManifest.xml
2. **Service Creation**: `onCreate()` initializes dictionaries, keyboard views, notification channel
3. **Input Start**: `onStartInput()` / `onStartInputView()` configure keyboard for current text field
4. **Key Events**: Touch events on keyboard views generate key events sent to `InputConnection`
5. **Input End**: `onFinishInput()` / `onFinishInputView()` clean up
6. **Service Destroy**: `onDestroy()` releases native resources

## JNI Bridge

The C++ native dictionary library (`jni_pckeyboard`) registers its JNI methods using the old package path `org.pocketworkstation.pckeyboard.BinaryDictionary`. A bridge class at that path forwards native calls to the new `dev.devkey.keyboard.BinaryDictionary`.

**Do NOT modify:**
- `app/src/main/cpp/` (C++ source files)
- `app/CMakeLists.txt` (library name: `jni_pckeyboard`)
- `app/src/main/java/org/pocketworkstation/pckeyboard/BinaryDictionary.java` (JNI bridge)

## Database

Room database `DevKeyDatabase` with 5 tables:
- **macros**: Recorded keyboard macro sequences
- **learned_words**: User-typed words with frequency tracking
- **clipboard_history**: Clipboard entries with optional encryption
- **settings**: Key-value application settings
- **command_apps**: Package names of apps where command mode activates

## Build System

- **Gradle**: Kotlin DSL with version catalogs (`gradle/libs.versions.toml`)
- **AGP**: 8.5.2
- **Kotlin**: 1.9.24
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Application ID**: `dev.devkey.keyboard`

### Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on connected device
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run integration tests
```

## Key Dependencies

| Dependency | Purpose |
|-----------|---------|
| Jetpack Compose | Modern declarative UI |
| Material 3 | Material Design components |
| Room + KSP | Local database with compile-time query verification |
| Kotlin Coroutines | Async operations, structured concurrency |
| TensorFlow Lite | On-device AI prediction (placeholder) |
| JUnit 5 + Espresso | Testing |

## Where to Add New Features

| Feature Type | Where to Add |
|-------------|-------------|
| New keyboard behavior | `core/` package |
| New data storage | `data/db/entity/` + `data/db/dao/` + add to `DevKeyDatabase` |
| New UI screen | `ui/` appropriate sub-package |
| New feature module | `feature/` new sub-package |
| New utility | `util/` package |

## Legacy Code

The 44 Java files in `dev.devkey.keyboard/` are original Hacker's Keyboard code with updated package declarations. They will be progressively migrated to Kotlin in future sessions. Key files:

- `LatinIME.java` (3566 lines) - The InputMethodService entry point
- `LatinKeyboardView.java` / `LatinKeyboardBaseView.java` - Keyboard rendering
- `Keyboard.java` - Keyboard data model and layout parsing
- `BinaryDictionary.java` - Dictionary lookup via JNI
- `Suggest.java` - Word suggestion engine
