# DevKey User Guide

## Getting Started

1. Install DevKey from the Play Store or sideload the APK.
2. Go to **Settings > System > Languages & Input > On-screen keyboard**.
3. Enable **DevKey**.
4. Open any text field and switch to DevKey using the keyboard switcher.

## Typing

- **Letters**: Tap to type. The keyboard uses a standard QWERTY layout.
- **Long-press**: Hold any letter key to type the secondary character shown in the top-right hint. For example, long-press `Q` to type `!`.
- **Numbers**: The top row shows numbers 1-9 with Esc and Tab at the ends.
- **Symbols**: The home row (A-L) and Z row have symbols on long-press: `~`, `|`, `\`, `{`, `}`, `[`, `]`, `"`, `'`, `;`, `:`, `/`, `?`, `<`, `>`, `_`.

## Modifier Keys

- **Shift**: Tap once for one-shot shift (next letter uppercase). Double-tap for Caps Lock (indicated by a dot below the key). Tap again to turn off.
- **Ctrl**: Hold for shortcut mode -- the keyboard highlights available shortcuts (Copy, Paste, Undo, Select All, etc.). Release to exit Ctrl mode. Tap for one-shot Ctrl (next key sends Ctrl+key).
- **Alt**: Tap for one-shot Alt mode.
- **Arrow keys**: Located on the bottom row for cursor navigation. Left and Right are repeatable (hold to repeat).

## Macros

Macros let you record and replay key sequences.

- **Quick access**: Tap the lightning bolt icon in the toolbar to show macro chips.
- **Long-press lightning bolt**: Opens the full macro grid with edit/delete options.
- **Recording**: Tap "+ Record" from the chips or grid. Type your key sequence, then tap "Stop". Name your macro and save.
- **Replaying**: Tap any macro chip or grid item to replay its key sequence.
- **Managing**: Go to **Settings > Macros > Manage Macros** to rename or delete macros.

## Command Mode

DevKey automatically detects terminal apps (Termux, ConnectBot, JuiceSH, etc.) and switches to command mode, which adjusts predictions for shell commands.

- **Auto-detection**: Enabled by default. Toggle in **Settings > Command Mode > Auto-Detect Terminals**.
- **Manual toggle**: Tap the overflow menu in the toolbar to toggle command mode for the current app.
- **App overrides**: Go to **Settings > Command Mode > Manage Pinned Apps** to force specific apps into command or normal mode.

## Voice Input

- Tap the microphone icon in the toolbar to start voice input.
- Speak naturally. Voice input auto-stops after detecting silence.
- Tap "Stop" to end recording early and process the audio.
- Tap "Cancel" to discard the recording.
- Voice recognition uses on-device Whisper (no internet required).

## Compact Mode

Compact mode hides the number row for a more compact keyboard.

- Enable in **Settings > Keyboard View > Compact Mode**.
- In compact mode, long-press on the QWERTY row types digits (Q=1, W=2, ..., P=0).
- Long-press Shift types Esc. Long-press Backspace types Tab.

## Clipboard

- Tap the clipboard icon in the toolbar to open the clipboard panel.
- Recent clipboard entries are shown with timestamps.
- Tap an entry to paste it.
- Pin important entries so they persist when older entries are cleared.

## Backup & Restore

- **Export**: Go to **Settings > Backup > Export Data**. Choose a location to save the JSON backup file (named `devkey-backup-YYYY-MM-DD.json`).
- **Import**: Go to **Settings > Backup > Import Data**. Select a backup file. Choose "Replace All" (overwrites existing data) or "Merge" (keeps existing, adds new).

## Settings

Access settings via:
- Toolbar overflow menu > Settings
- Android Settings > keyboard settings for DevKey

### Settings Categories

1. **Keyboard View**: Height, mode, compact mode, suggestions, hint mode, label scale, keyboard layout, render mode
2. **Key Behavior**: Auto-cap, caps lock, shift lock modifiers, Ctrl+A override, chording, slide keys
3. **Actions**: Swipe gestures (up/down/left/right), volume key behavior
4. **Feedback**: Vibrate, sound, click method, popup preview
5. **Prediction & Autocorrect**: Quick fixes, suggestions, auto-complete, suggested punctuation, autocorrect level
6. **Macros**: Display mode (chips/grid), manage macros
7. **Voice Input**: Voice model selection, auto-stop timeout
8. **Command Mode**: Auto-detect toggle, manage pinned apps
9. **Backup**: Export and import data
10. **About**: Version info, attribution
