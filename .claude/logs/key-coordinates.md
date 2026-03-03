# DevKey Keyboard Key Coordinates

Generated: 2026-03-02
Device: 1080x2400 emulator, density 2.625 (420dpi xxhdpi)

## Screen Geometry

| Parameter | Value |
|-----------|-------|
| Screen | 1080 x 2400 px |
| Density | 2.625 (420dpi) |
| Screen height dp | 914.29 dp |
| Height percent (portrait default) | 40% |
| Toolbar height | 36dp = 94.5px |
| Key area height | 329.71dp = 865.5px |
| Total keyboard height | 960px |
| Keyboard top Y | 1440 px |
| Toolbar Y range | 1440 - 1535 px |
| Key area Y range | 1535 - 2400 px |

### Spacing (in px)
- Horizontal padding: 10.5px (4dp)
- Row gap: 10.5px (4dp)
- Key gap: 10.5px (4dp)
- Available width: 1080 - 2*10.5 = 1059px

---

## COMPACT Mode (Default - 4 rows)

**Row weights:** 42, 42, 42, 42 (equal)
**Available height:** 865.5 - 3*10.5 = 834px
**Row height each:** 834 / 4 = 208.5px

### Row 0: QWERTY (y: 0 to 208.5, abs y: 1535 to 1743)
10 keys, all weight 1.0. Total weight = 10. Key gaps = 9 * 10.5 = 94.5.
Weighted width = 1059 - 94.5 = 964.5. Each key = 96.45px.
Row center Y (abs): 1535 + 104.25 = 1639

| Key | Code | Tap X | Tap Y | ADB Command |
|-----|------|-------|-------|-------------|
| q | 113 | 59 | 1639 | `adb shell input tap 59 1639` |
| w | 119 | 166 | 1639 | `adb shell input tap 166 1639` |
| e | 101 | 273 | 1639 | `adb shell input tap 273 1639` |
| r | 114 | 380 | 1639 | `adb shell input tap 380 1639` |
| t | 116 | 487 | 1639 | `adb shell input tap 487 1639` |
| y | 121 | 594 | 1639 | `adb shell input tap 594 1639` |
| u | 117 | 700 | 1639 | `adb shell input tap 700 1639` |
| i | 105 | 807 | 1639 | `adb shell input tap 807 1639` |
| o | 111 | 914 | 1639 | `adb shell input tap 914 1639` |
| p | 112 | 1021 | 1639 | `adb shell input tap 1021 1639` |

### Row 1: Home Row (y: 219 to 427.5, abs y: 1754 to 1962)
9 keys, all weight 1.0. Total weight = 9. Key gaps = 8 * 10.5 = 84.
Weighted width = 1059 - 84 = 975. Each key = 108.33px.
Row center Y (abs): 1754 + 104.25 = 1858

| Key | Code | Tap X | Tap Y | ADB Command |
|-----|------|-------|-------|-------------|
| a | 97 | 65 | 1858 | `adb shell input tap 65 1858` |
| s | 115 | 183 | 1858 | `adb shell input tap 183 1858` |
| d | 100 | 302 | 1858 | `adb shell input tap 302 1858` |
| f | 102 | 421 | 1858 | `adb shell input tap 421 1858` |
| g | 103 | 540 | 1858 | `adb shell input tap 540 1858` |
| h | 104 | 659 | 1858 | `adb shell input tap 659 1858` |
| j | 106 | 778 | 1858 | `adb shell input tap 778 1858` |
| k | 107 | 897 | 1858 | `adb shell input tap 897 1858` |
| l | 108 | 1016 | 1858 | `adb shell input tap 1016 1858` |

### Row 2: Z Row (y: 438 to 646.5, abs y: 1973 to 2181)
9 keys: Shift(1.5), z-m(7x1.0), Del(1.5). Total weight = 10. Key gaps = 8 * 10.5 = 84.
Weighted width = 1059 - 84 = 975. Per unit weight = 97.5px.
Shift width = 146.25, letter width = 97.5, Del width = 146.25.
Row center Y (abs): 1973 + 104.25 = 2077

