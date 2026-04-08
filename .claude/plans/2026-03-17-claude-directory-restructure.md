# .claude Directory Restructure Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** Restructure the DevKey `.claude/` directory to mirror Field Guide App patterns — scaled for DevKey's complexity — with systematic-debugging skill, tailor + writing-plans skills, specialist agents, rules extraction, and hooks.
**Decisions:** Brainstorming Session 36 — all sections approved by user. Re-scoped 2026-04-08 to include full tailor/writing-plans skills and incorporate staleness findings.

**Architecture:** Bottom-up: directory scaffolding first, then debug infrastructure (DevKeyLogger + HTTP server), then skills/agents, then rules/hooks, then planning pipeline (tailor + writing-plans), then CLAUDE.md update. Each phase builds on the previous.
**Blast Radius:** ~60 files created/modified in `.claude/`, 2 new Kotlin/Node source files (`DevKeyLogger.kt`, debug server `server.js`), 0 existing source files modified.

**Spec source:** This plan is treated as the spec — no separate spec file exists.

---

## Changelog

- **2026-03-17** — Initial plan (Session 36 brainstorming).
- **2026-04-08** — Rescoped. Staleness audit applied. Added full tailor skill (Phase 5.4a) and expanded writing-plans skill (Phase 5.4b) adapted for Kotlin/Android/jcodemunch. Corrected already-committed plan statuses. Noted `.claude/docs/guides/` and `docs/research/` preservation. Added jcodemunch re-index step.
- **2026-04-08 (later)** — Rewrote implement pipeline (Sub-phases 4.5 + 5.8). Removed all headless-mode usage — everything flows through the Agent tool. Restructured review flow: **one completeness review per phase** (spec intent vs. implementation); the **full 3-agent adversarial sweep + fixer loop runs once after all phases complete**, max 3 cycles with monotonicity check.
- **2026-04-08 (later still)** — Eliminated per-concern defect files entirely. **Defects are now GitHub Issues** on `RobertoChavez2433/DevKey`. Sub-phase 1.1 drops `.claude/defects/` creation. Sub-phase 1.3 rewritten to file the 7 existing defects as GH issues and archive `_defects.md` → `logs/defects-archive-final.md`. All skill/agent context_loading blocks replace file reads with `gh issue list --label "area:*" --label defect` queries. `defects-integration.md` reference renamed to `github-issues-integration.md` with label taxonomy + filter query patterns. Label schema: `defect` (marker) + `category:*` (IME/UI/MODIFIER/NATIVE/BUILD/TEXT/VOICE/ANDROID) + `area:*` (ime-lifecycle/compose-ui/modifier-state/native-jni/build-test/text-input/voice-dictation) + `priority:*`.

## Prerequisites (verify before running /implement)

1. **jcodemunch MCP reachable** — `mcp__jcodemunch__list_repos` returns results.
2. **Re-index repo with source_root** — stale index `local/Hackers_Keyboard_Fork` (2026-03-05, empty git_head) must be refreshed:
   ```
   mcp__jcodemunch__index_folder(path="C:\\Users\\rseba\\Projects\\Hackers_Keyboard_Fork", incremental=false, use_ai_summaries=true)
   ```
   After re-indexing, the repo key will become `local/Hackers_Keyboard_Fork-<hash>`. Record the hash — the debug-research-agent and tailor skill reference it.
3. **`docs/research/` preserved** — contains `competitive-landscape.md`, `hackers-keyboard-analysis.md`, `swiftkey-analysis.md`. Plan must not touch this directory.
4. **`.claude/docs/guides/` preserved** — contains `ui-prototyping-workflow.md`. Plan creates `.claude/docs/INDEX.md` alongside, does not clobber `guides/`.
5. **`.claude/plans/completed/` already exists** — Phase 1.4 moves new completions INTO it, does not create it.

---

## Phase 1: Directory Scaffolding + Stale Fixes

**Risk**: Zero — only `.claude/` config files. No source code changes.
**Agent**: general-purpose (sonnet)

### Sub-phase 1.1: Create new directories

Create these directories with `.gitkeep` where empty:

- `.claude/rules/` (will be populated in Phase 5)
- `.claude/hooks/` (will be populated in Phase 5)
- `.claude/agent-memory/ime-core-agent/`
- `.claude/agent-memory/compose-ui-agent/`
- `.claude/agent-memory/code-review-agent/`
- `.claude/agent-memory/security-agent/`
- `.claude/specs/` with `.gitkeep`
- `.claude/adversarial_reviews/` with `.gitkeep`
- `.claude/dependency_graphs/` with `.gitkeep`
- `.claude/code-reviews/` with `.gitkeep`
- `.claude/architecture-decisions/` (will be populated in Phase 5)
- `.claude/docs/` with `INDEX.md` placeholder

### Sub-phase 1.2: Update .gitignore

Add to `.claude/.gitignore`:
```
debug-sessions/
test-results/*/screenshots/
test-results/*/logs/
test-results/*/*.xml
state/implement-checkpoint.json
```

### Sub-phase 1.3: Migrate existing defects to GitHub Issues

**UPDATED 2026-04-08:** Defects are now tracked in **GitHub Issues** on `RobertoChavez2433/DevKey`, not in per-concern Markdown files. This sub-phase migrates the 7 existing defects from `.claude/autoload/_defects.md` to GH issues, then archives the file.

**Step 1: Set up GitHub labels** (one-time). Create these labels on `RobertoChavez2433/DevKey`:

```bash
gh label create "defect" --repo RobertoChavez2433/DevKey --color d73a4a --description "Pattern to avoid; root cause documented" --force
gh label create "area:ime-lifecycle" --repo RobertoChavez2433/DevKey --color 0075ca --force
gh label create "area:compose-ui" --repo RobertoChavez2433/DevKey --color 0075ca --force
gh label create "area:modifier-state" --repo RobertoChavez2433/DevKey --color 0075ca --force
gh label create "area:native-jni" --repo RobertoChavez2433/DevKey --color 0075ca --force
gh label create "area:build-test" --repo RobertoChavez2433/DevKey --color 0075ca --force
gh label create "area:text-input" --repo RobertoChavez2433/DevKey --color 0075ca --force
gh label create "area:voice-dictation" --repo RobertoChavez2433/DevKey --color 0075ca --force
gh label create "category:IME" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "category:UI" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "category:MODIFIER" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "category:NATIVE" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "category:BUILD" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "category:TEXT" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "category:VOICE" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "category:ANDROID" --repo RobertoChavez2433/DevKey --color a2eeef --force
gh label create "priority:critical" --repo RobertoChavez2433/DevKey --color b60205 --force
gh label create "priority:high" --repo RobertoChavez2433/DevKey --color d93f0b --force
gh label create "priority:medium" --repo RobertoChavez2433/DevKey --color fbca04 --force
gh label create "priority:low" --repo RobertoChavez2433/DevKey --color c5def5 --force
```

**Step 2: File the 7 existing defects as GH issues** using this body template:

```
**Pattern**: <what to avoid — 1 line from _defects.md>
**Prevention**: <how to avoid — 1-2 lines from _defects.md>
**Ref**: <file:line references>
**Migrated from**: .claude/autoload/_defects.md (2026-03-17)
```

| Title | Labels | Area | Status |
|---|---|---|---|
| `[BUILD] Compose UI test deps incompatible with API 36 (Android 16)` | defect, category:BUILD, area:build-test, priority:medium | build-test | open |
| `[BUILD] connectedAndroidTest uninstalls the app from emulator` | defect, category:BUILD, area:build-test, priority:low | build-test | open |
| `[IME] ComposeView decor lifecycle must match ComposeView lifecycle` | defect, category:IME, area:ime-lifecycle, priority:high | ime-lifecycle | **closed** (fixed Session 27, commit 386a25b) |
| `[IME] PluginManager loads untrusted packages without signature verification` | defect, category:IME, area:native-jni, priority:high | native-jni | open |
| `[IME] ChordeTracker still used for Symbol/Fn modifier state` | defect, category:IME, area:modifier-state, priority:low | modifier-state | open |
| `[ANDROID] KeyCodes use ASCII values, not Android KeyEvent constants` | defect, category:ANDROID, area:text-input, priority:medium | text-input | open |
| `[ANDROID] PendingIntent requires FLAG_IMMUTABLE on API 31+` | defect, category:ANDROID, area:ime-lifecycle, priority:medium | ime-lifecycle | open |

Create open issues with:
```bash
gh issue create --repo RobertoChavez2433/DevKey \
  --title "[CATEGORY] YYYY-MM-DD: Brief title" \
  --label "defect,category:IME,area:ime-lifecycle,priority:medium" \
  --body "<template above>"
```

For the already-fixed ComposeView decor lifecycle issue, create and immediately close:
```bash
ISSUE_URL=$(gh issue create --repo RobertoChavez2433/DevKey --title "..." --label "..." --body "...")
gh issue close "$ISSUE_URL" --comment "Fixed in commit 386a25b (Session 27). Preserved as historical reference."
```

**Step 3: Archive the old defects file.**

Move `.claude/autoload/_defects.md` → `.claude/logs/defects-archive-final.md` with a prepended header:

```markdown
# Defects Archive (FINAL)

**ARCHIVED 2026-04-08.** All defects migrated to GitHub Issues on
`RobertoChavez2433/DevKey` under the `defect` label. This file is a
historical snapshot only — do NOT add new defects here. Use:

    gh issue create --repo RobertoChavez2433/DevKey \
      --title "[CATEGORY] YYYY-MM-DD: Brief title" \
      --label "defect,category:<CAT>,area:<AREA>,priority:<P>" \
      --body "**Pattern**: ...\n**Prevention**: ...\n**Ref**: file:line"

<original _defects.md content below for history>
```

