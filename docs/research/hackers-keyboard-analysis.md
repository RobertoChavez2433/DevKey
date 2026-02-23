# Hacker's Keyboard - Research Analysis

**Date**: 2026-02-23
**Source**: https://github.com/klausw/hackerskeyboard

## Overview

Hacker's Keyboard is an Android soft keyboard (IME) designed to replicate a full desktop PC keyboard on mobile. Package: `org.pocketworkstation.pckeyboard`. Forked from Android 2.3 (Gingerbread) AOSP keyboard.

## Core Functionality

- Full 5-row layout with dedicated number row
- Modifier keys: Ctrl, Alt, Esc, Tab (true multitouch hold-and-press)
- Arrow keys, Home/End/PgUp/PgDn
- Function keys F1-F12
- 30+ language layouts (QWERTY, AZERTY, QWERTZ, Dvorak, Colemak, Cyrillic, Arabic, etc.)
- Compact 4-row portrait / full 5-row landscape modes
- ~10MB RAM footprint (vs ~40MB+ for Gboard)

## Technical Architecture

| Aspect | Detail |
|---|---|
| Primary language | Java (86.7%) |
| Native code | C++ (10.7%) + C (1.5%) — dictionary handling, prediction engine |
| Build system | Gradle |
| Origin | AOSP Gingerbread keyboard fork |
| License | Apache 2.0 |
| Target API | Originally API 9 (Android 2.3) |
| Commits | 1,502 |

### Project Structure
```
hackerskeyboard/
  app/                    — Main Android application
  java/                   — Java source files
  dictionaries/           — Language dictionary resources
  build.gradle            — Gradle build config
  gradlew / gradlew.bat   — Gradle wrapper
```

C/C++ native components handle dictionary lookup (liblatinime).

## Project Status

| Metric | Value |
|---|---|
| Stars | 2,233 |
| Forks | 526 |
| Open issues | 539 (including 25 open PRs) |
| Last release | v1.40.7 (Nov 2018) |
| Last substantive code change | ~2019 |
| Status | **Effectively dormant** |

Maintainer (klausw) stated: "This is a rather ancient project... it would need some major rewrites to work with newer APIs."

## Known Issues

### Critical Compatibility
- Targets API 9, Play Store requires API 29+
- Language switching broken on modern Android
- Popup keys don't work on current Android
- Android 13+ issues (issue #901)
- Android 15 landscape problems (issue #957)

### User Complaints
- Key labels too small to read (#169)
- No custom layout support (#13)
- No gesture/swipe typing
- No clipboard manager
- No voice input
- Project appears dead (#650)

## What Makes It Unique

1. **True modifier key support** with multitouch (hold Ctrl + press C)
2. **Full PC keyboard emulation** — dedicated number row, F1-F12, nav cluster
3. **Terminal-first design** — Esc, Tab, Ctrl, arrows are core
4. **Extreme lightweight** — ~10MB RAM, no cloud, no telemetry
5. **Open source** (Apache 2.0) — fully forkable
6. **30+ language layouts** in single APK

## Notable Forks

- `keymapperorg/KeyMapperHackersKeyboard` — KeyMapper integration
- `wyatt8740/wyattskeyboard` — personalized fork
- `86chan/hackerskeyboard_RE` — reverse-engineering variant
