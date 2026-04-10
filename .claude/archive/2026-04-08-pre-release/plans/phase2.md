# v1.0 Pre-Release Phase 2 — Regression Infrastructure Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** Stand up a sleep-free, HTTP-forwarded, state-gated regression infrastructure that covers every Phase 1 feature (voice, next-word, long-press, SwiftKey visual parity, mode switching) across FULL / COMPACT / COMPACT_DEV layouts.

**Spec:** `.claude/specs/pre-release-spec.md` (§6 Phase 2)
**Tailor:** `.claude/archive/2026-04-08-pre-release/tailor/phase2/`

**Architecture:** Phase 2 is overwhelmingly tooling work. IME-side changes are limited to two debug-only broadcast receivers (`ENABLE_DEBUG_SERVER`, `SET_LAYOUT_MODE`) and two new `DevKeyLogger.text` call sites inside `LatinIME.setNextSuggestions`. All heavy lifting happens in `tools/debug-server/` (extended into a test driver server with wave-gating and ADB dispatch) and `tools/e2e/` (Python harness upgraded from sleep-based assertions to HTTP-wait assertions, with new test modules for voice round-trip, next-word prediction, long-press coverage, and SwiftKey visual diff). Both run on the developer's machine — Node driver server and Python runner as separate processes, coupled via `http://127.0.0.1:3947`.

**Tech Stack:** Kotlin + C++ NDK, Jetpack Compose, Room, TF Lite, Gradle KTS; Node HTTP (driver server), Python 3.8+ (test runner), scikit-image (visual diff)

**Blast Radius:** 6-8 direct (`LatinIME.kt`, `KeyPressLogger.kt`, `CandidateView.kt`, `KeyMapGenerator.kt`, `DevKeyKeyboard.kt`, plus feature emit sites for clipboard / macros / command mode / plugin manager under 4.6-4.9) · 0 dependent (additive infrastructure) · 1-2 new unit tests (`DevKeyLoggerTest.kt`) · 5+ new Python test modules · 0 cleanup in Phase 2 (some Phase 4 refactor cleanup deferred)

**Design decisions locked by plan author** (7 open questions from tailor manifest):
1. Delivery mechanism for `enableServer(url)` → **Option A: broadcast intent** `dev.devkey.keyboard.ENABLE_DEBUG_SERVER` mirroring `DUMP_KEY_MAP`
2. Runner extension target → **`tools/e2e/` Python harness**. `/test` Claude skill stays as-is for ad-hoc exploration but is NOT the green-bar path (per `.claude/memory/test-skill-redesign.md`)
3. Visual-diff library → **`scikit-image.metrics.structural_similarity`**, SSIM ≥ **0.92** (tunable via env var `DEVKEY_SSIM_THRESHOLD`). **Justification:** Spec §9's example cites 0.95, but 0.92 is chosen here to accommodate anti-aliasing variance across runs (emulator GPU pipeline produces sub-pixel differences on repeated captures even without code changes). The threshold is tunable via `DEVKEY_SSIM_THRESHOLD` so a stricter gate can be enabled once per-mode reference sets stabilize and the observed floor is measured.
4. Wave-gating signals → per-flow signal lists documented in each test module; driver server exposes `GET /wait?category=X&event=Y&match=<json>&timeout=<ms>`
5. Driver-server lifetime → **long-lived per session**, user runs `node tools/debug-server/server.js` in a terminal before invoking `python tools/e2e/e2e_runner.py`
6. COMPACT mode switching → **new `SET_LAYOUT_MODE` broadcast** (same pattern as DUMP_KEY_MAP)
7. Legacy `Log.d` tag migration → **deferred**. Driver server merges HTTP log stream with `adb logcat -d -s DevKeyPress DevKeyMode DevKeyMap DevKeyBridge` polling. Migration is explicit non-goal for Phase 2.

**Out of scope for Phase 2 — track as follow-up:**
- Defense-in-depth guard inside `DevKeyLogger.enableServer` (reject non-loopback URLs at the setter): this plan does not touch `DevKeyLogger.kt` source. File as a follow-up issue; the threat model is dev-only and the Phase 2 loopback-binding + `RECEIVER_NOT_EXPORTED` controls are considered sufficient for now.

**Ground-truth anchors** (from tailor `ground-truth.md`):
- `DevKeyLogger.enableServer(url: String)` / `disableServer()` already exist (`debug/DevKeyLogger.kt:37-44`) — only a caller is missing.
- Existing debug server binds `127.0.0.1:3947` (`tools/debug-server/server.js:6, 82`), accepts `POST /log`, `GET /logs`, `GET /health`, `GET /categories`, `POST /clear`. Extension is additive — all existing endpoints stay.
- `KeyMapGenerator.isDebugBuild(context)` gates debug-only code paths via `ApplicationInfo.FLAG_DEBUGGABLE`.
- Key map logcat format: `KEY label=<label> code=<code> x=<x> y=<y>` on the `DevKeyMap` tag (verified `KeyMapGenerator.kt:193`).
- Layout mode preference: `SettingsRepository.KEY_LAYOUT_MODE`, values `"full"` / `"compact"` / `"compact_dev"` (verified `LatinIME.kt:407-413`).
- `KEYCODE_VOICE = -102` (verified `KeyData.kt:148`).
- VoiceState enum: `IDLE`, `LISTENING`, `PROCESSING`, `ERROR` (verified `VoiceInputEngine.kt:49-58`).
- `tools/e2e/lib/keyboard.py:45` regex `r"(\S+)=(\d+),(\d+)"` does NOT match current output — **broken, must fix**. Correct form: `r"KEY label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)"`.

---

## Phase 1 — IME-side debug broadcasts + instrumentation

Wires `DevKeyLogger.enableServer` into the running IME process via a new debug-only broadcast receiver, adds a second receiver to flip the layout-mode preference without a settings-UI drive, and instruments `setNextSuggestions` so Phase 2.3's next-word flow can gate on an observable signal.

### Sub-phase 1.1: Add `ENABLE_DEBUG_SERVER` broadcast receiver

**Files:** `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Add a nullable field on the `LatinIME` class (co-located with the existing `keyMapDumpReceiver` field — grep for `keyMapDumpReceiver` to locate):
   ```kotlin
   // WHY: Holds the debug-only BroadcastReceiver so onDestroy can unregister it.
   // NOTE: Mirrors the existing keyMapDumpReceiver field pattern (LatinIME.kt same area).
   private var enableDebugServerReceiver: BroadcastReceiver? = null
   ```

2. In `LatinIME.onCreate()`, inside the existing `if (KeyMapGenerator.isDebugBuild(this)) { ... }` block (LatinIME.kt:404-422 — the one that registers `keyMapDumpReceiver`), append a second receiver registration AFTER the existing `registerReceiver(keyMapDumpReceiver, ...)` call:
   ```kotlin
   // WHY: Test harness pushes the driver-server URL into the running IME via broadcast.
   // FROM SPEC: §6 Phase 2 item 2.1 — "DevKeyLogger.enableServer(url) wired into /test harness"
   // NOTE: Mirrors DUMP_KEY_MAP receiver exactly. Debug-only gate is the enclosing
   //       isDebugBuild check — no additional gating needed.
   // IMPORTANT: Extras: --es url http://10.0.2.2:3947 (emulator) or equivalent host loopback.
   //            Passing no url extra OR an empty string calls disableServer() for cleanup.
   enableDebugServerReceiver = object : BroadcastReceiver() {
       override fun onReceive(context: Context, intent: Intent) {
           val url = intent.getStringExtra("url")
           if (url.isNullOrBlank()) {
               DevKeyLogger.disableServer()
               DevKeyLogger.ime("debug_server_disabled")
           } else {
               DevKeyLogger.enableServer(url)
               // NOTE: This ime() call runs BEFORE the server is actually reachable
               //       via the new URL on every subsequent event, so it serves as a
               //       handshake: the first log line the driver server receives is
               //       this one, confirming the broadcast landed.
               // SECURITY: Scrub the URL to scheme://host:port only so a malicious
               //           broadcaster cannot inject log-poisoning query strings.
               val scrubbed = try {
                   java.net.URI(url).let { "${it.scheme}://${it.host}:${it.port}" }
               } catch (_: Exception) {
                   "<invalid url>"
               }
               DevKeyLogger.ime("debug_server_enabled", mapOf("url" to scrubbed))
           }
       }
   }
   registerReceiver(
       enableDebugServerReceiver,
       IntentFilter("dev.devkey.keyboard.ENABLE_DEBUG_SERVER"),
       Context.RECEIVER_NOT_EXPORTED
   )
   ```

3. In `LatinIME.onDestroy()` (grep for `override fun onDestroy` in LatinIME.kt), add receiver unregistration next to any existing receiver cleanup. If no existing unregister for `keyMapDumpReceiver` is present, add both:
   ```kotlin
   // WHY: Prevent leak of the newly added receiver across IME process restarts.
   // NOTE: keyMapDumpReceiver is already unregistered at LatinIME.kt:576 — do NOT duplicate that cleanup.
   try {
       enableDebugServerReceiver?.let { unregisterReceiver(it) }
   } catch (_: IllegalArgumentException) {
       // Already unregistered — safe to ignore.
   }
   enableDebugServerReceiver = null
   ```

4. Verify no new imports are needed — `BroadcastReceiver`, `Context`, `Intent`, `IntentFilter`, and `DevKeyLogger` are already imported in `LatinIME.kt` for the existing `keyMapDumpReceiver`. Grep the top of the file to confirm before editing.

### Sub-phase 1.2: Add `SET_LAYOUT_MODE` broadcast receiver

**Files:** `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Add a nullable field on the `LatinIME` class next to the two receivers from 1.1:
   ```kotlin
   // WHY: Holds the debug-only layout-mode switch receiver for unregistration.
   private var setLayoutModeReceiver: BroadcastReceiver? = null
   ```

2. Inside the same `if (KeyMapGenerator.isDebugBuild(this))` block in `onCreate()`, after the `ENABLE_DEBUG_SERVER` registration, add:
   ```kotlin
   // WHY: Test harness needs to flip between FULL/COMPACT/COMPACT_DEV without driving the Settings UI.
   // FROM SPEC: §6 Phase 2 item 2.4 — "Existing tier stabilization — FULL + COMPACT + COMPACT_DEV to 100% green"
   // NOTE: Writing the preference directly triggers the existing SharedPreferenceChangeListener
   //       path in LatinIME (already wired — LatinIME is a SharedPreferences.OnSharedPreferenceChangeListener).
   // IMPORTANT: Valid mode values are exactly "full", "compact", "compact_dev" — any other value is ignored.
   //            These match the whitelist in the existing keyMapDumpReceiver at LatinIME.kt:407-413.
   setLayoutModeReceiver = object : BroadcastReceiver() {
       override fun onReceive(context: Context, intent: Intent) {
           val mode = intent.getStringExtra("mode") ?: return
           if (mode !in setOf("full", "compact", "compact_dev")) {
               DevKeyLogger.error("set_layout_mode_rejected", mapOf("mode" to mode))
               return
           }
           PreferenceManager.getDefaultSharedPreferences(context)
               .edit()
               .putString(SettingsRepository.KEY_LAYOUT_MODE, mode)
               .apply()
           DevKeyLogger.ime("layout_mode_set", mapOf("mode" to mode))
       }
   }
   registerReceiver(
       setLayoutModeReceiver,
       IntentFilter("dev.devkey.keyboard.SET_LAYOUT_MODE"),
       Context.RECEIVER_NOT_EXPORTED
   )
   ```

3. In `onDestroy()`, add the matching unregistration next to the two added in 1.1:
   ```kotlin
   try {
       setLayoutModeReceiver?.let { unregisterReceiver(it) }
   } catch (_: IllegalArgumentException) {
       // Already unregistered — safe to ignore.
   }
   setLayoutModeReceiver = null
   ```

4. Verify no new imports are required — `PreferenceManager` and `SettingsRepository` are already imported in `LatinIME.kt` (used in the existing `keyMapDumpReceiver`).

### Sub-phase 1.3: Instrument `setNextSuggestions` for next-word flow gating

