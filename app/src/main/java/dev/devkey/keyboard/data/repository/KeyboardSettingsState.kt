package dev.devkey.keyboard.data.repository

/**
 * Cached keyboard settings state fields.
 *
 * All field reads/writes are main-thread only. These are populated by
 * [PrefRegistry.initPrefs] and updated by [PrefRegistry.handlePreferenceChanged].
 */
abstract class KeyboardSettingsState {

    // Read by Keyboard
    @JvmField var popupKeyboardFlags = 0x1
    @JvmField var topRowScale = 1.0f

    // Read by LatinIME
    @JvmField var suggestedPunctuation = "!?,."
    @JvmField var keyboardModePortrait = 0
    @JvmField var keyboardModeLandscape = 2
    @JvmField var compactModeEnabled = true
    @JvmField var ctrlAOverride = 0
    @JvmField var chordingCtrlKey = 0
    @JvmField var chordingAltKey = 0
    @JvmField var chordingMetaKey = 0
    @JvmField var keyClickVolume = 0.0f
    @JvmField var keyClickMethod = 0
    @JvmField var capsLock = true
    @JvmField var shiftLockModifiers = false

    // Read by CandidateView
    @JvmField var candidateScalePref = 1.0f

    // Legacy view settings
    @JvmField var showTouchPos = false
    @JvmField var labelScalePref = 1.0f
    @JvmField var sendSlideKeys = 0

    // Updated by LatinIME, read by KeyboardSwitcher
    @JvmField var keyboardMode = 0
    @JvmField var useExtension = false
    @JvmField var keyboardHeightPercent = 40.0f

    // Legacy view settings
    @JvmField var hintMode = 0
    @JvmField var renderMode = 1
    @JvmField var longpressTimeout = 400

    // Cached editor info
    @JvmField var editorPackageName: String? = null
    @JvmField var editorFieldName: String? = null
    @JvmField var editorFieldId = 0
    @JvmField var editorInputType = 0

    // Updated by LanguageSwitcher
    @JvmField var inputLocale: java.util.Locale = java.util.Locale.getDefault()
}
