# Layout Redesign & Theme System Design

**Date**: 2026-03-01
**Session**: 20
**Status**: Approved

## Problem Statement

1. **No dedicated 0 key** — hidden behind Tab long-press (undiscoverable)
2. **Esc/Tab waste number row space** — power-user keys taking prime real estate
3. **No smart Backspace/Escape** — two keys for related functions
4. **Generic flat theme** — no visual identity, no modifier differentiation
5. **Single layout mode** — no adaptation for different user contexts

## Confirmed Layout: #8 Hybrid

### Full Layout (6 rows, 216dp total height budget)

```
Row 0 (34dp)  [`] [1] [2] [3] [4] [5] [6] [7] [8] [9] [0]
Row 1 (38dp)  [q] [w] [e] [r] [t] [y] [u] [i] [o] [p]
Row 2 (38dp)  [a] [s] [d] [f] [g] [h] [j] [k] [l]
Row 3 (38dp)  [Shift] [z] [x] [c] [v] [b] [n] [m] [⌫/Esc]
Row 4 (38dp)  [123] [☺] [,] [     Space     ] [.] [Enter]
Row 5 (30dp)  [Ctrl] [Alt] [Tab]  ___  [←] [↓] [↑] [→]
```

### Key Changes from Current

| Change | Before | After |
|--------|--------|-------|
| Number row | Esc 1-9 Tab | ` 1-9 0 |
| 0 key | Hidden (Tab long-press) | Dedicated visible key |
| Esc | Dedicated key in number row | Smart: ⌫ when text, Esc when empty |
| Tab | Dedicated key in number row | Utility row |
| Backspace | Simple delete | Smart ⌫/Esc dual-function |
| Spacebar row | Ctrl Alt Space arrows Enter | 123 ☺ , Space . Enter |
| Utility row | N/A | New thin row: Ctrl Alt Tab _ ← ↓ ↑ → |
| Row count | 5 | 6 (same total height via shrink) |

### Row Height Budget

| Row | Current | New | Delta |
|-----|---------|-----|-------|
| Number | 44dp | 34dp | -10dp |
| QWERTY | 44dp | 38dp | -6dp |
| Home | 44dp | 38dp | -6dp |
| Z-row | 44dp | 38dp | -6dp |
| Spacebar | 44dp | 38dp | -6dp |
| Utility | N/A | 30dp | +30dp |
| **Total** | **220dp** | **216dp** | **-4dp** |

### Smart Backspace/Esc Behavior

| Context | Tap | Long-press |
|---------|-----|------------|
| Text in field (cursor > 0) | Backspace | Escape |
| Empty field (cursor = 0) | Escape | Escape |
| Text selected | Backspace (delete selection) | Escape |

Implementation: Check `getCurrentInputConnection().getExtractedText()` cursor position and selection before deciding which keycode to send.

## Keyboard Modes

### Mode 1: Compact (SwiftKey-style)

```
[q] [w] [e] [r] [t] [y] [u] [i] [o] [p]
  [a] [s] [d] [f] [g] [h] [j] [k] [l]
[Shift] [z] [x] [c] [v] [b] [n] [m] [⌫]
[123] [☺] [,] [     Space     ] [.] [Enter]
```

- 4 rows, no number row, no utility row
- Clean, familiar, zero learning curve
- Default for new users

### Mode 2: Compact Dev

```
[q¹] [w²] [e³] [r⁴] [t⁵] [y⁶] [u⁷] [i⁸] [o⁹] [p⁰]
  [a~] [s|] [d\] [f{] [g}] [h[] [j]] [k"] [l']
[Shift] [z;] [x:] [c/] [v?] [b<] [n>] [m_] [⌫/Esc]
[123] [☺] [,] [     Space     ] [.] [Enter]
```

- 4 rows, numbers on long-press of QWERTY row
- Dev symbols on long-press of home/Z rows
- Smart ⌫/Esc enabled
- For developers who want compact + quick number/symbol access

### Mode 3: Full

- The 6-row layout described above
- All keys visible, dedicated number row, utility row
- For power users, terminal work, remote desktop

### Mode Switching

- Setting in preferences: Compact / Compact Dev / Full
- Smart app detection can auto-switch (see below)

## Smart App Detection

### Detected Apps

| Category | Package Examples |
|----------|----------------|
| Terminal | com.termux, org.connectbot, com.sonelli.juicessh |
| Remote Desktop | com.google.chromeremotedesktop, com.limelight (Moonlight), com.parsec.app, com.anydesk.anydeskandroid, com.teamviewer.*, com.microsoft.rdc.* |
| Code Editors | com.visualstudio.code (VS Code), com.acode.*, com.dcoder.* |

