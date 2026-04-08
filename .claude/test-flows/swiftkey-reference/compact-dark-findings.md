# SwiftKey Reference Findings — compact-dark

**Source images (local-only, not committed per spec §8):**
- `.claude/test-flows/swiftkey-reference/compact-dark.png` — full-screen capture (Samsung Messages context + keyboard)
- `.claude/test-flows/swiftkey-reference/compact-dark-cropped.png` — keyboard-only crop (higher-resolution view for hint-label reading)

**Captured from:** Samsung Messages app, Microsoft SwiftKey, dark theme
**Mode:** COMPACT (number row + QWERTY + shift row + utility row)
**Captured:** 2026-04-08 (reference for DevKey v1.0 pre-release Phase 6 theme retuning)

**Reading methodology note:** Initial observations from the full-screen image were updated after reviewing the cropped image — the zxcvbnm long-press hints were originally mis-aligned by one key position. This document reflects the corrected reading from `compact-dark-cropped.png`.

> Legal scope (per spec §8): internal-use-only, do not redistribute.

## Observable layout

Five rows, visible top-to-bottom:
1. **Number row** — `1 2 3 4 5 6 7 8 9 0` (same key size as letter keys)
2. **qwertyuiop** — with small hint labels above each letter:
   `Q(%) W(^) E(~) R(|) T([) Y(]) U(<) I(>) O({) P(})`
3. **asdfghjkl** — hints:
   `A(@) S(#) D(&) F(*) G(-) H(+) J(=) K(() L())`
4. **shift | zxcvbnm | backspace** — hints:
   `Z($) X(") C(') V(no hint visible) B(no hint) N(.) M(/)`
5. **Utility row** — `123 | emoji | mic | , | space ("Microsoft SwiftKey" watermark) | .?! | enter`

Suggestion strip above keyboard showing "Yeah | I | Oh".

## Color observations (dark theme)

| Token region | Observed | Approx hex |
|---|---|---|
| Keyboard background | Very dark neutral, slightly warm, near-black | ~`#121212` – `#161618` |
| Key fill (all keys — letters, numbers, utility) | Slightly lighter than bg, flat | ~`#2B2B2E` – `#2D2D31` |
| Key primary text | Near-white, medium weight | ~`#F0F0F0` – `#FFFFFF` |
| Key hint label (long-press indicator) | Muted gray, small | ~`#8A8A8F` – `#909095` |
| Space bar watermark ("Microsoft SwiftKey") | Dim gray | ~`#707075` |
| Suggestion strip bg | Same as keyboard bg | ~`#121212` |
| Suggestion strip text | Near-white | ~`#F0F0F0` |

**Critical observation:** Utility/modifier keys (shift, backspace, 123, emoji, mic, comma, period, enter) use the **same fill color** as letter keys — NOT a darker or distinct "special" shade. Our current DevKey `keyBgSpecial` token giving modifier keys a distinct background does NOT match SwiftKey COMPACT dark.

## Spacing & shape

- Inter-key horizontal gap: small, ~3dp
- Inter-row vertical gap: small, ~3dp
- Key corner radius: medium, ~6–8dp
- Row heights: uniform across all 5 rows
- Keyboard padding: minimal horizontal inset (~4–6dp edge-to-edge)

## Typography

- Letter key primary glyph: ~22sp, sans-serif, medium weight, center-aligned
- Number row glyph: same size/weight as letters (~22sp)
- Hint label: ~10sp, sans-serif, top-left corner of the key, slightly offset from edge
- "Microsoft SwiftKey" watermark on space: ~11sp, dim gray, center-aligned

## Long-press data (reference-observed — RETUNE Phase 5.2 COMPACT)

SwiftKey COMPACT long-press content is the standard shift-symbol set, NOT the digits-as-default template we applied in Phase 5.2. Reference-observed mapping:

### Row 1: qwertyuiop
| Key | Long-press |
|---|---|
| q | `%` |
| w | `^` |
| e | `~` |
| r | `\|` |
| t | `[` |
| y | `]` |
| u | `<` |
| i | `>` |
| o | `{` |
| p | `}` |

### Row 2: asdfghjkl
| Key | Long-press |
|---|---|
| a | `@` |
| s | `#` |
| d | `&` |
| f | `*` |
| g | `-` |
| h | `+` |
| j | `=` |
| k | `(` |
| l | `)` |

### Row 3: zxcvbnm (CORRECTED from cropped image)
| Key | Long-press |
|---|---|
| z | `_` |
| x | `$` |
| c | `"` |
| v | `'` |
| b | `:` |
| n | `;` |
| m | `/` |

### Period key (utility row)
Hint shown: `.,?!` — suggests multi-char popup containing `. , ? !`.

**Note:** No accented-vowel popups are visible in this capture. SwiftKey may surface accents via long-press hold rather than immediate label display. We may retain the accented-vowel `longPressCodes` we added in Phase 5.2 since they're strictly richer than the SwiftKey single-char default.

## Known capture gaps

- **No light-theme capture** — dark-only. Spec §4.1 light palette remains unreferenced. Per user direction, light palette is deferred (user does not use light mode).
- **No FULL mode capture** — FULL layout tuning uses COMPACT observations as a directional proxy.
- **No SYMBOLS mode capture** — SymbolsLayout theme tuning inherits shared tokens only.
- **No QWERTY mode capture** — same.
- **No Fn / Phone mode captures** — same.
- ~~**`v` and `b` long-press** not visible in the capture (too small or blank in SwiftKey itself).~~ — Resolved via cropped re-read: v→`'`, b→`:`.

## Applied to

- Phase 6.1: `DevKeyTheme.kt` dark palette retune (this reference is the oracle)
- Phase 5.2 (retune): `QwertyLayout.kt` `buildCompactLayout()` letter-row long-press replaced with reference shift-symbols
