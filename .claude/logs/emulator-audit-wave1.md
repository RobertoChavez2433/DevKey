# Emulator Audit — Wave 1

**Date**: 2026-03-01
**Device**: Pixel 7 API 36 (emulator)
**App**: DevKey v0.1.0 (debug)
**Screenshots**: `.claude/screenshots/screen_*.png`

---

## Summary

First pass audit of DevKey on emulator after implementing the keyboard-layout-fix-plan. Found **8 bugs** across 3 severity levels. The app is functional as a basic keyboard, but the legacy Main activity landing page is severely outdated and several keyboard features don't work correctly.

---

## BUG-01: Outdated Landing Page (Main Activity) — BLOCKER

**Severity**: P0 — Blocker
**Screenshots**: `screen_old_main.png`, `screen_old_main_scroll.png`, `screen_app_landing.png`
**Activity**: `.Main` (legacy Java activity)

**Description**: The app's launcher activity is the old Hacker's Keyboard setup wizard, completely unmodified. This is the first thing users see when they open the app.

**Problems**:
- Title says "Enabling **Hacker's Keyboard**" — wrong branding
- Body text references "Hacker's Keyboard" throughout (5+ times)
- Instructions say "Find *Hacker's Keyboard* in the input method list"
- "MORE" button does a Play Store search for the old developer's dictionary packages
- "Additional information" links to the old project web page (klausw/hackerskeyboard)
- Shows "Version: custom" at bottom
- UI is raw Android Views with unstyled gray buttons — no Material You, no DevKey branding
- No connection to the modern Compose settings page
- The "SETTINGS" button opens the old Hacker's Keyboard preferences (not DevKey Compose settings)

**Expected**: A modern DevKey-branded onboarding/setup page using Compose with:
- DevKey branding and identity
- Quick setup flow (enable keyboard, set as default)
- Link to DevKey settings (not legacy prefs)
- Feature highlights / quick start guide

---

## BUG-02: 123 Toolbar Button Does Not Switch to Symbols Layout — P1

**Severity**: P1 — High
**Screenshots**: `screen_kb_settings.png`, `screen_kb_symbols.png`

**Description**: Tapping the "123" button on the toolbar row has no visible effect. The keyboard stays on the QWERTY layout. Comparing `screen_kb_settings.png` (before tap) and `screen_kb_symbols.png` (after tap) — they are identical.

**Expected**: Tapping 123 should switch to a symbols/numbers layout with punctuation, special characters, brackets, etc.

**Impact**: Users cannot access symbols layout at all. This is a core keyboard function.

---

## BUG-03: Dynamic Height Setting Not Applied — P1

**Severity**: P1 — High
**Screenshots**: `screen_kb_settings.png`, `screen_settings.png`

**Description**: The keyboard-layout-fix-plan hardcoded keyboard height to 40% of screen. However, the DevKey Settings page has a "Height (Portrait)" slider set to **50%** and "Height (Landscape)" at **40%**. These two systems appear to conflict:

- The Compose keyboard (`KeyboardView.kt`) calculates height as `screenHeightDp * 0.40`
- The Settings UI exposes a user-adjustable height percentage
- The hardcoded value ignores the user's preference

User reported "screen resizing didn't work" — the keyboard visually appears roughly the same size as before the plan was implemented.

**Root cause hypothesis**: Either:
1. The hardcoded 40% in `KeyboardView.kt` overrides the preference, OR
2. The preference slider updates a SharedPreferences value that nothing reads, OR
3. The Compose keyboard doesn't read the height preference at all

**Expected**: Keyboard height should respect the user's setting from the preferences slider. The hardcoded 40% should be replaced with a read from the preference value.

---

## BUG-04: Suggestion Bar Toggle in Settings References Removed Feature — P2

**Severity**: P2 — Medium
**Screenshots**: `screen_settings.png`

**Description**: The Settings page still shows "Suggestions in Landscape" toggle (enabled). The keyboard-layout-fix-plan **removed** the SuggestionBar composable from `DevKeyKeyboard.kt`. Settings related to suggestions/predictions may now be orphaned:
- "Suggestions in Landscape" toggle
- "Candidate Scale" slider
- "Show Suggestions" toggle
- "Auto-Complete" toggle
- "Suggested Punctuation" dropdown
- "Autocorrect Level" dropdown

These settings still appear in the UI but the corresponding UI feature (suggestion bar) has been removed.

**Expected**: Either re-enable suggestions (future feature), or hide/remove these orphaned settings to avoid user confusion.

---

## BUG-05: Settings Page Is Extremely Long — P2

**Severity**: P2 — Medium (UX)
**Screenshots**: `screen_settings.png` through `screen_settings_scroll4.png`

**Description**: The settings page requires **4+ full screen scrolls** to see all options. It's a single flat list with section headers. Sections:

1. Keyboard View (12 items)
2. Key Behavior (8 items)
3. Actions (7 items)
4. Feedback (5 items)
5. Prediction & Autocorrect (6 items)
6. Macros (2 items)
7. Voice Input (2 items)
8. Command Mode (2 items)
9. Backup (2 items)
10. About (2 items)

**Total: ~48 settings items in a single scrollable list.**

