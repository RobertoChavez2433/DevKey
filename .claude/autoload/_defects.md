# Active Defects

Max 7 active. Oldest rotates to `.claude/logs/defects-archive.md`.

## Active Patterns

### [BUILD] 2026-02-23: Kotlin classes are final by default — breaks Java subclasses
**Pattern**: Converting Java classes to Kotlin makes them `final`. Any Java subclass extending the converted class gets "cannot inherit from final" errors. Same for methods — `@JvmOverloads` generates final bridge methods.
**Prevention**: Always add `open` to Kotlin classes/methods that Java code extends/overrides. Avoid `@JvmOverloads` on open methods — use explicit overloads instead.
**Ref**: @Keyboard.kt (Key class, setShiftState, getNearestKeys)

### [BUILD] 2026-02-23: Kotlin companion object vals aren't Java compile-time constants
**Pattern**: `@JvmField val` in Kotlin companion objects are NOT compile-time constants for Java. Java `switch/case` requires compile-time constants, so `case Keyboard.KEYCODE_DELETE:` fails.
**Prevention**: Use `const val` (not `@JvmField val`) for primitive constants in companion objects. Or convert Java switch/case to if/else if chains.
**Ref**: @Keyboard.kt companion object, @LatinIME.java

### [BUILD] 2026-02-23: AGP 8.x R fields are non-constant — breaks switch/case
**Pattern**: With AGP 8.x and non-transitive R classes, `R.styleable.*` fields are no longer compile-time constants. Java switch/case on `R.styleable.*` fails with "constant expression required".
**Prevention**: Convert switch/case on R.* fields to if/else if chains when migrating to AGP 8.x.
**Ref**: @LatinKeyboardBaseView.java, @LatinKeyboardView.java

### [DB] 2026-02-24: DevKeyDatabase uses destructive migration — wipes user data on schema change
**Pattern**: `DevKeyDatabase.buildDatabase()` calls `fallbackToDestructiveMigration()`. Any change to Room entities (adding/removing columns, changing types, adding tables) triggers a full database wipe — all macros, learned words, clipboard history, and command apps are lost.
**Prevention**: Before changing any Room entity, write a proper `Migration(oldVersion, newVersion)` and add it via `.addMigrations()`. Test migration with both fresh install and upgrade scenarios.
**Ref**: @DevKeyDatabase.kt (buildDatabase method)

### [BUILD] 2026-02-23: /implement agents need broad Bash/Edit/Write permissions
**Pattern**: Background agents dispatched by the /implement orchestrator cannot prompt the user for tool permissions. They get blocked on Edit, Write, and Bash commands.
**Prevention**: Before running /implement, add `Edit`, `Write`, and `Bash(*)` patterns to `.claude/settings.local.json` permissions allow list.
**Ref**: @.claude/settings.local.json

### [BUILD] 2026-02-23: MCP servers need .mcp.json in project root
**Pattern**: Defining MCP servers only in `.claude/settings.local.json` may not connect — Claude Code prefers `.mcp.json` in the project root.
**Prevention**: Always create `.mcp.json` in project root for MCP server definitions. Use `settings.local.json` only for permissions and other settings.
**Ref**: @.mcp.json, @.claude/settings.local.json
