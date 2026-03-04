# ADB Command Reference for Test Agents

Quick reference for ADB commands used by test-wave-agent during automated keyboard testing. Commands use `$SERIAL` placeholder — replace with actual serial (e.g., `emulator-5554`).

## CRITICAL: Platform Workarounds

These workarounds are MANDATORY. Using the standard commands will FAIL.

### Android 15+: screencap broken
`adb shell screencap -p /sdcard/file.png` **FAILS** on Android 15+. Use pipe instead:
```bash
MSYS_NO_PATHCONV=1 adb -s $SERIAL exec-out screencap -p > ./local_file.png
```

### Git Bash: /sdcard/ path mangling
Git Bash rewrites `/sdcard/` to `C:/Program Files/Git/sdcard/`. **ALL** ADB commands with `/sdcard/` paths MUST be prefixed:
```bash
MSYS_NO_PATHCONV=1 adb -s $SERIAL shell screencap -p /sdcard/file.png
MSYS_NO_PATHCONV=1 adb -s $SERIAL pull /sdcard/file.png ./local.png
```

---

## Device Management

### Check connected devices
```bash
adb devices
```
Expected output: A line with a device serial and `device` status. If `unauthorized`, the device needs USB debugging approval.

### Get device info
```bash
adb -s $SERIAL shell getprop ro.product.model          # Device model (e.g., "sdk_gphone64_x86_64")
adb -s $SERIAL shell getprop ro.build.version.release   # Android version (e.g., "16")
adb -s $SERIAL shell getprop ro.build.version.sdk       # SDK level (e.g., "36")
adb -s $SERIAL shell wm size                            # Screen resolution (e.g., "1080x2400")
adb -s $SERIAL shell wm density                         # Density (e.g., "420")
```

## IME Installation & Setup

### Build debug APK
```bash
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Install APK
```bash
adb -s $SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```
- `-r` replaces existing installation
- **CRITICAL**: `adb install -r` does NOT restart the IME process. Always force-stop after install.

### Set up IME (REQUIRED after every install)
```bash
adb -s $SERIAL shell am force-stop dev.devkey.keyboard
adb -s $SERIAL shell ime enable dev.devkey.keyboard/.LatinIME
adb -s $SERIAL shell ime set dev.devkey.keyboard/.LatinIME
```

### Verify IME is active
```bash
adb -s $SERIAL shell ime list -s | grep dev.devkey.keyboard
```

### Check if keyboard process is running
```bash
adb -s $SERIAL shell pidof dev.devkey.keyboard
```
Returns PID if running, empty if not.

## Opening a Text Input Context

The keyboard only appears when a text field has focus. Use the Contacts app as a reliable test surface:

```bash
# Open Contacts "Add contact" screen
adb -s $SERIAL shell am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact
sleep 2
# Tap "First name" field to bring up keyboard
adb -s $SERIAL shell input tap 350 650
sleep 1
```

Alternative: use Settings search field or any app with a text input.

## UI Interaction

### Tap at coordinates
```bash
adb -s $SERIAL shell input tap X Y
```
Where X,Y are pixel coordinates. For keyboard keys, use coordinates from the key map with Y offset applied.

### Long press at coordinates
```bash
adb -s $SERIAL shell input swipe X Y X Y 500
```
Same start and end point with 500ms duration simulates long press. Used for long-press symbols on COMPACT_DEV mode keys.

### Type text (via ADB, not keyboard)
```bash
adb -s $SERIAL shell input text "hello"
```
- `%s` = space (spaces must be encoded)
- This bypasses the keyboard entirely — use for setting up test state, NOT for testing keyboard input

### Key events (system-level)
```bash
adb -s $SERIAL shell input keyevent KEYCODE_BACK        # Back button
adb -s $SERIAL shell input keyevent KEYCODE_HOME        # Home button
adb -s $SERIAL shell input keyevent KEYCODE_ENTER       # Enter/Return
adb -s $SERIAL shell input keyevent KEYCODE_TAB         # Tab (moves focus)
adb -s $SERIAL shell input keyevent KEYCODE_DEL         # Backspace/Delete
adb -s $SERIAL shell input keyevent KEYCODE_ESCAPE      # Escape
```

**WARNING**: System-level key events (e.g., `KEYCODE_TAB`) may move focus away from the text field, closing the keyboard. Use keyboard tap coordinates instead for testing keyboard-generated key events.

### Scroll / Swipe
```bash
# Swipe up (scroll down)
adb -s $SERIAL shell input swipe 540 1500 540 500 300

