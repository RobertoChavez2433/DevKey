# Dependency Graph — Phase 3 Regression Gate

Phase 3 is an execution gate with no new production code. The "dependency graph"
here is therefore the **runtime dependency chain** of each gate item — i.e.,
the set of preconditions that must be in place before a given Phase 3 check
can return a meaningful green signal.

---

## Root: Phase 3 Decision Gate (§3.8)
```
Phase 3 §3.8 (green to proceed to Phase 4)
    ├── §3.1 Full harness on FULL/COMPACT/COMPACT_DEV green
    ├── §3.2 Voice round-trip green
    ├── §3.3 Next-word prediction green
    ├── §3.4 Long-press coverage green
    ├── §3.5 SwiftKey visual diff within tolerance
    ├── §3.6 Manual smoke green
    └── §3.7 Lint clean
```

---

## §3.1 Full harness × 3 layouts
```
§3.1
  └─ tools/e2e/e2e_runner.py
       ├─ tools/e2e/lib/driver.py  ── requires ──▶ tools/debug-server/server.js running
       │                                              └─ node >= 18 on host
       │                                              └─ ADB_SERIAL env for multi-device
       ├─ tools/e2e/lib/keyboard.py ── requires ──▶ DUMP_KEY_MAP broadcast receiver
       │                                              └─ KeyMapGenerator.kt + debug build
       ├─ tools/e2e/lib/adb.py      ── requires ──▶ adb in PATH, booted device
       └─ tools/e2e/tests/*         ── requires ──▶ B1 skip-handling fix (see ground-truth)
                                                 └─ B2 pytest in requirements.txt
  └─ Phase 1 debug APK installed + IME enabled/set
  └─ DEVKEY_LAYOUT_MODE env var cycled through [full, compact, compact_dev]
```

**Blocker chain:** §3.1 blocked by **B1, B2** (harness crash) → blocks all other
§3.x items that route through `e2e_runner.py`.

---

## §3.2 Voice round-trip
```
§3.2
  └─ tests/test_voice.py::test_voice_round_trip_committed_text
       ├─ VoiceInputEngine (code-complete; state_transition signals verified)
       ├─ Whisper model + filter asset files in app/src/main/assets/   ◀── ABSENT (B3)
       ├─ RECORD_AUDIO permission granted to IME (manual precondition)
       ├─ Emulator audio injection support (tools/e2e/lib/audio.py)
       └─ tests/test_voice.py::_setup_voice_button
            └─ keyboard.get_key_map() includes code_-102 voice key
```

**Blocker chain:** §3.2 blocked by **B3** (model absent). Also bubbles B1/B2.

---

## §3.3 Next-word prediction
```
§3.3
  └─ tests/test_next_word.py (3 tests)
       ├─ LatinIME.setNextSuggestions instrumented with next_word_suggestions ✓
       ├─ CandidateView instrumented with candidate_strip_rendered ✓
       ├─ ExpandableDictionary bigram path wired (Phase 1 sub-phase 1.5) ✓
       └─ Default English dictionary loaded into running IME
```

**Blocker chain:** None except B1/B2. Runnable after harness fix.

---

## §3.4 Long-press coverage
```
§3.4
  └─ tests/test_long_press.py (4 tests, one per mode)
       ├─ KeyPressLogger.logLongPress → DevKeyLogger.text("long_press_fired") ✓
       ├─ .claude/test-flows/long-press-expectations/<mode>.json present ✓ (all 4 modes)
       ├─ lib/keyboard.py::set_layout_mode + layout_mode_recomposed gate ✓
       └─ Optional: swiftkey-reference/popups/<mode>/<label>.png for SSIM
            (SKIPs gracefully if missing — contingent on B1 fix)
```

**Blocker chain:** None except B1/B2.

---

## §3.5 SwiftKey visual diff
```
§3.5
  └─ tests/test_visual_diff.py (3 tests: compact/full/compact_dev dark)
       ├─ skimage + Pillow + numpy (requirements.txt ✓)
       ├─ .claude/test-flows/swiftkey-reference/<mode>-dark.png
       │     └─ compact-dark.png ✓
       │     └─ full-dark.png    ◀── ABSENT (B4)
       │     └─ compact-dev-dark.png ◀── ABSENT (B4)
       └─ DEVKEY_SSIM_THRESHOLD env (default 0.92 per lib/diff.py)
```

**Blocker chain:** Partial green only (B4). B1/B2 fix required for SKIPs to
not crash the runner.

---

## §3.6 Manual smoke
```
§3.6
  ├─ .claude/test-flows/phase1-regression-smoke.md (clipboard, macros, command, plugins)
  ├─ .claude/test-flows/phase1-voice-verification.md (voice model present, permission)
  └─ Human operator with emulator + installed RC build
```

**Blocker chain:** Voice section of phase1-voice-verification.md blocked by B3.

---

## §3.7 Lint clean
```
§3.7
  └─ ./gradlew lint
       ├─ app/build.gradle.kts::lint { abortOnError = false }   ◀── semantic trap
       └─ app/build/reports/lint-results-debug.xml
            └─ Interpretation rule: zero <issue severity="Error"> entries
```

**Blocker chain:** None, but see `patterns/lint-gate.md` for interpretation.

---

## §3.8 Decision gate
```
§3.8
  └─ All of §3.1..§3.7 green
  └─ tier-stabilization-status.md updated with green commit SHAs per tier
  └─ Phase 4 entry unblocked; if any red, bounce to:
       ├─ Phase 1 (if app defect)
       ├─ Phase 2 (if test infrastructure defect)
       └─ Phase 3 (if stabilization-in-place, per patterns/defect-triage.md)
```

---

## Cross-phase dependencies (upstream)

Phase 3 inherits from:
- **Phase 1 (features):** voice model sourcing, long-press data, predictive
  next-word wiring, plugin signature fix, all feature-completeness items
  from spec §2.1.
- **Phase 2 (infrastructure):** driver server extensions, ENABLE_DEBUG_SERVER
  + SET_LAYOUT_MODE broadcasts, all new test modules, coverage matrix,
  tier-stabilization document.

Phase 3 **MUST NOT** modify anything in those upstream layers except via
stabilization-in-place per §3.8 routing rules.