Also delete `.claude/logs/defects-archive.md` if it exists (was used by the old rotation system — no longer needed).

**Step 4: Verify migration.**

```bash
# No live defects file
test ! -f .claude/autoload/_defects.md && echo "PASS" || echo "FAIL"
# Archive exists
test -f .claude/logs/defects-archive-final.md && echo "PASS" || echo "FAIL"
# GH issues exist
test "$(gh issue list --repo RobertoChavez2433/DevKey --label defect --state all --json number | jq length)" -ge 7 && echo "PASS" || echo "FAIL"
```

### Sub-phase 1.4: Archive completed plans

**Note (2026-04-08 update):** `.claude/plans/completed/` already exists with 9 archived plans — do NOT recreate it. All four plans below are now either fully or mostly implemented AND committed; move them into `completed/` with a trailing status header line instead of leaving them at the top level.

Move the following plans to `.claude/plans/completed/`:

| Plan | Commit(s) | Final status header to prepend |
|------|-----------|--------------------------------|
| `kotlin-migration-plan.md` | 22a3050 | `**Status:** IMPLEMENTED AND COMMITTED (22a3050)` |
| `dead-code-cleanup-plan.md` | 73e211b | `**Status:** Phases 1-5 IMPLEMENTED AND COMMITTED (73e211b), Phase 6 DEFERRED` |
| `coordinate-calibration-implementation-plan.md` | 31d33f7, 038ea73 | `**Status:** IMPLEMENTED AND COMMITTED (31d33f7)` |
| `123-fix-e2e-harness-implementation-plan.md` | 1494cb8, 386a25b, ec91f67 | `**Status:** IMPLEMENTED AND COMMITTED (386a25b)` |

After this sub-phase, only `.claude/plans/2026-03-17-claude-directory-restructure.md` should remain at the top level of `.claude/plans/`.

### Sub-phase 1.5: Fix P0 stale references

Verified 2026-04-08 against current file contents:

1. **`skills/implement/SKILL.md`** lines 113-115: Replace `Hackers Keyboard Fork` with `Hackers_Keyboard_Fork` (3 occurrences) — **still broken, confirmed**
2. **`agents/test-wave-agent.md`** line 6: Change `model: haiku` to `model: sonnet` — **still broken, confirmed**
3. **`agents/test-orchestrator-agent.md`** line 127: Change tap coordinates from `350 650` to `540 1356` — **still broken, confirmed**
4. **`agents/test-orchestrator-agent.md`** lines 146 and 247: Remove the two `Y offset -153` references and fix Wave 2 example to match `registry.md` — **still broken, confirmed**
5. **`memory/MEMORY.md`** line 37: Update "355 tests" to "361 tests" (line moved from 38 to 37) — **still broken, confirmed**
6. ~~**`autoload/_state.md`** line 85: Remove stale `test-results/2026-03-04_0954_full/` path reference~~ — **already fixed or never present; grep returns zero matches. SKIP.**

### Sub-phase 1.6: Fix P2 stale references

1. **`skills/test/references/ime-testing-patterns.md`**: Update FULL mode row Y table to calibrated values (1475/1605/1775/1925/2090/2225), remove fixed -153px framing
2. **`agents/test-wave-agent.md`**: Replace "Y offset" input with "coordinate table from orchestrator"; remove "Option 1: parse DevKeyMap" coordinate loading section
3. **`logs/README.md`**: Add all 8 files to the documented list (currently only lists 2)
4. **`test-flows/registry.md`**: Fix Python runner path if `tools/e2e/run.py` reference exists → `tools/e2e/e2e_runner.py`
5. **`logs/emulator-audit-wave1.md`**: Add `## Resolution` section noting all 8 bugs fixed in Sessions 16-17
6. **`skills/brainstorming/SKILL.md`** line 55: Remove references to `frontend-design` and `mcp-builder`

### Verify Phase 1

```bash
# Verify new directories exist
ls .claude/rules/ .claude/hooks/ .claude/specs/ .claude/agent-memory/
# Verify defects migrated to GH issues (≥ 7 defect-labeled issues)
gh issue list --repo RobertoChavez2433/DevKey --label defect --state all --json number | jq length
# Verify old defects file archived, not present in autoload
test ! -f .claude/autoload/_defects.md && echo "PASS" || echo "FAIL"
test -f .claude/logs/defects-archive-final.md && echo "PASS" || echo "FAIL"
# Verify plans moved
test -f .claude/plans/completed/kotlin-migration-plan.md && echo "PASS" || echo "FAIL"
# Verify implement path fixed
grep -c "Hackers_Keyboard_Fork" .claude/skills/implement/SKILL.md
```

---

## Phase 2: DevKeyLogger + HTTP Debug Server

**Risk**: Low — new files only. No existing source modified.
**Agent**: ime-core-agent (or general-purpose if agent not yet defined)

### Sub-phase 2.1: Create DevKeyLogger Kotlin class

**Create**: `app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt`

```kotlin
// WHY: Structured logging with categories and hypothesis tagging for systematic debugging.
// Session-scoped hypothesis markers (H001, H002...) are temporary investigation tools
// that MUST be removed after each debug session (Phase 9 cleanup gate).
package dev.devkey.keyboard.debug

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object DevKeyLogger {

    enum class Category(val tag: String) {
        IME_LIFECYCLE("DevKey/IME"),
        COMPOSE_UI("DevKey/UI"),
        MODIFIER_STATE("DevKey/MOD"),
        TEXT_INPUT("DevKey/TXT"),
        NATIVE_JNI("DevKey/NDK"),
        VOICE("DevKey/VOX"),
        BUILD_TEST("DevKey/BLD"),
        ERROR("DevKey/ERR"),
    }

    private var serverUrl: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Enable HTTP server forwarding for Deep debug mode. */
    fun enableServer(url: String) {
        serverUrl = url
    }

    /** Disable HTTP server forwarding. */
    fun disableServer() {
        serverUrl = null
    }

    // Category convenience methods
    fun ime(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.IME_LIFECYCLE, message, data)

    fun ui(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.COMPOSE_UI, message, data)

    fun modifier(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.MODIFIER_STATE, message, data)

    fun text(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.TEXT_INPUT, message, data)

    fun native(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.NATIVE_JNI, message, data)

    fun voice(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.VOICE, message, data)

    fun error(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.ERROR, message, data)

    /**
     * Session-scoped hypothesis marker. MUST be removed after debug session.
     * Grep "hypothesis(" to find all markers for cleanup.
     */
    fun hypothesis(id: String, category: Category, description: String, data: Map<String, Any?> = emptyMap()) {
        val tag = "DevKey/$id"
        val fullMessage = "[${category.tag}] $description"
        Log.d(tag, formatMessage(fullMessage, data))
        sendToServer(category.tag, fullMessage, data, hypothesisId = id)
    }

    private fun log(category: Category, message: String, data: Map<String, Any?>) {
        Log.d(category.tag, formatMessage(message, data))
        sendToServer(category.tag, message, data)
    }

    private fun formatMessage(message: String, data: Map<String, Any?>): String {
        if (data.isEmpty()) return message
        val dataStr = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "$message | $dataStr"
    }

    private fun sendToServer(
        category: String,
        message: String,
        data: Map<String, Any?>,
        hypothesisId: String? = null
    ) {
        val url = serverUrl ?: return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("category", category)
                    put("message", message)
                    put("data", JSONObject(data.mapValues { it.value?.toString() }))
                    hypothesisId?.let { put("hypothesis", it) }
                }
                val conn = URL("$url/log").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                conn.responseCode // trigger send
                conn.disconnect()
            } catch (_: Exception) {
                // Silent fail — debug logging must never crash the IME
            }
        }
    }
}
```

### Sub-phase 2.2: Create HTTP debug server

**Create**: `tools/debug-server/server.js`

```javascript
// WHY: Local log collection server for systematic debugging Deep mode.
// Receives structured logs from DevKeyLogger via HTTP, queryable by
// category and hypothesis tag. Run with: node tools/debug-server/server.js
const http = require('http');
const PORT = 3947;
const MAX_ENTRIES = 30000;

let logs = [];

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);

  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');

  // POST /log — receive a log entry
  if (req.method === 'POST' && url.pathname === '/log') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        const entry = JSON.parse(body);
        entry.ts = new Date().toISOString().slice(11, 23);
        logs.push(entry);
        if (logs.length > MAX_ENTRIES) logs = logs.slice(-MAX_ENTRIES);
        res.writeHead(200);
        res.end('{"ok":true}');
      } catch (e) {
        res.writeHead(400);
        res.end(`{"error":"${e.message}"}`);
      }
    });
    return;
  }

  // GET /health
  if (url.pathname === '/health') {
    const mem = process.memoryUsage();
    res.end(JSON.stringify({
      status: 'ok',
      entries: logs.length,
      maxEntries: MAX_ENTRIES,
      memoryMB: Math.round(mem.heapUsed / 1024 / 1024),
      uptimeSeconds: Math.round(process.uptime()),
    }));
    return;
  }

  // GET /logs?last=N&category=X&hypothesis=H001
  if (url.pathname === '/logs') {
    let filtered = logs;
    const cat = url.searchParams.get('category');
    const hyp = url.searchParams.get('hypothesis');
    const last = parseInt(url.searchParams.get('last') || '50');
    if (cat) filtered = filtered.filter(e => e.category === cat);
    if (hyp) filtered = filtered.filter(e => e.hypothesis === hyp);
    filtered = filtered.slice(-last);
    res.end(filtered.map(e => JSON.stringify(e)).join('\n'));
    return;
  }

  // GET /categories
  if (url.pathname === '/categories') {
    const counts = {};
    logs.forEach(e => { counts[e.category] = (counts[e.category] || 0) + 1; });
    res.end(JSON.stringify(counts));
    return;
  }

  // POST /clear
  if (req.method === 'POST' && url.pathname === '/clear') {
    const count = logs.length;
    logs = [];
    res.end(JSON.stringify({ cleared: count }));
    return;
  }

  res.writeHead(404);
  res.end('{"error":"not found"}');
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Debug server listening on http://127.0.0.1:${PORT}`);
});
```

### Sub-phase 2.3: Create agent-memory MEMORY.md stubs

Create initial (mostly empty) memory files:

**`.claude/agent-memory/ime-core-agent/MEMORY.md`**:
```markdown
# Agent Memory — IME Core Agent

