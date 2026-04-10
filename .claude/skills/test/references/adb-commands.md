# ADB Command Reference For DevKey Test Runs

Quick reference for ADB commands used by the shared DevKey test harness.
Commands use `$SERIAL` as a placeholder for the active device.

## Platform Workarounds

### Android 15+: screencap via pipe

```bash
MSYS_NO_PATHCONV=1 adb -s $SERIAL exec-out screencap -p > ./local_file.png
```

### Git Bash path mangling

Commands that mention `/sdcard/` need `MSYS_NO_PATHCONV=1`.

## Build And Install

```bash
./gradlew assembleDebug
adb -s $SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb install -r` does not guarantee a fresh IME process. Re-establish IME state
intentionally after install.

## IME Setup

```bash
adb -s $SERIAL shell am force-stop dev.devkey.keyboard
adb -s $SERIAL shell ime enable dev.devkey.keyboard/.LatinIME
adb -s $SERIAL shell ime set dev.devkey.keyboard/.LatinIME
```

## Calibration

Preferred order:

1. `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`
2. cached calibration data
3. harness fallback logic

Use the harness and shared calibration data before inventing new coordinate
flows.
