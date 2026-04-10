# Source Excerpts — Organized by File

---

## `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`

### `setNextSuggestions` (line 1895-1897) — **STUB**

```kotlin
private fun setNextSuggestions() {
    setSuggestions(mSuggestPuncList, completions = false, typedWordValid = false, haveMinimalSuggestion = false)
}
```

### `handleSeparator` (line 1570-1624)

```kotlin
private fun handleSeparator(primaryCode: Int) {
    if (mCandidateView != null && mCandidateView!!.dismissAddToDictionaryHint()) {
        postUpdateSuggestions()
    }

    var pickedDefault = false
    val ic = currentInputConnection
    ic?.beginBatchEdit()
    abortCorrection(false)

    if (mPredicting) {
        if (mAutoCorrectOn
            && primaryCode != '\''.code
            && (mJustRevertedSeparator == null
                || mJustRevertedSeparator!!.isEmpty()
                || mJustRevertedSeparator!![0].code != primaryCode)
        ) {
            pickedDefault = pickDefaultSuggestion()
            if (primaryCode == ASCII_SPACE) {
                if (mAutoCorrectEnabled) {
                    mJustAddedAutoSpace = true
                } else {
                    TextEntryState.manualTyped("")
                }
            }
        } else {
            commitTyped(ic, true)
        }
    }
    if (mJustAddedAutoSpace && primaryCode == ASCII_ENTER) {
        removeTrailingSpace()
        mJustAddedAutoSpace = false
    }
    sendModifiableKeyChar(primaryCode.toChar())

    if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
        && primaryCode == ASCII_PERIOD
    ) {
        reswapPeriodAndSpace()
    }

    TextEntryState.typedCharacter(primaryCode.toChar(), true)
    if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
        && primaryCode != ASCII_ENTER
    ) {
        swapPunctuationAndSpace()
    } else if (isPredictionOn() && primaryCode == ASCII_SPACE) {
        doubleSpace()
    }
    if (pickedDefault) {
        TextEntryState.backToAcceptedDefault(mWord.getTypedWord())
    }
    updateShiftKeyState(currentInputEditorInfo)
    ic?.endBatchEdit()
}
```

### Class declaration (line 87)

```kotlin
class LatinIME : InputMethodService(),
    KeyboardActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    WordPromotionDelegate
```

### PluginManager field (line 219) + init (379-387) + unregister (579)

```kotlin
private var mPluginManager: PluginManager? = null
// ...
PluginManager.getPluginDictionaries(applicationContext)
mPluginManager = PluginManager(this)
val pFilter = IntentFilter().apply {
    addDataScheme("package")
    addAction("android.intent.action.PACKAGE_ADDED")
    addAction("android.intent.action.PACKAGE_REMOVED")
}
registerReceiver(mPluginManager, pFilter, Context.RECEIVER_NOT_EXPORTED)
// ...
unregisterReceiver(mPluginManager)
```

### setNextSuggestions call sites
- 905 — inside candidate-view-not-dirty guard
- 960 — `setCandidatesViewShownInternal`
- 1687 — `updateSuggestions` not-predicting early return
- 1809 — `pickSuggestionManually` after commit (not correcting)
- 1839 — `pickDefaultSuggestion` after commit
- 1889 — `setOldSuggestions` not-predicting branch
- 1895 — declaration
- 2476 — `initSuggestPuncList` tail

---

## `app/src/main/java/dev/devkey/keyboard/Suggest.kt`

### `getSuggestions` bigram branch (line 208-250 excerpt)

