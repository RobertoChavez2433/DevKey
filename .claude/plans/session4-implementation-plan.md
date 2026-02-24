# Session 4 Implementation Plan: Voice-to-Text, Command Mode & Autocorrect

**Design Doc**: `docs/plans/2026-02-23-session4-voice-command-autocorrect-design.md`
**Status**: Ready for implementation
**Base path**: `app/src/main/java/dev/devkey/keyboard/`

---

## Phase 1: InputMode Enum, Theme Additions & Build Config

**Goal**: Define input mode state, add Session 4 theme constants, configure TF Lite build settings, and add KeyboardMode.Voice.

**Files to create**:

### 1.1 `feature/command/InputMode.kt`
- Enum class:
  ```kotlin
  enum class InputMode {
      NORMAL,
      COMMAND
  }
  ```

**Files to modify**:

### 1.2 `ui/keyboard/KeyboardMode.kt`
- Add new variant to the sealed class:
  ```kotlin
  /** Voice input mode — keyboard replaced with voice recording UI. */
  object Voice : KeyboardMode()
  ```

### 1.3 `ui/theme/DevKeyTheme.kt`
Add the following new constants (do NOT change any existing constants):

**Colors**:
- `voicePanelBg = Color(0xFF1E1E2E)` — dark blue-tinted background for voice panel
- `voiceMicActive = Color(0xFF4CAF50)` — green pulsing mic indicator
- `voiceMicInactive = Color(0xFF666666)` — grey mic when not recording
- `voiceWaveform = Color(0xFF4CAF50)` — green waveform visualization
- `voiceStatusText = Color(0xFFCCCCCC)` — status text color
- `cmdBadgeBg = Color(0xFF3A3A2A)` — dark yellow-tinted background for CMD badge
- `cmdBadgeText = Color(0xFFFFB300)` — amber text for CMD badge
- `autocorrectBold = Color(0xFFFFFFFF)` — white bold for autocorrect suggestion (slot 1)

**Dimensions**:
- `voicePanelHeight = 200.dp` — height of voice input panel (matches keyboard area)
- `voiceMicSize = 64.dp` — mic button size
- `cmdBadgeHeight = 20.dp` — CMD badge height
- `cmdBadgePadding = 4.dp` — CMD badge internal padding

**Typography**:
- `voiceStatusSize = 16.sp` — "Listening..." / "Processing..." text
- `cmdBadgeTextSize = 10.sp` — CMD badge text size

### 1.4 `app/build.gradle.kts`
- Ensure TF Lite dependencies are in the `dependencies` block (they may already be declared via version catalog but not applied):
  ```kotlin
  implementation(libs.tflite)
  implementation(libs.tflite.support)
  ```
- Add `aaptOptions` to prevent compression of `.tflite` model files:
  ```kotlin
  android {
      aaptOptions {
          noCompress += "tflite"
      }
  }
  ```

### 1.5 `app/src/main/AndroidManifest.xml`
- Add permission declaration:
  ```xml
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  ```
- Add PermissionActivity declaration (created in Phase 4):
  ```xml
  <activity
      android:name=".ui.voice.PermissionActivity"
      android:theme="@android:style/Theme.Translucent.NoTitleBar"
      android:exported="false" />
  ```

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 2: Dictionary Provider & Prediction Engine

**Goal**: Wrap the existing native dictionary (BinaryDictionary/Suggest) in a Kotlin-friendly API and build the prediction engine that feeds the SuggestionBar.

**Files to create**:

### 2.1 `feature/prediction/DictionaryProvider.kt`
- Class `DictionaryProvider`:
  - Wraps the existing `Suggest` class (which already uses `BinaryDictionary`, `UserDictionary`, and `ContactsDictionary` under the hood)
  - Constructor takes `suggest: Suggest?` (nullable — may not be initialized yet)
  - `fun getSuggestions(word: String, maxResults: Int = 3): List<String>`:
    - If `suggest` is null, return empty list
    - Creates a `WordComposer` from the typed characters
    - Calls `suggest.getSuggestions()` passing the word composer
    - Returns the top `maxResults` suggestions as strings
  - `fun isValidWord(word: String): Boolean`:
    - Delegates to `suggest.isValidWord(word)` if available
    - Returns false if suggest is null