| Key | Code | Tap X | Tap Y | ADB Command |
|-----|------|-------|-------|-------------|
| Shift | -1 | 84 | 2077 | `adb shell input tap 84 2077` |
| z | 122 | 216 | 2077 | `adb shell input tap 216 2077` |
| x | 120 | 324 | 2077 | `adb shell input tap 324 2077` |
| c | 99 | 432 | 2077 | `adb shell input tap 432 2077` |
| v | 118 | 540 | 2077 | `adb shell input tap 540 2077` |
| b | 98 | 648 | 2077 | `adb shell input tap 648 2077` |
| n | 110 | 756 | 2077 | `adb shell input tap 756 2077` |
| m | 109 | 864 | 2077 | `adb shell input tap 864 2077` |
| Del | -5 | 997 | 2077 | `adb shell input tap 997 2077` |

### Row 3: Space Row (y: 657 to 865.5, abs y: 2192 to 2400)
6 keys: 123(1.0), Emoji(0.8), comma(0.8), Space(4.0), period(0.8), Enter(1.6). Total weight = 9.0.
Key gaps = 5 * 10.5 = 52.5. Weighted width = 1059 - 52.5 = 1006.5. Per unit = 111.83px.
Row center Y (abs): 2192 + 104.25 = 2296

| Key | Code | Weight | Width | Tap X | Tap Y | ADB Command |
|-----|------|--------|-------|-------|-------|-------------|
| 123 | -2 | 1.0 | 111.8 | 66 | 2296 | `adb shell input tap 66 2296` |
| Emoji | -300 | 0.8 | 89.5 | 177 | 2296 | `adb shell input tap 177 2296` |
| , | 44 | 0.8 | 89.5 | 277 | 2296 | `adb shell input tap 277 2296` |
| Space | 32 | 4.0 | 447.3 | 556 | 2296 | `adb shell input tap 556 2296` |
| . | 46 | 0.8 | 89.5 | 835 | 2296 | `adb shell input tap 835 2296` |
| Enter | 10 | 1.6 | 178.9 | 984 | 2296 | `adb shell input tap 984 2296` |

---

## FULL Mode (6 rows)

**Row weights:** 34, 38, 38, 38, 38, 30 (total = 216)
**Available height:** 865.5 - 5*10.5 = 813px
**Row heights:**
- Row 0 (Number): 813 * 34/216 = 127.9px
- Row 1 (QWERTY): 813 * 38/216 = 143.0px
- Row 2 (Home): 813 * 38/216 = 143.0px
- Row 3 (Z): 813 * 38/216 = 143.0px
- Row 4 (Space): 813 * 38/216 = 143.0px
- Row 5 (Utility): 813 * 30/216 = 112.9px

### Row 0: Number Row (abs y: 1535 to 1663)
11 keys, all weight 1.0. Total weight = 11. Key gaps = 10 * 10.5 = 105.
Weighted width = 1059 - 105 = 954. Each key = 86.7px.
Row center Y (abs): 1535 + 64.0 = 1599