## Patterns Discovered

## Gotchas & Quirks

## Architectural Decisions

## Frequently Referenced Files
| File | Notes |
|------|-------|
| LatinIME.kt | ~2500 lines, main IME entry point |
| KeyEventSender.kt | ~330 lines, key synthesis |
| ModifierStateManager.kt | Shift/Ctrl/Alt/Meta unified state |
```

**`.claude/agent-memory/compose-ui-agent/MEMORY.md`**:
```markdown
# Agent Memory — Compose UI Agent

## Patterns Discovered

## Gotchas & Quirks

## Architectural Decisions

## Frequently Referenced Files
| File | Notes |
|------|-------|
| DevKeyKeyboard.kt | Main Compose keyboard container |
| KeyView.kt | Individual key rendering + touch |
| KeyboardActionBridge.kt | Compose → LatinIME bridge |
| ComposeKeyboardViewFactory.kt | Lifecycle-sharing factory |
```

**`.claude/agent-memory/code-review-agent/MEMORY.md`**:
```markdown
# Agent Memory — Code Review Agent

## Patterns Discovered

## Anti-Patterns Found
```

**`.claude/agent-memory/security-agent/MEMORY.md`**:
```markdown
# Agent Memory — Security Agent

## Audit History

## Known Vulnerabilities
```

### Verify Phase 2

```bash
# Verify DevKeyLogger exists and compiles
grep -c "object DevKeyLogger" app/src/main/java/dev/devkey/keyboard/debug/DevKeyLogger.kt
# Verify server exists
test -f tools/debug-server/server.js && echo "PASS" || echo "FAIL"
# Verify agent memory stubs
ls .claude/agent-memory/*/MEMORY.md
# Build check
./gradlew assembleDebug
```

---

## Phase 3: Systematic Debug Skill

**Risk**: Zero — only `.claude/` config files.
**Agent**: general-purpose (sonnet)

### Sub-phase 3.1: Create SKILL.md

**Create**: `.claude/skills/systematic-debugging/SKILL.md`

Adapt the Field Guide's 10-phase framework for Android IME + Kotlin:
- Replace `Logger.hypothesis()` (Dart) → `DevKeyLogger.hypothesis()` (Kotlin)
- Replace `Logger.sync()` etc. → `DevKeyLogger.ime()`, `.ui()`, `.modifier()`, `.text()`, `.native()`, `.voice()`
- Replace `pwsh -Command "flutter test"` → `./gradlew test`
- Replace `pwsh -Command "flutter run"` → ADB install flow
- Replace `lib/` search paths → `app/src/main/java/dev/devkey/keyboard/` search paths
- Replace `Grep "hypothesis(" lib/` → `Grep "hypothesis(" app/src/`
- **Defect tracking: GitHub Issues on `RobertoChavez2433/DevKey`, not flat files.** Phase 1 triage step runs `gh issue list --repo RobertoChavez2433/DevKey --label "area:<area>" --state open --json number,title,body,labels --limit 20` and scans titles/bodies for known patterns before investigating.
- Defect categories (GitHub label prefix `category:`): `IME`, `UI`, `MODIFIER`, `NATIVE`, `BUILD`, `TEXT`, `VOICE`, `ANDROID`
- Defect areas (GitHub label prefix `area:`): `ime-lifecycle`, `compose-ui`, `modifier-state`, `native-jni`, `build-test`, `text-input`, `voice-dictation`
- Phase 10 (DEFECT LOG): when a new pattern is found, file a GH issue with `gh issue create --repo RobertoChavez2433/DevKey --title "[CATEGORY] YYYY-MM-DD: Brief Title" --label "defect,category:<CAT>,area:<AREA>,priority:<P>" --body "**Pattern**: ...\n**Prevention**: ...\n**Ref**: file:line"`
- Deep mode: `node tools/debug-server/server.js` + `adb reverse tcp:3947 tcp:3947`
- Quick mode: `adb logcat -s "DevKey/H001"` for hypothesis filtering

Keep identical: 10-phase structure, mode selection, anti-rationalization system, user signal handlers, stop conditions, mandatory cleanup gate, 30-day retention, root cause report format.

### Sub-phase 3.2: Create reference files

**Create**: `.claude/skills/systematic-debugging/references/log-investigation-and-instrumentation.md`

Adapt from Field Guide version:
- Replace Dart `Logger.*` calls → Kotlin `DevKeyLogger.*` calls
- Replace curl examples → keep identical (same server, same port, same API)
- Category guide: IME_LIFECYCLE, COMPOSE_UI, MODIFIER_STATE, TEXT_INPUT, NATIVE_JNI, VOICE, BUILD_TEST, ERROR
- Auth restrictions: never log passwords, tokens, user text being typed (IME sees ALL keystrokes)
- Add IME-specific restriction: NEVER log the text content being typed by the user

**Create**: `.claude/skills/systematic-debugging/references/codebase-tracing-paths.md`

8 DevKey-specific tracing paths (from design Section 2):
1. Key input: KeyView → KeyboardActionBridge → LatinIME.onKey → KeyEventSender
2. Text commit: LatinIME.onKey → InputConnection.commitText → Editor
3. Suggestion: WordComposer → Suggest → BinaryDictionary (JNI) → CandidateView
4. Modifier state: KeyView.onModifierDown → ModifierStateManager → LatinIME meta state
5. Mode switch: KeyView.onKey(SYMBOLS) → DevKeyKeyboard → LayoutMode → QwertyLayout
6. IME lifecycle: Android → LatinIME.onCreateInputView → ComposeKeyboardViewFactory → Compose
7. Voice input: VoiceInputPanel → TFLite Whisper → transcription → LatinIME.commitTyped
8. Settings: SettingsRepository → SharedPreferences → KeyboardSwitcher / LatinIME

For each: list exact file paths, key breakpoints to check, DevKeyLogger category.

**Create**: `.claude/skills/systematic-debugging/references/debug-session-management.md`

Adapt from Field Guide version:
- Server setup: identical (Node.js, port 3947)
- ADB port forwarding: identical
- App launch: replace flutter run → `./gradlew installDebug && adb shell am force-stop dev.devkey.keyboard && adb shell ime set dev.devkey.keyboard/.LatinIME`
- Session lifecycle flowchart: identical structure
- Cleanup gate rules: identical (grep `hypothesis(` in `app/src/`)
- Research agent management: identical
- Reproduction interview: adapted for IME context ("What app were you typing in? What keyboard mode? What keys?")
- 30-day retention: identical

**Create**: `.claude/skills/systematic-debugging/references/github-issues-integration.md`

**UPDATED 2026-04-08:** No per-concern files. All defects are GitHub Issues on `RobertoChavez2433/DevKey`. Content:

- **Repo**: `RobertoChavez2433/DevKey`
- **Filter queries** (used in Phase 1 triage):
  - All open defects: `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open --limit 30`
  - By area: `gh issue list --repo RobertoChavez2433/DevKey --label "area:ime-lifecycle" --state open`
  - By category: `gh issue list --repo RobertoChavez2433/DevKey --label "category:IME" --state all`
  - Recent fixes (30d): `gh issue list --repo RobertoChavez2433/DevKey --label defect --state closed --search "closed:>$(date -d '-30 days' +%Y-%m-%d)"`
- **Label taxonomy:**
  - `defect` — marker label, every defect issue has this
  - `category:{IME|UI|MODIFIER|NATIVE|BUILD|TEXT|VOICE|ANDROID}` — single category per issue
  - `area:{ime-lifecycle|compose-ui|modifier-state|native-jni|build-test|text-input|voice-dictation}` — primary concern
  - `priority:{critical|high|medium|low}`
- **DevKeyLogger category → GH labels mapping:**
  - `DevKeyLogger.ime()` → `category:IME` + `area:ime-lifecycle`
  - `DevKeyLogger.ui()` → `category:UI` + `area:compose-ui`
  - `DevKeyLogger.modifier()` → `category:MODIFIER` + `area:modifier-state`
  - `DevKeyLogger.native()` → `category:NATIVE` + `area:native-jni`
  - `DevKeyLogger.text()` → `category:TEXT` + `area:text-input`
  - `DevKeyLogger.voice()` → `category:VOICE` + `area:voice-dictation`
  - `DevKeyLogger.error()` → any category (use judgment)
- **Issue body template:**
  ```
  **Pattern**: <what to avoid — 1 line>
  **Prevention**: <how to avoid — 1-2 lines>
  **Ref**: <file:line references>
  **Root cause session**: <link to debug-sessions/*.md if applicable>
  ```
- **Defect lifecycle:**
  - `open` — active pattern to avoid
  - `closed` with comment referencing fix commit SHA — pattern resolved
  - No max count, no rotation, no archive — GitHub handles it
- **Phase 10 (DEFECT LOG) creation command:**
  ```bash
  gh issue create --repo RobertoChavez2433/DevKey \
    --title "[CATEGORY] YYYY-MM-DD: Brief Title" \
    --label "defect,category:<CAT>,area:<AREA>,priority:<P>" \
    --body "$(cat <<'EOF'
  **Pattern**: ...
  **Prevention**: ...
  **Ref**: file:line
  EOF
  )"
  ```

### Sub-phase 3.3: Create debug-research-agent

**Create**: `.claude/agents/debug-research-agent.md`

```yaml
---
name: debug-research-agent
description: Background research agent for deep debugging sessions. Launched with run_in_background during deep mode to parallelize codebase research while the user reproduces a bug.
tools: Read, Grep, Glob, Bash, mcp__jcodemunch__search_symbols, mcp__jcodemunch__get_file_outline, mcp__jcodemunch__search_text, mcp__jcodemunch__get_symbol, mcp__jcodemunch__get_file_tree
disallowedTools: Edit, Write, NotebookEdit
model: sonnet
---
```

Body adapted from Field Guide version:
- CodeMunch repo: `local/Hackers_Keyboard_Fork-{hash}` (update hash after first index)
- Defect source: GitHub Issues (`RobertoChavez2433/DevKey`, label `defect`). Add `Bash` tool to the agent's frontmatter and allow `gh issue list --repo RobertoChavez2433/DevKey ...` for defect queries only.
- Suggested instrumentation uses `DevKeyLogger.hypothesis()` format
- 15-tool-call budget strategy: identical
- Read-only on code (no Edit/Write), Bash allowed only for `gh issue list`, produce report regardless of completion

### Verify Phase 3

```bash
# Verify skill files exist
ls .claude/skills/systematic-debugging/SKILL.md
ls .claude/skills/systematic-debugging/references/*.md
# Verify agent definition
test -f .claude/agents/debug-research-agent.md && echo "PASS" || echo "FAIL"
```

---

## Phase 4: Specialist Agents + Implement Orchestrator

**Risk**: Zero — only `.claude/` config files.
**Agent**: general-purpose (sonnet)

### Sub-phase 4.1: Create ime-core-agent

**Create**: `.claude/agents/ime-core-agent.md`

```yaml
---
name: ime-core-agent
description: IME core specialist. Handles LatinIME, KeyEventSender, ModifierStateManager, InputConnection, Suggest, WordComposer. Never modifies JNI bridge.
tools: Read, Edit, Write, Bash, Glob, Grep
model: sonnet
permissionMode: acceptEdits
memory: project
specialization:
  primary_features:
    - ime-lifecycle
    - modifier-state
    - text-input
  shared_rules:
    - data-validation-rules.md
  context_loading: |
    Before starting work:
    - Query defects: gh issue list --repo RobertoChavez2433/DevKey --label "area:ime-lifecycle" --label "defect" --state open --limit 10 --json number,title,body
    - Also query: --label "area:modifier-state" and --label "area:text-input"
    - Read architecture-decisions/ime-core-constraints.md
    - Read agent-memory/ime-core-agent/MEMORY.md
---
```

Body includes: scope definition (LatinIME.kt, KeyEventSender.kt, ModifierStateManager.kt, ChordeTracker.kt, Suggest.kt, WordComposer.kt), key constraints (never rename JNI bridge, init order, serviceScope coroutines, ASCII keycodes), reference to `@.claude/rules/ime-lifecycle.md`, response rules (structured summary, file:line refs, no code blocks >5 lines).

### Sub-phase 4.2: Create compose-ui-agent

**Create**: `.claude/agents/compose-ui-agent.md`

```yaml
---
name: compose-ui-agent
description: Compose UI specialist. Handles keyboard layouts, themes, KeyView, KeyboardActionBridge, ComposeKeyboardViewFactory, QwertyLayout.
tools: Read, Edit, Write, Bash, Glob, Grep
model: sonnet
permissionMode: acceptEdits
memory: project
specialization:
  primary_features:
    - compose-ui
  shared_rules:
    - data-validation-rules.md
  context_loading: |
    Before starting work:
    - Query defects: gh issue list --repo RobertoChavez2433/DevKey --label "area:compose-ui" --label "defect" --state open --limit 10 --json number,title,body
    - Read architecture-decisions/compose-ui-constraints.md
    - Read agent-memory/compose-ui-agent/MEMORY.md
---
```

Body includes: scope definition (ui/keyboard/**, ui/theme/**, DevKeyKeyboard.kt, KeyView.kt, KeyboardActionBridge.kt, ComposeKeyboardViewFactory.kt, QwertyLayout.kt, KeyMapGenerator.kt), key constraints (shared lifecycle owner, theme tokens in DevKeyTheme.kt only, isComposeLocalCode filter, KeyMapGenerator in debug/ package), reference to `@.claude/rules/compose-keyboard.md`, response rules.

### Sub-phase 4.3: Create code-review-agent

**Create**: `.claude/agents/code-review-agent.md`

Adapted from Field Guide's code-review-agent:
- `tools: Read, Grep, Glob` / `disallowedTools: Write, Edit, Bash` / `model: opus`
- Review checklist adapted for Kotlin/Android (not Flutter/Dart)
- Anti-patterns: God class (LatinIME at 2500 lines), magic KeyEvent values, missing coroutine cancellation, Handler usage (should be serviceScope)
- Key files table: LatinIME.kt, KeyEventSender.kt, ModifierStateManager.kt, DevKeyKeyboard.kt, DevKeyTheme.kt
- Output: `.claude/code-reviews/YYYY-MM-DD-{scope}-review.md`
- Defect logging: file new GitHub issues via `gh issue create --repo RobertoChavez2433/DevKey --label "defect,category:<CAT>,area:<AREA>,priority:<P>" ...` for any new recurring pattern found during review. Add `Bash` to agent's allowed tools (limited to `gh issue *` commands for defect filing).
- Verification: `./gradlew assembleDebug` (full test suite runs in CI only)

### Sub-phase 4.4: Create security-agent

**Create**: `.claude/agents/security-agent.md`

Adapted from Field Guide's security-agent for IME-specific concerns:
- `tools: Read, Grep, Glob` / `disallowedTools: Write, Edit, Bash` / `model: opus`
- Iron Law: NEVER MODIFY CODE. REPORT ONLY.
- 8 audit domains (from design Section 3):
  1. Input capture (IME sees all keystrokes — ensure no production logging of passwords/PII)
  2. Plugin loading (PluginManager untrusted package loading)
  3. KeyEvent injection (Ctrl+V, Alt+Tab target window validation)
  4. Clipboard access (Room clipboard_history table)
  5. Network (voice/dictionary must not leak data)
  6. Permissions (INPUT_METHOD_SERVICE, RECORD_AUDIO)
  7. BuildConfig.DEBUG gates (debug logging in release)
  8. ProGuard/R8 (JNI bridge obfuscation)
- OWASP Mobile Top 10 scorecard adapted for IME
- Output: `.claude/code-reviews/YYYY-MM-DD-{scope}-security-review.md`

### Sub-phase 4.5: Create implement-orchestrator agent definition

**UPDATED 2026-04-08:** The implement pipeline uses the **Agent tool exclusively** — no headless `claude --bare` / `--agent` launches. Review flow is restructured: **one completeness review per phase** (spec intent vs. implementation); the **full 3-agent adversarial sweep + fixer loop runs once at the end** of all phases.

**Create**: `.claude/agents/implement-orchestrator.md`

```yaml
---
name: implement-orchestrator
description: Orchestrates implementation plan execution by reading plans and dispatching work to specialized agents via the Agent tool. NEVER implements code directly. NEVER uses headless claude invocations.
tools: Read, Glob, Grep, Task, Write
disallowedTools: Edit, NotebookEdit
model: opus
maxTurns: 200
permissionMode: bypassPermissions
---
```

> `Write` is allowed only for the checkpoint file at `.claude/state/implement-checkpoint.json`. All source-file edits go through dispatched agents via the Agent tool (Task).

Body includes:

**Iron Law:**
> Never edit source files. Never invoke `claude --bare` or any headless CLI. All work flows through the Agent tool (Task) dispatching named agents.

**Agent Routing Table for DevKey:**

| File Pattern | Agent (subagent_type) | Model |
|---|---|---|
| `**/LatinIME.kt`, `**/KeyEventSender.kt`, `**/Modifier*`, `**/Suggest*`, `**/WordComposer*`, `**/ChordeTracker*` | `ime-core-agent` | sonnet |
| `**/ui/keyboard/**`, `**/ui/theme/**`, `**/ComposeKeyboard*`, `**/DevKeyKeyboard*`, `**/KeyView*`, `**/QwertyLayout*` | `compose-ui-agent` | sonnet |
| `**/pckeyboard/**`, `**/jni/**`, `**/native-lib.cpp` | `general-purpose` (JNI is review-only unless user explicitly authorizes) | sonnet |
| `app/src/test/**`, `app/src/androidTest/**` | `general-purpose` | sonnet |
| `**/build.gradle*`, `**/proguard*`, `gradle/libs.versions.toml` | `general-purpose` | sonnet |
| `.claude/**` config | `general-purpose` | sonnet |

**Project context block:**
- Working directory: `C:\Users\rseba\Projects\Hackers_Keyboard_Fork`
- Build command: `./gradlew assembleDebug`
- Lint command: `./gradlew lint`
- **NEVER run `./gradlew clean`** — it's prohibited
- **NEVER run `./gradlew connectedAndroidTest`** — it uninstalls the IME (known defect)
- Full test suite runs in CI only — plans must not call `./gradlew test`

**Per-phase loop (4 steps):**

For each phase in the plan:

1. **Dispatch implementer(s)** via the Agent tool.
   - Use the routing table to pick the agent. Split by file ownership; max 3 parallel agents for independent files; sequential when dependent.
   - Dispatch all parallel agents in a SINGLE message with multiple Task tool calls.
   - Each implementer receives: phase name + plan line range, assigned files, a pre-fetched list of open defect issues for the relevant `area:*` labels (orchestrator runs `gh issue list --repo RobertoChavez2433/DevKey --label "area:<area>" --label defect --state open --json number,title,body` once per phase and passes the JSON into the prompt), relevant `rules/*.md`, and the instruction "Implement only your assigned files. Read each file before editing. Avoid the anti-patterns in the listed defect issues."
   - Agents return a structured summary (files modified, decisions, lint status).

2. **Run build** via Bash: `./gradlew assembleDebug`.
   - If build fails: dispatch a fixer agent (same subagent_type as the implementer) with the error output. Max 3 fix attempts per phase. If still failing → mark phase BLOCKED.

3. **Dispatch ONE completeness-review-agent** via the Agent tool.
   - Prompt: read the plan section for this phase, read the spec at `.claude/specs/YYYY-MM-DD-<name>-spec.md`, read the files listed in the implementer's summary, verify that **the spec's intent for this phase has been fully captured**.
   - Returns APPROVE or REJECT with findings.
   - If REJECT → dispatch the implementer again with the findings as a correction prompt (max 2 correction rounds per phase). If still rejected → mark phase BLOCKED and escalate.
   - If APPROVE → proceed to step 4.
   - **Note:** This per-phase review is completeness ONLY — no code-review-agent, no security-agent at this stage.

4. **Update checkpoint** — write `.claude/state/implement-checkpoint.json` with phase status, modified files, decisions, completeness verdict. MANDATORY after every phase.

**End-of-plan quality gate loop (runs ONCE after all phases done):**

1. Run `./gradlew lint`.
2. Dispatch **3 review agents in parallel** in a SINGLE message (Agent tool):
   - `code-review-agent` — code quality, DRY/KISS, Kotlin idioms, coroutine patterns, file:line refs
   - `security-agent` — IME input capture, plugin loading, KeyEvent injection, clipboard, permissions, BuildConfig.DEBUG gates, ProGuard/R8 (8 audit domains from Sub-phase 4.4)
   - `completeness-review-agent` — full plan vs. full spec (not per-phase this time) — verifies no drift introduced across phase boundaries
   Each returns APPROVE or REJECT with findings. Reports saved to `.claude/code-reviews/YYYY-MM-DD-{plan-name}-review-cycle-{N}.md`.
3. If ANY reviewer returned findings:
   - Consolidate findings (CRITICAL + HIGH + MEDIUM — skip LOW)
   - Dispatch the appropriate fixer agent(s) via the Agent tool (routing table applies — ime-core-agent for IME files, compose-ui-agent for UI files, etc.)
   - Re-run `./gradlew assembleDebug` after fixes
   - Re-dispatch **all 3** reviewers in parallel (full re-review, never partial)
4. **Monotonicity check:** if cycle N+1 has ≥ findings count of cycle N, ESCALATE to user immediately.
5. **Hard cap:** max 3 review/fix cycles. After 3 → mark BLOCKED, present remaining findings to user.
6. LOW-severity findings from all cycles are logged to checkpoint's `low_findings` array — never block, never fixed automatically.

**Severity standard:**

| Severity | Definition | Action |
|---|---|---|
| CRITICAL | Broken build, data loss, security vulnerability, crashes | Fix immediately |
| HIGH | Incorrect behavior, significant spec deviation, API misuse | Fix |
| MEDIUM | Code smell, missing edge case, minor spec deviation | Fix |
| LOW | Style preference, nit, optional cleanup | Log only, never fix |

**Context management:**
- At ~80% context utilization: write checkpoint, return HANDOFF status to supervisor. Supervisor spawns a fresh orchestrator which resumes from checkpoint.

**Termination states (orchestrator return):**

| Status | Meaning | Supervisor action |
|---|---|---|
| DONE | All phases complete, end-of-plan gate passed | Present final summary, delete checkpoint |
| HANDOFF | Context limit hit mid-run | Spawn fresh orchestrator (same checkpoint) |
| BLOCKED | Build fix loop exhausted, phase completeness fail, or end-of-plan cycle cap hit | Escalate to user with remaining findings |

### Verify Phase 4

```bash
# Verify all agent definitions exist
ls .claude/agents/ime-core-agent.md .claude/agents/compose-ui-agent.md
ls .claude/agents/code-review-agent.md .claude/agents/security-agent.md
ls .claude/agents/implement-orchestrator.md .claude/agents/debug-research-agent.md
```

---

## Phase 5: Rules, Hooks, Architecture Decisions, Tailor + Writing-Plans Pipeline, Audit-Config

**Risk**: Zero — only `.claude/` config files.
**Agent**: general-purpose (sonnet)

### Sub-phase 5.1: Create rules with paths: frontmatter

**Create**: `.claude/rules/ime-lifecycle.md`
```yaml
---
paths:
  - "**/LatinIME.kt"
  - "**/InputMethodService*"