### Behavior When Detected

When a detected app is in foreground:

1. **Auto-switch to Full mode** (if user is in Compact/Compact Dev)
2. **Esc key appears as dedicated key** in utility row (replaces gap/spacer)
3. **Fn modifier available**: Fn+1=F1 through Fn+0=F10, Fn+hyphen=F11, Fn+equals=F12
4. **Ctrl+Alt+Del combo** available via long-press on Ctrl

### Detection Method

Use `getCurrentInputEditorInfo().packageName` in `onStartInput()` to check against a configurable allowlist stored in SharedPreferences. Users can add/remove apps from settings.

## Theme System: Design Tokens

### Aesthetic

SwiftKey dark minimal: flat keys, dark rounded rectangles, clean white text, subtle long-press hints. No 3D, no bevels, no shadows. Modifier keys use **Option A: Teal Monochrome Gradient** — single hue at different intensities.

### Surface Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `kbBg` | `#1b1b1f` | Keyboard background |
| `keyBg` | `#2c2c31` | Letter key background |
| `keyBgSpecial` | `#232327` | 123, emoji key background |
| `keyPressed` | `#3a3a40` | Key background on press |

### Modifier Color Tokens (Option A: Teal Monochrome)

| Token | Value | Usage |
|-------|-------|-------|
| `modBgShift` | `#1a3538` | Shift key background |
| `modBgAction` | `#1a3538` | Backspace/Esc background |
| `modBgEnter` | `#1a3d40` | Enter key background |
| `modBgToggle` | `#1e2a2c` | 123, emoji backgrounds |
| `modBgSysmod` | `#172628` | Ctrl, Alt, Tab backgrounds |
| `modBgNav` | `#1a2425` | Arrow key backgrounds |
| `modTextShift` | `#5ec4cc` | Shift text |
| `modTextAction` | `#5ec4cc` | Backspace/Esc text |
| `modTextEnter` | `#6ed4dc` | Enter text |
| `modTextToggle` | `#5aabb2` | 123, emoji text |
| `modTextSysmod` | `#4a9aa0` | Ctrl, Alt, Tab text |
| `modTextNav` | `#3d8388` | Arrow key text |

### Text Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `keyText` | `#e8e8ec` | Letter key text |
| `keyTextSpecial` | `#a0a0a8` | Special key text |
| `keyHint` | `#5a5a62` | Long-press hint text |

### Spacing Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `keyGap` | `4dp` | Gap between keys |
| `keyRadius` | `6dp` | Key corner radius |
| `kbPadH` | `4dp` | Keyboard horizontal padding |
| `kbPadV` | `5dp` | Keyboard vertical padding |

### Typography Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `fontKey` | `14sp` | Letter key label |
| `fontKeySpecial` | `11sp` | Multi-char special key label |
| `fontKeyHint` | `9sp` | Long-press hint |
| `fontKeyUtility` | `10sp` | Utility row labels |
| `fontFamily` | System default (Roboto) | All text |

### Animation Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `pressDownDuration` | `40ms` | Press-in animation |
| `pressUpDuration` | `180ms` | Release animation |
| `pressEase` | `cubic-bezier(0.2, 0, 0, 1)` | Release easing curve |
| `pressScale` | `0.92` | Scale factor on press |

### Token Implementation

Tokens map to Compose:

```kotlin
object DevKeyTheme {
    // Surface
    val kbBg = Color(0xFF1B1B1F)
    val keyBg = Color(0xFF2C2C31)
    val keyPressed = Color(0xFF3A3A40)

    // Modifier colors (Teal Monochrome)
    val modBgShift = Color(0xFF1A3538)
    val modBgEnter = Color(0xFF1A3D40)
    val modBgSysmod = Color(0xFF172628)
    val modBgNav = Color(0xFF1A2425)

    // Spacing
    val keyGap = 4.dp
    val keyRadius = 6.dp

    // Animation
    val pressSpec = tween<Float>(
        durationMillis = 180,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    )
}
```

## Long-Press Characters

### Number Row

| Key | Primary | Long-press |
|-----|---------|------------|
| ` | backtick | ~ (tilde) |
| 1-9 | digit | (none or F1-F9 in terminal mode) |
| 0 | digit | (none or F10 in terminal mode) |

### QWERTY Row

| Key | Long-press |
|-----|------------|
| q ! | w @ | e # | r $ | t % | y ^ | u & | i * | o ( | p ) |

### Home Row

| Key | Long-press |
|-----|------------|
| a ~ | s \| | d \\ | f { | g } | h [ | j ] | k " | l ' |

### Z Row

| Key | Long-press |
|-----|------------|
| z ; | x : | c / | v ? | b < | n > | m _ |