**Expected**: Group into collapsible sections, or use a multi-screen settings hierarchy (top-level categories that drill into detail screens). Most modern keyboards (Gboard, SwiftKey) use a card/grid approach for top-level, with detail screens.

---

## BUG-06: "Hint Mode: Hidden" — Key Hints Not Visible — P2

**Severity**: P2 — Medium
**Screenshots**: `screen_kb_settings.png`

**Description**: The keyboard shows small superscript characters on keys (e.g., Q has `!`, W has `@`, E has `#`, etc.) but these are very faint and hard to read against the dark key background. The setting "Hint Mode" is set to "Hidden" — but hints appear to be showing anyway (just poorly).

Looking at the keyboard screenshot, hints ARE visible (tiny superscripts in upper-right of each key). Either:
1. "Hidden" mode isn't actually hiding them, or
2. The Compose keyboard doesn't read this preference

**Expected**: If Hint Mode = Hidden, no hints should show. If visible, they should be clearly readable.

---

## BUG-07: Number Row Missing `0` Key — P3

**Severity**: P3 — Low
**Screenshots**: `screen_kb_settings.png`

**Description**: The number row shows: `Esc 1 2 3 4 5 6 7 8 9 Tab`. There is no `0` key. On a standard keyboard, the number row is `1 2 3 4 5 6 7 8 9 0`. The `0` is replaced by `Esc` and `Tab` occupying the edges.

**Impact**: Users must use an alternate input method to type `0`. Long-pressing `O` or switching to symbols might work, but this is unexpected.

**Expected**: Include `0` in the number row. Consider: `Esc 1 2 3 4 5 6 7 8 9 0 Tab` (12 keys), or make `0` available through long-press on another key with clear indication.

---

## BUG-08: "Built on Hacker's Keyboard" Attribution in About — P3

**Severity**: P3 — Low
**Screenshots**: `screen_settings_scroll4.png`

**Description**: The About section at the bottom of Settings shows:
- Version: 0.1.0
- Built on Hacker's Keyboard — github.com/klausw/hackerskeyboard

While attribution is appropriate, the link goes to the old repository. Should link to the DevKey repo or at minimum be styled as a proper attribution (not the primary branding).

---

## Observations (Not Bugs)

### Keyboard Layout Is Correct Post-Fix
The bottom row correctly shows: `Ctrl Alt [Space] Left Up Down Right Enter` — no duplicate Shift key. The layout fix plan was successfully applied.

### Toolbar Row Working (Partially)
The toolbar shows 5 items: clipboard icon, edit/mic icon, 123, lightning bolt, overflow (...). The clipboard and lightning bolt icons render. The 123 button doesn't function (BUG-02).

### DevKey Enabled in System
`screen_more.png` shows DevKey properly listed and enabled in On-screen keyboard settings with the "Esc" icon.

### Settings Compose UI Is Functional
The dark-themed Compose settings page renders properly, sliders and toggles work, Material You theming is applied. The issue is organizational (too many items), not functional.

---

## Resolution

All 8 bugs reported in this audit were fixed in Sessions 16-17. BUG-02 (123 mode switch) and BUG-03 (dynamic height) were addressed in the 123-fix-e2e-harness implementation (commit 386a25b). Remaining UI and UX bugs were resolved through the Kotlin migration and dead-code cleanup phases.

---

## Recommended Fix Waves

### Wave 1 — Critical Function (BUG-02, BUG-03)
1. Fix 123 toolbar button → symbols layout switching
2. Fix dynamic height to read from user preference instead of hardcoded 40%
3. Investigate and fix hint mode preference not being respected

### Wave 2 — Landing Page Redesign (BUG-01)
1. Design new Compose-based onboarding/main activity
2. Replace old Main activity with modern DevKey setup flow
3. Remove all Hacker's Keyboard branding from launcher flow

### Wave 3 — UX Polish (BUG-04, BUG-05, BUG-06, BUG-07, BUG-08)
1. Clean up orphaned suggestion settings
2. Restructure settings into hierarchical navigation
3. Fix hint mode rendering
4. Add 0 to number row
5. Update About attribution link

---

## Screenshot Index

| File | Content |
|------|---------|
| `screen_home.png` | Home screen with keyboard active (Google search widget) |
| `screen_app_landing.png` | Old Main activity — Hacker's Keyboard setup wizard |
| `screen_old_main.png` | Old Main activity — full top view |
| `screen_old_main_scroll.png` | Old Main activity — scrolled to bottom |
| `screen_more.png` | Android on-screen keyboard list (DevKey enabled) |
| `screen_kb_settings.png` | Keyboard visible in Android Settings search bar |
| `screen_kb_symbols.png` | After tapping 123 — no change (bug) |
| `screen_kb_active.png` | Google sign-in page (keyboard not visible) |
| `screen_settings.png` | DevKey settings — Keyboard View section |
| `screen_settings_scroll.png` | DevKey settings — Key Behavior section |
| `screen_settings_scroll2.png` | DevKey settings — Actions & Feedback section |
| `screen_settings_scroll3.png` | DevKey settings — Prediction & Macros section |
| `screen_settings_scroll4.png` | DevKey settings — Voice, Command, Backup, About |