### 2.2 `feature/prediction/AutocorrectEngine.kt`
- Class `AutocorrectEngine(private val dictionaryProvider: DictionaryProvider)`:
  - `enum class Aggressiveness { OFF, MILD, AGGRESSIVE }`
  - `var aggressiveness: Aggressiveness = Aggressiveness.MILD`
  - `fun getCorrection(typed: String, learnedWords: Set<String>): AutocorrectResult`:
    - If `aggressiveness == OFF`, return `AutocorrectResult.None`
    - If `typed` is in `learnedWords`, return `AutocorrectResult.None` (user intended this word)
    - If `dictionaryProvider.isValidWord(typed)`, return `AutocorrectResult.None` (already correct)
    - Get suggestions from dictionary provider
    - If top suggestion has edit distance <= 2 from `typed`, return `AutocorrectResult.Suggestion(correction, autoApply = aggressiveness == AGGRESSIVE)`
    - Otherwise return `AutocorrectResult.None`
  - Sealed class `AutocorrectResult`:
    - `object None : AutocorrectResult()`
    - `data class Suggestion(val correction: String, val autoApply: Boolean) : AutocorrectResult()`
  - Private `fun editDistance(a: String, b: String): Int` — standard Levenshtein distance

### 2.3 `feature/prediction/LearningEngine.kt`
- Class `LearningEngine(private val dao: LearnedWordDao)`:
  - `private val learnedWordsCache = mutableSetOf<String>()` — in-memory cache for fast lookup
  - `suspend fun initialize()`: loads all words from DAO into cache
  - `suspend fun onWordCommitted(word: String, isCommand: Boolean, contextApp: String?)`:
    - If word length < 2, ignore
    - Check if word exists in DAO via `dao.findWord(word, contextApp)`
    - If exists: increment frequency, update
    - If new: insert with frequency = 1
    - Add to cache
  - `fun isLearnedWord(word: String): Boolean` — checks in-memory cache
  - `fun getLearnedWords(): Set<String>` — returns cache copy
  - `suspend fun getCommandSuggestions(prefix: String, limit: Int = 3): List<String>`:
    - Queries `dao.getCommandWords()` filtered by prefix, sorted by frequency

### 2.4 `feature/prediction/PredictionEngine.kt`
- Class `PredictionEngine(private val dictionaryProvider: DictionaryProvider, private val autocorrectEngine: AutocorrectEngine, private val learningEngine: LearningEngine)`:
  - `var inputMode: InputMode = InputMode.NORMAL`
  - `fun predict(currentWord: String, isCommand: Boolean = false): List<PredictionResult>`:
    - If `currentWord` is empty, return empty list
    - If `inputMode == COMMAND` or `isCommand`:
      - Return command suggestions from `learningEngine.getCommandSuggestions(currentWord)`
      - Map to `PredictionResult` with `isAutocorrect = false`
    - Normal mode:
      - Get dictionary suggestions via `dictionaryProvider.getSuggestions(currentWord)`
      - Get autocorrect result via `autocorrectEngine.getCorrection(currentWord, learningEngine.getLearnedWords())`
      - Build result list: autocorrect suggestion first (if any, marked `isAutocorrect = true`), then dictionary completions
      - Return up to 3 results
  - Data class `PredictionResult(val word: String, val isAutocorrect: Boolean = false)`

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 3: Command Mode Detection

**Goal**: Detect terminal apps and manage command mode state.

**Files to create**:

### 3.1 `feature/command/CommandModeDetector.kt`
- Class `CommandModeDetector(private val repository: CommandModeRepository)`:
  - `private val _inputMode = MutableStateFlow(InputMode.NORMAL)`
  - `val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()`
  - `private var manualOverride: InputMode? = null` — manual toggle override, cleared on app switch
  - `private var lastPackageName: String? = null`
  - Private constant list of known terminal packages:
    ```kotlin
    private val TERMINAL_PACKAGES = setOf(
        "com.termux",
        "org.connectbot",
        "com.sonelli.juicessh",
        "com.server.auditor",
        "com.offsec.nethunter",
        "jackpal.androidterm",
        "yarolegovich.materialterminal",
        "com.termoneplus",
        "com.googlecode.android_scripting"
    )
    ```
  - `suspend fun detect(packageName: String?)`:
    - If packageName != lastPackageName, clear manualOverride (app switched)
    - `lastPackageName = packageName`
    - If `manualOverride != null`, set `_inputMode.value = manualOverride` and return
    - Check `repository.getMode(packageName)` for user override
    - If user override exists, use it
    - Else if packageName in `TERMINAL_PACKAGES`, set `COMMAND`
    - Else set `NORMAL`
  - `fun toggleManualOverride()`:
    - If `manualOverride == null || manualOverride == NORMAL`, set `manualOverride = COMMAND`
    - Else set `manualOverride = NORMAL`
    - Update `_inputMode.value`
  - `fun isCommandMode(): Boolean = _inputMode.value == InputMode.COMMAND`