| Key | Code | Tap X | Tap Y | ADB Command |
|-----|------|-------|-------|-------------|
| ` | 96 | 54 | 1599 | `adb shell input tap 54 1599` |
| 1 | 49 | 151 | 1599 | `adb shell input tap 151 1599` |
| 2 | 50 | 248 | 1599 | `adb shell input tap 248 1599` |
| 3 | 51 | 346 | 1599 | `adb shell input tap 346 1599` |
| 4 | 52 | 443 | 1599 | `adb shell input tap 443 1599` |
| 5 | 53 | 540 | 1599 | `adb shell input tap 540 1599` |
| 6 | 54 | 637 | 1599 | `adb shell input tap 637 1599` |
| 7 | 55 | 735 | 1599 | `adb shell input tap 735 1599` |
| 8 | 56 | 832 | 1599 | `adb shell input tap 832 1599` |
| 9 | 57 | 929 | 1599 | `adb shell input tap 929 1599` |
| 0 | 48 | 1027 | 1599 | `adb shell input tap 1027 1599` |

### Row 1: QWERTY Row (abs y: 1673 to 1816)
10 keys, all weight 1.0. Total weight = 10. Key gaps = 9 * 10.5 = 94.5.
Weighted width = 964.5. Each key = 96.45px.
Row center Y (abs): 1673 + 71.5 = 1745

| Key | Code | Tap X | Tap Y | ADB Command |
|-----|------|-------|-------|-------------|
| q | 113 | 59 | 1745 | `adb shell input tap 59 1745` |
| w | 119 | 166 | 1745 | `adb shell input tap 166 1745` |
| e | 101 | 273 | 1745 | `adb shell input tap 273 1745` |
| r | 114 | 380 | 1745 | `adb shell input tap 380 1745` |
| t | 116 | 487 | 1745 | `adb shell input tap 487 1745` |
| y | 121 | 594 | 1745 | `adb shell input tap 594 1745` |
| u | 117 | 700 | 1745 | `adb shell input tap 700 1745` |
| i | 105 | 807 | 1745 | `adb shell input tap 807 1745` |
| o | 111 | 914 | 1745 | `adb shell input tap 914 1745` |
| p | 112 | 1021 | 1745 | `adb shell input tap 1021 1745` |

### Row 2: Home Row (abs y: 1827 to 1970)
9 keys, all weight 1.0. Total weight = 9. Key gaps = 8 * 10.5 = 84.
Weighted width = 975. Each key = 108.33px.
Row center Y (abs): 1827 + 71.5 = 1899

| Key | Code | Tap X | Tap Y | ADB Command |
|-----|------|-------|-------|-------------|
| a | 97 | 65 | 1899 | `adb shell input tap 65 1899` |
| s | 115 | 183 | 1899 | `adb shell input tap 183 1899` |
| d | 100 | 302 | 1899 | `adb shell input tap 302 1899` |
| f | 102 | 421 | 1899 | `adb shell input tap 421 1899` |
| g | 103 | 540 | 1899 | `adb shell input tap 540 1899` |
| h | 104 | 659 | 1899 | `adb shell input tap 659 1899` |
| j | 106 | 778 | 1899 | `adb shell input tap 778 1899` |
| k | 107 | 897 | 1899 | `adb shell input tap 897 1899` |
| l | 108 | 1016 | 1899 | `adb shell input tap 1016 1899` |

### Row 3: Z Row (abs y: 1980 to 2123)
9 keys: Shift(1.5), z-m(7x1.0), Backspace(1.5). Total weight = 10.
Key gaps = 8 * 10.5 = 84. Weighted width = 975. Per unit = 97.5px.
Row center Y (abs): 1980 + 71.5 = 2052

| Key | Code | Tap X | Tap Y | ADB Command |
|-----|------|-------|-------|-------------|
| Shift | -1 | 84 | 2052 | `adb shell input tap 84 2052` |
| z | 122 | 216 | 2052 | `adb shell input tap 216 2052` |
| x | 120 | 324 | 2052 | `adb shell input tap 324 2052` |
| c | 99 | 432 | 2052 | `adb shell input tap 432 2052` |
| v | 118 | 540 | 2052 | `adb shell input tap 540 2052` |
| b | 98 | 648 | 2052 | `adb shell input tap 648 2052` |
| n | 110 | 756 | 2052 | `adb shell input tap 756 2052` |
| m | 109 | 864 | 2052 | `adb shell input tap 864 2052` |
| Backspace | -301 | 997 | 2052 | `adb shell input tap 997 2052` |

### Row 4: Space Row (abs y: 2134 to 2277)
6 keys: 123(1.0), Emoji(0.8), comma(0.8), Space(4.0), period(0.8), Enter(1.6). Total weight = 9.0.
Key gaps = 5 * 10.5 = 52.5. Weighted width = 1006.5. Per unit = 111.83px.
Row center Y (abs): 2134 + 71.5 = 2206

| Key | Code | Weight | Tap X | Tap Y | ADB Command |
|-----|------|--------|-------|-------|-------------|
| 123 | -2 | 1.0 | 66 | 2206 | `adb shell input tap 66 2206` |
| Emoji | -300 | 0.8 | 177 | 2206 | `adb shell input tap 177 2206` |
| , | 44 | 0.8 | 277 | 2206 | `adb shell input tap 277 2206` |
| Space | 32 | 4.0 | 556 | 2206 | `adb shell input tap 556 2206` |
| . | 46 | 0.8 | 835 | 2206 | `adb shell input tap 835 2206` |
| Enter | 10 | 1.6 | 984 | 2206 | `adb shell input tap 984 2206` |

### Row 5: Utility Row (abs y: 2287 to 2400)
8 keys: Ctrl(1.5), Alt(1.5), Tab(1.5), Spacer(1.0), ArrowLeft(1.2), ArrowDown(1.2), ArrowUp(1.2), ArrowRight(1.2). Total weight = 10.3.
Key gaps = 7 * 10.5 = 73.5. Weighted width = 1059 - 73.5 = 985.5. Per unit = 95.68px.
Row center Y (abs): 2287 + 56.4 = 2343

| Key | Code | Weight | Width | Tap X | Tap Y | ADB Command |
|-----|------|--------|-------|-------|-------|-------------|
| Ctrl | -113 | 1.5 | 143.5 | 82 | 2343 | `adb shell input tap 82 2343` |
| Alt | -57 | 1.5 | 143.5 | 236 | 2343 | `adb shell input tap 236 2343` |
| Tab | 9 | 1.5 | 143.5 | 391 | 2343 | `adb shell input tap 391 2343` |
| (spacer) | 0 | 1.0 | 95.7 | 593 | 2343 | N/A |
| ArrowLeft | -21 | 1.2 | 114.8 | 698 | 2343 | `adb shell input tap 698 2343` |
| ArrowDown | -20 | 1.2 | 114.8 | 823 | 2343 | `adb shell input tap 823 2343` |
| ArrowUp | -19 | 1.2 | 114.8 | 949 | 2343 | `adb shell input tap 949 2343` |
| ArrowRight | -22 | 1.2 | 114.8 | 1023 | 2343 | `adb shell input tap 1023 2343` |

---

## COMPACT_DEV Mode (4 rows)

Same geometry as COMPACT (4 equal rows, same row height 208.5px each).
Differences from COMPACT:
- QWERTY row keys have long-press for numbers (1-0)
- Home row shared with Full (has long-press symbols)
- Z row uses Smart Backspace (-301) instead of plain Del (-5)

### Row 0: QWERTY (abs center Y: 1639) - same X as COMPACT
Long-press keys: q->1, w->2, e->3, r->4, t->5, y->6, u->7, i->8, o->9, p->0

| Key | Code | LP Label | LP Code | Tap X | Tap Y |
|-----|------|----------|---------|-------|-------|
| q | 113 | 1 | 49 | 59 | 1639 |
| w | 119 | 2 | 50 | 166 | 1639 |
| e | 101 | 3 | 51 | 273 | 1639 |
| r | 114 | 4 | 52 | 380 | 1639 |
| t | 116 | 5 | 53 | 487 | 1639 |
| y | 121 | 6 | 54 | 594 | 1639 |
| u | 117 | 7 | 55 | 700 | 1639 |
| i | 105 | 8 | 56 | 807 | 1639 |
| o | 111 | 9 | 57 | 914 | 1639 |
| p | 112 | 0 | 48 | 1021 | 1639 |

### Row 1: Home (abs center Y: 1858) - same X as COMPACT Home
Long-press keys: a->~, s->|, d->\\, f->{, g->}, h->[, j->], k->", l->'

### Row 2: Z Row (abs center Y: 2077) - same X as COMPACT Z
Smart Backspace (-301) replaces Del (-5) at same position (997, 2077).

### Row 3: Space Row (abs center Y: 2296) - identical to COMPACT Space

---

## Quick Reference: ADB Tap Commands (COMPACT mode)

### Alphabet Keys
```bash
# Row 0: QWERTY
adb shell input tap 59 1639    # q
adb shell input tap 166 1639   # w
adb shell input tap 273 1639   # e
adb shell input tap 380 1639   # r
adb shell input tap 487 1639   # t
adb shell input tap 594 1639   # y
adb shell input tap 700 1639   # u
adb shell input tap 807 1639   # i
adb shell input tap 914 1639   # o
adb shell input tap 1021 1639  # p

