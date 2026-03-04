# Active Defects

Max 7 active. Oldest rotates to `.claude/logs/defects-archive.md`.

## Active Patterns

### [BUILD] 2026-03-03: Compose UI test deps incompatible with API 36 (Android 16)
**Pattern**: Espresso uses reflection on `android.hardware.input.InputManager.getInstance()` which was removed/changed in API 36. All 14 Compose UI instrumented tests fail with `NoSuchMethodException` on API 36 emulator.
**Prevention**: Upgrade `espresso-core` and `compose-ui-test-junit4` to versions supporting API 36. Check Compose BOM release notes for Android 16 compatibility.
**Ref**: @app/build.gradle.kts (androidTestImplementation deps), Compose BOM 2024.06.00

### [BUILD] 2026-03-03: connectedAndroidTest uninstalls the app from emulator
**Pattern**: Running `./gradlew connectedAndroidTest` installs the test APK and then uninstalls the app after tests complete. This removes the DevKey IME from the device, requiring full reinstall + `ime enable` + `ime set`.
**Prevention**: After running `connectedAndroidTest`, always reinstall the debug APK and re-enable/re-set the IME. Consider using `--no-uninstall` flag if available.
**Ref**: @app/build.gradle.kts

### [IME] 2026-03-03: IME ComposeView decor lifecycle must match ComposeView lifecycle — FIXED Session 27
**Pattern**: `ComposeKeyboardViewFactory` created separate lifecycle owners for the decor view and the ComposeView, then DESTROYED the decor's owner. Compose's `WindowRecomposer` resolves from the root/decor view — finding a DESTROYED lifecycle, it shut down. Initial composition worked (forced by `setContent`), but NO state change EVER triggered recomposition. This silently broke ALL reactive UI: mode switching, animations, etc.
**Fix**: Single `create(context, actionListener, decorView)` method shares one RESUMED lifecycle owner on both decor and ComposeView.
**Prevention**: In IME Compose hosting, ALWAYS share one lifecycle owner between the decor view and ComposeView. Never destroy the decor's lifecycle owner independently. The WindowRecomposer resolves from the root view, not the ComposeView.
**Status**: FIXED — E2E verified (123 toggle, ABC toggle, Caps Lock all pass).
**Ref**: @ComposeKeyboardViewFactory.kt:create, @LatinIME.kt:onCreateInputView

### [IME] 2026-03-02: PluginManager loads untrusted packages without signature verification
**Pattern**: `getSoftKeyboardDictionaries` and `getLegacyDictionaries` load binary dictionary resources from any installed package that declares a matching intent filter. No signature verification — a malicious app could serve crafted dictionaries exploiting native C++ code.
**Prevention**: Add package signature verification before loading resources from third-party packages. Check signing certificate against an allowlist.
**Ref**: @PluginManager.kt:getSoftKeyboardDictionaries, @PluginManager.kt:getLegacyDictionaries

### [IME] 2026-03-02: ChordeTracker still used for Symbol/Fn modifier state
**Pattern**: After Phase 7b unified Shift/Ctrl/Alt/Meta into `ModifierStateManager`, the old `ModifierKeyState` class (renamed `ChordeTracker`) still tracks Symbol and Fn key chording separately in LatinIME. Two parallel state tracking mechanisms remain.
**Prevention**: Consider merging Symbol/Fn into ModifierStateManager in a future cleanup pass.
**Ref**: @ChordeTracker.kt, @LatinIME.kt

### [ANDROID] 2026-03-01: KeyCodes use ASCII values, not Android KeyEvent constants
**Pattern**: The LatinIME backend's `onKey()` switch operates on ASCII codepoints (Tab=9, Enter=10), not Android `KeyEvent.KEYCODE_*` constants (Tab=61). Using the wrong value silently sends the wrong character (e.g., 61 sends `=` instead of tab).
**Prevention**: Always verify keycode values against LatinIME.onKey() switch cases and keycodes.xml. The `KeyCodes` object in KeyData.kt is the single source of truth for Compose keyboard keycodes.
**Ref**: @KeyData.kt:KeyCodes, @LatinIME.kt:onKey

### [ANDROID] 2026-03-01: PendingIntent requires FLAG_IMMUTABLE on API 31+
**Pattern**: `PendingIntent.getBroadcast(..., 0)` with flags=0 crashes on API 31+ (targetSdk 34). Similarly, `registerReceiver()` without RECEIVER_NOT_EXPORTED crashes on API 34+.
**Prevention**: Always pass `PendingIntent.FLAG_IMMUTABLE` (or FLAG_MUTABLE if needed). Always pass `Context.RECEIVER_NOT_EXPORTED` for internal receivers.
**Ref**: @LatinIME.kt:showNotification