### 3.2 `feature/command/CommandModeRepository.kt`
- Class `CommandModeRepository(private val dao: CommandAppDao)`:
  - `suspend fun getMode(packageName: String?): InputMode?`:
    - If packageName is null, return null
    - `val entity = dao.getByPackageName(packageName) ?: return null`
    - Return `if (entity.mode == "command") InputMode.COMMAND else InputMode.NORMAL`
  - `suspend fun setMode(packageName: String, mode: InputMode)`:
    - Inserts/updates `CommandAppEntity(packageName, mode.name.lowercase())`
  - `suspend fun removeOverride(packageName: String)`:
    - Deletes entry for packageName
  - `fun getAllOverrides(): Flow<List<CommandAppEntity>>`:
    - Delegates to `dao.getAllCommandApps()`

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 4: Whisper Voice Input Engine

**Goal**: TF Lite Whisper inference, audio capture, silence detection, and permission handling.

**Prerequisites**: TF Lite dependencies configured (Phase 1), whisper-tiny.en.tflite model file in assets.

**Model file**:
- Download `whisper-tiny.en.tflite` from [whisper.tflite releases](https://github.com/nyadla-sys/whisper.tflite) or [whisper_android](https://github.com/vilassn/whisper_android)
- Also need `filters_vocab_en.bin` (Whisper's mel filter bank + English vocabulary)
- Place both in `app/src/main/assets/`
- If model files are not readily available as direct downloads, create placeholder infrastructure that loads from assets and add a TODO for model procurement

**Files to create**:

### 4.1 `feature/voice/SilenceDetector.kt`
- Class `SilenceDetector`:
  - `companion object { const val DEFAULT_THRESHOLD = 500.0; const val DEFAULT_TIMEOUT_MS = 2000L }`
  - `var threshold: Double = DEFAULT_THRESHOLD` — RMS amplitude threshold
  - `var timeoutMs: Long = DEFAULT_TIMEOUT_MS` — silence duration before auto-stop
  - `private var silenceStartTime: Long = 0L`
  - `fun isSilent(audioBuffer: ShortArray, size: Int): Boolean`:
    - Calculate RMS: `sqrt(sum of (sample^2) / size)`
    - If RMS < threshold: if silenceStartTime == 0, set it to current time. Return (currentTime - silenceStartTime) >= timeoutMs
    - If RMS >= threshold: reset silenceStartTime to 0, return false
  - `fun reset()`: sets silenceStartTime = 0

### 4.2 `feature/voice/WhisperProcessor.kt`
- Class `WhisperProcessor(private val context: Context)`:
  - Loads mel filter bank from `assets/filters_vocab_en.bin`
  - `fun processAudio(pcmData: ShortArray, sampleRate: Int = 16000): FloatArray`:
    - Convert ShortArray PCM to float (-1.0 to 1.0)
    - Pad or trim to 30 seconds (480,000 samples at 16kHz) — Whisper's expected input length
    - Compute mel spectrogram: 80 mel bins, 128 frames per segment
    - Return flattened float array ready for TF Lite interpreter input
  - Note: The exact mel computation mirrors the Python reference in whisper.tflite repo. If complexity is too high, use a simplified approach: pass raw audio directly if the model variant accepts it, or reference the Java implementation from whisper_android.

### 4.3 `feature/voice/VoiceInputEngine.kt`
- Class `VoiceInputEngine(private val context: Context)`:
  - `private var interpreter: Interpreter? = null` — TF Lite interpreter, lazy init
  - `private var audioRecord: AudioRecord? = null`
  - `private val silenceDetector = SilenceDetector()`
  - `private val processor: WhisperProcessor = WhisperProcessor(context)`
  - `private val _state = MutableStateFlow(VoiceState.IDLE)`
  - `val state: StateFlow<VoiceState> = _state.asStateFlow()`
  - `private val _amplitude = MutableStateFlow(0f)` — for waveform visualization
  - `val amplitude: StateFlow<Float> = _amplitude.asStateFlow()`
  - `private var audioBuffer = mutableListOf<Short>()` — accumulated audio
  - `enum class VoiceState { IDLE, LISTENING, PROCESSING, ERROR }`
  - `fun initialize()`:
    - Load model from assets: `context.assets.open("whisper-tiny.en.tflite")` → ByteBuffer
    - Create `Interpreter(modelBuffer)`
    - Called lazily on first `startListening()`
  - `suspend fun startListening()`:
    - If interpreter == null, call `initialize()`
    - Create `AudioRecord` with: `AudioFormat.ENCODING_PCM_16BIT`, `CHANNEL_IN_MONO`, 16000 Hz sample rate
    - Clear audioBuffer, reset silenceDetector
    - Set state to LISTENING
    - Start recording in a coroutine loop:
      - Read chunks (e.g., 1600 samples = 100ms at 16kHz)
      - Append to audioBuffer
      - Update _amplitude (RMS of chunk normalized to 0-1)
      - Check silenceDetector — if silent for timeout, auto-stop
  - `suspend fun stopListening(): String`:
    - Stop and release AudioRecord
    - Set state to PROCESSING
    - Convert audioBuffer to ShortArray
    - Call `processor.processAudio(audioData)` to get mel spectrogram
    - Run TF Lite inference: `interpreter.run(inputArray, outputArray)`
    - Decode output tokens to text (using vocab from filters_vocab_en.bin)
    - Set state to IDLE
    - Return transcription string
  - `fun cancelListening()`:
    - Stop and release AudioRecord
    - Clear buffer
    - Set state to IDLE
  - `fun release()`:
    - Close interpreter
    - Release audioRecord

### 4.4 `ui/voice/PermissionActivity.kt`
- Class `PermissionActivity : ComponentActivity()`:
  - Transparent activity that requests `RECORD_AUDIO` permission
  - Uses `registerForActivityResult(RequestPermission())` on launch
  - On result: sends result via `LocalBroadcastManager` or a static callback
  - Finishes immediately after result
  - Override `onCreate()`: launch permission request immediately
  - Static companion object:
    - `var onPermissionResult: ((Boolean) -> Unit)? = null`
    - `const val ACTION_PERMISSION_RESULT = "dev.devkey.keyboard.VOICE_PERMISSION"`

**Build gate**: `./gradlew assembleDebug` must pass (model file may not be present yet — guard with null checks).

---

## Phase 5: Voice Input UI

**Goal**: Compose UI for the voice recording panel.

**Files to create**:

### 5.1 `ui/voice/VoiceInputPanel.kt`
- `@Composable fun VoiceInputPanel(voiceState: VoiceInputEngine.VoiceState, amplitude: Float, onStop: () -> Unit, onCancel: () -> Unit)`:
  - Height: `voicePanelHeight` (200dp), fills keyboard area
  - Background: `voicePanelBg`
  - Layout: `Column(horizontalAlignment = CenterHorizontally, verticalArrangement = Center)`:
    - **Status text**: "Listening..." (when LISTENING), "Processing..." (when PROCESSING)
      - Color: `voiceStatusText`, size: `voiceStatusSize`
    - **Mic icon**: Large circle (`voiceMicSize` = 64dp)
      - LISTENING: `voiceMicActive` green, pulsing animation (infinite `animateFloat` scale 0.9 → 1.1)
      - PROCESSING: `voiceMicInactive` grey, no animation
      - Contains mic emoji or icon "🎤" centered
    - **Waveform indicator** (below mic): Simple `Canvas` drawing based on `amplitude` value
      - 5-7 vertical bars at varying heights based on amplitude
      - Color: `voiceWaveform`
      - Only visible when LISTENING
    - **Button row**: `Row` with:
      - "Cancel" text button (grey) → `onCancel()`
      - "Done" text button (green, `voiceMicActive`) → `onStop()` — only visible when LISTENING
    - **PROCESSING state**: Show a `CircularProgressIndicator` instead of buttons

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 6: Root Composable Wiring

**Goal**: Wire all Session 4 features into `DevKeyKeyboard.kt` — replace hardcoded suggestions with live predictions, add voice mode, add command mode awareness.

**Files to modify**:

### 6.1 `ui/keyboard/DevKeyKeyboard.kt` — Major additions

New state and dependencies to add to the `remember {}` block:
```kotlin
// Session 4 additions
val commandModeRepo = remember { CommandModeRepository(database.commandAppDao()) }
val commandModeDetector = remember { CommandModeDetector(commandModeRepo) }
val learningEngine = remember { LearningEngine(database.learnedWordDao()) }
val dictionaryProvider = remember { DictionaryProvider(null) } // TODO: wire Suggest instance from LatinIME
val autocorrectEngine = remember { AutocorrectEngine(dictionaryProvider) }
val predictionEngine = remember { PredictionEngine(dictionaryProvider, autocorrectEngine, learningEngine) }
val voiceInputEngine = remember { VoiceInputEngine(context) }

// Observe command mode
val inputMode by commandModeDetector.inputMode.collectAsState()
val voiceState by voiceInputEngine.state.collectAsState()
val voiceAmplitude by voiceInputEngine.amplitude.collectAsState()

// Track current typed word for predictions
var currentWord by remember { mutableStateOf("") }
val predictions = remember(currentWord, inputMode) {
    predictionEngine.inputMode = inputMode
    if (currentWord.isNotEmpty()) {
        predictionEngine.predict(currentWord, inputMode == InputMode.COMMAND)
    } else {
        emptyList()
    }
}
val suggestionStrings = predictions.map { it.word }
```

Changes to the `when (keyboardMode)` block:
- **`KeyboardMode.Normal`**: Replace `defaultSuggestions` with `suggestionStrings`. If `suggestionStrings` is empty, show fallback empty list (SuggestionBar handles this).
- **`KeyboardMode.Voice`** (new case):
  ```kotlin
  KeyboardMode.Voice -> {
      VoiceInputPanel(
          voiceState = voiceState,
          amplitude = voiceAmplitude,
          onStop = {
              coroutineScope.launch {
                  val transcription = voiceInputEngine.stopListening()
                  if (transcription.isNotEmpty()) {
                      bridge.onText(transcription)
                  }
                  keyboardMode = KeyboardMode.Normal
              }
          },
          onCancel = {
              voiceInputEngine.cancelListening()
              keyboardMode = KeyboardMode.Normal
          }
      )
  }
  ```
  - When in Voice mode, hide the keyboard (don't render KeyboardView)

Changes to toolbar wiring:
- Replace `onVoice = { /* Session 4 */ }` with:
  ```kotlin
  onVoice = {
      if (keyboardMode == KeyboardMode.Voice) {
          voiceInputEngine.cancelListening()
          keyboardMode = KeyboardMode.Normal
      } else {
          keyboardMode = KeyboardMode.Voice
          coroutineScope.launch { voiceInputEngine.startListening() }
      }
  }
  ```

Changes to `onKeyAction`:
- After `bridge.onKey(code, modifierState)`, track the current word being typed:
  ```kotlin
  // Track current word for predictions
  if (code == ' '.code || code == '.'.code || code == '\n'.code) {
      // Word committed — learn it
      if (currentWord.isNotEmpty()) {
          coroutineScope.launch {
              learningEngine.onWordCommitted(
                  currentWord,
                  isCommand = inputMode == InputMode.COMMAND,
                  contextApp = null // TODO: get from LatinIME
              )
          }
          currentWord = ""
      }
  } else if (code == -5) { // KEYCODE_DELETE
      currentWord = currentWord.dropLast(1)
  } else if (code in 32..126) {
      currentWord += code.toChar()
  }
  ```

Remove `val defaultSuggestions = remember { listOf("the", "I", "and") }` — no longer needed.

Conditional keyboard rendering for Voice mode:
```kotlin
// 4. Keyboard (hidden during voice input)
if (keyboardMode !is KeyboardMode.Voice) {
    KeyboardView(...)
}
```

### 6.2 `ui/toolbar/ToolbarRow.kt`
- Remove the "Coming soon" toast from the mic button — it now has a real action
- Add CMD badge support:
  - Add `isCommandMode: Boolean = false` parameter
  - When `isCommandMode` is true, show a small "CMD" badge next to the overflow or as an overlay
  - Badge: `Box` with `cmdBadgeBg` background, rounded 4dp, text "CMD" in `cmdBadgeText` color, `cmdBadgeTextSize`
- Add command mode toggle to overflow:
  - Add `onCommandModeToggle: () -> Unit = {}` parameter
  - When overflow is tapped, for now call `onCommandModeToggle()` directly (proper overflow menu deferred to Session 5)

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 7: IME Integration

**Goal**: Wire command mode detection into LatinIME lifecycle, pass Suggest instance to DictionaryProvider, handle voice permission flow.

**Files to modify**:

### 7.1 `core/LatinIME.java`

Add command mode detection in `onStartInputView()`:
```java
// After existing setup code:
if (commandModeDetector != null) {
    String packageName = attribute.packageName;
    // Run detection on IO thread
    new Thread(() -> {
        try {
            // CommandModeDetector.detect is a suspend function — call via runBlocking or adapt
            // Since this is Java, use a simple approach:
            commandModeDetector.detectSync(packageName);
        } catch (Exception e) {
            Log.w(TAG, "Command mode detection failed", e);
        }
    }).start();
}
```

Add new fields:
```java
private CommandModeDetector commandModeDetector;
```

In `onCreate()` (after database setup):
```java
CommandModeRepository cmdRepo = new CommandModeRepository(
    DevKeyDatabase.Companion.getInstance(this).commandAppDao()
);
commandModeDetector = new CommandModeDetector(cmdRepo);
```

Pass `commandModeDetector` to ComposeKeyboardViewFactory — add it as a parameter to `DevKeyKeyboard` composable or make it accessible via a static holder/singleton pattern.

Also: pass `mSuggest` (the existing Suggest instance) to `DictionaryProvider` so predictions use the real dictionary. The cleanest approach is to create a `SessionDependencies` object that holds references:
```kotlin
object SessionDependencies {
    var suggest: Suggest? = null
    var commandModeDetector: CommandModeDetector? = null
    var currentPackageName: String? = null
}
```

In `LatinIME.onCreate()`, set `SessionDependencies.suggest = mSuggest`.
In `onStartInputView()`, set `SessionDependencies.currentPackageName = attribute.packageName`.
In `DevKeyKeyboard.kt`, read from `SessionDependencies`.

**Note**: This is a pragmatic approach. A proper DI solution would be cleaner but is overkill for this stage. The `SessionDependencies` singleton bridges the Java IME lifecycle with the Compose UI.

### 7.2 Add `detectSync` to CommandModeDetector
Since `detect()` is a suspend function and LatinIME is Java, add a non-suspend wrapper:
```kotlin
fun detectSync(packageName: String?) {
    runBlocking { detect(packageName) }
}
```

**Build gate**: `./gradlew assembleDebug` must pass.

---

## Phase 8: Build Verification & Cleanup

**Goal**: Final build, cleanup, and verification.

### 8.1 Full build
- Run `./gradlew assembleDebug`
- Fix any compilation errors

### 8.2 Model file check
- Verify `whisper-tiny.en.tflite` is in `app/src/main/assets/` (or add null guards if model procurement is still pending)
- Verify `filters_vocab_en.bin` is in `app/src/main/assets/`
- If model files are not available, ensure voice engine degrades gracefully (shows "Model not found" error instead of crashing)

### 8.3 Cleanup
- Delete `.gitkeep` files from directories that now have real files: `feature/prediction/`, `feature/command/`, `feature/voice/`, `ui/voice/`
- Verify no unused imports in new files
- Verify no circular dependencies
- Remove any remaining "Coming soon" toasts for features that are now wired

### 8.4 Verify imports
- All new Compose files import from `androidx.compose.*`
- All Room operations import from `dev.devkey.keyboard.data.db.*`
- TF Lite imports from `org.tensorflow.lite.*`
- All feature classes are properly accessible from Compose UI files and Java files

**Final build gate**: `./gradlew assembleDebug` passes cleanly.

---

## Dependency Graph

```
Phase 1 (InputMode + Theme + Build Config)
  ├── Phase 2 (Dictionary Provider + Prediction Engine)
  │     └── Phase 6 (Root Composable Wiring)
  ├── Phase 3 (Command Mode Detection)
  │     ├── Phase 6 (Root Composable Wiring)
  │     └── Phase 7 (IME Integration)
  └── Phase 4 (Whisper Voice Engine)
        └── Phase 5 (Voice Input UI)
              └── Phase 6 (Root Composable Wiring)

Phase 6 → Phase 7 (IME Integration) → Phase 8 (Build & Cleanup)
```

Phases 2, 3, and 4 can run in parallel (independent). Phase 5 depends only on Phase 4. Phase 6 depends on 2, 3, and 5. All other phases are sequential.

---

## Files Summary

**New files (12)**:
1. `feature/command/InputMode.kt` — NORMAL/COMMAND enum
2. `feature/command/CommandModeDetector.kt` — Package-based terminal app detection
3. `feature/command/CommandModeRepository.kt` — Room-backed command app CRUD
4. `feature/prediction/DictionaryProvider.kt` — Kotlin wrapper for Suggest/BinaryDictionary
5. `feature/prediction/AutocorrectEngine.kt` — Edit-distance typo correction
6. `feature/prediction/LearningEngine.kt` — Learned words tracking + frequency
7. `feature/prediction/PredictionEngine.kt` — Orchestrates completions + corrections
8. `feature/voice/SilenceDetector.kt` — RMS-based silence detection
9. `feature/voice/WhisperProcessor.kt` — PCM to mel spectrogram conversion
10. `feature/voice/VoiceInputEngine.kt` — TF Lite Whisper inference + AudioRecord
11. `ui/voice/VoiceInputPanel.kt` — Voice recording Compose UI
12. `ui/voice/PermissionActivity.kt` — Transparent RECORD_AUDIO permission activity

**Modified files (7)**:
1. `ui/keyboard/KeyboardMode.kt` — Add `Voice` variant
2. `ui/theme/DevKeyTheme.kt` — Voice, CMD badge, autocorrect colors/dimensions
3. `app/build.gradle.kts` — TF Lite dependencies, aaptOptions
4. `app/src/main/AndroidManifest.xml` — RECORD_AUDIO permission, PermissionActivity
5. `ui/keyboard/DevKeyKeyboard.kt` — Wire prediction, command mode, voice; replace hardcoded suggestions
6. `ui/toolbar/ToolbarRow.kt` — Remove voice toast, add CMD badge, command toggle
7. `core/LatinIME.java` — Command mode detection in onStartInputView, SessionDependencies

**Asset files**:
- `app/src/main/assets/whisper-tiny.en.tflite` — Whisper tiny English model (~40MB)
- `app/src/main/assets/filters_vocab_en.bin` — Mel filter bank + English vocabulary

---

## Known Risks

- **Whisper model availability**: The `.tflite` model files need to be obtained from the whisper.tflite or whisper_android repos. They may need conversion or may be available as pre-built releases. If not immediately available, Phase 4 should create the full infrastructure with graceful degradation.
- **Mel spectrogram complexity**: The WhisperProcessor mel spectrogram computation is non-trivial. Reference the Java implementation from [whisper_android](https://github.com/vilassn/whisper_android) rather than reimplementing from scratch.
- **AudioRecord permissions**: On Android 6+, `RECORD_AUDIO` requires runtime permission. The PermissionActivity approach works but adds a brief visual flash. Alternative: use `ContextCompat.checkSelfPermission()` and show a toast directing user to settings if denied.
- **Suggest instance timing**: `mSuggest` in LatinIME is initialized during keyboard setup, not in `onCreate()`. The `SessionDependencies.suggest` reference must be set after `mSuggest` is created (in `onStartInputView` or similar).
- **Thread safety**: `VoiceInputEngine` records audio on a background coroutine and runs inference on another. Ensure StateFlow updates happen on the correct dispatcher.
- **DictionaryProvider WordComposer**: Creating a `WordComposer` from a plain string requires mimicking the key input sequence. May need to set input codes manually. Reference how `LatinIME` builds `WordComposer` during typing.
- **runBlocking in Java bridge**: Using `runBlocking` in `detectSync()` blocks the calling thread. Since it's called from `onStartInputView()` on a background thread, this is acceptable. Do NOT call from main thread.