# Row 1: Home
adb shell input tap 65 1858    # a
adb shell input tap 183 1858   # s
adb shell input tap 302 1858   # d
adb shell input tap 421 1858   # f
adb shell input tap 540 1858   # g
adb shell input tap 659 1858   # h
adb shell input tap 778 1858   # j
adb shell input tap 897 1858   # k
adb shell input tap 1016 1858  # l

# Row 2: Z row letters
adb shell input tap 216 2077   # z
adb shell input tap 324 2077   # x
adb shell input tap 432 2077   # c
adb shell input tap 540 2077   # v
adb shell input tap 648 2077   # b
adb shell input tap 756 2077   # n
adb shell input tap 864 2077   # m
```

### Special Keys
```bash
adb shell input tap 84 2077    # Shift
adb shell input tap 997 2077   # Del/Backspace
adb shell input tap 556 2296   # Space
adb shell input tap 984 2296   # Enter
adb shell input tap 277 2296   # Comma
adb shell input tap 835 2296   # Period
adb shell input tap 66 2296    # 123 (Symbols toggle)
adb shell input tap 177 2296   # Emoji
```

### Quick Reference: ADB Tap Commands (FULL mode extras)
```bash
# Number row
adb shell input tap 54 1599    # ` (backtick)
adb shell input tap 151 1599   # 1
adb shell input tap 248 1599   # 2
adb shell input tap 346 1599   # 3
adb shell input tap 443 1599   # 4
adb shell input tap 540 1599   # 5
adb shell input tap 637 1599   # 6
adb shell input tap 735 1599   # 7
adb shell input tap 832 1599   # 8
adb shell input tap 929 1599   # 9
adb shell input tap 1027 1599  # 0

