# Security Review: Phase 2 Regression Infrastructure — 2026-04-08

**APPROVE**

## Summary

- Domains audited: 8 of 8
- Findings: CRITICAL: 0, HIGH: 0, MEDIUM: 1, LOW: 2, INFO: 4
- Privacy invariant: **preserved**
- Net verdict: **APPROVE** — the plan's emit-site instrumentation, receiver gating, and HTTP surface are all sound.

The plan is notable for doing the privacy-conscious thing in several non-obvious places: next-word instrumentation emits `prev_word_length` not `prev_word`; a privacy-regression guard test (`test_next_word_privacy_payload_is_structural_only`) will fail if someone adds a field carrying word content; voice round-trip test asserts on `result_length`, never transcript; `voice-hello.wav` fixture is kept out of the repo.

## Findings

### [MEDIUM] `/adb/logcat` endpoint exposes DevKeyPress keystroke stream over unauthenticated loopback HTTP

- **Plan location**: Phase 2.3 (`GET /adb/logcat?tags=DevKeyPress,...`) + Phase 4.3 (`driver.logcat_dump(["DevKeyPress"])`)
- **Description**: The new endpoint dumps arbitrary logcat tags. On debug builds, `DevKeyPress` contains a live stream of key events including `KEY label=... code=... x=... y=...` and `LONG label=... lpCode=...`. The driver server has no auth — any process on the developer's machine during a test run can siphon keystrokes over loopback.
- **Recommendation** (pick one):
  1. Shared-secret token in `X-Driver-Token` header read from env var
  2. Scrub `KEY label=... x=... y=...` lines server-side, return only structural count
  3. Document explicitly in `tools/debug-server/README.md` under "Privacy contract"

### [LOW] `debug_server_enabled` handshake logs the raw URL including any embedded userinfo

- **Plan location**: Phase 1.1
- **Fix**: Scrub URL to host+port before logging:
  ```kotlin
  val scrubbed = try { java.net.URI(url).let { "${it.scheme}://${it.host}:${it.port}" } }
                 catch (_: Exception) { "<invalid url>" }
  DevKeyLogger.ime("debug_server_enabled", mapOf("url" to scrubbed))
  ```

### [LOW] `DevKeyLogger.enableServer` has no `BuildConfig.DEBUG` guard — only call-site gating

- **Recommendation**: Add inner guard as defense-in-depth:
  ```kotlin
  fun enableServer(url: String) {
      if (!BuildConfig.DEBUG) return
      serverUrl = url
  }
  ```
  Outside plan scope (plan doesn't touch DevKeyLogger.kt), but optional one-line follow-up.

### [INFO] No auth on driver server loopback — pre-existing, not materially worsened

The driver server is architecturally a local ADB convenience layer — anyone who can reach 127.0.0.1:3947 can already run ADB directly. Only `/adb/logcat` expands capability beyond raw ADB (see MEDIUM). Document threat model in README.

### [INFO] Privacy invariant on Phase 1.3 next-word instrumentation is sound

- `prev_word_length` is `Int` from `prevWord.length`; `result_count` is `Int`; `source` is hardcoded literal
- `sendToServer` stringifies via `toString()` — `Int` becomes `"3"`, never the word
- `test_next_word_privacy_payload_is_structural_only` enforces the whitelist at CI time — excellent

### [INFO] Broadcast receiver gating is correct

- Both receivers are nested inside `if (KeyMapGenerator.isDebugBuild(this))`
- Both use `Context.RECEIVER_NOT_EXPORTED`
- `SET_LAYOUT_MODE` validates mode against hardcoded whitelist before preference write
- Null/missing extras handled safely

### [INFO] `adb-exec.js` uses `execFile` not `exec` — no shell injection

- All commands funnel through `run(args)` → `execFileAsync('adb', adbArgs(args))`
- Coordinate/URL/action-name/extra values pass literally to `adb` without shell interpretation
- Minor nit: `screencap` with `>` should default to `spawn` + `fs.createWriteStream` pattern from day one (also noted in code review m1)

## Clean domains

- Input Capture, Plugin Loading (N/A Phase 2), KeyEvent Injection, Clipboard, Network (Whisper on-device), Permissions (no new), BuildConfig gates, ProGuard/R8

## Final disposition

**APPROVE**. MEDIUM should be addressed by one of the three options or documented as accepted risk in README. LOWs are optional hardening. None block implementation.
