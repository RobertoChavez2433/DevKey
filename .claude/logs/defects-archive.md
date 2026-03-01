# Defects Archive

Archived/resolved defect patterns from `_defects.md` rotation.

---

## Resolved 2026-03-01 (Session 17 — emulator audit fixes implemented)

### [UI] 2026-03-01: Main Activity is old Hacker's Keyboard setup wizard — BLOCKER
**Pattern**: The launcher activity was the unmodified Hacker's Keyboard setup wizard.
**Resolution**: Replaced with DevKeyWelcomeActivity (Compose-based, 3 setup steps, API 33+ handling).

### [UI] 2026-03-01: 123 toolbar button does not switch to symbols layout
**Pattern**: Tapping "123" had no effect. Root cause: stale IMELifecycleOwner corrupted Compose state.
**Resolution**: Fresh lifecycle owner per onCreateInputView(), extracted activeLayout to local val.

### [UI] 2026-03-01: Dynamic keyboard height ignores user preference
**Pattern**: KeyboardView.kt hardcoded height as screenHeightDp * 0.40.
**Resolution**: Wired SharedPreferences listener with orientation support, clamped to 48dp minimum.

---

## Archived 2026-03-01 (Session 16 — rotation for emulator audit bugs)

### [BUILD] 2026-02-23: Kotlin classes are final by default — breaks Java subclasses
**Pattern**: Converting Java classes to Kotlin makes them `final`. Any Java subclass extending the converted class gets "cannot inherit from final" errors. Same for methods — `@JvmOverloads` generates final bridge methods.
**Prevention**: Always add `open` to Kotlin classes/methods that Java code extends/overrides. Avoid `@JvmOverloads` on open methods — use explicit overloads instead.
**Ref**: @Keyboard.kt (Key class, setShiftState, getNearestKeys)
**Archived reason**: Well-established pattern, unlikely to forget.

### [BUILD] 2026-02-23: Kotlin companion object vals aren't Java compile-time constants
**Pattern**: `@JvmField val` in Kotlin companion objects are NOT compile-time constants for Java. Java `switch/case` requires compile-time constants, so `case Keyboard.KEYCODE_DELETE:` fails.
**Prevention**: Use `const val` (not `@JvmField val`) for primitive constants in companion objects. Or convert Java switch/case to if/else if chains.
**Ref**: @Keyboard.kt companion object, @LatinIME.java
**Archived reason**: Well-established pattern, unlikely to forget.

### [BUILD] 2026-02-23: AGP 8.x R fields are non-constant — breaks switch/case
**Pattern**: With AGP 8.x and non-transitive R classes, `R.styleable.*` fields are no longer compile-time constants. Java switch/case on `R.styleable.*` fails with "constant expression required".
**Prevention**: Convert switch/case on R.* fields to if/else if chains when migrating to AGP 8.x.
**Ref**: @LatinKeyboardBaseView.java, @LatinKeyboardView.java
**Archived reason**: Well-established pattern, unlikely to forget.

---

## Archived 2026-02-24 (Session 13)

### [BUILD] 2026-02-23: MCP servers need .mcp.json in project root
**Pattern**: Defining MCP servers only in `.claude/settings.local.json` may not connect — Claude Code prefers `.mcp.json` in the project root.
**Prevention**: Always create `.mcp.json` in project root for MCP server definitions. Use `settings.local.json` only for permissions and other settings.
**Ref**: @.mcp.json, @.claude/settings.local.json
**Archived reason**: Now established, unlikely to recur.

## Archived 2026-02-24 (Session 12)

### [BUILD] 2026-02-23: Compose compiler plugin requires Kotlin 2.0+
**Pattern**: `org.jetbrains.kotlin.plugin.compose` plugin ID was introduced in Kotlin 2.0. Using it with Kotlin 1.9.x causes "plugin not found" errors.
**Prevention**: Use Kotlin 2.0+ when using the compose-compiler plugin. With Kotlin 1.x, use the older `composeOptions { kotlinCompilerExtensionVersion = "..." }` approach instead.
**Ref**: @build.gradle.kts, @gradle/libs.versions.toml
**Archived reason**: Project is on Kotlin 2.0.21, won't recur.