# Utility row
adb shell input tap 82 2343    # Ctrl
adb shell input tap 236 2343   # Alt
adb shell input tap 391 2343   # Tab
adb shell input tap 698 2343   # Arrow Left
adb shell input tap 823 2343   # Arrow Down
adb shell input tap 949 2343   # Arrow Up
adb shell input tap 1023 2343  # Arrow Right
```

---

## ADB Test Script

```bash
#!/bin/bash
# DevKey ADB Key Tap Test Script
# Tests all keys on the keyboard by tapping their calculated coordinates
# Device: 1080x2400, density 2.625, portrait mode
#
# Prerequisites:
#   - DevKey keyboard must be active and visible
#   - A text field must be focused
#   - Layout mode must be COMPACT (default)
#
# Usage: bash key-test.sh [mode]
#   mode: compact (default), full, compact_dev

set -euo pipefail

MODE="${1:-compact}"
DELAY=0.5  # seconds between taps
LOGCAT_TAG="DevKeyPress"

# Clear logcat
adb logcat -c

echo "=== DevKey Key Tap Test ==="
echo "Mode: $MODE"
echo "Delay: ${DELAY}s between taps"
echo ""

tap_key() {
    local label="$1"
    local x="$2"
    local y="$3"
    local expected_code="$4"

    echo -n "Tapping $label at ($x, $y)... "
    adb shell input tap "$x" "$y"
    sleep "$DELAY"

    # Check logcat for key event
    local result
    result=$(adb logcat -d -s "$LOGCAT_TAG" | tail -1)
    if echo "$result" | grep -q "code=$expected_code"; then
        echo "OK (code=$expected_code)"
    else
        echo "SENT (expected code=$expected_code, last log: $result)"
    fi
}