```kotlin
if (wordComposer.size() == 1 && (mCorrectionMode == CORRECTION_FULL_BIGRAM ||
            mCorrectionMode == CORRECTION_BASIC)
) {
    mBigramPriorities.fill(0)
    collectGarbage(mBigramSuggestions, PREF_MAX_BIGRAMS)

    if (!mutablePrevWordForBigram.isNullOrEmpty()) {
        val lowerPrevWord: CharSequence = mutablePrevWordForBigram.toString().lowercase()
        if (mMainDict.isValidWord(lowerPrevWord)) {
            mutablePrevWordForBigram = lowerPrevWord
        }
        if (mUserBigramDictionary != null) {
            mUserBigramDictionary!!.getBigrams(
                wordComposer, mutablePrevWordForBigram!!, this,
                mNextLettersFrequencies
            )
        }
        if (mContactsDictionary != null) {
            mContactsDictionary!!.getBigrams(
                wordComposer, mutablePrevWordForBigram!!, this,
                mNextLettersFrequencies
            )
        }
        mMainDict.getBigrams(
            wordComposer, mutablePrevWordForBigram!!, this,
            mNextLettersFrequencies
        )
        // ... currentChar filter, copies to mSuggestions ...
    }
}
```

### PluginManager consumer (line 70)

```kotlin
if (!hasMainDictionary()) {
    val locale = context.resources.configuration.locales[0]
    val plug = PluginManager.getDictionary(context, locale.language)
    if (plug != null) {
        mMainDict.close()
        mMainDict = plug
    }
}
```

---

## `app/src/main/java/dev/devkey/keyboard/PluginManager.kt`

### Companion object constants (line 95+)

```kotlin
companion object {
    private const val TAG = "DevKey/PluginManager"
    private const val LEGACY_INTENT_DICT = "org.pocketworkstation.DICT"
    private const val SOFTKEYBOARD_INTENT_DICT = "com.menny.android.anysoftkeyboard.DICTIONARY"
    // ... more constants ...
```

### `getSoftKeyboardDictionaries` (line 107-173)

```kotlin
fun getSoftKeyboardDictionaries(packageManager: PackageManager) {
    val dictIntent = Intent(SOFTKEYBOARD_INTENT_DICT)
    val dictPacks = packageManager.queryBroadcastReceivers(
        dictIntent, PackageManager.GET_META_DATA
    )
    for (ri in dictPacks) {
        val appInfo = ri.activityInfo.applicationInfo
        val pkgName = appInfo.packageName
        var success = false
        try {
            val res = packageManager.getResourcesForApplication(appInfo)  // ← foreign resources, no signature check
            var dictId = res.getIdentifier("dictionaries", "xml", pkgName)
            if (dictId == 0) {
                try {
                    dictId = ri.activityInfo.metaData.getInt(SOFTKEYBOARD_DICT_RESOURCE_METADATA_NAME)
                } catch (_: Exception) { }
            }
            if (dictId == 0) continue
            val xrp = res.getXml(dictId)
            // ... XML parse for Dictionary tag ...
            val spec = DictPluginSpecSoftKeyboard(pkgName, assetName, resId)
            mPluginDicts[lang] = spec
            success = true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $pkgName", e)
        } finally {
            if (!success) Log.i(TAG, "failed to load plugin dictionary spec from $pkgName")
        }
    }
}
```

### `getLegacyDictionaries` (line 175-218)

```kotlin
fun getLegacyDictionaries(packageManager: PackageManager) {
    val dictIntent = Intent(LEGACY_INTENT_DICT)
    val dictPacks = packageManager.queryIntentActivities(dictIntent, 0)
    for (ri in dictPacks) {
        val appInfo = ri.activityInfo.applicationInfo
        val pkgName = appInfo.packageName
        try {
            val res = packageManager.getResourcesForApplication(appInfo)
            val langId = res.getIdentifier("dict_language", "string", pkgName)
            if (langId == 0) continue
            val lang = res.getString(langId)
            // ... discover main / main0..N raw resources ...
            val spec = DictPluginSpecLegacy(pkgName, rawIds)
            mPluginDicts[lang] = spec
        } catch (e: PackageManager.NameNotFoundException) { ... }
    }
}
```

---

## `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt`