---
```
Content extracted from MEMORY.md pitfalls: sKeyboardSettings init order, serviceScope coroutines, ComposeKeyboardViewFactory lifecycle sharing, adb install doesn't restart IME, KeyCodes ASCII values.

**Create**: `.claude/rules/compose-keyboard.md`
```yaml
---
paths:
  - "**/ui/keyboard/**"
  - "**/ui/theme/**"
---
```
Content: theme tokens in DevKeyTheme.kt only, isComposeLocalCode filter, LayoutMode separate from KeyboardMode, row height weight ratios, KeyMapGenerator in debug/ package.

**Create**: `.claude/rules/jni-bridge.md`
```yaml
---
paths:
  - "**/pckeyboard/**"
---
```
Content: NEVER rename BinaryDictionary, package path hardcoded in C++, file stays Java forever, asciiToKeyCode table.

**Create**: `.claude/rules/build-config.md`
```yaml
---
paths:
  - "**/build.gradle*"
  - "**/proguard*"
---
```
Content: Gradle Kotlin DSL with version catalogs, Min SDK 26 / Target 34, ProGuard must not break JNI, connectedAndroidTest uninstalls app, debug logging is intentional.

### Sub-phase 5.2: Create architecture-decisions constraint files

**Create**: `.claude/architecture-decisions/ime-core-constraints.md`
Content: Hard rules for LatinIME, init ordering, coroutine patterns, InputConnection rules.

**Create**: `.claude/architecture-decisions/compose-ui-constraints.md`
Content: Lifecycle sharing, theme token locations, bridge filtering, layout architecture.

**Create**: `.claude/architecture-decisions/modifier-state-constraints.md`
Content: ModifierStateManager + ChordeTracker dual system, double-tap stateBeforeDown tracking, multitouch state machine.

**Create**: `.claude/architecture-decisions/native-jni-constraints.md`
Content: BinaryDictionary is Java forever, C++ package path, dictionary plugin security.

**Create**: `.claude/architecture-decisions/data-validation-rules.md`
Content: Shared validation rules across all agents (KeyCode values, ASCII mapping, build requirements).

### Sub-phase 5.3: Create hooks

**Create**: `.claude/hooks/block-orchestrator-writes.sh`

Adapted from Field Guide version (identical logic, different project):
- PreToolUse hook: denies Edit/Write/NotebookEdit/MultiEdit
- Denies Bash commands with `sed -i`, `awk -i`, `>`, `>>`, `tee`, `rm`, `mv`, `cp`
- Uses `grep -oP` (no jq on Windows)

**Create**: `.claude/hooks/pre-agent-dispatch.sh`

Adapted for Android/Kotlin:
- Check 1: `./gradlew --version` succeeds (Gradle available)
- Check 2: `adb devices` returns at least one device (ADB reachable)
- Check 3: `./gradlew assembleDebug` can start (or skip if recent build)

### Sub-phase 5.4a: Create tailor skill (NEW — added 2026-04-08)

**Create**: `.claude/skills/tailor/SKILL.md`

Adapted from Field Guide's `tailor/skill.md`. This skill runs jcodemunch research against a spec and produces a structured `.claude/tailor/YYYY-MM-DD-<spec-slug>/` directory that feeds `/writing-plans`.

**Frontmatter:**
```yaml
---
name: tailor
description: "Runs jcodemunch research on a spec, discovers architectural patterns, verifies ground truth, and outputs a structured tailor directory. Prerequisite for writing-plans."
user-invocable: true
---
```

**Content adaptations from FG version:**

1. **Announce at start:** "I'm using the tailor skill to map the DevKey codebase for this spec."

2. **Phase 1 — Accept Spec:**
   - Read spec from `.claude/specs/`
   - Read adversarial review from `.claude/adversarial_reviews/` if it exists
   - Derive spec-slug from filename
   - Create output dir: `.claude/tailor/YYYY-MM-DD-<spec-slug>/`

3. **Phase 2 — jcodemunch Research Sequence (PRESCRIBED, 9 mandatory steps):**

   Run in this order. All MANDATORY unless noted. Repo key: `local/Hackers_Keyboard_Fork-<hash>` (confirm via `list_repos`).

   1. `mcp__jcodemunch__index_folder` on `C:\Users\rseba\Projects\Hackers_Keyboard_Fork` with `incremental: true`, `use_ai_summaries: true`
   2. `mcp__jcodemunch__get_file_outline` on EVERY file listed in the spec's "Files to Modify/Create" section — **SKIP** credential-bearing files (`**/local.properties`, `**/keystore*`, `**/*.jks`, `**/signing*.properties`, `**/google-services.json`, any file matching `*secret*` or `*credential*`)
   3. `mcp__jcodemunch__get_dependency_graph` for all key files identified in step 2
   4. `mcp__jcodemunch__get_blast_radius` for all symbols being changed
   5. `mcp__jcodemunch__find_importers` for all symbols being changed
   6. `mcp__jcodemunch__get_class_hierarchy` for all classes involved
   7. `mcp__jcodemunch__find_dead_code` to identify cleanup targets
   8. `mcp__jcodemunch__search_symbols` for every key symbol mentioned in the spec
   9. `mcp__jcodemunch__get_symbol_source` for each relevant symbol — **SKIP** symbols from credential-bearing files

   **Optional:** `get_ranked_context`, `get_context_bundle` when additional prioritization is needed.
   **NOT used:** `index_repo`, `get_repo_outline` (GitHub-fetch — freezes).

4. **Phase 3 — Pattern Discovery** (DevKey-specific patterns to look for):
   - **IME lifecycle pattern** (InputMethodService, onCreate init order, ComposeKeyboardViewFactory sharing)
   - **Modifier state pattern** (ModifierStateManager + ChordeTracker dual system, multitouch state machine)
   - **Key dispatch pattern** (KeyView → KeyboardActionBridge → LatinIME.onKey → KeyEventSender)
   - **Compose keyboard layout pattern** (DevKeyKeyboard, KeyView, LayoutMode, QwertyLayout)
   - **Theme token pattern** (DevKeyTheme.kt only, no raw colors/spacing)
   - **Native JNI pattern** (BinaryDictionary package path locked to `org.pocketworkstation.pckeyboard`)
   - **Coroutine pattern** (serviceScope on IME, never GlobalScope)
   - **Room DAO pattern** (learned data, clipboard, macros)
   - **Settings pattern** (SharedPreferences vs DataStore — document current choice)

   Each pattern gets: "How we do it" (2-3 sentences), 1-2 exemplars with full source from `get_symbol_source`, reusable methods table, required imports.

5. **Phase 4 — Ground Truth Verification** (DevKey sources of truth):

   | Category | Source of Truth |
   |----------|----------------|
   | KeyCode values | `KeyData.kt` (`KeyCodes` object) |
   | Layout modes | `LayoutMode.kt` + `QwertyLayout.kt` |
   | Modifier states | `ModifierStateManager.kt` |
   | Theme tokens | `DevKeyTheme.kt` |
   | Testing keys | `.claude/test-flows/registry.md` + widget `Key(...)` constants |
   | JNI class paths | C++ `native-lib.cpp` `RegisterNatives` calls — **MUST match** Kotlin package |
   | Intent actions | `AndroidManifest.xml` |
   | Room schema | `DevKeyDatabase.kt` + entity classes |
   | Gradle versions | `gradle/libs.versions.toml` |
   | Build flavors | `app/build.gradle.kts` |
   | ProGuard rules | `app/proguard-rules.pro` |
   | Calibration coordinates | `.claude/test-flows/calibration.json` + `.claude/logs/key-coordinates.md` |

6. **Phase 5 — Research Agent Gap-Fill** (read-only subagents via Agent tool, opus only, max 3, one question each). Skip if jcodemunch resolved everything.

7. **Phase 6 — Write Output Directory:**

   ```
   .claude/tailor/YYYY-MM-DD-<spec-slug>/
   ├── manifest.md
   ├── dependency-graph.md
   ├── ground-truth.md
   ├── blast-radius.md
   ├── patterns/
   │   └── <pattern-name>.md
   └── source-excerpts/
       ├── by-file.md
       └── by-concern.md
   ```

   **Security:** Exclude content from `local.properties`, keystores, signing configs. Include method signatures and structure only, never credential values. Additionally: never include raw user-typed text from test fixtures (IME privacy).

8. **Phase 7 — Present Summary** with the format from FG, ending with "Run /writing-plans when ready."

**Hard Gate:** Do NOT write the output directory (Phase 6) until Phases 2-5 are complete.

**Anti-patterns** (same as FG): skipping jcodemunch steps, using `index_repo`/`get_repo_outline`, haiku subagents, patterns without exemplars, skipping ground truth verification, `use_ai_summaries: false`, credential values in output.

---

### Sub-phase 5.4b: Create writing-plans skill

**Create**: `.claude/skills/writing-plans/SKILL.md`

Adapted from Field Guide's `writing-plans/skill.md`.

**Frontmatter:**
```yaml
---
name: writing-plans
description: "Reads tailor output and writes implementation plans for DevKey. Main agent writes directly for small/medium plans, splits across plan-writer subagents for large ones. Runs 3-sweep adversarial reviews with fix loops."
user-invocable: true
---
```

**Content adaptations from FG version:**

1. **Announce at start:** "I'm using the writing-plans skill to create an implementation plan."

2. **Spec as Source of Truth** section — identical to FG. Reviews verify plan, not spec.

3. **Phase 1 — Accept:** Read spec from `.claude/specs/`. Search `.claude/tailor/` for matching directory. **Hard gate:** if no tailor output → "No tailor output found. Run `/tailor <spec-path>` first." STOP.

4. **Phase 2 — Load Tailor Output:** Read manifest, ground-truth, dependency-graph, blast-radius, patterns, source-excerpts in that order.

5. **Phase 3 — Writer Strategy:** Under ~2000 lines → main agent direct; over ~2000 → plan-writer-agent subagents via Agent tool.

6. **Phase 4 — Write the Plan** using the Plan Format Reference below.

7. **Phase 5 — Review Loop:**
   - Create `.claude/plans/review_sweeps/<plan-name>-<date>/`
   - Dispatch 3 reviewers in ONE message: `code-review-agent`, `security-agent`, `completeness-review-agent` (all opus)
   - Save reports as `{type}-review-cycle-N.md`
   - If findings → dispatch `plan-fixer-agent` with consolidated findings, then re-run ALL 3 reviews
   - **Max 3 cycles** before escalating to user

8. **Phase 6 — Present Summary.**

**Plan Format Reference (DevKey-adapted):**

Plan header:
```markdown
# [Feature Name] Implementation Plan