if [ "$MODE" = "compact" ] || [ "$MODE" = "compact_dev" ]; then

    echo "--- Row 0: QWERTY ---"
    tap_key "q" 59 1639 113
    tap_key "w" 166 1639 119
    tap_key "e" 273 1639 101
    tap_key "r" 380 1639 114
    tap_key "t" 487 1639 116
    tap_key "y" 594 1639 121
    tap_key "u" 700 1639 117
    tap_key "i" 807 1639 105
    tap_key "o" 914 1639 111
    tap_key "p" 1021 1639 112

    echo ""
    echo "--- Row 1: Home ---"
    tap_key "a" 65 1858 97
    tap_key "s" 183 1858 115
    tap_key "d" 302 1858 100
    tap_key "f" 421 1858 102
    tap_key "g" 540 1858 103
    tap_key "h" 659 1858 104
    tap_key "j" 778 1858 106
    tap_key "k" 897 1858 107
    tap_key "l" 1016 1858 108

    echo ""
    echo "--- Row 2: Z Row ---"
    tap_key "Shift" 84 2077 -1
    tap_key "z" 216 2077 122
    tap_key "x" 324 2077 120
    tap_key "c" 432 2077 99
    tap_key "v" 540 2077 118
    tap_key "b" 648 2077 98
    tap_key "n" 756 2077 110
    tap_key "m" 864 2077 109
    if [ "$MODE" = "compact" ]; then
        tap_key "Del" 997 2077 -5
    else
        tap_key "Backspace" 997 2077 -301
    fi

    echo ""
    echo "--- Row 3: Space Row ---"
    tap_key "123" 66 2296 -2
    # After 123, keyboard switches to symbols -- tap ABC to go back
    sleep 0.3
    # Symbols mode ABC button position (see Symbols section)
    # tap_key "ABC" 66 2206 -200  # uncomment if needed
    tap_key "Emoji" 177 2296 -300
    tap_key "Comma" 277 2296 44
    tap_key "Space" 556 2296 32
    tap_key "Period" 835 2296 46
    tap_key "Enter" 984 2296 10

elif [ "$MODE" = "full" ]; then

    echo "--- Row 0: Number Row ---"
    tap_key "backtick" 54 1599 96
    tap_key "1" 151 1599 49
    tap_key "2" 248 1599 50
    tap_key "3" 346 1599 51
    tap_key "4" 443 1599 52
    tap_key "5" 540 1599 53
    tap_key "6" 637 1599 54
    tap_key "7" 735 1599 55
    tap_key "8" 832 1599 56
    tap_key "9" 929 1599 57
    tap_key "0" 1027 1599 48

    echo ""
    echo "--- Row 1: QWERTY ---"
    tap_key "q" 59 1745 113
    tap_key "w" 166 1745 119
    tap_key "e" 273 1745 101
    tap_key "r" 380 1745 114
    tap_key "t" 487 1745 116
    tap_key "y" 594 1745 121
    tap_key "u" 700 1745 117
    tap_key "i" 807 1745 105
    tap_key "o" 914 1745 111
    tap_key "p" 1021 1745 112

    echo ""
    echo "--- Row 2: Home ---"
    tap_key "a" 65 1899 97
    tap_key "s" 183 1899 115
    tap_key "d" 302 1899 100
    tap_key "f" 421 1899 102
    tap_key "g" 540 1899 103
    tap_key "h" 659 1899 104
    tap_key "j" 778 1899 106
    tap_key "k" 897 1899 107
    tap_key "l" 1016 1899 108

    echo ""
    echo "--- Row 3: Z Row ---"
    tap_key "Shift" 84 2052 -1
    tap_key "z" 216 2052 122
    tap_key "x" 324 2052 120
    tap_key "c" 432 2052 99
    tap_key "v" 540 2052 118
    tap_key "b" 648 2052 98
    tap_key "n" 756 2052 110
    tap_key "m" 864 2052 109
    tap_key "Backspace" 997 2052 -301

    echo ""
    echo "--- Row 4: Space Row ---"
    tap_key "123" 66 2206 -2
    tap_key "Emoji" 177 2206 -300
    tap_key "Comma" 277 2206 44
    tap_key "Space" 556 2206 32
    tap_key "Period" 835 2206 46
    tap_key "Enter" 984 2206 10

    echo ""
    echo "--- Row 5: Utility Row ---"
    tap_key "Ctrl" 82 2343 -113
    tap_key "Alt" 236 2343 -57
    tap_key "Tab" 391 2343 9
    tap_key "ArrowLeft" 698 2343 -21
    tap_key "ArrowDown" 823 2343 -20
    tap_key "ArrowUp" 949 2343 -19
    tap_key "ArrowRight" 1023 2343 -22

fi