**Files:** `app/src/main/java/dev/devkey/keyboard/LatinIME.kt` (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Locate `private fun setNextSuggestions()` (LatinIME.kt:1933). The function currently has three possible outcomes: (a) bigram lookup succeeds (`prevWord != null && nextWords.isNotEmpty()`), (b) bigram lookup misses (`prevWord != null && nextWords.isEmpty()`), (c) no previous word OR prediction disabled (`prevWord == null || mSuggest == null || !isPredictionOn()`). The instrumentation MUST distinguish all three as separate `source` values.

2. Restructure the function to compute the emit payload via three locals threaded through the branches, then emit ONCE at the end of the function body (just before the final `return` / end of function — whichever comes first after `setSuggestions(...)` is called):
   ```kotlin
   // WHY: Phase 2.3 next-word test flow gates on this signal instead of a sleep.
   // FROM SPEC: §6 Phase 2 item 2.3 — "Predictive next-word (type word → space → verify candidate strip populated)"
   // IMPORTANT: PRIVACY — prev_word content MUST NOT appear in the payload. Length only.
   //            This mirrors the voice-instrumentation privacy contract from Session 42.
   var emitSource: String = "no_prev_word"
   var emitLen: Int = 0
   var emitCount: Int = 0
   ```

3. In each branch, assign the locals:
   - **Bigram hit** (inside the `if (nextWords.isNotEmpty())` block, BEFORE `setSuggestions(nextWords, ...)`):
     ```kotlin
     emitSource = "bigram_hit"
     emitLen = prevWord.length
     emitCount = nextWords.size
     ```
   - **Bigram miss** (the branch where `prevWord != null` but `nextWords.isEmpty()` — falls through to the `setSuggestions(mSuggestPuncList, ...)` path; detect it by capturing `prevWord != null` before the fallback call):
     ```kotlin
     emitSource = "bigram_miss"
     emitLen = prevWord.length  // prevWord is non-null here
     emitCount = 0
     ```
   - **No prev word / prediction disabled** (the branch where `prevWord == null` OR `mSuggest == null || !isPredictionOn()`): leave the defaults (`emitSource = "no_prev_word"`, `emitLen = 0`, `emitCount = 0`).

4. At the single emit point (end of the function, after all branches have assigned the locals), emit once:
   ```kotlin
   DevKeyLogger.text(
       "next_word_suggestions",
       mapOf(
           "prev_word_length" to emitLen,
           "result_count" to emitCount,
           "source" to emitSource
       )
   )
   ```

   This guarantees exactly ONE event per `setNextSuggestions` call and correctly distinguishes bigram-hit, bigram-miss, and no-prev-word paths.

### Sub-phase 1.5: Instrument `KeyPressLogger.logLongPress` for long-press flow gating

**Files:** `app/src/main/java/dev/devkey/keyboard/debug/KeyPressLogger.kt` (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Locate `KeyPressLogger.logLongPress` (grep for `logLongPress` in `KeyPressLogger.kt`). After the existing `Log.d(...)` emit, add a DevKeyLogger.text emit:
   ```kotlin
   // WHY: Phase 2.3 long-press coverage flow gates on this signal instead of a post-swipe sleep.
   // FROM SPEC: §6 Phase 2 item 2.3 — "Long-press popup coverage (every key in every mode)"
   // IMPORTANT: No content leakage — label is the primary key identity, lp_code is the long-press output code.
   //            These are non-secret UI structure values, safe to log.
   DevKeyLogger.text(
       "long_press_fired",
       mapOf(
           "label" to label,
           "code" to code,
           "lp_code" to longPressCode
       )
   )
   ```

2. Verify that `DevKeyLogger` is importable — add `import dev.devkey.keyboard.debug.DevKeyLogger` at the top if not already present.

### Sub-phase 1.6: Instrument `CandidateView` suggestion rendering for next-word UI gating

**Files:** `app/src/main/java/dev/devkey/keyboard/CandidateView.kt` (or wherever the candidate strip is rendered — legacy View, not Compose) (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Locate the candidate strip render/update site. `CandidateView` is a legacy Android `View`, not Compose. The implementing agent must grep for `class CandidateView` or `setSuggestions` / `mSuggestions` to find the current update path — exact line varies.

2. After the candidate strip is populated (at the end of `setSuggestions(...)` or the equivalent internal state update), emit:
   ```kotlin
   // WHY: Phase 2.3 next-word test asserts that the candidate strip actually rendered,
   //      not just that the prediction logic fired. This guards against regressions where
   //      setNextSuggestions runs but the UI strip never reflects the result.
   // IMPORTANT: PRIVACY — only the count is logged, never the suggestion strings themselves.
   DevKeyLogger.ui(
       "candidate_strip_rendered",
       mapOf("suggestion_count" to (mSuggestions?.size ?: 0))
   )
   ```

3. Exact emit site to be determined during implementation by grepping for the current `setSuggestions` / `mSuggestions` update path inside `CandidateView`. The requirement is: exactly one emit per strip-population, with the correct count.

### Sub-phase 1.7: Instrument `KeyMapGenerator.dumpToLogcat` for keymap-load gating

**Files:** `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt` (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Locate `KeyMapGenerator.dumpToLogcat` (grep in `KeyMapGenerator.kt`). At the END of the function (after all `Log.d` lines have been emitted and the loop over `bounds` is complete), add:
   ```kotlin
   // WHY: Phase 2 Python harness replaces its `time.sleep(0.3)` after the DUMP_KEY_MAP
   //      broadcast with a wait_for on this event — see tools/e2e/lib/keyboard.py load_key_map.
   DevKeyLogger.ime(
       "keymap_dump_complete",
       mapOf(
           "mode" to layoutMode.name,
           "key_count" to bounds.size
       )
   )
   ```

2. Verify `DevKeyLogger` is imported at the top of `KeyMapGenerator.kt`; add the import if missing.

### Sub-phase 1.8: Instrument layout-mode recomposition observer

**Files:** `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt` (modify)
**Agent:** `compose-ui-agent`

**Steps:**

1. Grep for `LaunchedEffect` in `DevKeyKeyboard.kt`. There is an existing `LaunchedEffect(layoutMode)` block used by the current `dumpToLogcatWhenReady` path — use that as the recomposition point. If absent, locate the outermost `LaunchedEffect(layoutMode) { ... }` in the composable that renders the keyboard body.

2. Inside the `LaunchedEffect(layoutMode)` block, AFTER the existing body (or after the Compose recomposition settles — typically at the end of the effect body), add:
   ```kotlin
   // WHY: Phase 2 Python harness replaces `time.sleep(0.8)` after SET_LAYOUT_MODE broadcast
   //      with a wait_for on this event — see tools/e2e/lib/keyboard.py set_layout_mode.
   // NOTE: LaunchedEffect runs on a keyed-restart basis; emitting here means the observer
   //       will see one event per layoutMode change after Compose has re-keyed the block.
   DevKeyLogger.ime(
       "layout_mode_recomposed",
       mapOf("mode" to layoutMode.name.lowercase())
   )
   ```

3. Verify the `DevKeyLogger` import is present at the top of the file; add if needed.

### Sub-phase 1.9: Add `DevKeyLoggerTest` unit test for `enableServer`

**Files:** `app/src/test/java/dev/devkey/keyboard/debug/DevKeyLoggerTest.kt` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create the test directory if it does not exist (`mkdir -p app/src/test/java/dev/devkey/keyboard/debug`), then create the file:
   ```kotlin
   package dev.devkey.keyboard.debug

   import org.junit.After
   import org.junit.Test
   import org.junit.Assert.assertNotNull
   import org.junit.Assert.assertNull
   import java.lang.reflect.Field

   /**
    * Minimal guard test for DevKeyLogger.enableServer / disableServer lifecycle.
    *
    * WHY: Phase 2.1 is the first production caller of DevKeyLogger.enableServer.
    *      Before this plan, the API had zero callers and zero tests.
    * NOTE: We use reflection to inspect the private serverUrl field because DevKeyLogger
    *       is an object singleton with no public getter. This is a test-only tradeoff
    *       justified by the fact that the field controls the HTTP-forwarding kill-switch.
    */
   class DevKeyLoggerTest {

       @After
       fun tearDown() {
           // IMPORTANT: Clear server state between tests — DevKeyLogger is a singleton.
           DevKeyLogger.disableServer()
       }

       @Test
       fun `enableServer sets serverUrl field`() {
           DevKeyLogger.enableServer("http://127.0.0.1:3947")
           assertNotNull("serverUrl should be set after enableServer", readServerUrl())
       }

       @Test
       fun `disableServer clears serverUrl field`() {
           DevKeyLogger.enableServer("http://127.0.0.1:3947")
           DevKeyLogger.disableServer()
           assertNull("serverUrl should be null after disableServer", readServerUrl())
       }

       @Test
       fun `category convenience methods are no-op when server disabled`() {
           // WHY: Precondition — no server means no crash, no HTTP call.
           //      The sendToServer coroutine path must early-return on null serverUrl.
           DevKeyLogger.disableServer()
           // These calls must not throw.
           DevKeyLogger.ime("test_event")
           DevKeyLogger.voice("test_event", mapOf("state" to "IDLE"))
           DevKeyLogger.error("test_event")
       }

       private fun readServerUrl(): String? {
           val field: Field = DevKeyLogger::class.java.getDeclaredField("serverUrl")
           field.isAccessible = true
           return field.get(DevKeyLogger) as String?
       }
   }
   ```

2. This test uses only `junit:junit:4.13.2` which is already in `app/build.gradle.kts` `testImplementation` (verified by grepping for `junit` in that file before writing). No new test dependencies.

**Verification:** `./gradlew assembleDebug`

---

## Phase 2 — Test driver server extension

Turns `tools/debug-server/server.js` into the wave-gating, ADB-dispatching, sleep-replacing driver server the spec calls for. The existing endpoints (`/log`, `/logs`, `/health`, `/categories`, `/clear`) stay untouched so the `/systematic-debugging` skill's deep-mode consumer keeps working. New endpoints are additive: `/wait`, `/adb/tap`, `/adb/swipe`, `/adb/broadcast`, `/adb/logcat`, `/screenshot`, `/wave/start`, `/wave/gate`, `/wave/status`.

### Sub-phase 2.1: Create wave-gate module

**Files:** `tools/debug-server/wave-gate.js` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create the file with a wave-state tracker and a long-poll `wait_for` primitive:
   ```javascript
   // WHY: Replaces sleep-based test synchronization with event-observed gating.
   // FROM SPEC: §6 Phase 2 item 2.2 — "gates wave progression on observed state rather than sleeps"
   // NOTE: Dep-free on purpose — existing debug server has zero npm deps.
   //       All matching happens in-memory over the already-buffered logs array.

   /**
    * Waits for a log entry matching (category, event, match) to appear in the provided
    * `logs` array. Returns a Promise that resolves with the matching entry or rejects on timeout.
    *
    * @param {Array} logs — the shared log buffer (POST /log appends to this)
    * @param {string} category — e.g. "DevKey/VOX"
    * @param {string|null} event — the `message` field on the entry (e.g. "state_transition")
    * @param {object} matchData — subset of the `data` map that must appear on the entry
    * @param {number} timeoutMs
    */
   function waitFor(logs, category, event, matchData, timeoutMs) {
       return new Promise((resolve, reject) => {
           const startIdx = logs.length;
           const deadline = Date.now() + timeoutMs;

           const check = () => {
               // NOTE: Only look at entries appended AFTER wait_for was called — prevents
               //       stale hits from a prior wave. Future waves clear the buffer via /clear.
               for (let i = startIdx; i < logs.length; i++) {
                   const entry = logs[i];
                   if (entry.category !== category) continue;
                   if (event && entry.message !== event) continue;
                   if (matchData && !matchesData(entry.data, matchData)) continue;
                   return resolve(entry);
               }
               if (Date.now() >= deadline) {
                   return reject(new Error(
                       `wait_for timeout after ${timeoutMs}ms — ` +
                       `category=${category} event=${event || '*'} ` +
                       `match=${JSON.stringify(matchData || {})}`
                   ));
               }
               setTimeout(check, 50);
           };
           check();
       });
   }

   /**
    * Returns true if every key in `needle` appears in `haystack` with equal stringified value.
    * DevKeyLogger stringifies all data values before sending, so comparison is string-based.
    */
   function matchesData(haystack, needle) {
       if (!haystack || typeof haystack !== 'object') return false;
       for (const [k, v] of Object.entries(needle)) {
           if (String(haystack[k]) !== String(v)) return false;
       }
       return true;
   }

   // --- Wave state tracker ---

   const waves = new Map(); // waveId -> { startIdx, startedAt, status, flows: [] }

   function startWave(logs, waveId) {
       waves.set(waveId, {
           startIdx: logs.length,
           startedAt: Date.now(),
           status: 'running',
           flows: []
       });
       return waves.get(waveId);
   }

   function getWave(waveId) {
       return waves.get(waveId) || null;
   }

   function finishWave(waveId, status) {
       const w = waves.get(waveId);
       if (w) {
           w.status = status;
           w.finishedAt = Date.now();
       }
   }

   function allWaves() {
       return Array.from(waves.entries()).map(([id, w]) => ({ id, ...w }));
   }

   module.exports = { waitFor, matchesData, startWave, getWave, finishWave, allWaves };
   ```

### Sub-phase 2.2: Create ADB exec module

**Files:** `tools/debug-server/adb-exec.js` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create the file with ADB command wrappers that the HTTP endpoints in 2.3 will use:
   ```javascript
   // WHY: Centralizes ADB subprocess dispatch so the HTTP layer stays thin.
   // NOTE: Uses execFile (not exec) to avoid shell interpretation of coordinate args.
   // IMPORTANT: Serial pinning — if ADB_SERIAL env var is set, every command gets `-s <serial>`.
   //            Tests running against multiple devices must set this before starting the driver.

   const { execFile } = require('child_process');
   const { promisify } = require('util');
   const execFileAsync = promisify(execFile);

   function adbArgs(extra) {
       const base = [];
       const serial = process.env.ADB_SERIAL;
       if (serial) {
           base.push('-s', serial);
       }
       return base.concat(extra);
   }

   async function run(args, timeoutMs = 5000) {
       try {
           const { stdout, stderr } = await execFileAsync('adb', adbArgs(args), {
               timeout: timeoutMs,
               maxBuffer: 4 * 1024 * 1024, // 4 MB for logcat dumps
           });
           return { ok: true, stdout, stderr };
       } catch (err) {
           return { ok: false, error: String(err.message), stderr: err.stderr || '' };
       }
   }

   async function tap(x, y) {
       return run(['shell', 'input', 'tap', String(x), String(y)]);
   }

   async function swipe(x1, y1, x2, y2, durationMs = 300) {
       return run([
           'shell', 'input', 'swipe',
           String(x1), String(y1), String(x2), String(y2), String(durationMs)
       ]);
   }

   async function broadcast(action, extras = {}) {
       // WHY: Phase 2.1 and 2.4 add ENABLE_DEBUG_SERVER and SET_LAYOUT_MODE broadcasts.
       //      The driver is the single issuer so tests don't shell out to adb directly.
       const args = ['shell', 'am', 'broadcast', '-a', action];
       for (const [k, v] of Object.entries(extras)) {
           args.push('--es', k, String(v));
       }
       return run(args);
   }

   async function dumpLogcat(tags) {
       // WHY: Dual-source strategy — driver scrapes legacy Log.d tags that don't flow through DevKeyLogger.
       //      Primary call site: 2.3 flows polling DevKeyPress/DevKeyMode/DevKeyMap/DevKeyBridge.
       // NOTE: We do `-d` (dump and exit), not streaming. Merging streaming logcat into the driver
       //       event queue is a Phase 4 follow-up; for Phase 2 the test runner drives the polling cadence.
       const args = ['logcat', '-d'];
       for (const tag of tags) {
           args.push('-s', `${tag}:*`);
       }
       return run(args);
   }

   async function clearLogcat() {
       return run(['logcat', '-c']);
   }

   async function screencap(outputPath) {
       // IMPORTANT: Git Bash path mangling workaround from .claude/skills/test/references/adb-commands.md.
       //            We write directly to the provided host path via exec-out, no /sdcard round-trip.
       // NOTE: `execFile` does NOT invoke a shell, so `>` redirection cannot be used here.
       //       Use `spawn` + `fs.createWriteStream` to pipe stdout directly to the output file.
       const { spawn } = require('child_process');
       const fs = require('fs');
       return new Promise((resolve) => {
           const proc = spawn('adb', adbArgs(['exec-out', 'screencap', '-p']));
           const out = fs.createWriteStream(outputPath);
           proc.stdout.pipe(out);
           proc.on('close', (code) => resolve({ ok: code === 0 }));
           proc.on('error', (err) => resolve({ ok: false, error: String(err.message) }));
       });
   }

   async function imeSet(component = 'dev.devkey.keyboard/.LatinIME') {
       return run(['shell', 'ime', 'set', component]);
   }

   async function forceStop(pkg = 'dev.devkey.keyboard') {
       return run(['shell', 'am', 'force-stop', pkg]);
   }

   async function pidof(pkg = 'dev.devkey.keyboard') {
       const result = await run(['shell', 'pidof', pkg]);
       if (!result.ok) return null;
       const pid = result.stdout.trim();
       return pid || null;
   }

   module.exports = {
       run, tap, swipe, broadcast, dumpLogcat, clearLogcat,
       screencap, imeSet, forceStop, pidof
   };
   ```

2. (Previous "Known limitation" note removed — step 1 now ships the canonical `spawn` + `fs.createWriteStream` pattern from day one.)

### Sub-phase 2.3: Extend `server.js` with new endpoints

**Files:** `tools/debug-server/server.js` (modify)
**Agent:** `general-purpose`

**Steps:**

1. At the top of `server.js` (after the existing `const http = require('http');` line), add the new imports:
   ```javascript
   // WHY: Phase 2.2 extension adds wave-gating + ADB dispatch endpoints.
   const waveGate = require('./wave-gate');
   const adb = require('./adb-exec');
   ```

1a. **CRITICAL** — fix the log-buffer trim to preserve array identity. Locate the existing `if (logs.length > MAX_ENTRIES) logs = logs.slice(-MAX_ENTRIES);` line inside the `/log` POST handler (server.js:25) and replace with:
   ```javascript
   if (logs.length > MAX_ENTRIES) logs.splice(0, logs.length - MAX_ENTRIES);
   ```
   WHY: The existing `logs = logs.slice(...)` reassigns the module-level `logs` binding. The wave-gate `waitFor` long-poll closures close over the ORIGINAL array reference — if the buffer is reassigned mid-wait, new `/log` appends land in a different array and the wait silently times out. In-place `splice` preserves array identity so long-polling `waitFor` closures continue to see appends.

2. Inside the main `http.createServer((req, res) => { ... })` callback, locate the existing `res.writeHead(404); res.end('{"error":"not found"}');` fallback at the bottom (server.js:78-79). Immediately BEFORE that fallback, insert the new endpoint handlers:

   ```javascript
   // ============================================================
   // Phase 2 extension — wave-gating + ADB dispatch endpoints.
   // ============================================================

   // GET /wait?category=DevKey/VOX&event=state_transition&match=<urlencoded-json>&timeout=5000
   //   Long-polls until a matching log entry appears, or timeout fires.
   if (req.method === 'GET' && url.pathname === '/wait') {
       const category = url.searchParams.get('category');
       const event = url.searchParams.get('event');
       const matchRaw = url.searchParams.get('match');
       const timeoutMs = parseInt(url.searchParams.get('timeout') || '5000');
       if (!category) {
           res.writeHead(400);
           res.end(JSON.stringify({ error: 'category required' }));
           return;
       }
       let matchData = null;
       if (matchRaw) {
           try { matchData = JSON.parse(matchRaw); }
           catch (e) {
               res.writeHead(400);
               res.end(JSON.stringify({ error: 'match must be valid JSON' }));
               return;
           }
       }
       waveGate.waitFor(logs, category, event, matchData, timeoutMs)
           .then(entry => {
               res.writeHead(200);
               res.end(JSON.stringify({ matched: true, entry }));
           })
           .catch(err => {
               res.writeHead(408); // Request Timeout
               res.end(JSON.stringify({ matched: false, error: err.message }));
           });
       return;
   }

   // POST /adb/tap?x=540&y=1775
   if (req.method === 'POST' && url.pathname === '/adb/tap') {
       const x = parseInt(url.searchParams.get('x'));
       const y = parseInt(url.searchParams.get('y'));
       if (Number.isNaN(x) || Number.isNaN(y)) {
           res.writeHead(400);
           res.end(JSON.stringify({ error: 'x and y required' }));
           return;
       }
       adb.tap(x, y).then(result => {
           res.writeHead(result.ok ? 200 : 500);
           res.end(JSON.stringify(result));
       });
       return;
   }

   // POST /adb/swipe?x1=X&y1=Y&x2=X&y2=Y&duration=300
   if (req.method === 'POST' && url.pathname === '/adb/swipe') {
       const x1 = parseInt(url.searchParams.get('x1'));
       const y1 = parseInt(url.searchParams.get('y1'));
       const x2 = parseInt(url.searchParams.get('x2'));
       const y2 = parseInt(url.searchParams.get('y2'));
       const duration = parseInt(url.searchParams.get('duration') || '300');
       if ([x1, y1, x2, y2].some(Number.isNaN)) {
           res.writeHead(400);
           res.end(JSON.stringify({ error: 'x1,y1,x2,y2 required' }));
           return;
       }
       adb.swipe(x1, y1, x2, y2, duration).then(result => {
           res.writeHead(result.ok ? 200 : 500);
           res.end(JSON.stringify(result));
       });
       return;
   }

   // POST /adb/broadcast (JSON body: {action, extras})
   if (req.method === 'POST' && url.pathname === '/adb/broadcast') {
       let body = '';
       req.on('data', chunk => body += chunk);
       req.on('end', () => {
           let parsed;
           try { parsed = JSON.parse(body || '{}'); }
           catch (e) {
               res.writeHead(400);
               res.end(JSON.stringify({ error: 'invalid JSON' }));
               return;
           }
           if (!parsed.action) {
               res.writeHead(400);
               res.end(JSON.stringify({ error: 'action required' }));
               return;
           }
           adb.broadcast(parsed.action, parsed.extras || {}).then(result => {
               res.writeHead(result.ok ? 200 : 500);
               res.end(JSON.stringify(result));
           });
       });
       return;
   }

   // GET /adb/logcat?tags=DevKeyPress,DevKeyMode,DevKeyMap,DevKeyBridge
   //   Dual-source observation for legacy Log.d tags that do NOT go through DevKeyLogger.
   if (req.method === 'GET' && url.pathname === '/adb/logcat') {
       const tagsRaw = url.searchParams.get('tags') || 'DevKeyPress';
       const tags = tagsRaw.split(',').map(t => t.trim()).filter(Boolean);
       adb.dumpLogcat(tags).then(result => {
           res.writeHead(result.ok ? 200 : 500);
           res.end(JSON.stringify(result));
       });
       return;
   }

   // POST /adb/logcat/clear
   if (req.method === 'POST' && url.pathname === '/adb/logcat/clear') {
       adb.clearLogcat().then(result => {
           res.writeHead(result.ok ? 200 : 500);
           res.end(JSON.stringify(result));
       });
       return;
   }

   // POST /wave/start?id=typing
   if (req.method === 'POST' && url.pathname === '/wave/start') {
       const waveId = url.searchParams.get('id');
       if (!waveId) {
           res.writeHead(400);
           res.end(JSON.stringify({ error: 'id required' }));
           return;
       }
       const wave = waveGate.startWave(logs, waveId);
       res.writeHead(200);
       res.end(JSON.stringify(wave));
       return;
   }

   // POST /wave/finish?id=typing&status=pass
   if (req.method === 'POST' && url.pathname === '/wave/finish') {
       const waveId = url.searchParams.get('id');
       const status = url.searchParams.get('status') || 'pass';
       if (!waveId) {
           res.writeHead(400);
           res.end(JSON.stringify({ error: 'id required' }));
           return;
       }
       waveGate.finishWave(waveId, status);
       res.writeHead(200);
       res.end(JSON.stringify({ ok: true, id: waveId, status }));
       return;
   }

   // GET /wave/status
   if (url.pathname === '/wave/status') {
       res.writeHead(200);
       res.end(JSON.stringify(waveGate.allWaves()));
       return;
   }
   ```

3. Update the existing startup log line to advertise the new endpoints:
   ```javascript
   // Replace the existing server.listen callback body:
   server.listen(PORT, '127.0.0.1', () => {
       console.log(`DevKey driver server listening on http://127.0.0.1:${PORT}`);
       console.log(`  Log:      POST /log  GET /logs  GET /health  GET /categories  POST /clear`);
       console.log(`  Wait:     GET /wait?category=X&event=Y&match=<json>&timeout=<ms>`);
       console.log(`  ADB:      POST /adb/tap  POST /adb/swipe  POST /adb/broadcast`);
       console.log(`            GET /adb/logcat?tags=...  POST /adb/logcat/clear`);
       console.log(`  Waves:    POST /wave/start?id=  POST /wave/finish?id=&status=  GET /wave/status`);
       console.log(`  ADB serial: ${process.env.ADB_SERIAL || '(default device)'}`);
   });
   ```

### Sub-phase 2.4: Add `package.json` and README update

**Files:** `tools/debug-server/package.json` (create), `tools/debug-server/README.md` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/debug-server/package.json`:
   ```json
   {
     "name": "devkey-debug-server",
     "version": "0.2.0",
     "description": "DevKey local debug + test driver server. Receives structured logs from DevKeyLogger over HTTP, dispatches ADB commands, and exposes wave-gating primitives for the Phase 2 regression harness.",
     "main": "server.js",
     "private": true,
     "scripts": {
       "start": "node server.js"
     },
     "engines": {
       "node": ">=18"
     }
   }
   ```

2. Create `tools/debug-server/README.md`:
   ```markdown
   # DevKey Debug + Test Driver Server

   Local HTTP server for DevKey test infrastructure. Binds `127.0.0.1:3947`, zero npm deps, dep-free on purpose.

   ## Roles
   1. **Log sink** — receives structured log entries from `DevKeyLogger` (Android-side HTTP client in `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`).
   2. **Wave-gating primitive** — long-poll `/wait` endpoint that blocks until a matching log entry arrives, replacing sleep-based test synchronization.
   3. **ADB dispatcher** — centralizes `adb` subprocess calls for the Python test harness in `tools/e2e/`.

   ## Run

       node server.js

   With a specific device:

       ADB_SERIAL=emulator-5554 node server.js

   ## Enabling HTTP forwarding on the device

   The driver server receives nothing until the running IME is told to forward logs. Use the `ENABLE_DEBUG_SERVER` broadcast (debug builds only):

       # Emulator (host-loopback alias)
       adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://10.0.2.2:3947

       # Physical device — forward the host port first, then broadcast the forwarded URL
       adb reverse tcp:3947 tcp:3947
       adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://127.0.0.1:3947

   To disable (broadcast with no `url` extra):

       adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER

   ## Switching layout modes

       adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact
       adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact_dev
       adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode full

   ## Endpoints

   ### Legacy (Phase 1 debug server — keeps working)

   | Method | Path | Purpose |
   |---|---|---|
   | POST | `/log` | Append a log entry (JSON body) |
   | GET | `/logs?last=N&category=X&hypothesis=H` | Filtered tail |
   | GET | `/health` | `{status, entries, maxEntries, memoryMB, uptimeSeconds}` |
   | GET | `/categories` | `{category: count}` |
   | POST | `/clear` | Drop buffer |

   ### Phase 2 extension

   | Method | Path | Purpose |
   |---|---|---|
   | GET | `/wait?category=X&event=Y&match=<urlencoded-json>&timeout=<ms>` | Long-poll until matching entry arrives |
   | POST | `/adb/tap?x=X&y=Y` | Tap screen coordinate |
   | POST | `/adb/swipe?x1=&y1=&x2=&y2=&duration=` | Swipe / long-press |
   | POST | `/adb/broadcast` (JSON body `{action, extras}`) | Dispatch `am broadcast` |
   | GET | `/adb/logcat?tags=DevKeyPress,DevKeyMode,...` | Dump logcat for legacy tags |
   | POST | `/adb/logcat/clear` | `logcat -c` |
   | POST | `/wave/start?id=<name>` | Mark the start of a test wave |
   | POST | `/wave/finish?id=<name>&status=pass` | Record wave completion |
   | GET | `/wave/status` | List all waves |

   ## Privacy contract

   DevKey IMEs see every keystroke, including passwords and PII. The HTTP log endpoint accepts ONLY structural data (state names, lengths, counts, durations). NEVER log typed text, transcripts, audio samples, or buffer bytes — see `DevKeyLogger.kt` privacy comment.

   This server exposes a local ADB convenience layer on 127.0.0.1:3947. The `/adb/logcat` endpoint can return `DevKeyPress` KEY/LONG lines, which on debug builds contain a live stream of keystrokes. Any process on this machine can poll `/adb/logcat`. DO NOT run the driver server on a workstation where untrusted local processes exist. Stop the driver server between sessions.
   ```

**Verification:** `node --check tools/debug-server/server.js && node --check tools/debug-server/wave-gate.js && node --check tools/debug-server/adb-exec.js && ./gradlew assembleDebug`

---

## Phase 3 — Python harness upgrade + driver HTTP client

Fixes the broken `tools/e2e/lib/keyboard.py` regex, adds a driver-server HTTP client that replaces sleep-based assertions with `/wait` calls, adds SSIM visual diff + audio injection + layout-mode switching helpers, and creates a `requirements.txt` for the new Python deps.

### Sub-phase 3.1: Fix `load_key_map` regex + broadcast trigger

**Files:** `tools/e2e/lib/keyboard.py` (modify)
**Agent:** `general-purpose`

**Steps:**

1. Replace the body of `load_key_map` (tools/e2e/lib/keyboard.py:23-52) with a version that (a) triggers the `DUMP_KEY_MAP` broadcast before reading logcat, (b) uses the correct current `KeyMapGenerator` output format, (c) supports both label-keyed (`a`) and code-keyed (`code_-1`) lookups:

   ```python
   def load_key_map(serial: Optional[str] = None) -> Dict[str, Tuple[int, int]]:
       """
       Load the key coordinate map from DevKey's KeyMapGenerator logcat output.

       The app dumps key positions to logcat with tag 'DevKeyMap' in response to
       a broadcast (debug builds only). Format per line:
           DevKeyMap: KEY label=<label> code=<code> x=<x> y=<y>

       Returns a dict keyed by both label and "code_<code>" for flexible lookup.
       """
       global _key_map

       # WHY: DevKeyMap only dumps on demand. Prior version (pre-Phase 2) relied on the
       #      dump happening at IME boot, which was unreliable and format-incompatible.
       # FROM SPEC: §6 Phase 2 item 2.4 — reliable calibration across FULL/COMPACT/COMPACT_DEV.
       adb.clear_logcat(serial)
       cmd = ["shell", "am", "broadcast", "-a", "dev.devkey.keyboard.DUMP_KEY_MAP"]
       if serial:
           cmd = ["-s", serial] + cmd
       import subprocess
       subprocess.run(["adb"] + cmd, check=True, capture_output=True)
       # WHY: Replace the legacy `time.sleep(0.3)` with a proper wave-gate on the
       #     `keymap_dump_complete` event emitted from KeyMapGenerator.dumpToLogcat
       #     (added in Phase 1 sub-phase 1.7).
       from lib import driver as _driver
       try:
           _driver.wait_for("DevKey/IME", "keymap_dump_complete", timeout_ms=3000)
       except _driver.DriverError:
           # Driver may not be enabled for this run — fall back to a bounded poll
           # against logcat itself (the subsequent capture_logcat call handles it).
           pass

       lines = adb.capture_logcat("DevKeyMap", timeout=2.0, serial=serial)

       # Parse format: "KEY label=<label> code=<code> x=<x> y=<y>"
       key_map: Dict[str, Tuple[int, int]] = {}
       pattern = re.compile(r"KEY label=(\S+) code=(-?\d+) x=(\d+) y=(\d+)")
       for line in lines:
           m = pattern.search(line)
           if m:
               label = m.group(1)
               code = int(m.group(2))
               x = int(m.group(3))
               y = int(m.group(4))
               key_map[label] = (x, y)
               key_map[f"code_{code}"] = (x, y)

       _key_map = key_map
       return key_map
   ```

2. Verify the existing imports at the top of `keyboard.py` already include `re`, `time`, `Optional`, `Dict`, `Tuple` (they do per the tailor source excerpt). No new imports needed at module level — the `subprocess` import is hoisted into the function body to keep the diff small; if a reviewer prefers module-level, move it up.

### Sub-phase 3.2: Add driver HTTP client

**Files:** `tools/e2e/lib/driver.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/e2e/lib/driver.py`:
   ```python
   """
   HTTP client for the DevKey driver server (tools/debug-server/server.js).

   Replaces sleep-based ADB + logcat assertions with long-polling /wait calls.
   """
   import json
   import os
   import time
   from typing import Any, Dict, Optional
   from urllib import request, parse, error

   DRIVER_URL = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3947")


   class DriverError(Exception):
       pass


   class DriverTimeout(DriverError):
       pass


   def _get(path: str, params: Optional[Dict[str, Any]] = None, timeout: float = 30.0) -> Dict:
       qs = ""
       if params:
           qs = "?" + parse.urlencode({k: v for k, v in params.items() if v is not None})
       url = f"{DRIVER_URL}{path}{qs}"
       try:
           with request.urlopen(url, timeout=timeout) as resp:
               body = resp.read().decode("utf-8")
               return json.loads(body) if body else {}
       except error.HTTPError as e:
           body = e.read().decode("utf-8", errors="replace")
           if e.code == 408:
               raise DriverTimeout(body)
           raise DriverError(f"HTTP {e.code}: {body}")
       except error.URLError as e:
           raise DriverError(f"driver unreachable at {url}: {e}")


   def _post(path: str, params: Optional[Dict[str, Any]] = None,
             body: Optional[Dict] = None, timeout: float = 10.0) -> Dict:
       qs = ""
       if params:
           qs = "?" + parse.urlencode({k: v for k, v in params.items() if v is not None})
       url = f"{DRIVER_URL}{path}{qs}"
       data = json.dumps(body).encode("utf-8") if body is not None else b""
       req = request.Request(url, data=data, method="POST")
       if body is not None:
           req.add_header("Content-Type", "application/json")
       try:
           with request.urlopen(req, timeout=timeout) as resp:
               respbody = resp.read().decode("utf-8")
               return json.loads(respbody) if respbody else {}
       except error.HTTPError as e:
           body = e.read().decode("utf-8", errors="replace")
           raise DriverError(f"HTTP {e.code}: {body}")
       except error.URLError as e:
           raise DriverError(f"driver unreachable at {url}: {e}")


   # --- Health / lifecycle ---

   def health() -> Dict:
       return _get("/health", timeout=2.0)


   def require_driver() -> None:
       """Assert the driver is reachable. Call at the start of every test."""
       try:
           h = health()
           if h.get("status") != "ok":
               raise DriverError(f"driver health check returned: {h}")
       except DriverError as e:
           raise DriverError(
               f"DevKey driver server not reachable. "
               f"Start it with: node tools/debug-server/server.js\n"
               f"Underlying error: {e}"
           )


   def clear_logs() -> None:
       _post("/clear")


   # --- Wave gating ---

   def wait_for(category: str, event: Optional[str] = None,
                match: Optional[Dict] = None, timeout_ms: int = 5000) -> Dict:
       """
       Long-poll the driver for a matching log entry.

       Raises DriverTimeout if no entry matches within timeout_ms.
       """
       params = {"category": category, "timeout": timeout_ms}
       if event:
           params["event"] = event
       if match:
           params["match"] = json.dumps(match)
       # Python-side timeout is 2x server-side — catches the case where the
       # server's own timeout fires slightly late without dropping the wait.
       result = _get("/wait", params=params, timeout=(timeout_ms / 1000.0) + 5.0)
       if not result.get("matched"):
           raise DriverTimeout(result.get("error", "unknown"))
       return result["entry"]


   # --- ADB dispatch ---

   def tap(x: int, y: int) -> Dict:
       return _post("/adb/tap", params={"x": x, "y": y})


   def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300) -> Dict:
       return _post("/adb/swipe", params={
           "x1": x1, "y1": y1, "x2": x2, "y2": y2, "duration": duration_ms
       })


   def broadcast(action: str, extras: Optional[Dict[str, str]] = None) -> Dict:
       return _post("/adb/broadcast", body={"action": action, "extras": extras or {}})


   def logcat_dump(tags: list) -> str:
       # NOTE: Do NOT clear logcat here — that would wipe the events the caller wants to read.
       #       Callers that need a clean baseline must call `logcat_clear()` explicitly BEFORE
       #       the action that produces the events they expect to dump.
       result = _get("/adb/logcat", params={"tags": ",".join(tags)})
       return result.get("stdout", "")


   def logcat_clear() -> None:
       _post("/adb/logcat/clear")


   # --- Wave lifecycle ---

   def wave_start(wave_id: str) -> Dict:
       return _post("/wave/start", params={"id": wave_id})


   def wave_finish(wave_id: str, status: str = "pass") -> Dict:
       return _post("/wave/finish", params={"id": wave_id, "status": status})
   ```

### Sub-phase 3.3: Add audio injection helper

**Files:** `tools/e2e/lib/audio.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create a minimal audio-injection helper that supports the two realistic test modes: (a) silent audio (to exercise the "empty_audio" path), (b) pre-recorded WAV file played through the emulator's host audio input. Full synthetic speech is out of scope for Phase 2 — the voice round-trip flow asserts that the state machine traverses LISTENING → PROCESSING → IDLE and that *some* text is committed, not that the transcription is correct.

   ```python
   """
   Audio injection helpers for voice round-trip testing.

   Emulator mode: play a WAV file on the host audio device so the guest mic picks it up.
   Physical device mode: the test is manual — audio cannot be injected programmatically
                         without hardware. The flow auto-skips with a warning.
   """
   import os
   import shutil
   import subprocess
   from typing import Optional

   # Default sample lives next to the test modules so users can replace it.
   DEFAULT_SAMPLE = os.path.join(
       os.path.dirname(os.path.abspath(__file__)),
       "..", "fixtures", "voice-hello.wav"
   )


   def is_emulator(serial: Optional[str] = None) -> bool:
       """Best-effort emulator detection via getprop ro.kernel.qemu."""
       cmd = ["adb"]
       if serial:
           cmd += ["-s", serial]
       cmd += ["shell", "getprop", "ro.kernel.qemu"]
       result = subprocess.run(cmd, capture_output=True, text=True)
       return result.stdout.strip() == "1"


   def inject_sample(sample_path: str = DEFAULT_SAMPLE) -> bool:
       """
       Play `sample_path` on the host so the emulator's guest mic captures it.

       Returns True if playback succeeded, False if skipped (no tool available
       or sample missing). Does NOT raise — the caller decides whether a skip is fatal.
       """
       if not os.path.exists(sample_path):
           return False

       # NOTE: Different host OSs have different CLI audio players. Try ffplay first
       #       (cross-platform, quiet mode), fall back to aplay (Linux), afplay (macOS).
       for player, args in [
           ("ffplay", ["-nodisp", "-autoexit", "-loglevel", "quiet", sample_path]),
           ("aplay", [sample_path]),
           ("afplay", [sample_path]),
       ]:
           if shutil.which(player):
               try:
                   subprocess.run([player] + args, check=True, timeout=10)
                   return True
               except subprocess.CalledProcessError:
                   continue
       return False
   ```

2. Create an empty `tools/e2e/fixtures/` directory with a `.gitkeep` file — the actual `voice-hello.wav` is a per-developer asset (the test handles missing samples gracefully).
   ```bash
   mkdir -p tools/e2e/fixtures && touch tools/e2e/fixtures/.gitkeep
   ```

### Sub-phase 3.4: Add SSIM visual diff helper

**Files:** `tools/e2e/lib/diff.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/e2e/lib/diff.py`:
   ```python
   """
   Visual diff helpers for SwiftKey parity testing.

   Uses scikit-image's structural_similarity (SSIM) with a default threshold of 0.92.
   Override via env var DEVKEY_SSIM_THRESHOLD.
   """
   import os
   from typing import Tuple, Optional

   DEFAULT_THRESHOLD = float(os.environ.get("DEVKEY_SSIM_THRESHOLD", "0.92"))


   def ssim(actual_path: str, reference_path: str) -> float:
       """
       Compute SSIM between two image files.

       Raises FileNotFoundError if either file is missing — callers decide
       whether missing-reference is a SKIP (Phase 2 default) or a FAIL.
       """
       # WHY: scikit-image + Pillow are imported lazily so that the rest of the
       #      Python harness still works even if the visual-diff deps aren't installed.
       #      Unrelated flows shouldn't fail-hard at import time.
       from skimage.metrics import structural_similarity as _ssim
       from PIL import Image
       import numpy as np

       if not os.path.exists(actual_path):
           raise FileNotFoundError(f"actual image missing: {actual_path}")
       if not os.path.exists(reference_path):
           raise FileNotFoundError(f"reference image missing: {reference_path}")

       a = np.array(Image.open(actual_path).convert("RGB"))
       b = np.array(Image.open(reference_path).convert("RGB"))

       # NOTE: If sizes differ, resize the reference to match the actual.
       #       Different emulator DPIs produce different screen sizes; parity
       #       is about visual structure, not pixel-exact dimensions.
       if a.shape != b.shape:
           b_img = Image.open(reference_path).convert("RGB").resize(
               (a.shape[1], a.shape[0]), Image.BICUBIC
           )
           b = np.array(b_img)

       # channel_axis=-1 tells scikit-image this is an RGB image, not grayscale.
       score, _ = _ssim(a, b, channel_axis=-1, full=True)
       return float(score)


   def assert_ssim(actual_path: str, reference_path: str,
                   threshold: Optional[float] = None) -> Tuple[bool, float]:
       """
       Returns (passed, score). `passed` is True if score >= threshold.

       Never raises on SSIM — only on missing files.
       """
       t = threshold if threshold is not None else DEFAULT_THRESHOLD
       score = ssim(actual_path, reference_path)
       return (score >= t, score)
   ```

### Sub-phase 3.5: Add `requirements.txt` and README update

**Files:** `tools/e2e/requirements.txt` (create), `tools/e2e/README.md` (modify)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/e2e/requirements.txt`:
   ```
   # DevKey E2E Python test harness dependencies.
   # Phase 2 introduces visual-diff and image processing.

   scikit-image>=0.21
   Pillow>=10.0
   numpy>=1.24
   ```

   **NOTE**: `requests` is intentionally NOT listed — `lib/driver.py` uses the stdlib `urllib` module to keep the install footprint small. If implementing agents find `urllib` painful, `requests` can be added in a follow-up.

2. Append a "Phase 2 Infrastructure" section to `tools/e2e/README.md` (after the existing "Environment Variables" section, before "Coordinate Calibration"):
   ```markdown
   ## Phase 2 Infrastructure (Driver Server + Visual Diff)

   Phase 2 of the v1.0 pre-release adds a driver-server architecture that replaces
   sleep-based synchronization with log-event-gated waits, plus SSIM visual diff
   for SwiftKey parity testing.

   ### Prerequisites

       pip install -r tools/e2e/requirements.txt

   ### Running a test suite

       # Terminal 1 — start the driver server
       node tools/debug-server/server.js

       # Terminal 2 — enable HTTP forwarding on the running IME (emulator)
       adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER \
           --es url http://10.0.2.2:3947

       # Terminal 3 — run the tests
       python tools/e2e/e2e_runner.py

   ### Switching layout modes during a run

       adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact

   ### Environment variables

   | Variable | Default | Purpose |
   |----------|---------|---------|
   | `DEVKEY_DEVICE_SERIAL` | (first device) | ADB target |
   | `DEVKEY_DRIVER_URL` | `http://127.0.0.1:3947` | Driver server URL |
   | `DEVKEY_SSIM_THRESHOLD` | `0.92` | Visual diff pass threshold |
   | `DEVKEY_E2E_VERBOSE` | unset | Verbose tracebacks |
   ```

### Sub-phase 3.6: Add layout-mode switching helper

**Files:** `tools/e2e/lib/keyboard.py` (modify)
**Agent:** `general-purpose`

**Steps:**

1. At the bottom of `tools/e2e/lib/keyboard.py`, add:
   ```python
   def set_layout_mode(mode: str, serial: Optional[str] = None) -> None:
       """
       Switch the running IME to the given layout mode via debug broadcast.

       Valid values: "full", "compact", "compact_dev".
       FROM SPEC: §6 Phase 2 item 2.4 — programmatic layout-mode switching.

       WARNING: Blocks ~3s while the broadcast dispatches, Compose recomposes,
                and the key map is reloaded. Callers driving tight assertions
                should broadcast directly via `driver.broadcast(...)` + `driver.wait_for(...)`
                rather than calling this helper.
       """
       if mode not in ("full", "compact", "compact_dev"):
           raise ValueError(f"invalid layout mode: {mode}")

       import subprocess
       cmd = ["adb"]
       if serial:
           cmd += ["-s", serial]
       cmd += [
           "shell", "am", "broadcast",
           "-a", "dev.devkey.keyboard.SET_LAYOUT_MODE",
           "--es", "mode", mode,
       ]
       subprocess.run(cmd, check=True, capture_output=True)

       # WHY: Wave-gate on the `layout_mode_recomposed` event emitted from
       #      DevKeyKeyboard.kt's LaunchedEffect(layoutMode) block (added in
       #      Phase 1 sub-phase 1.8) — zero sleeps.
       from lib import driver as _driver
       try:
           _driver.wait_for(
               "DevKey/IME",
               "layout_mode_recomposed",
               match={"mode": mode},
               timeout_ms=5000,
           )
       except _driver.DriverError:
           # Driver not available — callers running without HTTP forwarding
           # must accept that this helper is best-effort. Log and continue.
           pass

       # Re-load key map for the new mode.
       load_key_map(serial)
   ```

**Verification:** `python -m py_compile tools/e2e/lib/keyboard.py tools/e2e/lib/driver.py tools/e2e/lib/audio.py tools/e2e/lib/diff.py`

### Sub-phase 3.7: Auto-enable driver + HTTP forwarding in `e2e_runner.py`

**Files:** `tools/e2e/e2e_runner.py` (modify), `tools/e2e/README.md` (modify)
**Agent:** `general-purpose`

**Steps:**

1. Modify `tools/e2e/e2e_runner.py` so that at startup — BEFORE running any tests — it:
   - Calls `driver.require_driver()` to fail-fast if the driver server isn't running.
   - Issues the `ENABLE_DEBUG_SERVER` broadcast automatically so developers don't need to remember the incantation.

   Insert near the top of `main()` (or the script entry point):
   ```python
   import os
   from lib import driver

   # F7: fail-fast on missing driver + auto-enable HTTP forwarding.
   driver.require_driver()
   _driver_url = os.environ.get("DEVKEY_DRIVER_URL", "http://10.0.2.2:3947")
   driver.broadcast(
       "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
       {"url": _driver_url},
   )
   ```

2. Update `tools/e2e/README.md` Phase 2 section:
   - REMOVE the manual broadcast step from "Running a test suite" (the runner does it automatically now).
   - ADD a "Troubleshooting" section that documents the manual broadcast for diagnostic scenarios where the runner is bypassed.

**Verification:** `python tools/e2e/e2e_runner.py --list && ./gradlew assembleDebug`

---

## Phase 4 — New test flows (voice / next-word / long-press / visual diff)

Adds one test module per new flow. Each module imports `from lib import driver, keyboard, adb` (plus `audio` / `diff` where needed) and uses `driver.wait_for(...)` exclusively for synchronization — zero new sleeps beyond the preference-recompose exception in 3.6.

### Sub-phase 4.1: Voice round-trip test module

**Files:** `tools/e2e/tests/test_voice.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/e2e/tests/test_voice.py`:
   ```python
   """
   Voice round-trip tests.

   FROM SPEC: §6 Phase 2 item 2.3 — "Voice round-trip (tap mic → speak → verify committed text)"

   Depends on:
     - VoiceInputEngine state-transition instrumentation (landed Session 42)
     - ENABLE_DEBUG_SERVER broadcast (Phase 2 sub-phase 1.1)
     - driver server running on $DEVKEY_DRIVER_URL

   Skips gracefully when:
     - Whisper model files are missing (voice button disabled)
     - Audio injection tool is unavailable (physical devices)
   """
   import os
   from lib import adb, keyboard, driver, audio

   KEYCODE_VOICE = -102  # from KeyData.kt:148


   def _setup_voice_button():
       serial = adb.get_device_serial()
       driver.require_driver()
       if not keyboard.get_key_map():
           keyboard.load_key_map(serial)
       # Precondition: voice button key present in the map.
       voice_code_key = f"code_{KEYCODE_VOICE}"
       if voice_code_key not in keyboard.get_key_map():
           raise AssertionError(
               f"Voice key (code {KEYCODE_VOICE}) not found in key map. "
               f"Is the keyboard in a mode that shows it?"
           )
       return serial, voice_code_key


   def test_voice_state_machine_to_listening():
       """Tap mic → VoiceInputEngine transitions IDLE → LISTENING."""
       serial, voice_key = _setup_voice_button()
       driver.clear_logs()
       keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
       driver.wait_for(
           category="DevKey/VOX",
           event="state_transition",
           match={"state": "LISTENING", "source": "startListening"},
           timeout_ms=3000,
       )


   def test_voice_cancel_returns_to_idle():
       """After tapping mic and cancelling, state returns to IDLE."""
       serial, voice_key = _setup_voice_button()
       driver.clear_logs()
       keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
       driver.wait_for("DevKey/VOX", "state_transition",
                       match={"state": "LISTENING"}, timeout_ms=3000)
       # Re-tapping the voice key toggles cancel (DevKeyKeyboard.kt:156-158).
       keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
       driver.wait_for("DevKey/VOX", "state_transition",
                       match={"state": "IDLE", "source": "cancelListening"},
                       timeout_ms=3000)


   def test_voice_round_trip_committed_text():
       """
       Full round-trip: tap mic → inject audio → silence-detect → processing → commit.

       SKIP if audio injection is unavailable or Whisper model is missing.
       """
       serial, voice_key = _setup_voice_button()
       driver.clear_logs()

       keyboard.tap_key_by_code(KEYCODE_VOICE, serial)
       try:
           driver.wait_for("DevKey/VOX", "state_transition",
                           match={"state": "LISTENING"}, timeout_ms=3000)
       except driver.DriverTimeout:
           # Voice button tapped but LISTENING never fired — either permission
           # prompt blocked the flow, or voice is disabled. Check for an ERROR event
           # with reason=permission_denied to distinguish.
           import pytest  # lazy import — we only need pytest for the skip sentinel
           pytest.skip("LISTENING state never reached (permission or model missing)")

       # Inject audio — on physical devices this is a no-op.
       if not audio.is_emulator(serial):
           import pytest
           pytest.skip("audio injection requires an emulator")
       if not audio.inject_sample():
           import pytest
           pytest.skip("audio sample not available or no host audio player found")

       # Wait for silence-detection or manual stop — then processing → IDLE.
       driver.wait_for("DevKey/VOX", "state_transition",
                       match={"state": "PROCESSING"}, timeout_ms=10000)
       entry = driver.wait_for("DevKey/VOX", "processing_complete",
                               timeout_ms=15000)
       result_length = int(entry["data"].get("result_length", 0))
       # PRIVACY: we assert on length, never content.
       assert result_length > 0, "processing_complete had zero result_length"

       driver.wait_for("DevKey/VOX", "state_transition",
                       match={"state": "IDLE", "reason": "inference_complete"},
                       timeout_ms=5000)

       # F1 (completeness): verify committed text actually landed in the text field.
       # PRIVACY: assert non-empty length only — NEVER assert on specific content.
       committed = adb.get_text_field_content(serial)
       assert committed, (
           f"Expected non-empty committed text after voice round-trip, got: '{committed}'"
       )
   ```

### Sub-phase 4.2: Next-word prediction test module

**Files:** `tools/e2e/tests/test_next_word.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/e2e/tests/test_next_word.py`:
   ```python
   """
   Next-word prediction tests.

   FROM SPEC: §6 Phase 2 item 2.3 — "Predictive next-word (type word → space → verify
              candidate strip populated with next-word suggestions)"

   Depends on:
     - setNextSuggestions instrumentation added in Phase 2 sub-phase 1.3
     - HTTP forwarding enabled (sub-phase 1.1)
   """
   from lib import adb, keyboard, driver

   SPACE_CODE = 32


   def _setup():
       serial = adb.get_device_serial()
       driver.require_driver()
       if not keyboard.get_key_map():
           keyboard.load_key_map(serial)
       return serial


   def test_next_word_fires_after_space():
       """
       Typing a known word then space must emit at least one next_word_suggestions event.

       The event MAY come from either the bigram path (result_count >= 1) or the
       fallback path (result_count = 0) — this test asserts the SIGNAL exists,
       not which path fired.
       """
       serial = _setup()
       driver.clear_logs()
       # Type "the" — a common word with many bigrams in the default dict.
       for ch in "the":
           keyboard.tap_key(ch, serial)
       keyboard.tap_key_by_code(SPACE_CODE, serial)

       entry = driver.wait_for(
           category="DevKey/TXT",
           event="next_word_suggestions",
           timeout_ms=3000,
       )
       assert entry["data"]["source"] in {"bigram_hit", "bigram_miss", "no_prev_word"}

       # F3 (completeness): verify the candidate strip actually rendered, not just
       # that setNextSuggestions fired. Instrumentation added in Phase 1 sub-phase 1.6.
       strip = driver.wait_for(
           category="DevKey/UI",
           event="candidate_strip_rendered",
           timeout_ms=3000,
       )
       # On the bigram-hit path, at least one suggestion must be visible.
       if entry["data"]["source"] == "bigram_hit":
           suggestion_count = int(strip["data"].get("suggestion_count", 0))
           assert suggestion_count >= 1, (
               f"bigram_hit fired but candidate_strip_rendered reported "
               f"suggestion_count={suggestion_count} (expected >=1)"
           )


   def test_next_word_bigram_hit_for_common_word():
       """
       Typing 'the' + space MUST hit the bigram path with result_count >= 1.

       F9 (completeness): this test FAILs rather than SKIPs if "the" doesn't produce
       a bigram hit — that's a real defect in the prediction wiring, not a test
       infrastructure problem. If the default dictionary legitimately lacks bigrams
       for "the", the test author must switch to a word with guaranteed bigrams.
       """
       serial = _setup()
       driver.clear_logs()
       for ch in "the":
           keyboard.tap_key(ch, serial)
       keyboard.tap_key_by_code(SPACE_CODE, serial)

       entry = driver.wait_for("DevKey/TXT", "next_word_suggestions", timeout_ms=3000)
       data = entry["data"]
       assert data["source"] == "bigram_hit", (
           f"Expected source='bigram_hit' for 'the' + space, got source={data['source']}. "
           f"This indicates a defect in the prediction wiring, not a test problem."
       )
       result_count = int(data["result_count"])
       assert result_count >= 1, (
           f"bigram_hit reported result_count={result_count}, expected >=1"
       )


   def test_next_word_privacy_payload_is_structural_only():
       """
       The next_word_suggestions payload must contain ONLY integer/string metadata.

       Guards against a regression where someone logs the actual candidate words.
       """
       serial = _setup()
       driver.clear_logs()
       for ch in "hi":
           keyboard.tap_key(ch, serial)
       keyboard.tap_key_by_code(SPACE_CODE, serial)

       entry = driver.wait_for("DevKey/TXT", "next_word_suggestions", timeout_ms=3000)
       data = entry["data"]
       allowed_keys = {"prev_word_length", "result_count", "source"}
       extra = set(data.keys()) - allowed_keys
       assert not extra, (
           f"next_word_suggestions payload contained unexpected keys: {extra}. "
           f"PRIVACY: payload must contain only prev_word_length, result_count, source."
       )
   ```

### Sub-phase 4.3: Long-press coverage test module

**Files:** `tools/e2e/tests/test_long_press.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. **Create long-press expectation files** at `.claude/test-flows/long-press-expectations/`:
   - `README.md` explaining the format (JSON list of `{label, lp_codes: [int]}` entries derived from current `QwertyLayout.buildXxxLayout()` and `SymbolsLayout.kt` sources — the implementing agent must read those Kotlin sources and hand-transcribe the long-press data because jcodemunch's Kotlin resolver cannot statically enumerate them).
   - `full.json` — every key in FULL mode with its expected `lp_codes`.
   - `compact.json` — COMPACT mode.
   - `compact_dev.json` — COMPACT_DEV mode (includes digit long-press on the QWERTY row).
   - `symbols.json` — SYMBOLS layer.

2. Create `tools/e2e/tests/test_long_press.py`:
   ```python
   """
   Long-press popup coverage tests — every key in every mode (F2).

   FROM SPEC: §6 Phase 2 item 2.3 — "Long-press popup coverage (every key in every mode)"

   Strategy:
     - Read expected long-press data from .claude/test-flows/long-press-expectations/<mode>.json
     - For each key, swipe-hold 500ms to trigger long-press
     - Assert DevKey/TXT long_press_fired event (from Phase 1 sub-phase 1.5 instrumentation)
       arrives with matching label and lp_code
     - Popup visual-content diff is a SKIP-on-missing-reference assertion (F2)
   """
   import json
   import os
   from lib import adb, keyboard, driver

   EXPECTATIONS_DIR = os.path.join(
       os.path.dirname(os.path.abspath(__file__)),
       "..", "..", "..", ".claude", "test-flows", "long-press-expectations"
   )
   POPUP_REF_DIR = os.path.join(
       os.path.dirname(os.path.abspath(__file__)),
       "..", "..", "..", ".claude", "test-flows", "swiftkey-reference", "popups"
   )


   def _load_expectations(mode: str) -> list:
       path = os.path.join(EXPECTATIONS_DIR, f"{mode}.json")
       if not os.path.exists(path):
           import pytest
           pytest.skip(f"long-press expectations file missing: {path}")
       with open(path, "r") as f:
           return json.load(f)


   def _setup(mode: str):
       serial = adb.get_device_serial()
       driver.require_driver()
       keyboard.set_layout_mode(mode, serial)
       return serial


   def _long_press(label: str, serial: str) -> None:
       km = keyboard.get_key_map()
       if label not in km:
           raise KeyError(f"'{label}' not in key map for current layout")
       x, y = km[label]
       driver.swipe(x, y, x, y, duration_ms=500)


   def _assert_long_press_for_mode(mode: str):
       """
       Every key in the mode's expectation file must emit a DevKey/TXT long_press_fired
       event with matching label and lp_code.
       """
       serial = _setup(mode)
       expectations = _load_expectations(mode)

       for entry in expectations:
           label = entry["label"]
           expected_codes = entry.get("lp_codes", [])
           if not expected_codes:
               continue  # key has no long-press popup — skip
           driver.clear_logs()
           _long_press(label, serial)
           # F6: wave-gate on DevKeyLogger.text long_press_fired — zero sleeps.
           fired = driver.wait_for(
               category="DevKey/TXT",
               event="long_press_fired",
               match={"label": label},
               timeout_ms=2000,
           )
           lp_code = int(fired["data"].get("lp_code", 0))
           assert lp_code in expected_codes, (
               f"[{mode}] long-press on '{label}': fired lp_code={lp_code}, "
               f"expected one of {expected_codes}"
           )

           # F2 popup-content diff — SKIP if the reference image is missing.
           ref_path = os.path.join(POPUP_REF_DIR, mode, f"{label}.png")
           if os.path.exists(ref_path):
               from lib import diff
               import subprocess
               actual_path = os.path.join(
                   os.path.dirname(os.path.abspath(__file__)),
                   "..", "artifacts", f"popup-{mode}-{label}.png"
               )
               os.makedirs(os.path.dirname(actual_path), exist_ok=True)
               cmd = ["adb"]
               if serial:
                   cmd += ["-s", serial]
               cmd += ["exec-out", "screencap", "-p"]
               with open(actual_path, "wb") as f:
                   subprocess.run(cmd, check=True, stdout=f)
               passed, score = diff.assert_ssim(actual_path, ref_path)
               assert passed, (
                   f"[{mode}] popup visual diff FAIL for '{label}': SSIM={score:.4f}"
               )


   def test_long_press_every_key_full():
       _assert_long_press_for_mode("full")


   def test_long_press_every_key_compact():
       _assert_long_press_for_mode("compact")


   def test_long_press_every_key_compact_dev():
       _assert_long_press_for_mode("compact_dev")


   def test_long_press_every_key_symbols():
       _assert_long_press_for_mode("symbols")
   ```

3. **Expectations file format** (example `compact.json`):
   ```json
   [
     {"label": "q", "lp_codes": [49]},
     {"label": "w", "lp_codes": [50]},
     {"label": "e", "lp_codes": [233, 232, 234, 235, 51]}
   ]
   ```
   The implementing agent populates these by reading `QwertyLayout.kt` and `SymbolsLayout.kt` at the current HEAD. This is in-scope as the long-press data capture step of Phase 1 sub-phase 1.5.

### Sub-phase 4.4: SwiftKey visual diff test module

**Files:** `tools/e2e/tests/test_visual_diff.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/e2e/tests/test_visual_diff.py`:
   ```python
   """
   SwiftKey visual parity tests.

   FROM SPEC: §6 Phase 2 item 2.3 — "SwiftKey visual diff (screenshot comparison
              against captured SwiftKey reference screenshots)"
   FROM SPEC: §4.4.1 — reference files live in .claude/test-flows/swiftkey-reference/
                        (local-only, .gitignored).

   Strategy:
     - Switch to the layout mode whose reference exists
     - Screenshot via driver.logcat_dump? No — direct adb exec-out
     - SSIM against reference
     - SKIP (not FAIL) when reference is missing, since capture is a manual prereq

   CURRENT REFERENCE INVENTORY (as of Session 42 — verify at runtime):
     - compact-dark.png   → COMPACT mode, dark theme
     - All other modes/themes: NOT YET CAPTURED → tests for them skip
   """
   import os
   import subprocess
   from lib import adb, keyboard, driver, diff

   REFERENCE_DIR = os.path.join(
       os.path.dirname(os.path.abspath(__file__)),
       "..", "..", "..", ".claude", "test-flows", "swiftkey-reference"
   )


   def _capture_screenshot(out_path: str, serial: str) -> None:
       os.makedirs(os.path.dirname(out_path), exist_ok=True)
       cmd = ["adb"]
       if serial:
           cmd += ["-s", serial]
       cmd += ["exec-out", "screencap", "-p"]
       with open(out_path, "wb") as f:
           subprocess.run(cmd, check=True, stdout=f)


   def _visual_diff_test(mode: str, reference_name: str):
       serial = adb.get_device_serial()
       driver.require_driver()

       reference_path = os.path.join(REFERENCE_DIR, reference_name)
       if not os.path.exists(reference_path):
           import pytest
           pytest.skip(
               f"SwiftKey reference not captured yet: {reference_name}. "
               f"See spec §4.4.1 for capture checklist."
           )

       keyboard.set_layout_mode(mode, serial)
       # Ensure keyboard is visible.
       if not adb.is_keyboard_visible(serial):
           subprocess.run(
               ["adb", "-s", serial, "shell", "am", "start", "-a",
                "android.intent.action.INSERT", "-t",
                "vnd.android.cursor.dir/contact"],
               check=True, capture_output=True,
           )

       actual_path = os.path.join(
           os.path.dirname(os.path.abspath(__file__)),
           "..", "artifacts", f"{mode}-{os.path.splitext(reference_name)[0]}.png"
       )
       _capture_screenshot(actual_path, serial)

       passed, score = diff.assert_ssim(actual_path, reference_path)
       assert passed, (
           f"Visual diff FAIL for {mode} vs {reference_name}: "
           f"SSIM={score:.4f} < threshold. "
           f"Actual: {actual_path}  Reference: {reference_path}"
       )


   def test_visual_compact_dark():
       _visual_diff_test("compact", "compact-dark.png")


   def test_visual_full_dark():
       _visual_diff_test("full", "full-dark.png")


   def test_visual_compact_dev_dark():
       _visual_diff_test("compact_dev", "compact-dev-dark.png")
   ```

### Sub-phase 4.5: Register new flows in `.claude/test-flows/registry.md`

**Files:** `.claude/test-flows/registry.md` (modify)
**Agent:** `general-purpose`

**Steps:**

1. Append four new flow entries to `.claude/test-flows/registry.md` AFTER the existing `rapid-stress` entry (before the "Unit Test Tiers" section). Format must match existing entries.

   ```markdown
   ---

   ## voice-round-trip

   - **feature**: voice
   - **deps**: [ime-setup]
   - **precondition**: Keyboard visible, voice button shows (non-password text field), driver server running, ENABLE_DEBUG_SERVER broadcast sent, Whisper model present.
   - **steps**:
     1. Clear driver logs
     2. Tap voice key (code=-102)
     3. Wait for DevKey/VOX state_transition{state=LISTENING} via `driver.wait_for`
     4. Inject audio sample (emulator only — SKIP on physical devices)
     5. Wait for DevKey/VOX state_transition{state=PROCESSING}
     6. Wait for DevKey/VOX processing_complete
     7. Assert result_length > 0
     8. Wait for DevKey/VOX state_transition{state=IDLE, reason=inference_complete}
   - **verify**: All wait_for calls succeed, processing_complete has result_length > 0.
   - **timeout**: 35s
   - **notes**: Privacy: NEVER assert on transcript content. result_length only. Test SKIPs gracefully when voice is disabled or audio injection is unavailable.

   ---

   ## next-word-prediction

   - **feature**: ime
   - **deps**: [ime-setup, typing]
   - **precondition**: Keyboard visible in Normal mode, HTTP forwarding enabled.
   - **steps**:
     1. Clear driver logs
     2. Type "the"
     3. Tap space (code=32)
     4. Wait for DevKey/TXT next_word_suggestions
     5. Assert source ∈ {bigram_hit, bigram_miss, no_prev_word}
     6. Assert payload contains only prev_word_length, result_count, source (privacy guard)
   - **verify**: Event arrives within 3s, payload is structural-only.
   - **timeout**: 15s
   - **notes**: SKIP the bigram-hit sub-test if the default dict lacks bigrams for "the" — it's not a code defect.

   ---

   ## long-press-coverage

   - **feature**: ui-keyboard
   - **deps**: [ime-setup]
   - **precondition**: Keyboard visible, layout mode switchable, long-press expectation JSON files committed under `.claude/test-flows/long-press-expectations/<mode>.json`, `KeyPressLogger.logLongPress` instrumented with `DevKeyLogger.text("long_press_fired", ...)` per Phase 2 sub-phase 1.5.
   - **steps**:
     1. For each mode in [full, compact, compact_dev, symbols]:
        - Switch to mode via SET_LAYOUT_MODE broadcast + wait_for layout_mode_recomposed
        - Load key map for the mode
        - Read `long-press-expectations/<mode>.json` — list of `{label, code, lp_code, popup_codes?}` entries derived from QwertyLayout.buildXxxLayout() / SymbolsLayout
        - For each expected entry: clear logs → swipe-hold 500ms on key → `driver.wait_for("DevKey/TXT", "long_press_fired", match={"label": label})` with 2s timeout → assert `lp_code` matches expected
        - For entries with `popup_codes` (multi-char popups): screencap popup region → SSIM against `swiftkey-reference/popups/<mode>/<label>.png` if present, SKIP otherwise
   - **verify**: Every key listed in the expectation JSONs produces a matching `long_press_fired` event via HTTP forwarding (no legacy DevKeyPress scraping). Popup content matches SwiftKey reference where a reference exists.
   - **timeout**: 240s total (iteration across 4 modes × all keys with long-press data)
   - **notes**: Expectation JSONs authored during sub-phase 4.3 from live `QwertyLayout.kt` / `SymbolsLayout.kt` source by the implementing agent. Flow routes exclusively through `DevKeyLogger.text` + driver `wait_for` — no sleeps, no legacy logcat scrape.

   ---

   ## swiftkey-visual-diff

   - **feature**: ui-theme
   - **deps**: [ime-setup]
   - **precondition**: Reference screenshots exist in .claude/test-flows/swiftkey-reference/, scikit-image installed.
   - **steps**:
     1. For each (mode, reference) pair:
        - SKIP if reference file missing
        - Switch to mode via SET_LAYOUT_MODE broadcast
        - Ensure keyboard visible
        - Capture screenshot via adb exec-out screencap
        - Compute SSIM against reference
        - Assert SSIM >= DEVKEY_SSIM_THRESHOLD (default 0.92)
   - **verify**: Captured screenshots score above threshold.
   - **timeout**: 30s per mode
   - **notes**: Phase 2 references (Session 42): compact-dark only. Other modes SKIP until captured per spec §4.4.1. Adjust threshold via DEVKEY_SSIM_THRESHOLD env var.
   ```

2. Also update the Feature-Path Map in registry.md to add a `voice` feature path if not present (grep first — may already be there):
   ```markdown
   | voice | `app/src/main/java/dev/devkey/keyboard/feature/voice/**` |
   ```
   (Verified in tailor ground truth — this entry already exists. Skip if present.)

**Verification:** `python tools/e2e/e2e_runner.py --list`

### Sub-phase 4.6: Clipboard panel smoke test module

**Files:** `tools/e2e/tests/test_clipboard.py` (create), possibly `app/src/main/java/dev/devkey/keyboard/feature/clipboard/**` for a DevKeyLogger emit site (modify)
**Agent:** `compose-ui-agent`

**Steps:**

1. The implementing agent may need to add a one-line `DevKeyLogger.ui("panel_opened", mapOf("panel" to "clipboard"))` emit inside the clipboard panel's `onShow` or initial composition. Privacy: structural only — NEVER log clipboard contents.

2. Create `tools/e2e/tests/test_clipboard.py` with at least one test:
   - Tap the clipboard toolbar button (via `keyboard.tap_key_by_code` or by coordinate).
   - `driver.wait_for("DevKey/UI", "panel_opened", match={"panel": "clipboard"}, timeout_ms=2000)`.
   - Tap dismiss / back.
   - Assert no crash (harness exits cleanly).

### Sub-phase 4.7: Macros panel smoke test module

**Files:** `tools/e2e/tests/test_macros.py` (create), possibly macros panel source (modify)
**Agent:** `compose-ui-agent`

**Steps:**

1. Add a `DevKeyLogger.ui("panel_opened", mapOf("panel" to "macros"))` emit in the macros panel composition entry point.

2. Create `tools/e2e/tests/test_macros.py`: tap macros button → `wait_for("DevKey/UI", "panel_opened", match={"panel": "macros"})` → dismiss → assert no crash.

### Sub-phase 4.8: Command mode smoke test module

**Files:** `tools/e2e/tests/test_command_mode.py` (create), `app/src/main/java/dev/devkey/keyboard/**/CommandMode*` or detection site (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Add a `DevKeyLogger.ime("command_mode_auto_enabled", mapOf("trigger" to "terminal_detect"))` emit at the auto-detect code path, and a matching `command_mode_manual_enabled` at the manual toggle path.

2. Create `tools/e2e/tests/test_command_mode.py`: either focus a terminal-capable text field (mock or Termux) and `wait_for` the auto event, OR manually toggle command mode via whatever settings broadcast / key exists and `wait_for` the manual event. Dismiss and assert clean exit.

### Sub-phase 4.9: Plugin system smoke test module

**Files:** `tools/e2e/tests/test_plugins.py` (create), `app/src/main/java/dev/devkey/keyboard/**/PluginManager*` (modify)
**Agent:** `ime-core-agent`

**Steps:**

1. Add a `DevKeyLogger.ime("plugin_scan_complete", mapOf("plugin_count" to plugins.size))` emit at the end of `PluginManager.scanPlugins()` (or equivalent boot-time scan).

2. Create `tools/e2e/tests/test_plugins.py`: after IME boot, `wait_for("DevKey/IME", "plugin_scan_complete", timeout_ms=10000)` and assert `plugin_count` matches the expected count (documented per build). Privacy: only count, never plugin metadata contents.

### Sub-phase 4.10: Modifier-combos test module

**Files:** `tools/e2e/tests/test_modifier_combos.py` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `tools/e2e/tests/test_modifier_combos.py` covering Ctrl+C, Ctrl+V, Alt+Tab smoke flows:
   - Type some text, long-press (or use modifier state machine) to engage Ctrl, tap 'A' then 'C' to copy.
   - `wait_for` DevKey/TXT events or assert via `adb.get_text_field_content` that the modifier cascade produced the expected state (for Ctrl+A: full selection highlighted; for Ctrl+V: content was pasted).
   - Alt+Tab: smoke-only — assert no IME crash after the combo dispatches.

2. This module covers the existing `modifier-combos` registry entry from `.claude/test-flows/registry.md` with an actual Python test.

**Privacy note for 4.6–4.10:** All DevKeyLogger emit sites added under these sub-phases must be STRUCTURAL-ONLY — no clipboard contents, no macro names, no command buffers, no plugin metadata strings, no typed content. Counts, panel identifiers, and state-name strings only.

**Verification:** `python tools/e2e/e2e_runner.py --list && ./gradlew assembleDebug`

---

## Phase 5 — Tier stabilization + coverage matrix

Extends the existing FULL-mode coverage documentation to COMPACT and COMPACT_DEV, updates the `layout-modes` registry entry to remove the "FULL only for now" note, and creates a coverage matrix mapping every Phase 1 feature to its backing automated flow(s). Phase 5 is documentation-heavy — no new runtime code except one trivial regression-guard addition in the existing `test_modes.py`.

### Sub-phase 5.1: Update `.claude/docs/reference/key-coordinates.md` with COMPACT/COMPACT_DEV rows

**Files:** `.claude/docs/reference/key-coordinates.md` (modify)
**Agent:** `general-purpose`

**Steps:**

1. Read the current `.claude/docs/reference/key-coordinates.md`. It currently documents FULL mode only. Append two new sections:

   ```markdown
   ---

   ## COMPACT mode (4-row SwiftKey layout)

   Calibrated via `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` after
   switching via `SET_LAYOUT_MODE` broadcast.

   > NOTE: Exact Y coordinates are device-specific — the values below are the pre-calibrated
   > reference for emulator-5554 at 1080x2340 density=3.0. For runtime coordinates use the
   > broadcast cascade via `keyboard.load_key_map()` in the Python harness.

   | Row | Content | Screen Y (reference emulator) |
   |-----|---------|-------------------------------|
   | 0 | QWERTY row (q-p) | ~1605 |
   | 1 | Home row (a-l) | ~1775 |
   | 2 | Z row (Shift, z-m, Backspace) | ~1925 |
   | 3 | Space row (123, Comma, Space, Period, Enter) | ~2090 |

   **TO-DO before release**: capture runtime-measured values on both the CI emulator
   and the developer reference emulator, commit the resulting `calibration.json`
   per mode if the cache is revived as a committed artifact.

   ---

   ## COMPACT_DEV mode (COMPACT with long-press digits)

   Same row layout as COMPACT. The only behavioral difference is that the QWERTY row
   keys have a number long-press (q→1, w→2, e→3, r→4, t→5, y→6, u→7, i→8, o→9, p→0).

   | Row | Content | Screen Y (reference emulator) |
   |-----|---------|-------------------------------|
   | 0 | QWERTY row with number long-press (q-p) | ~1605 |
   | 1 | Home row (a-l) | ~1775 |
   | 2 | Z row | ~1925 |
   | 3 | Space row | ~2090 |

   **Test strategy**: long-press coverage flow in Phase 2.3 covers both the letter
   primary tap AND the digit long-press for this mode.
   ```

### Sub-phase 5.2: Iterate tier stabilization (FULL / COMPACT / COMPACT_DEV → 100% green)

**Files:** `tools/e2e/tests/**` (modify as needed)
**Agent:** `general-purpose`

**Steps:**

1. Start the driver server (`node tools/debug-server/server.js`) and let the runner enable HTTP forwarding (Phase 3.7).

2. Run `python tools/e2e/e2e_runner.py` for each of the three layout modes: FULL, COMPACT, COMPACT_DEV. Mode is selected by setting the `DEVKEY_LAYOUT_MODE` env var OR by issuing a `SET_LAYOUT_MODE` broadcast before the runner starts.

3. For each FAILing test, diagnose via the relevant `DevKeyLogger` categories (`DevKey/IME`, `DevKey/VOX`, `DevKey/TXT`, `DevKey/UI`) and a `driver.logcat_dump(...)` of the legacy tags. Classify the failure:
   - **Test-infrastructure defect** (wait-gate wrong, coordinate stale, expectation outdated) → fix the Python test module.
   - **App defect** (real regression) → file a GitHub issue, do NOT silently fix app code inside this plan. App fixes are routed through a separate commit with a clear defect link.

4. Apply targeted fixes to test modules only — do NOT modify app code unless a real defect is discovered and filed.

5. Re-run the suite until 100% green across all three modes. This is inherently iterative; the plan author accepts that implementation may require multiple test-fix-run cycles.

6. Commit the stabilized test modules.

**Exit criterion:** `python tools/e2e/e2e_runner.py` passes with zero reds on each of FULL, COMPACT, COMPACT_DEV.

### Sub-phase 5.3: Update `test_modes.py` to assert mode-switch round-trip across all three layouts

**Files:** `tools/e2e/tests/test_modes.py` (modify)
**Agent:** `general-purpose`

**Steps:**

1. Read the current `tools/e2e/tests/test_modes.py`. It currently has FULL-only tests. Append a new test that exercises the `SET_LAYOUT_MODE` broadcast round-trip:

   ```python


   def test_layout_mode_round_trip():
       """
       Switch FULL → COMPACT → COMPACT_DEV → FULL and verify key maps load for each.

       FROM SPEC: §6 Phase 2 item 2.4 — FULL + COMPACT + COMPACT_DEV 100% green.

       NOTE: This test DOES NOT call `keyboard.set_layout_mode(...)` because that helper
             has its own wave-gate and loads the key map internally. Instead we drive
             the broadcast + wait_for + load_key_map sequence explicitly so we can assert
             on the `layout_mode_set` event BEFORE any recomposition blocks.
       """
       from lib import keyboard, driver
       serial = adb.get_device_serial()
       driver.require_driver()

       for mode in ("compact", "compact_dev", "full"):
           driver.clear_logs()
           # Broadcast directly via the driver — no sleeps.
           driver.broadcast(
               "dev.devkey.keyboard.SET_LAYOUT_MODE",
               {"mode": mode},
           )
           # Wait for the IME-side receiver to log the preference write.
           driver.wait_for(
               category="DevKey/IME",
               event="layout_mode_set",
               match={"mode": mode},
               timeout_ms=3000,
           )
           # Now load the key map for the new mode.
           keyboard.load_key_map(serial)
           km = keyboard.get_key_map()
           letter_count = sum(1 for k in km.keys() if len(k) == 1 and k.isalpha())
           assert letter_count >= 10, (
               f"Mode {mode}: only {letter_count} letter keys found after reload. "
               f"Expected at least 10. Broadcast may have failed to recompose."
           )
   ```

2. The existing imports at the top of `test_modes.py` already include `from lib import adb, keyboard` per the tailor source summary — the new test's `from lib import keyboard, driver` line inside the function is a lazy import to avoid perturbing the module header. Verify and simplify to a module-level `from lib import driver` if `driver` is not already imported.

### Sub-phase 5.4: Update registry.md layout-modes flow notes

**Files:** `.claude/test-flows/registry.md` (modify)
**Agent:** `general-purpose`

**Steps:**

1. Locate the `layout-modes` flow entry in `.claude/test-flows/registry.md` (currently notes "COMPACT and COMPACT_DEV mode testing requires changing the layout preference, which is not yet automated. This flow tests FULL mode only for now."). Replace those two sentences with:

   ```markdown
   COMPACT and COMPACT_DEV mode switching is now automated via the
   `dev.devkey.keyboard.SET_LAYOUT_MODE` debug broadcast (added Phase 2 sub-phase 1.2).
   The Python harness's `keyboard.set_layout_mode(mode, serial)` helper issues the
   broadcast and re-loads the key map. See `test_modes.py::test_layout_mode_round_trip`
   for the smoke test that guards this path.
   ```

2. Add a new step 4 under the `layout-modes` flow `steps:` list:
   ```markdown
     4. **Mode switching via broadcast**: Switch to COMPACT via
        `adb shell am broadcast -a dev.devkey.keyboard.SET_LAYOUT_MODE --es mode compact`,
        reload key map, verify at least 10 letter keys resolve. Repeat for COMPACT_DEV and FULL.
   ```

### Sub-phase 5.5: Create `coverage-matrix.md`

**Files:** `.claude/test-flows/coverage-matrix.md` (create)
**Agent:** `general-purpose`

**Steps:**

1. Create `.claude/test-flows/coverage-matrix.md`:
   ```markdown
   # Phase 2.5 Coverage Matrix

   Maps every Phase 1 feature (from `.claude/specs/pre-release-spec.md` §2.1)
   to its backing automated test flow(s). Per spec §6 Phase 2 item 2.5 every Phase 1 feature
   must have at least one automated test flow — Phase 2 delivers that bar.

   Updated: 2026-04-08 (Phase 2)

   ## Automated coverage

   | Phase 1 feature | Spec ref | Automated flow(s) | Test module |
   |---|---|---|---|
   | Voice input E2E | §2.1, §5 | `voice-round-trip` | `tools/e2e/tests/test_voice.py` |
   | SwiftKey visual parity (dark, COMPACT) | §2.1, §4 | `swiftkey-visual-diff` | `tools/e2e/tests/test_visual_diff.py` |
   | SwiftKey visual parity (other modes) | §2.1, §4 | `swiftkey-visual-diff` (SKIP until reference captured) | `tools/e2e/tests/test_visual_diff.py` |
   | Long-press popups (every key, every mode) | §2.1, §4.2 | `long-press-coverage` | `tools/e2e/tests/test_long_press.py` |
   | Predictive next-word after space | §2.1, §4.3 | `next-word-prediction` | `tools/e2e/tests/test_next_word.py` |
   | Keyboard mode switching (Normal ↔ Symbols) | §2.1 | `mode-switching` | `tools/e2e/tests/test_modes.py` |
   | Layout mode switching (FULL / COMPACT / COMPACT_DEV) | §2.1 | `layout-modes` + `test_layout_mode_round_trip` | `tools/e2e/tests/test_modes.py` |
   | Modifier states (Shift, Ctrl, Alt one-shot) | §2.1 | `modifier-states` | `tools/e2e/tests/test_modifiers.py` |
   | Caps Lock | §2.1 | `caps-lock` | `tools/e2e/tests/test_modifiers.py` |
   | Modifier combos (Ctrl+C/V/A/Z, Alt+Tab) | §2.1 | `modifier-combos` | `tools/e2e/tests/test_modifier_combos.py` (Phase 2 sub-phase 4.10) |
   | Clipboard panel | §2.1, §3 | `clipboard-smoke` | `tools/e2e/tests/test_clipboard.py` (Phase 2 sub-phase 4.6) |
   | Macro system | §2.1, §3 | `macros-smoke` | `tools/e2e/tests/test_macros.py` (Phase 2 sub-phase 4.7) |
   | Command mode | §2.1, §3 | `command-mode-smoke` | `tools/e2e/tests/test_command_mode.py` (Phase 2 sub-phase 4.8) |
   | Plugin system | §2.1, §2.4 | `plugin-smoke` | `tools/e2e/tests/test_plugins.py` (Phase 2 sub-phase 4.9) |
   | Basic typing | §2.1 | `typing` | `tools/e2e/tests/test_smoke.py` |
   | Rapid input stress | §2.1 | `rapid-stress` | `tools/e2e/tests/test_rapid.py` |

   ## Supplementary manual smoke (belt and suspenders)

   Phase 1 manual-checklist files continue to exist as additional verification signal
   alongside the automated flows above. They are NOT the primary coverage source for
   any feature — every Phase 1 feature has an automated flow as required by spec §6
   Phase 2 item 2.5. Manual checklists are re-run as part of the Phase 3 manual smoke
   gate (§3.6) on top of automated coverage.

   | Checklist | Phase 1 coverage |
   |---|---|
   | `.claude/test-flows/phase1-voice-verification.md` | Voice (supplements test_voice.py) |
   | `.claude/test-flows/phase1-regression-smoke.md` | Clipboard/macros/command/plugin (supplements test_clipboard.py, test_macros.py, test_command_mode.py, test_plugins.py) |

   ## Phase 3 gate — release blocker mapping

   Per spec §6 Phase 3, release cannot proceed until every row in the "Automated coverage"
   table above is green AND the supplementary manual checklists have a signed-off run.

   | Phase 3 gate item | Mechanism |
   |---|---|
   | §3.1 — Full `/test` run all three tiers green | All rows in "Automated coverage" pass on FULL, COMPACT, COMPACT_DEV |
   | §3.2 — Voice round-trip flow green | `voice-round-trip` |
   | §3.3 — Next-word prediction flow green | `next-word-prediction` |
   | §3.4 — Long-press coverage flow green | `long-press-coverage` |
   | §3.5 — SwiftKey visual diff within tolerance | `swiftkey-visual-diff` (or manual review until all references captured) |
   | §3.6 — Manual smoke on emulator | Phase 1 checklists re-run |
   | §3.7 — Lint clean | `./gradlew lint` clean on CI |

   ## Known reference capture gaps (blockers for full Phase 3.5 green)

   > **Reference capture is Phase 1 backlog (spec §6 Phase 1.2), NOT Phase 2 scope.
   > Visual-diff tests SKIP gracefully until references are captured.**


   From spec §4.4.1:
   - Qwerty / Full / Symbols / Fn / Phone modes — dark theme references NOT captured
   - Every long-press popup from SwiftKey — NOT captured
   - Light theme — DEFERRED post-v1.0 per user decision

   **Until captured**, the `swiftkey-visual-diff` flow SKIPs (rather than FAILs) for
   missing references. This is intentional — see `test_visual_diff.py`.
   ```

**Verification:** `./gradlew assembleDebug`

---

## Implementation notes for `/implement`

- **Phase 1 is Kotlin-only** → dispatch `ime-core-agent` for 1.1, 1.2, 1.3, 1.5, 1.7; `compose-ui-agent` for 1.6 and 1.8; `general-purpose` for 1.9 (unit test). Verify build at end of Phase 1.
- **Phases 2-3 are tooling-only** → Node + Python. `general-purpose` agent for all. `./gradlew assembleDebug` runs at the end of each phase as a safety net even though no Kotlin changes.
- **Phase 4 sub-phases 4.6-4.10** may require minimal Kotlin emit-site additions (clipboard / macros / command mode / plugin system). Routing: clipboard → `compose-ui-agent`, macros → `compose-ui-agent`, command mode → `ime-core-agent`, plugin system → `ime-core-agent`, modifier-combos test → `general-purpose`. Emit sites must be privacy-safe (no content, only structural).
- **No JNI changes anywhere** — C++ layer untouched. `BinaryDictionary.java` package path stays locked per `.claude/rules/jni-bridge.md`.
- **No tests in CI that require the driver server** — the new Phase 2.4 and 4.x tests fail fast with a clear error message (`driver.require_driver()`) if the driver server isn't running. CI that runs `./gradlew test` (unit only) is unaffected; CI that runs the Python harness must start the driver first.
- **Manual verification** after implementation: start the driver server, run `adb shell am broadcast -a dev.devkey.keyboard.ENABLE_DEBUG_SERVER --es url http://10.0.2.2:3947`, then `curl http://127.0.0.1:3947/logs?category=DevKey/IME&last=5` — the `debug_server_enabled` handshake line from sub-phase 1.1 should appear.
- **Out-of-scope deferred items** (tracked but not in this plan): migration of `KeyPressLogger` / `KeyboardModeManager` legacy `Log.d` tags to `DevKeyLogger` (Phase 4 refactor territory); streaming logcat merge on the driver side; visual diff reference-capture automation; voice transcription content assertions (privacy-prohibited anyway).