> **For Claude:** Use the implement skill (`/implement`) to execute this plan.

**Goal:** [One sentence]
**Spec:** `.claude/specs/YYYY-MM-DD-<name>-spec.md`
**Tailor:** `.claude/tailor/YYYY-MM-DD-<spec-slug>/`

**Architecture:** [2-3 sentences]
**Tech Stack:** Kotlin + C++ NDK, Jetpack Compose, Room, TF Lite, Gradle KTS
**Blast Radius:** [N direct, N dependent, N tests, N cleanup]
```

**Agent Routing Table for DevKey:**

| File Pattern | Agent |
|-------------|-------|
| `**/LatinIME.kt`, `**/KeyEventSender.kt`, `**/Modifier*`, `**/Suggest*`, `**/WordComposer*`, `**/ChordeTracker*` | `ime-core-agent` |
| `**/ui/keyboard/**`, `**/ui/theme/**`, `**/ComposeKeyboard*`, `**/DevKeyKeyboard*`, `**/KeyView*`, `**/QwertyLayout*` | `compose-ui-agent` |
| `**/pckeyboard/**`, `**/jni/**`, `**/native-lib.cpp` | `general-purpose` (sonnet) — JNI bridge is locked; plans here are review-only unless user explicitly authorizes |
| `**/build.gradle*`, `**/proguard*`, `gradle/libs.versions.toml` | `general-purpose` (sonnet) |
| `app/src/test/**`, `app/src/androidTest/**` | `general-purpose` (sonnet) |
| All files post-phase | `code-review-agent` + `security-agent` (parallel) |
| `.claude/**` config | `general-purpose` (sonnet) |

**Test Run Rules (DevKey):**

| Allowed in sub-phase steps | NOT allowed in sub-phase steps |
|---------------------------|-------------------------------|
| `./gradlew assembleDebug` (build verification) | `./gradlew test` (full unit suite — CI only) |
| `./gradlew lint` (static analysis) | `./gradlew connectedAndroidTest` (uninstalls app — see defect) |
| `./gradlew --offline compileDebugKotlin` (quick type check) | Manual ADB install/test loops |

The last step of the **final sub-phase in each phase** may include `./gradlew assembleDebug` as a verification gate. CI runs the full test suite after push.

**Code Annotations** (required where logic isn't self-evident):
```kotlin
// WHY: [Business reason for this code]
// NOTE: [Pattern choice, references existing convention]
// IMPORTANT: [Non-obvious behavior or gotchas]
// FROM SPEC: [References specific spec requirement]
```

**Phase Ordering Rules (DevKey):**
1. Data layer first (Room entities, DAOs, SharedPreferences migration) before IME/UI consumers
2. IME backend (`LatinIME.kt`, modifier state) before Compose UI that depends on it
3. Native/JNI changes last if any — they block everything else if broken
4. Dead code removal in final phase
5. Every sub-phase ends with `./gradlew assembleDebug` verification

**Ground Truth Verification** — cross-reference using DevKey source-of-truth table from tailor's `ground-truth.md`. Every string literal in plan code must match.

**Hard Gate:**
```
Do NOT write the plan (Phase 4) until tailor output loaded (Phases 1-2) and writer strategy determined (Phase 3).
Do NOT dispatch reviewers (Phase 5) until the plan file exists at .claude/plans/.
```

**Anti-patterns** (adapted from FG):

| Anti-Pattern | Why It's Wrong | Do This Instead |
|--------------|----------------|-----------------|
| Writing plans without tailor output | Missing codebase context → bad plans | Run `/tailor` first |
| Dispatching subagents for small plans | Unnecessary overhead, loses context | Main agent writes plans under ~2000 lines directly |
| Vague steps ("add validation") | Implementing agent has to think | Complete code with annotations |
| Missing file paths | Agent guesses wrong | Exact `path/to/file.kt:line` |
| Including `./gradlew test` in plans | Tests run in CI only | Use `./gradlew assembleDebug` as local gate |
| Including `connectedAndroidTest` in plans | Uninstalls app, requires IME re-enable | CI only; if needed, manual recovery documented |
| Assumed names in plan code | Runtime failures every time | Cross-reference tailor `ground-truth.md` |
| Sequential adversarial review | Wastes time | Dispatch all 3 reviewers in parallel in ONE message |
| Partial re-review after fixes | Misses regressions | Always run ALL 3 sweeps each cycle |
| Modifying `BinaryDictionary.java` package | Breaks C++ JNI `RegisterNatives` | Package path is locked forever |

**Save Locations:**
- Plans: `.claude/plans/YYYY-MM-DD-<feature-name>.md`
- Writer fragments: `.claude/plans/parts/<plan-name>-writer-N.md`
- Review sweeps: `.claude/plans/review_sweeps/<plan-name>-<date>/`

### Sub-phase 5.4c: Create planning pipeline support files

**Create**: `.claude/skills/writing-plans/references/plan-format-template.md` — extracted plan header + step template from the skill body, for plan-writer-agent subagents to include inline in their prompts.

**Create**: `.claude/skills/writing-plans/references/agent-routing-table.md` — extracted agent routing table, for plan-writer-agent subagents.

**Create**: `.claude/agents/plan-writer-agent.md` — adapted from FG:
```yaml
---
name: plan-writer-agent
description: Writes implementation plan fragments for large plans. Reads a tailor directory and writes phase-scoped plan sections. Never invokes reviewers or the full planning pipeline.
tools: Read, Write, Glob, Grep
model: opus
---
```
Body: instructions to read the tailor directory from `TAILOR_DIR`, write to `OUTPUT_PATH`, only phases in `PHASE_ASSIGNMENT`, follow the plan format template inline in the dispatch prompt.

**Create**: `.claude/agents/plan-fixer-agent.md` — adapted from FG:
```yaml
---
name: plan-fixer-agent
description: Addresses review findings on a plan via surgical edits. Never rewrites the plan. Preserves spec intent.
tools: Read, Edit, Grep, Glob
model: opus
---
```

**Create**: `.claude/agents/completeness-review-agent.md`:
```yaml
---
name: completeness-review-agent
description: Reviews a plan against its spec for drift, gaps, and missing requirements. Spec is sacred — any deviation is a finding.
tools: Read, Grep, Glob
disallowedTools: Edit, Write, Bash
model: opus
---
```
Body: instructions to read plan, spec, and tailor's `ground-truth.md`, return APPROVE or REJECT with findings.

### Sub-phase 5.5: Create audit-config skill

**Create**: `.claude/skills/audit-config/SKILL.md`

Adapted from Field Guide version:
- Read-only health check of `.claude/` against codebase
- Validates file paths, class references, agent tool restrictions
- Security invariants: security-agent has `disallowedTools: Write, Edit, Bash`, code-review-agent same, orchestrator has `disallowedTools: Edit, Write, Bash, NotebookEdit`
- Save report to `.claude/outputs/audit-report-YYYY-MM-DD.md`
- Scope exclusions: `logs/`, `adversarial_reviews/`, `code-reviews/`, `test-results/`

### Sub-phase 5.6: Update brainstorming skill

**Modify**: `.claude/skills/brainstorming/SKILL.md`

Changes:
- Spec output path: `specs/YYYY-MM-DD-{topic}-spec.md` (was `docs/plans/`)
- Remove steps 5-6 (write design doc + implementation plan)
- Terminal state: "Offer to invoke `/writing-plans`" (was: write implementation plan)
- Remove references to `frontend-design` and `mcp-builder`
- Keep everything else: HARD-GATE, one question at a time, multiple choice preferred, 2-3 approaches

### Sub-phase 5.7: Update end-session skill

**Modify**: `.claude/skills/end-session/SKILL.md`

**UPDATED 2026-04-08:** Defects are GitHub Issues, not files.

Changes:
- Remove the entire "Update Defects" section (currently step 3) that writes to `.claude/autoload/_defects.md`
- Replace with a "File Defects to GitHub Issues" step:

  > For each new pattern discovered this session, file a GH issue:
  > ```bash
  > gh issue create --repo RobertoChavez2433/DevKey \
  >   --title "[CATEGORY] YYYY-MM-DD: Brief Title" \
  >   --label "defect,category:<CAT>,area:<AREA>,priority:<P>" \
  >   --body "**Pattern**: ...\n**Prevention**: ...\n**Ref**: file:line"
  > ```
  > If a blocker was resolved this session, close the issue with a comment referencing the fix commit: `gh issue close <num> --comment "Fixed in <sha>"`

- **Category labels** (single `category:` label per issue): `IME`, `UI`, `MODIFIER`, `NATIVE`, `BUILD`, `TEXT`, `VOICE`, `ANDROID`
- **Area labels** (single `area:` label per issue): `ime-lifecycle`, `compose-ui`, `modifier-state`, `native-jni`, `build-test`, `text-input`, `voice-dictation`
- **Priority labels**: `priority:critical`, `priority:high`, `priority:medium`, `priority:low`
- Remove the "Defect Categories" table from Rules section — it's now encoded in labels
- Remove the "Max 7 active defects — oldest rotates to archive" rule — GitHub handles lifecycle; no rotation
- Remove any reference to `.claude/autoload/_defects.md` or `.claude/logs/defects-archive.md`
- Update Step 5 ("Display Summary") to list filed issue URLs, not defect file locations
- The NO-git-commands rule still applies to code git ops, but `gh issue` commands are explicitly allowed and encouraged in this skill

### Sub-phase 5.8: Rewrite implement skill (NO HEADLESS, Agent tool only)

**Modify**: `.claude/skills/implement/SKILL.md`

**Full rewrite.** The existing skill has an inline orchestrator prompt designed for the old "spawn a single orchestrator agent that handles everything" model. Replace with a thin supervisor skill that dispatches the `implement-orchestrator` agent via the **Agent tool (Task)** and loops on its return status.

**Iron Law (at top of rewritten SKILL.md):**
> 1. **NEVER use headless mode.** No `claude --bare`, no `claude --agent`, no `--print`, no CLI invocations of claude. Everything goes through the Agent tool (Task).
> 2. **Supervisor never edits source.** Allowed writes: only `.claude/state/implement-checkpoint.json` (initialize/delete).
> 3. **Supervisor never reads plan content.** Only plan metadata (phase names, line ranges). The orchestrator and implementers read the plan themselves.

**Supervisor workflow (rewritten body):**

**Step 1: Accept the plan**
1. User invokes `/implement <plan-path>` (bare filename → search `.claude/plans/`)
2. Read plan header only — extract phase names + line ranges, spec path (`**Spec:**` line), tailor path (`**Tailor:**` line if present)
3. Checkpoint path: `.claude/state/implement-checkpoint.json`
4. Checkpoint existence check:
   - Missing → start fresh
   - Exists + same plan path → ask user "Resume from checkpoint (phases done: N) or start fresh?"
   - Exists + different plan → delete, start fresh
5. Present phases to user, confirm before starting

**Step 2: Initialize checkpoint** (if starting fresh)

```json
{
  "plan": "<absolute plan path>",
  "spec": "<absolute spec path or null>",
  "tailor": "<absolute tailor path or null>",
  "phases": {
    "1": {
      "name": "...",
      "plan_lines": "10-85",
      "status": "pending",
      "files_modified": [],
      "decisions": [],
      "completeness": { "verdict": null, "findings": [], "correction_rounds": 0 },
      "build": null,
      "fix_attempts": 0
    }
  },
  "modified_files": [],
  "decisions": [],
  "end_gate": {
    "status": "pending",
    "cycles": [],
    "low_findings": []
  },
  "blocked": []
}
```

**Step 3: Dispatch orchestrator via Agent tool**

```
Task tool call:
  subagent_type: implement-orchestrator
  description: "Execute implementation plan"
  prompt: |
    Plan file: {{PLAN_PATH}}
    Spec file: {{SPEC_PATH}}
    Tailor dir: {{TAILOR_PATH}}
    Checkpoint file: {{CHECKPOINT_PATH}}

    Execute the full orchestration loop defined in your agent frontmatter.
    Return DONE, HANDOFF, or BLOCKED.
```

No headless flags, no `--print`, no `--json-schema`, no `tee` pipelines, no `stream-filter.py`, no `extract-result.py`. Just one Task call.

**Step 4: Handle return status (loop)**

```
while status != DONE:
  result = Task(subagent_type=implement-orchestrator, ...)
  status = parse(result)
  if status == HANDOFF:
    log cycle count, spawn a fresh orchestrator (Step 3 again)
  if status == BLOCKED:
    present blocked item(s) to user
    options: fix manually / skip / adjust plan
    if user resolves → update checkpoint, spawn again
    if user gives up → break
  if status == DONE:
    break
```

**Step 5: Final summary** (read from checkpoint)

```
## Implementation Complete

**Plan**: [filename]
**Orchestrator cycles**: N (M handoffs)
**Phases**: N/N complete

### Per-Phase Completeness
Phase 1 — APPROVED (0 correction rounds)
Phase 2 — APPROVED (1 correction round)
...

### End-of-Plan Quality Gate
- Build: PASS
- Lint: PASS
- Code Review: PASS (cycle 1)
- Security Review: PASS (cycle 1)
- Completeness Review: PASS (cycle 1)

### Files Modified
[deduped list from checkpoint]

### Decisions Made
[list]

### Low Findings (logged, not fixed)
[count]

Ready to review and commit.
```

Supervisor does NOT commit or push.

**What to remove from the current SKILL.md:**
- The entire `<ORCHESTRATOR-PROMPT>` block (lines ~106-183) — orchestrator instructions now live in `.claude/agents/implement-orchestrator.md`
- Any reference to headless commands, `--bare`, `--agent`, `--json-schema`, `stream-filter.py`, `extract-result.py`, `tee` pipelines
- Any reference to `references/headless-commands.md` — this file is NOT created in Phase 2; delete the reference
- The 3-reviewer-per-phase workflow — replaced by 1-completeness-per-phase + end-of-plan 3-sweep

**What to keep:**
- Iron Law framing (strengthened with the NO HEADLESS rule)
- Checkpoint-resume behavior
- The four sections (Accept plan / Initialize checkpoint / Dispatch / Final summary)
- Troubleshooting table (rewritten to drop CLI-specific entries)

**Verification after rewrite:**
```bash
grep -c "claude --bare" .claude/skills/implement/SKILL.md    # must be 0
grep -c "claude --agent" .claude/skills/implement/SKILL.md   # must be 0
grep -c "json-schema" .claude/skills/implement/SKILL.md      # must be 0
grep -c "subagent_type: implement-orchestrator" .claude/skills/implement/SKILL.md  # must be ≥ 1
```

### Sub-phase 5.9: Update resume-session skill

**Modify**: `.claude/skills/resume-session/SKILL.md`

**UPDATED 2026-04-08:** Defects are GitHub Issues, not files — no file to read.

Changes:
- Step 1: Read 2 files only (was 3): `memory/MEMORY.md` + `autoload/_state.md`
- Remove `autoload/_defects.md` from read list entirely
- Remove the `**Active Defects**:` line from the summary format. Defects are on GitHub; load them lazily if the user asks by running `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open --limit 20`
- Add to the Context Loading Reference section: "Defects: `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open` — only run when the user asks about defects or starts work on a feature"
- Add `gh` to the allowed-commands note (the NO-git-commands rule stays for code git ops, but `gh issue` is permitted)

### Verify Phase 5

```bash
# Verify rules
ls .claude/rules/*.md
# Verify architecture decisions
ls .claude/architecture-decisions/*.md
# Verify hooks
ls .claude/hooks/*.sh
# Verify new skills
test -f .claude/skills/tailor/SKILL.md && echo "PASS" || echo "FAIL"
test -f .claude/skills/writing-plans/SKILL.md && echo "PASS" || echo "FAIL"
test -f .claude/skills/audit-config/SKILL.md && echo "PASS" || echo "FAIL"
# Verify new planning agents
ls .claude/agents/plan-writer-agent.md .claude/agents/plan-fixer-agent.md .claude/agents/completeness-review-agent.md
# Verify writing-plans reference files
ls .claude/skills/writing-plans/references/*.md
```

---

## Phase 6: CLAUDE.md + docs/INDEX.md + logs/README.md

**Risk**: Zero — only `.claude/` config files.
**Agent**: general-purpose (sonnet)

### Sub-phase 6.1: Rewrite CLAUDE.md

Restructure `.claude/CLAUDE.md` to match Field Guide format:

1. **Title**: `# DevKey` (keep existing)
2. **Project description**: Keep existing 1-liner
3. **Skills table**: Expand to 9 skills (add systematic-debugging, tailor, writing-plans, audit-config, test)
4. **When to Use What table**: Add debug, tailor, writing-plans, audit-config rows. Document the full planning pipeline: `/brainstorming` → `/tailor` → `/writing-plans` → `/implement`
5. **Tech Stack table**: Keep existing
6. **Domain Rules section**: NEW — table of 4 rules with `paths:` triggers
7. **Quick Reference Commands**: Keep build commands
8. **Common Mistakes**: Trim to top 4 (detailed versions now in rules/)
9. **Context Efficiency section**: NEW — adopted from Field Guide (parallel Tasks, cap Explore at 3, summarize in 3-5 bullets)
10. **Directory Reference table**: Expand to all ~18 directories
11. **Conventions sections**: Keep existing Kotlin/Android, IME-Specific, Build sections
12. **Session section**: Keep existing `/resume-session` and `/end-session` pointers

### Sub-phase 6.2: Create docs/INDEX.md

Navigation index for all `.claude/` documentation:

```markdown
# .claude/ Documentation Index

## Skills
| Skill | File | Purpose |
|-------|------|---------|
| /brainstorming | skills/brainstorming/SKILL.md | Collaborative design → spec |
| /writing-plans | skills/writing-plans/skill.md | Spec → dependency graph → plan |
| /implement | skills/implement/SKILL.md | Plan → autonomous execution |
| /systematic-debugging | skills/systematic-debugging/SKILL.md | 10-phase root cause analysis |
| /test | skills/test/SKILL.md | ADB automated keyboard testing |
| /audit-config | skills/audit-config/SKILL.md | .claude/ health check |
| /resume-session | skills/resume-session/SKILL.md | Session start |
| /end-session | skills/end-session/SKILL.md | Session end |

## Agents
[table of all 8 agents with model, tools, scope]

## Rules
[table of 4 rules with paths: triggers]

## State Files
[table of all state files and their purposes]

## Defects
Defects live as GitHub Issues on `RobertoChavez2433/DevKey` with the `defect` label.
- All open defects: `gh issue list --repo RobertoChavez2433/DevKey --label defect --state open`
- By area: `gh issue list --repo RobertoChavez2433/DevKey --label "area:<area>"` (see label taxonomy in `skills/systematic-debugging/references/github-issues-integration.md`)
- Historical archive: `.claude/logs/defects-archive-final.md` (snapshot at migration time)
```

### Sub-phase 6.3: Update logs/README.md

Add all files currently in `logs/`:
- `state-archive.md`
- `defects-archive-final.md` (historical snapshot of old `_defects.md` — NOT rotated, NOT updated; new defects go to GitHub Issues)
- `key-coordinates.md`
- `e2e-test-observations.md`
- `emulator-audit-wave1.md`
- `emulator-test-log.json`

Remove `defects-archive.md` from the file list if present (deleted in Phase 1.3). Remove any rotation/archival rules for defects — they no longer apply. Defects are tracked as GitHub Issues on `RobertoChavez2433/DevKey`.

### Sub-phase 6.4: Update memory/MEMORY.md

- Fix test count: "355 tests" → "361 tests" (line 38)
- Add "Systematic Debugging" section noting DevKeyLogger class and HTTP server
- Add new directories to "Current State" section
- Update "Current State" to note `.claude/` restructure completed

### Verify Phase 6

```bash
# Verify CLAUDE.md has new skills
grep -c "systematic-debugging" .claude/CLAUDE.md
grep -c "writing-plans" .claude/CLAUDE.md
grep -c "audit-config" .claude/CLAUDE.md
# Verify INDEX.md
test -f .claude/docs/INDEX.md && echo "PASS" || echo "FAIL"
# Build still passes
./gradlew assembleDebug
# Tests still pass
./gradlew test
```

---

## Git Strategy

One commit per phase for bisectability:
1. `Restructure .claude directory scaffolding and fix stale references`
2. `Add DevKeyLogger and HTTP debug server`
3. `Add systematic-debugging skill with references`
4. `Add specialist agents and implement-orchestrator`
5. `Add rules, hooks, architecture decisions, tailor + writing-plans pipeline, audit-config skill, and planning agents`
6. `Update CLAUDE.md, docs/INDEX.md, and logs/README.md for new structure`

---

## Summary

| Phase | Files Created | Files Modified | Risk |
|-------|--------------|----------------|------|
| 1: Scaffolding + stale fixes | ~15 | ~10 | Zero |
| 2: DevKeyLogger + HTTP server | ~6 | 0 | Low |
| 3: Debug skill | ~6 | 0 | Zero |
| 4: Agents | ~5 | 0 | Zero |
| 5: Rules, hooks, skills (now incl. tailor + writing-plans + 3 planning agents) | ~25 | ~5 | Zero |
| 6: CLAUDE.md + docs | ~2 | ~3 | Zero |
| **Total** | **~59** | **~18** | **Low** |

## Post-implementation tasks (not part of /implement)

1. Re-index the repo in jcodemunch so tailor has fresh data:
   ```
   mcp__jcodemunch__index_folder(path="C:\\Users\\rseba\\Projects\\Hackers_Keyboard_Fork", incremental=false, use_ai_summaries=true)
   ```
2. Update `.claude/autoload/_state.md` — session count advanced past 36; current state says "Session 36" with 2026-03-17 timestamp.
3. Update `.claude/state/PROJECT-STATE.json` `current_phase` out of "Dead Code Cleanup + Kotlin Quality" (which is now committed).
4. Record the post-index jcodemunch repo key (`local/Hackers_Keyboard_Fork-<hash>`) into `debug-research-agent.md` and `tailor/SKILL.md` references.