# Swipe down (scroll up)
adb -s $SERIAL shell input swipe 540 500 540 1500 300
```
Format: `input swipe startX startY endX endY durationMs`

## Screenshots

### Capture screenshot (Android 15+ safe)
```bash
MSYS_NO_PATHCONV=1 adb -s $SERIAL exec-out screencap -p > ./screenshots/step_1.png
```
**DO NOT** use `adb shell screencap -p /sdcard/...` — it fails on Android 15+.

### Legacy capture + pull (Android 14 and below only)
```bash
MSYS_NO_PATHCONV=1 adb -s $SERIAL shell screencap -p /sdcard/screenshot.png
MSYS_NO_PATHCONV=1 adb -s $SERIAL pull /sdcard/screenshot.png ./local_path/screenshot.png
```

### Cleanup device screenshots
```bash
MSYS_NO_PATHCONV=1 adb -s $SERIAL shell rm /sdcard/screenshot.png
```

## Logcat (Primary Verification Method — MANDATORY)

**Check logcat after EVERY ADB interaction.** This catches IME errors, state drift, and transient issues.

DevKey uses specific logcat tags for test observability:

| Tag | Purpose |
|-----|---------|
| `DevKeyPress` | Key press events: keycode, label, modifier state, type |
| `DevKeyMode` | Mode transitions: toggleMode, setMode (Normal/Symbols) |
| `DevKeyMap` | Key coordinate map dump (debug builds only) |
| `DevKeyBridge` | Bridge events: onKey forwarded to LatinIME |

### Clear logcat buffer (do before each flow)
```bash
adb -s $SERIAL logcat -c
```

### Capture logcat for a specific tag
```bash
adb -s $SERIAL logcat -d -s DevKeyPress    # Key press events
adb -s $SERIAL logcat -d -s DevKeyMode     # Mode transitions
adb -s $SERIAL logcat -d -s DevKeyMap      # Key map coordinates
```

### Filter logcat with regex
```bash
adb -s $SERIAL logcat -d -s DevKeyPress | grep -E "ModifierTransition.*SHIFT"
adb -s $SERIAL logcat -d -s DevKeyMode | grep -E "toggleMode.*Normal.*Symbols"
```

### Recent warnings/errors
```bash
adb -s $SERIAL logcat -d -t "60" *:W
```

### Assert logcat contains pattern (shell one-liner)
```bash
timeout 5 adb -s $SERIAL logcat -s DevKeyPress | grep -m 1 "tap.*code=104"
```
Exit code 0 = found, non-zero = timeout (assertion failed).

## Screen State

### Check if screen is on
```bash
adb -s $SERIAL shell dumpsys power | grep "Display Power"
```

### Wake screen
```bash
adb -s $SERIAL shell input keyevent KEYCODE_WAKEUP
```

### Unlock screen (swipe up)
```bash
adb -s $SERIAL shell input swipe 540 1800 540 800 300
```

## Common Patterns for Test Agents

### Keyboard tap + verify pattern
```
1. Clear logcat
2. Tap key at (X, Y) with Y offset applied
3. Sleep 500ms
4. Read logcat for DevKeyPress tag
5. Assert expected keycode/label in output
```

### Modifier key test pattern
```
1. Clear logcat
2. Tap modifier key (Shift/Ctrl/Alt) at coordinates
3. Sleep 200ms
4. Read logcat for ModifierTransition
5. Assert expected state transition (e.g., OFF → ONE_SHOT)
6. Tap a letter key
7. Assert modifier was consumed (ONE_SHOT → OFF)
```

### Mode switch test pattern
```
1. Clear logcat
2. Tap 123 key at coordinates
3. Sleep 500ms
4. Read logcat for DevKeyMode tag
5. Assert "toggleMode: Normal -> Symbols"
6. Take screenshot for visual verification
```

### App crash detection pattern
```
1. adb -s $SERIAL shell pidof dev.devkey.keyboard
2. If empty: IME process crashed
3. Attempt recovery: ime set dev.devkey.keyboard/.LatinIME
4. Re-open text field to trigger keyboard display
5. If recovery fails after 3 attempts: mark flow as FAIL
```

### Screenshot + logcat verification pattern
```
1. Sleep 1-2s (wait for UI to settle)
2. MSYS_NO_PATHCONV=1 adb -s $SERIAL exec-out screencap -p > ./screenshots/flow_step_N.png
3. adb -s $SERIAL logcat -d -t 5 *:W 2>/dev/null | tail -30
4. Read screenshot with vision for visual verification
5. Check logcat output for errors
```