echo ""
echo "=== Test Complete ==="
echo "Check logcat: adb logcat -s DevKeyPress:D DevKeyMap:D"
```

---

## Coordinate Map as Bash Associative Array

```bash
# COMPACT mode key coordinates (copy-paste into scripts)
declare -A COMPACT_KEYS=(
    [q]="59 1639"      [w]="166 1639"    [e]="273 1639"    [r]="380 1639"
    [t]="487 1639"     [y]="594 1639"    [u]="700 1639"    [i]="807 1639"
    [o]="914 1639"     [p]="1021 1639"
    [a]="65 1858"      [s]="183 1858"    [d]="302 1858"    [f]="421 1858"
    [g]="540 1858"     [h]="659 1858"    [j]="778 1858"    [k]="897 1858"
    [l]="1016 1858"
    [z]="216 2077"     [x]="324 2077"    [c]="432 2077"    [v]="540 2077"
    [b]="648 2077"     [n]="756 2077"    [m]="864 2077"
    [shift]="84 2077"  [del]="997 2077"
    [123]="66 2296"    [emoji]="177 2296" [comma]="277 2296"
    [space]="556 2296" [period]="835 2296" [enter]="984 2296"
)

# FULL mode key coordinates
declare -A FULL_KEYS=(
    [backtick]="54 1599"
    [1]="151 1599"  [2]="248 1599"  [3]="346 1599"  [4]="443 1599"  [5]="540 1599"
    [6]="637 1599"  [7]="735 1599"  [8]="832 1599"  [9]="929 1599"  [0]="1027 1599"
    [q]="59 1745"   [w]="166 1745"  [e]="273 1745"  [r]="380 1745"  [t]="487 1745"
    [y]="594 1745"  [u]="700 1745"  [i]="807 1745"  [o]="914 1745"  [p]="1021 1745"
    [a]="65 1899"   [s]="183 1899"  [d]="302 1899"  [f]="421 1899"  [g]="540 1899"
    [h]="659 1899"  [j]="778 1899"  [k]="897 1899"  [l]="1016 1899"
    [shift]="84 2052"  [z]="216 2052"  [x]="324 2052"  [c]="432 2052"
    [v]="540 2052"     [b]="648 2052"  [n]="756 2052"  [m]="864 2052"
    [backspace]="997 2052"
    [123]="66 2206"    [emoji]="177 2206" [comma]="277 2206"
    [space]="556 2206" [period]="835 2206" [enter]="984 2206"
    [ctrl]="82 2343"   [alt]="236 2343"   [tab]="391 2343"
    [left]="698 2343"  [down]="823 2343"  [up]="949 2343"  [right]="1023 2343"
)

# Helper: tap a key by name
tap() {
    local coords="${COMPACT_KEYS[$1]:-${FULL_KEYS[$1]}}"
    if [ -z "$coords" ]; then
        echo "Unknown key: $1" >&2
        return 1
    fi
    adb shell input tap $coords
}

# Example usage:
# tap q         # types 'q'
# tap space     # types space
# tap enter     # presses enter
# tap shift     # toggles shift
```

---

## Calculation Method

All coordinates were computed using the same algorithm as `computeKeyBounds()` in
`/app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyBoundsCalculator.kt`.

**Formula for each key:**
1. Available height = keyAreaHeight - (rowCount - 1) * rowGap
2. Row height = availableHeight * (rowWeight / totalWeight)
3. Row top Y = sum of previous rows + previous gaps
4. Available width = screenWidth - 2 * horizontalPadding
5. Weighted width = availableWidth - (keyCount - 1) * keyGap
6. Key width = (keyWeight / totalRowWeight) * weightedWidth
7. Key left X = horizontalPadding + sum of previous key widths + previous key gaps
8. Center X = (left + right) / 2
9. Center Y = keyAreaTopY + (rowTop + rowBottom) / 2

**Assumptions:**
- Default portrait height: 40% of screen
- Default layout mode: FULL (settings default is "full")
- Navigation bar: included in 2400px (emulator renders within full resolution)
- Toolbar is above the key area, not overlapping it

**Accuracy note:** These coordinates are calculated from source code. Actual on-device
positions may vary slightly due to:
- Navigation bar height (if not using gesture nav)
- Status bar height consuming screen dp
- Non-default height preference
- Rounding in Compose layout engine

For production testing, use `KeyMapGenerator.dumpToLogcat()` which reads the actual
View position on screen via `getLocationOnScreen()`.

**Logcat extraction:**
```bash
adb logcat -s DevKeyMap:D | grep "^.*KEY " | \
  sed 's/.*KEY label=\([^ ]*\).*x=\([0-9]*\) y=\([0-9]*\)/\1 \2 \3/'
```