### `initialize()` (line 93-119)

```kotlin
private fun initialize() {
    if (interpreter != null) return

    try {
        val assetManager = context.assets
        val modelFile = assetManager.open("whisper-tiny.en.tflite")
        val modelBytes = modelFile.readBytes()
        modelFile.close()

        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
        modelBuffer.put(modelBytes)
        modelBuffer.rewind()

        interpreter = Interpreter(modelBuffer)
        modelLoaded = true

        processor.loadResources()

        Log.i(TAG, "Whisper model loaded successfully")
    } catch (e: Exception) {
        Log.w(TAG, "Whisper model not available — voice input will be limited", e)
        modelLoaded = false
    }
}
```

### VoiceState enum (line 48-56)

```kotlin
enum class VoiceState {
    IDLE,
    LISTENING,
    /** Processing recorded audio through Whisper. */
    PROCESSING,
    ERROR
}
```

---

## `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`

### `enableServer` / `disableServer` (line 37-42)

```kotlin
fun enableServer(url: String) {
    serverUrl = url
}

fun disableServer() {
    serverUrl = null
}
```

### `voice` category helper (line 63)

```kotlin
fun voice(message: String, data: Map<String, Any?> = emptyMap())
```

---

## `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt`

### `isDebugBuild` (line 38-42)

```kotlin
fun isDebugBuild(context: Context): Boolean {
    return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
```

---

## `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyData.kt`

### KeyData class (line 43-51)

```kotlin
data class KeyData(
    val primaryLabel: String,
    val primaryCode: Int,
    val longPressLabel: String? = null,
    val longPressCode: Int? = null,
    val type: KeyType = KeyType.LETTER,
    val weight: Float = 1.0f,
    val isRepeatable: Boolean = false
)
```

### LayoutMode enum (line 26-30)

```kotlin
enum class LayoutMode {
    COMPACT,
    COMPACT_DEV,
    FULL
}
```

### KeyCodes.KEYCODE_VOICE (line 137)

```kotlin
const val KEYCODE_VOICE = -102
```

---

## `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyView.kt`

### Long-press dispatch (line 213-215)

```kotlin
KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
if (key.longPressCode != null) {
    onKeyAction(key.longPressCode)
}
```

---

## `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`

### VoiceInputEngine wiring (line 81-249, excerpts)

```kotlin
val voiceInputEngine = remember { VoiceInputEngine(context) }

DisposableEffect(voiceInputEngine) {
    onDispose { voiceInputEngine.release() }
}

val voiceState by voiceInputEngine.state.collectAsState()
val voiceAmplitude by voiceInputEngine.amplitude.collectAsState()

// onVoice handler:
coroutineScope.launch { voiceInputEngine.startListening() }

// onStop handler:
val transcription = voiceInputEngine.stopListening()
if (transcription.isNotEmpty()) {
    bridge.onText(transcription)
}

// onCancel handler:
voiceInputEngine.cancelListening()
```

---

## `gradle/libs.versions.toml`

### TF Lite + Kotlin + Compose versions (line 1-17 excerpt)

```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.21"
compose-bom = "2024.06.00"
tflite = "2.16.1"
tflite-support = "0.4.4"
```

---

## `app/src/main/AndroidManifest.xml`

### RECORD_AUDIO + mic feature (line 6-7)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />
```

### LatinIME service (line 21-30)

```xml
<service android:name="LatinIME"
        android:label="@string/english_ime_name"
        android:permission="android.permission.BIND_INPUT_METHOD"
        android:hardwareAccelerated="false"
        android:exported="true">
    <intent-filter>
        <action android:name="android.view.InputMethod" />
    </intent-filter>
    <meta-data android:name="android.view.im" android:resource="@xml/method" />
</service>
```

### Voice permission activity (line 63-65)

```xml
<activity
    android:name=".ui.voice.PermissionActivity"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:exported="false" />
```
